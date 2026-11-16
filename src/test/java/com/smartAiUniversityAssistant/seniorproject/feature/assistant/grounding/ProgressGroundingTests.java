package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.KnowledgeEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec file 56, sections 17.3 to 17.5: policy, grounding, answer check and replies for progress evidence. */
class ProgressGroundingTests {
    static final String PROGRESS = "get_my_academic_progress";
    private final SafeAcademicResponseFactory responses = new SafeAcademicResponseFactoryImpl();
    private final AcademicGroundingPolicy policy = new AcademicGroundingPolicyImpl();
    private final AcademicGroundingService grounding = new AcademicGroundingServiceImpl(policy, responses, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final GroundedAnswerValidator validator = new GroundedAnswerValidatorImpl();

    static Map<String, Object> progress(String cgpa, String earned, Integer courses, Integer current) {
        return map("cgpa", cgpa == null ? null : new BigDecimal(cgpa), "creditsEarned", earned == null ? null : new BigDecimal(earned),
                "currentCourses", courses, "currentEnrolledCredits", current, "studyYear", 3, "academicYear", 2026,
                "dataStatus", cgpa == null ? "PARTIAL" : "AVAILABLE");
    }

    static AcademicEvidence evidence(Map<String, Object> data) {
        return new AcademicEvidence(PROGRESS, VERIFIED, data, Instant.EPOCH, null, null);
    }

    static final AcademicEvidence A = evidence(progress("2.57", "18", 3, 9));

    @Test
    void policyAndGrounding() {
        assertThat(policy.toolRequiresGrounding(PROGRESS)).isTrue();
        var call = new LlmToolCall("c", PROGRESS, Map.of());
        assertThat(grounding.capture(call, AcademicAiToolResult.success(PROGRESS, progress(null, null, null, null), 1)).status()).isEqualTo(VERIFIED);
        var down = grounding.capture(call, AcademicAiToolResult.failure(PROGRESS, "AI_TOOL_EXECUTION_FAILED", "m"));
        assertThat(down.status()).isEqualTo(UNAVAILABLE);
        assertThat(grounding.evaluate(null, List.of(down)).safeMessage()).isEqualTo(responses.academicServiceUnavailable());
        assertThat(grounding.evaluate(null, List.of(found("q", regulation(null, ARTICLE_11)), down)).safeMessage())
                .isEqualTo(responses.academicServiceUnavailable());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Your cumulative GPA is 2.57.                          | -
            Your GPA is 2.6.                                      | GPA
            You have earned 18 credits.                           | -
            You have earned 21 credits.                           | CREDIT_HOURS
            You are taking 9 credits this semester.               | -
            You need 120 credits to graduate.                     | CREDIT_HOURS
            Your GPA is 2.57, so you are eligible to graduate.    | STANDING
            """)
    void table10_2(String answer, String issue) {
        var result = validator.validate(answer, List.of(A));
        if ("-".equals(issue)) assertThat(result.valid()).as(result.issues().toString()).isTrue();
        else assertThat(result.issues()).containsExactly(issue);
    }

    @Test
    void nullGpaAndOutcomes() {
        var none = evidence(progress(null, null, 3, 9));
        var result = validator.validate("Your GPA is 3.00.", List.of(none));
        assertThat(result.issues()).containsExactly("GPA");
        assertThat(result.outcome()).isEqualTo(GroundingValidationResult.Outcome.UNSUPPORTED_ACADEMIC_CLAIM);
        assertThat(validator.validate("Your GPA is 2.6.", List.of(A)).outcome()).isEqualTo(GroundingValidationResult.Outcome.INCONSISTENT_ACADEMIC_VALUE);
        assertThat(validator.validate("Your GPA is 2.57, so you are eligible.", List.of(A)).outcome())
                .isEqualTo(GroundingValidationResult.Outcome.UNSUPPORTED_ACADEMIC_CLAIM);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"You are eligible to graduate.", "You're now eligible for cooperative education.",
            "You have met all graduation requirements.", "You meet every requirement.", "You have completed all major required courses.",
            "You will graduate next year.", "You can graduate this semester.", "You're on track to graduate.",
            "คุณมีสิทธิ์สำเร็จการศึกษา", "คุณมีสิทธิ์จบการศึกษา", "คุณจะจบการศึกษาในปีหน้า", "คุณจบการศึกษาได้", "คุณผ่านเกณฑ์ครบ",
            "ครบทุกเงื่อนไขแล้ว"})
    void standingClaims(String text) {
        assertThat(validator.validate(text, List.of(A)).issues()).contains("STANDING");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Before you can graduate, you need at least 132 credits.", "I can't tell whether you are eligible.",
            "You are not yet eligible.", "Eligibility is decided by the registrar."})
    void notStanding(String text) {
        assertThat(validator.validate(text, List.of(A, found("q", regulation(null, "A student needs at least 132 credits."))))
                .issues()).doesNotContain("STANDING");
    }

    @Test
    void factsAndConditions() {
        var half = evidence(progress("2.57", "18.5", 3, 9));
        assertThat(validator.validate("You have earned 18.5 credits.", List.of(half)).valid()).isTrue();
        assertThat(validator.validate("You have earned 18 credits.", List.of(half)).issues()).containsExactly("CREDIT_HOURS");
        assertThat(validator.validate("You have earned 5 credits.", List.of(half)).issues()).containsExactly("CREDIT_HOURS");
        for (var status : List.of(UNAVAILABLE, ERROR)) {
            var bad = new AcademicEvidence(PROGRESS, status, null, Instant.EPOCH, "X", null);
            var courses = new AcademicEvidence("get_my_courses", VERIFIED, List.of(enrollment("CS301", "Data Structures", "SEC-01")),
                    Instant.EPOCH, null, null);
            assertThat(validator.validate("You are eligible. You need 120 credits and a GPA of 2.00.", List.of(courses, bad)).valid()).isTrue();
        }
        assertThat(validator.validate("You are eligible to graduate.", List.of(found("q", regulation(null, ARTICLE_11)))).valid()).isTrue();
    }

    @Test
    void progressPartOfGroundedAnswer() {
        assertThat(responses.groundedAnswer(List.of(A))).isEqualTo("Your academic progress:\n- Cumulative GPA: 2.57\n"
                + "- Credits earned from recorded course results: 18\n- Courses this semester: 3\n- Credits this semester: 9\n- Study year: 3");
        assertThat(responses.groundedAnswer(List.of(evidence(progress(null, null, null, null)))))
                .contains("- Cumulative GPA: not currently available").contains("- Credits earned from recorded course results: not currently available")
                .contains("- Credits this semester: not currently available").doesNotContain(": 0");
        var profile = new AcademicEvidence("get_my_academic_profile", VERIFIED, profile("Computer Science", "Semester 1"), Instant.EPOCH, null, null);
        assertThat(responses.groundedAnswer(List.of(profile, A))).startsWith("Your academic profile:").endsWith("- Study year: 3")
                .contains("\n\nYour academic progress:");
        assertThat(responses.groundedAnswer(List.of(A, A)).split("Your academic progress:", -1)).hasSize(2);
    }
}
