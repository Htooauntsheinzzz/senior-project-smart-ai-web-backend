package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import java.time.*;

/** One row of the list view (spec file 42, section 38). */
public record TimetableMeetingListItem(Long id, Long courseSectionId, String courseCode, String courseName, String sectionNumber,
        Long facultyId, String facultyName, Long departmentId, String departmentName, Long lecturerId, String lecturerName,
        Long academicTermId, String academicYearLabel, String semesterName,
        int dayOfWeek, String dayName, @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Long campusId, String campusName, Long buildingId, String buildingName, Long roomId, String roomNumber, String legacyRoomLabel,
        CourseMeetingType meetingType, TimetableMeetingStatus status, LocalDate effectiveFrom, LocalDate effectiveTo) {}
