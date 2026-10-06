package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.mapper.StudentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
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
public class StudentServiceImpl implements StudentService {
    private final StudentRepository students;
    private final StudentCredentialRepository credentials;
    private final StudentAcademicValidator academics;
    private final StudentCredentialService credentialService;
    private final StudentCacheService cache;
    private final StudentMapper mapper;
    private final Clock clock;

    public StudentServiceImpl(StudentRepository students, StudentCredentialRepository credentials,
            StudentAcademicValidator academics, StudentCredentialService credentialService,
            StudentCacheService cache, StudentMapper mapper, Clock clock) {
        this.students = students;
        this.credentials = credentials;
        this.academics = academics;
        this.credentialService = credentialService;
        this.cache = cache;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override @Transactional(timeout = 15)
    public StudentResponse create(AuthenticatedUser actor, StudentCreateRequest request) {
        requireFull(actor);
        // PENDING is reserved for profile-only provisioning; a full account always has a credential.
        StudentAccountStatus status = StudentAccountStatus.parse(request.accountStatus());
        if (status == StudentAccountStatus.PENDING) throw new InvalidStudentAccountStatusException();
        String hash = credentialService.encodeNewPassword(request.temporaryPassword(), request.confirmPassword());
        var placement = academics.validate(request.facultyId(), request.departmentId(), request.programId(),
                request.semesterId(), null);
        if (students.existsByStudentCode(request.studentCode())) throw new StudentCodeAlreadyExistsException();
        if (students.existsByUniversityEmailIgnoreCase(request.universityEmail()))
            throw new StudentEmailAlreadyExistsException();
        var student = new Student();
        apply(student, request.studentCode(), request.universityEmail(), request.firstName(), request.lastName(),
                request.phoneNumber(), request.dateOfBirth(), placement, request.academicYear(), request.enrollmentYear());
        student.setAccountStatus(status.name());
        student.setDeleted(false);
        student.setCreatedBy(actor.userId());
        student.setCreatedAt(now());
        try {
            students.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw StudentConstraints.translate(e);
        }
        // A credential failure propagates and rolls the student insert back with it.
        var credential = credentialService.create(student, hash,
                request.forcePasswordChange() == null || request.forcePasswordChange());
        return mapper.response(student, credential);
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public StudentPageResponse list(AuthenticatedUser actor, StudentListQuery query) {
        requireFull(actor);
        return mapper.page(students.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public StudentSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        return new StudentSummaryResponse(students.countByDeletedFalse(),
                students.countByAccountStatusAndDeletedFalse(StudentAccountStatus.ACTIVE.name()),
                students.countByAccountStatusAndDeletedFalse(StudentAccountStatus.PENDING.name()),
                students.countByAccountStatusInAndDeletedFalse(
                        List.of(StudentAccountStatus.INACTIVE.name(), StudentAccountStatus.SUSPENDED.name())));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        Student student = students.findByIdAndDeletedFalse(id).orElseThrow(StudentNotFoundException::new);
        return mapper.response(student, credentials.findByStudentId(id).orElse(null));
    }

    @Override @Transactional(timeout = 15)
    public StudentResponse update(AuthenticatedUser actor, long id, StudentUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        Student student = locked(id);
        boolean credentialExists = credentials.existsByStudentId(id);
        StudentAccountStatus requested = StudentAccountStatus.parse(request.accountStatus());
        if (!credentialExists) {
            // Without a credential only PENDING is valid; PENDING → ACTIVE happens through registration only.
            if (!StudentAccountStatus.PENDING.name().equals(student.getAccountStatus()))
                throw new InvalidStudentAccountStateException();
            if (requested != StudentAccountStatus.PENDING) throw new InvalidStudentAccountStatusException();
        } else if (requested == StudentAccountStatus.PENDING) {
            throw new InvalidStudentAccountStatusException();
        }
        var placement = academics.validate(request.facultyId(), request.departmentId(), request.programId(),
                request.semesterId(), student);
        if (students.existsByStudentCodeAndIdNot(request.studentCode(), id)) throw new StudentCodeAlreadyExistsException();
        if (students.existsByUniversityEmailIgnoreCaseAndIdNot(request.universityEmail(), id))
            throw new StudentEmailAlreadyExistsException();
        apply(student, request.studentCode(), request.universityEmail(), request.firstName(), request.lastName(),
                request.phoneNumber(), request.dateOfBirth(), placement, request.academicYear(), request.enrollmentYear());
        student.setAccountStatus(requested.name());
        student.setUpdatedBy(actor.userId());
        student.setUpdatedAt(now());
        try {
            students.flush();
        } catch (DataIntegrityViolationException e) {
            throw StudentConstraints.translate(e);
        }
        if (requested == StudentAccountStatus.INACTIVE || requested == StudentAccountStatus.SUSPENDED)
            cache.revokeAfterCommit(id);
        else
            cache.evictAfterCommit(id);
        return mapper.response(student, credentials.findByStudentId(id).orElse(null));
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        Student student = locked(id);
        // Soft delete keeps account_status and the credential; is_deleted alone blocks access.
        student.setDeleted(true);
        student.setUpdatedBy(actor.userId());
        student.setUpdatedAt(now());
        students.flush();
        cache.revokeAfterCommit(id);
    }

    @Override
    public void resetPassword(AuthenticatedUser actor, long id, StudentPasswordResetRequest request) {
        requireFull(actor);
        positiveId(id);
        credentialService.resetPassword(actor, id, request);
    }

    private Student locked(long id) {
        Student student = students.findByIdForUpdate(id).orElseThrow(StudentNotFoundException::new);
        if (student.isDeleted()) throw new StudentNotFoundException();
        return student;
    }

    private void apply(Student student, String code, String email, String firstName, String lastName, String phone,
            LocalDate dateOfBirth, StudentAcademicValidator.AcademicPlacement placement,
            Integer academicYear, Integer enrollmentYear) {
        student.setStudentCode(code);
        student.setUniversityEmail(email);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setPhoneNumber(phone);
        student.setDateOfBirth(dateOfBirth);
        student.setFaculty(placement.faculty());
        student.setDepartment(placement.department());
        student.setProgram(placement.program());
        student.setSemester(placement.semester());
        student.setAcademicYear(academicYear == null ? null : academicYear.shortValue());
        student.setEnrollmentYear(enrollmentYear == null ? null : enrollmentYear.shortValue());
    }

    private Specification<Student> specification(StudentListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.facultyId() != null) predicates.add(cb.equal(root.get("faculty").get("id"), query.facultyId()));
            if (query.departmentId() != null)
                predicates.add(cb.equal(root.get("department").get("id"), query.departmentId()));
            if (query.programId() != null) predicates.add(cb.equal(root.get("program").get("id"), query.programId()));
            if (query.semesterId() != null) predicates.add(cb.equal(root.get("semester").get("id"), query.semesterId()));
            if (query.academicYear() != null)
                predicates.add(cb.equal(root.get("academicYear"), query.academicYear().shortValue()));
            if (query.enrollmentYear() != null)
                predicates.add(cb.equal(root.get("enrollmentYear"), query.enrollmentYear().shortValue()));
            if (query.status() != null) predicates.add(cb.equal(root.get("accountStatus"), query.status()));
            if (query.search() != null) {
                String literal = "%" + query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("studentCode")), literal, '\\'),
                        cb.like(cb.lower(root.get("universityEmail")), literal, '\\'),
                        cb.like(cb.lower(root.get("firstName")), literal, '\\'),
                        cb.like(cb.lower(root.get("lastName")), literal, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new StudentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
