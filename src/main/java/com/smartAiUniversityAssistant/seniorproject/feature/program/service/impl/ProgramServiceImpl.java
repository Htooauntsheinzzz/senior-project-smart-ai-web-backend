package com.smartAiUniversityAssistant.seniorproject.feature.program.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.InvalidFacultyException;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.FacultyNotFoundException;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.program.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.program.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.program.mapper.ProgramMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.program.service.ProgramService;
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
public class ProgramServiceImpl implements ProgramService {
    private final ProgramRepository programs;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final ProgramMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public ProgramServiceImpl(ProgramRepository programs, FacultyRepository faculties,
            DepartmentRepository departments, ProgramMapper mapper, Clock clock, EntityManager entityManager) {
        this.programs = programs;
        this.faculties = faculties;
        this.departments = departments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public ProgramResponse create(AuthenticatedUser actor, ProgramCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        duplicates(0L, request.programCode(), request.departmentId(), request.programName(), request.degreeLevel());
        Department department = selectedDepartment(request.departmentId());
        Faculty faculty = selectedFaculty(request.facultyId());
        requireFacultyMatch(department, faculty);
        var program = new Program();
        program.setProgramCode(request.programCode());
        program.setProgramName(request.programName());
        program.setDegreeLevel(request.degreeLevel());
        program.setDepartment(department);
        program.setDurationYears(request.durationYears());
        program.setTotalCredits(request.totalCredits());
        program.setActive(request.isActive());
        program.setDeleted(false);
        program.setCreatedBy(actor.userId());
        program.setCreatedAt(now());
        try {
            return mapper.response(programs.saveAndFlush(program));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public ProgramPageResponse list(AuthenticatedUser actor, ProgramListQuery query) {
        requireFull(actor);
        return mapper.page(programs.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public ProgramResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return mapper.response(programs.findByIdAndDeletedFalse(id).orElseThrow(ProgramNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public ProgramResponse update(AuthenticatedUser actor, long id, ProgramUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Program program = locked(id);
        duplicates(id, request.programCode(), request.departmentId(), request.programName(), request.degreeLevel());
        Department department = selectedDepartment(request.departmentId());
        Faculty faculty = selectedFaculty(request.facultyId());
        requireFacultyMatch(department, faculty);
        program.setProgramCode(request.programCode());
        program.setProgramName(request.programName());
        program.setDegreeLevel(request.degreeLevel());
        program.setDepartment(department);
        program.setDurationYears(request.durationYears());
        program.setTotalCredits(request.totalCredits());
        program.setActive(request.isActive());
        program.setUpdatedBy(actor.userId());
        program.setUpdatedAt(now());
        try {
            programs.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(program);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Program program = locked(id);
        program.setDeleted(true);
        program.setUpdatedBy(actor.userId());
        program.setUpdatedAt(now());
        programs.flush();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public ProgramSummaryResponse summary(AuthenticatedUser actor) {
        requireFull(actor);
        long total = programs.countByDeletedFalse();
        long active = programs.countByActiveTrueAndDeletedFalse();
        return new ProgramSummaryResponse(total, active, total - active);
    }

    private Program locked(long id) {
        Program program = programs.findByIdForUpdate(id).orElseThrow(ProgramNotFoundException::new);
        if (program.isDeleted()) throw new ProgramNotFoundException();
        return program;
    }

    private Faculty selectedFaculty(Long id) {
        Faculty faculty = faculties.findByIdForUpdate(id).orElseThrow(FacultyNotFoundException::new);
        if (faculty.isDeleted()) throw new FacultyNotFoundException();
        if (!faculty.isActive()) throw new InvalidFacultyException();
        return faculty;
    }

    private Department selectedDepartment(Long id) {
        Department department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (department.isDeleted()) throw new DepartmentNotFoundException();
        if (!department.isActive()) throw new InvalidProgramDepartmentException();
        return department;
    }

    private void requireFacultyMatch(Department department, Faculty faculty) {
        if (!department.getFaculty().getId().equals(faculty.getId())) throw new InvalidProgramDepartmentException();
    }

    private void duplicates(long id, String code, Long departmentId, String name, String degreeLevel) {
        // Global/scoped UNIQUE constraints reserve identifiers even after soft deletion.
        if (programs.existsByProgramCodeAndIdNot(code, id)) throw new ProgramCodeAlreadyExistsException();
        if (programs.existsByDepartmentIdAndProgramNameAndDegreeLevelAndIdNot(departmentId, name, degreeLevel, id))
            throw new ProgramAlreadyExistsException();
    }

    private Specification<Program> specification(ProgramListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.facultyId() != null)
                predicates.add(cb.equal(root.get("department").get("faculty").get("id"), query.facultyId()));
            if (query.departmentId() != null)
                predicates.add(cb.equal(root.get("department").get("id"), query.departmentId()));
            if (query.active() != null) predicates.add(cb.equal(root.get("active"), query.active()));
            if (query.degreeLevel() != null)
                predicates.add(cb.equal(cb.lower(root.get("degreeLevel")), query.degreeLevel().toLowerCase(Locale.ROOT)));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("programCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("programName")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_programs_program_code".equals(violation.getConstraintName()))
                    return new ProgramCodeAlreadyExistsException();
                if ("uk_programs_department_name_degree".equals(violation.getConstraintName()))
                    return new ProgramAlreadyExistsException();
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
        if (id <= 0) throw new ProgramFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
