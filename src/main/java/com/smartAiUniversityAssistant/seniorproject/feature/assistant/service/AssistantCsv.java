package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spec file 59: CSV for the Admin exports. Every field is quoted, lines end with CRLF, and the file starts with a UTF-8
 * byte order mark so that spreadsheet programs read Thai text correctly. A value that a spreadsheet would run as a
 * formula (it starts with =, +, -, @, tab or carriage return) gets a leading apostrophe.
 */
public final class AssistantCsv {
    private AssistantCsv() {}

    public static byte[] write(List<String> header, List<List<String>> rows) {
        var out = new StringBuilder("﻿");
        line(out, header);
        for (List<String> row : rows) line(out, row);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder out, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('"').append(safe(values.get(i)).replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }

    static String safe(String value) {
        if (value == null || value.isEmpty()) return "";
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
