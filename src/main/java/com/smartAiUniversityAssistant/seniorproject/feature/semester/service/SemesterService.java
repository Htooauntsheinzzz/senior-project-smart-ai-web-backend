package com.smartAiUniversityAssistant.seniorproject.feature.semester.service;

import com.smartAiUniversityAssistant.seniorproject.feature.semester.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface SemesterService {
    SemesterResponse create(AuthenticatedUser actor, SemesterCreateRequest request);
    SemesterPageResponse list(AuthenticatedUser actor, SemesterListQuery query);
    SemesterResponse detail(AuthenticatedUser actor, long id);
    SemesterResponse update(AuthenticatedUser actor, long id, SemesterUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
}
