package com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import java.util.Locale;

public final class LectureCreateRequest {
    @NotBlank @Size(max = 255) private String lectureNameTh;
    @NotBlank @Size(max = 255) private String lectureNameEn;
    @Size(max = 100) private String lectureNickname;
    @NotNull @Positive private Long facultyId;
    @NotNull @Positive private Long departmentId;
    @Pattern(regexp = "ACTIVE|INACTIVE") private String status = "ACTIVE";

    public String lectureNameTh() { return lectureNameTh; }
    public String lectureNameEn() { return lectureNameEn; }
    public String lectureNickname() { return lectureNickname; }
    public Long facultyId() { return facultyId; }
    public Long departmentId() { return departmentId; }
    public String status() { return status; }
    public void setLectureNameTh(String value) { lectureNameTh = value == null ? null : value.trim(); }
    public void setLectureNameEn(String value) { lectureNameEn = value == null ? null : value.trim(); }
    public void setLectureNickname(String value) { lectureNickname = value == null || value.isBlank() ? null : value.trim(); }
    public void setFacultyId(Long value) { facultyId = value; }
    public void setDepartmentId(Long value) { departmentId = value; }
    @JsonSetter(value = "status", nulls = Nulls.FAIL)
    public void setStatus(String value) { status = value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
}
