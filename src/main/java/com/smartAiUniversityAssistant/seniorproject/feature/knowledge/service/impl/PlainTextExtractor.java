package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TXT (spec file 47, section 11): strict UTF-8, read through a buffer, a leading byte order mark removed. A form feed
 * ends the current TEXT unit (empty units are skipped); \r\n and \r end a line like \n.
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {
    @Override public boolean supports(String fileExtension) { return "txt".equals(fileExtension); }

    @Override
    public List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var units = new ExtractedUnitCollector(limits);
        units.begin(DocumentUnitType.TEXT, null);
        try (Reader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), decoder), 64 * 1024)) {
            var line = new StringBuilder();
            char[] buffer = new char[16 * 1024];
            boolean first = true;
            boolean afterCarriageReturn = false;
            for (int read; (read = reader.read(buffer)) != -1; ) {
                ExtractedUnitCollector.checkInterrupted();
                for (int i = 0; i < read; i++) {
                    char c = buffer[i];
                    if (first) {
                        first = false;
                        if (c == '﻿') continue;
                    }
                    if (afterCarriageReturn) {
                        afterCarriageReturn = false;
                        if (c == '\n') continue;
                    }
                    switch (c) {
                        case '\n' -> endLine(units, line);
                        case '\r' -> {
                            endLine(units, line);
                            afterCarriageReturn = true;
                        }
                        case '\f' -> {
                            if (!line.isEmpty()) endLine(units, line);
                            units.begin(DocumentUnitType.TEXT, null);
                        }
                        default -> {
                            line.append(c);
                            if (line.length() > limits.maxChars()) units.count(line.length());
                        }
                    }
                }
            }
            if (!line.isEmpty()) endLine(units, line);
        }
        return units.finish();
    }

    private static void endLine(ExtractedUnitCollector units, StringBuilder line) {
        units.line(line.toString());
        line.setLength(0);
    }
}
