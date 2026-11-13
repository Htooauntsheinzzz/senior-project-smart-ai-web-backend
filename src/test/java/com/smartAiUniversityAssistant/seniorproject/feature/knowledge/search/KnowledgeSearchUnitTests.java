package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.DeterministicEmbeddingClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeSearchRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.KnowledgeSearchServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.*;

/** Spec file 52, section 17.1: no Spring context, a fake vector store with fixed matches. */
class KnowledgeSearchUnitTests {
    static final AuthenticatedUser ADMIN = new AuthenticatedUser(7, "s", "r", false, Set.of("SUPER_ADMIN"));

    static KnowledgeChunkMatch match(long chunkId, long documentId, double distance, String extension) {
        return new KnowledgeChunkMatch(chunkId, (int) chunkId, "Title", 1, 1, "content " + chunkId, 10 + documentId, 1, extension,
                documentId, "Doc " + documentId, "2026-2027", 3L, "REG", "Regulations", null, null, null, distance);
    }

    static final class FakeStore implements KnowledgeVectorStore {
        List<KnowledgeChunkMatch> matches = List.of();
        RuntimeException failure;
        int calls;
        int lastLimit;
        public List<KnowledgeChunkMatch> search(float[] v, String model, KnowledgeSearchFilter filter, int limit) {
            calls++;
            lastLimit = limit;
            if (failure != null) throw failure;
            return matches;
        }
        public int reuseEmbeddings(long fileId, String model) { throw new UnsupportedOperationException(); }
        public void saveEmbeddings(Map<Long, float[]> v, String model) { throw new UnsupportedOperationException(); }
        public FileIndexCounts countByFile(long fileId, String model) { throw new UnsupportedOperationException(); }
        public int deleteByFile(long fileId, int maxRows) { throw new UnsupportedOperationException(); }
        public int removeOutdated(int maxRows) { throw new UnsupportedOperationException(); }
        public IndexStatistics statistics(String model) { throw new UnsupportedOperationException(); }
    }

    final FakeStore store = new FakeStore();
    final DeterministicEmbeddingClient client = new DeterministicEmbeddingClient();
    final KnowledgeSearchServiceImpl service = new KnowledgeSearchServiceImpl(client, store, new QueryEmbeddingCache(null, Duration.ZERO),
            new KnowledgeSearchProperties(5, 0.5, 3, 100, Duration.ZERO), new KnowledgeCleaningProperties(true, true, true), null);

    static KnowledgeSearchRequest request(String query, Integer topK, Double minScore, Long documentId) {
        return new KnowledgeSearchRequest(query, topK, minScore, null, null, documentId, null);
    }

    @Test
    void questionPreparation() {
        assertThat(KnowledgeSearchServiceImpl.prepare("  How many   credits?\n", true)).isEqualTo("How many credits?");
        assertThat(KnowledgeSearchServiceImpl.prepare("๑๒๐ หน่วยกิต", true)).isEqualTo("120 หน่วยกิต");
        for (String invalid : List.of("   ", "​﻿", "x".repeat(501)))
            assertThatThrownBy(() -> KnowledgeSearchServiceImpl.prepare(invalid, true)).isInstanceOfSatisfying(KnowledgeFailure.class,
                    e -> assertThat(e.code()).isEqualTo("VALIDATION_ERROR"));
        assertThat(KnowledgeSearchServiceImpl.prepare("x".repeat(500), true)).hasSize(500);
        String ligatures = "a".repeat(497) + "ﬃﬃ";   // 499 chars that become 503
        assertThatThrownBy(() -> KnowledgeSearchServiceImpl.prepare(ligatures, true)).isInstanceOf(KnowledgeFailure.class);
    }

