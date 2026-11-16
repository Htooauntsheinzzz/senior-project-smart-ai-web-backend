package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.CampusNavigationValidationResponse.Issue;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusNavigationValidationService;

/** Read-only report of map data that would make routes wrong or unavailable, for the Admin to fix before release. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusNavigationValidationServiceImpl implements CampusNavigationValidationService {
    private final CampusSiteResolver siteResolver;
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusNavEdgePointRepository points;
    private final CampusNavDestinationRepository destinations;
    private final CampusBuildingRepository buildings;
    private final CampusPlaceRepository places;

    public CampusNavigationValidationServiceImpl(CampusSiteResolver siteResolver, CampusNavNodeRepository nodes, CampusNavEdgeRepository edges,
            CampusNavEdgePointRepository points, CampusNavDestinationRepository destinations, CampusBuildingRepository buildings, CampusPlaceRepository places) {
        this.siteResolver = siteResolver;
        this.nodes = nodes;
        this.edges = edges;
        this.points = points;
        this.destinations = destinations;
        this.buildings = buildings;
        this.places = places;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusNavigationValidationResponse validate(Long requestedSiteId) {
        long siteId = siteResolver.live(requestedSiteId).getId();
        var allNodes = nodes.findBySiteIdAndDeletedFalseOrderByNodeNameAscIdAsc(siteId);
        var activeNodes = allNodes.stream().filter(CampusNavNode::isActive).toList();
        var activeIds = new HashSet<Long>();
        activeNodes.forEach(n -> activeIds.add(n.getId()));
        var allEdges = edges.findBySiteIdAndDeletedFalseOrderByIdAsc(siteId);
        var mappings = destinations.search(siteId, null, null, null);

        var crossCampus = new ArrayList<Issue>();
        var inactive = new ArrayList<Issue>();
        var usable = new ArrayList<CampusNavEdge>();
        for (CampusNavEdge edge : allEdges) {
            boolean crosses = !edge.getStartNode().getSite().getId().equals(siteId) || !edge.getEndNode().getSite().getId().equals(siteId);
            if (crosses) crossCampus.add(issue("EDGE", edge.getId(), edge.getEdgeCode(), "The edge connects a node from another campus."));
            if (edge.getStatus() != CampusEdgeStatus.ACTIVE) inactive.add(issue("EDGE", edge.getId(), edge.getEdgeCode(), "Status is " + edge.getStatus() + "."));
            else if (!crosses && activeIds.contains(edge.getStartNode().getId()) && activeIds.contains(edge.getEndNode().getId())) usable.add(edge);
        }

        var neighbours = new HashMap<Long, Set<Long>>();
        var inbound = new HashSet<Long>();
        for (CampusNavEdge edge : usable) {
            long start = edge.getStartNode().getId(), end = edge.getEndNode().getId();
            neighbours.computeIfAbsent(start, k -> new HashSet<>()).add(end);
            neighbours.computeIfAbsent(end, k -> new HashSet<>()).add(start);
            inbound.add(end);
            if (edge.isBidirectional()) inbound.add(start);
        }
        var disconnected = new ArrayList<Issue>();
        var invalidCoordinates = new ArrayList<Issue>();
        for (CampusNavNode node : activeNodes)
            if (!neighbours.containsKey(node.getId())) disconnected.add(issue("NODE", node.getId(), node.getNodeCode(), "The node has no usable active edge."));
        for (CampusNavNode node : allNodes) {
            if (!CampusWriteSupport.inRange(node.getLatitude(), node.getLongitude()) || isZero(node.getLatitude()) && isZero(node.getLongitude()))
                invalidCoordinates.add(issue("NODE", node.getId(), node.getNodeCode(), "The coordinates are out of range or look like a placeholder (0, 0)."));
        }

        Set<Long> mainComponent = largestComponent(activeIds, neighbours);
        var unreachable = new ArrayList<Issue>();
        for (CampusNavDestination mapping : mappings) {
            CampusNavNode node = mapping.getNode();
            String code = mapping.getBuilding() != null ? mapping.getBuilding().getBuildingCode() : mapping.getPlace().getPlaceCode();
            String type = mapping.getBuilding() != null ? "BUILDING" : "PLACE";
            long owner = mapping.getBuilding() != null ? mapping.getBuilding().getId() : mapping.getPlace().getId();
            long ownerSite = mapping.getBuilding() != null ? mapping.getBuilding().getSite().getId() : mapping.getPlace().getSite().getId();
            if (ownerSite != siteId) crossCampus.add(issue(type, owner, code, "The destination maps to a node in another campus."));
            if (node.isDeleted() || !node.isActive()) unreachable.add(issue(type, owner, code, "The entrance node " + node.getNodeCode() + " is disabled."));
            else if (!mainComponent.contains(node.getId()) || !inbound.contains(node.getId()))
                unreachable.add(issue(type, owner, code, "The entrance node " + node.getNodeCode() + " cannot be reached from the main walking network."));
        }

        var primaryBuildings = new HashSet<Long>();
        var primaryPlaces = new HashSet<Long>();
        var accessibleOwners = new HashSet<String>();
        for (CampusNavDestination mapping : mappings) {
            if (mapping.isPrimary()) {
                if (mapping.getBuilding() != null) primaryBuildings.add(mapping.getBuilding().getId());
                else primaryPlaces.add(mapping.getPlace().getId());
            }
            if (mapping.isAccessibleEntrance() && mapping.getNode().isAccessible())
                accessibleOwners.add(mapping.getBuilding() != null ? "B" + mapping.getBuilding().getId() : "P" + mapping.getPlace().getId());
        }
        var missingPrimary = new ArrayList<Issue>();
        var accessibility = new ArrayList<Issue>();
        for (var building : buildings.findBySiteIdAndDeletedFalseOrderByBuildingNameAscIdAsc(siteId)) {
            if (building.getStatus() != CampusBuildingStatus.ACTIVE) continue;
            if (!primaryBuildings.contains(building.getId()))
                missingPrimary.add(issue("BUILDING", building.getId(), building.getBuildingCode(), "No primary entrance is mapped."));
            else if (!accessibleOwners.contains("B" + building.getId()))
                accessibility.add(issue("BUILDING", building.getId(), building.getBuildingCode(), "No accessible entrance is mapped."));
        }
        for (var place : places.findBySiteIdAndDeletedFalseOrderByPlaceNameAscIdAsc(siteId)) {
            if (place.getStatus() != CampusPlaceStatus.ACTIVE || !place.isVisibleOnMobile()) continue;
            boolean viaBuilding = place.getBuilding() != null && primaryBuildings.contains(place.getBuilding().getId());
            if (!primaryPlaces.contains(place.getId()) && !viaBuilding)
                missingPrimary.add(issue("PLACE", place.getId(), place.getPlaceCode(), "No primary entrance is mapped, directly or through its building."));
            else if (!accessibleOwners.contains("P" + place.getId()) && !(place.getBuilding() != null && accessibleOwners.contains("B" + place.getBuilding().getId())))
                accessibility.add(issue("PLACE", place.getId(), place.getPlaceCode(), "No accessible entrance is mapped."));
        }

        var withGeometry = new HashSet<Long>();
        if (!usable.isEmpty()) for (var point : points.findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(usable.stream().map(CampusNavEdge::getId).toList())) {
            withGeometry.add(point.getEdgeId());
            if (!CampusWriteSupport.inRange(point.getLatitude(), point.getLongitude()))
                invalidCoordinates.add(issue("EDGE_POINT", point.getId(), String.valueOf(point.getPointSequence()), "A geometry point is out of range."));
        }
        var missingGeometry = new ArrayList<Issue>();
        for (CampusNavEdge edge : usable) {
            if (!withGeometry.contains(edge.getId()))
                missingGeometry.add(issue("EDGE", edge.getId(), edge.getEdgeCode(), "No intermediate points: turn guidance is not offered on this edge."));
            if (edge.isWheelchairAccessible() && edge.getSurfaceType() == CampusSurfaceType.STAIRS)
                accessibility.add(issue("EDGE", edge.getId(), edge.getEdgeCode(), "Stairs are marked wheelchair accessible."));
            if (edge.isWheelchairAccessible() && (!edge.getStartNode().isAccessible() || !edge.getEndNode().isAccessible()))
                accessibility.add(issue("EDGE", edge.getId(), edge.getEdgeCode(), "A wheelchair accessible edge touches an inaccessible node."));
        }

        int blocking = disconnected.size() + unreachable.size() + invalidCoordinates.size() + missingPrimary.size() + crossCampus.size();
        int warnings = inactive.size() + missingGeometry.size() + accessibility.size();
        return new CampusNavigationValidationResponse(siteId, blocking == 0,
                new CampusNavigationValidationResponse.Summary(activeNodes.size(), usable.size(), mappings.size(), blocking, warnings),
                disconnected, unreachable, invalidCoordinates, inactive, missingPrimary, missingGeometry, accessibility, crossCampus);
    }

    private static Set<Long> largestComponent(Set<Long> ids, Map<Long, Set<Long>> neighbours) {
        var seen = new HashSet<Long>();
        Set<Long> largest = Set.of();
        for (Long id : new TreeSet<>(ids)) {
            if (seen.contains(id)) continue;
            var component = new HashSet<Long>();
            var pending = new ArrayDeque<Long>(List.of(id));
            while (!pending.isEmpty()) {
                Long current = pending.poll();
                if (!seen.add(current)) continue;
                component.add(current);
                pending.addAll(neighbours.getOrDefault(current, Set.of()));
            }
            if (component.size() > largest.size()) largest = component;
        }
        return largest;
    }
    private static boolean isZero(BigDecimal value) { return value.signum() == 0; }
    private static Issue issue(String type, Long id, String code, String detail) { return new Issue(type, id, code, detail); }
}
