package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.StudentAcademicProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.CampusRoomRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception.TimetableFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.mapper.TimetableMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meetings are created as drafts and published in a separate step. Publishing, editing a published meeting and
 * rescheduling an occurrence run under the meeting row lock plus advisory locks on the section, lecturer and rooms,
 * and re-check conflicts after the locks are held, so concurrent admins cannot double-book (spec file 42, section 48).
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class TimetableServiceImpl implements TimetableService {
    public static final Set<String> LIST_FILTERS = Set.of("academicTermId", "facultyId", "departmentId", "courseId", "courseSectionId", "lecturerId",
            "campusId", "buildingId", "roomId", "dayOfWeek", "meetingType", "status");
    public static final Set<String> CALENDAR_FILTERS = Set.of("academicTermId", "facultyId", "departmentId", "courseId", "courseSectionId", "lecturerId",
            "campusId", "buildingId", "roomId", "weekStart", "status");
    public static final Set<String> CONFLICT_FILTERS = Set.of("academicTermId", "facultyId", "departmentId", "courseSectionId", "buildingId", "roomId",
            "type", "weekStart");
    public static final Map<String, String> LIST_SORT = Map.ofEntries(Map.entry("id", "id"), Map.entry("dayOfWeek", "dayOfWeek"),
            Map.entry("startTime", "startTime"), Map.entry("endTime", "endTime"), Map.entry("status", "status"), Map.entry("meetingType", "meetingType"),
            Map.entry("createdAt", "createdAt"), Map.entry("effectiveFrom", "effectiveFrom"), Map.entry("courseCode", "courseSection.course.courseCode"),
            Map.entry("courseName", "courseSection.course.courseName"), Map.entry("sectionNumber", "courseSection.sectionNumber"),
            Map.entry("lecturerName", "courseSection.lecture.lectureNameEn"), Map.entry("roomNumber", "room.roomNumber"));
    public static final Map<String, String> ID_SORT = Map.of("id", "id");
    private static final TimetableMeetingStatus PUBLISHED = TimetableMeetingStatus.PUBLISHED;

    private final CourseSectionMeetingRepository meetings;
    private final CourseSectionRepository sections;
    private final CampusRoomRepository rooms;
    private final TimetableMeetingExceptionRepository exceptions;
    private final TimetableLookupRepository lookups;
    private final AcademicTermRepository terms;
    private final TimetableConflictService conflicts;
    private final TimetableMapper mapper;
    private final TimetableWriteSupport support;
    private final StudentAcademicProperties properties;

    public TimetableServiceImpl(CourseSectionMeetingRepository meetings, CourseSectionRepository sections, CampusRoomRepository rooms,
            TimetableMeetingExceptionRepository exceptions, TimetableLookupRepository lookups, AcademicTermRepository terms,
            TimetableConflictService conflicts, TimetableMapper mapper, TimetableWriteSupport support, StudentAcademicProperties properties) {
        this.meetings = meetings;
        this.sections = sections;
        this.rooms = rooms;
        this.exceptions = exceptions;
        this.lookups = lookups;
        this.terms = terms;
        this.conflicts = conflicts;
        this.mapper = mapper;
        this.support = support;
        this.properties = properties;
    }

    // Meetings

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse create(AuthenticatedUser actor, TimetableMeetingRequest request) {
        return createForSection(actor, request.courseSectionId(), request.slot(), null, false);
    }

    @Override @Transactional(timeout = 15)
    public List<TimetableMeetingResponse> createBulk(AuthenticatedUser actor, TimetableBulkMeetingRequest request) {
        requireFull(actor);
        support.lockTimeout();
        CourseSection section = schedulableSection(request.courseSectionId(), true);
        var created = new ArrayList<CourseSectionMeeting>();
        for (var slot : request.meetings()) created.add(newDraft(actor, section, slot, null));
        try {
            meetings.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return created.stream().map(m -> mapper.meeting(reload(m.getId()))).toList();
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse createForSection(AuthenticatedUser actor, long sectionId, TimetableMeetingSlot slot, String legacyLabel, boolean publish) {
        requireFull(actor);
        support.lockTimeout();
        CourseSection section = schedulableSection(sectionId, true);
        CourseSectionMeeting meeting = newDraft(actor, section, slot, legacyLabel);
        try {
            meetings.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        if (publish) publishLocked(actor, meeting);
        return mapper.meeting(reload(meeting.getId()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableMeetingResponse detail(long id) {
        return mapper.meeting(meetings.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::meetingNotFound));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<TimetableMeetingResponse> listForSection(long sectionId) {
        sections.findByIdAndDeletedFalse(sectionId).orElseThrow(TimetableFailure::sectionNotFound);
        return meetings.findAll(Specification.allOf(live(), (root, q, cb) -> cb.equal(root.get("courseSection").get("id"), sectionId)))
                .stream().sorted(Comparator.comparing(CourseSectionMeeting::getDayOfWeek).thenComparing(CourseSectionMeeting::getStartTime)
                        .thenComparing(CourseSectionMeeting::getId)).map(mapper::meeting).toList();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetablePageResponse<TimetableMeetingListItem> list(TimetablePageQuery query) {
        Integer day = query.integer("dayOfWeek", 1, 7);
        CourseMeetingType type = query.enumValue("meetingType", CourseMeetingType.class);
        TimetableMeetingStatus status = query.enumValue("status", TimetableMeetingStatus.class);
        Specification<CourseSectionMeeting> specification = Specification.allOf(live(), filters(query), (root, q, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (day != null) predicates.add(cb.equal(root.get("dayOfWeek"), day.shortValue()));
            if (type != null) predicates.add(cb.equal(root.get("meetingType"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (query.search() != null) {
                var section = root.get("courseSection");
                var room = root.join("room", JoinType.LEFT);
                predicates.add(cb.or(TimetableWriteSupport.contains(cb, section.get("course").get("courseCode"), query.search()),
                        TimetableWriteSupport.contains(cb, section.get("course").get("courseName"), query.search()),
                        TimetableWriteSupport.contains(cb, section.get("sectionNumber"), query.search()),
                        TimetableWriteSupport.contains(cb, section.get("lecture").get("lectureNameEn"), query.search()),
                        TimetableWriteSupport.contains(cb, section.get("lecture").get("lectureNameTh"), query.search()),
                        TimetableWriteSupport.contains(cb, room.get("roomNumber"), query.search())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
        return query.response(meetings.findAll(specification, query.pageable()), mapper::listItem);
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse update(AuthenticatedUser actor, long id, TimetableMeetingSlot slot) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        applyUpdate(actor, meeting, slot, meeting.getRoomLabel());
        return mapper.meeting(reload(id));
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse updateForSection(AuthenticatedUser actor, long sectionId, long id, TimetableMeetingSlot slot, String legacyLabel, Boolean publish) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        if (!meeting.getCourseSection().getId().equals(sectionId)) throw TimetableFailure.meetingNotFound();
        applyUpdate(actor, meeting, slot, legacyLabel);
        if (publish != null) {
            if (publish && meeting.getStatus() == TimetableMeetingStatus.DRAFT) publishLocked(actor, meeting);
            else if (!publish && meeting.getStatus() == PUBLISHED) meeting.setStatus(TimetableMeetingStatus.DRAFT);
        }
        meetings.flush();
        return mapper.meeting(reload(id));
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        support.lockTimeout();
        softDelete(actor, lockedMeeting(id));
    }

    @Override @Transactional(timeout = 15)
    public void deleteForSection(AuthenticatedUser actor, long sectionId, long id) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        if (!meeting.getCourseSection().getId().equals(sectionId)) throw TimetableFailure.meetingNotFound();
        softDelete(actor, meeting);
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse publish(AuthenticatedUser actor, long id) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        if (meeting.getStatus() == TimetableMeetingStatus.CANCELLED) throw TimetableFailure.publishNotAllowed("A cancelled meeting series cannot be published again; create a new meeting.");
        if (meeting.getStatus() == TimetableMeetingStatus.DRAFT) publishLocked(actor, meeting);
        return mapper.meeting(reload(id));
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse cancel(AuthenticatedUser actor, long id) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        if (meeting.getStatus() != TimetableMeetingStatus.CANCELLED) {
            meeting.setStatus(TimetableMeetingStatus.CANCELLED);
            touch(meeting, actor);
            meetings.flush();
        }
        return mapper.meeting(reload(id));
    }

    @Override @Transactional(timeout = 15)
    public TimetableMeetingResponse revertToDraft(AuthenticatedUser actor, long id) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(id);
        if (meeting.getStatus() == PUBLISHED) {
            meeting.setStatus(TimetableMeetingStatus.DRAFT);
            touch(meeting, actor);
            meetings.flush();
        }
        return mapper.meeting(reload(id));
    }

    // Views

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableCalendarResponse calendar(TimetablePageQuery query) {
        ZoneId zone = properties.timezone();
        LocalDate requested = query.date("weekStart");
        // A non-Monday weekStart is normalized to the Monday of its week and echoed back (section 36).
        LocalDate weekStart = (requested == null ? LocalDate.ofInstant(support.instant(), zone) : requested)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        TimetableMeetingStatus status = query.enumValue("status", TimetableMeetingStatus.class);
        var rows = meetings.findAll(Specification.allOf(live(), filters(query), inWindow(weekStart, weekEnd),
                (root, q, cb) -> status == null ? cb.notEqual(root.get("status"), TimetableMeetingStatus.CANCELLED) : cb.equal(root.get("status"), status)));
        var live = liveExceptions(rows);
        var events = new ArrayList<TimetableCalendarEvent>();
        var roomCache = new HashMap<Long, CampusRoom>();
        for (var m : rows) {
            if (m.getRoom() != null) roomCache.put(m.getRoom().getId(), m.getRoom());
            for (var o : TimetableOccurrences.expand(TimetableOccurrences.Rule.of(m), live.getOrDefault(m.getId(), List.of()), weekStart, weekEnd, true)) {
                CampusRoom room = o.roomId() == null ? null : roomCache.computeIfAbsent(o.roomId(), id -> rooms.findById(id).orElse(null));
                events.add(event(m, o, room, zone));
            }
        }
        events.sort(Comparator.comparing(TimetableCalendarEvent::startsAt).thenComparing(TimetableCalendarEvent::meetingId));
        return new TimetableCalendarResponse(query.id("academicTermId"), weekStart, weekEnd, zone.getId(), events);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableConflictCheckResponse check(TimetableConflictCheckRequest request) {
        CourseSection section = schedulableSection(request.courseSectionId(), false);
        CampusRoom room = room(request.roomId());
        var slot = new TimetableMeetingSlot(request.roomId(), request.meetingType(), request.dayOfWeek(), request.startTime(), request.endTime(),
                request.effectiveFrom(), request.effectiveTo(), null);
        validate(slot, section.getAcademicTerm());
        List<TimetableMeetingException> own = request.meetingId() == null ? List.of()
                : exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(request.meetingId());
        var found = conflicts.check(new ConflictCandidate(request.meetingId(), section.getId(), section.getLecture().getId(),
                room == null ? null : room.getId(), request.dayOfWeek(), minutes(request.startTime()), minutes(request.endTime()),
                request.effectiveFrom(), request.effectiveTo(), own));
        return new TimetableConflictCheckResponse(!found.isEmpty(), found.size(), found);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableConflictListResponse conflicts(TimetablePageQuery query) {
        LocalDate weekStart = query.date("weekStart");
        LocalDate from = weekStart == null ? null : weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate to = from == null ? null : from.plusDays(6);
        TimetableConflictType type = query.enumValue("type", TimetableConflictType.class);
        var rows = meetings.findAll(Specification.allOf(live(), filters(query), published(), from == null ? all() : inWindow(from, to)));
        var pairs = conflicts.conflictsAmong(rows, liveExceptions(rows), from, to, type);
        return new TimetableConflictListResponse(pairs.size(), pairs);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableStatisticsResponse statistics(Long academicTermId) {
        var counts = new EnumMap<TimetableMeetingStatus, Long>(TimetableMeetingStatus.class);
        for (var row : meetings.countByStatus(academicTermId)) counts.put(row.getStatus(), row.getTotal());
        var rows = meetings.findAll(Specification.allOf(live(), published(),
                (root, q, cb) -> academicTermId == null ? cb.conjunction() : cb.equal(root.get("courseSection").get("academicTerm").get("id"), academicTermId)));
        int conflictCount = conflicts.conflictsAmong(rows, liveExceptions(rows), null, null, null).size();
        return new TimetableStatisticsResponse(counts.values().stream().mapToLong(Long::longValue).sum(),
                counts.getOrDefault(PUBLISHED, 0L), counts.getOrDefault(TimetableMeetingStatus.DRAFT, 0L),
                counts.getOrDefault(TimetableMeetingStatus.CANCELLED, 0L), conflictCount);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetableFilterOptionsResponse filterOptions(Long academicTermId, Long facultyId, Long departmentId, Long courseId, Long campusId, Long buildingId) {
        var termOptions = terms.findByDeletedFalseOrderByStartDateDescIdDesc().stream()
                .map(t -> new TimetableOption(t.getId(), t.getAcademicYearCode(), t.getSemester().getSemesterNameEn() + " / " + t.getAcademicYearCode()
                        + " (" + t.getAcademicYearLabel() + ")", t.getSemester().getId())).toList();
        return new TimetableFilterOptionsResponse(termOptions, lookups.faculties(), lookups.departments(facultyId),
                lookups.courses(facultyId, departmentId), lookups.courseSections(courseId, academicTermId), lookups.lecturers(facultyId, departmentId),
                lookups.campuses(), lookups.buildings(campusId), lookups.rooms(campusId, buildingId),
                Arrays.stream(CourseMeetingType.values()).map(Enum::name).toList(),
                Arrays.stream(TimetableMeetingStatus.values()).map(Enum::name).toList());
    }

    // Exceptions

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<TimetableExceptionResponse> exceptions(long meetingId) {
        meetings.findByIdAndDeletedFalse(meetingId).orElseThrow(TimetableFailure::meetingNotFound);
        return exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(meetingId).stream().map(mapper::exception).toList();
    }

    @Override @Transactional(timeout = 15)
    public TimetableExceptionResponse addException(AuthenticatedUser actor, long meetingId, TimetableExceptionRequest request) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(meetingId);
        var exception = new TimetableMeetingException();
        exception.setMeeting(meeting);
        exception.setCreatedBy(actor.userId());
        exception.setCreatedAt(support.now());
        applyException(actor, meeting, exception, request, 0L);
        try {
            exceptions.saveAndFlush(exception);
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.exception(exception);
    }

    @Override @Transactional(timeout = 15)
    public TimetableExceptionResponse updateException(AuthenticatedUser actor, long meetingId, long exceptionId, TimetableExceptionRequest request) {
        requireFull(actor);
        support.lockTimeout();
        CourseSectionMeeting meeting = lockedMeeting(meetingId);
        var exception = exceptions.findByIdAndMeetingIdAndDeletedFalse(exceptionId, meetingId).orElseThrow(TimetableFailure::exceptionNotFound);
        applyException(actor, meeting, exception, request, exceptionId);
        exception.setUpdatedBy(actor.userId());
        exception.setUpdatedAt(support.now());
        try {
            exceptions.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.exception(exception);
    }

    @Override @Transactional(timeout = 15)
    public void deleteException(AuthenticatedUser actor, long meetingId, long exceptionId) {
        requireFull(actor);
        support.lockTimeout();
        lockedMeeting(meetingId);
        var exception = exceptions.findByIdAndMeetingIdAndDeletedFalse(exceptionId, meetingId).orElseThrow(TimetableFailure::exceptionNotFound);
        exception.setDeleted(true);
        exception.setUpdatedBy(actor.userId());
        exception.setUpdatedAt(support.now());
        exceptions.flush();
    }

    // Internals

    private CourseSectionMeeting newDraft(AuthenticatedUser actor, CourseSection section, TimetableMeetingSlot slot, String legacyLabel) {
        CampusRoom room = room(slot.roomId());
        validate(slot, section.getAcademicTerm());
        var meeting = new CourseSectionMeeting();
        meeting.setCourseSection(section);
        meeting.setStatus(TimetableMeetingStatus.DRAFT);
        meeting.setCreatedBy(actor.userId());
        meeting.setCreatedAt(support.now());
        applySlot(meeting, slot, room, legacyLabel);
        return meetings.save(meeting);
    }

    private void applyUpdate(AuthenticatedUser actor, CourseSectionMeeting meeting, TimetableMeetingSlot slot, String legacyLabel) {
        if (meeting.getStatus() == TimetableMeetingStatus.CANCELLED) throw TimetableFailure.invalid("A cancelled meeting series cannot be edited.");
        CourseSection section = schedulableSection(meeting.getCourseSection().getId(), true);
        CampusRoom room = room(slot.roomId());
        validate(slot, section.getAcademicTerm());
        if (meeting.getStatus() == PUBLISHED) {
            if (room == null) throw TimetableFailure.publishNotAllowed("A published meeting needs a campus room. Assign one or revert it to a draft.");
            conflicts.lockResources(section.getId(), section.getLecture().getId(), Arrays.asList(meeting.getRoomId(), room.getId()));
            requireNoConflicts(conflicts.check(new ConflictCandidate(meeting.getId(), section.getId(), section.getLecture().getId(), room.getId(),
                    slot.dayOfWeek(), minutes(slot.startTime()), minutes(slot.endTime()), slot.effectiveFrom(), slot.effectiveTo(),
                    exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(meeting.getId()))));
        }
        applySlot(meeting, slot, room, legacyLabel);
        touch(meeting, actor);
        try {
            meetings.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    private void applySlot(CourseSectionMeeting meeting, TimetableMeetingSlot slot, CampusRoom room, String legacyLabel) {
        meeting.setMeetingType(slot.meetingType());
        meeting.setDayOfWeek(slot.dayOfWeek().shortValue());
        meeting.setStartTime(minutes(slot.startTime()));
        meeting.setEndTime(minutes(slot.endTime()));
        meeting.setEffectiveFrom(slot.effectiveFrom());
        meeting.setEffectiveTo(slot.effectiveTo());
        meeting.setRoom(room);
        meeting.setRoomLabel(legacyLabel);
        meeting.setNotes(slot.notes());
    }

    // Section 34: validate section, term, lecturer, room, then lock and re-check the three conflict types before publishing.
    private void publishLocked(AuthenticatedUser actor, CourseSectionMeeting meeting) {
        CourseSection section = schedulableSection(meeting.getCourseSection().getId(), true);
        if (meeting.getRoom() == null) throw TimetableFailure.publishNotAllowed("A published meeting needs a campus room.");
        room(meeting.getRoom().getId());
        if (meeting.getEffectiveTo().isBefore(section.getAcademicTerm().getStartDate()) || meeting.getEffectiveFrom().isAfter(section.getAcademicTerm().getEndDate()))
            throw TimetableFailure.invalidDateRange("The meeting's effective dates lie outside the academic term.");
        conflicts.lockResources(section.getId(), section.getLecture().getId(), List.of(meeting.getRoom().getId()));
        requireNoConflicts(conflicts.check(new ConflictCandidate(meeting.getId(), section.getId(), section.getLecture().getId(), meeting.getRoom().getId(),
                meeting.getDayOfWeek(), meeting.getStartTime(), meeting.getEndTime(), meeting.getEffectiveFrom(), meeting.getEffectiveTo(),
                exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(meeting.getId()))));
        meeting.setStatus(PUBLISHED);
        touch(meeting, actor);
        try {
            meetings.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    private void softDelete(AuthenticatedUser actor, CourseSectionMeeting meeting) {
        meeting.setDeleted(true);
        touch(meeting, actor);
        // Dependent exceptions keep their foreign key; they are retired with the series.
        for (var x : exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(meeting.getId())) {
            x.setDeleted(true);
            x.setUpdatedBy(actor.userId());
            x.setUpdatedAt(support.now());
        }
        meetings.flush();
    }

    private void applyException(AuthenticatedUser actor, CourseSectionMeeting meeting, TimetableMeetingException exception,
            TimetableExceptionRequest request, long excludeId) {
        if (meeting.getStatus() == TimetableMeetingStatus.CANCELLED) throw TimetableFailure.exceptionInvalid("The meeting series is cancelled.");
        var rule = TimetableOccurrences.Rule.of(meeting);
        if (!rule.covers(request.originalDate()))
            throw TimetableFailure.exceptionInvalid("originalDate is not an occurrence of this meeting (wrong weekday or outside its effective dates).");
        if (exceptions.existsByMeetingIdAndOriginalDateAndDeletedFalseAndIdNot(meeting.getId(), request.originalDate(), excludeId))
            throw TimetableFailure.exceptionDuplicate();
        CampusRoom replacementRoom = null;
        if (request.exceptionType() == TimetableExceptionType.CANCELLED) {
            if (request.replacementDate() != null || request.replacementStartTime() != null || request.replacementEndTime() != null || request.replacementRoomId() != null)
                throw TimetableFailure.exceptionInvalid("A cancellation takes no replacement date, time or room.");
        } else {
            if (request.replacementDate() == null || request.replacementStartTime() == null || request.replacementEndTime() == null)
                throw TimetableFailure.exceptionInvalid("A reschedule needs replacementDate, replacementStartTime and replacementEndTime.");
            if (!minutes(request.replacementEndTime()).isAfter(minutes(request.replacementStartTime())))
                throw TimetableFailure.exceptionInvalid("The replacement end time must be after its start time.");
            AcademicTerm term = meeting.getCourseSection().getAcademicTerm();
            if (term != null && (request.replacementDate().isBefore(term.getStartDate()) || request.replacementDate().isAfter(term.getEndDate())))
                throw TimetableFailure.exceptionInvalid("The replacement date lies outside the academic term.");
            replacementRoom = room(request.replacementRoomId());
        }
        exception.setOriginalDate(request.originalDate());
        exception.setExceptionType(request.exceptionType());
        exception.setReplacementDate(request.replacementDate());
        exception.setReplacementStartTime(request.replacementStartTime() == null ? null : minutes(request.replacementStartTime()));
        exception.setReplacementEndTime(request.replacementEndTime() == null ? null : minutes(request.replacementEndTime()));
        exception.setReplacementRoom(replacementRoom);
        exception.setReason(request.reason());
        if (request.exceptionType() == TimetableExceptionType.RESCHEDULED && meeting.getStatus() == PUBLISHED) {
            CourseSection section = meeting.getCourseSection();
            Long newRoom = replacementRoom == null ? meeting.getRoomId() : replacementRoom.getId();
            conflicts.lockResources(section.getId(), section.getLecture().getId(), Arrays.asList(meeting.getRoomId(), newRoom));
            var own = new ArrayList<>(exceptions.findByMeetingIdAndDeletedFalseOrderByOriginalDateAscIdAsc(meeting.getId()));
            own.removeIf(x -> x.getId() != null && x.getId() == excludeId);
            own.add(exception);
            requireNoConflicts(conflicts.check(new ConflictCandidate(meeting.getId(), section.getId(), section.getLecture().getId(), meeting.getRoomId(),
                    meeting.getDayOfWeek(), meeting.getStartTime(), meeting.getEndTime(), meeting.getEffectiveFrom(), meeting.getEffectiveTo(), own)));
        }
    }

    /** A section that may be scheduled: live, ACTIVE, with a live course and lecturer and an open academic term. */
    private CourseSection schedulableSection(long id, boolean lock) {
        CourseSection section = (lock ? sections.findByIdForUpdate(id) : sections.findByIdAndDeletedFalse(id)).orElseThrow(TimetableFailure::sectionNotFound);
        if (section.isDeleted()) throw TimetableFailure.sectionNotFound();
        if (!CourseSectionStatus.ACTIVE.getValue().equals(section.getStatus()) || section.getCourse().isDeleted() || !section.getCourse().isActive())
            throw TimetableFailure.sectionInactive();
        var lecture = section.getLecture();
        if (lecture.isDeleted() || !CourseSectionStatus.ACTIVE.getValue().equals(lecture.getStatus())) throw TimetableFailure.lecturerNotFound();
        AcademicTerm term = section.getAcademicTerm();
        if (term == null || term.isDeleted()) throw TimetableFailure.sectionWithoutTerm();
        if (lock && term.getStatus() == AcademicTermStatus.CLOSED) throw TimetableFailure.termClosed();
        return section;
    }

    /** Null stays null; otherwise the room, its building and its campus must all be live and usable (section 19). */
    private CampusRoom room(Long id) {
        if (id == null) return null;
        CampusRoom room = rooms.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::roomNotFound);
        CampusBuilding building = room.getBuilding();
        if (building.isDeleted() || building.getSite().isDeleted()) throw TimetableFailure.roomNotFound();
        if (building.getStatus() != CampusBuildingStatus.ACTIVE) throw TimetableFailure.roomUnavailable("The room's building is not active (" + building.getStatus() + ").");
        if (!building.getSite().isActive()) throw TimetableFailure.roomUnavailable("The room's campus is inactive.");
        if (room.getStatus() == CampusRoomStatus.INACTIVE || room.getStatus() == CampusRoomStatus.MAINTENANCE)
            throw TimetableFailure.roomUnavailable("The room is not usable for scheduling (" + room.getStatus() + ").");
        return room;
    }

    private static void validate(TimetableMeetingSlot slot, AcademicTerm term) {
        if (slot.dayOfWeek() < 1 || slot.dayOfWeek() > 7) throw TimetableFailure.invalidWeekday();
        if (!minutes(slot.endTime()).isAfter(minutes(slot.startTime()))) throw TimetableFailure.invalidTime();
        if (slot.effectiveTo().isBefore(slot.effectiveFrom())) throw TimetableFailure.invalidDateRange("effectiveTo must not be before effectiveFrom.");
        if (term != null && (slot.effectiveFrom().isBefore(term.getStartDate()) || slot.effectiveTo().isAfter(term.getEndDate())))
            throw TimetableFailure.invalidDateRange("The meeting dates must lie within the academic term (" + term.getStartDate() + " to " + term.getEndDate() + ").");
    }

    private static void requireNoConflicts(List<TimetableConflict> found) {
        if (found.isEmpty()) return;
        var first = found.get(0);
        String suffix = " First on " + first.date() + " " + first.overlapStart() + "-" + first.overlapEnd() + " with meeting " + first.conflictingMeetingId()
                + (found.size() > 1 ? " (" + found.size() + " conflicts in total)." : ".");
        throw switch (first.type()) {
            case ROOM_CONFLICT -> new TimetableFailure(409, "TIMETABLE_ROOM_CONFLICT", TimetableFailure.roomConflict().getMessage() + suffix);
            case LECTURER_CONFLICT -> new TimetableFailure(409, "TIMETABLE_LECTURER_CONFLICT", TimetableFailure.lecturerConflict().getMessage() + suffix);
            case COURSE_SECTION_CONFLICT -> new TimetableFailure(409, "TIMETABLE_SECTION_CONFLICT", TimetableFailure.sectionConflict().getMessage() + suffix);
        };
    }

    private CourseSectionMeeting lockedMeeting(long id) {
        try {
            return meetings.findLiveForUpdate(id).orElseThrow(TimetableFailure::meetingNotFound);
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw TimetableFailure.concurrentModification();
        }
    }
    private CourseSectionMeeting reload(long id) {
        return meetings.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::meetingNotFound);
    }
    private Map<Long, List<TimetableMeetingException>> liveExceptions(List<CourseSectionMeeting> rows) {
        if (rows.isEmpty()) return Map.of();
        var live = new HashMap<Long, List<TimetableMeetingException>>();
        for (var x : exceptions.findLiveForMeetings(rows.stream().map(CourseSectionMeeting::getId).toList()))
            live.computeIfAbsent(x.getMeeting().getId(), k -> new ArrayList<>()).add(x);
        return live;
    }
    private void touch(CourseSectionMeeting meeting, AuthenticatedUser actor) {
        meeting.setUpdatedBy(actor.userId());
        meeting.setUpdatedAt(support.now());
    }
    private static LocalTime minutes(LocalTime time) { return time.truncatedTo(ChronoUnit.MINUTES); }
    private static void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }

    private TimetableCalendarEvent event(CourseSectionMeeting m, TimetableOccurrence o, CampusRoom room, ZoneId zone) {
        CourseSection s = m.getCourseSection();
        var course = s.getCourse();
        CampusBuilding b = room == null ? null : room.getBuilding();
        return new TimetableCalendarEvent(m.getId(), s.getId(), course.getCourseCode(), course.getCourseName(), s.getSectionNumber(),
                s.getLecture().getId(), s.getLecture().getLectureNameEn(), course.getFaculty().getId(), course.getDepartment().getId(),
                room == null ? null : room.getId(), room == null ? m.getRoomLabel() : room.getRoomNumber(), b == null ? null : b.getId(),
                b == null ? null : b.getBuildingName(), b == null ? null : b.getSite().getId(), m.getMeetingType(), o.date(),
                o.date().atTime(o.startTime()).atZone(zone).toOffsetDateTime(), o.date().atTime(o.endTime()).atZone(zone).toOffsetDateTime(),
                m.getStatus(), o.kind(), o.exceptionId(), o.originalDate());
    }

    // Specifications

    private static Specification<CourseSectionMeeting> live() { return (root, q, cb) -> cb.isFalse(root.get("deleted")); }
    private static Specification<CourseSectionMeeting> all() { return (root, q, cb) -> cb.conjunction(); }
    private static Specification<CourseSectionMeeting> published() { return (root, q, cb) -> cb.equal(root.get("status"), PUBLISHED); }

    /** Weekly rule touches the window, or a rescheduled occurrence lands in it. */
    private static Specification<CourseSectionMeeting> inWindow(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            var moved = q.subquery(Long.class);
            var x = moved.from(TimetableMeetingException.class);
            moved.select(x.get("id")).where(cb.equal(x.get("meeting"), root), cb.isFalse(x.get("deleted")), cb.between(x.get("replacementDate"), from, to));
            return cb.or(cb.and(cb.lessThanOrEqualTo(root.get("effectiveFrom"), to), cb.greaterThanOrEqualTo(root.get("effectiveTo"), from)), cb.exists(moved));
        };
    }

    /** The shared academic and campus filters; faculty and department go through the course, campus and building through the room. */
    private static Specification<CourseSectionMeeting> filters(TimetablePageQuery query) {
        Long term = query.id("academicTermId"), faculty = query.id("facultyId"), department = query.id("departmentId"), course = query.id("courseId"),
                section = query.id("courseSectionId"), lecturer = query.id("lecturerId"), campus = query.id("campusId"), building = query.id("buildingId"),
                room = query.id("roomId");
        return (root, q, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var s = root.get("courseSection");
            if (term != null) predicates.add(cb.equal(s.get("academicTerm").get("id"), term));
            if (faculty != null) predicates.add(cb.equal(s.get("course").get("faculty").get("id"), faculty));
            if (department != null) predicates.add(cb.equal(s.get("course").get("department").get("id"), department));
            if (course != null) predicates.add(cb.equal(s.get("course").get("id"), course));
            if (section != null) predicates.add(cb.equal(s.get("id"), section));
            if (lecturer != null) predicates.add(cb.equal(s.get("lecture").get("id"), lecturer));
            if (campus != null) predicates.add(cb.equal(root.get("room").get("building").get("site").get("id"), campus));
            if (building != null) predicates.add(cb.equal(root.get("room").get("building").get("id"), building));
            if (room != null) predicates.add(cb.equal(root.get("room").get("id"), room));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
