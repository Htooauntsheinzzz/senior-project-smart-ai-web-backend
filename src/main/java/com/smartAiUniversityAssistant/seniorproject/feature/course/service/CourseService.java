package com.smartAiUniversityAssistant.seniorproject.feature.course.service;

import com.smartAiUniversityAssistant.seniorproject.feature.course.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CourseService {
    CourseResponse create(AuthenticatedUser actor, CourseCreateRequest request);
    CoursePageResponse list(AuthenticatedUser actor, CourseListQuery query);
    CourseResponse detail(AuthenticatedUser actor, long id);
    CourseResponse update(AuthenticatedUser actor, long id, CourseUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    CourseSummaryResponse summary(AuthenticatedUser actor);
}
