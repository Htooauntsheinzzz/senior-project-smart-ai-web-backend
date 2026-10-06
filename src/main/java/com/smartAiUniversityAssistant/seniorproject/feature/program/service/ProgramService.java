package com.smartAiUniversityAssistant.seniorproject.feature.program.service;

import com.smartAiUniversityAssistant.seniorproject.feature.program.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface ProgramService {
    ProgramResponse create(AuthenticatedUser actor, ProgramCreateRequest request);
    ProgramPageResponse list(AuthenticatedUser actor, ProgramListQuery query);
    ProgramResponse detail(AuthenticatedUser actor, long id);
    ProgramResponse update(AuthenticatedUser actor, long id, ProgramUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    ProgramSummaryResponse summary(AuthenticatedUser actor);
}
