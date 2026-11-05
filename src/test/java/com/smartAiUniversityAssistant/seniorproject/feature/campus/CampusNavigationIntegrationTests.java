package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sections 48 to 55 and 64 of spec file 41 on a deterministic graph with synthetic coordinates:
 * A(10.000,20) -100- B(10.001,20) -100- C(10.002,20), with an optional long way A -500- C.
 */
class CampusNavigationIntegrationTests extends CampusIntegrationSupport {
    private long site, a, b, c, ab, bc, library, building;

    @BeforeEach
    void graph() {
        site = site("S1");
        a = node(site, "A", 10.000, 20);
        b = node(site, "B", 10.001, 20);
        c = node(site, "C", 10.002, 20);
        ab = edge(site, "AB", a, b, 100);
        bc = edge(site, "BC", b, c, 100);
        building = building(site, "B1", "Library Hall");
        library = place(site, category("LIB", "Library", "LIBRARY"), building, "LIB-01", "University Library");
    }

    private ResultActions route(double latitude, double longitude, String type, long id, boolean accessibleOnly) throws Exception {
        return call(studentToken, post(STUDENT + "/routes/preview"), body("siteId", site, "origin", body("latitude", latitude, "longitude", longitude),
                "destination", body("type", type, "id", id), "accessibleOnly", accessibleOnly));
    }
    private ResultActions routeToLibrary() throws Exception { return route(10.000, 20, "PLACE", library, false); }

    @Test
    void shortestRouteIsSummedTimedAndOrdered() throws Exception {
        edge(site, "LONG", a, c, 500);
        entrance(c, null, library, true);
        var response = routeToLibrary().andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ROUTE_FOUND"))
                .andExpect(jsonPath("$.destination.type").value("PLACE"))
                .andExpect(jsonPath("$.destination.id").value(library))
                .andExpect(jsonPath("$.destination.name").value("University Library"))
                .andExpect(jsonPath("$.distanceMeters").value(200.0))
                .andExpect(jsonPath("$.walkingNetworkDistanceMeters").value(200.0))
                .andExpect(jsonPath("$.approachDistanceMeters").value(0.0))
                // 200 m at 1.3 m/s is 153.8 s, so 3 minutes rounded up.
                .andExpect(jsonPath("$.estimatedWalkingMinutes").value(3))
                .andExpect(jsonPath("$.calculatedAt").value(MONDAY_NOON_BANGKOK.toString()))
                .andExpect(jsonPath("$.estimatedArrivalAt").value(MONDAY_NOON_BANGKOK.plusSeconds(154).toString()))
                .andExpect(jsonPath("$.startNode.code").value("A"))
                .andExpect(jsonPath("$.arrivalNode.code").value("C"))
                .andExpect(jsonPath("$.overview[*].edgeCode", contains("AB", "BC")))
                .andExpect(jsonPath("$.geometry[*].latitude", contains(10.0, 10.001, 10.002)))
                .andExpect(jsonPath("$.geometryStatus").value("APPROXIMATE"))
                .andExpect(jsonPath("$.instructions", hasSize(0))).andReturn();
        assertThat(UUID.fromString(tree(response).get("routeId").asText())).isNotNull();
    }

