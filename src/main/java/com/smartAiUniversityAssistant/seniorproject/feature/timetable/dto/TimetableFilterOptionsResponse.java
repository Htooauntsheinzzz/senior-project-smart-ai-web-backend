package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.util.List;

public record TimetableFilterOptionsResponse(List<TimetableOption> academicTerms, List<TimetableOption> faculties,
        List<TimetableOption> departments, List<TimetableOption> courses, List<TimetableOption> courseSections,
        List<TimetableOption> lecturers, List<TimetableOption> campuses, List<TimetableOption> buildings, List<TimetableOption> rooms,
        List<String> meetingTypes, List<String> statuses) {}
