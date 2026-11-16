package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.UniversityKnowledgeQuestionDetector.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicGroundingPolicy;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.AcademicGroundingPolicyImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.PersonalAcademicQuestionDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec file 53, section 20.1: the two keyword groups of section 10.1 and the three chat values of section 10.3. */
class UniversityKnowledgeQuestionDetectorTests {
    private final AcademicGroundingPolicy policy = new AcademicGroundingPolicyImpl();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            What are the graduation requirements?            | true  | false
            What electives are available?                    | false | true
            What does the curriculum say about internships?  | true  | true
            What are the requirements for my program?        | true  | false
            How many credits do I still need to graduate?    | true  | false
            How many credits does the program have?          | false | true
            Which elective courses am I taking?              | false | true
            What is the late registration fee?               | true  | false
            Is there a scholarship for second-year students? | true  | false
            ค่าธรรมเนียมรักษาสถานภาพนักศึกษาเท่าไร             | true  | false
            วิชาเลือกมีอะไรบ้าง                                 | false | true
            What does GPA mean?                              | false | false
            What is a prerequisite?                          | false | false
            Hello, how are you?                              | false | false
            I'm fine, thanks                                 | false | false
            I graduated from high school in 2023             | false | false
            What courses am I enrolled in?                   | false | false
            """)
    void tableOfSection10_1(String message, boolean rules, boolean curriculum) {
        assertThat(asksAboutUniversityRules(message)).as("rules").isEqualTo(rules);
        assertThat(asksAboutCurriculumTopic(message)).as("curriculum").isEqualTo(curriculum);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // English
            "What is the refund policy?", "When is the add/drop deadline?", "Can I take a leave of absence?",
            "What happens on academic probation?", "Where is the student handbook?", "How do I apply for honours?",
            // Thai
            "ระเบียบการลงทะเบียนเป็นอย่างไร", "ค่าเทอมเท่าไร", "ทุนการศึกษามีอะไรบ้าง", "เงื่อนไขการจบมีอะไรบ้าง", "ขอลาพักได้ไหม",
            "ปฏิทินการศึกษาปีนี้",
            // Myanmar
            "စည်းမျဉ်းက ဘာလဲ", "ကျောင်းလခ ဘယ်လောက်လဲ", "ပညာသင်ဆု ရှိလား", "နောက်ဆုံးရက် ဘယ်တော့လဲ", "သင်ရိုးညွှန်းတမ်း ပြောပြပါ"})
    void moreRulesQuestions(String message) {
        assertThat(asksAboutUniversityRules(message)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // English
            "Do I need an internship?", "Is a practicum required?", "Which core courses are there?", "What is co-op?",
            "Tell me about general education", "What compulsory courses must I take?",
            // Thai
            "วิชาบังคับมีอะไร", "วิชาแกนของสาขา", "ฝึกงานกี่เดือน", "สหกิจศึกษาคืออะไร", "ปริญญานิพนธ์ทำตอนไหน",
            // Myanmar
            "ရွေးချယ်ဘာသာ ဘာတွေရှိလဲ", "မသင်မနေရ ဘာသာ", "အလုပ်သင် လိုလား", "အလုပ်သင်ကာလ", "ရွေးချယ်ဘာသာ အရေအတွက်"})
    void moreCurriculumQuestions(String message) {
        assertThat(asksAboutCurriculumTopic(message)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"I like coffee", "Please feed the cat", "My policyholder number", "A selective process",
            "Hello", "สวัสดีครับ", "မင်္ဂလာပါ", "How do I prepare for an exam?", "What is a credit hour?", "ขอบคุณครับ",
            "ကျေးဇူးတင်ပါတယ်"})
    void notADocumentQuestion(String message) {
        assertThat(asksAboutUniversityRules(message)).isFalse();
        assertThat(asksAboutCurriculumTopic(message)).isFalse();
    }

    @Test
    void wholeWordsInEnglish() {
        assertThat(asksAboutUniversityRules("I drink coffee")).isFalse();
        assertThat(asksAboutUniversityRules("feed")).isFalse();
        assertThat(asksAboutUniversityRules("policyholder")).isFalse();
        assertThat(asksAboutCurriculumTopic("selective")).isFalse();
        assertThat(asksAboutUniversityRules("Which FEE applies?")).isTrue();
        assertThat(asksAboutCurriculumTopic("an Elective")).isTrue();
    }

    @Test
    void rule3NeedsGraduateAndAPartner() {
        assertThat(asksAboutUniversityRules("I graduated from high school in 2023")).isFalse();
        assertThat(asksAboutUniversityRules("I want to graduate")).isFalse();
        assertThat(asksAboutUniversityRules("What GPA do I need to graduate?")).isTrue();
        assertThat(asksAboutUniversityRules("Graduates of this program need 132 credits")).isTrue();
        assertThat(asksAboutUniversityRules("Which   grades are required to graduate?")).isTrue();
    }

    @Test
    void rule2OfTheCurriculumTopic() {
        assertThat(asksAboutCurriculumTopic("How many credits does the program have?")).isTrue();
        assertThat(asksAboutCurriculumTopic("How many credits is CS301?")).isFalse();
        assertThat(asksAboutCurriculumTopic("credits for the degree")).isTrue();
        assertThat(asksAboutUniversityRules("required by the curriculum")).isTrue();
        assertThat(asksAboutUniversityRules("the requirements of this major")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankMessages(String message) {
        assertThat(asksAboutUniversityRules(message)).isFalse();
        assertThat(asksAboutCurriculumTopic(message)).isFalse();
        assertThat(policy.messageRequiresKnowledgeEvidence(message)).isFalse();
        assertThat(policy.messageRequiresKnowledgeSearch(message)).isFalse();
    }

    @Test
    void policyMethods() {
        assertThat(policy.messageRequiresKnowledgeEvidence("What electives are available?")).isTrue();
        assertThat(policy.messageRequiresKnowledgeSearch("What electives are available?")).isFalse();
        assertThat(policy.messageRequiresKnowledgeEvidence("What is the late registration fee?")).isTrue();
        assertThat(policy.messageRequiresKnowledgeSearch("What is the late registration fee?")).isTrue();
        assertThat(policy.messageRequiresKnowledgeEvidence("What does GPA mean?")).isFalse();
        assertThat(policy.toolRequiresGrounding("search_university_knowledge")).isTrue();
    }

    /** Section 10.3, computed exactly as in the chat service (the reference resolver adds nothing without context). */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            What are the graduation requirements?            | false | true  | true
            What electives are available?                    | false | true  | true
            What does the curriculum say about internships?  | false | true  | true
            What are the requirements for my program?        | true  | true  | true
            How many credits do I still need to graduate?    | true  | true  | true
            Which elective courses am I taking?              | true  | true  | false
            What courses am I enrolled in?                   | true  | false | false
            What does GPA mean?                              | false | false | false
            """)
    void chatValuesOfSection10_3(String message, boolean personal, boolean document, boolean searchRequired) {
        boolean personalEvidence = PersonalAcademicQuestionDetector.requiresAcademicEvidence(message);
        boolean documentEvidence = policy.messageRequiresKnowledgeEvidence(message);
        boolean required = documentEvidence && (policy.messageRequiresKnowledgeSearch(message) || !personalEvidence);
        assertThat(personalEvidence).as("personal").isEqualTo(personal);
        assertThat(documentEvidence).as("document").isEqualTo(document);
        assertThat(required).as("searchRequired").isEqualTo(searchRequired);
    }
}
