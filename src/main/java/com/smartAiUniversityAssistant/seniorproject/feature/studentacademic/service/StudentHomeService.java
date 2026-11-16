package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentHomeSummaryResponse;

public interface StudentHomeService {
    StudentHomeSummaryResponse getSummary(long studentId);
}
