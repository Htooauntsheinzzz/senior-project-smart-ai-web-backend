package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

/**
 * Spec file 61: the answer to a registration or to a request for a new verification mail. It is the same whether or
 * not the address already has an account, so the endpoint does not reveal which addresses are registered.
 */
public record AdminRegistrationResponse(String email, String message) {}
