package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.ChangePasswordRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeServiceImpl implements PasswordChangeService {
    private final AppUserRepository users;
    private final AppUserCredentialsRepository credentials;
    private final PasswordPolicy passwords;
    private final CredentialPolicy policy;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public PasswordChangeServiceImpl(AppUserRepository users, AppUserCredentialsRepository credentials,
            PasswordPolicy passwords, CredentialPolicy policy, PasswordEncoder encoder, Clock clock) {
        this.users=users; this.credentials=credentials; this.passwords=passwords; this.policy=policy;
        this.encoder=encoder; this.clock=clock;
    }
    @Override @Transactional(timeout=10)
    public Outcome change(AuthenticatedUser principal, ChangePasswordRequest request) {
        var user=users.findByIdForUpdate(principal.userId()).orElse(null);
        if (user == null) return Outcome.UNAUTHORIZED;
        var c=credentials.findByUserIdForUpdate(user.getId()).orElse(null);
        var now=clock.instant();
        if (!policy.eligible(user,c,now) || !principal.credentialRevision().equals(TokenSupport.digest(c.getPasswordHash())))
            return Outcome.UNAUTHORIZED;
        policy.expireLock(c,now);
        if (!passwords.matches(request.currentPassword(),c.getPasswordHash())) {
            policy.failed(c,now); return Outcome.INVALID_CURRENT_PASSWORD;
        }
        passwords.validateNew(request.newPassword(),c.getPasswordHash());
        c.setPasswordHash(encoder.encode(request.newPassword()));
        c.setForcePasswordChange(false);
        c.setPasswordChangedAt(LocalDateTime.ofInstant(now,ZoneOffset.UTC));
        policy.succeeded(c,now);
        return Outcome.CHANGED;
    }
}
