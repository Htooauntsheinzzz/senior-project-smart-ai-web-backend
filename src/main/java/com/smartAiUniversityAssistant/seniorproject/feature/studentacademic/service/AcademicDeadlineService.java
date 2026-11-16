package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentDeadlinesResponse;
import java.time.OffsetDateTime;

public interface AcademicDeadlineService {
    /** Null arguments take the configured defaults: from now, for the default window, up to the default limit. */
    StudentDeadlinesResponse getDeadlines(long studentId, Integer limit, OffsetDateTime from, OffsetDateTime to);
}
