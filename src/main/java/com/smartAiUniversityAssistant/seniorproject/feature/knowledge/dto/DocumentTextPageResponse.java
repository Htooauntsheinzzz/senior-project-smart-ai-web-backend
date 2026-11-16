package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

/** One extracted unit of the current revision with its raw and cleaned text. */
public record DocumentTextPageResponse(long documentId, long fileId, int revisionNumber, int pageNumber, String unitType,
        String unitLabel, int rawCharCount, String rawText, Integer cleanedCharCount, String cleanedText) {}
