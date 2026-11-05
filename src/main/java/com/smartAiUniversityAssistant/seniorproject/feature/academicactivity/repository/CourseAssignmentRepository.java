package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity.CourseAssignment;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.AssignmentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourseAssignmentRepository extends JpaRepository<CourseAssignment, Long> {
    @Query(value = """
            select a from CourseAssignment a join fetch a.courseSection cs join fetch cs.course
            where a.deleted = false and (:sectionId is null or cs.id = :sectionId) and (:status is null or a.status = :status)
            """, countQuery = """
            select count(a) from CourseAssignment a
            where a.deleted = false and (:sectionId is null or a.courseSection.id = :sectionId)
              and (:status is null or a.status = :status)
            """)
    Page<CourseAssignment> search(@Param("sectionId") Long sectionId, @Param("status") AssignmentStatus status, Pageable pageable);

    @Query("select a from CourseAssignment a join fetch a.courseSection cs join fetch cs.course where a.id = :id and a.deleted = false")
    Optional<CourseAssignment> findLive(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select a from CourseAssignment a where a.id = :id and a.deleted = false")
    Optional<CourseAssignment> findLiveForUpdate(@Param("id") Long id);

    @Query("""
            select a from CourseAssignment a join fetch a.courseSection cs join fetch cs.course
            where cs.id in :sectionIds and a.deleted = false
              and a.status = com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.AssignmentStatus.PUBLISHED
              and a.dueAt >= :from and a.dueAt <= :to
            """)
    List<CourseAssignment> findPublishedBetween(@Param("sectionIds") Collection<Long> sectionIds,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
