package com.smartAiUniversityAssistant.seniorproject.feature.campus.controller;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/campus/navigation")
public class AdminCampusNavigationController {
    private static final Set<String> NODE_SORT = Set.of("id", "nodeCode", "nodeName", "nodeType", "createdAt");
    private static final Set<String> EDGE_SORT = Set.of("id", "edgeCode", "distanceMeters", "surfaceType", "status", "createdAt");
    private final CampusNavigationService navigation;
    private final CampusNavigationValidationService validation;
    public AdminCampusNavigationController(CampusNavigationService navigation, CampusNavigationValidationService validation) {
        this.navigation = navigation;
        this.validation = validation;
    }

    @GetMapping("/nodes") public ResponseEntity<CampusPageResponse<CampusNodeResponse>> nodes(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.nodes(CampusPageQuery.from(request, Set.of("siteId", "nodeType", "isActive"), NODE_SORT, "nodeName,asc")));
    }
    @GetMapping("/nodes/{id}") public ResponseEntity<CampusNodeResponse> node(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.node(id)); }
    @PostMapping("/nodes") public ResponseEntity<CampusNodeResponse> createNode(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusNodeCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(navigation.createNode(actor, body));
    }
    @PutMapping("/nodes/{id}") public ResponseEntity<CampusNodeResponse> updateNode(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusNodeUpdateRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.updateNode(actor, id, body));
    }
    @DeleteMapping("/nodes/{id}") public ResponseEntity<Void> deleteNode(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        navigation.deleteNode(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/edges") public ResponseEntity<CampusPageResponse<CampusEdgeResponse>> edges(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.edges(CampusPageQuery.from(request, Set.of("siteId", "status", "surfaceType", "isWheelchairAccessible"), EDGE_SORT, "edgeCode,asc")));
    }
    @GetMapping("/edges/statistics") public ResponseEntity<CampusEdgeStatisticsResponse> edgeStatistics(@RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.edgeStatistics(siteId));
    }
    @GetMapping("/edges/{id}") public ResponseEntity<CampusEdgeResponse> edge(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.edge(id)); }
    @PostMapping("/edges") public ResponseEntity<CampusEdgeResponse> createEdge(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusEdgeCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(navigation.createEdge(actor, body));
    }
    @PutMapping("/edges/{id}") public ResponseEntity<CampusEdgeResponse> updateEdge(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusEdgeUpdateRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.updateEdge(actor, id, body));
    }
    @PatchMapping("/edges/{id}/status") public ResponseEntity<CampusEdgeResponse> edgeStatus(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusEdgeStatusRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.edgeStatus(actor, id, body));
    }
    @DeleteMapping("/edges/{id}") public ResponseEntity<Void> deleteEdge(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        navigation.deleteEdge(actor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/destinations") public ResponseEntity<List<CampusDestinationResponse>> destinations(@RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long nodeId, @RequestParam(required = false) Long buildingId, @RequestParam(required = false) Long placeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.destinations(siteId, nodeId, buildingId, placeId));
    }
    @PostMapping("/destinations") public ResponseEntity<CampusDestinationResponse> createDestination(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CampusDestinationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(navigation.createDestination(actor, body));
    }
    @PutMapping("/destinations/{id}") public ResponseEntity<CampusDestinationResponse> updateDestination(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id, @Valid @RequestBody CampusDestinationRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(navigation.updateDestination(actor, id, body));
    }
    @DeleteMapping("/destinations/{id}") public ResponseEntity<Void> deleteDestination(@PathVariable long id) {
        navigation.deleteDestination(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/validation") public ResponseEntity<CampusNavigationValidationResponse> validation(@RequestParam(required = false) Long siteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(validation.validate(siteId));
    }
}
