package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusMapService;
import com.smartAiUniversityAssistant.seniorproject.config.CampusProperties;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Map layers for the Admin. Unmapped buildings and places are returned with null coordinates so the Admin can see what is missing. */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusMapServiceImpl implements CampusMapService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "UNDER_RENOVATION", "INACTIVE", "TEMPORARILY_CLOSED", "UNDER_CONSTRUCTION");
    private final CampusSiteResolver sites;
    private final CampusBuildingRepository buildings;
    private final CampusPlaceRepository places;
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusNavEdgePointRepository points;
    private final CampusDestinationResolver destinations;
    private final CampusProperties properties;

    public CampusMapServiceImpl(CampusSiteResolver sites, CampusBuildingRepository buildings, CampusPlaceRepository places, CampusNavNodeRepository nodes,
            CampusNavEdgeRepository edges, CampusNavEdgePointRepository points, CampusDestinationResolver destinations, CampusProperties properties) {
        this.sites = sites;
        this.buildings = buildings;
        this.places = places;
        this.nodes = nodes;
        this.edges = edges;
        this.points = points;
        this.destinations = destinations;
        this.properties = properties;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusAdminMapResponse map(Long siteId, boolean includeBuildings, boolean includePlaces, boolean includeNodes, boolean includeEdges,
            Long categoryId, String status) {
        if (status != null && !STATUSES.contains(status)) throw CampusFailure.invalid();
        CampusSite site = sites.live(siteId);
        long id = site.getId();
        var allBuildings = buildings.findBySiteIdAndDeletedFalseOrderByBuildingNameAscIdAsc(id);
        var allPlaces = places.findBySiteIdAndDeletedFalseOrderByPlaceNameAscIdAsc(id);
        var locations = new ArrayList<CampusDestinationResolver.Location>();
        for (var b : allBuildings)
            locations.add(new CampusDestinationResolver.Location(CampusLocationType.BUILDING, b.getId(), id,
                    b.getStatus() == CampusBuildingStatus.ACTIVE, b.getId(), b.getStatus() == CampusBuildingStatus.ACTIVE));
        for (var p : allPlaces)
            locations.add(new CampusDestinationResolver.Location(CampusLocationType.PLACE, p.getId(), id, p.getStatus() == CampusPlaceStatus.ACTIVE,
                    p.getBuilding() == null ? null : p.getBuilding().getId(),
                    p.getBuilding() != null && p.getBuilding().getStatus() == CampusBuildingStatus.ACTIVE));
        var resolved = destinations.resolve(locations);

        var buildingMarkers = !includeBuildings ? List.<CampusAdminMapResponse.Marker>of() : allBuildings.stream()
                .filter(b -> status == null || b.getStatus().name().equals(status))
                .map(b -> marker(b.getId(), CampusLocationType.BUILDING, b.getBuildingCode(), b.getBuildingName(), "Building", b.getStatus().name(), resolved)).toList();
        var placeMarkers = !includePlaces ? List.<CampusAdminMapResponse.Marker>of() : allPlaces.stream()
                .filter(p -> status == null || p.getStatus().name().equals(status))
                .filter(p -> categoryId == null || p.getCategory().getId().equals(categoryId))
                .map(p -> marker(p.getId(), CampusLocationType.PLACE, p.getPlaceCode(), p.getPlaceName(), p.getCategory().getCategoryName(), p.getStatus().name(), resolved)).toList();
        var nodeRows = !includeNodes ? List.<CampusAdminMapResponse.NavigationNode>of() : nodes.findBySiteIdAndDeletedFalseOrderByNodeNameAscIdAsc(id).stream()
                .map(n -> new CampusAdminMapResponse.NavigationNode(n.getId(), "NAV_NODE", n.getNodeCode(), n.getNodeName(), n.getNodeType(), n.getLatitude(),
                        n.getLongitude(), n.isAccessible(), n.isActive())).toList();
        var edgeRows = !includeEdges ? List.<CampusAdminMapResponse.NavigationEdge>of() : edges(id, status);

        long total = allBuildings.size() + allPlaces.size();
        long active = allBuildings.stream().filter(b -> b.getStatus() == CampusBuildingStatus.ACTIVE).count()
                + allPlaces.stream().filter(p -> p.getStatus() == CampusPlaceStatus.ACTIVE).count();
        long unmapped = resolved.values().stream().filter(r -> r.latitude() == null).count();
        return new CampusAdminMapResponse(new CampusAdminMapResponse.Site(id, site.getSiteName(), site.getCenterLatitude(), site.getCenterLongitude()),
                buildingMarkers, placeMarkers, nodeRows, edgeRows, new CampusAdminMapResponse.Summary(total, active, total - active, unmapped));
    }

    private CampusAdminMapResponse.Marker marker(long id, CampusLocationType type, String code, String name, String category, String status,
            Map<CampusDestinationResolver.Key, CampusDestinationResolver.Resolved> resolved) {
        var location = resolved.getOrDefault(new CampusDestinationResolver.Key(type, id), CampusDestinationResolver.Resolved.NONE);
        return new CampusAdminMapResponse.Marker(id, type, code, name, category, status, location.latitude(), location.longitude(), location.canNavigate());
    }

    private List<CampusAdminMapResponse.NavigationEdge> edges(long siteId, String status) {
        var rows = edges.findBySiteIdAndDeletedFalseOrderByIdAsc(siteId).stream().filter(e -> status == null || e.getStatus().name().equals(status)).toList();
        var geometry = new HashMap<Long, List<CampusCoordinate>>();
        if (!rows.isEmpty()) for (var point : points.findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(rows.stream().map(CampusNavEdge::getId).toList()))
            geometry.computeIfAbsent(point.getEdgeId(), k -> new ArrayList<>()).add(new CampusCoordinate(point.getLatitude(), point.getLongitude()));
        return rows.stream().map(e -> {
            var line = new ArrayList<CampusCoordinate>();
            line.add(new CampusCoordinate(e.getStartNode().getLatitude(), e.getStartNode().getLongitude()));
            line.addAll(geometry.getOrDefault(e.getId(), List.of()));
            line.add(new CampusCoordinate(e.getEndNode().getLatitude(), e.getEndNode().getLongitude()));
            return new CampusAdminMapResponse.NavigationEdge(e.getId(), "NAV_EDGE", e.getEdgeCode(), e.getStartNode().getId(), e.getEndNode().getId(), e.getDistanceMeters(),
                    CampusGeo.walkingMinutes(e.getDistanceMeters(), properties.walkingSpeedMetersPerSecond()), e.getSurfaceType(), e.isWheelchairAccessible(),
                    e.isBidirectional(), e.getStatus(), List.copyOf(line));
        }).toList();
    }
}
