package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeEmbeddingProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentChunkRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentChunkRepository.PendingChunkView;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The EMBED stage (spec file 50). Reuses vectors of identical passages, then sends the remaining chunks of the file
 * (those without a vector of the configured model) to the embedding provider in batches and saves every batch at once,
 * so a failed or interrupted run resumes with what is missing. Only the embedding input of spec file 49 is sent. No
 * transaction is open during a provider call.
 */
@Component
public class CreateEmbeddingsStage implements IngestionStage {
    private static final Logger log = LoggerFactory.getLogger(CreateEmbeddingsStage.class);
    private final EmbeddingClient client;
    private final KnowledgeVectorStore vectorStore;
    private final AcademicDocumentChunkRepository chunks;
    private final KnowledgeEmbeddingProperties properties;
    private final TransactionTemplate readOnly;
    private long lastRequestNanos;
    private boolean anyRequest;

    public CreateEmbeddingsStage(EmbeddingClient client, KnowledgeVectorStore vectorStore, AcademicDocumentChunkRepository chunks,
            KnowledgeEmbeddingProperties properties, PlatformTransactionManager transactions) {
        this.client = client;
        this.vectorStore = vectorStore;
        this.chunks = chunks;
        this.properties = properties;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setTimeout(15);
        this.readOnly.setReadOnly(true);
    }

    @Override public IngestionStageType type() { return IngestionStageType.EMBED; }

    @Override
    public void execute(IngestionJob job, IngestionJobControl control) {
        long started = System.nanoTime();
        control.checkpoint();
        if (!client.configured()) throw failure("EMBEDDING_NOT_CONFIGURED", false, EmbeddingException.message("EMBEDDING_NOT_CONFIGURED"));
        String model = client.model();
        int reused = vectorStore.reuseEmbeddings(job.fileId(), model);
        control.checkpoint();
        int embedded = 0, calls = 0, after = -1;
        while (true) {
            int afterIndex = after;
            List<PendingChunkView> candidates = readOnly.execute(s -> chunks.findNeedingEmbedding(job.fileId(), model, afterIndex,
                    properties.batchSize()));
            if (candidates == null || candidates.isEmpty()) break;
            var batch = new ArrayList<PendingChunkView>();
            long tokens = 0;
            for (PendingChunkView chunk : candidates) {
                if (!batch.isEmpty() && tokens + chunk.getTokenEstimate() > properties.maxBatchTokens()) break;
                batch.add(chunk);
                tokens += chunk.getTokenEstimate();
            }
            var inputs = new ArrayList<String>(batch.size());
            for (PendingChunkView chunk : batch) {
                String input = KnowledgeTextChunker.embeddingInput(chunk.getSectionTitle(), chunk.getContent());
                if (!KnowledgeTextChunker.sha256(input).equals(chunk.getContentHash()))
                    throw failure("DOCUMENT_CHUNKS_OUTDATED", false,
                            "The stored chunks do not match this version. Reprocess the document with mode FULL.");
                inputs.add(input);
            }
            waitForInterval();
            List<float[]> vectors;
            try {
                vectors = client.embedDocuments(inputs);
                calls++;
            } catch (EmbeddingException e) {
                if (Thread.currentThread().isInterrupted()) throw KnowledgeIngestionException.interrupted(IngestionStageType.EMBED);
                log.info("KNOWLEDGE_EMBED documentId={} fileId={} failed code={} embeddedBeforeFailure={}", job.documentId(),
                        job.fileId(), e.code(), embedded);
                throw failure(e.code(), e.retryable(), e.getMessage());
            } finally {
                lastRequestNanos = System.nanoTime();
                anyRequest = true;
            }
            if (vectors.size() != batch.size()) throw failure("EMBEDDING_INVALID_RESPONSE", true, EmbeddingException.message("EMBEDDING_INVALID_RESPONSE"));
            var byId = new LinkedHashMap<Long, float[]>();
            for (int i = 0; i < batch.size(); i++) byId.put(batch.get(i).getId(), vectors.get(i));
            vectorStore.saveEmbeddings(byId, model);
            embedded += batch.size();
            after = batch.get(batch.size() - 1).getChunkIndex();
            control.checkpoint();
        }
        log.info("KNOWLEDGE_EMBED documentId={} fileId={} model={} chunks={} reused={} embedded={} providerCalls={} durationMs={}",
                job.documentId(), job.fileId(), model, reused + embedded, reused, embedded, calls, (System.nanoTime() - started) / 1_000_000);
    }

    /** min-interval-ms between two provider requests of this stage; ends at once when the thread is interrupted. */
    private void waitForInterval() {
        if (!anyRequest || properties.minIntervalMs() <= 0) return;
        long waitMillis = properties.minIntervalMs() - (System.nanoTime() - lastRequestNanos) / 1_000_000;
        if (waitMillis <= 0) return;
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KnowledgeIngestionException.interrupted(IngestionStageType.EMBED);
        }
    }

    private static KnowledgeIngestionException failure(String code, boolean retryable, String message) {
        return new KnowledgeIngestionException(IngestionStageType.EMBED, code, retryable, message);
    }
}
