package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

// Department and Program store a single name, so nameTh is always null for them.
public record AcademicReferenceResponse(Long id, String code, String nameEn, String nameTh) {}
