package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

/** One row of the page list; preview is the first 200 characters of the cleaned text, else of the raw text. */
public record DocumentTextPageSummaryResponse(int pageNumber, String unitType, String unitLabel, int rawCharCount,
        Integer cleanedCharCount, String preview) {}
