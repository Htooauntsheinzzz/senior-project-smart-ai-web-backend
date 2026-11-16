package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.service;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CurriculumVersionService {
    CurriculumResponse create(AuthenticatedUser actor, CurriculumCreateRequest request);
    CurriculumPageResponse list(AuthenticatedUser actor, CurriculumListQuery query);
    CurriculumResponse detail(AuthenticatedUser actor, long id);
    CurriculumResponse update(AuthenticatedUser actor, long id, CurriculumUpdateRequest request);
    CurriculumResponse activate(AuthenticatedUser actor, long id);
    CurriculumResponse archive(AuthenticatedUser actor, long id);
    void delete(AuthenticatedUser actor, long id);
}
