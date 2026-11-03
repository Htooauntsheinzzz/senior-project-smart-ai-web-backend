package com.smartAiUniversityAssistant.seniorproject.feature.course.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    @EntityGraph(attributePaths = {"faculty", "department", "program", "semester"})
    Optional<Course> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"faculty", "department", "program", "semester"})
    Page<Course> findAll(Specification<Course> specification, Pageable pageable);
    boolean existsByCourseCodeAndIdNot(String courseCode, Long id);
    boolean existsByCourseCodeAndDeletedFalse(String courseCode);
    long countByDeletedFalse();
    long countByActiveTrueAndDeletedFalse();
    long countByActiveFalseAndDeletedFalse();

    // Course identifier resolution for the academic query service: each stage returns every non-deleted match.
    @EntityGraph(attributePaths = {"faculty", "department", "program"})
    List<Course> findByCourseCodeAndDeletedFalse(String courseCode);
    @EntityGraph(attributePaths = {"faculty", "department", "program"})
    List<Course> findByCourseNameAndDeletedFalse(String courseName);
    @EntityGraph(attributePaths = {"faculty", "department", "program"})
    List<Course> findByCourseCodeIgnoreCaseAndDeletedFalse(String courseCode);
    @EntityGraph(attributePaths = {"faculty", "department", "program"})
    List<Course> findByCourseNameIgnoreCaseAndDeletedFalse(String courseName);
    // pattern is lower-case with LIKE wildcards escaped by the caller.
    @EntityGraph(attributePaths = {"faculty", "department", "program"})
    @Query("""
            select c from Course c
            where c.deleted = false and (lower(c.courseCode) like :pattern escape '\\'
                or lower(c.courseName) like :pattern escape '\\')
            order by c.courseCode asc, c.id asc
            """)
    List<Course> findPartialMatches(@Param("pattern") String pattern, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
