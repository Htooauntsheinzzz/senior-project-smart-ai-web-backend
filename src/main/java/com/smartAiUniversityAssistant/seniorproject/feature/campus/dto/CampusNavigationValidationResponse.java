package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.util.List;

/** Blocking problems make valid false. Inactive edges, missing geometry and accessibility gaps are warnings only. */
public record CampusNavigationValidationResponse(Long siteId, boolean valid, Summary summary, List<Issue> disconnectedNodes,
        List<Issue> unreachableDestinations, List<Issue> invalidCoordinates, List<Issue> inactiveEdges, List<Issue> missingPrimaryEntrances,
        List<Issue> missingRouteGeometry, List<Issue> accessibilityGaps, List<Issue> crossCampusErrors) {
    public record Issue(String entityType, Long id, String code, String detail) {}
    public record Summary(long activeNodes, long activeEdges, long destinations, int blockingIssues, int warnings) {}
}
