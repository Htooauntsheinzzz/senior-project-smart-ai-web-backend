package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Step 15A (spec file 53, section 10.1): backend-side check whether a student's message needs document evidence, in
 * the style of PersonalAcademicQuestionDetector. Two groups: a rules topic exists only in an uploaded document
 * (regulations, fees, deadlines, graduation and program requirements), so the document search itself must run; a
 * curriculum topic (electives, internships, course groups, credits of a program) can also be a word of the Student's
 * own records. A conservative keyword list, not a classifier: a missed question is covered by prompt rule 17 alone.
 * English terms match as whole words, Thai and Myanmar terms when the text contains them. The Thai and Myanmar lists
 * still need a native speaker's review.
 */
public final class UniversityKnowledgeQuestionDetector {
    private static final Pattern RULE_TERMS_EN = words(List.of("regulation", "regulations", "rules", "policy", "policies",
            "handbook", "handbooks", "announcement", "announcements", "tuition", "fee", "fees", "refund", "refunds",
            "penalty", "penalties", "scholarship", "scholarships", "graduation", "deadline", "deadlines",
            "academic calendar", "withdraw", "withdrawal", "withdrawing", "add/drop", "add-drop", "add or drop",
            "leave of absence", "probation", "dismissal", "dismissed", "transfer credit", "transfer credits", "honors",
            "honours", "curriculum", "curricula", "study plan", "study plans", "program structure", "programme structure",
            "curriculum structure", "credit requirement", "credit requirements", "program requirement",
            "program requirements", "programme requirement", "programme requirements", "degree requirement",
            "degree requirements", "major requirement", "major requirements"));
    private static final List<String> RULE_TERMS_OTHER = List.of(
            // Thai
            "ระเบียบ", "ข้อบังคับ", "ประกาศ", "นโยบาย", "คู่มือ", "ค่าเทอม", "ค่าเล่าเรียน", "ค่าธรรมเนียม", "ค่าปรับ", "คืนเงิน",
            "ทุนการศึกษา", "สำเร็จการศึกษา", "จบการศึกษา", "วันสุดท้าย", "ปฏิทินการศึกษา", "ถอนรายวิชา", "เพิ่มถอน", "ลาพัก",
            "รอพินิจ", "พ้นสภาพ", "เทียบโอน", "เกียรตินิยม", "หลักสูตร", "แผนการศึกษา", "แผนการเรียน", "หน่วยกิตรวม",
            "หน่วยกิตตลอดหลักสูตร", "เงื่อนไขการจบ", "เกณฑ์การจบ",
            // Myanmar
            "စည်းမျဉ်း", "စည်းကမ်း", "မူဝါဒ", "လက်စွဲ", "ကြေညာချက်", "ကျောင်းလခ", "အခကြေးငွေ", "ဒဏ်ကြေး", "ပညာသင်ဆု",
            "ဘွဲ့ရ", "နောက်ဆုံးရက်", "သင်ရိုးညွှန်းတမ်း");
    private static final Pattern PROGRAM_REQUIREMENTS = Pattern.compile("(?<![a-z])(?:requirements?|required) (?:for|of|in|by) "
            + "(?:my|the|this|our) (?:program|programme|major|degree|curriculum)(?![a-z])");
    private static final Pattern GRADUATE = words(List.of("graduate", "graduates", "graduating", "graduated"));
    private static final Pattern GRADUATE_PARTNER = words(List.of("credit", "credits", "gpa", "gpax", "grade", "grades",
            "requirement", "requirements", "required", "need", "needs", "needed"));

    private static final Pattern CURRICULUM_TERMS_EN = words(List.of("elective", "electives", "internship", "internships",
            "cooperative education", "co-op", "practicum", "general education", "core course", "core courses",
            "required course", "required courses", "compulsory course", "compulsory courses"));
    private static final List<String> CURRICULUM_TERMS_OTHER = List.of(
            // Thai
            "วิชาเลือก", "วิชาบังคับ", "วิชาแกน", "วิชาเอก", "ศึกษาทั่วไป", "ฝึกงาน", "สหกิจ", "ปริญญานิพนธ์",
            // Myanmar
            "ရွေးချယ်ဘာသာ", "မသင်မနေရ", "အလုပ်သင်");
    private static final Pattern CREDIT = words(List.of("credit", "credits"));
    private static final Pattern PROGRAM = words(List.of("program", "programme", "degree", "major"));

    private UniversityKnowledgeQuestionDetector() {
    }

    /** A topic that only an uploaded document can answer: rules, fees, deadlines, graduation and program requirements. */
    public static boolean asksAboutUniversityRules(String message) {
        String text = normalize(message);
        if (text.isEmpty()) return false;
        if (RULE_TERMS_EN.matcher(text).find() || RULE_TERMS_OTHER.stream().anyMatch(text::contains)) return true;
        if (PROGRAM_REQUIREMENTS.matcher(text).find()) return true;
        return GRADUATE.matcher(text).find() && GRADUATE_PARTNER.matcher(text).find();
    }

    /** A curriculum topic that the database can also touch: electives, internships, course groups, credits of a program. */
    public static boolean asksAboutCurriculumTopic(String message) {
        String text = normalize(message);
        if (text.isEmpty()) return false;
        if (CURRICULUM_TERMS_EN.matcher(text).find() || CURRICULUM_TERMS_OTHER.stream().anyMatch(text::contains)) return true;
        return CREDIT.matcher(text).find() && PROGRAM.matcher(text).find();
    }

    private static Pattern words(List<String> terms) {
        return Pattern.compile("(?<![a-z])(?:" + terms.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")(?![a-z])");
    }

    private static String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
