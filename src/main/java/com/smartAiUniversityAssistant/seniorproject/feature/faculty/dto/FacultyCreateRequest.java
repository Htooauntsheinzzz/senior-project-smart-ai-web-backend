package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;

public final class FacultyCreateRequest {
    @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9-]+$") private String facultyCode;
    @NotBlank @Size(max = 255) private String facultyNameEn;
    @Size(max = 255) private String facultyNameTh;
    @NotNull private Boolean isActive = true;

    public FacultyCreateRequest() {}

    public String facultyCode() { return facultyCode; }
    public String facultyNameEn() { return facultyNameEn; }
    public String facultyNameTh() { return facultyNameTh; }
    public boolean isActive() { return isActive; }

    public void setFacultyCode(String value) { facultyCode = trim(value); }
    public void setFacultyNameEn(String value) { facultyNameEn = trim(value); }
    public void setFacultyNameTh(String value) {
        String trimmed = trim(value);
        facultyNameTh = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
    @JsonSetter(value = "isActive", nulls = Nulls.FAIL)
    public void setIsActive(Boolean value) { isActive = value; }

    private static String trim(String value) { return value == null ? null : value.trim(); }
}
