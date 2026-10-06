package com.smartAiUniversityAssistant.seniorproject.feature.course.enums;

import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.InvalidCourseTypeException;

public enum CourseType {
    GENERAL_EDUCATION(100, "General Education"),
    MAJOR_ELECTIVE(200, "Major Elective"),
    MAJOR_REQUIRED(300, "Major Required");

    private final int code;
    private final String displayName;

    CourseType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static CourseType fromCode(int code) {
        for (CourseType type : values()) {
            if (type.code == code) return type;
        }
        throw new InvalidCourseTypeException("Invalid course type: " + code);
    }
}
