package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;

public interface CampusMapService {
    CampusAdminMapResponse map(Long siteId, boolean includeBuildings, boolean includePlaces, boolean includeNodes, boolean includeEdges,
            Long categoryId, String status);
}
