package com.smartAiUniversityAssistant.seniorproject.feature.faculty.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.entity.Faculty;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FacultyRepository extends JpaRepository<Faculty, Long>, JpaSpecificationExecutor<Faculty> {
    Optional<Faculty> findByIdAndDeletedFalse(Long id);
    boolean existsByFacultyCode(String facultyCode);
    boolean existsByFacultyNameEn(String facultyNameEn);
    boolean existsByFacultyCodeAndIdNot(String facultyCode, Long id);
    boolean existsByFacultyNameEnAndIdNot(String facultyNameEn, Long id);
    long countByDeletedFalse();
    long countByActiveTrueAndDeletedFalse();
    long countByActiveFalseAndDeletedFalse();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select f from Faculty f where f.id = :id")
    Optional<Faculty> findByIdForUpdate(@Param("id") Long id);
}
