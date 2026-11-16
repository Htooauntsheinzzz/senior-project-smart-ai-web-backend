package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Section 66 of spec file 41: the whole workflow through the HTTP API only, in the spec's order, with the same bodies as the
 * Postman guide. All names and coordinates are synthetic and must never be copied into real university records.
 */
class CampusPostmanWorkflowTests extends CampusIntegrationSupport {
    private long created(ResultActions result) throws Exception {
        return tree(result.andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void adminBuildsTheCampusAndAStudentNavigatesIt() throws Exception {
        long site = created(call(superToken, post(ADMIN + "/sites"), body("siteCode", "TEST-MAIN", "siteName", "Test Campus", "description", "Synthetic Postman data",
                "timezone", "Asia/Bangkok", "centerLatitude", 13.001, "centerLongitude", 100.0)));

        long library = created(call(adminToken, post(ADMIN + "/categories"), body("categoryCode", "LIBRARY", "categoryName", "Library", "mobileGroup", "LIBRARY")));
        created(call(adminToken, post(ADMIN + "/categories"), body("categoryCode", "CAFETERIA", "categoryName", "Cafeteria", "mobileGroup", "FOOD")));
        long wifi = created(call(adminToken, post(ADMIN + "/facilities"), body("facilityCode", "WIFI", "facilityName", "Wi-Fi")));
        long printing = created(call(adminToken, post(ADMIN + "/facilities"), body("facilityCode", "PRINTING", "facilityName", "Printing")));

        long building = created(call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "BLD-001", "buildingName", "Test Library Building",
                "buildingNameTh", null, "description", "Synthetic building", "totalFloors", 5, "areaSquareMeters", 9600.00, "status", "ACTIVE")));
        long room = created(call(adminToken, post(ADMIN + "/rooms"), body("buildingId", building, "roomNumber", "BLD-201", "roomName", "Computer Lab A", "floorNumber", 2,
                "roomType", "COMPUTER_LAB", "capacity", 40, "status", "AVAILABLE", "facilityIds", List.of(wifi))));
        long place = created(call(adminToken, post(ADMIN + "/places"), body("siteId", site, "categoryId", library, "buildingId", building, "placeCode", "LIB-01",
                "placeName", "Test University Library", "placeNameTh", null, "description", "Synthetic library", "floorLabel", "1F–5F", "phoneNumber", null,
                "isPopular", true, "isVisibleOnMobile", true, "status", "ACTIVE", "facilityIds", List.of(wifi, printing))));

        var week = new ArrayList<Object>();
        for (int day = 1; day <= 5; day++) week.add(body("dayOfWeek", day, "opensAt", "08:00", "closesAt", "21:00", "closed", false));
        week.add(body("dayOfWeek", 6, "opensAt", null, "closesAt", null, "closed", true));
        week.add(body("dayOfWeek", 7, "opensAt", null, "closesAt", null, "closed", true));
        call(adminToken, put(ADMIN + "/places/" + place + "/opening-hours"), body("hours", week)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(7)));
        call(adminToken, put(ADMIN + "/buildings/" + building + "/opening-hours"), body("hours", week)).andExpect(status().isOk());

