package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;

public final class CourseSectionCreateRequest {
    @NotNull @Positive private Long courseId;
    @NotBlank @Size(max = 20) private String sectionNumber;
    @Positive private Integer capacity;
    @NotNull @Positive private Long lectureId;
    @Size(max = 100) private String room;
    @Size(max = 255) private String schedule;
    @NotNull @Positive private Long semesterId;
    private String status = "ACTIVE";

    public Long courseId() { return courseId; }
    public String sectionNumber() { return sectionNumber; }
    public Integer capacity() { return capacity; }
    public Long lectureId() { return lectureId; }
    public String room() { return room; }
    public String schedule() { return schedule; }
    public Long semesterId() { return semesterId; }
    public String status() { return status; }
    public void setCourseId(Long value) { courseId = value; }
    public void setSectionNumber(String value) { sectionNumber = value == null ? null : value.trim(); }
    public void setCapacity(Integer value) { capacity = value; }
    public void setLectureId(Long value) { lectureId = value; }
    public void setRoom(String value) { room = value == null || value.isBlank() ? null : value.trim(); }
    public void setSchedule(String value) { schedule = value == null || value.isBlank() ? null : value.trim(); }
    public void setSemesterId(Long value) { semesterId = value; }
    @JsonSetter(value = "status", nulls = Nulls.FAIL)
    public void setStatus(String value) { status = value == null ? null : value.trim(); }
}
