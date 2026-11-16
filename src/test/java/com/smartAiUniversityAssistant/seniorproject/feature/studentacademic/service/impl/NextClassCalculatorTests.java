package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.*;
import org.junit.jupiter.api.Test;

class NextClassCalculatorTests {
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    // 2026-11-04 is a Wednesday.
    private static final LocalDateTime WEDNESDAY_8 = LocalDateTime.of(2026, 11, 4, 8, 0);

    private static LocalDateTime next(int day, LocalDateTime now) {
        return NextClassCalculator.nextStart(day, NINE, FROM, TO, now).orElseThrow();
    }

    @Test void laterToday() { assertThat(next(3, WEDNESDAY_8)).isEqualTo("2026-11-04T09:00"); }

    @Test void startedOrStartingNowMovesToNextWeek() {
        assertThat(next(3, LocalDateTime.of(2026, 11, 4, 9, 0))).isEqualTo("2026-11-11T09:00");
        assertThat(next(3, LocalDateTime.of(2026, 11, 4, 9, 30))).isEqualTo("2026-11-11T09:00");
    }

    @Test void laterThisWeekAndCrossingTheWeek() {
        assertThat(next(5, WEDNESDAY_8)).isEqualTo("2026-11-06T09:00");
        assertThat(next(1, WEDNESDAY_8)).isEqualTo("2026-11-09T09:00");
    }

    @Test void sundayRollsToMonday() {
        assertThat(next(1, LocalDateTime.of(2026, 11, 8, 23, 59))).isEqualTo("2026-11-09T09:00");
        assertThat(next(7, LocalDateTime.of(2026, 11, 7, 12, 0))).isEqualTo("2026-11-08T09:00");
    }

    @Test void waitsForEffectiveStart() {
        assertThat(NextClassCalculator.nextStart(1, NINE, LocalDate.of(2026, 12, 1), TO, WEDNESDAY_8).orElseThrow())
                .isEqualTo("2026-12-07T09:00");
    }

    @Test void stopsAtEffectiveEnd() {
        assertThat(NextClassCalculator.nextStart(1, NINE, FROM, LocalDate.of(2026, 11, 8), WEDNESDAY_8)).isEmpty();
        assertThat(NextClassCalculator.nextStart(1, NINE, FROM, LocalDate.of(2026, 11, 9), WEDNESDAY_8))
                .contains(LocalDateTime.of(2026, 11, 9, 9, 0));
    }
}
