package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.mapper.CourseSectionMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionMeetingService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CourseSectionMeetingServiceImpl implements CourseSectionMeetingService {
    private final CourseSectionRepository sections;
    private final CourseSectionMeetingRepository meetings;
    private final CourseSectionMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public CourseSectionMeetingServiceImpl(CourseSectionRepository sections, CourseSectionMeetingRepository meetings,
            CourseSectionMapper mapper, Clock clock, EntityManager entityManager) {
        this.sections = sections;
        this.meetings = meetings;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CourseSectionMeetingResponse> list(AuthenticatedUser actor, long sectionId) {
        requireFull(actor);
        sections.findByIdAndDeletedFalse(sectionId).orElseThrow(CourseSectionNotFoundException::new);
        return meetings.findByCourseSectionIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAscIdAsc(sectionId).stream()
                .map(mapper::meeting).toList();
    }

    @Override @Transactional(timeout = 15)
    public CourseSectionMeetingResponse create(AuthenticatedUser actor, long sectionId, CourseSectionMeetingRequest request) {
        requireFull(actor);
        CourseSection section = lockedSection(sectionId);
        var meeting = new CourseSectionMeeting();
        meeting.setCourseSection(section);
        meeting.setActive(true);
        meeting.setCreatedAt(now());
        apply(meeting, 0L, sectionId, request);
        return mapper.meeting(meetings.saveAndFlush(meeting));
    }

    @Override @Transactional(timeout = 15)
    public CourseSectionMeetingResponse update(AuthenticatedUser actor, long sectionId, long meetingId,
            CourseSectionMeetingRequest request) {
        requireFull(actor);
        lockedSection(sectionId);
        CourseSectionMeeting meeting = existing(sectionId, meetingId);
        apply(meeting, meetingId, sectionId, request);
        meeting.setUpdatedAt(now());
        meetings.flush();
        return mapper.meeting(meeting);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long sectionId, long meetingId) {
        requireFull(actor);
        lockedSection(sectionId);
        CourseSectionMeeting meeting = existing(sectionId, meetingId);
        meeting.setDeleted(true);
        meeting.setUpdatedAt(now());
        meetings.flush();
    }

    private void apply(CourseSectionMeeting meeting, long excludeId, long sectionId, CourseSectionMeetingRequest request) {
        CourseMeetingType type = type(request.meetingType());
        // Minutes are the finest unit the timetable shows.
        LocalTime start = request.startTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime end = request.endTime().truncatedTo(ChronoUnit.MINUTES);
        if (!end.isAfter(start)) throw invalid("The end time must be after the start time.");
        if (request.effectiveTo().isBefore(request.effectiveFrom()))
            throw invalid("The effective end date must not be before the effective start date.");
        if (request.roomId() != null) throw new CourseSectionFailure(400, "ROOM_NOT_FOUND",
                "The referenced room does not exist. Use roomLabel until rooms are managed.");
        boolean active = request.active() == null ? meeting.isActive() : request.active();
        short day = request.dayOfWeek().shortValue();
        if (active && overlapsAnother(sectionId, excludeId, day, start, end, request.effectiveFrom(), request.effectiveTo()))
            throw new CourseSectionFailure(409, "COURSE_SECTION_MEETING_CONFLICT",
                    "This meeting overlaps another meeting of the same course section.");
        meeting.setMeetingType(type);
        meeting.setDayOfWeek(day);
        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setEffectiveFrom(request.effectiveFrom());
        meeting.setEffectiveTo(request.effectiveTo());
        meeting.setRoomLabel(request.roomLabel());
        meeting.setActive(active);
    }

    // Compared in Java: a JPQL comparison with LocalTime parameters missed real overlaps when the JVM is not in UTC.
    // Touching times do not overlap: 09:00-10:00 and 10:00-11:00 may coexist.
    private boolean overlapsAnother(long sectionId, long excludeId, short day, LocalTime start, LocalTime end,
            LocalDate from, LocalDate to) {
        return meetings.findByCourseSectionIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAscIdAsc(sectionId).stream()
                .anyMatch(m -> m.isActive() && !m.getId().equals(excludeId) && m.getDayOfWeek() == day
                        && m.getStartTime().isBefore(end) && m.getEndTime().isAfter(start)
                        && !m.getEffectiveFrom().isAfter(to) && !m.getEffectiveTo().isBefore(from));
    }

    // The Section row lock serializes the overlap check between concurrent meeting writes.
    private CourseSection lockedSection(long sectionId) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
        CourseSection section = sections.findByIdForUpdate(sectionId).orElseThrow(CourseSectionNotFoundException::new);
        if (section.isDeleted()) throw new CourseSectionNotFoundException();
        return section;
    }

    private CourseSectionMeeting existing(long sectionId, long meetingId) {
        return meetings.findByIdAndCourseSectionIdAndDeletedFalse(meetingId, sectionId).orElseThrow(() ->
                new CourseSectionFailure(404, "COURSE_SECTION_MEETING_NOT_FOUND", "The requested meeting does not exist."));
    }

    private static CourseMeetingType type(String value) {
        try {
            return CourseMeetingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("The meeting type must be LECTURE, LAB or TUTORIAL.");
        }
    }
    private static CourseSectionFailure invalid(String message) {
        return new CourseSectionFailure(400, "VALIDATION_ERROR", message);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
}
