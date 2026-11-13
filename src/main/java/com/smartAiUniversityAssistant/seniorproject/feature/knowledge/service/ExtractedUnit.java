package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;

/** One unit of extracted text in document order. An empty unit is the empty string, never null. */
public record ExtractedUnit(DocumentUnitType unitType, String label, String text) {}
