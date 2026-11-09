package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableOccurrence;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableExceptionType;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Expands weekly rules into dated occurrences with exceptions applied. Pure date arithmetic, no I/O. */
final class TimetableOccurrences {
    private TimetableOccurrences() {}

    /** The weekly rule of a stored or not-yet-stored meeting. */
    record Rule(Long meetingId, int dayOfWeek, LocalTime start, LocalTime end, LocalDate from, LocalDate to, Long roomId) {
        static Rule of(CourseSectionMeeting m) {
            return new Rule(m.getId(), m.getDayOfWeek(), m.getStartTime(), m.getEndTime(), m.getEffectiveFrom(), m.getEffectiveTo(), m.getRoomId());
        }
        boolean covers(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to) && date.getDayOfWeek().getValue() == dayOfWeek;
        }
    }

    /**
     * Occurrences of the rule between from and to (inclusive, intersected with the rule's own dates), minus cancelled
     * and moved dates, plus moved occurrences whose new date is in the window. Cancelled ones are kept as CANCELLED
     * only when includeCancelled is set.
     */
    static List<TimetableOccurrence> expand(Rule rule, Collection<TimetableMeetingException> exceptions, LocalDate from, LocalDate to,
            boolean includeCancelled) {
        var byOriginal = new HashMap<LocalDate, TimetableMeetingException>();
        for (var x : exceptions) if (!x.isDeleted()) byOriginal.put(x.getOriginalDate(), x);
        var result = new ArrayList<TimetableOccurrence>();
        LocalDate first = (from.isAfter(rule.from()) ? from : rule.from()).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(rule.dayOfWeek())));
        LocalDate last = to.isBefore(rule.to()) ? to : rule.to();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusWeeks(1)) {
            var x = byOriginal.get(date);
            if (x == null) result.add(new TimetableOccurrence(rule.meetingId(), date, rule.start(), rule.end(), rule.roomId(),
                    TimetableOccurrence.REGULAR, null, null));
            else if (x.getExceptionType() == TimetableExceptionType.CANCELLED && includeCancelled)
                result.add(new TimetableOccurrence(rule.meetingId(), date, rule.start(), rule.end(), rule.roomId(),
                        TimetableOccurrence.CANCELLED, x.getId(), date));
        }
        for (var x : exceptions) {
            if (x.isDeleted() || x.getExceptionType() != TimetableExceptionType.RESCHEDULED || !rule.covers(x.getOriginalDate())) continue;
            LocalDate date = x.getReplacementDate();
            if (date.isBefore(from) || date.isAfter(to)) continue;
            Long room = x.getReplacementRoom() == null ? rule.roomId() : x.getReplacementRoom().getId();
            result.add(new TimetableOccurrence(rule.meetingId(), date, x.getReplacementStartTime(), x.getReplacementEndTime(), room,
                    TimetableOccurrence.RESCHEDULED, x.getId(), x.getOriginalDate()));
        }
        result.sort(Comparator.comparing(TimetableOccurrence::date).thenComparing(TimetableOccurrence::startTime)
                .thenComparing(TimetableOccurrence::meetingId, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** Every occurrence the rule and its exceptions produce, wherever a reschedule lands. */
    static List<TimetableOccurrence> expandAll(Rule rule, Collection<TimetableMeetingException> exceptions) {
        LocalDate from = rule.from(), to = rule.to();
        for (var x : exceptions) {
            if (x.isDeleted() || x.getReplacementDate() == null) continue;
            if (x.getReplacementDate().isBefore(from)) from = x.getReplacementDate();
            if (x.getReplacementDate().isAfter(to)) to = x.getReplacementDate();
        }
        return expand(rule, exceptions, from, to, false);
    }

    /** First overlap between two occurrence lists (both sorted), optionally only where both use the same room. */
    record Overlap(LocalDate date, LocalTime start, LocalTime end, int count) {}
    static Optional<Overlap> overlap(List<TimetableOccurrence> a, List<TimetableOccurrence> b, boolean sameRoom) {
        Overlap first = null;
        int count = 0;
        var byDate = new HashMap<LocalDate, List<TimetableOccurrence>>();
        for (var o : b) byDate.computeIfAbsent(o.date(), d -> new ArrayList<>()).add(o);
        for (var o : a) {
            for (var p : byDate.getOrDefault(o.date(), List.of())) {
                if (!o.overlaps(p) || sameRoom && !Objects.equals(o.roomId(), p.roomId())) continue;
                count++;
                if (first == null) first = new Overlap(o.date(), later(o.startTime(), p.startTime()), earlier(o.endTime(), p.endTime()), 0);
            }
        }
        return first == null ? Optional.empty() : Optional.of(new Overlap(first.date(), first.start(), first.end(), count));
    }

    private static LocalTime later(LocalTime a, LocalTime b) { return a.isAfter(b) ? a : b; }
    private static LocalTime earlier(LocalTime a, LocalTime b) { return a.isBefore(b) ? a : b; }
}
