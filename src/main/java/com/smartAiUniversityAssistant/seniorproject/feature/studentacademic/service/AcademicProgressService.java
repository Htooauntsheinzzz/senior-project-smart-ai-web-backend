package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.*;

public interface AcademicProgressService {
    StudentAcademicProgressResponse getProgress(long studentId);
    StudentCurrentSemesterSummaryResponse getCurrentSemesterSummary(long studentId);
}
