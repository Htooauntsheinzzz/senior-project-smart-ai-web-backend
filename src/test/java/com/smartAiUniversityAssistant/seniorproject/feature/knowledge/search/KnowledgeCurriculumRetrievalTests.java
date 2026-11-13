package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.TXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.IngestionTestSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeRealFiles;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/**
 * Spec file 54, section 14.3: regulations (R), the same file as a handbook (H) and the sample curriculum for program A
 * (C), processed in this order with DeterministicEmbeddingClient; the Admin search endpoint and the assistant tool.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.knowledge.search.min-score=0.30")
class KnowledgeCurriculumRetrievalTests extends IngestionTestSupport {
    static final String SEARCH = "/api/v1/admin/knowledge/search";
    static final String ELECTIVES = "Compiler Construction Quantum Computing Bioinformatics Robotics";
    static final String ELECTIVES_2 = "CSC476 Software Testing CSC477 DevOps Engineering CSC478 Blockchain Technology CSC479 Digital Forensics";
    static final String TUITION = "tuition must be paid within 14 days after the first day of the semester";
    @MockitoBean LlmClient llm;
    long r, h, c;

    @BeforeEach
    void documents() throws Exception {
        r = upload("Academic Regulations 2026.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        h = upload("Student Handbook 2026.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        c = upload("BSc Computer Science Curriculum 2026.txt", TXT,
                KnowledgeRealFiles.utf8(Files.readString(Path.of("docker/dev-fixtures/rag-sample-curriculum-cs.txt"))),
                Map.of("programId", String.valueOf(programA)));
        switchable.runOnce();
        for (long d : List.of(r, h, c)) assertThat(statusOf(currentFile(d))).isEqualTo("READY");
    }

    record Row(long document, int chunk, int rank, boolean neighbor, String audience) {
        @Override public String toString() { return document + "#" + chunk + (neighbor ? "n" : "") + "@" + rank + ":" + audience; }
    }

    List<Row> rows(Map<String, Object> body) throws Exception {
        var result = tree(perform(post(SEARCH), body).andExpect(status().isOk()).andReturn());
        var rows = new ArrayList<Row>();
        result.get("results").forEach(x -> rows.add(new Row(x.get("documentId").asLong(), x.get("chunkIndex").asInt(), x.get("rank").asInt(),
                x.get("neighbor").asBoolean(), x.get("audience").isNull() ? null : x.get("audience").asString())));
        System.out.println("CURRICULUM_SEARCH " + body.get("query") + " " + rows + " scores="
                + tree(perform(post(SEARCH), Map.of("query", body.get("query"), "minScore", 0, "topK", 10)).andReturn()).get("results")
                        .valueStream().map(x -> x.get("documentId").asLong() + "#" + x.get("chunkIndex").asInt() + "=" + x.get("score").asDouble()).toList());
        return rows;
    }

    static Map<String, Object> request(String query, long programId, double minScore, Integer quota, Integer neighbors) {
        var body = new LinkedHashMap<String, Object>();
        body.put("query", query);
        body.put("topK", 4);
        body.put("minScore", minScore);
        body.put("studentScope", Map.of("programId", programId));
        if (quota != null) body.put("ownProgramQuota", quota);
        if (neighbors != null) body.put("maxNeighborPassages", neighbors);
        return body;
    }

    @Test
    void workedExampleAndAdminSearchTable() throws Exception {
        assertThat(db.queryForList("SELECT char_count FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", Integer.class,
                currentFile(c))).containsExactly(450, 409, 641, 974, 340, 634, 522, 273);

        assertThat(rows(request(ELECTIVES, programA, 0.30, null, 2)))
                .containsExactly(new Row(c, 3, 1, true, "PROGRAM"), new Row(c, 4, 1, false, "PROGRAM"));
        assertThat(rows(request(ELECTIVES, programA, 0.30, null, 0))).containsExactly(new Row(c, 4, 1, false, "PROGRAM"));
        assertThat(rows(request(ELECTIVES, programB, 0.30, null, 2))).isEmpty();
        assertThat(rows(request(ELECTIVES_2, programA, 0.25, null, 2)))
                .containsExactly(new Row(c, 3, 1, false, "PROGRAM"), new Row(c, 4, 1, true, "PROGRAM"));
        assertThat(rows(request(TUITION, programA, 0.30, 0, null))).extracting(x -> x.document() + "#" + x.chunk())
                .containsExactly(r + "#2", h + "#2", r + "#3", h + "#3");
        var reserved = rows(request(TUITION, programA, 0.30, 2, null));
        assertThat(reserved).extracting(x -> x.document() + "#" + x.chunk()).containsExactly(r + "#2", h + "#2", c + "#6", c + "#5");
        assertThat(reserved).extracting(Row::audience).containsExactly("UNIVERSITY", "UNIVERSITY", "PROGRAM", "PROGRAM");
        assertThat(rows(request(TUITION, programB, 0.30, 2, null))).extracting(x -> x.document() + "#" + x.chunk())
                .containsExactly(r + "#2", h + "#2", r + "#3", h + "#3");
        perform(post(SEARCH), Map.of("query", TUITION, "studentScope", Map.of(), "ownProgramQuota", 2))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        for (Object bad : List.of(Map.of("query", "credits", "ownProgramQuota", 2),
                Map.of("query", "credits", "ownProgramQuota", 11, "studentScope", Map.of("programId", programA)),
                Map.of("query", "credits", "maxNeighborPassages", 5)))
            perform(post(SEARCH), bad).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        var plain = tree(perform(post(SEARCH), Map.of("query", TUITION)).andExpect(status().isOk())
                .andExpect(jsonPath("ownProgramQuota").value(0)).andExpect(jsonPath("maxNeighborPassages").value(0)).andReturn());
        plain.get("results").forEach(x -> {
            assertThat(x.get("audience").isNull()).isTrue();
            assertThat(x.get("neighbor").asBoolean()).isFalse();
        });
    }

    final Deque<LlmResponse> script = new ArrayDeque<>();
    final List<LlmRequest> sent = new ArrayList<>();

    JsonNode chat(String token, String message) throws Exception {
        var response = mvc.perform(post("/api/v1/student/assistant/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("message", message))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    @Test
    @SuppressWarnings("unchecked")
    void throughTheAssistant(CapturedOutput output) throws Exception {
        when(llm.chat(any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return script.poll();
        });
        String a = studentToken("RAG-CUR-A");
        String b = studentToken("RAG-CUR-B");
        db.update("UPDATE students SET faculty_id=?, department_id=?, program_id=? WHERE student_code='RAG-CUR-A'", facultyId, departmentA, programA);
        db.update("UPDATE students SET faculty_id=?, department_id=?, program_id=? WHERE student_code='RAG-CUR-B'", facultyId, departmentB, programB);
        var search = new LlmResponse(null, "scripted-model", null,
                List.of(new LlmToolCall("s", "search_university_knowledge", Map.of("query", ELECTIVES))), null);
        script.add(search);
        script.add(new LlmResponse("See the curriculum.", "scripted-model", null));
        var bodyA = chat(a, "What electives are available?");
        var passages = (List<Map<String, Object>>) ((Map<String, Object>) sent.getLast().toolRounds().getLast().results().getLast().result()).get("data");
        assertThat(passages).hasSize(2).allSatisfy(p -> {
            assertThat(p).containsEntry("appliesTo", "your program");
            assertThat(p).doesNotContainKeys("neighbor", "documentId", "score", "rank");
        });
        assertThat((String) passages.get(0).get("text")).startsWith("Section 4 Major Elective Courses");
        assertThat((String) passages.get(1).get("text")).startsWith("Course code | Course title | Credits");
        assertThat(bodyA.get("sources")).hasSize(1);
        assertThat(output.getOut()).contains("ASSISTANT_KNOWLEDGE requestId=" + bodyA.get("requestId").asString())
                .contains("programPassages=2 neighborPassages=1");

        sent.clear();
        script.add(search);
        var bodyB = chat(b, "What electives are available?");
        assertThat(bodyB.get("message").asString()).isEqualTo(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
        assertThat(sent).hasSize(1);
    }
}
