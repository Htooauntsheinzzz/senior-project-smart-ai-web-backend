package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.mapper.CourseSectionMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionMeetingService;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.TimetableMeetingSlot;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.TimetableService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade over the timetable (spec file 42, section 23: one editable schedule source). The legacy
 * `active` flag maps to the meeting status: true publishes (so a campus roomId is required), false keeps or returns
 * the meeting to DRAFT. roomLabel is stored as legacy display text only.
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CourseSectionMeetingServiceImpl implements CourseSectionMeetingService {
    private final TimetableService timetable;
    private final CourseSectionMapper mapper;

    public CourseSectionMeetingServiceImpl(TimetableService timetable, CourseSectionMapper mapper) {
        this.timetable = timetable;
        this.mapper = mapper;
    }

    @Override
    public List<CourseSectionMeetingResponse> list(AuthenticatedUser actor, long sectionId) {
        requireFull(actor);
        return timetable.listForSection(sectionId).stream().map(mapper::meeting).toList();
    }

    @Override
    public CourseSectionMeetingResponse create(AuthenticatedUser actor, long sectionId, CourseSectionMeetingRequest request) {
        requireFull(actor);
        boolean publish = request.active() == null || request.active();
        return mapper.meeting(timetable.createForSection(actor, sectionId, slot(request), request.roomLabel(), publish));
    }

    @Override
    public CourseSectionMeetingResponse update(AuthenticatedUser actor, long sectionId, long meetingId, CourseSectionMeetingRequest request) {
        requireFull(actor);
        return mapper.meeting(timetable.updateForSection(actor, sectionId, meetingId, slot(request), request.roomLabel(), request.active()));
    }

    @Override
    public void delete(AuthenticatedUser actor, long sectionId, long meetingId) {
        requireFull(actor);
        timetable.deleteForSection(actor, sectionId, meetingId);
    }

    private static TimetableMeetingSlot slot(CourseSectionMeetingRequest request) {
        CourseMeetingType type;
        try {
            type = CourseMeetingType.valueOf(request.meetingType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CourseSectionFailure(400, "VALIDATION_ERROR", "The meeting type must be LECTURE, LAB or TUTORIAL.");
        }
        return new TimetableMeetingSlot(request.roomId(), type, request.dayOfWeek(), request.startTime(), request.endTime(),
                request.effectiveFrom(), request.effectiveTo(), null);
    }
    private static void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
}
