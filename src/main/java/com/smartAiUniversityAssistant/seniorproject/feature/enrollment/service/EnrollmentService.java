package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface EnrollmentService {
    EnrollmentResponse create(AuthenticatedUser actor, EnrollmentCreateRequest request);
    EnrollmentPageResponse list(AuthenticatedUser actor, EnrollmentListQuery query);
    EnrollmentSummaryResponse summary(AuthenticatedUser actor);
    EnrollmentResponse detail(AuthenticatedUser actor, long id);
    EnrollmentResponse update(AuthenticatedUser actor, long id, EnrollmentUpdateRequest request);
    EnrollmentResponse updateStatus(AuthenticatedUser actor, long id, EnrollmentStatusUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
}
