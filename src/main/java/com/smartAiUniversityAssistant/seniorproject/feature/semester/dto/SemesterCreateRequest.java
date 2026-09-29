package com.smartAiUniversityAssistant.seniorproject.feature.semester.dto;

import jakarta.validation.constraints.*;

public final class SemesterCreateRequest {
    @NotBlank @Size(max = 150) private String semesterNameTh;
    @NotBlank @Size(max = 150) private String semesterNameEn;

    public String semesterNameTh() { return semesterNameTh; }
    public String semesterNameEn() { return semesterNameEn; }
    public void setSemesterNameTh(String value) { semesterNameTh = value == null ? null : value.trim(); }
    public void setSemesterNameEn(String value) { semesterNameEn = value == null ? null : value.trim(); }
}
