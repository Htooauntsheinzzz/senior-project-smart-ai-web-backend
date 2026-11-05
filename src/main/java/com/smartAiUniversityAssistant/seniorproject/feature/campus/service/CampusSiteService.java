package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CampusSiteService {
    CampusPageResponse<CampusSiteResponse> list(CampusPageQuery query);
    CampusSiteResponse detail(long id);
    CampusSiteResponse create(AuthenticatedUser actor, CampusSiteRequest request);
    CampusSiteResponse update(AuthenticatedUser actor, long id, CampusSiteRequest request);
    void delete(AuthenticatedUser actor, long id);
}
