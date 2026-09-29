package com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto;

import java.time.Instant;

public record LectureListResponse(Long id, String lectureNameTh, String lectureNameEn, String lectureNickname,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        String status, Instant createdAt, Instant updatedAt) {}
