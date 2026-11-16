package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.exception.AcademicResultFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.mapper.CourseResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.service.CourseResultService;
import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.EnrollmentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ACADEMIC_ADMIN')")
public class CourseResultServiceImpl implements CourseResultService {
    private final StudentCourseResultRepository results;
    private final StudentCourseResultAuditRepository audits;
    private final GradeDefinitionRepository grades;
    private final EnrollmentRepository enrollments;
    private final CourseResultMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public CourseResultServiceImpl(StudentCourseResultRepository results, StudentCourseResultAuditRepository audits,
            GradeDefinitionRepository grades, EnrollmentRepository enrollments, CourseResultMapper mapper, Clock clock,
            EntityManager entityManager) {
        this.results = results;
        this.audits = audits;
        this.grades = grades;
        this.enrollments = enrollments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CourseResultPageResponse list(AuthenticatedUser actor, CourseResultListQuery query) {
        requireFull(actor);
        return mapper.page(results.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(timeout = 15)
    public CourseResultResponse create(AuthenticatedUser actor, CourseResultCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        Enrollment enrollment = enrollments.findByIdForUpdate(request.enrollmentId())
                .orElseThrow(EnrollmentNotFoundException::new);
        requireFinalizable(enrollment);
        if (results.existsByEnrollmentId(enrollment.getId())) throw alreadyExists();
        GradeDefinition grade = usableGrade(request.gradeDefinitionId());
        var result = new StudentCourseResult();
        result.setEnrollment(enrollment);
        result.setGradeDefinition(grade);
        result.setCreditsAttempted(currentCredits(enrollment));
        result.setStatus(CourseResultStatus.DRAFT);
        result.setCreatedBy(actor.userId());
        result.setCreatedAt(now());
        try {
            results.saveAndFlush(result);
        } catch (DataIntegrityViolationException e) {
            throw alreadyExists();
        }
        audit(result, CourseResultAuditAction.CREATED, null, null, null, actor);
        return mapper.response(result);
    }

    @Override @Transactional(timeout = 15)
    public CourseResultResponse update(AuthenticatedUser actor, long id, CourseResultUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        StudentCourseResult result = locked(id);
        boolean finalized = result.getStatus() == CourseResultStatus.FINALIZED;
        if (finalized && request.correctionReason() == null) throw new AcademicResultFailure(400,
                "CORRECTION_REASON_REQUIRED", "A finalized result can be corrected only with a correction reason.");
        GradeDefinition grade = usableGrade(request.gradeDefinitionId());
        Long previous = result.getGradeDefinition().getId();
        if (previous.equals(grade.getId())) return mapper.response(result);
        result.setGradeDefinition(grade);
        result.setUpdatedBy(actor.userId());
        result.setUpdatedAt(now());
        results.flush();
        audit(result, finalized ? CourseResultAuditAction.CORRECTED : CourseResultAuditAction.UPDATED, previous,
                result.getStatus(), request.correctionReason(), actor);
        return mapper.response(result);
    }

    @Override @Transactional(timeout = 15)
    public CourseResultResponse finalizeResult(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        StudentCourseResult result = locked(id);
        if (result.getStatus() == CourseResultStatus.FINALIZED) throw new AcademicResultFailure(409,
                "COURSE_RESULT_ALREADY_FINALIZED", "This course result is already finalized.");
        Enrollment enrollment = result.getEnrollment();
        requireFinalizable(enrollment);
        GradeDefinition grade = usableGrade(result.getGradeDefinition().getId());
        LocalDateTime now = now();
        // The snapshot: later edits to the Course's credit hours no longer touch this attempt.
        result.setCreditsAttempted(currentCredits(enrollment));
        result.setGradeDefinition(grade);
        result.setStatus(CourseResultStatus.FINALIZED);
        result.setFinalizedAt(now);
        result.setFinalizedBy(actor.userId());
        result.setUpdatedBy(actor.userId());
        result.setUpdatedAt(now);
        results.flush();
        audit(result, CourseResultAuditAction.FINALIZED, grade.getId(), CourseResultStatus.DRAFT, null, actor);
        return mapper.response(result);
    }

    private StudentCourseResult locked(long id) {
        return results.findByIdForUpdate(id).orElseThrow(() -> new AcademicResultFailure(404,
                "COURSE_RESULT_NOT_FOUND", "The requested course result does not exist."));
    }

    // Enrollment, Student and Course all exist and the enrollment reached a state that can carry a result.
    private void requireFinalizable(Enrollment enrollment) {
        if (enrollment.isDeleted()) throw new EnrollmentNotFoundException();
        if (enrollment.getStudent().isDeleted()) throw new StudentNotFoundException();
        if (enrollment.getCourseSection().getCourse().isDeleted()) throw new CourseNotFoundException();
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
            throw new AcademicResultFailure(409, "COURSE_RESULT_ENROLLMENT_NOT_ELIGIBLE",
                    "Only an active or withdrawn enrollment can carry a course result.");
    }

    private GradeDefinition usableGrade(Long id) {
        GradeDefinition grade = grades.findWithSchemeById(id).orElseThrow(() -> new AcademicResultFailure(404,
                "GRADE_DEFINITION_NOT_FOUND", "The requested grade definition does not exist."));
        if (!grade.isActive() || !grade.getGradingScheme().isActive()) throw new AcademicResultFailure(400,
                "GRADE_DEFINITION_NOT_USABLE", "The grade definition or its grading scheme is not active.");
        return grade;
    }

    private static BigDecimal currentCredits(Enrollment enrollment) {
        return BigDecimal.valueOf(enrollment.getCourseSection().getCourse().getCreditHours());
    }

    private void audit(StudentCourseResult result, CourseResultAuditAction action, Long previousGrade,
            CourseResultStatus previousStatus, String reason, AuthenticatedUser actor) {
        var entry = new StudentCourseResultAudit();
        entry.setResultId(result.getId());
        entry.setAction(action);
        entry.setPreviousGradeDefinitionId(previousGrade);
        entry.setNewGradeDefinitionId(result.getGradeDefinition().getId());
        entry.setPreviousStatus(previousStatus);
        entry.setNewStatus(result.getStatus());
        entry.setCreditsAttempted(result.getCreditsAttempted());
        entry.setReason(reason);
        entry.setChangedBy(actor.userId());
        entry.setChangedAt(now());
        audits.save(entry);
    }

    private Specification<StudentCourseResult> specification(CourseResultListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var enrollment = root.get("enrollment");
            if (query.enrollmentId() != null) predicates.add(cb.equal(enrollment.get("id"), query.enrollmentId()));
            if (query.studentId() != null) predicates.add(cb.equal(enrollment.get("student").get("id"), query.studentId()));
            if (query.semesterId() != null)
                predicates.add(cb.equal(enrollment.get("courseSection").get("semester").get("id"), query.semesterId()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AcademicResultFailure alreadyExists() {
        return new AcademicResultFailure(409, "COURSE_RESULT_ALREADY_EXISTS", "This enrollment already has a course result.");
    }
    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new AcademicResultFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
