package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

/** A dropdown entry; parentId drives the cascades faculty -> department -> course -> section and campus -> building -> room. */
public record TimetableOption(Long id, String code, String name, Long parentId) {}
