package com.smartAiUniversityAssistant.seniorproject.feature.department.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.DepartmentUserAssignments;
import com.smartAiUniversityAssistant.seniorproject.feature.department.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.mapper.DepartmentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentService;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.FacultyNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
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
public class DepartmentServiceImpl implements DepartmentService {
    private final DepartmentRepository departments;
    private final FacultyRepository faculties;
    private final DepartmentUserAssignments assignments;
    private final DepartmentMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public DepartmentServiceImpl(DepartmentRepository departments, FacultyRepository faculties,
            DepartmentUserAssignments assignments, DepartmentMapper mapper, Clock clock, EntityManager entityManager) {
        this.departments = departments;
        this.faculties = faculties;
        this.assignments = assignments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public DepartmentResponse create(AuthenticatedUser actor, DepartmentCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        Faculty faculty = selectedFaculty(request.facultyId());
        duplicates(0L, request.departmentCode(), request.departmentName(), faculty.getId());
        var department = new Department();
        department.setDepartmentCode(request.departmentCode());
        department.setDepartmentName(request.departmentName());
        department.setFaculty(faculty);
        department.setActive(request.isActive());
        department.setDeleted(false);
        department.setCreatedBy(actor.userId());
        department.setCreatedAt(now());
        try {
            return mapper.response(departments.saveAndFlush(department));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public DepartmentPageResponse list(AuthenticatedUser actor, DepartmentListQuery query) {
        requireFull(actor);
        return mapper.page(departments.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DepartmentResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return mapper.response(departments.findByIdAndDeletedFalse(id).orElseThrow(DepartmentNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public DepartmentResponse update(AuthenticatedUser actor, long id, DepartmentUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Department department = locked(id);
        Faculty faculty = selectedFaculty(request.facultyId());
        duplicates(id, request.departmentCode(), request.departmentName(), faculty.getId());
        department.setDepartmentCode(request.departmentCode());
        department.setDepartmentName(request.departmentName());
        department.setFaculty(faculty);
        department.setActive(request.isActive());
        department.setUpdatedBy(actor.userId());
        department.setUpdatedAt(now());
        try {
            departments.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(department);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Department department = locked(id);
        if (assignments.hasNonDeletedUsers(id)) throw new DepartmentInUseException();
        department.setDeleted(true);
        department.setUpdatedBy(actor.userId());
        department.setUpdatedAt(now());
        departments.flush();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public DepartmentSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        long total = departments.countByDeletedFalse();
        long active = departments.countByActiveTrueAndDeletedFalse();
        return new DepartmentSummaryResponse(total, active, total - active);
    }

    private Department locked(long id) {
        Department department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (department.isDeleted()) throw new DepartmentNotFoundException();
        return department;
    }

    private Faculty selectedFaculty(Long id) {
        Faculty faculty = faculties.findByIdForUpdate(id).orElseThrow(FacultyNotFoundException::new);
        if (faculty.isDeleted()) throw new FacultyNotFoundException();
        if (!faculty.isActive()) throw new InvalidFacultyException();
        return faculty;
    }

    private void duplicates(long id, String code, String name, long facultyId) {
        // Global/scoped UNIQUE constraints reserve identifiers even after soft deletion.
        if (departments.existsByDepartmentCodeAndIdNot(code, id)) throw new DepartmentCodeAlreadyExistsException();
        if (departments.existsByFacultyIdAndDepartmentNameAndIdNot(facultyId, name, id))
            throw new DepartmentNameAlreadyExistsException();
    }

    private Specification<Department> specification(DepartmentListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.facultyId() != null) predicates.add(cb.equal(root.get("faculty").get("id"), query.facultyId()));
            if (query.active() != null) predicates.add(cb.equal(root.get("active"), query.active()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("departmentCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("departmentName")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_departments_department_code".equals(violation.getConstraintName()))
                    return new DepartmentCodeAlreadyExistsException();
                if ("uk_departments_faculty_department_name".equals(violation.getConstraintName()))
                    return new DepartmentNameAlreadyExistsException();
            }
        }
        return exception;
    }

    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new DepartmentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
