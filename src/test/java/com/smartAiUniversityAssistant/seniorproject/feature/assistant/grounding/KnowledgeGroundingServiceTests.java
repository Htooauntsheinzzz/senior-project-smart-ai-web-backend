package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.KnowledgeEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec file 53, section 20.3: capture and evaluate for knowledge evidence. */
class KnowledgeGroundingServiceTests {
    private final SafeAcademicResponseFactory responses = new SafeAcademicResponseFactoryImpl();
    private final AcademicGroundingService service = new AcademicGroundingServiceImpl(new AcademicGroundingPolicyImpl(), responses,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private static final LlmToolCall CALL = new LlmToolCall("c", TOOL, Map.of("query", "minimum credits"));

    @Test
    void captureRows() {
        var found = service.capture(CALL, AcademicAiToolResult.success(TOOL, List.of(regulation(null, "x")), 1));
        assertThat(found.status()).isEqualTo(VERIFIED);
        assertThat(found.verifiedData()).isEqualTo(List.of(regulation(null, "x")));
        assertThat(found.requestedIdentifier()).isEqualTo("minimum credits");
        var none = service.capture(CALL, AcademicAiToolResult.success(TOOL, List.of(), 0));
        assertThat(none.status()).isEqualTo(VERIFIED_EMPTY);
        assertThat(none.verifiedData()).isEqualTo(List.of());
        var invalid = service.capture(CALL, AcademicAiToolResult.failure(TOOL, "INVALID_AI_TOOL_ARGUMENTS", "m"));
        assertThat(invalid.status()).isEqualTo(ERROR);
        assertThat(invalid.requestedIdentifier()).isNull();
        assertThat(service.capture(CALL, AcademicAiToolResult.failure(TOOL, "SOMETHING_ELSE", "m")).status()).isEqualTo(ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KNOWLEDGE_SEARCH_UNAVAILABLE", "KNOWLEDGE_SEARCH_RATE_LIMITED", "KNOWLEDGE_SEARCH_NOT_CONFIGURED",
            "AI_TOOL_EXECUTION_FAILED"})
    void searchFailuresAreUnavailable(String code) {
        var e = service.capture(CALL, AcademicAiToolResult.failure(TOOL, code, "m"));
        assertThat(e.status()).isEqualTo(UNAVAILABLE);
        assertThat(e.verifiedData()).isNull();
    }

    @Test
    void requestedIdentifierIsTheCleanedQuery() {
        var call = new LlmToolCall("c", TOOL, Map.of("query", " is\u0007CS301\nrequired " + "y".repeat(600)));
        String identifier = service.capture(call, AcademicAiToolResult.success(TOOL, List.of(), 0)).requestedIdentifier();
        assertThat(identifier).startsWith("is CS301 required").hasSize(500).doesNotContain("\n");
    }

    private AcademicGroundingDecision evaluate(AcademicEvidence... evidence) {
        return service.evaluate(null, List.of(evidence));
    }

    @Test
    void evaluateRows() {
        var verified = found("q", regulation(null, "x"));
        var courses = database("get_my_courses", VERIFIED, List.of(Map.of("courseCode", "CS301")));
        var noCourses = database("get_my_courses", VERIFIED_EMPTY, List.of());
        var dbDown = database("get_my_courses", UNAVAILABLE, null);
        assertThat(evaluate(verified).allowedToAnswer()).isTrue();
        assertThat(evaluate(empty()).safeMessage()).isEqualTo(responses.knowledgeNotFound());
        assertThat(evaluate(empty(), empty()).safeMessage()).isEqualTo(responses.knowledgeNotFound());
        assertThat(evaluate(unavailable()).safeMessage()).isEqualTo(responses.knowledgeUnavailable());
        assertThat(evaluate(unavailable()).reasonCode()).isEqualTo(AcademicGroundingDecision.ACADEMIC_VERIFICATION_FAILED);
        assertThat(evaluate(empty(), unavailable()).safeMessage()).isEqualTo(responses.knowledgeUnavailable());
        assertThat(evaluate(unavailable(), courses).allowedToAnswer()).isTrue();
        assertThat(evaluate(verified, dbDown).safeMessage()).isEqualTo(responses.academicServiceUnavailable());
        assertThat(evaluate(empty(), courses).allowedToAnswer()).isTrue();
        assertThat(evaluate(empty(), noCourses).safeMessage())
                .isEqualTo(responses.enrollmentUnavailable() + "\n\n" + responses.knowledgeNotFound());
        assertThat(evaluate(noCourses, empty()).safeMessage())
                .isEqualTo(responses.enrollmentUnavailable() + "\n\n" + responses.knowledgeNotFound());
        assertThat(evaluate(error()).allowedToAnswer()).isTrue();
        assertThat(evaluate(error(), empty()).safeMessage()).isEqualTo(responses.knowledgeNotFound());
        assertThat(evaluate(noCourses).safeMessage()).isEqualTo(responses.enrollmentUnavailable());
    }
}
