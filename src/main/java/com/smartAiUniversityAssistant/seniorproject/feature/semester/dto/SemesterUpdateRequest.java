package com.smartAiUniversityAssistant.seniorproject.feature.semester.dto;

import jakarta.validation.constraints.*;

public record SemesterUpdateRequest(
        @NotNull @Min(2000) @Max(2100) Integer academicYear,
        @NotBlank @Size(max = 150) String semesterNameTh,
        @NotBlank @Size(max = 150) String semesterNameEn) {
    public SemesterUpdateRequest {
        semesterNameTh = semesterNameTh == null ? null : semesterNameTh.trim();
        semesterNameEn = semesterNameEn == null ? null : semesterNameEn.trim();
    }
}
