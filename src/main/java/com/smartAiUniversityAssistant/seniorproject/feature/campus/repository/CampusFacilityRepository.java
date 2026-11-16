package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusFacilityRepository extends JpaRepository<CampusFacility, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select f from CampusFacility f where f.id = :id")
    Optional<CampusFacility> findForUpdate(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select f from CampusFacility f where f.id in :ids and f.active = true order by f.id")
    List<CampusFacility> findUsableForShare(@Param("ids") Collection<Long> ids);
    List<CampusFacility> findAllByOrderByFacilityNameAscIdAsc();
    List<CampusFacility> findByActiveTrueOrderByFacilityNameAscIdAsc();
    boolean existsByFacilityCodeAndIdNot(String facilityCode, long id);
    @Query(value = """
            select exists(select 1 from campus_room_facilities x join campus_rooms r on r.id = x.room_id
                          where x.facility_id = :id and r.is_deleted = false)
                or exists(select 1 from campus_place_facilities x join campus_places p on p.id = x.place_id
                          where x.facility_id = :id and p.is_deleted = false)""", nativeQuery = true)
    boolean isInUse(@Param("id") long id);
    interface OwnedFacility { Long getOwnerId(); Long getId(); String getCode(); String getName(); }
    @Query("select r.id as ownerId, f.id as id, f.facilityCode as code, f.facilityName as name from CampusRoom r join r.facilities f where r.id in :ids order by f.facilityName, f.id")
    List<OwnedFacility> forRooms(@Param("ids") Collection<Long> ids);
    @Query("select p.id as ownerId, f.id as id, f.facilityCode as code, f.facilityName as name from CampusPlace p join p.facilities f where p.id in :ids order by f.facilityName, f.id")
    List<OwnedFacility> forPlaces(@Param("ids") Collection<Long> ids);
}
