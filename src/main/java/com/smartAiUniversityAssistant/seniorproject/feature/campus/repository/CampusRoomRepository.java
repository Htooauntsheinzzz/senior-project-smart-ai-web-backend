package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusRoomRepository extends JpaRepository<CampusRoom, Long>, JpaSpecificationExecutor<CampusRoom> {
    @EntityGraph(attributePaths = "building") Page<CampusRoom> findAll(Specification<CampusRoom> specification, Pageable pageable);
    @EntityGraph(attributePaths = "building") Optional<CampusRoom> findByIdAndDeletedFalse(long id);
    @EntityGraph(attributePaths = "building")
    List<CampusRoom> findByBuildingIdAndDeletedFalseOrderByFloorNumberAscRoomNumberAscIdAsc(long buildingId);
    @EntityGraph(attributePaths = "building")
    List<CampusRoom> findByBuildingIdAndDeletedFalseAndStatusNotOrderByFloorNumberAscRoomNumberAscIdAsc(long buildingId, CampusRoomStatus status);
    boolean existsByBuildingIdAndRoomNumberAndIdNot(long buildingId, String roomNumber, long id);
    boolean existsByBuildingIdAndDeletedFalse(long buildingId);
    boolean existsByBuildingIdAndDeletedFalseAndStatusNot(long buildingId, CampusRoomStatus status);
    interface BuildingCount { Long getBuildingId(); long getTotal(); }
    @Query("select r.building.id as buildingId, count(r) as total from CampusRoom r where r.deleted = false and r.building.id in :ids group by r.building.id")
    List<BuildingCount> countForBuildings(@Param("ids") Collection<Long> ids);
    @Query("select max(r.floorNumber) from CampusRoom r where r.deleted = false and r.building.id = :id")
    Short highestFloor(@Param("id") long buildingId);
    interface StatusCount { CampusRoomStatus getStatus(); long getTotal(); }
    @Query("""
            select r.status as status, count(r) as total from CampusRoom r
            where r.deleted = false and r.building.deleted = false and (:siteId is null or r.building.site.id = :siteId)
              and (:buildingId is null or r.building.id = :buildingId) group by r.status""")
    List<StatusCount> countByStatus(@Param("siteId") Long siteId, @Param("buildingId") Long buildingId);
}