        long a = created(call(superToken, post(ADMIN + "/navigation/nodes"), body("siteId", site, "nodeCode", "JCT-A", "nodeName", "Junction A", "nodeType", "JUNCTION",
                "latitude", 13.0000000, "longitude", 100.0000000, "isAccessible", true, "isActive", true)));
        long b = created(call(superToken, post(ADMIN + "/navigation/nodes"), body("siteId", site, "nodeCode", "JCT-B", "nodeName", "Junction B", "nodeType", "JUNCTION",
                "latitude", 13.0010000, "longitude", 100.0000000, "isAccessible", true, "isActive", true)));
        long entrance = created(call(superToken, post(ADMIN + "/navigation/nodes"), body("siteId", site, "nodeCode", "LIB-ENT", "nodeName", "Library Entrance", "nodeType", "ENTRANCE",
                "latitude", 13.0020000, "longitude", 100.0000000, "isAccessible", true, "isActive", true)));
        created(call(superToken, post(ADMIN + "/navigation/edges"), body("siteId", site, "edgeCode", "R01", "startNodeId", a, "endNodeId", b, "distanceMeters", 112,
                "surfaceType", "COVERED_WALKWAY", "isWheelchairAccessible", true, "isBidirectional", true, "status", "ACTIVE",
                "intermediatePoints", List.of(body("latitude", 13.0005000, "longitude", 100.0000500)))));
        long r02 = created(call(superToken, post(ADMIN + "/navigation/edges"), body("siteId", site, "edgeCode", "R02", "startNodeId", b, "endNodeId", entrance, "distanceMeters", 113,
                "surfaceType", "PAVED", "isWheelchairAccessible", true, "isBidirectional", true, "status", "ACTIVE",
                "intermediatePoints", List.of(body("latitude", 13.0015000, "longitude", 100.0000500)))));
        created(call(superToken, post(ADMIN + "/navigation/destinations"), body("nodeId", entrance, "buildingId", null, "placeId", place, "isPrimary", true, "isAccessibleEntrance", true)));
        created(call(superToken, post(ADMIN + "/navigation/destinations"), body("nodeId", entrance, "buildingId", building, "placeId", null, "isPrimary", true, "isAccessibleEntrance", true)));
        call(superToken, get(ADMIN + "/navigation/validation?siteId=" + site), null).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.summary.blockingIssues").value(0)).andExpect(jsonPath("$.summary.warnings").value(0));
        call(superToken, get(ADMIN + "/map?siteId=" + site), null).andExpect(jsonPath("$.summary.totalLocations").value(2)).andExpect(jsonPath("$.summary.locationsWithoutCoordinates").value(0));

        call(studentToken, get(STUDENT + "/categories"), null).andExpect(status().isOk()).andExpect(jsonPath("$[*].code", contains("ALL", "BUILDINGS", "FOOD", "LIBRARY")));
        call(studentToken, get(STUDENT + "/locations?group=LIBRARY"), null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("LIB-01")).andExpect(jsonPath("$.items[0].canNavigate").value(true));
        call(studentToken, get(STUDENT + "/places/" + place), null).andExpect(status().isOk()).andExpect(jsonPath("$.facilities", contains("Printing", "Wi-Fi")))
                .andExpect(jsonPath("$.openingStatus.expectedOpen").value(true)).andExpect(jsonPath("$.canViewRooms").value(true)).andExpect(jsonPath("$.latitude").value(13.002));
        call(studentToken, get(STUDENT + "/buildings/" + building + "/rooms"), null).andExpect(jsonPath("$.items[0].id").value(room));
        call(studentToken, get(STUDENT + "/map?siteId=" + site), null).andExpect(jsonPath("$.places", hasSize(1)));
        call(studentToken, post(STUDENT + "/nearby"), body("siteId", site, "latitude", 13.0000000, "longitude", 100.0000000, "group", "ALL", "limit", 10, "accessibleOnly", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].walkingDistanceMeters").value(225.0)).andExpect(jsonPath("$.items[0].canNavigate").value(true));
        var route = body("siteId", site, "origin", body("latitude", 13.0000000, "longitude", 100.0000000), "destination", body("type", "PLACE", "id", place), "accessibleOnly", false);
        call(studentToken, post(STUDENT + "/routes/preview"), route).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ROUTE_FOUND"))
                .andExpect(jsonPath("$.distanceMeters").value(225.0)).andExpect(jsonPath("$.estimatedWalkingMinutes").value(3))
                .andExpect(jsonPath("$.geometryStatus").value("MEASURED")).andExpect(jsonPath("$.geometry", hasSize(5)))
                .andExpect(jsonPath("$.instructions[0].text").value("Head north toward Junction B"))
                .andExpect(jsonPath("$.instructions[2].text").value("Arrive at Test University Library"));
        call(studentToken, post(STUDENT + "/routes/preview"), body("origin", body("latitude", 13.0000000, "longitude", 100.0000000), "destination", body("type", "ROOM", "id", room)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.destination.note", containsString("building entrance")));

        // Disabling an edge changes what students get at once, and the validation report shows why.
        call(superToken, patch(ADMIN + "/navigation/edges/" + r02 + "/status"), body("status", "INACTIVE")).andExpect(status().isOk());
        call(studentToken, post(STUDENT + "/routes/preview"), route).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CAMPUS_NO_AVAILABLE_ROUTE"));
        call(superToken, get(ADMIN + "/navigation/validation?siteId=" + site), null).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.inactiveEdges[0].code").value("R02"));
        call(superToken, patch(ADMIN + "/navigation/edges/" + r02 + "/status"), body("status", "ACTIVE")).andExpect(status().isOk());
        call(studentToken, post(STUDENT + "/routes/preview"), route).andExpect(status().isOk());
    }
}
