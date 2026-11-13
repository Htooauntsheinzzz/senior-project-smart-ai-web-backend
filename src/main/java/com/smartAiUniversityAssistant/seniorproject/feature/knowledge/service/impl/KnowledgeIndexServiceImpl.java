package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeIndexStatisticsResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Index statistics for Admins (spec file 51, section 11), counted on every call. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {
    private final KnowledgeVectorStore vectorStore;
    private final EmbeddingClient client;
    private final DocumentIngestionService ingestion;

    public KnowledgeIndexServiceImpl(KnowledgeVectorStore vectorStore, EmbeddingClient client, DocumentIngestionService ingestion) {
        this.vectorStore = vectorStore;
        this.client = client;
        this.ingestion = ingestion;
    }

    @Override
    public KnowledgeIndexStatisticsResponse statistics(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
        boolean configured = client.configured();
        String model = configured ? client.model() : null;
        IndexStatistics s = vectorStore.statistics(model);
        return new KnowledgeIndexStatisticsResponse(ingestion.ingestionAvailable(), configured, model, client.dimensions(),
                s.readyDocuments(), s.staleDocuments(), s.searchableDocuments(), s.totalChunks(), s.searchableChunks(),
                s.chunksOfOtherModels(), s.outdatedChunks(), s.totalPages(), s.chunkTableBytes(), s.vectorIndexBytes());
    }
}
