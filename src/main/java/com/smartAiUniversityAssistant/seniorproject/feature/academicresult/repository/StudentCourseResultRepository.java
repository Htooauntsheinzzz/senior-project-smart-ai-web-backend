package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity.StudentCourseResult;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StudentCourseResultRepository extends JpaRepository<StudentCourseResult, Long>,
        JpaSpecificationExecutor<StudentCourseResult> {

    @Override @EntityGraph(attributePaths = {"enrollment", "enrollment.student", "enrollment.courseSection",
            "enrollment.courseSection.course", "enrollment.courseSection.semester", "gradeDefinition",
            "gradeDefinition.gradingScheme"})
    Page<StudentCourseResult> findAll(Specification<StudentCourseResult> specification, Pageable pageable);

    boolean existsByEnrollmentId(Long enrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select r from StudentCourseResult r where r.id = :id")
    Optional<StudentCourseResult> findByIdForUpdate(@Param("id") Long id);

    // Official progress input: finalized results of live, ACTIVE enrollments only.
    // Results stay part of the record even if the Section or Course was soft-deleted afterwards.
    @Query("""
            select r from StudentCourseResult r
            join fetch r.gradeDefinition g join fetch g.gradingScheme
            join fetch r.enrollment e join fetch e.courseSection cs join fetch cs.course join fetch cs.semester
            where e.student.id = :studentId and e.deleted = false
              and e.status = com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus.ACTIVE
              and r.status = com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.CourseResultStatus.FINALIZED
            """)
    List<StudentCourseResult> findFinalizedForProgress(@Param("studentId") Long studentId);
}
