package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusCategoryRepository extends JpaRepository<CampusCategory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from CampusCategory c where c.id = :id and c.deleted = false")
    Optional<CampusCategory> findLiveForUpdate(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from CampusCategory c where c.id = :id and c.deleted = false and c.active = true")
    Optional<CampusCategory> findUsableForShare(@Param("id") long id);
    Optional<CampusCategory> findByIdAndDeletedFalse(long id);
    List<CampusCategory> findByDeletedFalseOrderByCategoryNameAscIdAsc();
    boolean existsByCategoryCodeAndIdNot(String categoryCode, long id);
    @Query("select distinct c.mobileGroup from CampusCategory c where c.deleted = false and c.active = true")
    Set<CampusMobileGroup> activeGroups();
}
