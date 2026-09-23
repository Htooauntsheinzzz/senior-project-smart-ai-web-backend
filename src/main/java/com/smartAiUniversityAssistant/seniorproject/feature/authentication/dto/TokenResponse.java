package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;
public record TokenResponse(String tokenType, String accessToken, long expiresIn,
        String refreshToken, long refreshExpiresIn, boolean forcePasswordChange, CurrentUserResponse user) {
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
