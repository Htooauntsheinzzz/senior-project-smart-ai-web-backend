package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.*;
import java.time.*;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class CredentialPolicy {
    private final AuthenticationProperties properties;
    private final MeterRegistry metrics;
    public CredentialPolicy(AuthenticationProperties properties, MeterRegistry metrics) {
        this.properties = properties; this.metrics = metrics;
    }
    public boolean eligible(AppUser user, AppUserCredentials credential, Instant now) {
        return user != null && !user.isDeleted() && "ACTIVE".equals(user.getAccountStatus()) && credential != null
                && (credential.getLockedUntil() == null || !credential.getLockedUntil().toInstant(ZoneOffset.UTC).isAfter(now));
    }
    public void expireLock(AppUserCredentials c, Instant now) {
        if (c.getLockedUntil() != null && !c.getLockedUntil().toInstant(ZoneOffset.UTC).isAfter(now)) {
            c.setLockedUntil(null); c.setFailedLoginAttempts(0);
            c.setUpdatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        }
    }
    public void failed(AppUserCredentials c, Instant now) {
        int next = Math.min(properties.lockout().maxFailedAttempts(), Math.max(0, c.getFailedLoginAttempts()) + 1);
        c.setFailedLoginAttempts(next);
        c.setUpdatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (next >= properties.lockout().maxFailedAttempts()) {
            c.setLockedUntil(LocalDateTime.ofInstant(now.plus(properties.lockout().duration()), ZoneOffset.UTC));
            metrics.counter("auth.lock.events").increment();
        }
    }
    public void succeeded(AppUserCredentials c, Instant now) {
        c.setFailedLoginAttempts(0); c.setLockedUntil(null);
        c.setUpdatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
