package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

public interface CampusBuildingService {
    CampusPageResponse<CampusBuildingListResponse> list(CampusPageQuery query);
    CampusBuildingResponse detail(long id);
    CampusBuildingResponse create(AuthenticatedUser actor, CampusBuildingCreateRequest request);
    CampusBuildingResponse update(AuthenticatedUser actor, long id, CampusBuildingUpdateRequest request);
    CampusBuildingResponse status(AuthenticatedUser actor, long id, CampusBuildingStatusRequest request);
    void delete(AuthenticatedUser actor, long id);
    CampusBuildingStatisticsResponse statistics(Long siteId);
    List<CampusRoomResponse> rooms(long id);
    List<CampusOpeningHourDto> openingHours(AuthenticatedUser actor, long id, CampusOpeningHoursRequest request);
    List<CampusOpeningExceptionDto> openingExceptions(AuthenticatedUser actor, long id, CampusOpeningExceptionsRequest request);
}
