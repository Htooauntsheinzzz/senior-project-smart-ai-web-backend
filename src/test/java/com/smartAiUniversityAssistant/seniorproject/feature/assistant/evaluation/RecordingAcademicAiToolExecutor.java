package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolExecutor;
import java.util.*;

/**
 * Test-only decorator in front of the real tool executor: records every executed call with the backend's result for
 * the current case. Step 11 answers a not-found, ambiguous, empty or failed result without another provider call, so
 * those results never appear in a later provider request; this is where the evaluation observes them. Adds nothing
 * to the Student response.
 */
public final class RecordingAcademicAiToolExecutor implements AcademicAiToolExecutor {
    /** One executed call and the backend's result. */
    public record Execution(LlmToolCall call, AcademicAiToolResult result) {}

    private final AcademicAiToolExecutor delegate;
    private final List<Execution> executions = Collections.synchronizedList(new ArrayList<>());

    public RecordingAcademicAiToolExecutor(AcademicAiToolExecutor delegate) { this.delegate = delegate; }

    public void startCase() { executions.clear(); }

    public List<Execution> executions() { return List.copyOf(executions); }

    @Override
    public AcademicAiToolResult execute(LlmToolCall toolCall) {
        AcademicAiToolResult result = delegate.execute(toolCall);
        executions.add(new Execution(toolCall, result));
        return result;
    }
}
