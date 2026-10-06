package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {
    public record IssuedToken(String value,Instant expiresAt) {
        @Override public String toString() { return "IssuedToken[REDACTED]"; }
    }
    public static final String STUDENT_PRINCIPAL="STUDENT";
    private final JwtEncoder encoder; private final AuthenticationProperties properties; private final Clock clock;
    public JwtTokenService(JwtEncoder encoder,AuthenticationProperties properties,Clock clock) {
        this.encoder=encoder; this.properties=properties; this.clock=clock;
    }
    public IssuedToken issue(AccountSecuritySnapshot account,String sid,String mode,Instant sessionExpiry) {
        var now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var exp=now.plus(properties.jwt().accessTokenTtl());
        if (sessionExpiry.isBefore(exp)) exp=sessionExpiry.truncatedTo(ChronoUnit.SECONDS);
        if (!exp.isAfter(now)) throw new org.springframework.security.authentication.BadCredentialsException("Session expired");
        var claims=JwtClaimsSet.builder().issuer(properties.jwt().issuer()).audience(java.util.List.of(properties.jwt().audience()))
                .subject(Long.toString(account.userId())).issuedAt(now).notBefore(now).expiresAt(exp).id(TokenSupport.random(16))
                .claim("sid",sid).claim("token_use","access").claim("roles",account.roles().stream().sorted().toList())
                .claim("force_password_change",account.forcePasswordChange() || mode.equals("PASSWORD_CHANGE_ONLY"))
                .claim("session_mode",mode).build();
        try {
            var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
                    .type("JWT").keyId(properties.jwt().keyId()).build(),claims));
            return new IssuedToken(token.getTokenValue(),exp);
        } catch (JwtException e) { throw new AuthenticationInfrastructureException(); }
    }

    /**
     * Student access token. It carries the claims the shared decoder requires plus studentId, studentCode and
     * principal_type=STUDENT, which routes it to the student session check instead of the admin one.
     */
    public IssuedToken issueStudent(long studentId,String studentCode,String sid,boolean forcePasswordChange,Instant sessionExpiry) {
        var now=clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var exp=now.plus(properties.jwt().accessTokenTtl());
        if (sessionExpiry.isBefore(exp)) exp=sessionExpiry.truncatedTo(ChronoUnit.SECONDS);
        if (!exp.isAfter(now)) throw new org.springframework.security.authentication.BadCredentialsException("Session expired");
        var claims=JwtClaimsSet.builder().issuer(properties.jwt().issuer()).audience(java.util.List.of(properties.jwt().audience()))
                .subject(Long.toString(studentId)).issuedAt(now).notBefore(now).expiresAt(exp).id(TokenSupport.random(16))
                .claim("sid",sid).claim("token_use","access").claim("principal_type",STUDENT_PRINCIPAL)
                .claim("studentId",studentId).claim("studentCode",studentCode)
                .claim("roles",java.util.List.of("STUDENT")).claim("force_password_change",forcePasswordChange)
                .claim("session_mode",forcePasswordChange?"PASSWORD_CHANGE_ONLY":"FULL").build();
        try {
            var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
                    .type("JWT").keyId(properties.jwt().keyId()).build(),claims));
            return new IssuedToken(token.getTokenValue(),exp);
        } catch (JwtException e) { throw new AuthenticationInfrastructureException(); }
    }
}
