package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;

/**
 * Step 16A: the token Student's academic progress totals, for the assistant. Read-only. A separate interface, so that
 * AcademicQueryService and its hand-written test doubles stay unchanged.
 */
public interface AcademicProgressQueryService {
    AcademicProgressQueryResult getMyAcademicProgress();
}
