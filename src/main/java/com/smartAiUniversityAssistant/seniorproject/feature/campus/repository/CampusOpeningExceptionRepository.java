package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusOpeningExceptionRepository extends JpaRepository<CampusOpeningException, Long> {
    List<CampusOpeningException> findByBuildingIdOrderByExceptionDateAsc(long buildingId);
    List<CampusOpeningException> findByPlaceIdOrderByExceptionDateAsc(long placeId);
    List<CampusOpeningException> findByBuildingIdInAndExceptionDateIn(Collection<Long> buildingIds, Collection<java.time.LocalDate> dates);
    List<CampusOpeningException> findByPlaceIdInAndExceptionDateIn(Collection<Long> placeIds, Collection<java.time.LocalDate> dates);
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CampusOpeningException e where e.buildingId = :id")
    void removeForBuilding(@Param("id") long id);
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CampusOpeningException e where e.placeId = :id")
    void removeForPlace(@Param("id") long id);
}
