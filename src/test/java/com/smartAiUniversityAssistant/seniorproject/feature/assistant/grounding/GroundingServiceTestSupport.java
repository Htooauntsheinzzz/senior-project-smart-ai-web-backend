package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.AssistantTestServices;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolExecutor;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.ConversationContextService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

/**
 * Gemini stub tests (spec §46): the real chat service and grounding layer with a scripted LlmClient and a mocked tool
 * executor, so each test states exactly what the model said and what the backend returned. No provider, database or key.
 */
abstract class GroundingServiceTestSupport {
    static final long STUDENT_ID = 905_317L;
    static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    final CurrentStudentProvider currentStudent = mock(CurrentStudentProvider.class);
    final StudentAcademicService academics = mock(StudentAcademicService.class);
    final LlmClient llm = mock(LlmClient.class);
    final AcademicAiToolExecutor toolExecutor = mock(AcademicAiToolExecutor.class);
    final ConversationContextService conversations = mock(ConversationContextService.class);
    final AiAssistantProperties properties = new AiAssistantProperties("test-provider", "test-only-key", "configured-model",
            "https://provider.example.test/v1", 30, 10, 0.2, 1024, 1, Duration.ZERO, 4, 8, 120);
    StudentAssistantServiceImpl service = AssistantTestServices.service(currentStudent, academics, llm, toolExecutor, properties,
            conversations, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void student() {
        when(currentStudent.getCurrentStudentId()).thenReturn(STUDENT_ID);
    }

    /** The model's turns in order: a String answers in text, an LlmToolCall[] requests tools. */
    void model(Object... turns) {
        var responses = new ArrayList<LlmResponse>();
        for (Object turn : turns)
            responses.add(turn instanceof String text ? new LlmResponse(text, "scripted-model", null)
                    : new LlmResponse(null, "scripted-model", null, List.of((LlmToolCall[]) turn), "opaque"));
        var stubbing = when(llm.chat(any()));
        for (LlmResponse r : responses) stubbing = stubbing.thenReturn(r);
    }

    static LlmToolCall[] tools(LlmToolCall... calls) { return calls; }

    static LlmToolCall call(String name, Object... args) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < args.length; i += 2) map.put((String) args[i], args[i + 1]);
        return new LlmToolCall("call-" + name, name, map);
    }

    String ask(String message) {
        return service.chat(new StudentAssistantChatRequest(message)).message();
    }

    List<LlmRequest> sent(int expected) {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(expected)).chat(captor.capture());
        return captor.getAllValues();
    }
}
