package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity.CourseAssignment;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.AssignmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.exception.AcademicActivityFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.mapper.AcademicActivityMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.repository.CourseAssignmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service.CourseAssignmentService;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CourseAssignmentServiceImpl implements CourseAssignmentService {
    private final CourseAssignmentRepository assignments;
    private final CourseSectionRepository sections;
    private final AcademicActivityMapper mapper;
    private final Clock clock;

    public CourseAssignmentServiceImpl(CourseAssignmentRepository assignments, CourseSectionRepository sections,
            AcademicActivityMapper mapper, Clock clock) {
        this.assignments = assignments;
        this.sections = sections;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicActivityPageResponse<CourseAssignmentResponse> list(AuthenticatedUser actor, AcademicActivityListQuery query) {
        requireFull(actor);
        AssignmentStatus status = query.status() == null ? null : status(query.status());
        return AcademicActivityPageResponse.of(assignments.search(query.courseSectionId(), status, query.pageable("dueAt"))
                .map(mapper::assignment));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseAssignmentResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        return mapper.assignment(assignments.findLive(id).orElseThrow(CourseAssignmentServiceImpl::notFound));
    }

    @Override @Transactional(timeout = 15)
    public CourseAssignmentResponse create(AuthenticatedUser actor, CourseAssignmentRequest request) {
        requireFull(actor);
        var assignment = new CourseAssignment();
        assignment.setCourseSection(section(request.courseSectionId()));
        assignment.setTitle(request.title());
        assignment.setDescription(request.description());
        assignment.setDueAt(request.dueAt());
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setCreatedBy(actor.userId());
        assignment.setCreatedAt(now());
        return mapper.assignment(assignments.saveAndFlush(assignment));
    }

    @Override @Transactional(timeout = 15)
    public CourseAssignmentResponse update(AuthenticatedUser actor, long id, CourseAssignmentRequest request) {
        requireFull(actor);
        CourseAssignment assignment = locked(id);
        if (assignment.getStatus() == AssignmentStatus.CLOSED) throw invalidTransition();
        assignment.setCourseSection(section(request.courseSectionId()));
        assignment.setTitle(request.title());
        assignment.setDescription(request.description());
        assignment.setDueAt(request.dueAt());
        touch(assignment, actor);
        assignments.flush();
        return mapper.assignment(assignment);
    }

    @Override @Transactional(timeout = 15)
    public CourseAssignmentResponse publish(AuthenticatedUser actor, long id) {
        requireFull(actor);
        CourseAssignment assignment = locked(id);
        if (assignment.getStatus() != AssignmentStatus.DRAFT) throw invalidTransition();
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        touch(assignment, actor);
        assignments.flush();
        return mapper.assignment(assignment);
    }

    @Override @Transactional(timeout = 15)
    public CourseAssignmentResponse close(AuthenticatedUser actor, long id) {
        requireFull(actor);
        CourseAssignment assignment = locked(id);
        if (assignment.getStatus() == AssignmentStatus.CLOSED) throw invalidTransition();
        assignment.setStatus(AssignmentStatus.CLOSED);
        touch(assignment, actor);
        assignments.flush();
        return mapper.assignment(assignment);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        CourseAssignment assignment = locked(id);
        assignment.setDeleted(true);
        touch(assignment, actor);
        assignments.flush();
    }

    private CourseAssignment locked(long id) {
        return assignments.findLiveForUpdate(id).orElseThrow(CourseAssignmentServiceImpl::notFound);
    }
    private CourseSection section(Long id) {
        return sections.findByIdAndDeletedFalse(id).orElseThrow(CourseSectionNotFoundException::new);
    }
    private void touch(CourseAssignment assignment, AuthenticatedUser actor) {
        assignment.setUpdatedBy(actor.userId());
        assignment.setUpdatedAt(now());
    }
    private static AssignmentStatus status(String value) {
        try {
            return AssignmentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new AcademicActivityFailure(400, "VALIDATION_ERROR", "The request is invalid.");
        }
    }
    private static AcademicActivityFailure notFound() {
        return new AcademicActivityFailure(404, "ASSIGNMENT_NOT_FOUND", "The requested assignment does not exist.");
    }
    private static AcademicActivityFailure invalidTransition() {
        return new AcademicActivityFailure(409, "ASSIGNMENT_STATUS_CONFLICT", "The assignment's status does not allow this change.");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
}
