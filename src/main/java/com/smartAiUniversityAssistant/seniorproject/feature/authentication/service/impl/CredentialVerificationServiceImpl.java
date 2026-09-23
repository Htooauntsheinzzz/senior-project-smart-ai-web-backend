package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialVerificationServiceImpl implements CredentialVerificationService {
    private final AppUserRepository users;
    private final AppUserCredentialsRepository credentials;
    private final PasswordPolicy passwords;
    private final CredentialPolicy policy;
    private final Clock clock;
    public CredentialVerificationServiceImpl(AppUserRepository users, AppUserCredentialsRepository credentials,
            PasswordPolicy passwords, CredentialPolicy policy, Clock clock) {
        this.users=users; this.credentials=credentials; this.passwords=passwords; this.policy=policy; this.clock=clock;
    }
    @Override @Transactional(timeout=10)
    public CredentialVerificationResult verify(String email, String password) {
        var user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null) { passwords.dummyMatch(password); return CredentialVerificationResult.rejected(); }
        var c = credentials.findByUserIdForUpdate(user.getId()).orElse(null);
        var now = clock.instant();
        if (!policy.eligible(user,c,now)) return CredentialVerificationResult.rejected();
        policy.expireLock(c,now);
        if (!passwords.matches(password,c.getPasswordHash())) {
            policy.failed(c,now); return CredentialVerificationResult.rejected();
        }
        policy.succeeded(c,now);
        return new CredentialVerificationResult(true,user.getId(),TokenSupport.digest(c.getPasswordHash()));
    }
}
