package com.smartAiUniversityAssistant.seniorproject.feature.campus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.CampusSurfaceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Small deterministic graphs, no database. Coordinates are synthetic. */
class CampusRouteGraphTests {
    private static CampusRouteGraph.Node node(long id, boolean accessible) {
        return new CampusRouteGraph.Node(id, "N" + id, "Node " + id, 10 + id / 1000d, 20, accessible);
    }
    private static CampusRouteGraph.Edge edge(long id, long from, long to, String distance) {
        return new CampusRouteGraph.Edge(id, "E" + id, from, to, new BigDecimal(distance), CampusSurfaceType.PAVED, true, true, false, List.of());
    }
    private static CampusRouteGraph.Edge oneWay(long id, long from, long to, String distance) {
        return new CampusRouteGraph.Edge(id, "E" + id, from, to, new BigDecimal(distance), CampusSurfaceType.PAVED, true, false, false, List.of());
    }
    private static CampusRouteGraph.Edge inaccessible(long id, long from, long to, String distance) {
        return new CampusRouteGraph.Edge(id, "E" + id, from, to, new BigDecimal(distance), CampusSurfaceType.STAIRS, false, true, false, List.of());
    }
    private static CampusRouteGraph.Edge construction(long id, long from, long to, String distance) {
        return new CampusRouteGraph.Edge(id, "E" + id, from, to, new BigDecimal(distance), CampusSurfaceType.PAVED, true, true, true, List.of());
    }
    private static List<String> codes(CampusRouteGraph.Path path) {
        return path.steps().stream().map(s -> s.edge().code()).toList();
    }

