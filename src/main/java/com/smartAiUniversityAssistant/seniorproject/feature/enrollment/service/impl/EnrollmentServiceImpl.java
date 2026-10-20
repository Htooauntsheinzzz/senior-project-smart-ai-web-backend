package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseSectionStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.CourseSectionNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository.CourseSectionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.mapper.EnrollmentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.EnrollmentService;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.StudentRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class EnrollmentServiceImpl implements EnrollmentService {
    private static final String UNIQUE_STUDENT_SECTION = "uk_enrollments_student_course_section";
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final CourseSectionRepository sections;
    private final EnrollmentMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public EnrollmentServiceImpl(EnrollmentRepository enrollments, StudentRepository students,
            CourseSectionRepository sections, EnrollmentMapper mapper, Clock clock, EntityManager entityManager) {
        this.enrollments = enrollments;
        this.students = students;
        this.sections = sections;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public EnrollmentResponse create(AuthenticatedUser actor, EnrollmentCreateRequest request) {
        requireFull(actor);
        EnrollmentStatus status = EnrollmentStatus.parse(request.status());
        lockTimeout();
        // Lock order is always Student then Course Section, so concurrent requests serialize without deadlocks.
        Student student = eligibleStudent(request.studentId());
        CourseSection section = availableSection(request.courseSectionId());
        requireNotDuplicate(student, section, 0L);
        if (status.holdsSeat()) {
            requireNoCourseConflict(student, section, 0L);
            requireSeat(section);
        }
        var enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourseSection(section);
        enrollment.setEnrollmentDate(request.enrollmentDate() != null ? request.enrollmentDate() : LocalDate.now(clock));
        enrollment.setStatus(status);
        enrollment.setDeleted(false);
        enrollment.setCreatedBy(actor.userId());
        enrollment.setCreatedAt(now());
        try {
            enrollments.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return response(enrollment);
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public EnrollmentPageResponse list(AuthenticatedUser actor, EnrollmentListQuery query) {
        requireFull(actor);
        return mapper.page(enrollments.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public EnrollmentSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        var counts = new EnumMap<EnrollmentStatus, Long>(EnrollmentStatus.class);
        enrollments.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long active = counts.getOrDefault(EnrollmentStatus.ACTIVE, 0L);
        long pending = counts.getOrDefault(EnrollmentStatus.PENDING, 0L);
        long withdrawn = counts.getOrDefault(EnrollmentStatus.WITHDRAWN, 0L);
        long dropped = counts.getOrDefault(EnrollmentStatus.DROPPED, 0L);
        return new EnrollmentSummaryResponse(active + pending + withdrawn + dropped, active, pending, withdrawn, dropped,
                withdrawn + dropped);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public EnrollmentResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return response(enrollments.findByIdAndDeletedFalse(id).orElseThrow(EnrollmentNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public EnrollmentResponse update(AuthenticatedUser actor, long id, EnrollmentUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        EnrollmentStatus target = EnrollmentStatus.parse(request.status());
        lockTimeout();
        Enrollment enrollment = locked(id);
        requireTransition(enrollment.getStatus(), target);
        boolean studentChanged = !enrollment.getStudent().getId().equals(request.studentId());
        boolean sectionChanged = !enrollment.getCourseSection().getId().equals(request.courseSectionId());
        if (studentChanged || sectionChanged) {
            // Only a PENDING enrollment may be moved to another Student or Course Section.
            if (enrollment.getStatus() != EnrollmentStatus.PENDING) throw new EnrollmentUpdateNotAllowedException();
            Student student = studentChanged ? eligibleStudent(request.studentId())
                    : lockedStudent(enrollment.getStudent().getId());
            CourseSection section = sectionChanged ? availableSection(request.courseSectionId())
                    : lockedSection(enrollment.getCourseSection().getId());
            requireNotDuplicate(student, section, id);
            if (target.holdsSeat()) {
                requireNoCourseConflict(student, section, id);
                if (sectionChanged) requireSeat(section);
            }
            enrollment.setStudent(student);
            enrollment.setCourseSection(section);
        }
        acquireSeatIfNeeded(enrollment, target);
        enrollment.setStatus(target);
        if (request.enrollmentDate() != null) enrollment.setEnrollmentDate(request.enrollmentDate());
        enrollment.setUpdatedBy(actor.userId());
        enrollment.setUpdatedAt(now());
        try {
            enrollments.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return response(enrollment);
    }

    @Override @Transactional(timeout = 15)
    public EnrollmentResponse updateStatus(AuthenticatedUser actor, long id, EnrollmentStatusUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        EnrollmentStatus target = EnrollmentStatus.parse(request.status());
        lockTimeout();
        Enrollment enrollment = locked(id);
        requireTransition(enrollment.getStatus(), target);
        if (enrollment.getStatus() != target) {
            acquireSeatIfNeeded(enrollment, target);
            enrollment.setStatus(target);
            enrollment.setUpdatedBy(actor.userId());
            enrollment.setUpdatedAt(now());
            enrollments.flush();
        }
        return response(enrollment);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Enrollment enrollment = locked(id);
        enrollment.setDeleted(true);
        enrollment.setUpdatedBy(actor.userId());
        enrollment.setUpdatedAt(now());
        enrollments.flush();
    }

    private Enrollment locked(long id) {
        Enrollment enrollment = enrollments.findByIdForUpdate(id).orElseThrow(EnrollmentNotFoundException::new);
        if (enrollment.isDeleted()) throw new EnrollmentNotFoundException();
        return enrollment;
    }

    private Student lockedStudent(Long id) {
        return students.findByIdForUpdate(id).orElseThrow(StudentNotFoundException::new);
    }

    private Student eligibleStudent(Long id) {
        Student student = lockedStudent(id);
        if (student.isDeleted() || !StudentAccountStatus.ACTIVE.name().equals(student.getAccountStatus()))
            throw new EnrollmentStudentNotEligibleException();
        return student;
    }

    private CourseSection lockedSection(Long id) {
        CourseSection section = sections.findByIdForUpdate(id).orElseThrow(CourseSectionNotFoundException::new);
        if (section.isDeleted()) throw new CourseSectionNotFoundException();
        return section;
    }

    private CourseSection availableSection(Long id) {
        CourseSection section = lockedSection(id);
        if (!CourseSectionStatus.ACTIVE.getValue().equals(section.getStatus()))
            throw new CourseSectionNotAvailableException();
        return section;
    }

    private void requireNotDuplicate(Student student, CourseSection section, long excludeId) {
        if (enrollments.existsByStudentIdAndCourseSectionIdAndIdNot(student.getId(), section.getId(), excludeId))
            throw new EnrollmentAlreadyExistsException();
    }

    private void requireNoCourseConflict(Student student, CourseSection section, long excludeId) {
        if (enrollments.countConflictingEnrollments(student.getId(), section.getCourse().getId(),
                section.getSemester().getId(), EnrollmentStatus.SEAT_HOLDING, excludeId) > 0)
            throw new StudentAlreadyEnrolledInCourseException();
    }

    // Caller must hold the Course Section row lock so the count cannot change before commit.
    private void requireSeat(CourseSection section) {
        if (occupied(section) >= section.getCapacity()) throw new CourseSectionFullException();
    }

    private void requireTransition(EnrollmentStatus current, EnrollmentStatus target) {
        if (!current.canTransitionTo(target)) throw new InvalidEnrollmentStatusTransitionException();
    }

    // No allowed transition currently moves from a released seat back to a held one; this keeps capacity
    // safe if the transition matrix is ever widened.
    private void acquireSeatIfNeeded(Enrollment enrollment, EnrollmentStatus target) {
        if (enrollment.getStatus().holdsSeat() || !target.holdsSeat()) return;
        CourseSection section = availableSection(enrollment.getCourseSection().getId());
        requireNoCourseConflict(enrollment.getStudent(), section, enrollment.getId());
        requireSeat(section);
    }

    private long occupied(CourseSection section) {
        return enrollments.countOccupiedSeats(section.getId(), EnrollmentStatus.SEAT_HOLDING);
    }

    private EnrollmentResponse response(Enrollment enrollment) {
        return mapper.response(enrollment, occupied(enrollment.getCourseSection()));
    }

    private Specification<Enrollment> specification(EnrollmentListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var student = root.get("student");
            var section = root.get("courseSection");
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.studentId() != null) predicates.add(cb.equal(student.get("id"), query.studentId()));
            if (query.courseSectionId() != null) predicates.add(cb.equal(section.get("id"), query.courseSectionId()));
            if (query.courseId() != null) predicates.add(cb.equal(section.get("course").get("id"), query.courseId()));
            if (query.semesterId() != null)
                predicates.add(cb.equal(section.get("semester").get("id"), query.semesterId()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.enrollmentDate() != null) predicates.add(cb.equal(root.get("enrollmentDate"), query.enrollmentDate()));
            if (query.search() != null) {
                String pattern = "%" + query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(student.get("studentCode")), pattern, '\\'),
                        cb.like(cb.lower(student.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(student.get("lastName")), pattern, '\\'),
                        cb.like(cb.lower(student.get("universityEmail")), pattern, '\\'),
                        cb.like(cb.lower(section.get("course").get("courseCode")), pattern, '\\'),
                        cb.like(cb.lower(section.get("course").get("courseName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && UNIQUE_STUDENT_SECTION.equals(violation.getConstraintName()))
                return new EnrollmentAlreadyExistsException();
        }
        return new EnrollmentFailure(409, "DATABASE_CONSTRAINT_VIOLATION",
                "The request conflicts with existing data.");
    }

    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new EnrollmentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
