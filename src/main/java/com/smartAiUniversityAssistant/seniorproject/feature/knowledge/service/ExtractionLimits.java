package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** Limits an extractor enforces while it reads (spec file 47, section 5). */
public record ExtractionLimits(int maxPages, int maxChars, int unitMaxChars) {}