    @Test
    void candidatesToResults() {
        store.matches = List.of(match(1, 1, 0.10, "pdf"), match(2, 2, 0.20, "pdf"), match(3, 3, 0.60, "pdf"));
        var response = service.adminSearch(ADMIN, request("credits", null, 0.5, null));
        assertThat(response.results()).extracting(r -> r.score()).containsExactly(0.9, 0.8);
        assertThat(response.results()).extracting(r -> r.rank()).containsExactly(1, 2);
        assertThat(response.topK()).isEqualTo(5);
        assertThat(response.model()).isEqualTo(DeterministicEmbeddingClient.MODEL);
        assertThat(store.lastLimit).isEqualTo(20);

        store.matches = List.of(match(1, 1, 0.5, "pdf"));
        assertThat(service.adminSearch(ADMIN, request("credits", null, 0.5, null)).results()).hasSize(1);   // equal to minScore is kept

        store.matches = List.of(match(1, 1, 0.1, "pdf"), match(2, 1, 0.11, "pdf"), match(3, 1, 0.12, "pdf"), match(4, 1, 0.13, "pdf"),
                match(5, 1, 0.14, "pdf"), match(6, 2, 0.15, "pdf"));
        assertThat(service.adminSearch(ADMIN, request("credits", 5, 0.0, null)).results())
                .extracting(r -> r.chunkIndex()).containsExactly(1, 2, 3, 6);
        assertThat(service.adminSearch(ADMIN, request("credits", 5, 0.0, 1L)).results())
                .extracting(r -> r.chunkIndex()).containsExactly(1, 2, 3, 4, 5);
        assertThat(service.adminSearch(ADMIN, request("credits", 1, 0.0, null)).results()).hasSize(1);
        for (int k = 1; k <= 5; k++) assertThat(KnowledgeSearchServiceImpl.candidateLimit(k)).isEqualTo(20);
        assertThat(KnowledgeSearchServiceImpl.candidateLimit(6)).isEqualTo(24);
        assertThat(KnowledgeSearchServiceImpl.candidateLimit(10)).isEqualTo(40);

        // score 0.74996 rounds to 0.75, but it is compared with minScore before rounding
        store.matches = List.of(match(1, 1, 0.25004, "pdf"));
        assertThat(service.adminSearch(ADMIN, request("credits", 5, 0.7499, null)).results()).singleElement()
                .satisfies(r -> assertThat(r.score()).isEqualTo(0.75));
        assertThat(service.adminSearch(ADMIN, request("credits", 5, 0.74997, null)).results()).isEmpty();
    }

    @Test
    void unitTypeAndScopeType() {
        assertThat(KnowledgeSearchServiceImpl.unitType("pdf")).isEqualTo("PAGE");
        assertThat(KnowledgeSearchServiceImpl.unitType("docx")).isEqualTo("SECTION");
        assertThat(KnowledgeSearchServiceImpl.unitType("xlsx")).isEqualTo("SHEET");
        assertThat(KnowledgeSearchServiceImpl.unitType("txt")).isEqualTo("TEXT");
        var m = match(1, 1, 0.1, "pdf");
        assertThat(KnowledgeSearchServiceImpl.scopeType(m)).isEqualTo("UNIVERSITY");
        assertThat(KnowledgeSearchServiceImpl.scopeType(new KnowledgeChunkMatch(1, 1, null, 1, 1, "c", 1, 1, "pdf", 1, "t", "y", 1L, "c", "n", 5L, 6L, 7L, 0.1)))
                .isEqualTo("CURRICULUM");
        assertThat(KnowledgeSearchServiceImpl.scopeType(new KnowledgeChunkMatch(1, 1, null, 1, 1, "c", 1, 1, "pdf", 1, "t", "y", 1L, "c", "n", null, 6L, null, 0.1)))
                .isEqualTo("PROGRAM");
        assertThat(KnowledgeSearchServiceImpl.scopeType(new KnowledgeChunkMatch(1, 1, null, 1, 1, "c", 1, 1, "pdf", 1, "t", "y", 1L, "c", "n", null, null, 7L, 0.1)))
                .isEqualTo("DEPARTMENT");
    }

    void fails(String code) {
        assertThatThrownBy(() -> service.adminSearch(ADMIN, request("credits", null, null, null)))
                .isInstanceOfSatisfying(KnowledgeFailure.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).doesNotContain("EMBEDDING").doesNotContain("provider text");
                });
    }

    @Test
    void errors() {
        client.configured(false);
        fails("KNOWLEDGE_SEARCH_NOT_CONFIGURED");
        assertThat(store.calls).isZero();
        assertThat(client.calls()).isZero();
        client.reset();
        client.failAlways("EMBEDDING_RATE_LIMITED");
        fails("KNOWLEDGE_SEARCH_RATE_LIMITED");
        for (String code : List.of("EMBEDDING_TIMEOUT", "EMBEDDING_AUTHENTICATION_FAILED", "EMBEDDING_INVALID_RESPONSE")) {
            client.failAlways(code);
            fails("KNOWLEDGE_SEARCH_UNAVAILABLE");
        }
        client.failAlways("EMBEDDING_NOT_CONFIGURED");
        fails("KNOWLEDGE_SEARCH_NOT_CONFIGURED");
        assertThat(store.calls).isZero();
        client.reset();
        store.failure = new QueryTimeoutException("db down");
        fails("KNOWLEDGE_SEARCH_UNAVAILABLE");
    }
}
