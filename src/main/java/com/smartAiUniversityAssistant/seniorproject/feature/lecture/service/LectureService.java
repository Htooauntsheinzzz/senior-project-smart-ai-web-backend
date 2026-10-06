package com.smartAiUniversityAssistant.seniorproject.feature.lecture.service;

import com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface LectureService {
    LectureResponse create(AuthenticatedUser actor, LectureCreateRequest request);
    LecturePageResponse list(AuthenticatedUser actor, LectureListQuery query);
    LectureResponse detail(AuthenticatedUser actor, long id);
    LectureResponse update(AuthenticatedUser actor, long id, LectureUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    LectureSummaryResponse summary(AuthenticatedUser actor);
}
