package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.HybridAcademicQuestionDetector.asksAboutOwnStanding;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProgressProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.AcademicGroundingPolicyImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.PersonalAcademicQuestionDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Spec file 57, sections 18.1 and 18.2: the hybrid detector, the policy and the settings. */
class HybridAcademicQuestionDetectorTests {
    private final AcademicGroundingPolicyImpl policy = new AcademicGroundingPolicyImpl();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"How many credits do I still need to graduate?", "How many credits do I need to graduate?",
            "How many credits are left in my program?", "Do I have enough credits for cooperative education?", "Am I eligible for co-op?",
            "Is my GPA high enough to graduate?", "Do I meet the GPA requirement?", "What GPA do I need to graduate?",
            "Have I met the graduation requirements?", "Can I graduate this year?", "When will I graduate?",
            "ต้องเรียนอีกกี่หน่วยกิตถึงจะจบ", "เกรดเฉลี่ยของฉันผ่านเกณฑ์ไหม", "ဘွဲ့ရဖို့ ခရက်ဒစ် ဘယ်လောက် လိုသေးလဲ",
            // five more per rule
            "Am I able to graduate in 2027?", "Am I going to graduate on time?", "When can I graduate?", "will i graduate", "CAN I GRADUATE",
            "Do I qualify for the internship?", "How close am I to the credit requirement?", "Is my cgpa enough for honours degree?",
            "How far am I from my degree?", "Which requirements am I still missing?",
            "ขาดกี่หน่วยกิต", "ขาดอีกกี่หน่วยกิตครับ", "ปีนี้จบได้ไหม", "จบได้หรือยัง", "ผ่านเกณฑ์หรือยัง",
            "หน่วยกิตพอไหม", "เกรดพอหรือยังที่จะไปสหกิจ", "มีสิทธิ์ไหมที่จะฝึกงาน", "หน่วยกิตครบไหม", "ครบหรือยังที่จะจบ",
            "ကျွန်တော် ဘွဲ့ရနိုင်မလား", "ဘွဲ့ရနိုင်ပါသလား", "ခရက်ဒစ် ကျန်သေးလား", "ဘွဲ့ အတွက် လိုသေးလား", "ခရက်ဒစ် ဘယ်လောက် လိုသေး"})
    void hybrid(String message) {
        assertThat(asksAboutOwnStanding(message)).isTrue();
        assertThat(policy.messageAsksAboutOwnStanding(message)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "What are the requirements for my program?", "What are the graduation requirements?",
            "How many credits have I earned?", "What is my GPA?", "How many credits does the program require?",
            "Graduates of this program need 132 credits", "Which courses do I need for my major?", "Which elective courses am I taking?",
            "I need help", "What does GPA mean?", "My credited metadata needle remainder", "I am credited with a remainder"})
    void notHybrid(String message) {
        assertThat(asksAboutOwnStanding(message)).isFalse();
    }

    /** Section 6.3 with both features active and hybrid answers on, computed as in the chat service. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            How many credits do I still need to graduate? | true  | true
            Can I graduate this year?                     | true  | true
            What are the requirements for my program?     | false | true
            What is my GPA?                               | false | false
            What are the graduation requirements?         | false | true
            """)
    void table6_3(String message, boolean hybrid, boolean searchRequired) {
        boolean h = policy.messageAsksAboutOwnStanding(message);
        boolean personal = PersonalAcademicQuestionDetector.requiresAcademicEvidence(message);
        boolean document = policy.messageRequiresKnowledgeEvidence(message) || h;
        boolean required = document && (policy.messageRequiresKnowledgeSearch(message) || !personal || h);
        assertThat(h).isEqualTo(hybrid);
        assertThat(required).isEqualTo(searchRequired);
    }

    @Test
    void settings() {
        assertThat(AssistantProgressSettings.of(true).hybridEnabled()).isFalse();
        assertThat(AssistantProgressSettings.of(true, true).hybridEnabled()).isTrue();
        assertThat(AssistantProgressSettings.of(false, true).hybridEnabled()).isFalse();
        assertThat(new AssistantProgressSettings(new AiAssistantProgressProperties(true, false)).hybridEnabled()).isFalse();
        assertThat(new AssistantProgressSettings(new AiAssistantProgressProperties(true, true)).hybridEnabled()).isTrue();
    }
}
