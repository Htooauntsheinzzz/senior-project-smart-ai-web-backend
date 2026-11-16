package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.CampusOpeningStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Expected opening state: status, then the date's exception, then regular hours. Pure, so it is tested without a database. */
final class CampusOpeningCalculator {
    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private CampusOpeningCalculator() {}

    static CampusOpeningStatus status(boolean statusActive, String statusName, List<CampusOpeningHour> hours,
            CampusOpeningException exception, ZonedDateTime now) {
        if (!statusActive) return new CampusOpeningStatus(false, "OPERATIONAL_STATUS", false, null, null, statusName);
        LocalTime time = now.toLocalTime();
        if (exception != null) {
            if (exception.isClosed()) return new CampusOpeningStatus(false, "PUBLISHED_EXCEPTION", false, null, null, exception.getReason());
            return new CampusOpeningStatus(within(time, exception.getOpensAt(), exception.getClosesAt()), "PUBLISHED_EXCEPTION", false,
                    exception.getOpensAt(), exception.getClosesAt(), exception.getReason());
        }
        int day = now.getDayOfWeek().getValue();
        for (CampusOpeningHour hour : hours) {
            if (hour.getDayOfWeek() != day) continue;
            if (hour.isClosed()) return new CampusOpeningStatus(false, "PUBLISHED_HOURS", false, null, null, null);
            return new CampusOpeningStatus(within(time, hour.getOpensAt(), hour.getClosesAt()), "PUBLISHED_HOURS", false,
                    hour.getOpensAt(), hour.getClosesAt(), null);
        }
        return new CampusOpeningStatus(null, "NOT_PUBLISHED", false, null, null, null);
    }

    /** The closing minute itself counts as closed. */
    private static boolean within(LocalTime time, LocalTime opens, LocalTime closes) {
        return !time.isBefore(opens) && time.isBefore(closes);
    }

    /** Groups consecutive days with the same hours, for example "Mon-Fri 08:00-17:00, Sat Closed". Null when nothing is published. */
    static String summary(List<CampusOpeningHour> hours) {
        if (hours.isEmpty()) return null;
        var byDay = new TreeMap<Integer, String>();
        for (CampusOpeningHour hour : hours)
            byDay.put((int) hour.getDayOfWeek(), hour.isClosed() ? "Closed"
                    : hour.getOpensAt().format(TIME) + "-" + hour.getClosesAt().format(TIME));
        var parts = new ArrayList<String>();
        int start = -1, previous = -1;
        String current = null;
        for (var entry : byDay.entrySet()) {
            if (current != null && entry.getValue().equals(current) && entry.getKey() == previous + 1) {
                previous = entry.getKey();
                continue;
            }
            if (current != null) parts.add(label(start, previous) + " " + current);
            start = previous = entry.getKey();
            current = entry.getValue();
        }
        parts.add(label(start, previous) + " " + current);
        return String.join(", ", parts);
    }
    private static String label(int start, int end) {
        return start == end ? DAYS[start - 1] : DAYS[start - 1] + "-" + DAYS[end - 1];
    }
}
