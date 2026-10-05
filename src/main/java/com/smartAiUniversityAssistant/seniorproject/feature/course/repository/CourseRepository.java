package com.smartAiUniversityAssistant.seniorproject.feature.course.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.course.entity.Course;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
