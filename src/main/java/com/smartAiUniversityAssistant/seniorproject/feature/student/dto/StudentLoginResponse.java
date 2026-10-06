package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

public record StudentLoginResponse(String tokenType, String accessToken, long expiresIn,
        String refreshToken, long refreshExpiresIn, boolean forcePasswordChange, StudentMeResponse user) {
    @Override public String toString() { return "StudentLoginResponse[REDACTED]"; }
}
