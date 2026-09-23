package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUserCredentials;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppUserCredentialsRepository extends JpaRepository<AppUserCredentials, Long> {
    Optional<AppUserCredentials> findByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select c from AppUserCredentials c where c.user.id = :id")
    Optional<AppUserCredentials> findByUserIdForUpdate(@Param("id") Long id);
}
