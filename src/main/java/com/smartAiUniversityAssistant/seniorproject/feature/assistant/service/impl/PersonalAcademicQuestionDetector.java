package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Backend-side evidence check for Step 7: decides whether a student's message asks for their own (personal or
 * current) academic facts, which may only be answered from a tool result. A prompt rule alone cannot guarantee
 * that, so when the model answers such a question in text without having run any tool, the service retries once
 * requiring a tool and otherwise returns a safe "not retrieved" answer instead of the model's text.
 * <p>
 * This is a conservative keyword heuristic over English, Thai and Myanmar, not an intent classifier: a personal
 * question needs a first-person marker and an academic term (or one of a few phrases that imply both). General
 * questions such as "What is a credit hour?" are left to the model. A false positive costs one extra provider
 * request and yields a tool-grounded answer; a false negative leaves the prompt rules as the guard.
 */
public final class PersonalAcademicQuestionDetector {
    private static final Pattern FIRST_PERSON_EN = Pattern.compile(
            "(?<![a-z])(i|me|my|mine|myself|i'm|i’m|am i|do i|did i|have i|can i|could i|should i|will i)(?![a-z])");
    private static final Pattern ACADEMIC_TERM_EN = Pattern.compile(
            "(?<![a-z])(enrol\\w*|register\\w*|course\\w*|class\\w*|section\\w*|lecturer\\w*|teacher\\w*|professor\\w*|"
            + "instructor\\w*|teach\\w*|schedule\\w*|timetable\\w*|room\\w*|program\\w*|major\\w*|faculty|department|"
            + "semester\\w*|term|credit\\w*|prerequisite\\w*|seat\\w*|capacity|full|subject\\w*|study(ing)?|"
            + "grade\\w*|gpa|year|profile|academic\\w*|degree\\w*)(?![a-z])");
    // A course code as students write it (CS301, CSC452, cs-301) or a numbered section ("section 15", "section #15").
    // Spaced codes ("CS 301") are not matched: "year 2026" or "room 405" would then look like codes too.
    private static final Pattern COURSE_CODE = Pattern.compile("(?<![\\p{L}\\d])[a-z]{2,5}-?\\d{3,4}[a-z]?(?![\\p{L}\\d])");
    private static final Pattern NUMBERED_SECTION = Pattern.compile("(?<![a-z])section\\s*(no\\.?|number|#)?\\s*\\d+");
    private static final List<String> FIRST_PERSON_OTHER = List.of(
            // Thai
            "ฉัน", "ผม", "ดิฉัน", "หนู", "ข้าพเจ้า", "เรา", "กู", "ตัวเอง",
            // Myanmar
            "ကျွန်တော်", "ကျွန်မ", "ကျွန်ုပ်", "ငါ", "ကိုယ်");
    private static final List<String> ACADEMIC_TERM_OTHER = List.of(
            // Thai
            "วิชา", "คอร์ส", "ลงทะเบียน", "ตาราง", "อาจารย์", "ผู้สอน", "ห้อง", "เซค", "กลุ่มเรียน", "หลักสูตร", "สาขา",
            "คณะ", "ภาควิชา", "เทอม", "ภาคเรียน", "ภาคการศึกษา", "หน่วยกิต", "ที่นั่ง", "เต็ม", "เกรด", "เรียน", "สอน",
            // Myanmar
            "ဘာသာ", "သင်တန်း", "အတန်း", "ဆရာ", "အချိန်ဇယား", "အခန်း", "စာရင်းသွင်း", "တက်ရောက်", "ခရက်ဒစ်", "ဌာန",
            "ပရိုဂရမ်", "သင်ရိုး", "ကျောင်း", "အမှတ်");
    // Phrases that already imply the student is asking about their own records (Thai and Myanmar often drop pronouns).
    private static final List<String> PERSONAL_PHRASES = List.of(
            "enrolled in", "enrolled for", "registered for", "my schedule", "my timetable", "my courses", "my classes",
            "my section", "my lecturer", "my program", "my profile",
            "ลงทะเบียน", "เรียนอยู่", "ตารางเรียน", "ตารางสอน", "เรียนวิชาอะไร", "วิชาที่เรียน", "ลงวิชาอะไร", "ลงเรียน",
            "စာရင်းသွင်း", "တက်ရောက်နေ", "တက်နေ", "အချိန်ဇယား", "ယူထားတဲ့", "ယူထားသော");

    private PersonalAcademicQuestionDetector() {
    }

    /**
     * Step 8: whether the answer must rest on a tool result. True for a personal academic question and for any
     * question about a specific course code or numbered section ("How many credits is CS301?", "Is section 15 full?"),
     * whose facts must come from the catalogue rather than the model's memory. General questions stay false.
     */
    public static boolean requiresAcademicEvidence(String message) {
        return isPersonalAcademic(message) || namesCourseOrSection(message);
    }

    /** Step 10: whether the message names a course code or a numbered section itself, which overrides earlier context. */
    public static boolean namesCourseOrSection(String message) {
        String text = normalize(message);
        return COURSE_CODE.matcher(text).find() || NUMBERED_SECTION.matcher(text).find();
    }

    public static boolean isPersonalAcademic(String message) {
        if (message == null || message.isBlank()) return false;
        String text = normalize(message);
        if (PERSONAL_PHRASES.stream().anyMatch(text::contains)) return true;
        boolean firstPerson = FIRST_PERSON_EN.matcher(text).find() || FIRST_PERSON_OTHER.stream().anyMatch(text::contains);
        if (!firstPerson) return false;
        return ACADEMIC_TERM_EN.matcher(text).find() || ACADEMIC_TERM_OTHER.stream().anyMatch(text::contains);
    }

    private static String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
