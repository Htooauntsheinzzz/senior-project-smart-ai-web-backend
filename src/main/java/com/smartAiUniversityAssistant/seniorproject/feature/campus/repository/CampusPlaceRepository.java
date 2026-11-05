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

public interface CampusPlaceRepository extends JpaRepository<CampusPlace, Long>, JpaSpecificationExecutor<CampusPlace> {
    @EntityGraph(attributePaths = {"site", "category", "building"}) Page<CampusPlace> findAll(Specification<CampusPlace> specification, Pageable pageable);
    @EntityGraph(attributePaths = {"site", "category", "building"}) Optional<CampusPlace> findByIdAndDeletedFalse(long id);
    @EntityGraph(attributePaths = {"site", "category", "building"}) List<CampusPlace> findBySiteIdAndDeletedFalseOrderByPlaceNameAscIdAsc(long siteId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from CampusPlace p where p.id = :id and p.deleted = false")
    Optional<CampusPlace> findLiveForUpdate(@Param("id") long id);
    boolean existsBySiteIdAndPlaceCodeAndIdNot(long siteId, String placeCode, long id);
    boolean existsBySiteIdAndDeletedFalse(long siteId);
    boolean existsByBuildingIdAndDeletedFalse(long buildingId);
    boolean existsByCategoryIdAndDeletedFalse(long categoryId);
    @EntityGraph(attributePaths = "site")
    @Query("select p from CampusPlace p where p.deleted = false and p.status = :status and (:siteId is null or p.site.id = :siteId)")
    List<CampusPlace> liveWithStatus(@Param("status") CampusPlaceStatus status, @Param("siteId") Long siteId);
    interface PlaceCounts { long getTotal(); long getActive(); long getTemporarilyClosed(); long getInactive(); long getPopular(); long getVisibleOnMobile(); }
    @Query("""
            select count(p) as total,
                   coalesce(sum(case when p.status = 'ACTIVE' then 1 else 0 end), 0) as active,
                   coalesce(sum(case when p.status = 'TEMPORARILY_CLOSED' then 1 else 0 end), 0) as temporarilyClosed,
                   coalesce(sum(case when p.status = 'INACTIVE' then 1 else 0 end), 0) as inactive,
                   coalesce(sum(case when p.popular = true then 1 else 0 end), 0) as popular,
                   coalesce(sum(case when p.visibleOnMobile = true then 1 else 0 end), 0) as visibleOnMobile
            from CampusPlace p where p.deleted = false and (:siteId is null or p.site.id = :siteId)""")
    PlaceCounts counts(@Param("siteId") Long siteId);
}
