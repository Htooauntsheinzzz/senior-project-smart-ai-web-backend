package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.mapper.AuthenticationMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.time.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final CredentialVerificationService credentials; private final PasswordChangeService passwords;
    private final PasswordPolicy policy; private final AccountSecurityReader accounts; private final RefreshSessionService sessions;
    private final JwtTokenService tokens; private final AppUserRepository users; private final AppUserRoleRepository roles;
    private final AuthenticationMapper mapper; private final AuthenticationThrottle throttle;
    private final AuthenticationProperties properties; private final Clock clock; private final MeterRegistry metrics;
    public AuthenticationServiceImpl(CredentialVerificationService credentials,PasswordChangeService passwords,PasswordPolicy policy,
            AccountSecurityReader accounts,RefreshSessionService sessions,JwtTokenService tokens,AppUserRepository users,
            AppUserRoleRepository roles,AuthenticationMapper mapper,AuthenticationThrottle throttle,
            AuthenticationProperties properties,Clock clock,MeterRegistry metrics) {
        this.credentials=credentials; this.passwords=passwords; this.policy=policy; this.accounts=accounts; this.sessions=sessions;
        this.tokens=tokens; this.users=users; this.roles=roles; this.mapper=mapper; this.throttle=throttle;
        this.properties=properties; this.clock=clock; this.metrics=metrics;
    }
    @Override public TokenResponse login(LoginRequest request) {
        if (!policy.validInput(request.password())) throw AuthenticationFailure.validation();
        throttle.check("login-identifier",request.email(),properties.throttle().loginPerIdentifier());
        // No surrounding transaction: the separately proxied verifier commits failure counters before rejection.
        var result=credentials.verify(request.email(),request.password());
        metrics.counter("auth.login.outcomes","outcome",result.accepted()?"matched":"rejected").increment();
        if (!result.accepted()) throw AuthenticationFailure.credentials();
        var account=accounts.read(result.userId()).filter(a -> a.eligible() && a.credentialRevision().equals(result.revision()))
                .orElseThrow(AuthenticationFailure::credentials);
        for (int attempt=0;attempt<3;attempt++) {
            var prepared=sessions.prepare(account);
            var jwt=tokens.issue(account,prepared.session().sessionId(),prepared.session().mode(),prepared.session().absoluteExpiresAt());
            var profile=profile(account.userId(),account.forcePasswordChange());
            if (sessions.create(prepared.session())) return response(jwt,prepared.token(),prepared.session(),profile);
        }
        throw new AuthenticationInfrastructureException();
    }
    @Override public TokenResponse refresh(RefreshTokenRequest request) {
        var presented=RefreshToken.parse(request.refreshToken());
        var session=sessions.read(presented).orElseThrow(AuthenticationFailure::refresh);
        var account=accounts.read(session.userId()).orElse(null);
        if (account==null || !account.eligible() || !session.credentialRevision().equals(account.credentialRevision())) {
            bestEffortDelete(session.userId(),session.sessionId()); throw AuthenticationFailure.refresh();
        }
        if (Duration.between(clock.instant(),session.absoluteExpiresAt()).getSeconds()<1) throw AuthenticationFailure.refresh();
        String mode=account.forcePasswordChange() || session.mode().equals("PASSWORD_CHANGE_ONLY")?"PASSWORD_CHANGE_ONLY":"FULL";
        var next=RefreshToken.generate(session.userId(),session.sessionId());
        var jwt=tokens.issue(account,session.sessionId(),mode,session.absoluteExpiresAt());
        var profile=profile(account.userId(),mode.equals("PASSWORD_CHANGE_ONLY"));
        sessions.rotate(session,presented,next,mode);
        return response(jwt,next,session,profile);
    }
    @Override public void logout(AuthenticatedUser principal) { sessions.delete(principal.userId(),principal.sessionId()); }
    @Override @Transactional(readOnly=true)
    public CurrentUserResponse me(AuthenticatedUser principal) { return profile(principal.userId(),principal.forcePasswordChange()); }
    private CurrentUserResponse profile(long uid,boolean restricted) {
        return mapper.profile(users.findById(uid).orElseThrow(AuthenticationFailure::unauthorized),roles.findActiveRoles(uid),restricted);
    }
    private TokenResponse response(JwtTokenService.IssuedToken jwt,RefreshToken token,RefreshSession session,CurrentUserResponse profile) {
        var now=clock.instant();
        return new TokenResponse("Bearer",jwt.value(),Math.max(0,Duration.between(now,jwt.expiresAt()).getSeconds()),token.value(),
                Math.max(0,Duration.between(now,session.absoluteExpiresAt()).getSeconds()),profile.forcePasswordChange(),profile);
    }
    @Override public void changePassword(AuthenticatedUser principal,ChangePasswordRequest request) {
        if (!policy.validInput(request.currentPassword())) throw AuthenticationFailure.validation();
        var outcome=passwords.change(principal,request);
        if (outcome==PasswordChangeService.Outcome.UNAUTHORIZED) throw AuthenticationFailure.unauthorized();
        if (outcome==PasswordChangeService.Outcome.INVALID_CURRENT_PASSWORD)
            throw new AuthenticationFailure(400,"INVALID_CURRENT_PASSWORD","The current password is invalid.");
        bestEffortDelete(principal.userId(),principal.sessionId());
    }
    private void bestEffortDelete(long uid,String sid) {
        try { sessions.delete(uid,sid); }
        catch (AuthenticationInfrastructureException e) {
            metrics.counter("auth.session.cleanup.failures").increment();
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("AUTH_SESSION_CLEANUP_UNCONFIRMED");
        }
    }
}
