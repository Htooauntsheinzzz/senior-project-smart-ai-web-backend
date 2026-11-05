package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusSurfaceType;
import java.math.BigDecimal;
import java.util.*;

/**
 * The walking network of one campus, with Dijkstra's shortest path on verified edge distances. Nodes are never connected
 * because they are close: only edges supplied here are walked. No database or Spring dependency, so it is tested directly.
 */
final class CampusRouteGraph {
    record Point(double latitude, double longitude) {}
    record Node(long id, String code, String name, double latitude, double longitude, boolean accessible) {}
    record Edge(long id, String code, long startNodeId, long endNodeId, BigDecimal distanceMeters, CampusSurfaceType surface,
            boolean wheelchairAccessible, boolean bidirectional, boolean underConstruction, List<Point> intermediatePoints) {}
    /** One edge walked in one direction. */
    record Step(Edge edge, Node from, Node to, boolean forward) {
        /** Full line in walking order: from node, intermediate points, to node. */
        List<Point> line() {
            var line = new ArrayList<Point>();
            line.add(new Point(from.latitude(), from.longitude()));
            var points = new ArrayList<>(edge.intermediatePoints());
            if (!forward) Collections.reverse(points);
            line.addAll(points);
            line.add(new Point(to.latitude(), to.longitude()));
            return line;
        }
        boolean hasMeasuredGeometry() { return !edge.intermediatePoints().isEmpty(); }
    }
    record Snap(Node node, double distanceMeters) {}
    record Path(Node target, BigDecimal distanceMeters, List<Step> steps) {}

    private final Map<Long, Node> nodes = new TreeMap<>();
    private final Map<Long, List<Step>> outgoing = new HashMap<>();

    CampusRouteGraph(Collection<Node> nodes, Collection<Edge> edges) {
        for (Node node : nodes) this.nodes.put(node.id(), node);
        for (Edge edge : edges) {
            Node start = this.nodes.get(edge.startNodeId()), end = this.nodes.get(edge.endNodeId());
            // An edge touching a disabled or deleted node cannot be walked.
            if (start == null || end == null) continue;
            outgoing.computeIfAbsent(start.id(), k -> new ArrayList<>()).add(new Step(edge, start, end, true));
            if (edge.bidirectional()) outgoing.computeIfAbsent(end.id(), k -> new ArrayList<>()).add(new Step(edge, end, start, false));
        }
    }

    boolean contains(long nodeId) { return nodes.containsKey(nodeId); }
    Node node(long nodeId) { return nodes.get(nodeId); }
    Collection<Node> nodes() { return nodes.values(); }

    /** Nearest usable node by straight line, lowest id on a tie. */
    Optional<Snap> nearest(double latitude, double longitude, boolean accessibleOnly) {
        Snap best = null;
        for (Node node : nodes.values()) {
            if (accessibleOnly && !node.accessible()) continue;
            double distance = CampusGeo.haversineMeters(latitude, longitude, node.latitude(), node.longitude());
            if (best == null || distance < best.distanceMeters()) best = new Snap(node, distance);
        }
        return Optional.ofNullable(best);
    }

    Search search(long sourceId, boolean accessibleOnly, boolean includeUnderConstruction) {
        var distances = new HashMap<Long, BigDecimal>();
        var arrivedBy = new HashMap<Long, Step>();
        var settled = new HashSet<Long>();
        record Entry(long node, BigDecimal distance) {}
        var queue = new PriorityQueue<Entry>(Comparator.comparing(Entry::distance).thenComparingLong(Entry::node));
        Node source = nodes.get(sourceId);
        if (source != null && (!accessibleOnly || source.accessible())) {
            distances.put(sourceId, BigDecimal.ZERO);
            queue.add(new Entry(sourceId, BigDecimal.ZERO));
        }
        while (!queue.isEmpty()) {
            Entry current = queue.poll();
            if (!settled.add(current.node())) continue;
            for (Step step : outgoing.getOrDefault(current.node(), List.of())) {
                Edge edge = step.edge();
                if (edge.underConstruction() && !includeUnderConstruction) continue;
                if (accessibleOnly && (!edge.wheelchairAccessible() || !step.to().accessible())) continue;
                BigDecimal next = current.distance().add(edge.distanceMeters());
                BigDecimal known = distances.get(step.to().id());
                if (known == null || next.compareTo(known) < 0) {
                    distances.put(step.to().id(), next);
                    arrivedBy.put(step.to().id(), step);
                    queue.add(new Entry(step.to().id(), next));
                }
            }
        }
        return new Search(distances, arrivedBy, this);
    }

    /** Result of one search from one source. */
    static final class Search {
        private final Map<Long, BigDecimal> distances;
        private final Map<Long, Step> arrivedBy;
        private final CampusRouteGraph graph;
        private Search(Map<Long, BigDecimal> distances, Map<Long, Step> arrivedBy, CampusRouteGraph graph) {
            this.distances = distances;
            this.arrivedBy = arrivedBy;
            this.graph = graph;
        }
        Optional<BigDecimal> distanceTo(long nodeId) { return Optional.ofNullable(distances.get(nodeId)); }
        Optional<Path> pathTo(long nodeId) {
            BigDecimal distance = distances.get(nodeId);
            if (distance == null) return Optional.empty();
            var steps = new ArrayDeque<Step>();
            for (Step step = arrivedBy.get(nodeId); step != null; step = arrivedBy.get(step.from().id())) steps.addFirst(step);
            return Optional.of(new Path(graph.node(nodeId), distance, List.copyOf(steps)));
        }
    }
}
