package com.smartAiUniversityAssistant.seniorproject.common;

import java.util.regex.Pattern;

/**
 * Academic-year labels such as "2024-2025". The en dash form "2024–2025" is accepted and stored with a hyphen, so
 * filters match one spelling; the second year must follow the first.
 */
public final class AcademicYearLabel {
    private static final Pattern LABEL = Pattern.compile("^(\\d{4})\\s*[-–]\\s*(\\d{4})$");

    private AcademicYearLabel() {}

    /** The normalized label, or null when the value is not a valid academic-year label. */
    public static String normalize(String value) {
        if (value == null) return null;
        var matcher = LABEL.matcher(value.trim());
        if (!matcher.matches()) return null;
        int first = Integer.parseInt(matcher.group(1));
        int second = Integer.parseInt(matcher.group(2));
        if (first < 2000 || first > 2100 || second != first + 1) return null;
        return first + "-" + second;
    }
}
