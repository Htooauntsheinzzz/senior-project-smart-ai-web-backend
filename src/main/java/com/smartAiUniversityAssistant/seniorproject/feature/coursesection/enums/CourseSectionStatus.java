package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception.InvalidCourseSectionStatusException;

public enum CourseSectionStatus {
    ACTIVE("ACTIVE"),
    CLOSED("CLOSED");

    private final String value;

    CourseSectionStatus(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static CourseSectionStatus fromValue(String value) {
        for (CourseSectionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) return status;
        }
        throw new InvalidCourseSectionStatusException("Invalid course section status: " + value);
    }
}
