package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.GoogleIdTokenVerifier;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.GoogleIdentity;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Spec file 61: verifies a Google ID token with Google's published signing keys. The decoder is not a Spring bean:
 * the application's only {@link JwtDecoder} bean is the one for its own access tokens.
 */
@Component
public class GoogleIdTokenVerifierImpl implements GoogleIdTokenVerifier {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleIdTokenVerifierImpl.class);
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final AdminRegistrationProperties properties;
    private volatile JwtDecoder decoder;

    public GoogleIdTokenVerifierImpl(AdminRegistrationProperties properties) { this.properties = properties; }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) throw invalid();
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (BadJwtException e) {
            // Malformed, wrong signature, wrong issuer or audience, or expired.
            throw invalid();
        } catch (JwtException e) {
            LOG.warn("GOOGLE_SIGNING_KEYS_UNAVAILABLE");
            throw new AuthenticationFailure(503, "GOOGLE_SIGN_IN_UNAVAILABLE", "Google sign-in is temporarily unavailable.");
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || subject.length() > 255 || email == null || email.isBlank()
                || email.length() > 255) throw invalid();
        Object verified = jwt.getClaims().get("email_verified");
        return new GoogleIdentity(subject, email, Boolean.TRUE.equals(verified) || "true".equals(verified),
                jwt.getClaimAsString("hd"), jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) decoder = current = build();
            }
        }
        return current;
    }

    private JwtDecoder build() {
        var http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(5));
        http.setReadTimeout(Duration.ofSeconds(5));
        NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(properties.google().jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(http)).build();
        String clientId = properties.google().clientId();
        built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), jwt -> {
            // Issued by Google, for this application's own client ID (never for another site's), with an expiry.
            boolean valid = jwt.getClaims().get(JwtClaimNames.ISS) != null
                    && ISSUERS.contains(jwt.getClaims().get(JwtClaimNames.ISS).toString())
                    && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                    && jwt.getExpiresAt() != null;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not a Google ID token of this application", null));
        }));
        return built;
    }

    private static AuthenticationFailure invalid() {
        return new AuthenticationFailure(401, "INVALID_GOOGLE_TOKEN", "The Google sign-in could not be verified.");
    }
}
