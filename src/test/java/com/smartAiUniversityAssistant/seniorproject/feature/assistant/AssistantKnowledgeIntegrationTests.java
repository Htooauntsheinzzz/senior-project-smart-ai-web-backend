package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.TXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.IngestionTestSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeRealFiles;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/**
 * Spec file 53, section 20.7: the real chat endpoint, tool executor, semantic search, pgvector and Redis with
 * DeterministicEmbeddingClient as the EmbeddingClient bean (so the knowledge tool is active) and a scripted model.
 * The sample document is processed twice: university-wide and for program A only.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.knowledge.search.min-score=0.30")
class AssistantKnowledgeIntegrationTests extends IngestionTestSupport {
    static final String CHAT = "/api/v1/student/assistant/chat";
    static final String QUERY = "a student must complete at least 120 credits required by the curriculum";
    @MockitoBean LlmClient llm;
    final Deque<Function<LlmRequest, LlmResponse>> script = new ArrayDeque<>();
    final List<LlmRequest> sent = new ArrayList<>();
    long universityDoc, programDoc;
    String studentA, studentB;

    @BeforeEach
    void documentsAndStudents() throws Exception {
        universityDoc = upload("regulations.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        programDoc = upload("program-regulations.txt", TXT, KnowledgeRealFiles.utf8(sampleText()), Map.of("programId", String.valueOf(programA)));
        switchable.runOnce();
        switchable.runOnce();
        assertThat(statusOf(currentFile(universityDoc))).isEqualTo("READY");
        assertThat(statusOf(currentFile(programDoc))).isEqualTo("READY");
        studentA = studentToken("RAG-KA");
        studentB = studentToken("RAG-KB");
        db.update("UPDATE students SET faculty_id=?, department_id=?, program_id=? WHERE student_code='RAG-KA'", facultyId, departmentA, programA);
        db.update("UPDATE students SET faculty_id=?, department_id=?, program_id=? WHERE student_code='RAG-KB'", facultyId, departmentB, programB);
        script.clear();
        sent.clear();
        when(llm.chat(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            sent.add(request);
            var step = script.poll();
            assertThat(step).as("unexpected provider call").isNotNull();
            return step.apply(request);
        });
    }

    void search(String query) {
        script.add(r -> new LlmResponse(null, "scripted-model", null,
                List.of(new LlmToolCall("call-search", "search_university_knowledge", Map.of("query", query))), null));
    }

    void tool(String name) {
        script.add(r -> new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("call-" + name, name, Map.of())), null));
    }

    void answer(String text) { script.add(r -> new LlmResponse(text, "scripted-model", null)); }

    JsonNode chat(String token, String message) throws Exception {
        var response = mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("message", message)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertThat(body.get("sources").isArray()).isTrue();
        return body;
    }

    static List<String> sourceTitles(JsonNode body) {
        var titles = new ArrayList<String>();
        body.get("sources").forEach(s -> titles.add(s.get("title").asString()));
        return titles;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> passagesSent() {
        var result = (Map<String, Object>) sent.getLast().toolRounds().getLast().results().getLast().result();
        return (List<Map<String, Object>>) result.get("data");
    }

    @Test
    void scopeThroughTheWholeStack(CapturedOutput output) throws Exception {
        search(QUERY);
        answer("See the documents.");
        var a = chat(studentA, "What are the graduation requirements?");
        assertThat(passagesSent()).extracting(p -> p.get("documentTitle")).contains("Sample regulations.txt", "Sample program-regulations.txt");
        assertThat(sourceTitles(a)).containsExactlyInAnyOrder("Sample regulations.txt", "Sample program-regulations.txt");
        assertThat(a.get("sources").get(0).get("academicYear").asString()).isEqualTo("2026-2027");
        assertThat(output.getOut()).contains("ASSISTANT_KNOWLEDGE requestId=" + a.get("requestId").asString() + " searchRequired=true searches=1")
                .doesNotContain("required by the curriculum\"").doesNotContain("query=" + QUERY);

        sent.clear();
        search(QUERY);
        answer("See the documents.");
        var b = chat(studentB, "What are the graduation requirements?");
        assertThat(passagesSent()).isNotEmpty().allSatisfy(p -> assertThat(p.get("documentTitle")).isEqualTo("Sample regulations.txt"));
        assertThat(sourceTitles(b)).containsExactly("Sample regulations.txt");

        perform(patch(BASE + "/" + universityDoc + "/archive"), null).andExpect(status().isOk());
        sent.clear();
        search(QUERY);
        assertThat(chat(studentB, "What are the graduation requirements?").get("message").asString())
                .isEqualTo(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
        assertThat(sent).hasSize(1);
        perform(patch(BASE + "/" + universityDoc + "/activate"), null).andExpect(status().isOk());
        search(QUERY);
        answer("See the documents.");
        assertThat(sourceTitles(chat(studentB, "What are the graduation requirements?"))).containsExactly("Sample regulations.txt");
    }

    @Test
    void nothingFoundCostsOneProviderRequest() throws Exception {
        search("What are the opening hours of the swimming pool?");
        var body = chat(studentA, "What is the swimming pool policy?");
        assertThat(body.get("message").asString()).isEqualTo(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
        assertThat(body.get("sources")).isEmpty();
        assertThat(sent).hasSize(1);
    }

    @Test
    void searchIsDemandedAfterProfileOnly() throws Exception {
        tool("get_my_academic_profile");
        answer("Your program is Bachelor of Computer Science.");
        search(QUERY);
        answer("See the documents.");
        var body = chat(studentA, "What are the requirements for my program?");
        assertThat(sent).hasSize(4);
        assertThat(sent.get(2).requireTool()).isTrue();
        assertThat(sent.get(2).systemPrompt()).endsWith("from the database tools alone.");
        assertThat(sourceTitles(body)).isNotEmpty();
    }

    @Test
    void access() throws Exception {
        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"What are the graduation requirements?\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(CHAT).header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What are the graduation requirements?\"}")).andExpect(status().isForbidden());
        assertThat(sent).isEmpty();
    }
}
