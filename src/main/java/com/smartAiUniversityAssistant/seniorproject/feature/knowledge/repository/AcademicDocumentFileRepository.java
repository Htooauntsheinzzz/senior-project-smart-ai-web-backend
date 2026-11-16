package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentFile;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AcademicDocumentFileRepository extends JpaRepository<AcademicDocumentFile, Long> {
    /** Row: [AcademicDocumentFile, AcademicDocumentProcessing or null, uploader first name, uploader last name]. */
    @Query("select f, p, u.firstName, u.lastName from AcademicDocumentFile f "
            + "left join AcademicDocumentProcessing p on p.file = f "
            + "left join com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUser u on u.id = f.uploadedBy "
            + "where f.current = true and f.document.id in :documentIds")
    List<Object[]> findCurrentWithProcessing(@Param("documentIds") Collection<Long> documentIds);

    @Query("select f, p, u.firstName, u.lastName from AcademicDocumentFile f "
            + "left join AcademicDocumentProcessing p on p.file = f "
            + "left join com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUser u on u.id = f.uploadedBy "
            + "where f.document.id = :documentId order by f.revisionNumber")
    List<Object[]> findRevisionsWithProcessing(@Param("documentId") Long documentId);

    Optional<AcademicDocumentFile> findByDocumentIdAndCurrentTrue(Long documentId);
    Optional<AcademicDocumentFile> findByIdAndDocumentId(Long id, Long documentId);
    boolean existsByStorageKey(String storageKey);

    @Query("select coalesce(max(f.revisionNumber), 0) from AcademicDocumentFile f where f.document.id = :documentId")
    int maxRevision(@Param("documentId") Long documentId);
}
