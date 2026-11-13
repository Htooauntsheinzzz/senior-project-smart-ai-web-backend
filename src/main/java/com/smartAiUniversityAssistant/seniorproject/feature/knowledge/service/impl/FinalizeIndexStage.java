package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.util.List;
import org.slf4j.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The INDEX stage (spec file 51, section 5): the gate between "vectors were written" and "this file may be searched".
 * It verifies that every chunk has a vector of the configured model and that the chunk set is consistent, then removes
 * the pages and chunks of the document's older revisions (kept until now as the source for vector reuse). It creates
 * no data; the worker sets READY afterwards.
 */
@Component
public class FinalizeIndexStage implements IngestionStage {
    private static final Logger log = LoggerFactory.getLogger(FinalizeIndexStage.class);
    static final int DELETE_BATCH = 500;
    private final KnowledgeVectorStore vectorStore;
    private final EmbeddingClient client;
    private final NamedParameterJdbcTemplate jdbc;

    public FinalizeIndexStage(KnowledgeVectorStore vectorStore, EmbeddingClient client, NamedParameterJdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.client = client;
        this.jdbc = jdbc;
    }

    @Override public IngestionStageType type() { return IngestionStageType.INDEX; }

    @Override
    public void execute(IngestionJob job, IngestionJobControl control) {
        long started = System.nanoTime();
        control.checkpoint();
        if (!client.configured()) throw failure("EMBEDDING_NOT_CONFIGURED", false, EmbeddingException.message("EMBEDDING_NOT_CONFIGURED"));
        String model = client.model();
        FileIndexCounts counts = vectorStore.countByFile(job.fileId(), model);
        if (counts.chunks() == 0 || counts.embeddedWithModel() != counts.chunks()) {
            log.info("KNOWLEDGE_INDEX documentId={} fileId={} failed code=INDEX_INCOMPLETE chunks={} embedded={}",
                    job.documentId(), job.fileId(), counts.chunks(), counts.embeddedWithModel());
            throw failure("INDEX_INCOMPLETE", true, "Some chunks have no embedding yet.");
        }
        if (counts.maxChunkIndex() != counts.chunks() - 1 || counts.maxPageEnd() > counts.pageCount()) {
            log.info("KNOWLEDGE_INDEX documentId={} fileId={} failed code=INDEX_VERIFICATION_FAILED chunks={}", job.documentId(),
                    job.fileId(), counts.chunks());
            throw failure("INDEX_VERIFICATION_FAILED", false,
                    "The stored chunks are inconsistent. Reprocess the document with mode FULL.");
        }
        List<Long> older = jdbc.queryForList("SELECT id FROM academic_document_files WHERE document_id = :documentId AND id <> :fileId ORDER BY id",
                java.util.Map.of("documentId", job.documentId(), "fileId", job.fileId()), Long.class);
        long removed = 0;
        for (long fileId : older) {
            for (int n; (n = vectorStore.deleteByFile(fileId, DELETE_BATCH)) > 0; ) {
                removed += n;
                control.checkpoint();
            }
        }
        log.info("KNOWLEDGE_INDEX documentId={} fileId={} chunks={} model={} verified=true removedOldRows={} durationMs={}",
                job.documentId(), job.fileId(), counts.chunks(), model, removed, (System.nanoTime() - started) / 1_000_000);
    }

    private static KnowledgeIngestionException failure(String code, boolean retryable, String message) {
        return new KnowledgeIngestionException(IngestionStageType.INDEX, code, retryable, message);
    }
}
