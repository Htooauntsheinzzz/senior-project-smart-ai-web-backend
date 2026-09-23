package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import java.time.Clock;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SessionAccountGuard {
    private final AccountSecurityReader accounts; private final AuthenticationSessionReader sessions; private final Clock clock;
    public SessionAccountGuard(AccountSecurityReader accounts,AuthenticationSessionReader sessions,Clock clock) {
        this.accounts=accounts; this.sessions=sessions; this.clock=clock;
    }
    public AuthenticatedUser authenticate(Jwt jwt) {
        try {
            long uid=Long.parseLong(jwt.getSubject()); String sid=jwt.getClaimAsString("sid");
            var session=sessions.read(uid,sid).orElseThrow(() -> new BadCredentialsException("Session unavailable"));
            var account=accounts.read(uid).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
            if (!account.eligible() || session.userId()!=uid || !sid.equals(session.sessionId())
                    || !clock.instant().isBefore(session.absoluteExpiresAt())
                    || !session.credentialRevision().equals(account.credentialRevision())) throw new BadCredentialsException("Session unavailable");
            boolean restricted=account.forcePasswordChange() || session.mode().equals("PASSWORD_CHANGE_ONLY")
                    || Boolean.TRUE.equals(jwt.getClaim("force_password_change")) || "PASSWORD_CHANGE_ONLY".equals(jwt.getClaimAsString("session_mode"));
            return new AuthenticatedUser(uid,sid,session.credentialRevision(),restricted,account.roles());
        } catch (DataAccessException | TransactionException e) { throw new AuthenticationInfrastructureException(); }
    }
}
