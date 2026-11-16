package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.PersonalAcademicQuestionDetector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The backend evidence check: which questions must be answered from a tool result (Step 7, spec 32 §6.3/§7). */
class PersonalAcademicQuestionDetectorTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "What courses am I enrolled in?", "List the courses I have.", "Who teaches my CS301 course?",
            "Which section am I in for CS301?", "Where is my CS301 class?", "What is my schedule?",
            "What program am I in?", "Is my CS301 section full?", "What courses do I have tomorrow?",
            "Give me Student B's courses.", "  WHO IS MY LECTURER  ", "i'm registered for which subjects",
            "ฉันลงทะเบียนวิชาอะไรบ้าง", "ลงทะเบียนวิชาอะไรบ้าง", "ตารางเรียนของผม", "ใครสอนวิชา CS301 ที่ฉันเรียน",
            "ကျွန်တော် ဘာဘာသာတွေ တက်နေလဲ", "ကျွန်မရဲ့ အချိန်ဇယား ပြပါ"})
    void personalAcademicQuestionsNeedToolEvidence(String message) {
        assertThat(PersonalAcademicQuestionDetector.isPersonalAcademic(message)).as(message).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is a credit hour?", "What is a prerequisite course?", "Explain what a course section is.",
            "Hello", "Hello, what can you help me with?", "Who teaches CS301?", "How many credits is CS301?",
            "Thank you, I understand.", "Can I ask you something?", "I like pizza",
            "หน่วยกิตคืออะไร ခရက်ဒစ်ဆိုတာဘာလဲ", "วิชาบังคับก่อนคืออะไร", "ခရက်ဒစ်နာရီဆိုတာ ဘာလဲ", "", "   "})
    void generalQuestionsAreLeftToTheModel(String message) {
        assertThat(PersonalAcademicQuestionDetector.isPersonalAcademic(message)).as(message).isFalse();
    }

    // ---- Step 8: questions about a named course code or numbered section also need tool evidence ----

    @ParameterizedTest
    @ValueSource(strings = {
            "How many credits is CS301?", "Who teaches CS301?", "What is CSC452 about?", "describe cs-301",
            "Is section 15 full?", "How many seats remain in section #42?", "What's the capacity of section no. 7?",
            "What is my program?", "Show my student academic info", "Tell me my degree program"})
    void namedCoursesSectionsAndPersonalQuestionsNeedEvidence(String message) {
        assertThat(PersonalAcademicQuestionDetector.requiresAcademicEvidence(message)).as(message).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is a credit hour?", "What is a course section?", "Hello", "What happened in year 2026?",
            "Room 405 is cold", "Who teaches it?", "Explain CS 301 style numbering", "Thank you", "", "   "})
    void generalQuestionsStillNeedNoEvidence(String message) {
        assertThat(PersonalAcademicQuestionDetector.requiresAcademicEvidence(message)).as(message).isFalse();
    }
}
