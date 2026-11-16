package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/** The Student of a conversation or a feedback row, as Admin screens show them. */
public record AssistantStudentReference(long id, String studentCode, String fullName) {}
