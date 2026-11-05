package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentProcessing;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicDocumentProcessingRepository extends JpaRepository<AcademicDocumentProcessing, Long> {
    Optional<AcademicDocumentProcessing> findByFileId(Long fileId);
}
