package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocument;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AcademicDocumentRepository extends JpaRepository<AcademicDocument, Long>,
        JpaSpecificationExecutor<AcademicDocument> {

    @EntityGraph(attributePaths = {"category", "curriculumVersion", "curriculumVersion.program",
            "curriculumVersion.program.department", "program", "program.department", "department"})
    Optional<AcademicDocument> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"category", "curriculumVersion", "curriculumVersion.program",
            "curriculumVersion.program.department", "program", "program.department", "department"})
    Page<AcademicDocument> findAll(Specification<AcademicDocument> specification, Pageable pageable);
    boolean existsByCurriculumVersionIdAndDeletedFalse(Long curriculumVersionId);
    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select d from AcademicDocument d where d.id = :id")
    Optional<AcademicDocument> findByIdForUpdate(@Param("id") Long id);

    interface CurriculumCount {
        Long getCurriculumVersionId();
        long getTotal();
    }
    @Query("select d.curriculumVersion.id as curriculumVersionId, count(d) as total from AcademicDocument d "
            + "where d.deleted = false and d.active = true and d.curriculumVersion.id in :ids group by d.curriculumVersion.id")
    List<CurriculumCount> countActiveForCurriculums(@Param("ids") Collection<Long> ids);

    interface CategoryCount {
        Long getCategoryId();
        long getTotal();
    }
    @Query("select d.category.id as categoryId, count(d) as total from AcademicDocument d "
            + "where d.deleted = false and d.category.id in :ids group by d.category.id")
    List<CategoryCount> countLiveForCategories(@Param("ids") Collection<Long> ids);

    interface Statistics {
        long getTotal();
        long getReady();
        long getProcessing();
        long getFailed();
    }
    /** Documents (not revisions) that are active and not deleted, grouped by their current revision's status. */
    @Query(nativeQuery = true, value = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE p.status = 'READY') AS ready,
                   count(*) FILTER (WHERE p.status IN ('UPLOADED','QUEUED','PROCESSING','CHUNKING','EMBEDDING')) AS processing,
                   count(*) FILTER (WHERE p.status = 'FAILED') AS failed
            FROM academic_documents d
            LEFT JOIN academic_document_files f ON f.document_id = d.id AND f.is_current
            LEFT JOIN academic_document_processing p ON p.file_id = f.id
            WHERE d.is_active AND NOT d.is_deleted
            """)
    Statistics statistics();
}
