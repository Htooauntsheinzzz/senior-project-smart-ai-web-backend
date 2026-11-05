package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.StudentCampusService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Published campus data for students, read only. Deleted, inactive and hidden records never leave this service. */
@Service
public class StudentCampusServiceImpl implements StudentCampusService {
    private static final int DEFAULT_LIMIT = 20, MAX_LIMIT = 100;
    private static final List<CampusMobileGroup> CHIP_ORDER = List.of(CampusMobileGroup.BUILDINGS, CampusMobileGroup.FOOD, CampusMobileGroup.LIBRARY,
            CampusMobileGroup.STUDENT_SERVICES, CampusMobileGroup.HEALTH, CampusMobileGroup.PARKING);

    private final CampusSiteRepository sites;
    private final CampusSiteResolver siteResolver;
    private final CampusCategoryRepository categories;
    private final CampusBuildingRepository buildings;
    private final CampusRoomRepository rooms;
    private final CampusPlaceRepository places;
    private final CampusFacilityRepository facilities;
    private final CampusLocationSearchRepository locations;
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusNavEdgePointRepository points;
    private final CampusDestinationResolver destinations;
    private final CampusOpeningHoursSupport opening;
    private final Clock clock;

    public StudentCampusServiceImpl(CampusSiteRepository sites, CampusSiteResolver siteResolver, CampusCategoryRepository categories,
            CampusBuildingRepository buildings, CampusRoomRepository rooms, CampusPlaceRepository places, CampusFacilityRepository facilities,
            CampusLocationSearchRepository locations, CampusNavNodeRepository nodes, CampusNavEdgeRepository edges, CampusNavEdgePointRepository points,
            CampusDestinationResolver destinations, CampusOpeningHoursSupport opening, Clock clock) {
        this.sites = sites;
        this.siteResolver = siteResolver;
        this.categories = categories;
        this.buildings = buildings;
        this.rooms = rooms;
        this.places = places;
        this.facilities = facilities;
        this.locations = locations;
        this.nodes = nodes;
        this.edges = edges;
        this.points = points;
        this.destinations = destinations;
        this.opening = opening;
        this.clock = clock;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentCampusSiteResponse> sites() {
        return sites.findByDeletedFalseAndActiveTrueOrderBySiteNameAscIdAsc().stream()
                .map(s -> new StudentCampusSiteResponse(s.getId(), s.getSiteCode(), s.getSiteName(), s.getDescription(), s.getTimezone(),
                        s.getCenterLatitude(), s.getCenterLongitude())).toList();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<StudentCampusCategoryResponse> categories() {
        var active = categories.activeGroups();
        boolean hasBuildings = buildings.existsByDeletedFalseAndStatusNotAndSiteDeletedFalseAndSiteActiveTrue(CampusBuildingStatus.INACTIVE);
        var chips = new ArrayList<StudentCampusCategoryResponse>();
        chips.add(new StudentCampusCategoryResponse("ALL", "All"));
        for (CampusMobileGroup group : CHIP_ORDER)
            if (group == CampusMobileGroup.BUILDINGS ? hasBuildings : active.contains(group))
                chips.add(new StudentCampusCategoryResponse(group.name(), group.displayName()));
        return chips;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusLocationsResponse locations(Long siteId, String search, String group, Integer limit, String cursor) {
        if (siteId != null) siteResolver.active(siteId);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) throw CampusFailure.invalid();
        String term = search == null || search.isBlank() ? null : search.trim();
        if (term != null && term.length() > 200) throw CampusFailure.invalid();
        var filter = new CampusLocationSearchRepository.Filter(siteId, term, parseGroup(group));
        var rows = locations.search(filter, decode(cursor), size + 1);
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        var resolved = destinations.resolve(page.stream().map(StudentCampusServiceImpl::location).toList());
        var items = page.stream().map(row -> {
            var at = resolved.getOrDefault(new CampusDestinationResolver.Key(row.type(), row.id()), CampusDestinationResolver.Resolved.NONE);
            return new StudentCampusLocationsResponse.Item(row.id(), row.type(), row.siteId(), row.code(), row.name(), row.category(), row.group(),
                    row.description(), at.latitude(), at.longitude(), row.status(), row.popular(), at.canNavigate());
        }).toList();
        return new StudentCampusLocationsResponse(locations.count(filter), items, more ? encode(page.get(page.size() - 1)) : null);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusPlaceResponse place(long id) {
        CampusPlace p = eligiblePlace(id);
        CampusBuilding b = p.getBuilding();
        var hours = opening.placeHours(id);
        var status = CampusOpeningCalculator.status(p.getStatus() == CampusPlaceStatus.ACTIVE, p.getStatus().name(), hours,
                today(opening.placeExceptions(id), p.getSite()), now(p.getSite()));
        var at = destinations.resolveOne(new CampusDestinationResolver.Location(CampusLocationType.PLACE, id, p.getSite().getId(),
                p.getStatus() == CampusPlaceStatus.ACTIVE, b == null ? null : b.getId(), b != null && b.getStatus() == CampusBuildingStatus.ACTIVE));
        boolean canViewRooms = b != null && !b.isDeleted() && b.getStatus() != CampusBuildingStatus.INACTIVE
                && rooms.existsByBuildingIdAndDeletedFalseAndStatusNot(b.getId(), CampusRoomStatus.INACTIVE);
        return new StudentCampusPlaceResponse(id, p.getSite().getId(), p.getPlaceCode(), p.getPlaceName(), p.getPlaceNameTh(), p.getCategory().getCategoryName(),
                p.getCategory().getMobileGroup(), p.getDescription(), b == null || b.isDeleted() ? null : new CampusNamedRef(b.getId(), b.getBuildingCode(), b.getBuildingName()),
                p.getFloorLabel(), p.getPhoneNumber(), p.getStatus(), p.isPopular(), opening.dtos(hours), status,
                facilities.forPlaces(List.of(id)).stream().map(CampusFacilityRepository.OwnedFacility::getName).toList(),
                at.latitude(), at.longitude(), at.canNavigate(), canViewRooms);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusBuildingResponse building(long id) {
        CampusBuilding b = eligibleBuilding(id);
        var hours = opening.buildingHours(id);
        var status = CampusOpeningCalculator.status(b.getStatus() == CampusBuildingStatus.ACTIVE, b.getStatus().name(), hours,
                today(opening.buildingExceptions(id), b.getSite()), now(b.getSite()));
        boolean active = b.getStatus() == CampusBuildingStatus.ACTIVE;
        var at = destinations.resolveOne(new CampusDestinationResolver.Location(CampusLocationType.BUILDING, id, b.getSite().getId(), active, id, active));
        var roomList = roomResponses(b);
        return new StudentCampusBuildingResponse(id, b.getSite().getId(), b.getBuildingCode(), b.getBuildingName(), b.getBuildingNameTh(), b.getDescription(),
                b.getTotalFloors(), opening.dtos(hours), status, b.getStatus(), at.latitude(), at.longitude(), at.canNavigate(), roomList.size(), roomList);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusRoomsResponse buildingRooms(long id) {
        var roomList = roomResponses(eligibleBuilding(id));
        return new StudentCampusRoomsResponse(roomList.size(), roomList);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusRoomResponse room(long id) {
        CampusRoom room = rooms.findByIdAndDeletedFalse(id).filter(r -> r.getStatus() != CampusRoomStatus.INACTIVE).orElseThrow(CampusFailure::locationNotFound);
        eligibleBuilding(room.getBuilding().getId());
        return roomResponse(room, facilities.forRooms(List.of(id)).stream().map(CampusFacilityRepository.OwnedFacility::getName).toList());
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public StudentCampusMapResponse map(Long siteId, boolean includeNavigationGraph) {
        CampusSite site = siteResolver.active(siteId);
        var rows = locations.search(new CampusLocationSearchRepository.Filter(site.getId(), null, null), null, null);
        var resolved = destinations.resolve(rows.stream().map(StudentCampusServiceImpl::location).toList());
        var buildingMarkers = new ArrayList<StudentCampusMapResponse.Marker>();
        var placeMarkers = new ArrayList<StudentCampusMapResponse.Marker>();
        for (var row : rows) {
            var at = resolved.getOrDefault(new CampusDestinationResolver.Key(row.type(), row.id()), CampusDestinationResolver.Resolved.NONE);
            // A marker needs verified coordinates; without a mapped entrance the location simply has no marker.
            if (at.latitude() == null) continue;
            (row.type() == CampusLocationType.BUILDING ? buildingMarkers : placeMarkers).add(new StudentCampusMapResponse.Marker(row.id(), row.type(), row.code(), row.name(),
                    row.category(), row.group(), row.status(), row.popular(), at.latitude(), at.longitude(), at.canNavigate()));
        }
        var nodeRows = nodes.findBySiteIdAndDeletedFalseAndActiveTrueOrderByIdAsc(site.getId());
        StudentCampusMapResponse.Bounds bounds = null;
        if (!nodeRows.isEmpty()) {
            var latitudes = nodeRows.stream().map(CampusNavNode::getLatitude).toList();
            var longitudes = nodeRows.stream().map(CampusNavNode::getLongitude).toList();
            bounds = new StudentCampusMapResponse.Bounds(Collections.min(latitudes), Collections.min(longitudes), Collections.max(latitudes), Collections.max(longitudes));
        }
        return new StudentCampusMapResponse(new StudentCampusMapResponse.Site(site.getId(), site.getSiteName(), site.getCenterLatitude(), site.getCenterLongitude()),
                bounds, buildingMarkers, placeMarkers, includeNavigationGraph ? graph(site.getId(), nodeRows) : null);
    }

    private StudentCampusMapResponse.Graph graph(long siteId, List<CampusNavNode> nodeRows) {
        var ids = new HashSet<Long>();
        var graphNodes = nodeRows.stream().map(n -> {
            ids.add(n.getId());
            return new StudentCampusMapResponse.Node(n.getId(), n.getNodeName(), n.getNodeType(), n.getLatitude(), n.getLongitude(), n.isAccessible());
        }).toList();
        var rows = edges.findBySiteIdAndDeletedFalseOrderByIdAsc(siteId).stream().filter(e -> e.getStatus() == CampusEdgeStatus.ACTIVE
                && ids.contains(e.getStartNode().getId()) && ids.contains(e.getEndNode().getId())).toList();
        var geometry = new HashMap<Long, List<CampusCoordinate>>();
        if (!rows.isEmpty()) for (var point : points.findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(rows.stream().map(CampusNavEdge::getId).toList()))
            geometry.computeIfAbsent(point.getEdgeId(), k -> new ArrayList<>()).add(new CampusCoordinate(point.getLatitude(), point.getLongitude()));
        var graphEdges = rows.stream().map(e -> new StudentCampusMapResponse.Edge(e.getId(), e.getStartNode().getId(), e.getEndNode().getId(), e.getDistanceMeters(),
                e.isWheelchairAccessible(), e.isBidirectional(), geometry.getOrDefault(e.getId(), List.of()))).toList();
        return new StudentCampusMapResponse.Graph(graphNodes, graphEdges);
    }

    private CampusPlace eligiblePlace(long id) {
        CampusPlace place = places.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::locationNotFound);
        if (!place.isVisibleOnMobile() || place.getStatus() == CampusPlaceStatus.INACTIVE || place.getCategory().isDeleted()
                || place.getSite().isDeleted() || !place.getSite().isActive()) throw CampusFailure.locationNotFound();
        return place;
    }
    private CampusBuilding eligibleBuilding(long id) {
        CampusBuilding building = buildings.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::locationNotFound);
        if (building.getStatus() == CampusBuildingStatus.INACTIVE || building.getSite().isDeleted() || !building.getSite().isActive())
            throw CampusFailure.locationNotFound();
        return building;
    }
    private List<StudentCampusRoomResponse> roomResponses(CampusBuilding building) {
        var rows = rooms.findByBuildingIdAndDeletedFalseAndStatusNotOrderByFloorNumberAscRoomNumberAscIdAsc(building.getId(), CampusRoomStatus.INACTIVE);
        var names = new HashMap<Long, List<String>>();
        if (!rows.isEmpty()) for (var row : facilities.forRooms(rows.stream().map(CampusRoom::getId).toList()))
            names.computeIfAbsent(row.getOwnerId(), k -> new ArrayList<>()).add(row.getName());
        return rows.stream().map(r -> roomResponse(r, names.getOrDefault(r.getId(), List.of()))).toList();
    }
    private StudentCampusRoomResponse roomResponse(CampusRoom r, List<String> facilityNames) {
        CampusBuilding b = r.getBuilding();
        return new StudentCampusRoomResponse(r.getId(), r.getRoomNumber(), r.getRoomName(), new CampusNamedRef(b.getId(), b.getBuildingCode(), b.getBuildingName()),
                r.getFloorNumber(), r.getRoomType(), r.getCapacity(), r.getStatus(), facilityNames);
    }
    private ZonedDateTime now(CampusSite site) { return clock.instant().atZone(ZoneId.of(site.getTimezone())); }
    private CampusOpeningException today(List<CampusOpeningException> exceptions, CampusSite site) {
        LocalDate date = now(site).toLocalDate();
        return exceptions.stream().filter(e -> e.getExceptionDate().equals(date)).findFirst().orElse(null);
    }
    static CampusDestinationResolver.Location location(CampusLocationSearchRepository.Row row) {
        return new CampusDestinationResolver.Location(row.type(), row.id(), row.siteId(), "ACTIVE".equals(row.status()), row.buildingId(), "ACTIVE".equals(row.buildingStatus()));
    }
    /** null and ALL both mean no group filter. */
    static CampusMobileGroup parseGroup(String group) {
        if (group == null || group.equals("ALL")) return null;
        try {
            return CampusMobileGroup.valueOf(group);
        } catch (IllegalArgumentException e) {
            throw CampusFailure.invalid();
        }
    }
    private static String encode(CampusLocationSearchRepository.Row row) {
        String value = row.name().toLowerCase(Locale.ROOT) + "\u0000" + row.type().name() + "\u0000" + row.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static CampusLocationSearchRepository.Cursor decode(String cursor) {
        if (cursor == null) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\u0000", -1);
            if (parts.length != 3) throw CampusFailure.invalid();
            return new CampusLocationSearchRepository.Cursor(parts[0], CampusLocationType.valueOf(parts[1]).name(), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException e) {
            throw CampusFailure.invalid();
        }
    }
}
