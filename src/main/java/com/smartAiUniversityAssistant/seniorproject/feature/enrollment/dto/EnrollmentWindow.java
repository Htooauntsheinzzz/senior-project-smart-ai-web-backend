package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import java.time.LocalDate;

/**
 * Whether a Course Section can take new enrollments (spec file 44). scheduled: it has a published timetable meeting.
 * dueDate: the earliest enrollment due date of its published meetings, null when none is set (no deadline).
 * closed: today, on the university calendar, is after dueDate.
 */
public record EnrollmentWindow(boolean scheduled, LocalDate dueDate, boolean closed) {
    public static final EnrollmentWindow UNSCHEDULED = new EnrollmentWindow(false, null, false);
    public boolean open() { return scheduled && !closed; }
}
