package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSectionMeeting;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionMeetingRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.TimetableMeetingException;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.mapper.TimetableMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository.TimetableMeetingExceptionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Conflicts are decided on real dated occurrences (same date, half-open time overlap), never on weekday rules alone,
 * so a cancelled occurrence removes a conflict and a reschedule can create one. Comparison happens in Java (D56:
 * LocalTime query parameters were unreliable); the database only narrows the candidate set.
 */
@Service
public class TimetableConflictServiceImpl implements TimetableConflictService {
    // Advisory lock key spaces, ordered: sections, lecturers, rooms. Keys are taken in ascending order.
    private static final long SECTION = 1L << 40, LECTURER = 2L << 40, ROOM = 3L << 40;
    private static final TimetableMeetingStatus PUBLISHED = TimetableMeetingStatus.PUBLISHED;
    private static final List<TimetableConflictType> PRECEDENCE = List.of(TimetableConflictType.COURSE_SECTION_CONFLICT,
            TimetableConflictType.LECTURER_CONFLICT, TimetableConflictType.ROOM_CONFLICT);

    private final CourseSectionMeetingRepository meetings;
    private final TimetableMeetingExceptionRepository exceptions;
    private final TimetableMapper mapper;
    private final EntityManager entityManager;

    public TimetableConflictServiceImpl(CourseSectionMeetingRepository meetings, TimetableMeetingExceptionRepository exceptions,
            TimetableMapper mapper, EntityManager entityManager) {
        this.meetings = meetings;
        this.exceptions = exceptions;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public List<TimetableConflict> check(ConflictCandidate c) {
        var rule = new TimetableOccurrences.Rule(c.excludeMeetingId(), c.dayOfWeek(), c.startTime(), c.endTime(), c.effectiveFrom(),
                c.effectiveTo(), c.roomId());
        var own = TimetableOccurrences.expandAll(rule, c.exceptions());
        if (own.isEmpty()) return List.of();
        LocalDate from = own.get(0).date(), to = own.get(own.size() - 1).date();
        long exclude = c.excludeMeetingId() == null ? 0L : c.excludeMeetingId();
        var result = new ArrayList<TimetableConflict>();
        var roomIds = new HashSet<Long>();
        for (var o : own) if (o.roomId() != null) roomIds.add(o.roomId());
        for (Long roomId : roomIds) {
            var inRoom = own.stream().filter(o -> roomId.equals(o.roomId())).toList();
            for (var other : withOccurrences(meetings.findPublishedUsingRoom(roomId, from, to, exclude, PUBLISHED)).entrySet())
                TimetableOccurrences.overlap(inRoom, other.getValue(), true).ifPresent(hit -> result.add(conflict(TimetableConflictType.ROOM_CONFLICT,
                        other.getKey(), hit, roomId, null, null)));
        }
        for (var other : withOccurrences(meetings.findPublishedOfLecturer(c.lectureId(), from, to, exclude, PUBLISHED)).entrySet())
            TimetableOccurrences.overlap(own, other.getValue(), false).ifPresent(hit -> result.add(conflict(TimetableConflictType.LECTURER_CONFLICT,
                    other.getKey(), hit, null, c.lectureId(), null)));
        for (var other : withOccurrences(meetings.findPublishedOfSection(c.courseSectionId(), from, to, exclude, PUBLISHED)).entrySet())
            TimetableOccurrences.overlap(own, other.getValue(), false).ifPresent(hit -> result.add(conflict(TimetableConflictType.COURSE_SECTION_CONFLICT,
                    other.getKey(), hit, null, null, c.courseSectionId())));
        // Most specific first: a meeting of the same section is reported as a section conflict, not as a room conflict.
        result.sort(Comparator.comparing((TimetableConflict x) -> PRECEDENCE.indexOf(x.type())).thenComparing(TimetableConflict::conflictingMeetingId));
        return result;
    }

    @Override
    public List<TimetableConflictPair> conflictsAmong(List<CourseSectionMeeting> published, Map<Long, List<TimetableMeetingException>> live,
            LocalDate windowFrom, LocalDate windowTo, TimetableConflictType type) {
        var byId = new HashMap<Long, CourseSectionMeeting>();
        var byRoom = new HashMap<Long, List<TimetableOccurrence>>();
        var byLecturer = new HashMap<Long, List<TimetableOccurrence>>();
        var bySection = new HashMap<Long, List<TimetableOccurrence>>();
        for (var m : published) {
            if (m.getStatus() != PUBLISHED || m.isDeleted()) continue;
            byId.put(m.getId(), m);
            var rule = TimetableOccurrences.Rule.of(m);
            var exc = live.getOrDefault(m.getId(), List.of());
            var occurrences = windowFrom == null ? TimetableOccurrences.expandAll(rule, exc)
                    : TimetableOccurrences.expand(rule, exc, windowFrom, windowTo, false);
            for (var o : occurrences) {
                if (o.roomId() != null) byRoom.computeIfAbsent(o.roomId(), k -> new ArrayList<>()).add(o);
                byLecturer.computeIfAbsent(m.getCourseSection().getLecture().getId(), k -> new ArrayList<>()).add(o);
                bySection.computeIfAbsent(m.getCourseSection().getId(), k -> new ArrayList<>()).add(o);
            }
        }
        var result = new ArrayList<TimetableConflictPair>();
        if (type == null || type == TimetableConflictType.ROOM_CONFLICT) pairs(TimetableConflictType.ROOM_CONFLICT, byRoom, byId, result);
        if (type == null || type == TimetableConflictType.LECTURER_CONFLICT) pairs(TimetableConflictType.LECTURER_CONFLICT, byLecturer, byId, result);
        if (type == null || type == TimetableConflictType.COURSE_SECTION_CONFLICT) pairs(TimetableConflictType.COURSE_SECTION_CONFLICT, bySection, byId, result);
        result.sort(Comparator.comparing(TimetableConflictPair::firstDate).thenComparing(TimetableConflictPair::overlapStart)
                .thenComparing(p -> p.meetingA().meetingId()).thenComparing(p -> p.meetingB().meetingId()));
        return result;
    }

    @Override
    public void lockResources(Long courseSectionId, Long lectureId, Collection<Long> roomIds) {
        var keys = new TreeSet<Long>();
        if (courseSectionId != null) keys.add(SECTION + courseSectionId);
        if (lectureId != null) keys.add(LECTURER + lectureId);
        for (Long room : roomIds) if (room != null) keys.add(ROOM + room);
        try {
            for (Long key : keys) entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)").setParameter("key", key).getSingleResult();
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException e) {
            // SET LOCAL lock_timeout = '3s' cancelled the wait: another admin is changing the same resources right now.
            throw com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception.TimetableFailure.concurrentModification();
        }
    }

