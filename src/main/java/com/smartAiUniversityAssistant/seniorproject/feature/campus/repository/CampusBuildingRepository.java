package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusBuildingRepository extends JpaRepository<CampusBuilding, Long>, JpaSpecificationExecutor<CampusBuilding> {
    @EntityGraph(attributePaths = "site") Page<CampusBuilding> findAll(Specification<CampusBuilding> specification, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select b from CampusBuilding b where b.id = :id and b.deleted = false")
    Optional<CampusBuilding> findLiveForUpdate(@Param("id") long id);
    @EntityGraph(attributePaths = "site") Optional<CampusBuilding> findByIdAndDeletedFalse(long id);
    @EntityGraph(attributePaths = "site") List<CampusBuilding> findBySiteIdAndDeletedFalseOrderByBuildingNameAscIdAsc(long siteId);
    boolean existsBySiteIdAndBuildingCodeAndIdNot(long siteId, String buildingCode, long id);
    boolean existsBySiteIdAndDeletedFalse(long siteId);
    boolean existsByDeletedFalseAndStatusNotAndSiteDeletedFalseAndSiteActiveTrue(CampusBuildingStatus status);
    interface StatusCount { CampusBuildingStatus getStatus(); long getTotal(); }
    @Query("select b.status as status, count(b) as total from CampusBuilding b where b.deleted = false and (:siteId is null or b.site.id = :siteId) group by b.status")
    List<StatusCount> countByStatus(@Param("siteId") Long siteId);
}
