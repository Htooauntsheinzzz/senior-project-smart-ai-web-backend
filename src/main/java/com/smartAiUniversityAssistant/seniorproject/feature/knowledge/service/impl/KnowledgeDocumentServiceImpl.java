package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.common.AcademicYearLabel;
import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.repository.CurriculumVersionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.department.repository.DepartmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.mapper.KnowledgeDocumentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.mapper.KnowledgeDocumentMapper.CurrentFile;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentProcessingStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.FileStorageService.StoredFile;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeFileValidator.ValidatedFile;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import com.smartAiUniversityAssistant.seniorproject.feature.program.repository.ProgramRepository;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI Knowledge Base documents. Metadata changes lock the document row; a scope reference is locked and re-checked
 * only when it changes, so a document never moves onto a deleted or inactive program, department or curriculum.
 * Uploads store the bytes first and then commit metadata in a separate transaction; when that transaction fails the
 * stored file is deleted again (startup reconciliation reports files left behind by a crash in between).
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);
    static final String AWAITING_PROCESSING = "Uploaded. Awaiting AI processing: the document is not available to the AI assistant yet.";
    private final AcademicDocumentRepository documents;
    private final AcademicDocumentFileRepository files;
    private final DocumentCategoryRepository categories;
    private final CurriculumVersionRepository curriculums;
    private final ProgramRepository programs;
    private final DepartmentRepository departments;
    private final KnowledgeDocumentMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;
    private final AcademicDocumentProcessingRepository processingRecords;
    private final KnowledgeFileValidator validator;
    private final FileStorageService storage;
    private final KnowledgeStorageProperties properties;
    private final TransactionTemplate transaction;

    public KnowledgeDocumentServiceImpl(AcademicDocumentRepository documents, AcademicDocumentFileRepository files,
            DocumentCategoryRepository categories, CurriculumVersionRepository curriculums, ProgramRepository programs,
            DepartmentRepository departments, KnowledgeDocumentMapper mapper, Clock clock, EntityManager entityManager,
            AcademicDocumentProcessingRepository processingRecords, KnowledgeFileValidator validator,
            FileStorageService storage, KnowledgeStorageProperties properties, PlatformTransactionManager transactions) {
        this.documents = documents;
        this.files = files;
        this.categories = categories;
        this.curriculums = curriculums;
        this.programs = programs;
        this.departments = departments;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
        this.processingRecords = processingRecords;
        this.validator = validator;
        this.storage = storage;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public KnowledgeDocumentPageResponse search(AuthenticatedUser actor, KnowledgeDocumentListQuery query) {
        requireFull(actor);
        var page = documents.findAll(specification(query), query.pageable());
        return mapper.page(page, query, currentFiles(page.getContent().stream().map(AcademicDocument::getId).toList()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public KnowledgeDocumentResponse getById(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return response(documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound));
    }

    @Override @Transactional(timeout = 15)
    public KnowledgeDocumentResponse updateMetadata(AuthenticatedUser actor, long id, KnowledgeDocumentUpdateRequest request) {
        requireFull(actor);
        AcademicDocument document = locked(id);
        if (!document.isActive()) throw KnowledgeFailure.notEditable();
        String title = request.title().trim();
        String year = AcademicYearLabel.normalize(request.academicYearLabel());
        if (title.isEmpty() || year == null) throw KnowledgeFailure.invalid();
        if (!request.categoryId().equals(document.getCategory().getId()))
            document.setCategory(activeCategory(request.categoryId(), true));
        if (!sameScope(document, request.curriculumVersionId(), request.programId(), request.departmentId()))
            applyScope(document, resolveScope(request.curriculumVersionId(), request.programId(), request.departmentId()));
        document.setTitle(title);
        document.setDescription(blankToNull(request.description()));
        document.setAcademicYearLabel(year);
        touch(document, actor);
        documents.flush();
        return response(document);
    }

    @Override @Transactional(timeout = 15)
    public KnowledgeDocumentResponse activate(AuthenticatedUser actor, long id) {
        requireFull(actor);
        AcademicDocument document = locked(id);
        if (!document.isActive()) {
            // Re-check the stored scope: a document never becomes active on a deleted or inactive reference.
            resolveScope(document.getCurriculumVersion() == null ? null : document.getCurriculumVersion().getId(),
                    document.getProgram() == null ? null : document.getProgram().getId(),
                    document.getDepartment() == null ? null : document.getDepartment().getId());
            document.setActive(true);
            touch(document, actor);
            documents.flush();
        }
        return response(document);
    }

    @Override @Transactional(timeout = 15)
    public KnowledgeDocumentResponse archive(AuthenticatedUser actor, long id) {
        requireFull(actor);
        AcademicDocument document = locked(id);
        if (document.isActive()) {
            document.setActive(false);
            touch(document, actor);
            documents.flush();
        }
        return response(document);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        AcademicDocument document = locked(id);
        // Soft delete only: file revisions and stored bytes stay for a separate retention/purge policy.
        document.setDeleted(true);
        touch(document, actor);
        documents.flush();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public KnowledgeDocumentStatisticsResponse statistics(AuthenticatedUser actor) {
        requireFull(actor);
        var row = documents.statistics();
        return new KnowledgeDocumentStatisticsResponse(row.getTotal(), row.getReady(), row.getProcessing(), row.getFailed());
    }

    @Override
    public KnowledgeDocumentUploadResponse upload(AuthenticatedUser actor, KnowledgeDocumentUploadRequest request, MultipartFile file) {
        requireFull(actor);
        if (!properties.uploadEnabled()) throw KnowledgeFailure.uploadDisabled();
        String title = request.title() == null ? "" : request.title().trim();
        String year = AcademicYearLabel.normalize(request.academicYearLabel());
        String description = blankToNull(request.description());
        if (title.isEmpty() || title.length() > 255 || year == null || request.categoryId() == null
                || description != null && description.length() > 5000) throw KnowledgeFailure.uploadValidation();
        // Category and scope are checked before any byte is stored, and again under lock when metadata is saved.
        transaction.executeWithoutResult(status -> {
            activeCategory(request.categoryId(), false);
            resolveScope(request.curriculumVersionId(), request.programId(), request.departmentId());
        });
        ValidatedFile validated = validator.validate(file);
        StoredFile stored = storage.store(file, validated.extension());
        return persistOrDiscard(stored, () -> {
            lockTimeout();
            var document = new AcademicDocument();
            document.setTitle(title);
            document.setDescription(description);
            document.setCategory(activeCategory(request.categoryId(), true));
            document.setAcademicYearLabel(year);
            applyScope(document, resolveScope(request.curriculumVersionId(), request.programId(), request.departmentId()));
            document.setActive(true);
            document.setDeleted(false);
            document.setCreatedBy(actor.userId());
            document.setCreatedAt(now());
            documents.saveAndFlush(document);
            return revision(actor, document, 1, validated, stored);
        });
    }

    @Override
    public KnowledgeDocumentUploadResponse replaceFile(AuthenticatedUser actor, long id, MultipartFile file) {
        requireFull(actor);
        if (!properties.uploadEnabled()) throw KnowledgeFailure.uploadDisabled();
        positiveId(id);
        transaction.executeWithoutResult(status -> {
            if (!documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound).isActive())
                throw KnowledgeFailure.notEditable();
        });
        ValidatedFile validated = validator.validate(file);
        StoredFile stored = storage.store(file, validated.extension());
        return persistOrDiscard(stored, () -> {
            // The document lock serializes replacements; the one-current index is the last line of defence.
            AcademicDocument document = locked(id);
            if (!document.isActive()) throw KnowledgeFailure.notEditable();
            files.findByDocumentIdAndCurrentTrue(id).ifPresent(previous -> {
                previous.setCurrent(false);
                files.flush();
            });
            touch(document, actor);
            return revision(actor, document, files.maxRevision(id) + 1, validated, stored);
        });
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<KnowledgeDocumentFileResponse> revisions(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound);
        return files.findRevisionsWithProcessing(id).stream().map(row -> {
            var file = (AcademicDocumentFile) row[0];
            var processing = (AcademicDocumentProcessing) row[1];
            return new KnowledgeDocumentFileResponse(file.getId(), file.getRevisionNumber(), file.getOriginalFilename(),
                    file.getFileExtension(), file.getMimeType(), file.getSizeBytes(), file.getSha256Checksum(), file.isCurrent(),
                    new KnowledgeDocumentResponse.Uploader(file.getUploadedBy(), displayName(row[2], row[3])),
                    instant(file.getUploadedAt()), processing == null ? null : processing.getStatus().name());
        }).toList();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public KnowledgeDocumentDownload download(AuthenticatedUser actor, long id, Long fileId) {
        requireFull(actor);
        positiveId(id);
        documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound);
        // A revision id is only honoured together with its own document id.
        AcademicDocumentFile file = (fileId == null ? files.findByDocumentIdAndCurrentTrue(id) : files.findByIdAndDocumentId(fileId, id))
                .orElseThrow(KnowledgeFailure::fileNotFound);
        return new KnowledgeDocumentDownload(file.getOriginalFilename(), file.getMimeType(), file.getSizeBytes(),
                storage.open(file.getStorageKey()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public KnowledgeDocumentProcessingResponse processing(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound);
        AcademicDocumentFile file = files.findByDocumentIdAndCurrentTrue(id).orElseThrow(KnowledgeFailure::fileNotFound);
        AcademicDocumentProcessing processing = processingRecords.findByFileId(file.getId()).orElseThrow(KnowledgeFailure::fileNotFound);
        String message = switch (processing.getStatus()) {
            case UPLOADED -> "Awaiting AI processing. Document ingestion is not available yet.";
            case READY -> "Indexed and ready for AI retrieval.";
            case FAILED -> "Processing failed.";
            default -> "Processing is in progress.";
        };
        return new KnowledgeDocumentProcessingResponse(id, file.getId(), file.getRevisionNumber(), processing.getStatus().name(),
                processing.getAttemptCount(), instant(processing.getStartedAt()), instant(processing.getCompletedAt()),
                processing.getLastErrorCode(), false, message);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public void reprocess(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        documents.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::documentNotFound);
        files.findByDocumentIdAndCurrentTrue(id).orElseThrow(KnowledgeFailure::fileNotFound);
        // No ingestion worker exists yet, so nothing can be queued; the status is left unchanged.
        throw KnowledgeFailure.processingNotAvailable();
    }

    /** Commits metadata for an already stored file, deleting the file again when the commit does not happen. */
    private KnowledgeDocumentUploadResponse persistOrDiscard(StoredFile stored,
            java.util.function.Supplier<KnowledgeDocumentUploadResponse> work) {
        try {
            return transaction.execute(status -> work.get());
        } catch (KnowledgeFailure | PasswordChangeRequiredException e) {
            discard(stored);
            throw e;
        } catch (DataIntegrityViolationException e) {
            discard(stored);
            throw KnowledgeFailure.revisionConflict();
        } catch (RuntimeException e) {
            discard(stored);
            log.error("KNOWLEDGE_UPLOAD metadata save failed: {}", e.getClass().getSimpleName());
            throw KnowledgeFailure.metadataSaveFailed();
        }
    }

    private void discard(StoredFile stored) {
        try {
            storage.delete(stored.storageKey());
        } catch (RuntimeException e) {
            log.error("KNOWLEDGE_UPLOAD could not discard stored file key={}; startup reconciliation will report it", stored.storageKey());
        }
    }

    private KnowledgeDocumentUploadResponse revision(AuthenticatedUser actor, AcademicDocument document, int number,
            ValidatedFile validated, StoredFile stored) {
        var file = new AcademicDocumentFile();
        file.setDocument(document);
        file.setRevisionNumber(number);
        file.setOriginalFilename(validated.filename());
        file.setStorageKey(stored.storageKey());
        file.setMimeType(validated.mimeType());
        file.setFileExtension(validated.extension());
        file.setSizeBytes(stored.sizeBytes());
        file.setSha256Checksum(stored.sha256Checksum());
        file.setCurrent(true);
        file.setUploadedBy(actor.userId());
        file.setUploadedAt(now());
        files.saveAndFlush(file);
        var processing = new AcademicDocumentProcessing();
        processing.setFile(file);
        processing.setStatus(DocumentProcessingStatus.UPLOADED);
        processing.setAttemptCount(0);
        processing.setUpdatedAt(now());
        processingRecords.saveAndFlush(processing);
        return new KnowledgeDocumentUploadResponse(document.getId(), file.getId(), document.getTitle(),
                document.getCategory().getId(), document.getAcademicYearLabel(), file.getOriginalFilename(),
                file.getFileExtension(), file.getMimeType(), file.getRevisionNumber(), file.getSizeBytes(),
                file.getSha256Checksum(), processing.getStatus().name(), document.isActive(), file.getUploadedBy(),
                instant(file.getUploadedAt()), AWAITING_PROCESSING);
    }

    /** The referenced scope of a document; at most one field is non-null. */
    record ResolvedScope(CurriculumVersion curriculum, Program program, Department department) {}

    ResolvedScope resolveScope(Long curriculumVersionId, Long programId, Long departmentId) {
        int references = (curriculumVersionId == null ? 0 : 1) + (programId == null ? 0 : 1) + (departmentId == null ? 0 : 1);
        if (references > 1) throw KnowledgeFailure.scopeInvalid();
        if (curriculumVersionId != null) {
            CurriculumVersion curriculum = curriculums.findByIdForUpdate(curriculumVersionId).orElseThrow(KnowledgeFailure::scopeInvalid);
            if (curriculum.isDeleted() || curriculum.getProgram().isDeleted()) throw KnowledgeFailure.scopeInvalid();
            return new ResolvedScope(curriculum, null, null);
        }
        if (programId != null) {
            Program program = programs.findByIdForUpdate(programId).orElseThrow(KnowledgeFailure::scopeInvalid);
            if (program.isDeleted() || !program.isActive()) throw KnowledgeFailure.scopeInvalid();
            return new ResolvedScope(null, program, null);
        }
        if (departmentId != null) {
            Department department = departments.findByIdForUpdate(departmentId).orElseThrow(KnowledgeFailure::scopeInvalid);
            if (department.isDeleted() || !department.isActive()) throw KnowledgeFailure.scopeInvalid();
            return new ResolvedScope(null, null, department);
        }
        return new ResolvedScope(null, null, null);
    }

    void applyScope(AcademicDocument document, ResolvedScope scope) {
        document.setCurriculumVersion(scope.curriculum());
        document.setProgram(scope.program());
        document.setDepartment(scope.department());
    }

    DocumentCategory activeCategory(Long id, boolean lock) {
        DocumentCategory category = (lock ? categories.findByIdForUpdate(id) : categories.findById(id))
                .orElseThrow(KnowledgeFailure::categoryInvalid);
        if (category.isDeleted() || !category.isActive()) throw KnowledgeFailure.categoryInvalid();
        return category;
    }

    AcademicDocument locked(long id) {
        positiveId(id);
        lockTimeout();
        AcademicDocument document = documents.findByIdForUpdate(id).orElseThrow(KnowledgeFailure::documentNotFound);
        if (document.isDeleted()) throw KnowledgeFailure.documentNotFound();
        return document;
    }

    KnowledgeDocumentResponse response(AcademicDocument document) {
        return mapper.response(document, currentFiles(List.of(document.getId())).get(document.getId()));
    }

    private Map<Long, CurrentFile> currentFiles(Collection<Long> documentIds) {
        if (documentIds.isEmpty()) return Map.of();
        var current = new HashMap<Long, CurrentFile>();
        for (Object[] row : files.findCurrentWithProcessing(documentIds)) {
            var file = (AcademicDocumentFile) row[0];
            current.put(file.getDocument().getId(),
                    new CurrentFile(file, (AcademicDocumentProcessing) row[1], displayName(row[2], row[3])));
        }
        return current;
    }

    static String displayName(Object firstName, Object lastName) {
        if (firstName == null && lastName == null) return null;
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    private boolean sameScope(AcademicDocument document, Long curriculumVersionId, Long programId, Long departmentId) {
        return Objects.equals(document.getCurriculumVersion() == null ? null : document.getCurriculumVersion().getId(), curriculumVersionId)
                && Objects.equals(document.getProgram() == null ? null : document.getProgram().getId(), programId)
                && Objects.equals(document.getDepartment() == null ? null : document.getDepartment().getId(), departmentId);
    }

    private Specification<AcademicDocument> specification(KnowledgeDocumentListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.categoryId() != null) predicates.add(cb.equal(root.get("category").get("id"), query.categoryId()));
            if (query.academicYear() != null) predicates.add(cb.equal(root.get("academicYearLabel"), query.academicYear()));
            if (query.active() != null) predicates.add(cb.equal(root.get("active"), query.active()));
            if (query.curriculumVersionId() != null)
                predicates.add(cb.equal(root.get("curriculumVersion").get("id"), query.curriculumVersionId()));
            if (query.programId() != null || query.departmentId() != null) {
                Join<?, ?> program = root.join("program", JoinType.LEFT);
                Join<?, ?> department = root.join("department", JoinType.LEFT);
                Join<?, ?> curriculumProgram = root.join("curriculumVersion", JoinType.LEFT).join("program", JoinType.LEFT);
                if (query.programId() != null)
                    predicates.add(cb.or(cb.equal(program.get("id"), query.programId()),
                            cb.equal(curriculumProgram.get("id"), query.programId())));
                if (query.departmentId() != null)
                    predicates.add(cb.or(cb.equal(department.get("id"), query.departmentId()),
                            cb.equal(program.get("department").get("id"), query.departmentId()),
                            cb.equal(curriculumProgram.get("department").get("id"), query.departmentId())));
            }
            if (query.status() != null) {
                Subquery<Long> current = criteria.subquery(Long.class);
                Root<AcademicDocumentProcessing> processing = current.from(AcademicDocumentProcessing.class);
                current.select(processing.get("file").get("document").get("id")).where(
                        cb.equal(processing.get("status"), query.status()),
                        cb.isTrue(processing.get("file").get("current")));
                predicates.add(root.get("id").in(current));
            }
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("description")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    void touch(AcademicDocument document, AuthenticatedUser actor) {
        document.setUpdatedBy(actor.userId());
        document.setUpdatedAt(now());
    }
    private void lockTimeout() { entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate(); }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw KnowledgeFailure.invalid();
    }
}
