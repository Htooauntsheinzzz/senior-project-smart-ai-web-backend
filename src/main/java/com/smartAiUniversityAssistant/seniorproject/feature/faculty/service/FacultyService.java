package com.smartAiUniversityAssistant.seniorproject.feature.faculty.service;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface FacultyService {
    FacultyResponse create(AuthenticatedUser actor, FacultyCreateRequest request);
    FacultyPageResponse list(AuthenticatedUser actor, FacultyListQuery query);
    FacultyResponse detail(AuthenticatedUser actor, long id);
    FacultyResponse update(AuthenticatedUser actor, long id, FacultyUpdateRequest request);
    void delete(AuthenticatedUser actor, long id);
    FacultySummaryResponse summary(AuthenticatedUser actor);
}
