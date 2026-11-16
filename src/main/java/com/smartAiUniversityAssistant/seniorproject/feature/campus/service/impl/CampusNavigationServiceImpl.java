package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.mapper.CampusMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import com.smartAiUniversityAssistant.seniorproject.config.CampusProperties;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.service.CampusNavigationService;

/** Nodes, edges and destinations of the walking network. Writes are limited to SUPER_ADMIN (spec section 56). */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class CampusNavigationServiceImpl implements CampusNavigationService {
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusNavEdgePointRepository points;
    private final CampusNavDestinationRepository destinations;
    private final CampusSiteRepository sites;
    private final CampusBuildingRepository buildings;
    private final CampusPlaceRepository places;
    private final CampusMapper mapper;
    private final CampusWriteSupport support;
    private final CampusProperties properties;

    public CampusNavigationServiceImpl(CampusNavNodeRepository nodes, CampusNavEdgeRepository edges, CampusNavEdgePointRepository points,
            CampusNavDestinationRepository destinations, CampusSiteRepository sites, CampusBuildingRepository buildings,
            CampusPlaceRepository places, CampusMapper mapper, CampusWriteSupport support, CampusProperties properties) {
        this.nodes = nodes;
        this.edges = edges;
        this.points = points;
        this.destinations = destinations;
        this.sites = sites;
        this.buildings = buildings;
        this.places = places;
        this.mapper = mapper;
        this.support = support;
        this.properties = properties;
    }

    // Nodes

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusNodeResponse> nodes(CampusPageQuery query) {
        Long siteId = query.id("siteId");
        CampusNodeType type = query.enumValue("nodeType", CampusNodeType.class);
        Boolean active = query.bool("isActive");
        Specification<CampusNavNode> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (siteId != null) predicates.add(cb.equal(root.get("site").get("id"), siteId));
            if (type != null) predicates.add(cb.equal(root.get("nodeType"), type));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (query.search() != null) predicates.add(cb.or(CampusQuerySupport.contains(cb, root.get("nodeCode"), query.search()),
                    CampusQuerySupport.contains(cb, root.get("nodeName"), query.search())));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return query.response(nodes.findAll(specification, query.pageable()), mapper::node);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusNodeResponse node(long id) {
        return mapper.node(nodes.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::nodeNotFound));
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusNodeResponse createNode(AuthenticatedUser actor, CampusNodeCreateRequest request) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(request.siteId()).filter(CampusSite::isActive).orElseThrow(CampusFailure::invalidSite);
        var node = new CampusNavNode();
        node.setSite(site);
        applyNode(node, 0L, request.nodeCode(), request.nodeName(), request.nodeType(), request.latitude(), request.longitude());
        node.setAccessible(request.isAccessible() == null || request.isAccessible());
        node.setActive(request.isActive() == null || request.isActive());
        node.setCreatedBy(actor.userId());
        node.setCreatedAt(support.now());
        try {
            return mapper.node(nodes.saveAndFlush(node));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusNodeResponse updateNode(AuthenticatedUser actor, long id, CampusNodeUpdateRequest request) {
        support.lockTimeout();
        CampusNavNode node = nodes.findLiveForUpdate(id).orElseThrow(CampusFailure::nodeNotFound);
        applyNode(node, id, request.nodeCode(), request.nodeName(), request.nodeType(), request.latitude(), request.longitude());
        if (request.isAccessible() != null) node.setAccessible(request.isAccessible());
        if (request.isActive() != null) node.setActive(request.isActive());
        node.setUpdatedBy(actor.userId());
        node.setUpdatedAt(support.now());
        try {
            nodes.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.node(node);
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public void deleteNode(AuthenticatedUser actor, long id) {
        support.lockTimeout();
        CampusNavNode node = nodes.findLiveForUpdate(id).orElseThrow(CampusFailure::nodeNotFound);
        if (edges.existsLiveForNode(id)) throw CampusFailure.inUse("Navigation edges still use this node.");
        if (destinations.existsByNodeId(id)) throw CampusFailure.inUse("Navigation destinations still use this node.");
        node.setDeleted(true);
        node.setUpdatedBy(actor.userId());
        node.setUpdatedAt(support.now());
        nodes.flush();
    }

    private void applyNode(CampusNavNode node, long id, String code, String name, CampusNodeType type, BigDecimal latitude, BigDecimal longitude) {
        support.coordinates(latitude, longitude);
        String nodeCode = support.code(code);
        if (nodes.existsBySiteIdAndNodeCodeAndIdNot(node.getSite().getId(), nodeCode, id)) throw CampusFailure.duplicate("The node code");
        node.setNodeCode(nodeCode);
        node.setNodeName(support.required(name));
        node.setNodeType(type);
        node.setLatitude(latitude);
        node.setLongitude(longitude);
    }

    // Edges

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusPageResponse<CampusEdgeResponse> edges(CampusPageQuery query) {
        Long siteId = query.id("siteId");
        CampusEdgeStatus status = query.enumValue("status", CampusEdgeStatus.class);
        CampusSurfaceType surface = query.enumValue("surfaceType", CampusSurfaceType.class);
        Boolean accessible = query.bool("isWheelchairAccessible");
        Specification<CampusNavEdge> specification = (root, criteria, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (siteId != null) predicates.add(cb.equal(root.get("site").get("id"), siteId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (surface != null) predicates.add(cb.equal(root.get("surfaceType"), surface));
            if (accessible != null) predicates.add(cb.equal(root.get("wheelchairAccessible"), accessible));
            if (query.search() != null) predicates.add(CampusQuerySupport.contains(cb, root.get("edgeCode"), query.search()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var page = edges.findAll(specification, query.pageable());
        var geometry = geometry(page.getContent().stream().map(CampusNavEdge::getId).toList());
        return query.response(page, e -> edgeResponse(e, geometry.getOrDefault(e.getId(), List.of())));
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusEdgeResponse edge(long id) {
        CampusNavEdge edge = edges.findByIdAndDeletedFalse(id).orElseThrow(CampusFailure::edgeNotFound);
        return edgeResponse(edge, geometry(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusEdgeResponse createEdge(AuthenticatedUser actor, CampusEdgeCreateRequest request) {
        support.lockTimeout();
        CampusSite site = sites.findLiveForUpdate(request.siteId()).filter(CampusSite::isActive).orElseThrow(CampusFailure::invalidSite);
        var edge = new CampusNavEdge();
        edge.setSite(site);
        applyEdge(edge, 0L, request.edgeCode(), request.startNodeId(), request.endNodeId(), request.distanceMeters(), request.surfaceType(),
                request.isWheelchairAccessible(), request.intermediatePoints());
        edge.setBidirectional(request.isBidirectional() == null || request.isBidirectional());
        edge.setStatus(request.status() == null ? CampusEdgeStatus.ACTIVE : request.status());
        edge.setCreatedBy(actor.userId());
        edge.setCreatedAt(support.now());
        try {
            edges.saveAndFlush(edge);
            return edgeResponse(edge, replaceGeometry(edge.getId(), request.intermediatePoints()));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    /** One transaction updates the edge and replaces its geometry, so they can never disagree. */
    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusEdgeResponse updateEdge(AuthenticatedUser actor, long id, CampusEdgeUpdateRequest request) {
        support.lockTimeout();
        CampusNavEdge edge = edges.findLiveForUpdate(id).orElseThrow(CampusFailure::edgeNotFound);
        applyEdge(edge, id, request.edgeCode(), request.startNodeId(), request.endNodeId(), request.distanceMeters(), request.surfaceType(),
                request.isWheelchairAccessible(), request.intermediatePoints());
        if (request.isBidirectional() != null) edge.setBidirectional(request.isBidirectional());
        if (request.status() != null) edge.setStatus(request.status());
        edge.setUpdatedBy(actor.userId());
        edge.setUpdatedAt(support.now());
        try {
            edges.flush();
            return edgeResponse(edge, replaceGeometry(id, request.intermediatePoints()));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusEdgeResponse edgeStatus(AuthenticatedUser actor, long id, CampusEdgeStatusRequest request) {
        support.lockTimeout();
        CampusNavEdge edge = edges.findLiveForUpdate(id).orElseThrow(CampusFailure::edgeNotFound);
        edge.setStatus(request.status());
        edge.setUpdatedBy(actor.userId());
        edge.setUpdatedAt(support.now());
        edges.flush();
        return edgeResponse(edge, geometry(List.of(id)).getOrDefault(id, List.of()));
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public void deleteEdge(AuthenticatedUser actor, long id) {
        support.lockTimeout();
        CampusNavEdge edge = edges.findLiveForUpdate(id).orElseThrow(CampusFailure::edgeNotFound);
        edge.setDeleted(true);
        edge.setUpdatedBy(actor.userId());
        edge.setUpdatedAt(support.now());
        edges.flush();
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public CampusEdgeStatisticsResponse edgeStatistics(Long siteId) {
        var counts = edges.counts(siteId);
        long totalNodes = siteId == null ? nodes.countByDeletedFalse() : nodes.countBySiteIdAndDeletedFalse(siteId);
        long activeNodes = siteId == null ? nodes.countByDeletedFalseAndActiveTrue() : nodes.countBySiteIdAndDeletedFalseAndActiveTrue(siteId);
        return new CampusEdgeStatisticsResponse(totalNodes, activeNodes, counts.getTotal(), counts.getActive(), counts.getInactive(),
                counts.getUnderConstruction(), counts.getWheelchairAccessible(), counts.getOneWay());
    }

    private void applyEdge(CampusNavEdge edge, long id, String code, long startId, long endId, BigDecimal distance, CampusSurfaceType surface,
            Boolean wheelchair, List<CampusCoordinate> intermediate) {
        if (startId == endId) throw CampusFailure.invalid("An edge must connect two different nodes.");
        long siteId = edge.getSite().getId();
        String edgeCode = support.code(code);
        if (edges.existsBySiteIdAndEdgeCodeAndIdNot(siteId, edgeCode, id)) throw CampusFailure.duplicate("The edge code");
        // Locked in id order so that two edges on the same nodes cannot deadlock, and a node delete cannot slip in.
        CampusNavNode first = nodes.findLiveForUpdate(Math.min(startId, endId)).orElseThrow(CampusFailure::invalidNode);
        CampusNavNode second = nodes.findLiveForUpdate(Math.max(startId, endId)).orElseThrow(CampusFailure::invalidNode);
        if (!first.getSite().getId().equals(siteId) || !second.getSite().getId().equals(siteId)) throw CampusFailure.invalidNode();
        CampusNavNode start = first.getId() == startId ? first : second;
        CampusNavNode end = first.getId() == startId ? second : first;
        boolean accessible = wheelchair != null ? wheelchair : id != 0L && edge.isWheelchairAccessible();
        if (accessible && surface == CampusSurfaceType.STAIRS)
            throw CampusFailure.invalidEdgeAccessibility("A stair segment cannot be marked wheelchair accessible.");
        if (accessible && (!start.isAccessible() || !end.isAccessible()))
            throw CampusFailure.invalidEdgeAccessibility("A wheelchair accessible segment must connect accessible nodes.");
        if (intermediate != null) intermediate.forEach(support::coordinates);
        edge.setEdgeCode(edgeCode);
        edge.setStartNode(start);
        edge.setEndNode(end);
        edge.setDistanceMeters(distance);
        edge.setSurfaceType(surface);
        edge.setWheelchairAccessible(accessible);
    }

    private List<CampusCoordinate> replaceGeometry(long edgeId, List<CampusCoordinate> intermediate) {
        points.removeForEdge(edgeId);
        if (intermediate == null || intermediate.isEmpty()) return List.of();
        var rows = new ArrayList<CampusNavEdgePoint>();
        for (int i = 0; i < intermediate.size(); i++) {
            var point = new CampusNavEdgePoint();
            point.setEdgeId(edgeId);
            point.setPointSequence(i);
            point.setLatitude(intermediate.get(i).latitude());
            point.setLongitude(intermediate.get(i).longitude());
            rows.add(point);
        }
        points.saveAllAndFlush(rows);
        return List.copyOf(intermediate);
    }

    private Map<Long, List<CampusCoordinate>> geometry(Collection<Long> edgeIds) {
        if (edgeIds.isEmpty()) return Map.of();
        var grouped = new HashMap<Long, List<CampusCoordinate>>();
        for (var point : points.findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(edgeIds))
            grouped.computeIfAbsent(point.getEdgeId(), k -> new ArrayList<>()).add(new CampusCoordinate(point.getLatitude(), point.getLongitude()));
        return grouped;
    }

    private CampusEdgeResponse edgeResponse(CampusNavEdge edge, List<CampusCoordinate> geometry) {
        return mapper.edge(edge, CampusGeo.walkingMinutes(edge.getDistanceMeters(), properties.walkingSpeedMetersPerSecond()), geometry);
    }

    // Destinations

    @Override @Transactional(readOnly = true, timeout = 15)
    public List<CampusDestinationResponse> destinations(Long siteId, Long nodeId, Long buildingId, Long placeId) {
        return destinations.search(siteId, nodeId, buildingId, placeId).stream().map(mapper::destination).toList();
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusDestinationResponse createDestination(AuthenticatedUser actor, CampusDestinationRequest request) {
        support.lockTimeout();
        var destination = new CampusNavDestination();
        applyDestination(destination, request);
        destination.setCreatedBy(actor.userId());
        destination.setCreatedAt(support.now());
        try {
            return mapper.destination(destinations.saveAndFlush(destination));
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public CampusDestinationResponse updateDestination(AuthenticatedUser actor, long id, CampusDestinationRequest request) {
        support.lockTimeout();
        CampusNavDestination destination = destinations.findWithOwnerById(id).orElseThrow(CampusFailure::destinationNotFound);
        applyDestination(destination, request);
        destination.setUpdatedBy(actor.userId());
        destination.setUpdatedAt(support.now());
        try {
            destinations.flush();
        } catch (DataIntegrityViolationException e) {
            throw support.translate(e);
        }
        return mapper.destination(destination);
    }

    @Override @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional(timeout = 15)
    public void deleteDestination(long id) {
        destinations.delete(destinations.findById(id).orElseThrow(CampusFailure::destinationNotFound));
        destinations.flush();
    }

    private void applyDestination(CampusNavDestination destination, CampusDestinationRequest request) {
        if ((request.buildingId() == null) == (request.placeId() == null))
            throw CampusFailure.invalidDestination("Exactly one of buildingId and placeId is required.");
        CampusNavNode node = nodes.findLiveForUpdate(request.nodeId()).orElseThrow(CampusFailure::invalidNode);
        long siteId = node.getSite().getId();
        CampusBuilding building = null;
        CampusPlace place = null;
        if (request.buildingId() != null) {
            building = buildings.findLiveForUpdate(request.buildingId()).orElseThrow(CampusFailure::invalidBuilding);
            if (!building.getSite().getId().equals(siteId)) throw CampusFailure.invalidDestination("The node and the building belong to different campuses.");
        } else {
            place = places.findLiveForUpdate(request.placeId()).orElseThrow(CampusFailure::placeNotFound);
            if (!place.getSite().getId().equals(siteId)) throw CampusFailure.invalidDestination("The node and the place belong to different campuses.");
        }
        boolean accessible = request.isAccessibleEntrance() == null ? node.isAccessible() : request.isAccessibleEntrance();
        if (accessible && !node.isAccessible())
            throw CampusFailure.invalidDestination("An accessible entrance must use an accessible node.");
        destination.setNode(node);
        destination.setBuilding(building);
        destination.setPlace(place);
        destination.setPrimary(Boolean.TRUE.equals(request.isPrimary()));
        destination.setAccessibleEntrance(accessible);
    }
}
