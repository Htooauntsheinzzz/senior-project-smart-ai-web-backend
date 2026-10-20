package com.smartAiUniversityAssistant.seniorproject.feature.semester.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.mapper.SemesterMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.repository.SemesterRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.service.SemesterService;
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
public class SemesterServiceImpl implements SemesterService {
    private final SemesterRepository semesters;
    private final SemesterMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public SemesterServiceImpl(SemesterRepository semesters, SemesterMapper mapper, Clock clock, EntityManager entityManager) {
        this.semesters = semesters;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public SemesterResponse create(AuthenticatedUser actor, SemesterCreateRequest request) {
        requireFull(actor);
        duplicate(0L, request.academicYear(), request.semesterNameTh(), request.semesterNameEn());
        var semester = new Semester();
        semester.setAcademicYear(request.academicYear());
        semester.setSemesterNameTh(request.semesterNameTh());
        semester.setSemesterNameEn(request.semesterNameEn());
        semester.setDeleted(false);
        semester.setCreatedBy(actor.userId());
        semester.setCreatedAt(now());
        try {
            return mapper.response(semesters.saveAndFlush(semester));
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public SemesterPageResponse list(AuthenticatedUser actor, SemesterListQuery query) {
        requireFull(actor);
        return mapper.page(semesters.findAll(specification(query), query.pageable()), query);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public SemesterResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return mapper.response(semesters.findByIdAndDeletedFalse(id).orElseThrow(SemesterNotFoundException::new));
    }

    @Override @Transactional(timeout = 15)
    public SemesterResponse update(AuthenticatedUser actor, long id, SemesterUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Semester semester = locked(id);
        duplicate(id, request.academicYear(), request.semesterNameTh(), request.semesterNameEn());
        semester.setAcademicYear(request.academicYear());
        semester.setSemesterNameTh(request.semesterNameTh());
        semester.setSemesterNameEn(request.semesterNameEn());
        semester.setUpdatedBy(actor.userId());
        semester.setUpdatedAt(now());
        try {
            semesters.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return mapper.response(semester);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Semester semester = locked(id);
        semester.setDeleted(true);
        semester.setUpdatedBy(actor.userId());
        semester.setUpdatedAt(now());
        semesters.flush();
    }

    private Semester locked(long id) {
        Semester semester = semesters.findByIdForUpdate(id).orElseThrow(SemesterNotFoundException::new);
        if (semester.isDeleted()) throw new SemesterNotFoundException();
        return semester;
    }

    private void duplicate(long id, int academicYear, String nameTh, String nameEn) {
        // The UNIQUE constraint reserves year + name combinations even after soft deletion.
        if (semesters.existsByAcademicYearAndSemesterNameThAndSemesterNameEnAndIdNot(academicYear, nameTh, nameEn, id))
            throw new SemesterAlreadyExistsException();
    }

    private Specification<Semester> specification(SemesterListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.academicYear() != null) predicates.add(cb.equal(root.get("academicYear"), query.academicYear()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("semesterNameTh")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("semesterNameEn")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_semesters_year_name_th_en".equals(violation.getConstraintName()))
                return new SemesterAlreadyExistsException();
        }
        return exception;
    }

    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw new SemesterFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
