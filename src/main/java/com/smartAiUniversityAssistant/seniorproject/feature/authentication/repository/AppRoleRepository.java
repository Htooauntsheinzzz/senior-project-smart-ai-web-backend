package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppRole;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select r from AppRole r where r.id = :id")
    Optional<AppRole> findByIdForUpdate(@Param("id") Long id);
}
