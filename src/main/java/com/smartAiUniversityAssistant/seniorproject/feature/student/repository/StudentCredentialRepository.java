package com.smartAiUniversityAssistant.seniorproject.feature.student.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.StudentCredential;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StudentCredentialRepository extends JpaRepository<StudentCredential, Long> {
    Optional<StudentCredential> findByStudentId(Long studentId);
    boolean existsByStudentId(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from StudentCredential c where c.student.id = :studentId")
    Optional<StudentCredential> findByStudentIdForUpdate(@Param("studentId") Long studentId);
}
