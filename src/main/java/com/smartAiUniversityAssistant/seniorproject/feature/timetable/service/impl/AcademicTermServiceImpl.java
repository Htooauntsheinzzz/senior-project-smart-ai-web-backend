package com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.repository.SemesterRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.entity.AcademicTerm;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception.TimetableFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.mapper.TimetableMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.repository.AcademicTermRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.service.AcademicTermService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Year-specific offerings of a semester (spec file 42, Part C and Part J). Dates are stored as given, never derived. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class AcademicTermServiceImpl implements AcademicTermService {
    public static final Map<String, String> SORT = Map.of("id", "id", "startDate", "startDate", "endDate", "endDate", "status", "status",
            "academicYearCode", "academicYearCode", "academicYearLabel", "academicYearLabel", "createdAt", "createdAt");
    public static final Set<String> FILTERS = Set.of("semesterId", "status", "academicYearCode");

    private final AcademicTermRepository terms;
    private final SemesterRepository semesters;
    private final TimetableMapper mapper;
    private final TimetableWriteSupport support;

    public AcademicTermServiceImpl(AcademicTermRepository terms, SemesterRepository semesters, TimetableMapper mapper, TimetableWriteSupport support) {
        this.terms = terms;
        this.semesters = semesters;
        this.mapper = mapper;
        this.support = support;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public TimetablePageResponse<AcademicTermResponse> list(TimetablePageQuery query) {
        Long semesterId = query.id("semesterId");
        AcademicTermStatus status = query.enumValue("status", AcademicTermStatus.class);
        String code = query.text("academicYearCode");
        Specification<AcademicTerm> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (semesterId != null) predicates.add(cb.equal(root.get("semester").get("id"), semesterId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (code != null) predicates.add(cb.equal(root.get("academicYearCode"), code));
            if (query.search() != null) predicates.add(cb.or(TimetableWriteSupport.contains(cb, root.get("academicYearCode"), query.search()),
                    TimetableWriteSupport.contains(cb, root.get("academicYearLabel"), query.search()),
                    TimetableWriteSupport.contains(cb, root.get("semester").get("semesterNameEn"), query.search()),
                    TimetableWriteSupport.contains(cb, root.get("semester").get("semesterNameTh"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return query.response(terms.findAll(specification, query.pageable()), mapper::term);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public AcademicTermResponse detail(long id) {
        return mapper.term(terms.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::termNotFound));
    }

    @Override @Transactional(timeout = 15)
    public AcademicTermResponse create(AuthenticatedUser actor, AcademicTermRequest request) {
        requireFull(actor);
        support.lockTimeout();
        var term = new AcademicTerm();
        apply(term, request, 0L);
        term.setStatus(request.status() == null ? AcademicTermStatus.PLANNED : request.status());
        term.setCreatedBy(actor.userId());
        term.setCreatedAt(support.now());
        try {
            return mapper.term(terms.saveAndFlush(term));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @Transactional(timeout = 15)
    public AcademicTermResponse update(AuthenticatedUser actor, long id, AcademicTermRequest request) {
        requireFull(actor);
        support.lockTimeout();
        AcademicTerm term = terms.findLiveForUpdate(id).orElseThrow(TimetableFailure::termNotFound);
        apply(term, request, id);
        if (request.status() != null) term.setStatus(request.status());
        touch(term, actor);
        try {
            terms.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.term(terms.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::termNotFound));
    }

    @Override @Transactional(timeout = 15)
    public AcademicTermResponse status(AuthenticatedUser actor, long id, AcademicTermStatusRequest request) {
        requireFull(actor);
        support.lockTimeout();
        AcademicTerm term = terms.findLiveForUpdate(id).orElseThrow(TimetableFailure::termNotFound);
        term.setStatus(request.status());
        touch(term, actor);
        terms.flush();
        return mapper.term(terms.findByIdAndDeletedFalse(id).orElseThrow(TimetableFailure::termNotFound));
    }

    // A term that course sections still point at cannot vanish from their timetable (spec file 42, section 25).
    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        support.lockTimeout();
        AcademicTerm term = terms.findLiveForUpdate(id).orElseThrow(TimetableFailure::termNotFound);
        if (terms.isReferencedByLiveSections(id)) throw TimetableFailure.termInUse();
        term.setDeleted(true);
        touch(term, actor);
        terms.flush();
    }

    private void apply(AcademicTerm term, AcademicTermRequest request, long id) {
        Semester semester = semesters.findByIdForUpdate(request.semesterId()).orElseThrow(TimetableFailure::semesterNotFound);
        if (semester.isDeleted()) throw TimetableFailure.semesterNotFound();
        if (request.endDate().isBefore(request.startDate())) throw TimetableFailure.invalidDateRange("The term end date must not be before its start date.");
        if (terms.existsBySemesterIdAndAcademicYearCodeAndIdNot(semester.getId(), request.academicYearCode(), id)) throw TimetableFailure.termDuplicate();
        term.setSemester(semester);
        term.setAcademicYearCode(request.academicYearCode());
        term.setAcademicYearLabel(request.academicYearLabel());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
    }
    private void touch(AcademicTerm term, AuthenticatedUser actor) {
        term.setUpdatedBy(actor.userId());
        term.setUpdatedAt(support.now());
    }
    private static void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
}
