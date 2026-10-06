package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface StudentService {
    StudentResponse create(AuthenticatedUser actor, StudentCreateRequest request);
    StudentPageResponse list(AuthenticatedUser actor, StudentListQuery query);
    StudentSummaryResponse summary(AuthenticatedUser actor);
    StudentResponse detail(AuthenticatedUser actor, long id);
    StudentResponse update(AuthenticatedUser actor, long id, StudentUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    void resetPassword(AuthenticatedUser actor, long id, StudentPasswordResetRequest request);
}
