package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

public interface CampusRoomService {
    CampusPageResponse<CampusRoomResponse> list(CampusPageQuery query);
    CampusRoomResponse detail(long id);
    CampusRoomResponse create(AuthenticatedUser actor, CampusRoomRequest request);
    CampusRoomResponse update(AuthenticatedUser actor, long id, CampusRoomRequest request);
    CampusRoomResponse status(AuthenticatedUser actor, long id, CampusRoomStatusRequest request);
    void delete(AuthenticatedUser actor, long id);
    CampusRoomStatisticsResponse statistics(Long siteId, Long buildingId);
}
