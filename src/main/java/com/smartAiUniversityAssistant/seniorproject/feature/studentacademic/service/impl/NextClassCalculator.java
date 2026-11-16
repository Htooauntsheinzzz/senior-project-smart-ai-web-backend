package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** The next weekly occurrence of a meeting that starts strictly after now and falls inside its effective dates. */
final class NextClassCalculator {
    private NextClassCalculator() {}

    static Optional<LocalDateTime> nextStart(int dayOfWeek, LocalTime start, LocalDate effectiveFrom, LocalDate effectiveTo,
            LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate date = (effectiveFrom.isAfter(today) ? effectiveFrom : today)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
        // Today's occurrence has already started (or starts this instant): it is no longer the next class.
        if (date.equals(today) && !start.isAfter(now.toLocalTime())) date = date.plusWeeks(1);
        return date.isAfter(effectiveTo) ? Optional.empty() : Optional.of(date.atTime(start));
    }
}
