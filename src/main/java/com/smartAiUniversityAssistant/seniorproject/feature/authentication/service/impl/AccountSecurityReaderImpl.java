package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.Clock;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityReaderImpl implements AccountSecurityReader {
    private final AppUserRepository users;
    private final AppUserCredentialsRepository credentials;
    private final AppUserRoleRepository roles;
    private final CredentialPolicy policy;
    private final PasswordPolicy passwords;
    private final Clock clock;
    public AccountSecurityReaderImpl(AppUserRepository users, AppUserCredentialsRepository credentials,
            AppUserRoleRepository roles, CredentialPolicy policy, PasswordPolicy passwords, Clock clock) {
        this.users=users; this.credentials=credentials; this.roles=roles; this.policy=policy; this.passwords=passwords; this.clock=clock;
    }
    @Override @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<AccountSecuritySnapshot> read(long id) {
        return users.findById(id).map(user -> {
            var c=credentials.findByUserId(id).orElse(null);
            boolean eligible=policy.eligible(user,c,clock.instant()) && passwords.usableHash(c.getPasswordHash());
            return new AccountSecuritySnapshot(id,eligible,c==null ? "" : TokenSupport.digest(c.getPasswordHash()),
                    c!=null && c.isForcePasswordChange(), roles.findActiveRoles(id).stream().map(r -> r.code()).collect(Collectors.toUnmodifiableSet()));
        });
    }
}