    @Test
    void measuredGeometryGivesInstructionsInWalkingOrderEvenAgainstTheStoredDirection() throws Exception {
        db.update("INSERT INTO campus_nav_edge_points(edge_id,point_sequence,latitude,longitude) VALUES (?,0,10.0005,20.0001)", ab);
        db.update("INSERT INTO campus_nav_edge_points(edge_id,point_sequence,latitude,longitude) VALUES (?,0,10.0013,20.0001),(?,1,10.0016,20.0002)", bc, bc);
        entrance(a, null, library, true);
        // Walk C to A: both edges backwards.
        route(10.002, 20, "PLACE", library, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.geometryStatus").value("MEASURED"))
                .andExpect(jsonPath("$.geometry[*].latitude", contains(10.002, 10.0016, 10.0013, 10.001, 10.0005, 10.0)))
                .andExpect(jsonPath("$.instructions", hasSize(3)))
                .andExpect(jsonPath("$.instructions[0].text", startsWith("Head ")))
                .andExpect(jsonPath("$.instructions[0].segment").value("BC"))
                .andExpect(jsonPath("$.instructions[0].distanceMeters").value(100.0))
                .andExpect(jsonPath("$.instructions[2].text").value("Arrive at University Library"));
    }

    @Test
    void approachSegmentIsSnappedCountedAndLabeled() throws Exception {
        entrance(c, null, library, true);
        // About 11 m south of A, inside the 75 m limit.
        route(9.9999, 20, "PLACE", library, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.startNode.code").value("A"))
                .andExpect(jsonPath("$.approachDistanceMeters", closeTo(11.12, 0.05)))
                .andExpect(jsonPath("$.walkingNetworkDistanceMeters").value(200.0))
                .andExpect(jsonPath("$.distanceMeters", closeTo(211.12, 0.05)))
                .andExpect(jsonPath("$.geometry[0].latitude").value(9.9999));
    }

    @Test
    void originFarFromTheNetworkIsRefused() throws Exception {
        entrance(c, null, library, true);
        route(11, 20, "PLACE", library, false).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAMPUS_ORIGIN_OUTSIDE_SUPPORTED_AREA"));
    }

    @Test
    void invalidOrMissingCoordinatesAreRejected() throws Exception {
        entrance(c, null, library, true);
        route(91, 20, "PLACE", library, false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        route(10, 181, "PLACE", library, false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(studentToken, post(STUDENT + "/routes/preview"), body("destination", body("type", "PLACE", "id", library))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(studentToken, post(STUDENT + "/routes/preview"), body("origin", body("latitude", 10, "longitude", null), "destination", body("type", "PLACE", "id", library)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(studentToken, post(STUDENT + "/routes/preview"), body("origin", body("latitude", 10, "longitude", 20), "destination", body("type", "SPACESHIP", "id", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingDestinationsAndUnmappedLocations() throws Exception {
        route(10, 20, "PLACE", 999999, false).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_LOCATION_NOT_FOUND"));
        route(10, 20, "ROOM", 999999, false).andExpect(status().isNotFound());
        route(10, 20, "PLACE", library, false).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_DESTINATION_NOT_MAPPED"));
        call(studentToken, post(STUDENT + "/routes/preview"), body("siteId", 999999, "origin", body("latitude", 10, "longitude", 20), "destination", body("type", "PLACE", "id", library)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_NOT_FOUND"));
        long other = site("S2");
        call(studentToken, post(STUDENT + "/routes/preview"), body("siteId", other, "origin", body("latitude", 10, "longitude", 20), "destination", body("type", "PLACE", "id", library)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_LOCATION_NOT_FOUND"));
    }

    @Test
    void placeFallsBackToItsBuildingAndRoomsStopAtTheEntrance() throws Exception {
        entrance(b, building, null, true);
        routeToLibrary().andExpect(status().isOk()).andExpect(jsonPath("$.arrivalNode.code").value("B")).andExpect(jsonPath("$.distanceMeters").value(100.0));
        long room = room(building, "L201", 2);
        route(10, 20, "ROOM", room, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.destination.type").value("ROOM"))
                .andExpect(jsonPath("$.destination.name").value("L201"))
                .andExpect(jsonPath("$.destination.building.name").value("Library Hall"))
                .andExpect(jsonPath("$.destination.floorNumber").value(2))
                .andExpect(jsonPath("$.destination.note", containsString("building entrance")))
                .andExpect(jsonPath("$.arrivalNode.code").value("B"));
        route(10, 20, "BUILDING", building, false).andExpect(status().isOk()).andExpect(jsonPath("$.destination.name").value("Library Hall"));
        // A dedicated place entrance beats the building's.
        entrance(c, null, library, true);
        routeToLibrary().andExpect(jsonPath("$.arrivalNode.code").value("C"));
    }

    @Test
    void nearestEntranceWinsAmongSeveral() throws Exception {
        entrance(c, null, library, true);
        entrance(b, null, library, false);
        routeToLibrary().andExpect(status().isOk()).andExpect(jsonPath("$.arrivalNode.code").value("B"));
    }

    @Test
    void oneWayEdgesAreRespected() throws Exception {
        db.update("UPDATE campus_nav_edges SET is_bidirectional=FALSE WHERE id=?", ab);
        entrance(a, null, library, true);
        route(10.001, 20, "PLACE", library, false).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"));
        db.update("DELETE FROM campus_nav_destinations");
        entrance(b, null, library, true);
        route(10.000, 20, "PLACE", library, false).andExpect(status().isOk());
    }

    @Test
    void disablingAnEdgeChangesTheRouteAndUnderConstructionIsExplained() throws Exception {
        long longWay = edge(site, "LONG", a, c, 500);
        entrance(c, null, library, true);
        routeToLibrary().andExpect(jsonPath("$.distanceMeters").value(200.0));
        call(superToken, patch(ADMIN + "/navigation/edges/" + bc + "/status"), body("status", "INACTIVE")).andExpect(status().isOk());
        routeToLibrary().andExpect(status().isOk()).andExpect(jsonPath("$.distanceMeters").value(500.0)).andExpect(jsonPath("$.overview[*].edgeCode", contains("LONG")));
        call(superToken, patch(ADMIN + "/navigation/edges/" + longWay + "/status"), body("status", "UNDER_CONSTRUCTION")).andExpect(status().isOk());
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_ROUTE_UNDER_CONSTRUCTION"));
        call(superToken, patch(ADMIN + "/navigation/edges/" + longWay + "/status"), body("status", "INACTIVE")).andExpect(status().isOk());
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"));
    }

    @Test
    void deletedEdgesAndDisabledOrDeletedNodesAreExcluded() throws Exception {
        entrance(c, null, library, true);
        db.update("UPDATE campus_nav_edges SET is_deleted=TRUE WHERE id=?", bc);
        routeToLibrary().andExpect(status().isUnprocessableEntity());
        db.update("UPDATE campus_nav_edges SET is_deleted=FALSE WHERE id=?", bc);
        db.update("UPDATE campus_nav_nodes SET is_active=FALSE WHERE id=?", b);
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"));
        db.update("UPDATE campus_nav_nodes SET is_active=TRUE, is_deleted=TRUE WHERE id=?", b);
        routeToLibrary().andExpect(status().isUnprocessableEntity());
        // A disabled entrance node leaves the place unmapped.
        db.update("UPDATE campus_nav_nodes SET is_deleted=FALSE WHERE id=?", b);
        db.update("UPDATE campus_nav_nodes SET is_active=FALSE WHERE id=?", c);
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_DESTINATION_NOT_MAPPED"));
    }

    @Test
    void accessibleRoutingAvoidsInaccessiblePartsOfTheNetwork() throws Exception {
        long d = node(site, "D", 10.001, 20.001);
        edge(site, "AD", a, d, 150);
        edge(site, "DC", d, c, 150);
        db.update("UPDATE campus_nav_edges SET is_wheelchair_accessible=FALSE, surface_type='STAIRS' WHERE id=?", bc);
        entrance(c, null, library, true);
        routeToLibrary().andExpect(jsonPath("$.distanceMeters").value(200.0)).andExpect(jsonPath("$.accessibleOnly").value(false));
        route(10, 20, "PLACE", library, true).andExpect(status().isOk()).andExpect(jsonPath("$.distanceMeters").value(300.0))
                .andExpect(jsonPath("$.accessibleOnly").value(true))
                .andExpect(jsonPath("$.overview[*].edgeCode", contains("AD", "DC")));
        db.update("UPDATE campus_nav_edges SET is_wheelchair_accessible=FALSE WHERE edge_code='DC'");
        route(10, 20, "PLACE", library, true).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_ACCESSIBLE_ROUTE_NOT_FOUND"));
        routeToLibrary().andExpect(status().isOk());
        // An inaccessible entrance is also refused for accessible routing.
        db.update("UPDATE campus_nav_edges SET is_wheelchair_accessible=TRUE WHERE edge_code='DC'");
        db.update("UPDATE campus_nav_destinations SET is_accessible_entrance=FALSE");
        route(10, 20, "PLACE", library, true).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_ACCESSIBLE_ROUTE_NOT_FOUND"));
        // So is an origin whose only nearby node is inaccessible.
        db.update("UPDATE campus_nav_destinations SET is_accessible_entrance=TRUE");
        db.update("UPDATE campus_nav_nodes SET is_accessible=FALSE WHERE id=?", a);
        route(10, 20, "PLACE", library, true).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_ACCESSIBLE_ROUTE_NOT_FOUND"));
    }

    @Test
    void disconnectedGraphIsAnExplicitFailure() throws Exception {
        long island = node(site, "ISLAND", 10.0015, 20.0005);
        entrance(island, null, library, true);
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"))
                .andExpect(jsonPath("$.distanceMeters").doesNotExist());
    }

    @Test
    void crossCampusDataIsRefusedWithoutLeakingDetails() throws Exception {
        long other = site("S2");
        long foreign = node(other, "FOREIGN", 10.003, 20);
        edge(site, "CROSS", c, foreign, 100);
        entrance(c, null, library, true);
        routeToLibrary().andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NAVIGATION_GRAPH_INVALID"))
                .andExpect(jsonPath("$.message", not(containsString("FOREIGN"))));
    }

    @Test
    void closedLocationsAreNotOfferedAsDestinations() throws Exception {
        entrance(c, null, library, true);
        entrance(c, building, null, true);
        db.update("UPDATE campus_buildings SET status='UNDER_RENOVATION' WHERE id=?", building);
        route(10, 20, "BUILDING", building, false).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"));
        routeToLibrary().andExpect(status().isUnprocessableEntity());
        call(studentToken, get(STUDENT + "/buildings/" + building), null).andExpect(jsonPath("$.canNavigate").value(false)).andExpect(jsonPath("$.latitude").value(10.002));
        db.update("UPDATE campus_buildings SET status='ACTIVE' WHERE id=?", building);
        db.update("UPDATE campus_places SET status='TEMPORARILY_CLOSED' WHERE id=?", library);
        routeToLibrary().andExpect(status().isUnprocessableEntity());
        call(studentToken, get(STUDENT + "/places/" + library), null).andExpect(jsonPath("$.canNavigate").value(false));
        db.update("UPDATE campus_places SET status='INACTIVE' WHERE id=?", library);
        routeToLibrary().andExpect(status().isNotFound());
    }

    @Test
    void locationListReportsNavigabilityFromMappedNodes() throws Exception {
        entrance(c, null, library, true);
        call(studentToken, get(STUDENT + "/locations?group=LIBRARY"), null).andExpect(jsonPath("$.items[0].canNavigate").value(true))
                .andExpect(jsonPath("$.items[0].latitude").value(10.002));
        call(studentToken, get(STUDENT + "/places/" + library), null).andExpect(jsonPath("$.canNavigate").value(true)).andExpect(jsonPath("$.longitude").value(20.0));
    }

    @Test
    void nearbyRanksByWalkingDistanceAndHandlesDisconnectedDestinations() throws Exception {
        long islandNode = node(site, "ISLAND", 10.0004, 20.0004);
        long canteen = place(site, category("CAF", "Cafeteria", "FOOD"), null, "CAF-01", "Canteen");
        long island = place(site, category("HLT", "Health", "HEALTH"), null, "HLT-01", "Island Clinic");
        place(site, category("ATM", "ATM", "OTHER"), null, "ATM-01", "Unmapped ATM");
        entrance(c, null, library, true);
        entrance(b, null, canteen, true);
        entrance(islandNode, null, island, true);
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10.000, "longitude", 20, "group", "ALL", "limit", 10, "accessibleOnly", false)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[*].name", contains("Canteen", "University Library", "Island Clinic")))
                .andExpect(jsonPath("$.items[0].walkingDistanceMeters").value(100.0))
                .andExpect(jsonPath("$.items[0].estimatedWalkingMinutes").value(2))
                .andExpect(jsonPath("$.items[0].canNavigate").value(true))
                .andExpect(jsonPath("$.items[2].walkingDistanceMeters").value(nullValue()))
                .andExpect(jsonPath("$.items[2].estimatedWalkingMinutes").value(nullValue()))
                .andExpect(jsonPath("$.items[2].canNavigate").value(false))
                .andExpect(jsonPath("$.items[2].straightLineDistanceMeters").isNumber());
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10.000, "longitude", 20, "group", "FOOD")).andExpect(jsonPath("$.items[*].name", contains("Canteen")));
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10.000, "longitude", 20, "limit", 1)).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void nearbyWithoutGpsOrFarAwayNeverClaimsAWalkingRoute() throws Exception {
        entrance(c, null, library, true);
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 95, "longitude", 20)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10, "longitude", 20, "group", "NOPE")).andExpect(status().isBadRequest());
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10, "longitude", 20, "limit", 51)).andExpect(status().isBadRequest());
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 11, "longitude", 20)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].walkingDistanceMeters").value(nullValue()))
                .andExpect(jsonPath("$.items[0].canNavigate").value(false))
                .andExpect(jsonPath("$.items[0].straightLineDistanceMeters", greaterThan(100000.0)));
    }

    @Test
    void studentCoordinatesAreNeverPersisted() throws Exception {
        entrance(c, null, library, true);
        String sentinel = "10.0000999";
        long before = rowCount();
        routeToLibrary().andExpect(status().isOk());
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 10.0000999, "longitude", 20.0000999)).andExpect(status().isOk());
        assertThat(rowCount()).isEqualTo(before);
        var redisKeys = redis.keys("*");
        assertThat(redisKeys).isNotNull();
        for (String key : redisKeys)
            if ("string".equals(redis.type(key).code())) assertThat(redis.opsForValue().get(key)).doesNotContain(sentinel);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
                  AND table_name IN (SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE '%student%')
                  AND (column_name LIKE '%latitude%' OR column_name LIKE '%longitude%' OR column_name LIKE '%gps%')""", Long.class)).isZero();
    }

    private long rowCount() {
        long total = 0;
        for (String table : db.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'", String.class))
            total += db.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return total;
    }
}
