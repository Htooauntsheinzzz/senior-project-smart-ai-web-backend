package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

public interface CampusCatalogService {
    List<CampusCategoryResponse> categories();
    CampusCategoryResponse createCategory(AuthenticatedUser actor, CampusCategoryRequest request);
    CampusCategoryResponse updateCategory(AuthenticatedUser actor, long id, CampusCategoryRequest request);
    List<CampusFacilityResponse> facilities(boolean activeOnly);
    CampusFacilityResponse createFacility(AuthenticatedUser actor, CampusFacilityRequest request);
    CampusFacilityResponse updateFacility(AuthenticatedUser actor, long id, CampusFacilityRequest request);
}
