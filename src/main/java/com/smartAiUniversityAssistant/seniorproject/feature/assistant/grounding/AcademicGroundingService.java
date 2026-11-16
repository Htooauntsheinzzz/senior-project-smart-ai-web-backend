package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import java.util.List;

/**
 * Step 11 grounding layer between the academic tools and Gemini's final answer. It turns the backend executor's tool
 * results into typed evidence and decides whether Gemini may phrase an answer from them or whether the backend must
 * reply itself (information not available, clarification needed, verification failed). It never queries PostgreSQL:
 * AcademicQueryService (through the tool executor) stays the only source of academic facts.
 */
public interface AcademicGroundingService {

    /** Evidence for one executed tool call; the only way verified evidence is created. */
    AcademicEvidence capture(LlmToolCall call, AcademicAiToolResult result);

    /** The decision for the evidence gathered so far in the current request (academicIntent may be null). */
    AcademicGroundingDecision evaluate(String academicIntent, List<AcademicEvidence> evidence);
}
