package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CourseSectionService {
    CourseSectionResponse create(AuthenticatedUser actor, CourseSectionCreateRequest request);
    CourseSectionPageResponse list(AuthenticatedUser actor, CourseSectionListQuery query);
    CourseSectionResponse detail(AuthenticatedUser actor, long id);
    CourseSectionResponse update(AuthenticatedUser actor, long id, CourseSectionUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    CourseSectionSummaryResponse summary(AuthenticatedUser actor);
}
