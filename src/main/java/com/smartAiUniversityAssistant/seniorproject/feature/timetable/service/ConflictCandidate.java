package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import java.time.*;
import java.util.List;

/**
 * A meeting as it would be after a save: the resources it uses and its weekly rule. excludeMeetingId keeps an edited
 * meeting from conflicting with its own stored row; exceptions are the live exceptions it will keep.
 */
public record ConflictCandidate(Long excludeMeetingId, long courseSectionId, long lectureId, Long roomId, int dayOfWeek,
        LocalTime startTime, LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo,
        List<TimetableMeetingException> exceptions) {}
