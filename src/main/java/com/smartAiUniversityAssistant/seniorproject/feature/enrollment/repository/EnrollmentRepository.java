package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {
    @EntityGraph(attributePaths = {"student", "courseSection", "courseSection.course", "courseSection.semester",
            "courseSection.lecture"})
    Optional<Enrollment> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"student", "courseSection", "courseSection.course", "courseSection.semester"})
    Page<Enrollment> findAll(Specification<Enrollment> specification, Pageable pageable);
    // Includes soft-deleted rows: UNIQUE(student_id, course_section_id) still reserves the pair.
    boolean existsByStudentIdAndCourseSectionIdAndIdNot(Long studentId, Long courseSectionId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(e) from Enrollment e
            where e.courseSection.id = :sectionId and e.status in :statuses and e.deleted = false
            """)
    long countOccupiedSeats(@Param("sectionId") Long sectionId, @Param("statuses") Collection<EnrollmentStatus> statuses);

    @Query("""
            select count(e) from Enrollment e join e.courseSection cs
            where e.student.id = :studentId and cs.course.id = :courseId and cs.semester.id = :semesterId
              and e.status in :statuses and e.deleted = false and e.id <> :excludeId
            """)
    long countConflictingEnrollments(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
            @Param("semesterId") Long semesterId, @Param("statuses") Collection<EnrollmentStatus> statuses,
            @Param("excludeId") Long excludeId);

    interface StatusCount {
        EnrollmentStatus getStatus();
        long getTotal();
    }
    @Query("select e.status as status, count(e) as total from Enrollment e where e.deleted = false group by e.status")
    List<StatusCount> countByStatus();

    interface SectionCount {
        Long getSectionId();
        long getTotal();
    }
    @Query("""
            select e.courseSection.id as sectionId, count(e) as total from Enrollment e
            where e.courseSection.id in :ids and e.status in :statuses and e.deleted = false
            group by e.courseSection.id
            """)
    List<SectionCount> countOccupiedSeats(@Param("ids") Collection<Long> sectionIds,
            @Param("statuses") Collection<EnrollmentStatus> statuses);

    @Query("""
            select count(e) from Enrollment e
            where e.courseSection.deleted = false and e.status in :statuses and e.deleted = false
            """)
    long countOccupiedSeatsInLiveSections(@Param("statuses") Collection<EnrollmentStatus> statuses);

    @Query("""
            select count(s) from CourseSection s where s.deleted = false and s.capacity <= (
                select count(e) from Enrollment e
                where e.courseSection = s and e.status in :statuses and e.deleted = false)
            """)
    long countFullSections(@Param("statuses") Collection<EnrollmentStatus> statuses);
}
