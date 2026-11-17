package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import java.time.*;
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
        // Spec file 61: self-registered accounts store the email in lower case, so "Name@rsu.ac.th" must find
        // "name@rsu.ac.th". An exact match always wins; older mixed-case accounts keep their behaviour.
        if (user == null && email != null) {
            String lower = email.toLowerCase(java.util.Locale.ROOT);
            if (!lower.equals(email)) user = users.findByEmailForUpdate(lower).orElse(null);
        }
        if (user == null) { passwords.dummyMatch(password); return CredentialVerificationResult.rejected(); }
        var c = credentials.findByUserIdForUpdate(user.getId()).orElse(null);
        var now = clock.instant();
        if (awaitingApproval(user, c, now)) {
            // Spec file 62: the status is told only to somebody who knows the password; a wrong password counts as
            // a failed attempt, as for every account.
            policy.expireLock(c,now);
            if (!passwords.matches(password,c.getPasswordHash())) {
                policy.failed(c,now); return CredentialVerificationResult.rejected();
            }
            policy.succeeded(c,now);
            return CredentialVerificationResult.awaitingApproval();
        }
        if (!policy.eligible(user,c,now)) return CredentialVerificationResult.rejected();
        policy.expireLock(c,now);
        if (!passwords.matches(password,c.getPasswordHash())) {
            policy.failed(c,now); return CredentialVerificationResult.rejected();
        }
        policy.succeeded(c,now);
        return new CredentialVerificationResult(true,user.getId(),TokenSupport.digest(c.getPasswordHash()));
    }
    private static boolean awaitingApproval(AppUser user, AppUserCredentials c, Instant now) {
        return !user.isDeleted() && AppUser.PENDING_APPROVAL.equals(user.getAccountStatus()) && c != null
                && (c.getLockedUntil() == null || !c.getLockedUntil().toInstant(ZoneOffset.UTC).isAfter(now));
    }
}
