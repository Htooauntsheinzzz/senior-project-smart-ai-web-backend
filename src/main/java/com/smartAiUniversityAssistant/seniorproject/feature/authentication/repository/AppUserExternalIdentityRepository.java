package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUserExternalIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserExternalIdentityRepository extends JpaRepository<AppUserExternalIdentity, Long> {
    Optional<AppUserExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
    Optional<AppUserExternalIdentity> findByUserIdAndProvider(Long userId, String provider);
}
