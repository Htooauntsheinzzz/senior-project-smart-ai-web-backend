package com.smartAiUniversityAssistant.seniorproject.feature.campus.service;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;

public interface CampusNavigationService {
    CampusPageResponse<CampusNodeResponse> nodes(CampusPageQuery query);
    CampusNodeResponse node(long id);
    CampusNodeResponse createNode(AuthenticatedUser actor, CampusNodeCreateRequest request);
    CampusNodeResponse updateNode(AuthenticatedUser actor, long id, CampusNodeUpdateRequest request);
    void deleteNode(AuthenticatedUser actor, long id);
    CampusPageResponse<CampusEdgeResponse> edges(CampusPageQuery query);
    CampusEdgeResponse edge(long id);
    CampusEdgeResponse createEdge(AuthenticatedUser actor, CampusEdgeCreateRequest request);
    CampusEdgeResponse updateEdge(AuthenticatedUser actor, long id, CampusEdgeUpdateRequest request);
    CampusEdgeResponse edgeStatus(AuthenticatedUser actor, long id, CampusEdgeStatusRequest request);
    void deleteEdge(AuthenticatedUser actor, long id);
    CampusEdgeStatisticsResponse edgeStatistics(Long siteId);
    List<CampusDestinationResponse> destinations(Long siteId, Long nodeId, Long buildingId, Long placeId);
    CampusDestinationResponse createDestination(AuthenticatedUser actor, CampusDestinationRequest request);
    CampusDestinationResponse updateDestination(AuthenticatedUser actor, long id, CampusDestinationRequest request);
    void deleteDestination(long id);
}
