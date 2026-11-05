package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CourseAssignmentService {
    AcademicActivityPageResponse<CourseAssignmentResponse> list(AuthenticatedUser actor, AcademicActivityListQuery query);
    CourseAssignmentResponse detail(AuthenticatedUser actor, long id);
    CourseAssignmentResponse create(AuthenticatedUser actor, CourseAssignmentRequest request);
    CourseAssignmentResponse update(AuthenticatedUser actor, long id, CourseAssignmentRequest request);
    CourseAssignmentResponse publish(AuthenticatedUser actor, long id);
    CourseAssignmentResponse close(AuthenticatedUser actor, long id);
    void delete(AuthenticatedUser actor, long id);
}
