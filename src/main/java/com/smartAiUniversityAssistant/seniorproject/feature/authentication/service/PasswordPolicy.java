package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    private final AuthenticationProperties properties;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PasswordPolicy.class);
    public PasswordPolicy(AuthenticationProperties properties, PasswordEncoder encoder) {
        this.properties = properties; this.encoder = encoder;
        dummyHash = encoder.encode(com.smartAiUniversityAssistant.seniorproject.security.TokenSupport.random(32));
    }
    public boolean validInput(String value) {
        return value != null && !value.isEmpty() && value.indexOf('\0') < 0
                && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.getBytes(StandardCharsets.UTF_8).length <= properties.password().maxUtf8Bytes();
    }
    public boolean validNewInput(String value) {
        return validInput(value) && value.codePointCount(0, value.length()) >= properties.password().minCodePoints();
    }
    public boolean usableHash(String hash) {
        return hash != null && hash.matches("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
    }
    public boolean matches(String raw, String hash) {
        if (!usableHash(hash)) { LOG.error("AUTH_CREDENTIAL_INTEGRITY"); return false; }
        try { return validInput(raw) && encoder.matches(raw, hash); }
        catch (IllegalArgumentException e) { LOG.error("AUTH_CREDENTIAL_INTEGRITY"); return false; }
    }
    public void dummyMatch(String raw) { encoder.matches(raw, dummyHash); }
    public void validateNew(String raw, String oldHash) {
        if (!validNewInput(raw) || oldHash != null && matches(raw, oldHash)) {
            throw new AuthenticationFailure(400, "PASSWORD_POLICY_VIOLATION",
                    "Use a different password with at least 15 Unicode characters and at most 72 UTF-8 bytes, without NUL.");
        }
    }
}
