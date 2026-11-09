package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import java.time.*;

/**
 * One dated occurrence in the requested week. occurrenceStatus: REGULAR, RESCHEDULED (shown at the new slot, with
 * originalDate) or CANCELLED (admin view only, at the original slot).
 */
public record TimetableCalendarEvent(Long meetingId, Long courseSectionId, String courseCode, String courseName, String sectionNumber,
        Long lecturerId, String lecturerName, Long facultyId, Long departmentId,
        Long roomId, String roomNumber, Long buildingId, String buildingName, Long campusId,
        CourseMeetingType meetingType, LocalDate date, OffsetDateTime startsAt, OffsetDateTime endsAt,
        TimetableMeetingStatus status, String occurrenceStatus, Long exceptionId, LocalDate originalDate) {}
