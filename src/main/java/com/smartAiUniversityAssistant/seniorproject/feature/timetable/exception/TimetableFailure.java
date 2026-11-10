package com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception;

/** Timetable and academic-term errors in the project's ApiError format (spec file 42, section 57). */
public class TimetableFailure extends RuntimeException {
    private final int status;
    private final String code;
    public TimetableFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }

    public static TimetableFailure invalid() { return new TimetableFailure(400, "VALIDATION_ERROR", "The request is invalid."); }
    public static TimetableFailure invalid(String message) { return new TimetableFailure(400, "VALIDATION_ERROR", message); }

    public static TimetableFailure termNotFound() { return new TimetableFailure(404, "ACADEMIC_TERM_NOT_FOUND", "The requested academic term does not exist."); }
    public static TimetableFailure sectionWithoutTerm() {
        return new TimetableFailure(400, "ACADEMIC_TERM_NOT_FOUND", "The course section has no academic term. Assign one before scheduling it.");
    }
    public static TimetableFailure termClosed() { return new TimetableFailure(400, "ACADEMIC_TERM_CLOSED", "The academic term is closed; its timetable can no longer change."); }
    public static TimetableFailure termInUse() { return new TimetableFailure(409, "ACADEMIC_TERM_IN_USE", "Course sections still reference this academic term."); }
    public static TimetableFailure termDuplicate() { return new TimetableFailure(409, "ACADEMIC_TERM_ALREADY_EXISTS", "This semester already has a term for that academic year code."); }
    public static TimetableFailure semesterNotFound() { return new TimetableFailure(400, "SEMESTER_NOT_FOUND", "The semester does not exist or is deleted."); }

    public static TimetableFailure sectionNotFound() { return new TimetableFailure(404, "COURSE_SECTION_NOT_FOUND", "The requested course section does not exist."); }
    public static TimetableFailure sectionInactive() { return new TimetableFailure(400, "COURSE_SECTION_INACTIVE", "The course section is not active, or its course or lecturer is inactive."); }
    public static TimetableFailure meetingNotFound() { return new TimetableFailure(404, "TIMETABLE_MEETING_NOT_FOUND", "The requested timetable meeting does not exist."); }
    public static TimetableFailure exceptionNotFound() { return new TimetableFailure(404, "TIMETABLE_EXCEPTION_NOT_FOUND", "The requested timetable exception does not exist."); }

    public static TimetableFailure invalidTime() { return new TimetableFailure(400, "TIMETABLE_INVALID_TIME", "The end time must be after the start time."); }
    public static TimetableFailure invalidDateRange(String message) { return new TimetableFailure(400, "TIMETABLE_INVALID_DATE_RANGE", message); }
    public static TimetableFailure invalidEnrollmentDueDate() {
        return new TimetableFailure(400, "TIMETABLE_INVALID_ENROLLMENT_DUE_DATE", "The enrollment due date must not be after the effective start date.");
    }
    public static TimetableFailure invalidWeekday() { return new TimetableFailure(400, "TIMETABLE_INVALID_WEEKDAY", "The weekday must be 1 (Monday) to 7 (Sunday)."); }
    public static TimetableFailure roomNotFound() { return new TimetableFailure(400, "TIMETABLE_ROOM_NOT_FOUND", "The room does not exist or is deleted."); }
    public static TimetableFailure roomUnavailable(String message) { return new TimetableFailure(400, "TIMETABLE_ROOM_UNAVAILABLE", message); }
    public static TimetableFailure lecturerNotFound() { return new TimetableFailure(400, "TIMETABLE_LECTURER_NOT_FOUND", "The course section's lecturer does not exist or is inactive."); }

    public static TimetableFailure roomConflict() { return new TimetableFailure(409, "TIMETABLE_ROOM_CONFLICT", "The selected room is already scheduled during this interval."); }
    public static TimetableFailure lecturerConflict() { return new TimetableFailure(409, "TIMETABLE_LECTURER_CONFLICT", "The assigned lecturer has another class during this interval."); }
    public static TimetableFailure sectionConflict() { return new TimetableFailure(409, "TIMETABLE_SECTION_CONFLICT", "The course section already has a meeting during this interval."); }
    public static TimetableFailure publishNotAllowed(String message) { return new TimetableFailure(409, "TIMETABLE_PUBLISH_NOT_ALLOWED", message); }
    public static TimetableFailure exceptionInvalid(String message) { return new TimetableFailure(400, "TIMETABLE_EXCEPTION_INVALID", message); }
    public static TimetableFailure exceptionDuplicate() { return new TimetableFailure(409, "TIMETABLE_EXCEPTION_INVALID", "That occurrence already has an exception."); }
    public static TimetableFailure concurrentModification() {
        return new TimetableFailure(409, "TIMETABLE_CONCURRENT_MODIFICATION", "Another timetable change is in progress for the same room, lecturer or section. Try again.");
    }
}
