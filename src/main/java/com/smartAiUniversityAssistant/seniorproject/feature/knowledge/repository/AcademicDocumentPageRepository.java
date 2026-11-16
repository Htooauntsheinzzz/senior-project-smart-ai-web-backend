package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentPage;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AcademicDocumentPageRepository extends JpaRepository<AcademicDocumentPage, Long> {
    /** One page of the list endpoint without the full texts. */
    interface PageSummaryView {
        int getPageNumber();
        String getUnitType();
        String getUnitLabel();
        int getRawCharCount();
        Integer getCleanedCharCount();
        String getPreview();
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM academic_document_pages WHERE id IN (SELECT id FROM academic_document_pages WHERE file_id = :fileId LIMIT :max)",
            nativeQuery = true)
    int deleteBatch(@Param("fileId") long fileId, @Param("max") int max);

    long countByFileId(Long fileId);

    @Query(value = """
            SELECT page_number AS pageNumber, unit_type AS unitType, unit_label AS unitLabel, raw_char_count AS rawCharCount,
                   cleaned_char_count AS cleanedCharCount, LEFT(COALESCE(cleaned_text, raw_text), 200) AS preview
            FROM academic_document_pages WHERE file_id = :fileId ORDER BY page_number""",
            countQuery = "SELECT COUNT(*) FROM academic_document_pages WHERE file_id = :fileId", nativeQuery = true)
    Page<PageSummaryView> findSummaries(@Param("fileId") long fileId, Pageable pageable);

    Optional<AcademicDocumentPage> findByFileIdAndPageNumber(Long fileId, int pageNumber);

    @Modifying
    @Query("update AcademicDocumentPage p set p.cleanedText = :text, p.cleanedCharCount = :count where p.id = :id")
    int updateCleaned(@Param("id") long id, @Param("text") String text, @Param("count") int count);

    /** The next pages of a file in order, after the given page number (0 for the first batch). */
    List<AcademicDocumentPage> findByFileIdAndPageNumberGreaterThanOrderByPageNumberAsc(Long fileId, int afterPageNumber, Pageable limit);
}
