package com.smartAiUniversityAssistant.seniorproject.feature.campus.enums;

/** Mobile filter groups stored on a category. ALL is a request filter and is deliberately not a value here. */
public enum CampusMobileGroup {
    BUILDINGS("Buildings"), FOOD("Food"), LIBRARY("Library"), STUDENT_SERVICES("Student Services"),
    HEALTH("Health"), PARKING("Parking"), OTHER("Other");

    private final String displayName;
    CampusMobileGroup(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
