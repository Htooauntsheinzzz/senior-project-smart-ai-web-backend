package com.smartAiUniversityAssistant.seniorproject.feature.department.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record DepartmentUpdateRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9-]+$") String departmentCode,
        @NotBlank @Size(max = 255) String departmentName,
        @NotNull @Positive Long facultyId,
        @NotNull @JsonProperty("isActive") Boolean isActive) {
    public DepartmentUpdateRequest {
        departmentCode = departmentCode == null ? null : departmentCode.trim();
        departmentName = departmentName == null ? null : departmentName.trim();
    }
}
