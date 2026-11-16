package com.smartAiUniversityAssistant.seniorproject.feature.campus.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CampusNavEdgePointRepository extends JpaRepository<CampusNavEdgePoint, Long> {
    List<CampusNavEdgePoint> findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(Collection<Long> edgeIds);
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CampusNavEdgePoint p where p.edgeId = :id")
    void removeForEdge(@Param("id") long id);
}
