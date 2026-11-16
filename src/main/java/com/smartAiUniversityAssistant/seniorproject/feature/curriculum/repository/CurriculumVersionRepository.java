package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.enums.CurriculumStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CurriculumVersionRepository extends JpaRepository<CurriculumVersion, Long>,
        JpaSpecificationExecutor<CurriculumVersion> {
    @EntityGraph(attributePaths = {"program", "program.department"})
    Optional<CurriculumVersion> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"program", "program.department"})
    Page<CurriculumVersion> findAll(Specification<CurriculumVersion> specification, Pageable pageable);
    boolean existsByProgramIdAndVersionCodeAndIdNot(Long programId, String versionCode, Long id);
    boolean existsByProgramIdAndStatusAndDeletedFalse(Long programId, CurriculumStatus status);
    Optional<CurriculumVersion> findFirstByProgramIdAndStatusAndDeletedFalseAndIdNot(Long programId,
            CurriculumStatus status, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from CurriculumVersion c where c.id = :id")
    Optional<CurriculumVersion> findByIdForUpdate(@Param("id") Long id);

    @Query("select c.program.id from CurriculumVersion c where c.id = :id and c.deleted = false")
    Optional<Long> findLiveProgramId(@Param("id") Long id);
}
