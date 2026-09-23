package com.smartAiUniversityAssistant.seniorproject.security;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
@Component
public class JwtAuthenticationConverter implements Converter<Jwt,AbstractAuthenticationToken> {
    private final SessionAccountGuard guard;
    public JwtAuthenticationConverter(SessionAccountGuard guard) { this.guard=guard; }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        var principal=guard.authenticate(jwt);
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,
                principal.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_"+r)).toList());
    }
}
