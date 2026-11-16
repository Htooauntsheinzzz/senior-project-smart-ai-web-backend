package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolRegistry;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The eight Step 6 tools; Step 8 descriptions name the question categories each tool answers (AcademicIntentCatalog). Schemas are minimal and closed (additionalProperties=false); none has a student id,
 * because the Student always comes from the token. The executor enforces the same rules on whatever the model sends.
 * Step 15A: search_university_knowledge is the ninth tool, declared and supported only while the knowledge search is
 * active. Its only argument is the query; the Student scope always comes from the Student's own profile.
 * Step 16A: get_my_academic_progress (no argument) follows the eight database tools while the progress tool is active,
 * before the document search.
 */
@Component
public class AcademicAiToolRegistryImpl implements AcademicAiToolRegistry {
    static final int MAX_COURSE_IDENTIFIER_LENGTH = 100;
    static final int MAX_KNOWLEDGE_QUERY_LENGTH = 300;

    private static final List<AcademicAiToolDefinition> TOOLS = List.of(
            noArguments(GET_MY_ACADEMIC_PROFILE, "Get the authenticated student's academic profile: student code, name, "
                    + "faculty, department, program, current semester, academic year and enrollment year. Use it for "
                    + "profile questions and for \"What is my program / major / degree?\" (answer with programName)."),
            noArguments(GET_MY_ENROLLMENTS, "List the authenticated student's current (ACTIVE) enrollments with course, "
                    + "section, semester, lecturer and `meetings`, the section's published timetable. Use it for questions "
                    + "about enrollments or the sections the student is registered for; an empty list means no active enrollments."),
            noArguments(GET_MY_COURSES, "List the courses the authenticated student is currently taking, with credit "
                    + "hours, course type and section. Use it for \"What courses / subjects do I have?\"."),
            noArguments(GET_MY_SCHEDULE, "Get the authenticated student's current class schedule: for each course its "
                    + "section, lecturer and `meetings`, the published timetable with weekday, start and end time, effective "
                    + "dates, room number and building. Use it for timetable and \"when / where are my classes\" questions; "
                    + "an empty `meetings` list means no published class times. It does not apply one-day cancellations."),
            course(GET_MY_COURSE_SECTION, "Get the authenticated student's own section of one course they are enrolled "
                    + "in: section number, courseSectionId, lecturer and `meetings` (published timetable with weekday, times, "
                    + "room number and building). Use it for \"Which section am I in for X?\", \"Where / what room is my X "
                    + "class?\", and to find the courseSectionId of the student's own section before get_section_availability."),
            course(GET_MY_COURSE_LECTURER, "Get the lecturer of the authenticated student's own section of one course "
                    + "they are enrolled in. Use it for \"Who teaches X?\" and \"Who is my lecturer for X?\"; other "
                    + "sections of the same course may have other lecturers."),
            course(GET_COURSE_INFORMATION, "Get general catalogue information about any course: name, credit hours, "
                    + "description, course type, prerequisite text, faculty, department and program. Use it for \"What is "
                    + "X about?\", prerequisites, course type, and \"How many credits is X?\" (answer with creditHours). "
                    + "It does not show whether the student is enrolled."),
            new AcademicAiToolDefinition(GET_SECTION_AVAILABILITY, "Get seat availability of a course section by its "
                    + "courseSectionId: capacity, occupied seats, available seats and whether it is full. Use it for \"Is section "
                    + "N full?\" or seats left. The id is the courseSectionId from another tool result or a section id "
                    + "the student gave as a number; never derive it from a section label such as SEC-01.",
                    schema(Map.of("courseSectionId", Map.of("type", "integer", "minimum", 1,
                            "description", "The courseSectionId from another academic tool result, or the section id the student gave as a number.")), "courseSectionId")));
    private static final Set<String> NAMES = TOOLS.stream().map(AcademicAiToolDefinition::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    static final AcademicAiToolDefinition KNOWLEDGE_TOOL = new AcademicAiToolDefinition(SEARCH_UNIVERSITY_KNOWLEDGE,
            "Search the university's official documents that this student is allowed to read (regulations, announcements, "
            + "handbooks, the curriculum documents of the student's program, fee and calendar information) and return the most "
            + "relevant passages with their document title, academic year, whom each passage applies to (appliesTo), section and "
            + "pages. Use it for questions about university rules, requirements, fees, deadlines and procedures, and about the "
            + "student's curriculum: graduation requirements, total credits, program structure, required and elective courses, "
            + "internships and cooperative education. It does not return the student's own records or results: use the other tools "
            + "for those. An empty list means no relevant passage was found.",
            schema(Map.of("query", Map.of("type", "string", "maxLength", MAX_KNOWLEDGE_QUERY_LENGTH,
                    "description", "What to look for, as one short question or phrase in the language of the student's "
                            + "question, for example \"minimum credits to graduate\" or \"major elective courses\". Do not include "
                            + "the student's name or other personal data.")), "query"));
    static final AcademicAiToolDefinition PROGRESS_TOOL = noArguments(GET_MY_ACADEMIC_PROGRESS, "Get the authenticated student's "
            + "academic progress totals: cumulative grade point average (cgpa), credits earned from finalized course results "
            + "(creditsEarned), number of courses and credits in the current semester (currentCourses, currentEnrolledCredits), study "
            + "year and academic year. Use it for \"What is my GPA?\", \"How many credits have I earned?\" and \"How many credits am I "
            + "taking this semester?\", and together with search_university_knowledge when the student asks how they stand against a "
            + "requirement. A null value means not currently available. It does not list courses or grades and does not say whether "
            + "a requirement is met.");

    private final AssistantKnowledgeSettings knowledge;
    private final AssistantProgressSettings progress;

    /** The eight database tools only: knowledge search and progress not active (existing tests build the registry this way). */
    public AcademicAiToolRegistryImpl() {
        this(AssistantKnowledgeSettings.inactive());
    }

    /** Spec file 53 tests: progress not active. */
    public AcademicAiToolRegistryImpl(AssistantKnowledgeSettings knowledge) {
        this(knowledge, AssistantProgressSettings.inactive());
    }

    @Autowired
    public AcademicAiToolRegistryImpl(AssistantKnowledgeSettings knowledge, AssistantProgressSettings progress) {
        this.knowledge = knowledge;
        this.progress = progress;
    }

    @Override
    public List<AcademicAiToolDefinition> getAvailableTools() {
        boolean withProgress = progress.active();
        boolean withKnowledge = knowledge.active();
        if (!withProgress && !withKnowledge) return TOOLS;
        var all = new ArrayList<>(TOOLS);
        if (withProgress) all.add(PROGRESS_TOOL);
        if (withKnowledge) all.add(KNOWLEDGE_TOOL);
        return List.copyOf(all);
    }

    @Override
    public boolean isSupported(String toolName) {
        if (toolName == null) return false;
        return NAMES.contains(toolName) || SEARCH_UNIVERSITY_KNOWLEDGE.equals(toolName) && knowledge.active()
                || GET_MY_ACADEMIC_PROGRESS.equals(toolName) && progress.active();
    }

    private static AcademicAiToolDefinition noArguments(String name, String description) {
        return new AcademicAiToolDefinition(name, description, schema(Map.of()));
    }

    private static AcademicAiToolDefinition course(String name, String description) {
        return new AcademicAiToolDefinition(name, description, schema(Map.of("courseIdentifier", Map.of("type", "string",
                "maxLength", MAX_COURSE_IDENTIFIER_LENGTH,
                "description", "Course code or course name as the student wrote it, for example CS301 or Data Structures.")),
                "courseIdentifier"));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return Collections.unmodifiableMap(schema);
    }
}
