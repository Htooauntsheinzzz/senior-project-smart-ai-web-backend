package com.smartAiUniversityAssistant.seniorproject.feature.faculty.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.mapper.FacultyMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository.FacultyRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.service.FacultyService;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentStatisticsService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class FacultyServiceImpl implements FacultyService {
    private final FacultyRepository faculties;
    private final FacultyMapper mapper;
    private final Clock clock;
    private final DepartmentStatisticsService departmentStatistics;

    public FacultyServiceImpl(FacultyRepository faculties, FacultyMapper mapper, Clock clock,
            DepartmentStatisticsService departmentStatistics) {
        this.faculties = faculties;
        this.mapper = mapper;
        this.clock = clock;
        this.departmentStatistics = departmentStatistics;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(timeout = 15)
    public FacultyResponse create(AuthenticatedUser actor, FacultyCreateRequest request) {
        requireFullSession(actor);
        if (faculties.existsByFacultyCode(request.facultyCode())) throw new FacultyCodeAlreadyExistsException();
        if (faculties.existsByFacultyNameEn(request.facultyNameEn())) throw new FacultyNameAlreadyExistsException();
        var faculty = new Faculty();
        faculty.setFacultyCode(request.facultyCode());
        faculty.setFacultyNameEn(request.facultyNameEn());
        faculty.setFacultyNameTh(request.facultyNameTh());
        faculty.setActive(request.isActive());
        faculty.setDeleted(false);
        faculty.setCreatedBy(actor.userId());
        faculty.setCreatedAt(utcNow());
        try {
            return mapper.response(faculties.saveAndFlush(faculty), 0);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public FacultyPageResponse list(AuthenticatedUser actor, FacultyListQuery query) {
        requireFullSession(actor);
        FacultyListQuery.Parsed parsed = query.parse();
        Page<Faculty> page = faculties.findAll(specification(parsed), pageRequest(parsed));
        var counts = departmentStatistics.countsByFaculty(page.getContent().stream().map(Faculty::getId).toList());
        return mapper.page(page, parsed, counts);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public FacultyResponse detail(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        return response(faculties.findByIdAndDeletedFalse(id).orElseThrow(FacultyNotFoundException::new));
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(timeout = 15)
    public FacultyResponse update(AuthenticatedUser actor, long id, FacultyUpdateRequest request) {
        requireFullSession(actor);
        requirePositiveId(id);
        Faculty faculty = faculties.findByIdForUpdate(id).orElseThrow(FacultyNotFoundException::new);
        if (faculty.isDeleted()) throw new FacultyNotFoundException();
        if (faculties.existsByFacultyCodeAndIdNot(request.facultyCode(), id))
            throw new FacultyCodeAlreadyExistsException();
        if (faculties.existsByFacultyNameEnAndIdNot(request.facultyNameEn(), id))
            throw new FacultyNameAlreadyExistsException();
        faculty.setFacultyCode(request.facultyCode());
        faculty.setFacultyNameEn(request.facultyNameEn());
        faculty.setFacultyNameTh(request.facultyNameTh());
        faculty.setActive(request.isActive());
        faculty.setUpdatedBy(actor.userId());
        faculty.setUpdatedAt(utcNow());
        try {
            faculties.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
        return response(faculty);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFullSession(actor);
        requirePositiveId(id);
        Faculty faculty = faculties.findByIdForUpdate(id).orElseThrow(FacultyNotFoundException::new);
        if (faculty.isDeleted()) throw new FacultyNotFoundException();
        faculty.setDeleted(true);
        faculty.setUpdatedBy(actor.userId());
        faculty.setUpdatedAt(utcNow());
        faculties.flush();
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public FacultySummaryResponse summary(AuthenticatedUser actor) {
        requireFullSession(actor);
        long total = faculties.countByDeletedFalse();
        long active = faculties.countByActiveTrueAndDeletedFalse();
        long inactive = faculties.countByActiveFalseAndDeletedFalse();
        return new FacultySummaryResponse(total, active, inactive, departmentStatistics.total());
    }

    private FacultyResponse response(Faculty faculty) {
        long count = departmentStatistics.countsByFaculty(List.of(faculty.getId())).getOrDefault(faculty.getId(), 0L);
        return mapper.response(faculty, count);
    }

    private Specification<Faculty> specification(FacultyListQuery.Parsed query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.search() != null) {
                String pattern = "%" + escapeLike(query.search().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("facultyCode")), pattern, '\\'),
                        cb.like(cb.lower(root.get("facultyNameEn")), pattern, '\\'),
                        cb.like(cb.lower(root.get("facultyNameTh")), pattern, '\\')));
            }
            if (query.active() != null) predicates.add(cb.equal(root.get("active"), query.active()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PageRequest pageRequest(FacultyListQuery.Parsed query) {
        List<Sort.Order> orders = new ArrayList<>();
        for (FacultyListQuery.Parsed.Order order : query.sort()) {
            Sort.Order sortOrder = new Sort.Order(order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC,
                    order.field().equals("isActive") ? "active" : order.field());
            if (order.field().equals("createdAt") || order.field().equals("updatedAt"))
                sortOrder = sortOrder.with(Sort.NullHandling.NULLS_LAST);
            orders.add(sortOrder);
        }
        return PageRequest.of(query.page(), query.size(), Sort.by(orders));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireFullSession(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }

    private void requirePositiveId(long id) {
        if (id <= 0) throw new FacultyQueryValidationException("The faculty ID must be a positive integer.");
    }

    private RuntimeException translateConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_faculties_faculty_code".equals(violation.getConstraintName()))
                    return new FacultyCodeAlreadyExistsException();
                if ("uk_faculties_faculty_name_en".equals(violation.getConstraintName()))
                    return new FacultyNameAlreadyExistsException();
                break;
            }
            cause = cause.getCause();
        }
        return exception;
    }
}
