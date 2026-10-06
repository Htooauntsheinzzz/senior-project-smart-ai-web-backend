package com.smartAiUniversityAssistant.seniorproject.security;
import com.smartAiUniversityAssistant.seniorproject.exception.AuthenticationInfrastructureException;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
@Component
public class JwtAuthenticationConverter implements Converter<Jwt,AbstractAuthenticationToken> {
    private final SessionAccountGuard guard; private final StudentAccessVerifier students;
    public JwtAuthenticationConverter(SessionAccountGuard guard,StudentAccessVerifier students) { this.guard=guard; this.students=students; }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.hasClaim("principal_type")) return student(jwt);
        var principal=guard.authenticate(jwt);
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,
                principal.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_"+r)).toList());
    }
    private AbstractAuthenticationToken student(Jwt jwt) {
        if (!JwtTokenService.STUDENT_PRINCIPAL.equals(jwt.getClaimAsString("principal_type"))
                || !(jwt.getClaims().get("studentId") instanceof Number id) || !jwt.getSubject().equals(Long.toString(id.longValue()))
                || !List.of("STUDENT").equals(jwt.getClaimAsStringList("roles"))) throw new BadCredentialsException("Invalid student token");
        AuthenticatedStudent principal;
        try {
            principal=students.verify(id.longValue(),jwt.getClaimAsString("sid"))
                    .orElseThrow(() -> new BadCredentialsException("Session unavailable"));
        } catch (BadCredentialsException e) { throw e; }
        catch (RuntimeException e) { throw new AuthenticationInfrastructureException(); }
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }
}
