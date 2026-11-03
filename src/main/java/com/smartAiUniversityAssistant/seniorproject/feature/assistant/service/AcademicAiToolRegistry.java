package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import java.util.List;

/** Explicit allowlist of read-only academic tools; nothing is discovered from beans or methods. */
public interface AcademicAiToolRegistry {
    List<AcademicAiToolDefinition> getAvailableTools();
    boolean isSupported(String toolName);
}
