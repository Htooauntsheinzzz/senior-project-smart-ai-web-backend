package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentNextClassResponse;
import java.util.Optional;

public interface AcademicScheduleService {
    Optional<StudentNextClassResponse> getNextClass(long studentId);
}
