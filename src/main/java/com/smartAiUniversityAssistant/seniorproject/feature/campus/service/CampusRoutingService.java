package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;

public interface CampusRoutingService {
    CampusNearbyResponse nearby(CampusNearbyRequest request);
    CampusRouteResponse preview(CampusRoutePreviewRequest request);
}
