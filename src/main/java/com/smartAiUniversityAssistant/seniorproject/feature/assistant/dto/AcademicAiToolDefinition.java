package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.Map;

/** Provider-neutral tool declaration; parameterSchema is a JSON Schema object. The LLM client converts it. */
public record AcademicAiToolDefinition(String name, String description, Map<String, Object> parameterSchema) {}
