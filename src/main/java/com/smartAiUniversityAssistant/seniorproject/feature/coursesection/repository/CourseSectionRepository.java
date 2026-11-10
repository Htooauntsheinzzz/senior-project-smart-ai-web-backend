package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long>, JpaSpecificationExecutor<CourseSection> {
    @EntityGraph(attributePaths = {"course", "lecture", "semester", "academicTerm"})
    Optional<CourseSection> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"course", "lecture", "semester", "academicTerm"})
    Page<CourseSection> findAll(Specification<CourseSection> specification, Pageable pageable);
    boolean existsByCourseIdAndSectionNumberAndSemesterIdAndIdNot(Long courseId, String sectionNumber, Long semesterId,
            Long id);
    long countByDeletedFalse();
    long countByStatusAndDeletedFalse(String status);
    long countByCourseIdAndDeletedFalse(Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from CourseSection s where s.id = :id")
    Optional<CourseSection> findByIdForUpdate(@Param("id") Long id);

    // Sections a Student may request from the mobile application: open sections of live, active courses in one semester.
    @Query("""
            select s from CourseSection s join fetch s.course c join fetch s.lecture join fetch s.semester sem
            where sem.id = :semesterId and s.deleted = false and s.status = 'ACTIVE'
              and c.deleted = false and c.active = true
            order by c.courseCode asc, s.sectionNumber asc, s.id asc
            """)
    List<CourseSection> findOpenForSelfEnrollment(@Param("semesterId") Long semesterId);

    interface CourseCount {
        Long getCourseId();
        long getTotal();
    }
    @Query("select s.course.id as courseId, count(s) as total from CourseSection s where s.deleted = false and s.course.id in :ids group by s.course.id")
    List<CourseCount> countForCourses(@Param("ids") Collection<Long> ids);
}
