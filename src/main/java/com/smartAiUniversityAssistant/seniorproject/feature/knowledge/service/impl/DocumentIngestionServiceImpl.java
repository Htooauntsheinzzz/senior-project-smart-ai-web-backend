package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeIngestionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeDocumentProcessingResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocument;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.IngestionQueueRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.IngestionQueueRepository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingestion worker (spec file 46). PostgreSQL is the queue: a tick claims at most one job with FOR UPDATE SKIP LOCKED
 * and runs the installed stages in order. No transaction spans a stage; the worker's own updates are single short
 * transactions that only apply while the row still belongs to this run (same status and attempt_count).
 */
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);
    static final int UPLOAD_QUEUE_BATCH = 50;
    static final int DELETE_BATCH = 500;
    static final String SUPERSEDED_MESSAGE = "The file was replaced or the document was deleted.";
    static final String INTERRUPTED_MESSAGE = "Processing was interrupted.";

    private final IngestionQueueRepository queue;
    private final AcademicDocumentRepository documents;
    private final Clock clock;
    private final KnowledgeIngestionProperties properties;
    private final TransactionTemplate transaction;
    private final EnumMap<IngestionStageType, IngestionStage> stages = new EnumMap<>(IngestionStageType.class);
    private final KnowledgeVectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    static final int CLEANUP_ROWS = 1000;

    public DocumentIngestionServiceImpl(IngestionQueueRepository queue, AcademicDocumentRepository documents, Clock clock,
            KnowledgeIngestionProperties properties, PlatformTransactionManager transactions, List<IngestionStage> stages,
            KnowledgeVectorStore vectorStore, EmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.queue = queue;
        this.documents = documents;
        this.clock = clock;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
        for (IngestionStage stage : stages) {
            // Two beans for one stage type are a configuration error and stop the application from starting.
            if (this.stages.putIfAbsent(stage.type(), stage) != null)
                throw new IllegalStateException("Two ingestion stages are installed for " + stage.type());
        }
    }

    @Override
    public boolean ingestionAvailable() {
        return properties.enabled() && stages.size() == IngestionStageType.values().length;
    }

    /** At least one stage is installed and ingestion is enabled: reprocess can queue a job. */
    boolean processingAvailable() {
        return properties.enabled() && !stages.isEmpty();
    }

    @Override
    public void runOnce() {
        if (!properties.enabled()) return;
        LocalDateTime now = now();
        requireTransition(DocumentProcessingStatus.QUEUED, DocumentProcessingStatus.FAILED);
        for (QueueRow row : tx(() -> queue.cancelSupersededQueued(now, SUPERSEDED_MESSAGE)))
            log.info("KNOWLEDGE_INGESTION event=CANCELLED documentId={} fileId={} code=INGESTION_SUPERSEDED", row.documentId(), row.fileId());
        for (QueueRow row : tx(() -> queue.recoverInterrupted(now, now.minus(properties.staleAfter()), properties.maxAttempts(), INTERRUPTED_MESSAGE)))
            log.info("KNOWLEDGE_INGESTION event=RECOVERED documentId={} fileId={} code=INGESTION_INTERRUPTED", row.documentId(), row.fileId());
        requireTransition(DocumentProcessingStatus.FAILED, DocumentProcessingStatus.QUEUED);
        for (QueueRow row : tx(() -> queue.queueDueRetries(now)))
            log.info("KNOWLEDGE_INGESTION event=QUEUED documentId={} fileId={} source=RETRY mode=RESUME", row.documentId(), row.fileId());
        if (properties.autoQueue() && ingestionAvailable()) {
            requireTransition(DocumentProcessingStatus.UPLOADED, DocumentProcessingStatus.QUEUED);
            for (QueueRow row : tx(() -> queue.queueUploads(now, UPLOAD_QUEUE_BATCH)))
                log.info("KNOWLEDGE_INGESTION event=QUEUED documentId={} fileId={} source=AUTO mode=FULL", row.documentId(), row.fileId());
        }
        claim().ifPresent(this::run);
        cleanUp();
    }

    /** Spec file 51, section 7.1: removes outdated pages and chunks; a failure is logged once and never fails the tick. */
    void cleanUp() {
        if (vectorStore == null) return;
        try {
            long started = System.nanoTime();
            int removed = vectorStore.removeOutdated(CLEANUP_ROWS);
            if (removed > 0) log.info("KNOWLEDGE_CLEANUP rows={} durationMs={}", removed, millis(started));
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_CLEANUP failed error={}", e.getClass().getSimpleName());
        }
    }

    /** The model whose vectors count (null when embeddings are not configured). */
    private String model() {
        return embeddingClient != null && embeddingClient.configured() ? embeddingClient.model() : null;
    }

    /** Claims the oldest queued job in one short transaction and decides where the run starts (section 10.1). */
    Optional<IngestionJob> claim() {
        return tx(() -> queue.lockNextQueued().map(row -> {
            requireTransition(DocumentProcessingStatus.QUEUED, DocumentProcessingStatus.PROCESSING);
            boolean resume = ("EMBED".equals(row.failedStage()) || "INDEX".equals(row.failedStage())) && queue.chunksExist(row.fileId());
            if (!queue.markClaimed(row.processingId(), row.attemptCount(), now())) return null;
            var job = new IngestionJob(row.processingId(), row.documentId(), row.fileId(), row.revisionNumber(), row.storageKey(),
                    row.fileExtension(), row.mimeType(), row.sizeBytes(), row.sha256Checksum(), row.attemptCount() + 1,
                    resume ? IngestionStageType.EMBED : IngestionStageType.EXTRACT);
            log.info("KNOWLEDGE_INGESTION event=CLAIMED documentId={} fileId={} attempt={} firstStage={}",
                    job.documentId(), job.fileId(), job.attempt(), job.firstStage());
            return job;
        }));
    }

    void run(IngestionJob job) {
        var run = new Run(job);
        long started = System.nanoTime();
        IngestionStageType current = job.firstStage();
        try {
            if (job.firstStage() == IngestionStageType.EXTRACT) deleteOutput(job.fileId());
            for (IngestionStageType type : IngestionStageType.values()) {
                if (type.ordinal() < job.firstStage().ordinal()) continue;
                current = type;
                IngestionStage stage = stages.get(type);
                if (stage == null) {
                    fail(run, type, "INGESTION_STAGE_NOT_AVAILABLE", false, "The next processing stage is not available in this version.");
                    return;
                }
                run.stage = null;
                run.checkpoint();
                run.advanceTo(type.status());
                run.stage = type;
                run.stageStarted = clock.instant();
                long stageStarted = System.nanoTime();
                stage.execute(job, run);
                log.info("KNOWLEDGE_INGESTION event=STAGE_COMPLETED documentId={} fileId={} stage={} durationMs={}",
                        job.documentId(), job.fileId(), type, millis(stageStarted));
            }
            run.stage = null;
            run.checkpoint();
            requireTransition(DocumentProcessingStatus.EMBEDDING, DocumentProcessingStatus.READY);
            if (tx(() -> queue.markReady(job.processingId(), job.attempt(), now())))
                log.info("KNOWLEDGE_INGESTION event=READY documentId={} fileId={} attempt={} durationMs={}",
                        job.documentId(), job.fileId(), job.attempt(), millis(started));
        } catch (RunStop stop) {
            switch (stop.reason) {
                case LOST -> log.info("KNOWLEDGE_INGESTION event=LOST documentId={} fileId={} attempt={}", job.documentId(), job.fileId(), job.attempt());
                case CANCELLED -> cancel(run);
                case TIMEOUT -> fail(run, current, "INGESTION_STAGE_TIMEOUT", true, "A processing stage took too long.");
            }
        } catch (KnowledgeIngestionException e) {
            fail(run, e.stage() == null ? current : e.stage(), e.code(), e.retryable(), e.getMessage());
        } catch (DataAccessException e) {
            log.warn("KNOWLEDGE_INGESTION database error documentId={} fileId={} error={}", job.documentId(), job.fileId(), e.getClass().getSimpleName());
            fail(run, current, "INGESTION_DATABASE_ERROR", true, "A database error interrupted processing.");
        } catch (Exception | OutOfMemoryError | StackOverflowError e) {
            // Only the class name: an exception message can contain document text or a path.
            log.warn("KNOWLEDGE_INGESTION unexpected error documentId={} fileId={} error={}", job.documentId(), job.fileId(), e.getClass().getSimpleName());
            fail(run, current, "INGESTION_INTERNAL_ERROR", true, "An unexpected error interrupted processing.");
        }
    }

    private void fail(Run run, IngestionStageType stage, String code, boolean retryable, String message) {
        IngestionJob job = run.job;
        boolean retry = retryable && job.attempt() < properties.maxAttempts();
        LocalDateTime now = now();
        LocalDateTime next = retry ? now.plus(properties.retryDelay(job.attempt())) : null;
        String safe = message == null ? "Processing failed." : message.length() > 500 ? message.substring(0, 500) : message;
        String storedCode = code.length() > 100 ? code.substring(0, 100) : code;
        try {
            requireTransition(run.status, DocumentProcessingStatus.FAILED);
            if (tx(() -> queue.markFailed(job.processingId(), run.status, job.attempt(), stage == null ? null : stage.name(),
                    storedCode, safe, next, now)))
                log.info("KNOWLEDGE_INGESTION event=FAILED documentId={} fileId={} attempt={} stage={} code={} retryable={} nextAttemptInSeconds={}",
                        job.documentId(), job.fileId(), job.attempt(), stage, storedCode, retryable,
                        next == null ? "-" : Duration.between(now, next).toSeconds());
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_INGESTION could not record failure documentId={} fileId={} error={}", job.documentId(), job.fileId(), e.getClass().getSimpleName());
        }
    }

    /** The file was replaced or the document deleted while the job ran: FAILED without retry, output removed. */
    private void cancel(Run run) {
        IngestionJob job = run.job;
        try {
            requireTransition(run.status, DocumentProcessingStatus.FAILED);
            if (tx(() -> queue.markFailed(job.processingId(), run.status, job.attempt(), null, "INGESTION_SUPERSEDED",
                    SUPERSEDED_MESSAGE, null, now()))) {
                deleteOutput(job.fileId());
                log.info("KNOWLEDGE_INGESTION event=CANCELLED documentId={} fileId={} code=INGESTION_SUPERSEDED", job.documentId(), job.fileId());
            }
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_INGESTION could not cancel documentId={} fileId={} error={}", job.documentId(), job.fileId(), e.getClass().getSimpleName());
        }
    }

    /** Deletes the file's chunks, then its pages, in batches of at most 500 rows, one short transaction each. */
    void deleteOutput(long fileId) {
        while (tx(() -> queue.deleteChunks(fileId, DELETE_BATCH)) > 0) { /* next batch */ }
        while (tx(() -> queue.deletePages(fileId, DELETE_BATCH)) > 0) { /* next batch */ }
    }

    @Override
    public KnowledgeDocumentProcessingResponse requestProcessing(long documentId, long actorUserId, ReprocessMode mode) {
        if (!processingAvailable()) throw KnowledgeFailure.processingNotAvailable();
        ReprocessMode effective = mode == null ? ReprocessMode.RESUME : mode;
        ProcessingRow queued = tx(() -> {
            AcademicDocument document = documents.findByIdAndDeletedFalse(documentId).orElseThrow(KnowledgeFailure::documentNotFound);
            if (!document.isActive()) throw KnowledgeFailure.notEditable();
            queue.lockTimeout();
            ProcessingRow row = queue.currentProcessing(documentId, true).orElseThrow(KnowledgeFailure::fileNotFound);
            DocumentProcessingStatus status = DocumentProcessingStatus.valueOf(row.status());
            String failedStage = switch (status) {
                case UPLOADED -> null;
                case FAILED -> effective == ReprocessMode.FULL ? null : row.failedStage();
                case READY -> {
                    if (effective == ReprocessMode.FULL) yield null;
                    // A READY file indexed with another model is stale: RESUME re-embeds it, starting at EMBED (spec file 51, section 9).
                    if (!stale(row.fileId())) throw KnowledgeFailure.alreadyProcessed();
                    yield "EMBED";
                }
                case QUEUED, PROCESSING, CHUNKING, EMBEDDING -> throw KnowledgeFailure.processingAlreadyRunning();
            };
            requireTransition(status, DocumentProcessingStatus.QUEUED);
            if (!queue.requeue(row.processingId(), status, failedStage, actorUserId, now())) throw KnowledgeFailure.processingAlreadyRunning();
            log.info("KNOWLEDGE_INGESTION event=QUEUED documentId={} fileId={} source=ADMIN mode={}", documentId, row.fileId(), effective);
            return queue.currentProcessing(documentId, false).orElseThrow(KnowledgeFailure::fileNotFound);
        });
        return response(queued);
    }

    @Override
    public KnowledgeDocumentProcessingResponse describe(long documentId) {
        return response(queue.currentProcessing(documentId, false).orElseThrow(KnowledgeFailure::fileNotFound));
    }

    private boolean stale(long fileId) {
        return queue.chunkCount(fileId) > queue.embeddedChunkCount(fileId, model());
    }

    private KnowledgeDocumentProcessingResponse response(ProcessingRow row) {
        long pages = queue.pageCount(row.fileId());
        long chunks = queue.chunkCount(row.fileId());
        long embedded = queue.embeddedChunkCount(row.fileId(), model());
        DocumentProcessingStatus status = DocumentProcessingStatus.valueOf(row.status());
        boolean stale = status == DocumentProcessingStatus.READY && chunks > embedded;
        boolean available = ingestionAvailable();
        String message = switch (status) {
            case UPLOADED -> available ? "Uploaded. Waiting to be queued for AI processing."
                    : "Awaiting AI processing. Document ingestion is not available yet.";
            case QUEUED -> "Queued for AI processing.";
            case PROCESSING -> "Extracting and cleaning text.";
            case CHUNKING -> "Splitting text into chunks.";
            case EMBEDDING -> "Creating embeddings (" + embedded + " of " + chunks + " chunks).";
            case READY -> stale ? "Indexed with a different embedding model. Reprocess the document to make it searchable again."
                    : "Indexed and ready for AI retrieval.";
            case FAILED -> row.nextAttemptAt() != null ? "Processing failed. It will be retried automatically." : "Processing failed.";
        };
        return new KnowledgeDocumentProcessingResponse(row.documentId(), row.fileId(), row.revisionNumber(), row.status(),
                row.attemptCount(), instant(row.startedAt()), instant(row.completedAt()), row.lastErrorCode(), available, message,
                row.failedStage(), row.lastErrorMessage(), instant(row.queuedAt()), instant(row.nextAttemptAt()), pages, chunks, embedded,
                stale);
    }

    private static void requireTransition(DocumentProcessingStatus from, DocumentProcessingStatus to) {
        from.requireTransitionTo(to);
    }

    private <T> T tx(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private static long millis(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }

    /** Why a run stops without a stage failure. Thrown by checkpoint(); only the worker catches it. */
    enum StopReason { LOST, CANCELLED, TIMEOUT }

    static final class RunStop extends RuntimeException {
        final StopReason reason;
        RunStop(StopReason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }
    }

    /** The state of one run, also the IngestionJobControl handed to its stages. */
    private final class Run implements IngestionJobControl {
        final IngestionJob job;
        volatile DocumentProcessingStatus status = DocumentProcessingStatus.PROCESSING;
        volatile IngestionStageType stage;
        volatile Instant stageStarted;

        Run(IngestionJob job) { this.job = job; }

        @Override
        public void checkpoint() {
            Boolean wanted = tx(() -> {
                if (!queue.heartbeat(job.processingId(), status, job.attempt(), now())) return null;
                return queue.wanted(job.fileId());
            });
            if (wanted == null) throw new RunStop(StopReason.LOST);
            if (!wanted) throw new RunStop(StopReason.CANCELLED);
            if (stage != null && stageStarted != null
                    && Duration.between(stageStarted, clock.instant()).compareTo(properties.stageTimeout()) > 0)
                throw new RunStop(StopReason.TIMEOUT);
        }

        /** Moves PROCESSING → CHUNKING → EMBEDDING one valid step at a time (a resumed run passes every step). */
        void advanceTo(DocumentProcessingStatus target) {
            while (status != target) {
                DocumentProcessingStatus next = switch (status) {
                    case PROCESSING -> DocumentProcessingStatus.CHUNKING;
                    case CHUNKING -> DocumentProcessingStatus.EMBEDDING;
                    default -> throw new IllegalStateException("Cannot advance from " + status);
                };
                requireTransition(status, next);
                DocumentProcessingStatus from = status;
                if (!tx(() -> queue.moveRunning(job.processingId(), from, job.attempt(), next, now()))) throw new RunStop(StopReason.LOST);
                status = next;
            }
        }
    }
}
