package com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto;

import java.time.Instant;

public record LectureResponse(Long id, String lectureNameTh, String lectureNameEn, String lectureNickname,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        String status, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
