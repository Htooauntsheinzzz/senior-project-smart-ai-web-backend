package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUser;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByIdAndDeletedFalse(Long id);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByEmployeeIdAndIdNot(String employeeId, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.email = :email")
    Optional<AppUser> findByEmailForUpdate(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);
}
