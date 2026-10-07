package com.smartAiUniversityAssistant.seniorproject.feature.semester.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.entity.Semester;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, Long>, JpaSpecificationExecutor<Semester> {
    Optional<Semester> findByIdAndDeletedFalse(Long id);
    boolean existsByAcademicYearAndSemesterNameThAndSemesterNameEnAndIdNot(Integer academicYear, String nameTh, String nameEn, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Semester s where s.id = :id")
    Optional<Semester> findByIdForUpdate(@Param("id") Long id);
}
