package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

/** Spec file 61: the email is verified and the account can sign in. */
public record VerifyEmailResponse(String email, String accountStatus, String message) {}
