package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;
import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.RefreshSessionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import java.util.Optional;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
@Service
public class RefreshSessionServiceImpl implements RefreshSessionService {
    private final RefreshSessionRepository repository; private final AuthenticationProperties properties;
    private final Clock clock; private final MeterRegistry metrics;
    public RefreshSessionServiceImpl(RefreshSessionRepository repository,AuthenticationProperties properties,Clock clock,MeterRegistry metrics) {
        this.repository=repository; this.properties=properties; this.clock=clock; this.metrics=metrics;
    }
    public Prepared prepare(AccountSecuritySnapshot account) {
        var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var token=RefreshToken.generate(account.userId(),TokenSupport.random(16));
        var expiry=now.plus(account.forcePasswordChange()?properties.session().passwordChangeTtl():properties.session().refreshTtl());
        return new Prepared(new RefreshSession(account.userId(),token.sessionId(),token.digest(),account.credentialRevision(),
                account.forcePasswordChange()?"PASSWORD_CHANGE_ONLY":"FULL",now,now,expiry,0),token);
    }
    public boolean create(RefreshSession session) { return repository.create(session); }
    public Optional<RefreshSession> read(RefreshToken token) { return repository.read(token.userId(),token.sessionId()); }
    public void rotate(RefreshSession session,RefreshToken presented,RefreshToken replacement,String mode) {
        var result=repository.rotate(session,presented.digest(),replacement.digest(),mode,clock.instant());
        metrics.counter("auth.refresh.outcomes","outcome",result.name()).increment();
        if (result!=RefreshSessionRepository.Rotation.ROTATED) throw AuthenticationFailure.refresh();
    }
    public void delete(long uid,String sid) { repository.delete(uid,sid); }
}
