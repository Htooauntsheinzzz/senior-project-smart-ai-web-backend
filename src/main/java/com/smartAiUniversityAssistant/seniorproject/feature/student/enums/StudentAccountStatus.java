package com.smartAiUniversityAssistant.seniorproject.feature.student.enums;

import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.InvalidStudentAccountStatusException;
import java.util.Locale;

public enum StudentAccountStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    SUSPENDED;

    public static StudentAccountStatus parse(String value) {
        if (value == null) throw new InvalidStudentAccountStatusException();
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidStudentAccountStatusException();
        }
    }

    public static boolean isCredentialedStatus(String value) {
        return ACTIVE.name().equals(value) || INACTIVE.name().equals(value) || SUSPENDED.name().equals(value);
    }
}
