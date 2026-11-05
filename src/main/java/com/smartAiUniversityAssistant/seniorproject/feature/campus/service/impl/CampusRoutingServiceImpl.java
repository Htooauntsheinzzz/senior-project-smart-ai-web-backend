package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusRoutingService;
import com.smartAiUniversityAssistant.seniorproject.config.CampusProperties;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nearby locations and walking routes. The student's coordinates exist only for the length of the request: they are never
 * stored, logged, cached or sent to any other service, and the transaction is read only.
 */
@Service
public class CampusRoutingServiceImpl implements CampusRoutingService {
    private final CampusSiteResolver sites;
    private final CampusBuildingRepository buildings;
    private final CampusPlaceRepository places;
    private final CampusRoomRepository rooms;
    private final CampusLocationSearchRepository locations;
    private final CampusDestinationResolver destinations;
    private final CampusGraphLoader graphs;
    private final CampusProperties properties;
    private final Clock clock;

    public CampusRoutingServiceImpl(CampusSiteResolver sites, CampusBuildingRepository buildings, CampusPlaceRepository places, CampusRoomRepository rooms,
            CampusLocationSearchRepository locations, CampusDestinationResolver destinations, CampusGraphLoader graphs, CampusProperties properties, Clock clock) {
        this.sites = sites;
        this.buildings = buildings;
        this.places = places;
        this.rooms = rooms;
        this.locations = locations;
        this.destinations = destinations;
        this.graphs = graphs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusNearbyResponse nearby(CampusNearbyRequest request) {
        origin(request.latitude(), request.longitude());
        int limit = request.limit() == null ? properties.nearbyDefaultLimit() : request.limit();
        if (limit > properties.nearbyMaxLimit()) throw CampusFailure.invalid();
        boolean accessibleOnly = Boolean.TRUE.equals(request.accessibleOnly());
        double latitude = request.latitude().doubleValue(), longitude = request.longitude().doubleValue();
        long siteId = sites.active(request.siteId()).getId();
        var rows = locations.search(new CampusLocationSearchRepository.Filter(siteId, null, StudentCampusServiceImpl.parseGroup(request.group())), null, null);
        var resolved = destinations.resolve(rows.stream().map(StudentCampusServiceImpl::location).toList());
        var graph = graphs.load(siteId);
        var snap = graph.nearest(latitude, longitude, accessibleOnly).filter(s -> s.distanceMeters() <= properties.maxOriginSnapDistanceMeters());
        var search = snap.map(s -> graph.search(s.node().id(), accessibleOnly, false));

        record Ranked(CampusNearbyResponse.Item item, BigDecimal walking, BigDecimal straight) {}
        var ranked = new ArrayList<Ranked>();
        for (var row : rows) {
            var at = resolved.getOrDefault(new CampusDestinationResolver.Key(row.type(), row.id()), CampusDestinationResolver.Resolved.NONE);
            // Without a mapped entrance there is no verified position, so proximity cannot be stated at all.
            if (at.latitude() == null) continue;
            BigDecimal straight = CampusGeo.meters(CampusGeo.haversineMeters(latitude, longitude, at.latitude().doubleValue(), at.longitude().doubleValue()));
            BigDecimal walking = null;
            if (at.canNavigate() && search.isPresent()) {
                var best = at.entrances().stream().filter(e -> !accessibleOnly || e.accessible())
                        .map(e -> search.get().distanceTo(e.nodeId())).flatMap(Optional::stream).min(Comparator.naturalOrder());
                if (best.isPresent()) walking = best.get().add(CampusGeo.meters(snap.get().distanceMeters()));
            }
            ranked.add(new Ranked(new CampusNearbyResponse.Item(row.id(), row.type(), row.code(), row.name(), row.category(), row.group(), row.status(), walking,
                    walking == null ? null : CampusGeo.walkingMinutes(walking, properties.walkingSpeedMetersPerSecond()), straight, walking != null), walking, straight));
        }
        // Calculated routes first, shortest first; the rest follow by labeled straight-line proximity.
        ranked.sort(Comparator.comparing(Ranked::walking, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Ranked::straight)
                .thenComparing(r -> r.item().name()).thenComparing(r -> r.item().locationId()));
        var items = ranked.stream().limit(limit).map(Ranked::item).toList();
        return new CampusNearbyResponse(items.size(), items);
    }

    private record Target(CampusLocationType type, long id, String name, long siteId, CampusDestinationResolver.Location location,
            CampusRouteResponse.Building building, Integer floor, String note) {}

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusRouteResponse preview(CampusRoutePreviewRequest request) {
        if (request.origin() == null) throw CampusFailure.invalidCoordinates();
        origin(request.origin().latitude(), request.origin().longitude());
        if (request.siteId() != null) sites.active(request.siteId());
        Target target = target(request.destination());
        if (request.siteId() != null && request.siteId() != target.siteId()) throw CampusFailure.locationNotFound();
        boolean accessibleOnly = Boolean.TRUE.equals(request.accessibleOnly());
        double latitude = request.origin().latitude().doubleValue(), longitude = request.origin().longitude().doubleValue();

        var resolved = destinations.resolveOne(target.location());
        if (resolved.entrances().isEmpty()) throw CampusFailure.destinationNotMapped();
        if (!resolved.canNavigate()) throw CampusFailure.noRoute();
        var entrances = resolved.entrances().stream().filter(e -> !accessibleOnly || e.accessible()).toList();
        if (entrances.isEmpty()) throw CampusFailure.noAccessibleRoute();

        var graph = graphs.load(target.siteId());
        var snap = graph.nearest(latitude, longitude, accessibleOnly).filter(s -> s.distanceMeters() <= properties.maxOriginSnapDistanceMeters());
        if (snap.isEmpty()) {
            boolean nearInaccessibleNode = accessibleOnly && graph.nearest(latitude, longitude, false)
                    .filter(s -> s.distanceMeters() <= properties.maxOriginSnapDistanceMeters()).isPresent();
            throw nearInaccessibleNode ? CampusFailure.noAccessibleRoute() : CampusFailure.originOutside();
        }
        long source = snap.get().node().id();
        var search = graph.search(source, accessibleOnly, false);
        // The closest usable entrance wins; a primary entrance wins a tie.
        var entrance = entrances.stream().filter(e -> search.distanceTo(e.nodeId()).isPresent())
                .min(Comparator.<CampusDestinationResolver.Entrance, BigDecimal>comparing(e -> search.distanceTo(e.nodeId()).orElseThrow())
                        .thenComparing(e -> !e.primary()).thenComparingLong(CampusDestinationResolver.Entrance::nodeId));
        if (entrance.isEmpty()) throw unavailable(graph, source, entrances, resolved.entrances(), accessibleOnly);

        var path = search.pathTo(entrance.get().nodeId()).orElseThrow(CampusFailure::noRoute);
        BigDecimal approach = CampusGeo.meters(snap.get().distanceMeters());
        BigDecimal total = path.distanceMeters().add(approach).setScale(2);
        double seconds = total.doubleValue() / properties.walkingSpeedMetersPerSecond();
        Instant now = clock.instant();

        var geometry = new ArrayList<CampusCoordinate>();
        geometry.add(new CampusCoordinate(request.origin().latitude(), request.origin().longitude()));
        var startNode = snap.get().node();
        addPoint(geometry, new CampusRouteGraph.Point(startNode.latitude(), startNode.longitude()));
        var overview = new ArrayList<CampusRouteResponse.OverviewSegment>();
        for (var step : path.steps()) {
            step.line().forEach(point -> addPoint(geometry, point));
            overview.add(new CampusRouteResponse.OverviewSegment(step.edge().code(), step.from().name(), step.to().name(), step.edge().distanceMeters(),
                    step.edge().surface(), step.edge().wheelchairAccessible(), step.hasMeasuredGeometry()));
        }
        boolean measured = CampusRouteInstructions.available(path.steps());
        var instructions = new ArrayList<CampusRouteResponse.Instruction>();
        if (measured && approach.signum() > 0) {
            int bearing = CampusGeo.bearingDegrees(latitude, longitude, startNode.latitude(), startNode.longitude());
            instructions.add(new CampusRouteResponse.Instruction("Walk " + CampusGeo.compass(bearing) + " from your current location to " + startNode.name(),
                    approach, bearing, CampusGeo.compass(bearing), "APPROACH"));
        }
        instructions.addAll(CampusRouteInstructions.build(path.steps(), target.name()));
        return new CampusRouteResponse(UUID.randomUUID().toString(), "ROUTE_FOUND",
                new CampusRouteResponse.Destination(target.type(), target.id(), target.name(), target.building(), target.floor(), target.note()),
                accessibleOnly, total, path.distanceMeters(), approach, CampusGeo.walkingMinutes(total, properties.walkingSpeedMetersPerSecond()),
                now.plusSeconds(Math.round(Math.ceil(seconds))), new CampusNamedRef(startNode.id(), startNode.code(), startNode.name()),
                new CampusNamedRef(path.target().id(), path.target().code(), path.target().name()), measured ? "MEASURED" : "APPROXIMATE",
                geometry, overview, instructions, now);
    }

    /** Tells apart why no route exists, without ever inventing one. */
    private CampusFailure unavailable(CampusRouteGraph graph, long source, List<CampusDestinationResolver.Entrance> usable,
            List<CampusDestinationResolver.Entrance> all, boolean accessibleOnly) {
        var withConstruction = graph.search(source, accessibleOnly, true);
        if (usable.stream().anyMatch(e -> withConstruction.distanceTo(e.nodeId()).isPresent())) return CampusFailure.underConstruction();
        if (accessibleOnly) {
            var unrestricted = graph.search(source, false, true);
            if (all.stream().anyMatch(e -> unrestricted.distanceTo(e.nodeId()).isPresent())) return CampusFailure.noAccessibleRoute();
        }
        return CampusFailure.noRoute();
    }

    private Target target(CampusRoutePreviewRequest.Destination destination) {
        long id = destination.id();
        return switch (destination.type()) {
            case PLACE -> {
                CampusPlace p = places.findByIdAndDeletedFalse(id).filter(x -> x.isVisibleOnMobile() && x.getStatus() != CampusPlaceStatus.INACTIVE && activeSite(x.getSite()))
                        .orElseThrow(CampusFailure::locationNotFound);
                CampusBuilding b = p.getBuilding();
                yield new Target(CampusLocationType.PLACE, id, p.getPlaceName(), p.getSite().getId(),
                        new CampusDestinationResolver.Location(CampusLocationType.PLACE, id, p.getSite().getId(), p.getStatus() == CampusPlaceStatus.ACTIVE,
                                b == null ? null : b.getId(), b != null && b.getStatus() == CampusBuildingStatus.ACTIVE),
                        b == null ? null : new CampusRouteResponse.Building(b.getId(), b.getBuildingName()), null, null);
            }
            case BUILDING -> {
                CampusBuilding b = eligibleBuilding(id);
                yield new Target(CampusLocationType.BUILDING, id, b.getBuildingName(), b.getSite().getId(), buildingLocation(b), null, null, null);
            }
            case ROOM -> {
                CampusRoom r = rooms.findByIdAndDeletedFalse(id).filter(x -> x.getStatus() != CampusRoomStatus.INACTIVE).orElseThrow(CampusFailure::locationNotFound);
                CampusBuilding b = eligibleBuilding(r.getBuilding().getId());
                yield new Target(CampusLocationType.ROOM, id, r.getRoomName() == null ? r.getRoomNumber() : r.getRoomName(), b.getSite().getId(), buildingLocation(b),
                        new CampusRouteResponse.Building(b.getId(), b.getBuildingName()), (int) r.getFloorNumber(),
                        "The route ends at the building entrance. Indoor directions are not available.");
            }
        };
    }
    private CampusBuilding eligibleBuilding(long id) {
        return buildings.findByIdAndDeletedFalse(id).filter(b -> b.getStatus() != CampusBuildingStatus.INACTIVE && activeSite(b.getSite()))
                .orElseThrow(CampusFailure::locationNotFound);
    }
    private static CampusDestinationResolver.Location buildingLocation(CampusBuilding b) {
        boolean active = b.getStatus() == CampusBuildingStatus.ACTIVE;
        return new CampusDestinationResolver.Location(CampusLocationType.BUILDING, b.getId(), b.getSite().getId(), active, b.getId(), active);
    }
    private static boolean activeSite(CampusSite site) { return site.isActive() && !site.isDeleted(); }
    private static void addPoint(List<CampusCoordinate> geometry, CampusRouteGraph.Point point) {
        var next = new CampusCoordinate(BigDecimal.valueOf(point.latitude()), BigDecimal.valueOf(point.longitude()));
        var last = geometry.get(geometry.size() - 1);
        if (last.latitude().compareTo(next.latitude()) != 0 || last.longitude().compareTo(next.longitude()) != 0) geometry.add(next);
    }
    private static void origin(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null || !CampusWriteSupport.inRange(latitude, longitude)) throw CampusFailure.invalidCoordinates();
    }
}
