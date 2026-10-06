package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.InvalidRefreshTokenException;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;

/** Opaque student refresh token: {@code srt1.<sessionId>.<secret>}. Only its SHA-256 digest is stored. */
public record StudentRefreshToken(String sessionId, String value) {
    private static final String VERSION = "srt1";

    public static StudentRefreshToken parse(String value) {
        if (value == null || value.length() > 512) throw new InvalidRefreshTokenException();
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals(VERSION) || !TokenSupport.canonical(parts[1], 16)
                || !TokenSupport.canonical(parts[2], 32)) throw new InvalidRefreshTokenException();
        return new StudentRefreshToken(parts[1], value);
    }

    public static StudentRefreshToken generate(String sessionId) {
        return parse(VERSION + "." + sessionId + "." + TokenSupport.random(32));
    }

    public String digest() { return TokenSupport.digest(value); }

    @Override public String toString() { return "StudentRefreshToken[REDACTED]"; }
}
