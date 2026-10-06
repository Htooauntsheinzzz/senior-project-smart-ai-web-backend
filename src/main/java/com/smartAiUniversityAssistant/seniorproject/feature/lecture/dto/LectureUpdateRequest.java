package com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record LectureUpdateRequest(
        @NotBlank @Size(max = 255) String lectureNameTh,
        @NotBlank @Size(max = 255) String lectureNameEn,
        @Size(max = 100) String lectureNickname,
        @NotNull @Positive Long facultyId,
        @NotNull @Positive Long departmentId,
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    public LectureUpdateRequest {
        lectureNameTh = lectureNameTh == null ? null : lectureNameTh.trim();
        lectureNameEn = lectureNameEn == null ? null : lectureNameEn.trim();
        lectureNickname = lectureNickname == null || lectureNickname.isBlank() ? null : lectureNickname.trim();
        status = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
    }
}
