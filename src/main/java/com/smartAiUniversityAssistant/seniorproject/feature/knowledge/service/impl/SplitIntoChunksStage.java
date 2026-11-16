package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeChunkingProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The CHUNK stage (spec file 49). Deletes the file's earlier chunks, streams the cleaned pages (50 at a time) through
 * KnowledgeTextChunker, drops chunks whose content_hash was already emitted for this file, numbers the rest 0, 1, 2 …
 * and inserts them in batches (100 chunks or 200,000 characters). The embedding columns stay NULL.
 */
@Component
public class SplitIntoChunksStage implements IngestionStage {
    private static final Logger log = LoggerFactory.getLogger(SplitIntoChunksStage.class);
    static final int DELETE_BATCH = 500;
    static final int PAGE_BATCH = 50;
    static final int INSERT_CHUNKS = 100;
    static final int INSERT_CHARS = 200_000;

    private final AcademicDocumentPageRepository pages;
    private final AcademicDocumentChunkRepository chunks;
    private final KnowledgeChunkingProperties properties;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public SplitIntoChunksStage(AcademicDocumentPageRepository pages, AcademicDocumentChunkRepository chunks,
            KnowledgeChunkingProperties properties, EntityManager entityManager, PlatformTransactionManager transactions, Clock clock) {
        this.pages = pages;
        this.chunks = chunks;
        this.properties = properties;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setTimeout(15);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Override public IngestionStageType type() { return IngestionStageType.CHUNK; }

    @Override
    public void execute(IngestionJob job, IngestionJobControl control) {
        long started = System.nanoTime();
        control.checkpoint();
        while (transaction.execute(s -> chunks.deleteBatch(job.fileId(), DELETE_BATCH)) > 0) control.checkpoint();
        var writer = new Writer(job, control);
        KnowledgeTextChunker chunker;
        try {
            chunker = new KnowledgeTextChunker(properties, writer::accept);
        } catch (IllegalArgumentException e) {
            throw failure("DOCUMENT_CHUNKING_FAILED");
        }
        int pageCount = 0;
        int after = 0;
        for (List<AcademicDocumentPage> batch; !(batch = batch(job.fileId(), after)).isEmpty(); ) {
            for (AcademicDocumentPage page : batch) {
                if (page.getCleanedText() == null) throw failure("DOCUMENT_CHUNKING_FAILED");
                guarded(() -> chunker.unit(page.getPageNumber(), page.getUnitType(), page.getUnitLabel(), page.getCleanedText()));
                after = page.getPageNumber();
                pageCount++;
            }
            writer.flush();
            control.checkpoint();
        }
        guarded(chunker::finish);
        writer.flush();
        if (writer.stored == 0) throw failure("DOCUMENT_TEXT_EMPTY");
        log.info("KNOWLEDGE_CHUNK documentId={} fileId={} pages={} chunks={} duplicatesDropped={} avgChars={} maxChars={} splitLines={} durationMs={}",
                job.documentId(), job.fileId(), pageCount, writer.stored, writer.duplicates, writer.totalChars / writer.stored,
                writer.maxChars, chunker.splitLines(), (System.nanoTime() - started) / 1_000_000);
    }

    /** Numbers, de-duplicates and stores the chunks the chunker emits. */
    private final class Writer {
        final IngestionJob job;
        final IngestionJobControl control;
        final Set<String> hashes = new HashSet<>();
        final List<AcademicDocumentChunk> batch = new ArrayList<>();
        int stored, duplicates, maxChars;
        long totalChars;

        Writer(IngestionJob job, IngestionJobControl control) {
            this.job = job;
            this.control = control;
        }

        void accept(ChunkDraft draft) {
            if (!hashes.add(draft.contentHash())) {
                duplicates++;
                return;
            }
            if (stored >= properties.maxChunks()) throw failure("DOCUMENT_TOO_MANY_CHUNKS");
            var chunk = new AcademicDocumentChunk();
            chunk.setChunkIndex(stored++);
            chunk.setContent(draft.content());
            chunk.setSectionTitle(draft.sectionTitle());
            chunk.setContentHash(draft.contentHash());
            chunk.setCharCount(draft.charCount());
            chunk.setTokenEstimate(draft.tokenEstimate());
            chunk.setPageStart(draft.pageStart());
            chunk.setPageEnd(draft.pageEnd());
            batch.add(chunk);
            totalChars += draft.charCount();
            maxChars = Math.max(maxChars, draft.charCount());
        }

        /** Inserts the buffered chunks in transactions of at most 100 chunks or 200,000 characters, checkpoint between. */
        void flush() {
            while (!batch.isEmpty()) {
                int end = 0, chars = 0;
                while (end < batch.size() && end < INSERT_CHUNKS && chars < INSERT_CHARS) chars += batch.get(end++).getCharCount();
                insert(List.copyOf(batch.subList(0, end)));
                batch.subList(0, end).clear();
                if (!batch.isEmpty()) control.checkpoint();
            }
        }

        void insert(List<AcademicDocumentChunk> rows) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            transaction.executeWithoutResult(status -> {
                AcademicDocumentFile file = entityManager.getReference(AcademicDocumentFile.class, job.fileId());
                for (AcademicDocumentChunk chunk : rows) {
                    chunk.setFile(file);
                    chunk.setCreatedAt(now);
                }
                chunks.saveAll(rows);
                chunks.flush();
                entityManager.clear();
            });
        }
    }

    private List<AcademicDocumentPage> batch(long fileId, int after) {
        return readOnly.execute(s -> pages.findByFileIdAndPageNumberGreaterThanOrderByPageNumberAsc(fileId, after, PageRequest.of(0, PAGE_BATCH)));
    }

    /** Unexpected errors in the chunker are DOCUMENT_CHUNKING_FAILED; only the class name is logged. */
    private static void guarded(Runnable work) {
        try {
            work.run();
        } catch (KnowledgeIngestionException e) {
            throw e;
        } catch (RuntimeException | StackOverflowError e) {
            log.warn("KNOWLEDGE_CHUNK chunker error={}", e.getClass().getSimpleName());
            throw failure("DOCUMENT_CHUNKING_FAILED");
        }
    }

    static KnowledgeIngestionException failure(String code) {
        String message = switch (code) {
            case "DOCUMENT_TOO_MANY_CHUNKS" -> "The document produces more chunks than the configured limit.";
            case "DOCUMENT_TEXT_EMPTY" -> "No usable text remained for chunking.";
            default -> "The document text could not be split into chunks.";
        };
        return new KnowledgeIngestionException(IngestionStageType.CHUNK, code, false, message);
    }
}
