package com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Static metadata for the ten Step 8 intents: which of the eight existing tools backs each one and what the answer
 * must focus on. No database access and no routing: Gemini's function calling still chooses the tools. Two intents
 * reuse another intent's tool (GET_MY_PROGRAM -> get_my_academic_profile, GET_COURSE_CREDITS -> get_course_information),
 * so a tool name alone never identifies the intent; the answer focus is what tells them apart. The focus lines are
 * added to the system prompt so the model keeps the student's requested focus.
 */
public final class AcademicIntentCatalog {

    /** What the tool call needs from the student's message. */
    public enum RequiredEntity { NONE, COURSE_IDENTIFIER, COURSE_SECTION_ID }

    /** STUDENT: the authenticated student's own records. CATALOGUE: general course or section data. */
    public enum PrivacyScope { STUDENT, CATALOGUE }

    /** backingTools: the tool that answers the intent; GET_SECTION_AVAILABILITY may first resolve the section id. */
    public record Definition(AcademicIntent intent, String purpose, List<String> backingTools, RequiredEntity requiredEntity,
            PrivacyScope privacyScope, String answerFocus) {}

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(AcademicIntent.GET_MY_PROFILE, "The student's own academic profile",
                    List.of(GET_MY_ACADEMIC_PROFILE), RequiredEntity.NONE, PrivacyScope.STUDENT,
                    "Academic profile (faculty, department, program, semester, academic year): get_my_academic_profile; "
                    + "give only the academic fields asked for, never account or contact details."),
            new Definition(AcademicIntent.GET_MY_ENROLLMENTS, "The student's current enrollment records",
                    List.of(GET_MY_ENROLLMENTS), RequiredEntity.NONE, PrivacyScope.STUDENT,
                    "Enrollments or registered sections: get_my_enrollments; list course, section and status as returned."),
            new Definition(AcademicIntent.GET_MY_COURSES, "The courses the student is taking",
                    List.of(GET_MY_COURSES), RequiredEntity.NONE, PrivacyScope.STUDENT,
                    "The student's course list: get_my_courses; list the courses, not enrollment records."),
            new Definition(AcademicIntent.GET_MY_SCHEDULE, "The student's stored class timetable",
                    List.of(GET_MY_SCHEDULE), RequiredEntity.NONE, PrivacyScope.STUDENT,
                    "Timetable: get_my_schedule; repeat the stored schedule text and room, never work out dates or the next class."),
            new Definition(AcademicIntent.GET_MY_COURSE_SECTION, "The student's own section of one course",
                    List.of(GET_MY_COURSE_SECTION), RequiredEntity.COURSE_IDENTIFIER, PrivacyScope.STUDENT,
                    "The student's section, room or class time for one course: get_my_course_section."),
            new Definition(AcademicIntent.GET_MY_COURSE_LECTURER, "The lecturer of the student's own section of one course",
                    List.of(GET_MY_COURSE_LECTURER), RequiredEntity.COURSE_IDENTIFIER, PrivacyScope.STUDENT,
                    "Who teaches a course (\"Who teaches CS301?\"): get_my_course_lecturer, the lecturer of the student's own section."),
            new Definition(AcademicIntent.GET_COURSE_INFORMATION, "General catalogue information about one course",
                    List.of(GET_COURSE_INFORMATION), RequiredEntity.COURSE_IDENTIFIER, PrivacyScope.CATALOGUE,
                    "What a course is about, its type or prerequisites: get_course_information; it does not show that "
                    + "the student is enrolled or eligible."),
            new Definition(AcademicIntent.GET_COURSE_CREDITS, "The credit hours of one course",
                    List.of(GET_COURSE_INFORMATION), RequiredEntity.COURSE_IDENTIFIER, PrivacyScope.CATALOGUE,
                    "Credits of a course: get_course_information; answer with the creditHours value only."),
            new Definition(AcademicIntent.GET_SECTION_AVAILABILITY, "Seat availability of one course section",
                    List.of(GET_SECTION_AVAILABILITY), RequiredEntity.COURSE_SECTION_ID, PrivacyScope.CATALOGUE,
                    "Whether a section is full or how many seats remain: get_section_availability with a courseSectionId; "
                    + "for the student's own section of a course, take the id from get_my_course_section first."),
            new Definition(AcademicIntent.GET_MY_PROGRAM, "The student's own program or major",
                    List.of(GET_MY_ACADEMIC_PROFILE), RequiredEntity.NONE, PrivacyScope.STUDENT,
                    "Program, major or degree: get_my_academic_profile; answer with the program name only, never the "
                    + "faculty or department name in its place."));
    private static final Map<AcademicIntent, Definition> BY_INTENT = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Definition::intent, d -> d));

    private AcademicIntentCatalog() {
    }

    public static List<Definition> all() { return DEFINITIONS; }

    public static Definition of(AcademicIntent intent) { return BY_INTENT.get(intent); }

    /**
     * Step 11 logs: the first intent (catalogue order) backed by this tool, or null. Inferred from the tool that ran,
     * not observed: get_my_academic_profile is reported as GET_MY_PROFILE and get_course_information as
     * GET_COURSE_INFORMATION even when the student asked for the program or the credits.
     */
    public static AcademicIntent primaryIntentForTool(String toolName) {
        return DEFINITIONS.stream().filter(d -> d.backingTools().contains(toolName)).map(Definition::intent).findFirst().orElse(null);
    }

    /** The focus lines as one prompt section, in catalogue order. */
    public static String promptSection() {
        return DEFINITIONS.stream().map(d -> "- " + d.answerFocus()).collect(Collectors.joining("\n"));
    }
}
