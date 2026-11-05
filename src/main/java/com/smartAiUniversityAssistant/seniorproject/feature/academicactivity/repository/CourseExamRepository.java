package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity.CourseExam;
import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.ExamStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourseExamRepository extends JpaRepository<CourseExam, Long> {
    @Query(value = """
            select x from CourseExam x join fetch x.courseSection cs join fetch cs.course
            where x.deleted = false and (:sectionId is null or cs.id = :sectionId) and (:status is null or x.status = :status)
            """, countQuery = """
            select count(x) from CourseExam x
            where x.deleted = false and (:sectionId is null or x.courseSection.id = :sectionId)
              and (:status is null or x.status = :status)
            """)
    Page<CourseExam> search(@Param("sectionId") Long sectionId, @Param("status") ExamStatus status, Pageable pageable);

    @Query("select x from CourseExam x join fetch x.courseSection cs join fetch cs.course where x.id = :id and x.deleted = false")
    Optional<CourseExam> findLive(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select x from CourseExam x where x.id = :id and x.deleted = false")
    Optional<CourseExam> findLiveForUpdate(@Param("id") Long id);

    @Query("""
            select x from CourseExam x join fetch x.courseSection cs join fetch cs.course
            where cs.id in :sectionIds and x.deleted = false
              and x.status = com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.ExamStatus.SCHEDULED
              and x.startsAt >= :from and x.startsAt <= :to
            """)
    List<CourseExam> findScheduledBetween(@Param("sectionIds") Collection<Long> sectionIds,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
