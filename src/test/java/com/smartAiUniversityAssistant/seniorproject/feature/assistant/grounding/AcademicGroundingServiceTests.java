package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec §21-24: evidence statuses from the executor's own results, and the decision before Gemini's final answer. */
class AcademicGroundingServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private final AcademicGroundingService grounding = new AcademicGroundingServiceImpl(new AcademicGroundingPolicyImpl(),
            new SafeAcademicResponseFactoryImpl(), Clock.fixed(NOW, ZoneOffset.UTC));

    private AcademicEvidence capture(String tool, Map<String, Object> args, AcademicAiToolResult result) {
        return grounding.capture(new LlmToolCall("c", tool, args), result);
    }

    @Test
    void successfulResultsAreVerifiedAndAnEmptyListIsVerifiedEmpty() {
        var lecturer = lecturer("CS301", "SEC-01", "Dr. John Smith");
        AcademicEvidence one = capture("get_my_course_lecturer", Map.of("courseIdentifier", "CS301"), ok("get_my_course_lecturer", lecturer));
        assertThat(one).isEqualTo(new AcademicEvidence("get_my_course_lecturer", VERIFIED, lecturer, NOW, null, "CS301"));
        assertThat(one.verified()).isTrue();
        AcademicEvidence empty = capture("get_my_enrollments", Map.of(), ok("get_my_enrollments", List.of()));
        assertThat(empty.status()).isEqualTo(VERIFIED_EMPTY);
        assertThat(empty.verified()).isTrue();
        assertThat(empty.requestedIdentifier()).isNull();
        assertThat(capture("get_section_availability", Map.of("courseSectionId", 1501), ok("get_section_availability", availability(40, 3)))
                .requestedIdentifier()).isEqualTo("1501");
    }

    @Test
    void failuresAreClassifiedSoMissingDataIsNeverConfusedWithAServiceFailure() {
        var args = Map.<String, Object>of("courseIdentifier", "XYZ999");
        assertThat(capture("get_course_information", args, failed("get_course_information", "COURSE_NOT_FOUND")).status()).isEqualTo(NOT_FOUND);
        assertThat(capture("get_my_course_lecturer", args, failed("get_my_course_lecturer", "STUDENT_COURSE_ENROLLMENT_NOT_FOUND")).status()).isEqualTo(NOT_FOUND);
        assertThat(capture("get_section_availability", Map.of("courseSectionId", 9), failed("get_section_availability", "COURSE_SECTION_NOT_FOUND")).status()).isEqualTo(NOT_FOUND);
        AcademicEvidence ambiguous = capture("get_my_course_lecturer", Map.of("courseIdentifier", "Programming"), ambiguous("get_my_course_lecturer"));
        assertThat(ambiguous.status()).isEqualTo(AMBIGUOUS);
        assertThat(ambiguous.verifiedData()).asList().hasSize(2);
        AcademicEvidence down = capture("get_my_enrollments", Map.of(), failed("get_my_enrollments", "AI_TOOL_EXECUTION_FAILED"));
        assertThat(down.status()).isEqualTo(UNAVAILABLE);
        assertThat(down.verified()).isFalse();
        assertThat(down.verifiedData()).isNull();
        assertThat(down.safeErrorCode()).isEqualTo("AI_TOOL_EXECUTION_FAILED");
        // A model mistake is an ERROR and carries none of the model's arguments.
        AcademicEvidence invalid = capture("get_my_enrollments", Map.of("studentId", 7), failed("get_my_enrollments", "INVALID_AI_TOOL_ARGUMENTS"));
        assertThat(invalid.status()).isEqualTo(ERROR);
        assertThat(invalid.requestedIdentifier()).isNull();
        assertThat(capture("run_sql", Map.of("courseIdentifier", "x"), failed("unsupported", "UNSUPPORTED_AI_TOOL")).status()).isEqualTo(ERROR);
    }

    private AcademicEvidence evidence(AcademicEvidenceStatus status, String code, Object data, String identifier) {
        return new AcademicEvidence("get_my_course_lecturer", status, data, NOW, code, identifier);
    }

    @Test
    void verifiedDataMayBePhrasedByGemini() {
        var decision = grounding.evaluate("GET_MY_COURSE_LECTURER", List.of(evidence(VERIFIED, null, lecturer("CS301", "SEC-01", null), "CS301")));
        assertThat(decision).isEqualTo(AcademicGroundingDecision.allow());
        // A partly failed multi-course question still goes to Gemini (the answer is validated afterwards).
        assertThat(grounding.evaluate(null, List.of(evidence(VERIFIED, null, Map.of("courseCode", "CS301"), "CS301"),
                evidence(NOT_FOUND, "STUDENT_COURSE_ENROLLMENT_NOT_FOUND", null, "CSC470"))).allowedToAnswer()).isTrue();
    }

    @Test
    void aServiceFailureFailsClosedEvenNextToVerifiedData() {
        var decision = grounding.evaluate("GET_MY_ENROLLMENTS", List.of(evidence(VERIFIED, null, Map.of("courseCode", "CS301"), "CS301"),
                evidence(UNAVAILABLE, "AI_TOOL_EXECUTION_FAILED", null, null)));
        assertThat(decision.allowedToAnswer()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(AcademicGroundingDecision.ACADEMIC_VERIFICATION_FAILED);
        assertThat(decision.safeMessage()).startsWith("I can't verify your academic information right now").doesNotContain("no ");
    }

    @Test
    void ambiguityAsksWithTheBackendsCandidates() {
        var candidates = List.of(Map.of("courseCode", "CSC452", "courseName", "Mobile Programming"),
                Map.of("courseCode", "CSC453", "courseName", "Web Programming"));
        var decision = grounding.evaluate(null, List.of(evidence(AMBIGUOUS, "ACADEMIC_COURSE_AMBIGUOUS", candidates, "Programming")));
        assertThat(decision.clarificationRequired()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo(AcademicGroundingDecision.ASK_FOR_CLARIFICATION);
        assertThat(decision.safeMessage()).isEqualTo("I found more than one course matching \"Programming\":\n"
                + "1. CSC452 — Mobile Programming\n2. CSC453 — Web Programming\nWhich course do you mean?");
    }

    @Test
    void notFoundAndVerifiedEmptyAreInformationNotAvailable() {
        assertThat(grounding.evaluate(null, List.of(evidence(NOT_FOUND, "COURSE_NOT_FOUND", null, "XYZ999"))).safeMessage())
                .isEqualTo("I could not find a course matching \"XYZ999\" in the university catalogue.");
        assertThat(grounding.evaluate(null, List.of(evidence(NOT_FOUND, "STUDENT_COURSE_ENROLLMENT_NOT_FOUND", null, "CSC470"))).safeMessage())
                .contains("No matching enrollment was found for your account for CSC470");
        assertThat(grounding.evaluate(null, List.of(evidence(NOT_FOUND, "COURSE_SECTION_NOT_FOUND", null, "999999"))).safeMessage())
                .isEqualTo("I could not find a course section with id 999999.");
        var empty = grounding.evaluate("GET_MY_ENROLLMENTS", List.of(evidence(VERIFIED_EMPTY, null, List.of(), null)));
        assertThat(empty.reasonCode()).isEqualTo(AcademicGroundingDecision.INFORMATION_NOT_AVAILABLE);
        assertThat(empty.safeMessage()).isEqualTo("No current active enrollments were found for your account.");
    }

    @Test
    void modelMistakesStayWithTheModelAndMissingEvidenceIsBlockedOnlyWhenRequired() {
        assertThat(grounding.evaluate(null, List.of(evidence(ERROR, "INVALID_AI_TOOL_ARGUMENTS", null, null))).allowedToAnswer()).isTrue();
        var missing = grounding.evaluate("GET_MY_PROGRAM", List.of());
        assertThat(missing.allowedToAnswer()).isFalse();
        assertThat(missing.reasonCode()).isEqualTo(AcademicGroundingDecision.BLOCK_UNSUPPORTED_CLAIM);
        assertThat(missing.safeMessage()).isEqualTo(SafeAcademicResponseFactoryImpl.MISSING_EVIDENCE);
        assertThat(grounding.evaluate(null, List.of()).allowedToAnswer()).isTrue();
    }
}
