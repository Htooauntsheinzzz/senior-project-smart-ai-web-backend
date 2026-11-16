package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.AcademicGroundingPolicyImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec §20: when evidence is required, without relying on a model-supplied intent label alone. */
class AcademicGroundingPolicyTests {
    private final AcademicGroundingPolicy policy = new AcademicGroundingPolicyImpl();

    @Test
    void everyStep8IntentRequiresEvidenceButNoLabelOrAnUnknownOneDoesNot() {
        for (AcademicIntent intent : AcademicIntent.values()) assertThat(policy.requiresAcademicEvidence(intent.name())).as(intent.name()).isTrue();
        assertThat(policy.requiresAcademicEvidence(null)).isFalse();
        assertThat(policy.requiresAcademicEvidence("GENERAL_QUESTION")).isFalse();
        assertThat(policy.requiresAcademicEvidence("get_my_enrollments")).isFalse();
    }

    @Test
    void theMessageItselfTriggersGroundingWithoutAnyLabel() {
        for (String personal : List.of("What courses am I enrolled in?", "Who teaches CS301?", "How many credits is CS301?",
                "What program am I in?", "Is section 1501 full?", "ฉันเรียนวิชาอะไรบ้าง"))
            assertThat(policy.messageRequiresAcademicEvidence(personal)).as(personal).isTrue();
        for (String general : List.of("What is a credit hour?", "What does GPA mean?", "Hello"))
            assertThat(policy.messageRequiresAcademicEvidence(general)).as(general).isFalse();
    }

    @Test
    void everyAcademicToolResultMustBeGrounded() {
        for (String tool : List.of("get_my_academic_profile", "get_my_enrollments", "get_my_courses", "get_my_schedule",
                "get_my_course_section", "get_my_course_lecturer", "get_course_information", "get_section_availability"))
            assertThat(policy.toolRequiresGrounding(tool)).as(tool).isTrue();
        assertThat(policy.toolRequiresGrounding("run_sql")).isFalse();
        assertThat(policy.toolRequiresGrounding(null)).isFalse();
    }

    @Test
    void dateRelativeScheduleQuestionsAreUnsupported() {
        for (String unsupported : List.of("What is my next class?", "What classes do I have tomorrow?", "Do I have class today?",
                "Which lecture starts first?", "What's my upcoming lecture?", "พรุ่งนี้ฉันเรียนวิชาอะไร", "คาบถัดไปของฉันคืออะไร"))
            assertThat(policy.isUnsupportedScheduleQuestion(unsupported)).as(unsupported).isTrue();
        for (String supported : List.of("What is my schedule?", "When is my CS301 class?", "Is my CS301 section full today?",
                "What is a credit hour?", "What is the next step to enroll?", "Who teaches CS301?", ""))
            assertThat(policy.isUnsupportedScheduleQuestion(supported)).as(supported).isFalse();
        assertThat(policy.isUnsupportedScheduleQuestion(null)).isFalse();
    }
}
