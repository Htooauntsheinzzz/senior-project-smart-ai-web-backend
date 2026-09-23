package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
public record CredentialVerificationResult(boolean accepted, Long userId, String revision) {
    public static CredentialVerificationResult rejected() { return new CredentialVerificationResult(false, null, null); }
    @Override public String toString() { return "CredentialVerificationResult[accepted=" + accepted + "]"; }
}
