package com.smartAiUniversityAssistant.seniorproject.feature.program.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public final class ProgramUpdateRequest {
    @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9-]+$") private String programCode;
    @NotBlank @Size(max = 255) private String programName;
    @NotBlank @Size(max = 100) private String degreeLevel;
    @NotNull @Positive private Long facultyId;
    @NotNull @Positive private Long departmentId;
    @Positive private Integer durationYears;
    @Positive private Integer totalCredits;
    @NotNull private Boolean isActive;

    public String programCode() { return programCode; }
    public String programName() { return programName; }
    public String degreeLevel() { return degreeLevel; }
    public Long facultyId() { return facultyId; }
    public Long departmentId() { return departmentId; }
    public Integer durationYears() { return durationYears; }
    public Integer totalCredits() { return totalCredits; }
    public Boolean isActive() { return isActive; }

    public void setProgramCode(String value) { programCode = value == null ? null : value.trim(); }
    public void setProgramName(String value) { programName = value == null ? null : value.trim(); }
    @JsonSetter("degreeLevel")
    public void setDegreeLevel(JsonNode value) {
        if (value != null && !value.isNull() && !value.isString())
            throw new IllegalArgumentException("degreeLevel must be a JSON string");
        degreeLevel = value == null || value.isNull() ? null : value.stringValue().trim();
    }
    public void setFacultyId(Long value) { facultyId = value; }
    public void setDepartmentId(Long value) { departmentId = value; }
    public void setDurationYears(Integer value) { durationYears = value; }
    public void setTotalCredits(Integer value) { totalCredits = value; }
    public void setIsActive(Boolean value) { isActive = value; }
}
