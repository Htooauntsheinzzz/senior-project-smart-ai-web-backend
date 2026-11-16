package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;

public final class CourseSectionCreateRequest {
    @NotNull @Positive private Long courseId;
    @NotBlank @Size(max = 20) private String sectionNumber;
    @Positive private Integer capacity;
    @NotNull @Positive private Long lectureId;
    @NotNull @Positive private Long semesterId;
    // Optional until every section is reconciled with a verified term (spec file 42, section 10); needed before timetabling.
    @Positive private Long academicTermId;
    private String status = "ACTIVE";

    public Long courseId() { return courseId; }
    public String sectionNumber() { return sectionNumber; }
    public Integer capacity() { return capacity; }
    public Long lectureId() { return lectureId; }
    public Long semesterId() { return semesterId; }
    public Long academicTermId() { return academicTermId; }
    public String status() { return status; }
    public void setCourseId(Long value) { courseId = value; }
    public void setSectionNumber(String value) { sectionNumber = value == null ? null : value.trim(); }
    public void setCapacity(Integer value) { capacity = value; }
    public void setLectureId(Long value) { lectureId = value; }
    public void setSemesterId(Long value) { semesterId = value; }
    public void setAcademicTermId(Long value) { academicTermId = value; }
    @JsonSetter(value = "status", nulls = Nulls.FAIL)
    public void setStatus(String value) { status = value == null ? null : value.trim(); }
}
