package com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent;

/**
 * The ten academic question categories the assistant supports in version 1 (Step 8). Internal only: the chat API has
 * no intent field, and the model's function calls (not this enum) decide which tool runs. See AcademicIntentCatalog.
 */
public enum AcademicIntent {
    GET_MY_PROFILE,
    GET_MY_ENROLLMENTS,
    GET_MY_COURSES,
    GET_MY_SCHEDULE,
    GET_MY_COURSE_SECTION,
    GET_MY_COURSE_LECTURER,
    GET_COURSE_INFORMATION,
    GET_COURSE_CREDITS,
    GET_SECTION_AVAILABILITY,
    GET_MY_PROGRAM
}
