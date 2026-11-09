package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import java.time.*;

public record TimetableConflictMeeting(Long meetingId, Long courseSectionId, String courseCode, String courseName, String sectionNumber,
        CourseMeetingType meetingType, int dayOfWeek, @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo,
        Long roomId, String roomNumber, Long lecturerId, String lecturerName) {}
