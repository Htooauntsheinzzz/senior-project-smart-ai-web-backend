package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.DocumentCategory;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.mapper.DocumentCategoryMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.DocumentCategoryService;
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
 * Document categories are reference records. Deactivating one keeps its existing documents readable and listed, but
 * no new upload or metadata change may choose it; deleting one is refused while live documents still use it.
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class DocumentCategoryServiceImpl implements DocumentCategoryService {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private final DocumentCategoryRepository categories;
    private final AcademicDocumentRepository documents;
    private final DocumentCategoryMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public DocumentCategoryServiceImpl(DocumentCategoryRepository categories, AcademicDocumentRepository documents,
            DocumentCategoryMapper mapper, Clock clock, EntityManager entityManager) {
        this.categories = categories;
        this.documents = documents;
        this.mapper = mapper;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public DocumentCategoryPageResponse list(AuthenticatedUser actor, DocumentCategoryListQuery query) {
        requireFull(actor);
        var page = categories.findAll(specification(query), query.pageable());
        return mapper.page(page, query, counts(page.getContent().stream().map(DocumentCategory::getId).toList()));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DocumentCategoryResponse detail(AuthenticatedUser actor, long id) {
        requireFull(actor);
        positiveId(id);
        return response(categories.findByIdAndDeletedFalse(id).orElseThrow(KnowledgeFailure::categoryNotFound));
    }

    @Override @Transactional(timeout = 15)
    public DocumentCategoryResponse create(AuthenticatedUser actor, DocumentCategoryRequest request) {
        requireFull(actor);
        var category = new DocumentCategory();
        apply(category, request, 0L);
        category.setActive(true);
        category.setDeleted(false);
        category.setCreatedBy(actor.userId());
        category.setCreatedAt(now());
        try {
            return mapper.response(categories.saveAndFlush(category), 0);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override @Transactional(timeout = 15)
    public DocumentCategoryResponse update(AuthenticatedUser actor, long id, DocumentCategoryRequest request) {
        requireFull(actor);
        DocumentCategory category = locked(id);
        apply(category, request, id);
        touch(category, actor);
        try {
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        return response(category);
    }

    @Override @Transactional(timeout = 15)
    public DocumentCategoryResponse activate(AuthenticatedUser actor, long id) {
        requireFull(actor);
        DocumentCategory category = locked(id);
        if (!category.isActive()) {
            category.setActive(true);
            touch(category, actor);
            categories.flush();
        }
        return response(category);
    }

    @Override @Transactional(timeout = 15)
    public DocumentCategoryResponse deactivate(AuthenticatedUser actor, long id) {
        requireFull(actor);
        DocumentCategory category = locked(id);
        if (category.isActive()) {
            category.setActive(false);
            touch(category, actor);
            categories.flush();
        }
        return response(category);
    }

    @Override @Transactional(timeout = 15)
    public void delete(AuthenticatedUser actor, long id) {
        requireFull(actor);
        DocumentCategory category = locked(id);
        if (documents.existsByCategoryIdAndDeletedFalse(id)) throw KnowledgeFailure.categoryInUse();
        category.setDeleted(true);
        category.setActive(false);
        touch(category, actor);
        categories.flush();
    }

    private DocumentCategory locked(long id) {
        positiveId(id);
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
        DocumentCategory category = categories.findByIdForUpdate(id).orElseThrow(KnowledgeFailure::categoryNotFound);
        if (category.isDeleted()) throw KnowledgeFailure.categoryNotFound();
        return category;
    }

    private void apply(DocumentCategory category, DocumentCategoryRequest request, long id) {
        String code = request.categoryCode().trim().toUpperCase(Locale.ROOT);
        String name = request.categoryName().trim();
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        if (!CODE.matcher(code).matches() || name.isEmpty()) throw KnowledgeFailure.invalid();
        // The UNIQUE constraint keeps codes reserved even after a category is soft-deleted.
        if (categories.existsByCategoryCodeAndIdNot(code, id)) throw KnowledgeFailure.categoryDuplicate();
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setDescription(description);
    }

    private DocumentCategoryResponse response(DocumentCategory category) {
        return mapper.response(category, counts(List.of(category.getId())).getOrDefault(category.getId(), 0L));
    }

    private Map<Long, Long> counts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var counts = new HashMap<Long, Long>();
        for (var row : documents.countLiveForCategories(ids)) counts.put(row.getCategoryId(), row.getTotal());
        return counts;
    }

    private Specification<DocumentCategory> specification(DocumentCategoryListQuery query) {
        return (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (query.active() != null) predicates.add(cb.equal(root.get("active"), query.active()));
            if (query.search() != null) {
                String literal = query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.or(cb.like(cb.lower(root.get("categoryCode")), "%" + literal + "%", '\\'),
                        cb.like(cb.lower(root.get("categoryName")), "%" + literal + "%", '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "document_categories_category_code_key".equals(violation.getConstraintName()))
                return KnowledgeFailure.categoryDuplicate();
        }
        return exception;
    }

    private void touch(DocumentCategory category, AuthenticatedUser actor) {
        category.setUpdatedBy(actor.userId());
        category.setUpdatedAt(now());
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private void requireFull(AuthenticatedUser actor) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
    }
    private void positiveId(long id) {
        if (id <= 0) throw KnowledgeFailure.invalid();
    }
}
