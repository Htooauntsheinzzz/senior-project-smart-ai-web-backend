package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.service;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CourseExamService {
    AcademicActivityPageResponse<CourseExamResponse> list(AuthenticatedUser actor, AcademicActivityListQuery query);
    CourseExamResponse detail(AuthenticatedUser actor, long id);
    CourseExamResponse create(AuthenticatedUser actor, CourseExamRequest request);
    CourseExamResponse update(AuthenticatedUser actor, long id, CourseExamRequest request);
    CourseExamResponse schedule(AuthenticatedUser actor, long id);
    CourseExamResponse cancel(AuthenticatedUser actor, long id);
    CourseExamResponse complete(AuthenticatedUser actor, long id);
    void delete(AuthenticatedUser actor, long id);
}
