package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads one file and returns its text units in document order. No database, no Spring Security, no network. It enforces
 * the limits while reading, stops when its thread is interrupted, and may throw KnowledgeIngestionException (stage
 * EXTRACT) with a code of spec file 47 section 14. Exactly one extractor supports each extension.
 */
public interface DocumentTextExtractor {
    boolean supports(String fileExtension);
    List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException;
}
