package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.service;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CourseResultService {
    CourseResultPageResponse list(AuthenticatedUser actor, CourseResultListQuery query);
    CourseResultResponse create(AuthenticatedUser actor, CourseResultCreateRequest request);
    CourseResultResponse update(AuthenticatedUser actor, long id, CourseResultUpdateRequest request);
    CourseResultResponse finalizeResult(AuthenticatedUser actor, long id);
}
