package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusOpeningHourRepository extends JpaRepository<CampusOpeningHour, Long> {
    List<CampusOpeningHour> findByBuildingIdOrderByDayOfWeekAsc(long buildingId);
    List<CampusOpeningHour> findByPlaceIdOrderByDayOfWeekAsc(long placeId);
    List<CampusOpeningHour> findByBuildingIdInOrderByDayOfWeekAsc(Collection<Long> buildingIds);
    List<CampusOpeningHour> findByPlaceIdInOrderByDayOfWeekAsc(Collection<Long> placeIds);
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CampusOpeningHour h where h.buildingId = :id")
    void removeForBuilding(@Param("id") long id);
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CampusOpeningHour h where h.placeId = :id")
    void removeForPlace(@Param("id") long id);
}
