package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CampusOpeningCalculatorTests {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    // Monday 2026-11-02.
    private static ZonedDateTime monday(String time) { return LocalDate.of(2026, 11, 2).atTime(LocalTime.parse(time)).atZone(BANGKOK); }
    private static CampusOpeningHour hour(int day, String opens, String closes) {
        var row = new CampusOpeningHour();
        row.setDayOfWeek((short) day);
        row.setClosed(opens == null);
        row.setOpensAt(opens == null ? null : LocalTime.parse(opens));
        row.setClosesAt(closes == null ? null : LocalTime.parse(closes));
        return row;
    }
    private static CampusOpeningException exception(String opens, String closes, String reason) {
        var row = new CampusOpeningException();
        row.setExceptionDate(LocalDate.of(2026, 11, 2));
        row.setClosed(opens == null);
        row.setOpensAt(opens == null ? null : LocalTime.parse(opens));
        row.setClosesAt(closes == null ? null : LocalTime.parse(closes));
        row.setReason(reason);
        return row;
    }

    @Test
    void usesPublishedHoursAndTreatsTheClosingMinuteAsClosed() {
        var hours = List.of(hour(1, "08:00", "17:00"));
        assertThat(CampusOpeningCalculator.status(true, "ACTIVE", hours, null, monday("07:59")).expectedOpen()).isFalse();
        assertThat(CampusOpeningCalculator.status(true, "ACTIVE", hours, null, monday("08:00")).expectedOpen()).isTrue();
        assertThat(CampusOpeningCalculator.status(true, "ACTIVE", hours, null, monday("17:00")).expectedOpen()).isFalse();
        var state = CampusOpeningCalculator.status(true, "ACTIVE", hours, null, monday("12:00"));
        assertThat(state.basis()).isEqualTo("PUBLISHED_HOURS");
        assertThat(state.verifiedRealTime()).isFalse();
    }

    @Test
    void anExceptionBeatsRegularHours() {
        var hours = List.of(hour(1, "08:00", "17:00"));
        var holiday = CampusOpeningCalculator.status(true, "ACTIVE", hours, exception(null, null, "Holiday"), monday("12:00"));
        assertThat(holiday.expectedOpen()).isFalse();
        assertThat(holiday.basis()).isEqualTo("PUBLISHED_EXCEPTION");
        assertThat(holiday.reason()).isEqualTo("Holiday");
        assertThat(CampusOpeningCalculator.status(true, "ACTIVE", hours, exception("13:00", "14:00", null), monday("12:00")).expectedOpen()).isFalse();
    }

    @Test
    void statusBeatsEverythingAndMissingHoursAreUnknown() {
        var hours = List.of(hour(1, "08:00", "17:00"));
        var closed = CampusOpeningCalculator.status(false, "TEMPORARILY_CLOSED", hours, null, monday("12:00"));
        assertThat(closed.expectedOpen()).isFalse();
        assertThat(closed.basis()).isEqualTo("OPERATIONAL_STATUS");
        var unknown = CampusOpeningCalculator.status(true, "ACTIVE", List.of(hour(2, "08:00", "17:00")), null, monday("12:00"));
        assertThat(unknown.expectedOpen()).isNull();
        assertThat(unknown.basis()).isEqualTo("NOT_PUBLISHED");
    }

    @Test
    void summaryGroupsConsecutiveDays() {
        var rows = new ArrayList<CampusOpeningHour>();
        for (int day = 1; day <= 5; day++) rows.add(hour(day, "08:00", "17:00"));
        rows.add(hour(6, null, null));
        rows.add(hour(7, "08:00", "17:00"));
        assertThat(CampusOpeningCalculator.summary(rows)).isEqualTo("Mon-Fri 08:00-17:00, Sat Closed, Sun 08:00-17:00");
        assertThat(CampusOpeningCalculator.summary(List.of())).isNull();
    }
}