    // Sweep each resource's occurrences by date and start time; every overlapping pair of different meetings is a conflict.
    private void pairs(TimetableConflictType type, Map<Long, List<TimetableOccurrence>> byResource, Map<Long, CourseSectionMeeting> byId,
            List<TimetableConflictPair> out) {
        for (var entry : byResource.entrySet()) {
            var occurrences = entry.getValue();
            occurrences.sort(Comparator.comparing(TimetableOccurrence::date).thenComparing(TimetableOccurrence::startTime).thenComparing(TimetableOccurrence::meetingId));
            var found = new LinkedHashMap<List<Long>, TimetableOccurrences.Overlap>();
            for (int i = 0; i < occurrences.size(); i++) {
                var a = occurrences.get(i);
                for (int j = i + 1; j < occurrences.size() && occurrences.get(j).date().equals(a.date())
                        && occurrences.get(j).startTime().isBefore(a.endTime()); j++) {
                    var b = occurrences.get(j);
                    if (a.meetingId().equals(b.meetingId()) || !a.overlaps(b)) continue;
                    var key = List.of(Math.min(a.meetingId(), b.meetingId()), Math.max(a.meetingId(), b.meetingId()));
                    var previous = found.get(key);
                    found.put(key, previous == null ? new TimetableOccurrences.Overlap(a.date(), b.startTime(),
                            a.endTime().isBefore(b.endTime()) ? a.endTime() : b.endTime(), 1)
                            : new TimetableOccurrences.Overlap(previous.date(), previous.start(), previous.end(), previous.count() + 1));
                }
            }
            for (var hit : found.entrySet()) {
                var first = byId.get(hit.getKey().get(0));
                var second = byId.get(hit.getKey().get(1));
                var section = first.getCourseSection();
                var room = type == TimetableConflictType.ROOM_CONFLICT ? entry.getKey() : null;
                out.add(new TimetableConflictPair(type, mapper.conflictMeeting(first), mapper.conflictMeeting(second), hit.getValue().date(),
                        hit.getValue().start(), hit.getValue().end(), hit.getValue().count(), room,
                        room == null ? null : roomNumber(first, second, room),
                        type == TimetableConflictType.LECTURER_CONFLICT ? section.getLecture().getId() : null,
                        type == TimetableConflictType.LECTURER_CONFLICT ? section.getLecture().getLectureNameEn() : null,
                        type == TimetableConflictType.COURSE_SECTION_CONFLICT ? section.getId() : null));
            }
        }
    }

    private static String roomNumber(CourseSectionMeeting a, CourseSectionMeeting b, Long roomId) {
        if (a.getRoom() != null && roomId.equals(a.getRoom().getId())) return a.getRoom().getRoomNumber();
        if (b.getRoom() != null && roomId.equals(b.getRoom().getId())) return b.getRoom().getRoomNumber();
        return null; // the conflict comes from a rescheduled occurrence in another room
    }

    private Map<Long, List<TimetableOccurrence>> withOccurrences(List<CourseSectionMeeting> candidates) {
        if (candidates.isEmpty()) return Map.of();
        var live = new HashMap<Long, List<TimetableMeetingException>>();
        for (var x : exceptions.findLiveForMeetings(candidates.stream().map(CourseSectionMeeting::getId).toList()))
            live.computeIfAbsent(x.getMeeting().getId(), k -> new ArrayList<>()).add(x);
        var result = new TreeMap<Long, List<TimetableOccurrence>>();
        for (var m : candidates)
            result.put(m.getId(), TimetableOccurrences.expandAll(TimetableOccurrences.Rule.of(m), live.getOrDefault(m.getId(), List.of())));
        return result;
    }

    private static TimetableConflict conflict(TimetableConflictType type, Long otherId, TimetableOccurrences.Overlap hit, Long roomId,
            Long lecturerId, Long sectionId) {
        String message = switch (type) {
            case ROOM_CONFLICT -> "The selected room is already scheduled during this interval.";
            case LECTURER_CONFLICT -> "The assigned lecturer has another class during this interval.";
            case COURSE_SECTION_CONFLICT -> "The course section already has a meeting during this interval.";
        };
        return new TimetableConflict(type, otherId, message, hit.date(), hit.start(), hit.end(), roomId, lecturerId, sectionId);
    }
}
