package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusNavNodeRepository extends JpaRepository<CampusNavNode, Long>, JpaSpecificationExecutor<CampusNavNode> {
    @EntityGraph(attributePaths = "site") Page<CampusNavNode> findAll(Specification<CampusNavNode> specification, Pageable pageable);
    @EntityGraph(attributePaths = "site") Optional<CampusNavNode> findByIdAndDeletedFalse(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select n from CampusNavNode n where n.id = :id and n.deleted = false")
    Optional<CampusNavNode> findLiveForUpdate(@Param("id") long id);
    boolean existsBySiteIdAndNodeCodeAndIdNot(long siteId, String nodeCode, long id);
    boolean existsBySiteIdAndDeletedFalse(long siteId);
    List<CampusNavNode> findBySiteIdAndDeletedFalseOrderByNodeNameAscIdAsc(long siteId);
    List<CampusNavNode> findBySiteIdAndDeletedFalseAndActiveTrueOrderByIdAsc(long siteId);
    long countBySiteIdAndDeletedFalse(long siteId);
    long countByDeletedFalse();
    long countByDeletedFalseAndActiveTrue();
    long countBySiteIdAndDeletedFalseAndActiveTrue(long siteId);
}
