package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusEdgeStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.campus.repository.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reads one campus's walking network from PostgreSQL. Inactive and deleted rows never enter the graph. */
@Component
class CampusGraphLoader {
    private final CampusNavNodeRepository nodes;
    private final CampusNavEdgeRepository edges;
    private final CampusNavEdgePointRepository points;

    CampusGraphLoader(CampusNavNodeRepository nodes, CampusNavEdgeRepository edges, CampusNavEdgePointRepository points) {
        this.nodes = nodes;
        this.edges = edges;
        this.points = points;
    }

    /** @throws CampusFailure CAMPUS_NAVIGATION_GRAPH_INVALID when a usable edge crosses into another campus */
    CampusRouteGraph load(long siteId) {
        var graphNodes = nodes.findBySiteIdAndDeletedFalseAndActiveTrueOrderByIdAsc(siteId).stream()
                .map(n -> new CampusRouteGraph.Node(n.getId(), n.getNodeCode(), n.getNodeName(), n.getLatitude().doubleValue(),
                        n.getLongitude().doubleValue(), n.isAccessible())).toList();
        var rows = edges.findBySiteIdAndDeletedFalseOrderByIdAsc(siteId).stream()
                .filter(e -> e.getStatus() != CampusEdgeStatus.INACTIVE).toList();
        for (CampusNavEdge edge : rows)
            if (!edge.getStartNode().getSite().getId().equals(siteId) || !edge.getEndNode().getSite().getId().equals(siteId))
                throw CampusFailure.graphInvalid();
        var geometry = new HashMap<Long, List<CampusRouteGraph.Point>>();
        if (!rows.isEmpty()) for (var point : points.findByEdgeIdInOrderByEdgeIdAscPointSequenceAsc(rows.stream().map(CampusNavEdge::getId).toList()))
            geometry.computeIfAbsent(point.getEdgeId(), k -> new ArrayList<>())
                    .add(new CampusRouteGraph.Point(point.getLatitude().doubleValue(), point.getLongitude().doubleValue()));
        var graphEdges = rows.stream().map(e -> new CampusRouteGraph.Edge(e.getId(), e.getEdgeCode(), e.getStartNode().getId(), e.getEndNode().getId(),
                e.getDistanceMeters(), e.getSurfaceType(), e.isWheelchairAccessible(), e.isBidirectional(),
                e.getStatus() == CampusEdgeStatus.UNDER_CONSTRUCTION, geometry.getOrDefault(e.getId(), List.of()))).toList();
        return new CampusRouteGraph(graphNodes, graphEdges);
    }
}
