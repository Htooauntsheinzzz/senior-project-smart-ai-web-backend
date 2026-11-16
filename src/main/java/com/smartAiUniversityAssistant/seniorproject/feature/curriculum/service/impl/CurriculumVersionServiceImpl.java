package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.service.impl;

import com.smartAiUniversityAssistant.seniorproject.common.AcademicYearLabel;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.enums.CurriculumStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.exception.CurriculumFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.mapper.CurriculumVersionMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.repository.CurriculumVersionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.service.CurriculumVersionService;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Curriculum versions of a program. Every status change locks the program row first, so creating, activating and
 * archiving versions of one program are serialized and the one-active-version index is never raced.
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CurriculumVersionServiceImpl implements CurriculumVersionService {
    private static final Pattern VERSION_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9._-]*$");
    private final CurriculumVersionRepository curriculums;
    private final ProgramRepository programs;
    private final AcademicDocumentRepository documents;
    private final CurriculumVersionMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public CurriculumVersionServiceImpl(CurriculumVersionRepository curriculums, ProgramRepository programs,
            AcademicDocumentRepository documents, CurriculumVersionMapper mapper, Clock clock, EntityManager entityManager) {
        this.curriculums = curriculums;
        this.programs = programs;
        this.documents = documents;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(timeout = 15)
    public CurriculumResponse create(AuthenticatedUser actor, CurriculumCreateRequest request) {
        requireFull(actor);
        lockTimeout();
        Fields fields = fields(request.versionCode(), request.versionName(), request.academicYearLabel(),
                request.description(), request.effectiveFrom(), request.effectiveTo());
        Program program = programs.findByIdForUpdate(request.programId()).orElseThrow(CurriculumFailure::invalidProgram);
        if (program.isDeleted() || !program.isActive()) throw CurriculumFailure.invalidProgram();
        if (curriculums.existsByProgramIdAndVersionCodeAndIdNot(program.getId(), fields.code(), 0L))
            throw CurriculumFailure.duplicate();
        var curriculum = new CurriculumVersion();
        curriculum.setProgram(program);
        apply(curriculum, fields);
        // A new version becomes ACTIVE only when the program has none; replacing one is the explicit activate call.
        curriculum.setStatus(curriculums.existsByProgramIdAndStatusAndDeletedFalse(program.getId(), CurriculumStatus.ACTIVE)
                ? CurriculumStatus.ARCHIVED : CurriculumStatus.ACTIVE);
        curriculum.setDeleted(false);
        curriculum.setCreatedBy(actor.userId());
        curriculum.setCreatedAt(now());
        try {
            return mapper.response(curriculums.saveAndFlush(curriculum), 0);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public CurriculumPageResponse list(AuthenticatedUser actor, CurriculumListQuery query) {
        requireFull(actor);
        var page = curriculums.findAll(specification(query), query.pageable());
        return mapper.page(page, query, documentCounts(page.getContent().stream().map(CurriculumVersion::getId).toList()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CurriculumResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return response(curriculums.findByIdAndDeletedFalse(id).orElseThrow(CurriculumFailure::notFound));
    }

    @Override @Transactional(timeout = 15)
    public CurriculumResponse update(AuthenticatedUser actor, long id, CurriculumUpdateRequest request) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        Fields fields = fields(request.versionCode(), request.versionName(), request.academicYearLabel(),
                request.description(), request.effectiveFrom(), request.effectiveTo());
        CurriculumVersion curriculum = lockedWithProgram(id);
        if (curriculums.existsByProgramIdAndVersionCodeAndIdNot(curriculum.getProgram().getId(), fields.code(), id))
            throw CurriculumFailure.duplicate();
        apply(curriculum, fields);
        touch(curriculum, actor);
        try {
            curriculums.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return response(curriculum);
    }

    @Override @Transactional(timeout = 15)
    public CurriculumResponse activate(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        CurriculumVersion curriculum = lockedWithProgram(id);
        Program program = curriculum.getProgram();
        if (program.isDeleted() || !program.isActive()) throw CurriculumFailure.invalidProgram();
        if (curriculum.getStatus() == CurriculumStatus.ACTIVE) throw CurriculumFailure.alreadyActive();
        // Archive the previous active version first, inside the same transaction, so the partial unique index holds.
        curriculums.findFirstByProgramIdAndStatusAndDeletedFalseAndIdNot(program.getId(), CurriculumStatus.ACTIVE, id)
                .ifPresent(previous -> {
                    previous.setStatus(CurriculumStatus.ARCHIVED);
                    touch(previous, actor);
                    curriculums.flush();
                });
        curriculum.setStatus(CurriculumStatus.ACTIVE);
        touch(curriculum, actor);
        try {
            curriculums.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return response(curriculum);
    }

    @Override @Transactional(timeout = 15)
    public CurriculumResponse archive(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        CurriculumVersion curriculum = lockedWithProgram(id);
        if (curriculum.getStatus() == CurriculumStatus.ARCHIVED) throw CurriculumFailure.invalidTransition();
        curriculum.setStatus(CurriculumStatus.ARCHIVED);
        touch(curriculum, actor);
        curriculums.flush();
        return response(curriculum);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        lockTimeout();
        CurriculumVersion curriculum = lockedWithProgram(id);
        if (documents.existsByCurriculumVersionIdAndDeletedFalse(id)) throw CurriculumFailure.hasDocuments();
        curriculum.setDeleted(true);
        touch(curriculum, actor);
        curriculums.flush();
    }

    /** Locks the owning program, then the version (the same order as create and activate). */
    private CurriculumVersion lockedWithProgram(long id) {
        Long programId = curriculums.findLiveProgramId(id).orElseThrow(CurriculumFailure::notFound);
        programs.findByIdForUpdate(programId).orElseThrow(CurriculumFailure::notFound);
        CurriculumVersion curriculum = curriculums.findByIdForUpdate(id).orElseThrow(CurriculumFailure::notFound);
        if (curriculum.isDeleted() || !curriculum.getProgram().getId().equals(programId)) throw CurriculumFailure.notFound();
        return curriculum;
    }

    private CurriculumResponse response(CurriculumVersion curriculum) {
        return mapper.response(curriculum, documentCounts(List.of(curriculum.getId())).getOrDefault(curriculum.getId(), 0L));
    }

    private Map<Long, Long> documentCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var counts = new HashMap<Long, Long>();
        for (var row : documents.countActiveForCurriculums(ids)) counts.put(row.getCurriculumVersionId(), row.getTotal());
        return counts;
    }

    private record Fields(String code, String name, String academicYear, String description,
            LocalDate effectiveFrom, LocalDate effectiveTo) {}

    private Fields fields(String code, String name, String academicYear, String description,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        code = code.trim();
        name = name.trim();
        String year = AcademicYearLabel.normalize(academicYear);
        description = description == null || description.isBlank() ? null : description.trim();
        if (!VERSION_CODE.matcher(code).matches() || name.isEmpty() || year == null
                || effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
            throw CurriculumFailure.invalid();
        return new Fields(code, name, year, description, effectiveFrom, effectiveTo);
    }

    private void apply(CurriculumVersion curriculum, Fields fields) {
        curriculum.setVersionCode(fields.code());
        curriculum.setVersionName(fields.name());
        curriculum.setAcademicYearLabel(fields.academicYear());
        curriculum.setDescription(fields.description());
        curriculum.setEffectiveFrom(fields.effectiveFrom());
        curriculum.setEffectiveTo(fields.effectiveTo());
    }

    private void touch(CurriculumVersion curriculum, AuthenticatedUser actor) {
        curriculum.setUpdatedBy(actor.userId());
        curriculum.setUpdatedAt(now());
    }

    private Specification<CurriculumVersion> specification(CurriculumListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.programId() != null) predicates.add(cb.equal(root.get("program").get("id"), query.programId()));
            if (query.academicYear() != null) predicates.add(cb.equal(root.get("academicYearLabel"), query.academicYear()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("versionCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("versionName")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                if ("uk_curriculum_versions_program_code".equals(violation.getConstraintName()))
                    return CurriculumFailure.duplicate();
                if ("uk_curriculum_versions_one_active".equals(violation.getConstraintName()))
                    return CurriculumFailure.invalidTransition();
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
        if (id <= 0) throw CurriculumFailure.invalid();
    }
}
