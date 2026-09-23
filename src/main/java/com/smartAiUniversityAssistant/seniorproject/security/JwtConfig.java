package com.smartAiUniversityAssistant.seniorproject.security;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods=false)
public class JwtConfig {
    @Bean public JwtEncoder jwtEncoder(KeyPair pair,AuthenticationProperties properties) {
        var key=new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate())
                .keyID(properties.jwt().keyId()).algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    @Bean public JwtDecoder jwtDecoder(KeyPair pair,AuthenticationProperties properties,Clock clock) {
        var decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)pair.getPublic()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var standardClaims=MappedJwtClaimSetConverter.withDefaults(java.util.Map.of());
        decoder.setClaimSetConverter(claims -> {
            // Check types before Spring's standard converter can coerce numeric subjects into strings.
            if (!java.util.stream.Stream.of("iss","sub","jti","sid","token_use","session_mode")
                    .allMatch(name -> claims.get(name) instanceof String)
                    || !java.util.stream.Stream.of("iat","nbf","exp").allMatch(name -> claims.get(name) instanceof java.util.Date)
                    || !(claims.get("aud") instanceof List<?> audience) || !audience.stream().allMatch(a -> a instanceof String))
                throw new BadJwtException("Invalid access token claim types");
            return standardClaims.convert(claims);
        });
        decoder.setJwtValidator(jwt -> validate(jwt,properties,clock));
        return decoder;
    }
    private OAuth2TokenValidatorResult validate(Jwt jwt,AuthenticationProperties p,Clock clock) {
        try {
            var raw=com.nimbusds.jwt.SignedJWT.parse(jwt.getTokenValue()).getPayload().toJSONObject();
            var now=clock.instant(); var skew=p.jwt().clockSkew();
            var iat=jwt.getIssuedAt(); var exp=jwt.getExpiresAt(); var nbf=jwt.getNotBefore();
            Object roles=jwt.getClaims().get("roles");
            boolean valid=java.util.stream.Stream.of("iss","sub","jti","sid","token_use","session_mode")
                        .allMatch(name -> raw.get(name) instanceof String)
                    && java.util.stream.Stream.of("iat","nbf","exp").allMatch(name -> raw.get(name) instanceof Number)
                    && (raw.get("aud") instanceof String || raw.get("aud") instanceof List<?> rawAudience
                        && rawAudience.stream().allMatch(a -> a instanceof String))
                    && "RS256".equals(jwt.getHeaders().get("alg")) && "JWT".equals(jwt.getHeaders().get("typ"))
                    && java.util.stream.Stream.of("iss","sub","jti","sid","token_use","session_mode")
                        .allMatch(name -> jwt.getClaims().get(name) instanceof String)
                    && p.jwt().keyId().equals(jwt.getHeaders().get("kid"))
                    && p.jwt().issuer().equals(jwt.getClaimAsString("iss")) && jwt.getAudience().contains(p.jwt().audience())
                    && TokenSupport.userId(jwt.getSubject()) && TokenSupport.canonical(jwt.getClaimAsString("sid"),16)
                    && TokenSupport.canonical(jwt.getId(),16) && "access".equals(jwt.getClaimAsString("token_use"))
                    && jwt.getClaims().get("force_password_change") instanceof Boolean
                    && List.of("FULL","PASSWORD_CHANGE_ONLY").contains(jwt.getClaimAsString("session_mode"))
                    && roles instanceof List<?> list && list.size()<=100 && list.stream().distinct().count()==list.size()
                    && list.stream().allMatch(r -> r instanceof String s && !s.isBlank() && !s.startsWith("ROLE_") && s.length()<=50)
                    && iat!=null && exp!=null && nbf!=null && exp.isAfter(iat) && nbf.equals(iat)
                    && Duration.between(iat,exp).compareTo(p.jwt().accessTokenTtl())<=0
                    && !iat.isAfter(now.plus(skew)) && !nbf.isAfter(now.plus(skew)) && exp.isAfter(now.minus(skew));
            if (valid) return OAuth2TokenValidatorResult.success();
        } catch (Exception ignored) { /* Malformed claims are authentication failures. */ }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid access token",null));
    }
}
