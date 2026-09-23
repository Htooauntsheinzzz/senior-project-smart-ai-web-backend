package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
public record RefreshToken(long userId, String sessionId, String value) {
    public static RefreshToken parse(String value) {
        if (value == null || value.length()>512) throw AuthenticationFailure.refresh();
        String[] parts=value.split("\\.",-1);
        if (parts.length!=4 || !parts[0].equals("rt1") || !TokenSupport.userId(parts[1])
                || !TokenSupport.canonical(parts[2],16) || !TokenSupport.canonical(parts[3],32)) throw AuthenticationFailure.refresh();
        return new RefreshToken(Long.parseLong(parts[1]),parts[2],value);
    }
    public static RefreshToken generate(long userId, String sid) {
        return parse("rt1."+userId+"."+sid+"."+TokenSupport.random(32));
    }
    public String digest() { return TokenSupport.digest(value); }
    @Override public String toString() { return "RefreshToken[REDACTED]"; }
}