    @Test
    void sumsTheEdgesOfAChain() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true), node(3, true)), List.of(edge(1, 1, 2, "100.25"), edge(2, 2, 3, "50.50")));
        var path = graph.search(1, false, false).pathTo(3).orElseThrow();
        assertThat(path.distanceMeters()).isEqualByComparingTo("150.75");
        assertThat(codes(path)).containsExactly("E1", "E2");
    }

    @Test
    void picksTheShortestAlternativeEvenWithMoreEdges() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true), node(3, true), node(4, true)),
                List.of(edge(1, 1, 3, "500"), edge(2, 1, 2, "100"), edge(3, 2, 4, "100"), edge(4, 4, 3, "100")));
        var path = graph.search(1, false, false).pathTo(3).orElseThrow();
        assertThat(path.distanceMeters()).isEqualByComparingTo("300");
        assertThat(codes(path)).containsExactly("E2", "E3", "E4");
    }

    @Test
    void respectsOneWayEdges() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true)), List.of(oneWay(1, 1, 2, "100")));
        assertThat(graph.search(1, false, false).pathTo(2)).isPresent();
        assertThat(graph.search(2, false, false).pathTo(1)).isEmpty();
    }

    @Test
    void underConstructionEdgesAreOnlyWalkedWhenExplicitlyAsked() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true)), List.of(construction(1, 1, 2, "100")));
        assertThat(graph.search(1, false, false).pathTo(2)).isEmpty();
        assertThat(graph.search(1, false, true).pathTo(2)).isPresent();
    }

    @Test
    void accessibleSearchAvoidsInaccessibleEdgesAndNodes() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true), node(3, true), node(4, false)),
                List.of(inaccessible(1, 1, 2, "10"), edge(2, 1, 3, "50"), edge(3, 3, 2, "50"), edge(4, 1, 4, "1"), edge(5, 4, 2, "1")));
        assertThat(graph.search(1, false, false).pathTo(2).orElseThrow().distanceMeters()).isEqualByComparingTo("2");
        var accessible = graph.search(1, true, false).pathTo(2).orElseThrow();
        assertThat(accessible.distanceMeters()).isEqualByComparingTo("100");
        assertThat(codes(accessible)).containsExactly("E2", "E3");
    }

    @Test
    void disconnectedNodesHaveNoPathAndAreNeverJoinedBecauseTheyAreClose() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true)), List.of());
        assertThat(graph.search(1, false, false).pathTo(2)).isEmpty();
    }

    @Test
    void edgesTouchingAMissingNodeAreIgnored() {
        var graph = new CampusRouteGraph(List.of(node(1, true), node(3, true)), List.of(edge(1, 1, 2, "10"), edge(2, 2, 3, "10")));
        assertThat(graph.search(1, false, false).pathTo(3)).isEmpty();
    }

    @Test
    void walkingABidirectionalEdgeBackwardsReversesItsGeometry() {
        var edge = new CampusRouteGraph.Edge(1, "E1", 1, 2, new BigDecimal("10"), CampusSurfaceType.PAVED, true, true, false,
                List.of(new CampusRouteGraph.Point(1, 1), new CampusRouteGraph.Point(2, 2)));
        var graph = new CampusRouteGraph(List.of(node(1, true), node(2, true)), List.of(edge));
        var line = graph.search(2, false, false).pathTo(1).orElseThrow().steps().get(0).line();
        assertThat(line).hasSize(4);
        assertThat(line.get(1)).isEqualTo(new CampusRouteGraph.Point(2, 2));
        assertThat(line.get(2)).isEqualTo(new CampusRouteGraph.Point(1, 1));
        assertThat(line.get(0).latitude()).isEqualTo(node(2, true).latitude());
    }

    @Test
    void nearestSkipsInaccessibleNodesWhenAsked() {
        var graph = new CampusRouteGraph(List.of(node(1, false), node(5, true)), List.of());
        assertThat(graph.nearest(10.001, 20, false).orElseThrow().node().id()).isEqualTo(1);
        assertThat(graph.nearest(10.001, 20, true).orElseThrow().node().id()).isEqualTo(5);
    }

    @Test
    void geoHelpers() {
        // One degree of latitude is about 111.2 km.
        assertThat(CampusGeo.haversineMeters(10, 20, 11, 20)).isBetween(111_000d, 111_400d);
        assertThat(CampusGeo.bearingDegrees(10, 20, 11, 20)).isZero();
        assertThat(CampusGeo.bearingDegrees(10, 20, 10, 21)).isBetween(89, 91);
        assertThat(CampusGeo.compass(0)).isEqualTo("north");
        assertThat(CampusGeo.compass(359)).isEqualTo("north");
        assertThat(CampusGeo.compass(180)).isEqualTo("south");
        assertThat(CampusGeo.walkingMinutes(new BigDecimal("78"), 1.3)).isEqualTo(1);
        assertThat(CampusGeo.walkingMinutes(new BigDecimal("79"), 1.3)).isEqualTo(2);
        assertThat(CampusGeo.walkingMinutes(BigDecimal.ZERO, 1.3)).isZero();
    }

    @Test
    void instructionsNeedMeasuredGeometryOnEveryEdge() {
        var measured = new CampusRouteGraph.Edge(1, "E1", 1, 2, new BigDecimal("100"), CampusSurfaceType.PAVED, true, true, false,
                List.of(new CampusRouteGraph.Point(10.0005, 20)));
        var turning = new CampusRouteGraph.Edge(2, "E2", 2, 3, new BigDecimal("50"), CampusSurfaceType.PAVED, true, true, false,
                List.of(new CampusRouteGraph.Point(10.001, 20.0005)));
        var bare = edge(3, 2, 3, "50");
        var a = new CampusRouteGraph.Node(1, "A", "A", 10, 20, true);
        var b = new CampusRouteGraph.Node(2, "B", "B", 10.001, 20, true);
        var c = new CampusRouteGraph.Node(3, "C", "C", 10.002, 20.001, true);
        var full = new CampusRouteGraph(List.of(a, b, c), List.of(measured, turning)).search(1, false, false).pathTo(3).orElseThrow().steps();
        var text = CampusRouteInstructions.build(full, "Target").stream().map(i -> i.text()).toList();
        assertThat(text).hasSize(3);
        assertThat(text.get(0)).isEqualTo("Head north toward B");
        assertThat(text.get(1)).startsWith("Turn right").endsWith("toward C");
        assertThat(text.get(2)).isEqualTo("Arrive at Target");
        var partial = new CampusRouteGraph(List.of(a, b, c), List.of(measured, bare)).search(1, false, false).pathTo(3).orElseThrow().steps();
        assertThat(CampusRouteInstructions.available(partial)).isFalse();
        assertThat(CampusRouteInstructions.build(partial, "Target")).isEmpty();
    }

    @Test
    void turnNames() {
        assertThat(CampusRouteInstructions.turn(0, 10)).isEqualTo("Continue straight");
        assertThat(CampusRouteInstructions.turn(0, 90)).isEqualTo("Turn right");
        assertThat(CampusRouteInstructions.turn(0, 270)).isEqualTo("Turn left");
        assertThat(CampusRouteInstructions.turn(350, 80)).isEqualTo("Turn right");
        assertThat(CampusRouteInstructions.turn(0, 180)).isEqualTo("Turn around");
    }
}
