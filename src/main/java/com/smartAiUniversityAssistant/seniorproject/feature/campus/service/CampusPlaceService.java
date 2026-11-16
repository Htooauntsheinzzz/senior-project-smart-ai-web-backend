package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

public interface CampusPlaceService {
    CampusPageResponse<CampusPlaceResponse> list(CampusPageQuery query);
    CampusPlaceResponse detail(long id);
    CampusPlaceResponse create(AuthenticatedUser actor, CampusPlaceCreateRequest request);
    CampusPlaceResponse update(AuthenticatedUser actor, long id, CampusPlaceUpdateRequest request);
    CampusPlaceResponse status(AuthenticatedUser actor, long id, CampusPlaceStatusRequest request);
    CampusPlaceResponse visibility(AuthenticatedUser actor, long id, CampusPlaceVisibilityRequest request);
    CampusPlaceResponse popular(AuthenticatedUser actor, long id, CampusPlacePopularRequest request);
    CampusPlaceResponse facilities(AuthenticatedUser actor, long id, CampusFacilityIdsRequest request);
    void delete(AuthenticatedUser actor, long id);
    CampusPlaceStatisticsResponse statistics(Long siteId);
    List<CampusOpeningHourDto> openingHours(AuthenticatedUser actor, long id, CampusOpeningHoursRequest request);
    List<CampusOpeningExceptionDto> openingExceptions(AuthenticatedUser actor, long id, CampusOpeningExceptionsRequest request);
}
