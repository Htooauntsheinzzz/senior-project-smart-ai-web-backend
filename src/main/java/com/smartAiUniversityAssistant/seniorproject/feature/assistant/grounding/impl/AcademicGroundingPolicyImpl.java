package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicGroundingPolicy;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntent;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.PersonalAcademicQuestionDetector;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Every Step 8 intent and every one of the eight academic tools is personal or current university data, so all of
 * them need evidence. The message rule reuses Step 7/8's PersonalAcademicQuestionDetector. Date-relative schedule
 * questions are recognised by a small English/Thai/Myanmar phrase list: "next class" style phrases on their own,
 * relative days ("today", "tomorrow") only in a personal academic question about classes.
 */
@Component
public class AcademicGroundingPolicyImpl implements AcademicGroundingPolicy {
    private static final Set<String> ACADEMIC_TOOLS = Set.of(GET_MY_ACADEMIC_PROFILE, GET_MY_ENROLLMENTS, GET_MY_COURSES,
            GET_MY_SCHEDULE, GET_MY_COURSE_SECTION, GET_MY_COURSE_LECTURER, GET_COURSE_INFORMATION, GET_SECTION_AVAILABILITY);
    private static final Pattern NEXT_CLASS = Pattern.compile(
            "(?<![a-z])(next|upcoming|following)\\s+(class|classes|lecture|lectures|lesson|lessons|session|period)(?![a-z])"
            + "|(?<![a-z])(which|what)\\s+(class|lecture|course)\\w*\\s+(starts?|begins?|is)\\s+(first|next)(?![a-z])");
    private static final Pattern RELATIVE_DAY = Pattern.compile(
            "(?<![a-z])(today|tonight|tomorrow|tmr|tmrw|yesterday|this\\s+(morning|afternoon|evening))(?![a-z])");
    // A relative day only matters for class times: "Is my section full today?" is a seat question, not a schedule one.
    private static final Pattern CLASS_TERM = Pattern.compile(
            "(?<![a-z])(class|classes|lecture|lectures|lesson|lessons|schedule|timetable|study|studying|have|course|courses|"
            + "subject|subjects)(?![a-z])");
    private static final List<String> NEXT_CLASS_OTHER = List.of("คาบต่อไป", "คาบถัดไป", "วิชาถัดไป", "วิชาต่อไป",
            "နောက်အတန်း", "နောက်ထပ်အတန်း");
    private static final List<String> RELATIVE_DAY_OTHER = List.of("พรุ่งนี้", "วันนี้", "เมื่อวาน",
            "မနက်ဖြန်", "ဒီနေ့", "ယနေ့");

    @Override
    public boolean requiresAcademicEvidence(String academicIntent) {
        if (academicIntent == null) return false;
        return Arrays.stream(AcademicIntent.values()).anyMatch(i -> i.name().equals(academicIntent)
                && AcademicIntentCatalog.of(i) != null);
    }

    @Override
    public boolean messageRequiresAcademicEvidence(String message) {
        return PersonalAcademicQuestionDetector.requiresAcademicEvidence(message);
    }

    @Override
    public boolean toolRequiresGrounding(String toolName) {
        return toolName != null && ACADEMIC_TOOLS.contains(toolName);
    }

    @Override
    public boolean isUnsupportedScheduleQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        if (NEXT_CLASS.matcher(text).find() || NEXT_CLASS_OTHER.stream().anyMatch(text::contains)) return true;
        boolean relativeDay = RELATIVE_DAY.matcher(text).find() && CLASS_TERM.matcher(text).find()
                || RELATIVE_DAY_OTHER.stream().anyMatch(text::contains);
        return relativeDay && PersonalAcademicQuestionDetector.isPersonalAcademic(message);
    }
}
