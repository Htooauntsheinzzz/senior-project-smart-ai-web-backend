package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.DocumentCategory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long>,
        JpaSpecificationExecutor<DocumentCategory> {
    Optional<DocumentCategory> findByIdAndDeletedFalse(Long id);
    boolean existsByCategoryCodeAndIdNot(String categoryCode, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from DocumentCategory c where c.id = :id")
    Optional<DocumentCategory> findByIdForUpdate(@Param("id") Long id);
}
