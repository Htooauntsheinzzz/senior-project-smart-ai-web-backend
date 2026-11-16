package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Step 16B (spec file 57, section 6.1): whether the student asks how they themselves stand against a requirement (a
 * credit total, a GPA, eligibility, graduation). A word list in the style of PersonalAcademicQuestionDetector, not a
 * classifier: a wrong true costs one database query and a search, a wrong false leaves the question to the flows of
 * spec files 53 and 56. The Thai and Myanmar lists still need a native speaker's review.
 */
public final class HybridAcademicQuestionDetector {
    private static final Pattern DIRECT = Pattern.compile("(?<![a-z])(can i|will i|am i able to|am i going to|when will i|when can i)\\s+graduate(?![a-z])");
    private static final Pattern FIRST_PERSON = Pattern.compile(
            "(?<![a-z])(i|me|my|mine|myself|i'm|i’m|am i|do i|did i|have i|can i|could i|should i|will i)(?![a-z])");
    private static final Pattern TOPIC = Pattern.compile("(?<![a-z])(credit|credits|gpa|gpax|cgpa|grade point average|graduate|graduates|"
            + "graduating|graduation|degree|requirement|requirements|cooperative education|co-op|coop|internship|internships)(?![a-z])");
    private static final Pattern STANDING = Pattern.compile("(?<![a-z])(need|needs|needed|left|remaining|remain|remains|still|enough|"
            + "eligible|eligibility|qualify|qualified|meet|meets|met|on track|how far|how close|missing)(?![a-z])");
    private static final List<String> THAI_PHRASES = List.of("อีกกี่หน่วยกิต", "ขาดกี่หน่วยกิต", "ขาดอีกกี่หน่วยกิต", "จบได้ไหม",
            "จบได้หรือยัง", "จะจบไหม", "ผ่านเกณฑ์ไหม", "ผ่านเกณฑ์หรือยัง");
    private static final List<String> THAI_ASKS = List.of("พอไหม", "พอหรือยัง", "มีสิทธิ์ไหม", "ครบไหม", "ครบหรือยัง");
    private static final List<String> THAI_TOPICS = List.of("หน่วยกิต", "เกรด", "สหกิจ", "ฝึกงาน", "จบ");
    private static final List<String> MYANMAR_LEFT = List.of("လိုသေး", "ကျန်သေး");
    private static final List<String> MYANMAR_TOPICS = List.of("ခရက်ဒစ်", "ဘွဲ့");

    private HybridAcademicQuestionDetector() {
    }

    /** Whether the student asks how they themselves stand against a requirement: a credit total, a GPA, eligibility, graduation. */
    public static boolean asksAboutOwnStanding(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) return false;
        if (DIRECT.matcher(text).find()) return true;
        if (FIRST_PERSON.matcher(text).find() && TOPIC.matcher(text).find() && STANDING.matcher(text).find()) return true;
        if (THAI_PHRASES.stream().anyMatch(text::contains)) return true;
        if (THAI_ASKS.stream().anyMatch(text::contains) && THAI_TOPICS.stream().anyMatch(text::contains)) return true;
        return text.contains("ဘွဲ့ရနိုင်")
                || MYANMAR_LEFT.stream().anyMatch(text::contains) && MYANMAR_TOPICS.stream().anyMatch(text::contains);
    }
}
