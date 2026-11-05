package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusNavEdgeRepository extends JpaRepository<CampusNavEdge, Long>, JpaSpecificationExecutor<CampusNavEdge> {
    @EntityGraph(attributePaths = {"site", "startNode", "endNode"}) Page<CampusNavEdge> findAll(Specification<CampusNavEdge> specification, Pageable pageable);
    @EntityGraph(attributePaths = {"site", "startNode", "endNode"}) Optional<CampusNavEdge> findByIdAndDeletedFalse(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select e from CampusNavEdge e where e.id = :id and e.deleted = false")
    Optional<CampusNavEdge> findLiveForUpdate(@Param("id") long id);
    boolean existsBySiteIdAndEdgeCodeAndIdNot(long siteId, String edgeCode, long id);
    boolean existsBySiteIdAndDeletedFalse(long siteId);
    @Query("select count(e) > 0 from CampusNavEdge e where e.deleted = false and (e.startNode.id = :nodeId or e.endNode.id = :nodeId)")
    boolean existsLiveForNode(@Param("nodeId") long nodeId);
    @EntityGraph(attributePaths = {"site", "startNode", "startNode.site", "endNode", "endNode.site"})
    List<CampusNavEdge> findBySiteIdAndDeletedFalseOrderByIdAsc(long siteId);
    interface EdgeCounts { long getTotal(); long getActive(); long getInactive(); long getUnderConstruction(); long getWheelchairAccessible(); long getOneWay(); }
    @Query("""
            select count(e) as total,
                   coalesce(sum(case when e.status = 'ACTIVE' then 1 else 0 end), 0) as active,
                   coalesce(sum(case when e.status = 'INACTIVE' then 1 else 0 end), 0) as inactive,
                   coalesce(sum(case when e.status = 'UNDER_CONSTRUCTION' then 1 else 0 end), 0) as underConstruction,
                   coalesce(sum(case when e.wheelchairAccessible = true then 1 else 0 end), 0) as wheelchairAccessible,
                   coalesce(sum(case when e.bidirectional = false then 1 else 0 end), 0) as oneWay
            from CampusNavEdge e where e.deleted = false and (:siteId is null or e.site.id = :siteId)""")
    EdgeCounts counts(@Param("siteId") Long siteId);
}
