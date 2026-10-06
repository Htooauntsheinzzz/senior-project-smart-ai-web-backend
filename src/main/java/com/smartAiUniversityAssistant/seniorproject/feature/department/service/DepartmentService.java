package com.smartAiUniversityAssistant.seniorproject.feature.department.service;

import com.smartAiUniversityAssistant.seniorproject.feature.department.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface DepartmentService {
    DepartmentResponse create(AuthenticatedUser actor, DepartmentCreateRequest request);
    DepartmentPageResponse list(AuthenticatedUser actor, DepartmentListQuery query);
    DepartmentResponse detail(AuthenticatedUser actor, long id);
    DepartmentResponse update(AuthenticatedUser actor, long id, DepartmentUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    DepartmentSummaryResponse summary(AuthenticatedUser actor);
}
