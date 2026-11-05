package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusSiteRepository extends JpaRepository<CampusSite, Long>, JpaSpecificationExecutor<CampusSite> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from CampusSite s where s.id = :id and s.deleted = false")
    Optional<CampusSite> findLiveForUpdate(@Param("id") long id);
    Optional<CampusSite> findByIdAndDeletedFalse(long id);
    Optional<CampusSite> findByIdAndDeletedFalseAndActiveTrue(long id);
    List<CampusSite> findByDeletedFalseAndActiveTrueOrderBySiteNameAscIdAsc();
    boolean existsBySiteCodeAndIdNot(String siteCode, long id);
}
