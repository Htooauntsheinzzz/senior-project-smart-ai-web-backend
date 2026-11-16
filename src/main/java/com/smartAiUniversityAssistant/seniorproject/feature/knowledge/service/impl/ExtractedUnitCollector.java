package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ExtractedUnit;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ExtractionLimits;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Collects extracted lines into units while the extractor reads, enforcing max-pages and max-chars at once and
 * splitting DOCX, XLSX and TXT units at unit-max-chars (spec file 47, section 7): when the unit reaches the limit and
 * the current line ends, the unit is closed and a new one with the same type and label begins. A single longer line is
 * cut at the limit, never between the two halves of a surrogate pair.
 */
final class ExtractedUnitCollector {
    static final String TOO_MANY_PAGES = "DOCUMENT_TOO_MANY_PAGES";
    static final String TOO_LARGE = "DOCUMENT_TEXT_TOO_LARGE";
    private final ExtractionLimits limits;
    private final List<ExtractedUnit> units = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private DocumentUnitType type;
    private String label;
    private boolean open;
    private long totalChars;
    private int lines;

    ExtractedUnitCollector(ExtractionLimits limits) { this.limits = limits; }

    /** Closes the current unit (if it has text) and opens a new one. */
    void begin(DocumentUnitType unitType, String unitLabel) {
        closeIfNotEmpty();
        type = unitType;
        label = unitLabel;
        open = true;
    }

    /** Appends one line (without its line break) to the current unit. */
    void line(String text) {
        checkInterrupted();
        count(text.length());
        int start = 0;
        while (text.length() - start > limits.unitMaxChars()) {
            int end = start + limits.unitMaxChars();
            if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
            closeIfNotEmpty();
            append(text.substring(start, end));
            closeIfNotEmpty();
            start = end;
        }
        append(start == 0 ? text : text.substring(start));
        if (current.length() >= limits.unitMaxChars()) closeIfNotEmpty();
    }

    private void append(String line) {
        if (lines > 0) current.append('\n');
        current.append(line);
        lines++;
    }

    /** A whole unit that is never split (a PDF page). Empty pages are kept. */
    void whole(DocumentUnitType unitType, String unitLabel, String text) {
        checkInterrupted();
        closeIfNotEmpty();
        count(text.length());
        add(new ExtractedUnit(unitType, unitLabel, text));
        open = false;
    }

    List<ExtractedUnit> finish() {
        closeIfNotEmpty();
        return List.copyOf(units);
    }

    private void closeIfNotEmpty() {
        if (open && current.length() > 0) add(new ExtractedUnit(type, label, current.toString()));
        current.setLength(0);
        lines = 0;
    }

    private void add(ExtractedUnit unit) {
        if (units.size() >= limits.maxPages())
            throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, TOO_MANY_PAGES, false,
                    "The document has more pages than the configured limit.");
        units.add(unit);
    }

    void count(int chars) {
        totalChars += chars;
        if (totalChars > limits.maxChars())
            throw new KnowledgeIngestionException(IngestionStageType.EXTRACT, TOO_LARGE, false,
                    "The document contains more text than the configured limit.");
    }

    static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new UncheckedIOException(new InterruptedIOException("extraction interrupted"));
    }
}
