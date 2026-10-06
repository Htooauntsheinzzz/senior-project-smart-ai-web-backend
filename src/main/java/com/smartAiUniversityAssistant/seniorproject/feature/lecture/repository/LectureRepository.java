package com.smartAiUniversityAssistant.seniorproject.feature.lecture.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.lecture.entity.Lecture;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LectureRepository extends JpaRepository<Lecture, Long>, JpaSpecificationExecutor<Lecture> {
    @EntityGraph(attributePaths = {"faculty", "department"})
    Optional<Lecture> findByIdAndDeletedFalse(Long id);
    @Override @EntityGraph(attributePaths = {"faculty", "department"})
    Page<Lecture> findAll(Specification<Lecture> specification, Pageable pageable);
    boolean existsByDepartmentIdAndLectureNameThAndLectureNameEnAndIdNot(Long departmentId, String lectureNameTh,
            String lectureNameEn, Long id);
    long countByDeletedFalse();
    long countByStatusAndDeletedFalse(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select l from Lecture l where l.id = :id")
    Optional<Lecture> findByIdForUpdate(@Param("id") Long id);
}
