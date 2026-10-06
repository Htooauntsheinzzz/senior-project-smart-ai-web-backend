package com.smartAiUniversityAssistant.seniorproject.feature.department.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;

public final class DepartmentCreateRequest {
    @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9-]+$") private String departmentCode;
    @NotBlank @Size(max = 255) private String departmentName;
    @NotNull @Positive private Long facultyId;
    @NotNull private Boolean isActive = true;

    public String departmentCode() { return departmentCode; }
    public String departmentName() { return departmentName; }
    public Long facultyId() { return facultyId; }
    public boolean isActive() { return isActive; }
    public void setDepartmentCode(String value) { departmentCode = value == null ? null : value.trim(); }
    public void setDepartmentName(String value) { departmentName = value == null ? null : value.trim(); }
    public void setFacultyId(Long value) { facultyId = value; }
    @JsonSetter(value = "isActive", nulls = Nulls.FAIL)
    public void setIsActive(Boolean value) { isActive = value; }
}
