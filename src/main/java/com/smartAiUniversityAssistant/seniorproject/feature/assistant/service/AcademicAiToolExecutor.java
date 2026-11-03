package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;

/**
 * Validates and runs one model-requested tool against AcademicQueryService for the authenticated Student.
 * Unsupported tools, invalid arguments and expected academic errors come back as failure results for the model;
 * identity and eligibility failures propagate.
 */
public interface AcademicAiToolExecutor {
    AcademicAiToolResult execute(LlmToolCall toolCall);
}
