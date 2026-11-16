package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeSearchRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.KnowledgeSearchServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 52, section 17.4: the real application with DeterministicEmbeddingClient as the EmbeddingClient bean. */
class KnowledgeSearchIntegrationTests extends IngestionTestSupport {
    static final String SEARCH = "/api/v1/admin/knowledge/search";
    @Autowired KnowledgeSearchService searchService;
    @Autowired KnowledgeSearchProperties searchProperties;
    @Autowired KnowledgeCleaningProperties cleaningProperties;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate stringRedis;
    long sample;

    @BeforeEach
    void sampleIsReady() throws Exception {
        sample = upload("rag-sample-regulations.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        assertThat(statusOf(currentFile(sample))).isEqualTo("READY");
        beanClient.reset();
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    ResultActions search(Map<String, Object> body) throws Exception { return perform(post(SEARCH), body); }

    List<Integer> chunkIndexes(Map<String, Object> body) throws Exception {
        var result = tree(search(body).andExpect(status().isOk()).andReturn());
        var indexes = new ArrayList<Integer>();
        result.get("results").forEach(r -> indexes.add(r.get("chunkIndex").asInt()));
        return indexes;
    }

    double topScore(String query) throws Exception {
        return tree(search(Map.of("query", query, "minScore", 0)).andReturn()).get("results").get(0).get("score").asDouble();
    }

    @Test
    void theSevenQuestionsFindTheirChunkAtRankOne() throws Exception {
        var expected = new LinkedHashMap<String, Integer>();
        expected.put("a student must complete at least 120 credits required by the curriculum", 4);
        expected.put("late registration fee of 500 baht", 1);
        expected.put("late payment fine of 50 baht per day", 2);
        expected.put("attend at least 80 percent of the classes", 3);
        expected.put("นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษา", 5);
        expected.put("An academic year has two regular semesters and one summer session", 0);
        expected.put("How many credits do I need to graduate?", 4);
        var scores = new LinkedHashMap<String, Double>();
        for (var e : expected.entrySet()) {
            assertThat(chunkIndexes(Map.of("query", e.getKey(), "minScore", 0)).get(0)).as(e.getKey()).isEqualTo(e.getValue());
            scores.put(e.getKey(), topScore(e.getKey()));
        }
        System.out.println("DETERMINISTIC_SCORES " + scores);
        assertThat(topScore("What are the opening hours of the swimming pool?")).isLessThan(0.30);
        assertThat(chunkIndexes(Map.of("query", "a student must complete at least 120 credits required by the curriculum", "minScore", 0.30)))
                .first().isEqualTo(4);
        assertThat(chunkIndexes(Map.of("query", "What are the opening hours of the swimming pool?", "minScore", 0.30))).isEmpty();
        assertThat(chunkIndexes(Map.of("query", "How many credits do I need to graduate?", "minScore", 0.99))).isEmpty();
    }

    @Test
    void limitPerDocumentCacheAndCacheOff() throws Exception {
        String q = "a student must complete at least 120 credits required by the curriculum";
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).hasSize(3);
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0, "documentId", sample))).hasSize(5);
        int calls = beanClient.queryCalls();
        var first = tree(search(Map.of("query", "fresh question about tuition", "minScore", 0)).andReturn()).get("results");
        assertThat(beanClient.queryCalls()).isEqualTo(calls + 1);
        var second = tree(search(Map.of("query", "fresh question about tuition", "minScore", 0)).andReturn()).get("results");
        assertThat(beanClient.queryCalls()).isEqualTo(calls + 1);
        assertThat(second).isEqualTo(first);
        assertThat(stringRedis.keys("knowledge:search:query-vector:*")).isNotEmpty()
                .allSatisfy(key -> assertThat(key).doesNotContain("tuition"));

        var off = new KnowledgeSearchServiceImpl(beanClient, vectorStore, new QueryEmbeddingCache(stringRedis, Duration.ZERO),
                searchProperties, cleaningProperties, null);
        var actor = new AuthenticatedUser(actorId, "s", "r", false, Set.of("SUPER_ADMIN"));
        int before = beanClient.queryCalls();
        off.adminSearch(actor, new KnowledgeSearchRequest("cache off question", null, 0.0, null, null, null, null));
        off.adminSearch(actor, new KnowledgeSearchRequest("cache off question", null, 0.0, null, null, null, null));
        assertThat(beanClient.queryCalls()).isEqualTo(before + 2);
    }

    @Test
    void lifecycleArchiveDeleteReplaceAndModelChange() throws Exception {
        String q = "late registration fee of 500 baht";
        perform(patch(BASE + "/" + sample + "/archive"), null).andExpect(status().isOk());
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isEmpty();
        perform(patch(BASE + "/" + sample + "/activate"), null).andExpect(status().isOk());
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isNotEmpty();
        assertThat(processingRow(currentFile(sample))).containsEntry("status", "READY").containsEntry("attempt_count", 1);

        replaceFile(sample, "v2.txt", TXT, KnowledgeRealFiles.utf8(sampleText().replace("at least 120 credits", "at least 126 credits")))
                .andExpect(status().isCreated());
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isEmpty();
        switchable.runOnce();
        var after = tree(search(Map.of("query", "at least 126 credits", "minScore", 0)).andReturn()).get("results");
        assertThat(after.get(0).get("revisionNumber").asInt()).isEqualTo(2);
        after.forEach(r -> assertThat(r.get("revisionNumber").asInt()).isEqualTo(2));
        assertThat(after.get(0).get("content").asString()).contains("126 credits");

        beanClient.model("test-embedding-B");
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isEmpty();
        reprocess(sample, null).andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isNotEmpty();

        perform(delete(BASE + "/" + sample), null).andExpect(status().isNoContent());
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isEmpty();
        switchable.runOnce();
        assertThat(chunkIndexes(Map.of("query", q, "minScore", 0))).isEmpty();
    }

    @Test
    void providerFailuresAndNoTransactionDuringTheProviderCall() throws Exception {
        var active = new ArrayList<Boolean>();
        beanClient.onCall(text -> active.add(TransactionSynchronizationManager.isActualTransactionActive()));
        search(Map.of("query", "transaction check question")).andExpect(status().isOk());
        assertThat(active).containsExactly(false);
        beanClient.failAlways("EMBEDDING_TIMEOUT");
        search(Map.of("query", "q1")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("KNOWLEDGE_SEARCH_UNAVAILABLE"));
        beanClient.failAlways("EMBEDDING_RATE_LIMITED");
        search(Map.of("query", "q2")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("code").value("KNOWLEDGE_SEARCH_RATE_LIMITED"));
        beanClient.failAlways(null);
        beanClient.configured(false);
        search(Map.of("query", "q3")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("KNOWLEDGE_SEARCH_NOT_CONFIGURED"));
    }

    @Test
    void endpointContract() throws Exception {
        var ok = search(Map.of("query", "  How many   credits do I need to graduate?  ", "minScore", 0)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("query").value("How many credits do I need to graduate?"))
                .andExpect(jsonPath("model").value(DeterministicEmbeddingClient.MODEL)).andExpect(jsonPath("topK").value(5))
                .andExpect(jsonPath("minScore").value(0.0)).andExpect(jsonPath("studentScope").doesNotExist())
                .andExpect(jsonPath("tookMs").isNumber())
                .andExpect(jsonPath("results[0].rank").value(1)).andExpect(jsonPath("results[0].documentId").value(sample))
                .andExpect(jsonPath("results[0].category.code").value("REGULATION")).andExpect(jsonPath("results[0].scopeType").value("UNIVERSITY"))
                .andExpect(jsonPath("results[0].unitType").value("TEXT")).andExpect(jsonPath("results[0].academicYearLabel").value("2026-2027"))
                .andReturn();
        String body = ok.getResponse().getContentAsString();
        for (String forbidden : List.of("embedding\"", "distance", "storageKey", "sha256", "originalFilename"))
            assertThat(body).doesNotContain(forbidden);
        String q501 = "x".repeat(501);
        for (String bad : List.of("{}", "{\"query\":\"   \"}", "{\"query\":\"" + q501 + "\"}", "{\"query\":\"c\",\"topK\":0}",
                "{\"query\":\"c\",\"topK\":11}", "{\"query\":\"c\",\"topK\":\"5\"}", "{\"query\":\"c\",\"minScore\":-0.1}",
                "{\"query\":\"c\",\"minScore\":1.1}", "{\"query\":\"c\",\"categoryId\":0}", "{\"query\":\"c\",\"academicYear\":\"2026\"}",
                "{\"query\":\"c\",\"studentScope\":{\"programId\":0}}", "{\"query\":\"c\",\"unknownField\":true}", "{\"query\":"))
            mvc.perform(post(SEARCH).header("Authorization", "Bearer " + access).contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));

        long programDoc = upload("program.txt", TXT, KnowledgeRealFiles.utf8(sampleText()), Map.of("programId", String.valueOf(programA)));
        switchable.runOnce();
        assertThat(statusOf(currentFile(programDoc))).isEqualTo("READY");
        var scoped = tree(search(Map.of("query", "How many credits do I need to graduate?", "minScore", 0, "studentScope", Map.of())).andReturn());
        scoped.get("results").forEach(r -> assertThat(r.get("documentId").asLong()).isEqualTo(sample));
        var withProgram = tree(search(Map.of("query", "How many credits do I need to graduate?", "minScore", 0,
                "studentScope", Map.of("programId", programA))).andReturn());
        var docs = new HashSet<Long>();
        withProgram.get("results").forEach(r -> docs.add(r.get("documentId").asLong()));
        assertThat(docs).containsExactlyInAnyOrder(sample, programDoc);
        assertThat(withProgram.get("studentScope").get("programId").asLong()).isEqualTo(programA);

        mvc.perform(post(SEARCH).contentType("application/json").content("{\"query\":\"x\"}")).andExpect(status().isUnauthorized());
        String student = studentToken("RAG-SEARCH-1");
        mvc.perform(post(SEARCH).header("Authorization", "Bearer " + student).contentType("application/json").content("{\"query\":\"x\"}"))
                .andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            access = login();
            search(Map.of("query", "credits")).andExpect(status().isOk());
        }
    }

    @Test
    void serviceGuards() {
        var studentAuth = new UsernamePasswordAuthenticationToken(new AuthenticatedStudent(42, "S-42", "session", false), null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(studentAuth);
        assertThat(searchService.studentSearch("a student must complete at least 120 credits required by the curriculum", new StudentKnowledgeScope(null, null), 5))
                .isNotEmpty().allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(sample));
        assertThatThrownBy(() -> searchService.studentSearch("x", null, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> searchService.studentSearch("x", new StudentKnowledgeScope(null, null), 0)).isInstanceOf(IllegalArgumentException.class);
        var actor = new AuthenticatedUser(actorId, "s", "r", false, Set.of("SUPER_ADMIN"));
        assertThatThrownBy(() -> searchService.adminSearch(actor, new KnowledgeSearchRequest("x", null, null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        assertThatThrownBy(() -> searchService.studentSearch("x", new StudentKnowledgeScope(null, null), 5)).isInstanceOf(AccessDeniedException.class);
        assertThat(searchService.adminSearch(actor, new KnowledgeSearchRequest("credits", null, 0.0, null, null, null, null)).results()).isNotEmpty();
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> searchService.studentSearch("x", new StudentKnowledgeScope(null, null), 5)).isInstanceOf(RuntimeException.class);
    }
}
