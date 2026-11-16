package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusNavDestinationRepository extends JpaRepository<CampusNavDestination, Long> {
    @EntityGraph(attributePaths = {"node", "node.site", "building", "building.site", "place", "place.site"})
    @Query("""
            select d from CampusNavDestination d left join d.building b left join d.place p
            where (:siteId is null or d.node.site.id = :siteId) and (:nodeId is null or d.node.id = :nodeId)
              and (:buildingId is null or b.id = :buildingId) and (:placeId is null or p.id = :placeId)
            order by d.id""")
    List<CampusNavDestination> search(@Param("siteId") Long siteId, @Param("nodeId") Long nodeId,
            @Param("buildingId") Long buildingId, @Param("placeId") Long placeId);
    @EntityGraph(attributePaths = {"node", "node.site", "building", "building.site", "place", "place.site"})
    Optional<CampusNavDestination> findWithOwnerById(long id);
    boolean existsByNodeId(long nodeId);
    boolean existsByBuildingId(long buildingId);
    boolean existsByPlaceId(long placeId);
    /** Live, enabled entrance nodes of the given owners; the node's site is fetched so a cross-campus row can be detected. */
    @EntityGraph(attributePaths = {"node", "node.site", "building", "place"})
    @Query("""
            select d from CampusNavDestination d where d.node.deleted = false and d.node.active = true
              and (d.building.id in :buildingIds or d.place.id in :placeIds) order by d.id""")
    List<CampusNavDestination> usableFor(@Param("buildingIds") Collection<Long> buildingIds, @Param("placeIds") Collection<Long> placeIds);
}
