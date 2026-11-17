package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUserEmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppUserEmailVerificationRepository extends JpaRepository<AppUserEmailVerification, Long> {
    /** Only the owner's id, so the row can be read again after the user is locked. */
    @Query("select v.userId from AppUserEmailVerification v where v.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);
    Optional<AppUserEmailVerification> findByUserId(Long userId);
}
