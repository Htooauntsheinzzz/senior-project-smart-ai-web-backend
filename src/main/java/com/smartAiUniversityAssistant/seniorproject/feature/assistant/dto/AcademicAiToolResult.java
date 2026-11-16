package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of one tool call. payload() is exactly what the model receives: {success, data} or {success, code,
 * message[, candidates]}. resultCount and errorCode are for logs, which never carry the data itself. neighborCount
 * (Step 15B, logs only) is the number of neighbour passages among the data of a document search.
 */
public record AcademicAiToolResult(String toolName, boolean success, Object data, String errorCode, String errorMessage,
        Object candidates, int resultCount, int neighborCount) {

    public static AcademicAiToolResult success(String toolName, Object data, int resultCount) {
        return success(toolName, data, resultCount, 0);
    }

    public static AcademicAiToolResult success(String toolName, Object data, int resultCount, int neighborCount) {
        return new AcademicAiToolResult(toolName, true, data, null, null, null, resultCount, neighborCount);
    }

    public static AcademicAiToolResult failure(String toolName, String code, String message) {
        return failure(toolName, code, message, null);
    }

    public static AcademicAiToolResult failure(String toolName, String code, String message, Object candidates) {
        return new AcademicAiToolResult(toolName, false, null, code, message, candidates, 0, 0);
    }

    public Map<String, Object> payload() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("success", success);
        if (success) {
            payload.put("data", data);
        } else {
            payload.put("code", errorCode);
            payload.put("message", errorMessage);
            if (candidates != null) payload.put("candidates", candidates);
        }
        return payload;
    }
}
