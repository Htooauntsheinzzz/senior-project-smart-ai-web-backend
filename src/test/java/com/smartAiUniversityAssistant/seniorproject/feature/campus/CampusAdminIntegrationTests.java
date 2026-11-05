package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Section 62 of spec file 41: admin CRUD, statistics, dependency checks, audit fields and authorization. */
class CampusAdminIntegrationTests extends CampusIntegrationSupport {

    @Test
    void nothingIsSeeded() throws Exception {
        call(superToken, get(ADMIN + "/sites"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        call(superToken, get(ADMIN + "/categories"), null).andExpect(jsonPath("$", hasSize(0)));
        call(superToken, get(ADMIN + "/facilities"), null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void siteCrudValidationAndAudit() throws Exception {
        var created = call(superToken, post(ADMIN + "/sites"), body("siteCode", "main-1", "siteName", "Test Campus", "timezone", "Asia/Bangkok",
                "centerLatitude", 13.5, "centerLongitude", 100.5)).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.siteCode").value("MAIN-1"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.createdBy").value(superAdminId))
                .andExpect(jsonPath("$.updatedBy").value(nullValue())).andReturn();
        long id = tree(created).get("id").asLong();
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "MAIN-1", "siteName", "Again")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPUS_DUPLICATE_CODE"));
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "BAD-TZ", "siteName", "X", "timezone", "Mars/Olympus")).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "HALF", "siteName", "X", "centerLatitude", 13.5)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "FAR", "siteName", "X", "centerLatitude", 95, "centerLongitude", 10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "EXTRA", "siteName", "X", "createdBy", 99)).andExpect(status().isBadRequest());
        call(superToken, put(ADMIN + "/sites/" + id), body("siteCode", "MAIN-1", "siteName", "Renamed")).andExpect(status().isOk())
                .andExpect(jsonPath("$.siteName").value("Renamed"))
                .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
                .andExpect(jsonPath("$.updatedBy").value(superAdminId));
        building(id, "B1", "Dependent");
        call(superToken, delete(ADMIN + "/sites/" + id), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPUS_DEPENDENCY_EXISTS"));
        db.update("DELETE FROM campus_buildings");
        call(superToken, delete(ADMIN + "/sites/" + id), null).andExpect(status().isNoContent());
        call(superToken, get(ADMIN + "/sites/" + id), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_NOT_FOUND"));
    }

    @Test
    void buildingCrudFiltersStatisticsAndHours() throws Exception {
        long site = site("S1");
        var created = call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "bld-001", "buildingName", "Academic One",
                "buildingNameTh", null, "description", "Teaching", "totalFloors", 5, "areaSquareMeters", 9600.50, "status", "ACTIVE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buildingCode").value("BLD-001"))
                .andExpect(jsonPath("$.totalRooms").value(0))
                .andExpect(jsonPath("$.siteName").value("S1 Campus"))
                .andExpect(jsonPath("$.openingHoursSummary").value(nullValue())).andReturn();
        long id = tree(created).get("id").asLong();
        String path = ADMIN + "/buildings/" + id;
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "BLD-001", "buildingName", "Dup", "totalFloors", 1)).andExpect(status().isConflict());
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "X", "buildingName", "Y", "totalFloors", 0)).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "X", "buildingName", "Y", "totalFloors", 1, "areaSquareMeters", -1)).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "X", "buildingName", "Y", "totalFloors", 1, "status", "MADE_UP")).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "X", "buildingName", "Y", "totalFloors", 1, "totalRooms", 9)).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", 999999, "buildingCode", "X", "buildingName", "Y", "totalFloors", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_SITE"));
        db.update("UPDATE campus_sites SET is_active=FALSE WHERE id=?", site);
        call(adminToken, post(ADMIN + "/buildings"), body("siteId", site, "buildingCode", "X", "buildingName", "Y", "totalFloors", 1)).andExpect(status().isBadRequest());
        db.update("UPDATE campus_sites SET is_active=TRUE WHERE id=?", site);

        call(adminToken, put(path), body("buildingCode", "BLD-001", "buildingName", "Academic Uno", "totalFloors", 6)).andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingName").value("Academic Uno"))
                .andExpect(jsonPath("$.totalFloors").value(6))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(db.queryForObject("SELECT updated_by FROM campus_buildings WHERE id=?", Long.class, id)).isNotNull();
        call(adminToken, patch(path + "/status"), body("status", "UNDER_RENOVATION")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_RENOVATION"));
        call(adminToken, patch(path + "/status"), body("status", "NOPE")).andExpect(status().isBadRequest());

        var hours = new ArrayList<Object>();
        for (int day = 1; day <= 5; day++) hours.add(body("dayOfWeek", day, "opensAt", "08:00", "closesAt", "17:00", "closed", false));
        hours.add(body("dayOfWeek", 6, "opensAt", null, "closesAt", null, "closed", true));
        call(adminToken, put(path + "/opening-hours"), body("hours", hours)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6))).andExpect(jsonPath("$[0].opensAt").value("08:00"));
        // Replacing the same days must not trip the unique index.
        call(adminToken, put(path + "/opening-hours"), body("hours", hours)).andExpect(status().isOk());
        call(adminToken, put(path + "/opening-exceptions"), body("exceptions", List.of(body("date", "2026-12-25", "closed", true, "reason", "Closure"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value("2026-12-25"));
        room(id, "101", 1);
        call(adminToken, get(ADMIN + "/buildings?search=uno&siteId=" + site + "&status=UNDER_RENOVATION"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].totalRooms").value(1))
                .andExpect(jsonPath("$.content[0].openingHoursSummary").value("Mon-Fri 08:00-17:00, Sat Closed"));
        call(adminToken, get(ADMIN + "/buildings?status=ACTIVE"), null).andExpect(jsonPath("$.totalElements").value(0));
        call(adminToken, get(ADMIN + "/buildings?unknown=1"), null).andExpect(status().isBadRequest());
        call(adminToken, get(ADMIN + "/buildings?sort=password,asc"), null).andExpect(status().isBadRequest());
        call(adminToken, get(path), null).andExpect(jsonPath("$.openingHours", hasSize(6))).andExpect(jsonPath("$.openingExceptions[0].reason").value("Closure"));

        building(site, "B2", "Second");
        call(adminToken, get(ADMIN + "/buildings/statistics"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBuildings").value(2))
                .andExpect(jsonPath("$.activeBuildings").value(1))
                .andExpect(jsonPath("$.underRenovationBuildings").value(1))
                .andExpect(jsonPath("$.inactiveBuildings").value(0))
                .andExpect(jsonPath("$.totalRooms").value(1));
        call(adminToken, get(path + "/rooms"), null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void openingHoursRejectInvalidSchedules() throws Exception {
        long building = building(site("S1"), "B1", "Building");
        String path = ADMIN + "/buildings/" + building + "/opening-hours";
        call(superToken, put(path), body("hours", List.of(body("dayOfWeek", 8, "opensAt", "08:00", "closesAt", "17:00", "closed", false)))).andExpect(status().isBadRequest());
        call(superToken, put(path), body("hours", List.of(body("dayOfWeek", 1, "opensAt", "22:00", "closesAt", "02:00", "closed", false)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_OPENING_HOURS"));
        call(superToken, put(path), body("hours", List.of(body("dayOfWeek", 1, "opensAt", "08:00", "closesAt", "17:00", "closed", true)))).andExpect(status().isBadRequest());
        call(superToken, put(path), body("hours", List.of(body("dayOfWeek", 1, "opensAt", "08:00", "closesAt", null, "closed", false)))).andExpect(status().isBadRequest());
        var repeated = body("dayOfWeek", 1, "opensAt", "08:00", "closesAt", "17:00", "closed", false);
        call(superToken, put(path), body("hours", List.of(repeated, repeated))).andExpect(status().isBadRequest());
        call(superToken, put(path), body("hours", List.of(body("dayOfWeek", 1, "opensAt", "8am", "closesAt", "17:00", "closed", false)))).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT count(*) FROM campus_opening_hours", Long.class)).isZero();
    }

    @Test
    void roomCrudFiltersFacilitiesAndStatistics() throws Exception {
        long site = site("S1");
        long building = building(site, "B1", "Science");
        long other = building(site, "B2", "Arts");
        long wifi = facility("WIFI", "Wi-Fi"), projector = facility("PROJECTOR", "Projector");
        long retired = facility("RETIRED", "Retired");
        db.update("UPDATE campus_facilities SET is_active=FALSE WHERE id=?", retired);
        var created = call(adminToken, post(ADMIN + "/rooms"), body("buildingId", building, "roomNumber", "bld-201", "roomName", "Computer Lab A", "floorNumber", 2,
                "roomType", "COMPUTER_LAB", "capacity", 40, "status", "AVAILABLE", "facilityIds", List.of(wifi, projector)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value("BLD-201"))
                .andExpect(jsonPath("$.buildingName").value("Science"))
                .andExpect(jsonPath("$.facilities[*].name", contains("Projector", "Wi-Fi"))).andReturn();
        long id = tree(created).get("id").asLong();
        String path = ADMIN + "/rooms/" + id;
        Map<String, Object> base = body("buildingId", building, "roomNumber", "R2", "floorNumber", 1, "roomType", "CLASSROOM");
        call(adminToken, post(ADMIN + "/rooms"), with(base, "roomNumber", "BLD-201")).andExpect(status().isConflict());
        call(adminToken, post(ADMIN + "/rooms"), with(base, "buildingId", other, "roomNumber", "BLD-201")).andExpect(status().isCreated());
        call(adminToken, post(ADMIN + "/rooms"), with(base, "floorNumber", 6)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_FLOOR"));
        call(adminToken, post(ADMIN + "/rooms"), with(base, "floorNumber", -6)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_FLOOR"));
        call(adminToken, post(ADMIN + "/rooms"), with(base, "capacity", 0)).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/rooms"), with(base, "roomType", "SAUNA")).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/rooms"), with(base, "facilityIds", List.of(wifi, wifi))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_FACILITY"));
        call(adminToken, post(ADMIN + "/rooms"), with(base, "facilityIds", List.of(999999))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_FACILITY"));
        call(adminToken, post(ADMIN + "/rooms"), with(base, "facilityIds", List.of(retired))).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/rooms"), with(base, "buildingId", 999999)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_BUILDING"));
        call(adminToken, post(ADMIN + "/rooms"), with(base, "floorNumber", -1, "roomNumber", "BASE-1")).andExpect(status().isCreated());

        call(adminToken, put(path), body("buildingId", building, "roomNumber", "BLD-201", "floorNumber", 3, "roomType", "LABORATORY", "facilityIds", List.of(wifi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorNumber").value(3))
                .andExpect(jsonPath("$.facilities", hasSize(1)))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        call(adminToken, patch(path + "/status"), body("status", "MAINTENANCE")).andExpect(jsonPath("$.status").value("MAINTENANCE"));

        call(adminToken, get(ADMIN + "/rooms?buildingId=" + building + "&floorNumber=3&roomType=LABORATORY&status=MAINTENANCE"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].facilities[0].name").value("Wi-Fi"));
        call(adminToken, get(ADMIN + "/rooms?floorNumber=-1"), null).andExpect(jsonPath("$.totalElements").value(1));
        call(adminToken, get(ADMIN + "/rooms?search=Science"), null).andExpect(jsonPath("$.totalElements").value(2));
        call(adminToken, get(ADMIN + "/rooms/statistics"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRooms").value(3))
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.maintenance").value(1))
                .andExpect(jsonPath("$.basis").value("DECLARED_STATUS"));
        call(adminToken, get(ADMIN + "/rooms/facilities"), null).andExpect(jsonPath("$", hasSize(2)));
        // A building cannot shrink below a room that already exists.
        call(adminToken, put(ADMIN + "/buildings/" + building), body("buildingCode", "B1", "buildingName", "Science", "totalFloors", 2)).andExpect(status().isConflict());
        call(adminToken, delete(path), null).andExpect(status().isNoContent());
        call(adminToken, get(path), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_ROOM_NOT_FOUND"));
        // The soft-deleted row and its facility links remain.
        assertThat(db.queryForObject("SELECT count(*) FROM campus_rooms WHERE id=? AND is_deleted", Long.class, id)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM campus_room_facilities WHERE room_id=?", Long.class, id)).isOne();
    }

    @Test
    void placeCrudVisibilityPopularityAndStatistics() throws Exception {
        long site = site("S1"), otherSite = site("S2");
        long library = category("LIBRARY", "Library", "LIBRARY");
        long building = building(site, "B1", "Library Building"), foreign = building(otherSite, "B9", "Foreign");
        long wifi = facility("WIFI", "Wi-Fi");
        var created = call(adminToken, post(ADMIN + "/places"), body("siteId", site, "categoryId", library, "buildingId", building, "placeCode", "lib-01", "placeName", "University Library",
                "description", "Study", "floorLabel", "1F–5F", "phoneNumber", "+66 2 000 0000", "isPopular", true, "isVisibleOnMobile", true, "status", "ACTIVE", "facilityIds", List.of(wifi)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeCode").value("LIB-01"))
                .andExpect(jsonPath("$.isPopular").value(true))
                .andExpect(jsonPath("$.isVisibleOnMobile").value(true))
                .andExpect(jsonPath("$.category.mobileGroup").value("LIBRARY"))
                .andExpect(jsonPath("$.building.name").value("Library Building"))
                .andExpect(jsonPath("$.facilities[0].name").value("Wi-Fi")).andReturn();
        long id = tree(created).get("id").asLong();
        String path = ADMIN + "/places/" + id;
        Map<String, Object> base = body("siteId", site, "categoryId", library, "placeCode", "P2", "placeName", "Second");
        call(adminToken, post(ADMIN + "/places"), with(base, "placeCode", "LIB-01")).andExpect(status().isConflict());
        call(adminToken, post(ADMIN + "/places"), with(base, "buildingId", foreign)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_BUILDING"));
        call(adminToken, post(ADMIN + "/places"), with(base, "categoryId", 999999)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_CATEGORY"));
        call(adminToken, post(ADMIN + "/places"), with(base, "phoneNumber", "call me")).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/places"), with(base, "latitude", 1.0)).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/places"), base).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPopular").value(false))
                .andExpect(jsonPath("$.isVisibleOnMobile").value(true))
                .andExpect(jsonPath("$.building").value(nullValue()));

        call(adminToken, patch(path + "/visibility"), body("isVisibleOnMobile", false)).andExpect(jsonPath("$.isVisibleOnMobile").value(false));
        call(adminToken, patch(path + "/popular"), body("isPopular", false)).andExpect(jsonPath("$.isPopular").value(false));
        call(adminToken, patch(path + "/status"), body("status", "TEMPORARILY_CLOSED")).andExpect(jsonPath("$.status").value("TEMPORARILY_CLOSED"));
        call(adminToken, put(path + "/facilities"), body("facilityIds", List.of())).andExpect(jsonPath("$.facilities", hasSize(0)));
        call(adminToken, put(path), body("categoryId", library, "placeCode", "LIB-01", "placeName", "Renamed Library")).andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("Renamed Library")).andExpect(jsonPath("$.building").value(nullValue()));

        // The second place is open at noon on Monday, with published hours.
        long second = tree(call(adminToken, get(ADMIN + "/places?search=second"), null).andExpect(jsonPath("$.totalElements").value(1)).andReturn()).get("content").get(0).get("id").asLong();
        call(adminToken, put(ADMIN + "/places/" + second + "/opening-hours"), body("hours", List.of(body("dayOfWeek", 1, "opensAt", "08:00", "closesAt", "17:00", "closed", false)))).andExpect(status().isOk());
        call(adminToken, get(ADMIN + "/places/statistics"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlaces").value(2))
                .andExpect(jsonPath("$.activePlaces").value(1))
                .andExpect(jsonPath("$.temporarilyClosedPlaces").value(1))
                .andExpect(jsonPath("$.expectedOpenNow").value(1))
                .andExpect(jsonPath("$.activePlacesWithoutPublishedHours").value(0));
        // A holiday exception removes it from expected-open-now, while its ACTIVE status count is unchanged.
        call(adminToken, put(ADMIN + "/places/" + second + "/opening-exceptions"), body("exceptions", List.of(body("date", MONDAY.toString(), "closed", true)))).andExpect(status().isOk());
        call(adminToken, get(ADMIN + "/places/statistics"), null).andExpect(jsonPath("$.activePlaces").value(1)).andExpect(jsonPath("$.expectedOpenNow").value(0));
        call(adminToken, get(ADMIN + "/places?mobileGroup=LIBRARY&isPopular=false&isVisibleOnMobile=false"), null).andExpect(jsonPath("$.totalElements").value(1));
        call(adminToken, delete(path), null).andExpect(status().isNoContent());
        call(adminToken, get(path), null).andExpect(status().isNotFound());
    }

    @Test
    void catalogDependencyChecks() throws Exception {
        long site = site("S1");
        var category = tree(call(adminToken, post(ADMIN + "/categories"), body("categoryCode", "cafeteria", "categoryName", "Cafeteria", "mobileGroup", "FOOD")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryCode").value("CAFETERIA")).andReturn()).get("id").asLong();
        call(adminToken, post(ADMIN + "/categories"), body("categoryCode", "X", "categoryName", "X", "mobileGroup", "ALL")).andExpect(status().isBadRequest());
        call(adminToken, post(ADMIN + "/categories"), body("categoryCode", "CAFETERIA", "categoryName", "Dup", "mobileGroup", "FOOD")).andExpect(status().isConflict());
        long facility = tree(call(adminToken, post(ADMIN + "/facilities"), body("facilityCode", "wifi", "facilityName", "Wi-Fi")).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long place = place(site, category, null, "P1", "Canteen");
        db.update("INSERT INTO campus_place_facilities(place_id,facility_id) VALUES (?,?)", place, facility);
        call(adminToken, put(ADMIN + "/categories/" + category), body("categoryCode", "CAFETERIA", "categoryName", "Cafeteria", "mobileGroup", "FOOD", "isActive", false)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPUS_DEPENDENCY_EXISTS"));
        call(adminToken, put(ADMIN + "/facilities/" + facility), body("facilityCode", "WIFI", "facilityName", "Wi-Fi", "isActive", false)).andExpect(status().isConflict());
        call(adminToken, put(ADMIN + "/categories/" + category), body("categoryCode", "CAFETERIA", "categoryName", "Canteens", "mobileGroup", "FOOD")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Canteens")).andExpect(jsonPath("$.updatedBy").isNumber());
        db.update("UPDATE campus_places SET is_deleted=TRUE WHERE id=?", place);
        call(adminToken, put(ADMIN + "/categories/" + category), body("categoryCode", "CAFETERIA", "categoryName", "Canteens", "mobileGroup", "FOOD", "isActive", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        call(adminToken, put(ADMIN + "/facilities/" + facility), body("facilityCode", "WIFI", "facilityName", "Wi-Fi", "isActive", false)).andExpect(status().isOk());
        call(adminToken, get(ADMIN + "/rooms/facilities"), null).andExpect(jsonPath("$", hasSize(0)));
        call(adminToken, get(ADMIN + "/facilities"), null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void buildingDeleteIsRefusedWhileAnythingDependsOnIt() throws Exception {
        long site = site("S1");
        long building = building(site, "B1", "Building");
        long room = room(building, "101", 1);
        String path = ADMIN + "/buildings/" + building;
        call(superToken, delete(path), null).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("The building still has rooms."));
        db.update("UPDATE campus_rooms SET is_deleted=TRUE WHERE id=?", room);
        long place = place(site, category("C", "C", "OTHER"), building, "P1", "Place");
        call(superToken, delete(path), null).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("The building still has campus places."));
        db.update("UPDATE campus_places SET is_deleted=TRUE WHERE id=?", place);
        long node = node(site, "N1", 10, 20);
        entrance(node, building, null, true);
        call(superToken, delete(path), null).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("The building is still mapped to navigation nodes."));
        db.update("DELETE FROM campus_nav_destinations");
        call(superToken, delete(path), null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM campus_rooms WHERE building_id=?", Long.class, building)).as("rooms are kept").isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM campus_places WHERE building_id=?", Long.class, building)).as("places are kept").isOne();
        call(superToken, get(path), null).andExpect(status().isNotFound());
    }

    @Test
    void navigationNodeCrudAndDependencies() throws Exception {
        long site = site("S1");
        var created = call(superToken, post(ADMIN + "/navigation/nodes"), body("siteId", site, "nodeCode", "jct-a", "nodeName", "Junction A", "nodeType", "JUNCTION",
                "latitude", 13.0000001, "longitude", 100.0000002, "isAccessible", true, "isActive", true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeCode").value("JCT-A"))
                .andExpect(jsonPath("$.isAccessible").value(true))
                .andExpect(jsonPath("$.latitude").value(13.0000001)).andReturn();
        long a = tree(created).get("id").asLong();
        Map<String, Object> base = body("siteId", site, "nodeCode", "N2", "nodeName", "N2", "nodeType", "GATE", "latitude", 13.1, "longitude", 100.1);
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "nodeCode", "JCT-A")).andExpect(status().isConflict());
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "latitude", 91)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "longitude", -181)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "latitude", null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "latitude", 13.12345678)).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/nodes"), with(base, "nodeType", "PORTAL")).andExpect(status().isBadRequest());
        long b = tree(call(superToken, post(ADMIN + "/navigation/nodes"), base).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        call(superToken, put(ADMIN + "/navigation/nodes/" + b), body("nodeCode", "N2", "nodeName", "Renamed", "nodeType", "GATE", "latitude", 13.2, "longitude", 100.2, "isActive", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false)).andExpect(jsonPath("$.nodeName").value("Renamed"));
        call(superToken, get(ADMIN + "/navigation/nodes?siteId=" + site + "&isActive=false"), null).andExpect(jsonPath("$.totalElements").value(1));

        long edge = edge(site, "E1", a, b, 100);
        call(superToken, delete(ADMIN + "/navigation/nodes/" + a), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPUS_DEPENDENCY_EXISTS"));
        db.update("UPDATE campus_nav_edges SET is_deleted=TRUE WHERE id=?", edge);
        entrance(a, building(site, "B1", "B"), null, true);
        call(superToken, delete(ADMIN + "/navigation/nodes/" + a), null).andExpect(status().isConflict());
        db.update("DELETE FROM campus_nav_destinations");
        call(superToken, delete(ADMIN + "/navigation/nodes/" + a), null).andExpect(status().isNoContent());
        call(superToken, get(ADMIN + "/navigation/nodes/" + a), null).andExpect(status().isNotFound());
    }

    @Test
    void navigationEdgeCrudAndGeometry() throws Exception {
        long site = site("S1"), otherSite = site("S2");
        long a = node(site, "A", 10.000, 20), b = node(site, "B", 10.001, 20), foreign = node(otherSite, "F", 10.002, 20);
        var created = call(superToken, post(ADMIN + "/navigation/edges"), body("siteId", site, "edgeCode", "r01", "startNodeId", a, "endNodeId", b, "distanceMeters", 180, "surfaceType", "COVERED_WALKWAY",
                "isWheelchairAccessible", true, "isBidirectional", true, "status", "ACTIVE",
                "intermediatePoints", List.of(body("latitude", 10.0003, "longitude", 20.0001), body("latitude", 10.0007, "longitude", 20.0001))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.edgeCode").value("R01"))
                .andExpect(jsonPath("$.startNode.id").value(a))
                .andExpect(jsonPath("$.walkTimeMinutes").value(3))
                .andExpect(jsonPath("$.hasGeometry").value(true))
                .andExpect(jsonPath("$.intermediatePoints", hasSize(2))).andReturn();
        long id = tree(created).get("id").asLong();
        Map<String, Object> base = body("siteId", site, "edgeCode", "R02", "startNodeId", a, "endNodeId", b, "distanceMeters", 10, "surfaceType", "PAVED");
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "edgeCode", "R01")).andExpect(status().isConflict());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "endNodeId", a)).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "endNodeId", foreign)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_NODE"));
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "endNodeId", 999999)).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "distanceMeters", 0)).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "distanceMeters", -5)).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "surfaceType", "LAVA")).andExpect(status().isBadRequest());
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "surfaceType", "STAIRS", "isWheelchairAccessible", true)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_ACCESSIBILITY"));
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "intermediatePoints", List.of(body("latitude", 95, "longitude", 20)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPUS_INVALID_COORDINATES"));
        db.update("UPDATE campus_nav_nodes SET is_accessible=FALSE WHERE id=?", b);
        call(superToken, post(ADMIN + "/navigation/edges"), with(base, "isWheelchairAccessible", true)).andExpect(status().isBadRequest());
        db.update("UPDATE campus_nav_nodes SET is_accessible=TRUE WHERE id=?", b);

        call(superToken, put(ADMIN + "/navigation/edges/" + id), body("edgeCode", "R01", "startNodeId", b, "endNodeId", a, "distanceMeters", 200.5, "surfaceType", "PAVED",
                "intermediatePoints", List.of(body("latitude", 10.0005, "longitude", 20.0002)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(200.5))
                .andExpect(jsonPath("$.startNode.id").value(b))
                .andExpect(jsonPath("$.isWheelchairAccessible").value(true))
                .andExpect(jsonPath("$.intermediatePoints", hasSize(1)));
        assertThat(db.queryForObject("SELECT count(*) FROM campus_nav_edge_points WHERE edge_id=?", Long.class, id)).isOne();
        call(superToken, patch(ADMIN + "/navigation/edges/" + id + "/status"), body("status", "UNDER_CONSTRUCTION")).andExpect(jsonPath("$.status").value("UNDER_CONSTRUCTION"));
        edge(site, "E9", a, b, 5);
        call(superToken, get(ADMIN + "/navigation/edges/statistics?siteId=" + site), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNodes").value(2))
                .andExpect(jsonPath("$.totalEdges").value(2))
                .andExpect(jsonPath("$.activeEdges").value(1))
                .andExpect(jsonPath("$.underConstructionEdges").value(1));
        call(superToken, get(ADMIN + "/navigation/edges?status=UNDER_CONSTRUCTION"), null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].intermediatePoints", hasSize(1)));
        call(superToken, delete(ADMIN + "/navigation/edges/" + id), null).andExpect(status().isNoContent());
        call(superToken, get(ADMIN + "/navigation/edges/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void destinationMappingRules() throws Exception {
        long site = site("S1"), otherSite = site("S2");
        long a = node(site, "A", 10, 20), b = node(site, "B", 10.001, 20), foreign = node(otherSite, "F", 11, 20);
        long building = building(site, "B1", "Building");
        long place = place(site, category("C", "C", "OTHER"), building, "P1", "Place");
        String path = ADMIN + "/navigation/destinations";
        call(superToken, post(path), body("nodeId", a)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_DESTINATION"));
        call(superToken, post(path), body("nodeId", a, "buildingId", building, "placeId", place)).andExpect(status().isBadRequest());
        call(superToken, post(path), body("nodeId", foreign, "placeId", place)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAMPUS_INVALID_DESTINATION"));
        long id = tree(call(superToken, post(path), body("nodeId", a, "buildingId", null, "placeId", place, "isPrimary", true, "isAccessibleEntrance", true)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.place.name").value("Place"))
                .andExpect(jsonPath("$.building").value(nullValue()))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.createdBy").value(superAdminId)).andReturn()).get("id").asLong();
        call(superToken, post(path), body("nodeId", a, "placeId", place)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPUS_DUPLICATE_DESTINATION"));
        call(superToken, post(path), body("nodeId", b, "placeId", place, "isPrimary", true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CAMPUS_PRIMARY_DESTINATION_EXISTS"));
        call(superToken, post(path), body("nodeId", b, "placeId", place)).andExpect(status().isCreated()).andExpect(jsonPath("$.isPrimary").value(false));
        call(superToken, put(path + "/" + id), body("nodeId", a, "placeId", place, "isPrimary", false, "isAccessibleEntrance", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isAccessibleEntrance").value(false)).andExpect(jsonPath("$.updatedBy").value(superAdminId));
        call(superToken, get(path + "?placeId=" + place), null).andExpect(jsonPath("$", hasSize(2)));
        call(superToken, get(path + "?siteId=" + otherSite), null).andExpect(jsonPath("$", hasSize(0)));
        call(superToken, delete(ADMIN + "/places/" + place), null).andExpect(status().isConflict());
        call(superToken, delete(path + "/" + id), null).andExpect(status().isNoContent());
        call(superToken, delete(path + "/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void graphValidationReportsEachProblem() throws Exception {
        long site = site("S1"), otherSite = site("S2");
        long a = node(site, "A", 10, 20), b = node(site, "B", 10.001, 20), c = node(site, "C", 10.002, 20), lonely = node(site, "L", 10.003, 20), zero = node(site, "Z", 0, 0);
        long foreign = node(otherSite, "F", 11, 20);
        edge(site, "E1", a, b, 100);
        edge(site, "E2", b, c, 100);
        long closed = edge(site, "E3", b, lonely, 100);
        db.update("UPDATE campus_nav_edges SET status='UNDER_CONSTRUCTION' WHERE id=?", closed);
        long cross = edge(site, "X1", a, foreign, 100);
        long mapped = building(site, "B1", "Mapped");
        long stranded = building(site, "B2", "Stranded");
        long missing = building(site, "B3", "Missing");
        long unmappedPlace = place(site, category("C", "C", "OTHER"), null, "P1", "Unmapped Place");
        entrance(c, mapped, null, true);
        entrance(lonely, stranded, null, true);
        var report = call(superToken, get(ADMIN + "/navigation/validation?siteId=" + site), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.disconnectedNodes[*].code", hasItems("L", "Z")))
                .andExpect(jsonPath("$.unreachableDestinations[*].code", contains("B2")))
                .andExpect(jsonPath("$.invalidCoordinates[*].code", contains("Z")))
                .andExpect(jsonPath("$.inactiveEdges[*].code", contains("E3")))
                .andExpect(jsonPath("$.missingPrimaryEntrances[*].code", containsInAnyOrder("B3", "P1")))
                .andExpect(jsonPath("$.missingRouteGeometry[*].code", hasItems("E1", "E2")))
                .andExpect(jsonPath("$.crossCampusErrors[*].code", contains("X1"))).andReturn();
        assertThat(tree(report).get("summary").get("blockingIssues").asInt()).isPositive();
        assertThat(missing + unmappedPlace + cross).isPositive();
        // Fixing it all gives a clean report, with warnings only.
        db.update("DELETE FROM campus_nav_destinations");
        db.update("DELETE FROM campus_nav_edges WHERE id IN (?,?)", cross, closed);
        db.update("UPDATE campus_nav_nodes SET is_active=FALSE WHERE id IN (?,?)", lonely, zero);
        db.update("UPDATE campus_nav_nodes SET is_deleted=TRUE WHERE id=?", zero);
        db.update("UPDATE campus_nav_nodes SET is_deleted=TRUE WHERE id=?", lonely);
        db.update("UPDATE campus_buildings SET status='INACTIVE' WHERE id IN (?,?)", stranded, missing);
        db.update("UPDATE campus_places SET status='INACTIVE' WHERE id=?", unmappedPlace);
        entrance(c, mapped, null, true);
        call(superToken, get(ADMIN + "/navigation/validation?siteId=" + site), null).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.summary.blockingIssues").value(0))
                .andExpect(jsonPath("$.missingRouteGeometry", hasSize(2)));
        call(superToken, get(ADMIN + "/navigation/validation"), null).andExpect(status().isBadRequest());
        call(superToken, get(ADMIN + "/navigation/validation?siteId=999999"), null).andExpect(status().isNotFound());
    }

    @Test
    void adminMapShowsLayersAndNeverInventsCoordinates() throws Exception {
        long site = site("S1");
        long a = node(site, "A", 10, 20), b = node(site, "B", 10.001, 20);
        long edge = edge(site, "E1", a, b, 100);
        db.update("INSERT INTO campus_nav_edge_points(edge_id,point_sequence,latitude,longitude) VALUES (?,0,10.0005,20.0001)", edge);
        long mapped = building(site, "B1", "Mapped");
        long unmapped = building(site, "B2", "Unmapped");
        long library = category("LIB", "Library", "LIBRARY"), food = category("FOOD", "Food", "FOOD");
        long place = place(site, library, mapped, "P1", "Library");
        place(site, food, null, "P2", "Canteen");
        db.update("UPDATE campus_buildings SET status='UNDER_RENOVATION' WHERE id=?", unmapped);
        entrance(b, mapped, null, true);
        call(academicToken, get(ADMIN + "/map?siteId=" + site), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.site.name").value("S1 Campus"))
                .andExpect(jsonPath("$.buildings", hasSize(2)))
                .andExpect(jsonPath("$.buildings[0].entityType").value("BUILDING"))
                .andExpect(jsonPath("$.buildings[?(@.code=='B1')].latitude", contains(10.001)))
                .andExpect(jsonPath("$.buildings[?(@.code=='B1')].canNavigate", contains(true)))
                .andExpect(jsonPath("$.buildings[?(@.code=='B2')].latitude", contains(nullValue())))
                .andExpect(jsonPath("$.places[?(@.code=='P1')].latitude", contains(10.001)))
                .andExpect(jsonPath("$.places[?(@.code=='P2')].canNavigate", contains(false)))
                .andExpect(jsonPath("$.navigationNodes", hasSize(2)))
                .andExpect(jsonPath("$.navigationEdges[0].geometry", hasSize(3)))
                .andExpect(jsonPath("$.summary.totalLocations").value(4))
                .andExpect(jsonPath("$.summary.activeLocations").value(3))
                .andExpect(jsonPath("$.summary.inactiveLocations").value(1))
                .andExpect(jsonPath("$.summary.locationsWithoutCoordinates").value(2));
        call(academicToken, get(ADMIN + "/map?siteId=" + site + "&includeBuildings=false&includeEdges=false&includeNodes=false&categoryId=" + library), null)
                .andExpect(jsonPath("$.buildings", hasSize(0)))
                .andExpect(jsonPath("$.navigationNodes", hasSize(0)))
                .andExpect(jsonPath("$.navigationEdges", hasSize(0)))
                .andExpect(jsonPath("$.places", hasSize(1)))
                .andExpect(jsonPath("$.places[0].id").value(place));
        call(academicToken, get(ADMIN + "/map?siteId=" + site + "&status=UNDER_RENOVATION"), null).andExpect(jsonPath("$.buildings", hasSize(1))).andExpect(jsonPath("$.places", hasSize(0)));
        call(academicToken, get(ADMIN + "/map?siteId=" + site + "&status=NOPE"), null).andExpect(status().isBadRequest());
    }

    @Test
    void authorizationFollowsSection56() throws Exception {
        long site = site("S1");
        long building = building(site, "B1", "Building");
        long node = node(site, "N", 10, 20);
        var building2 = body("siteId", site, "buildingCode", "B2", "buildingName", "Two", "totalFloors", 1);
        var nodeBody = body("siteId", site, "nodeCode", "N2", "nodeName", "N2", "nodeType", "GATE", "latitude", 1, "longitude", 1);

        // No token, and a student token, cannot reach the admin API at all.
        call(null, get(ADMIN + "/buildings"), null).andExpect(status().isUnauthorized());
        call(studentToken, get(ADMIN + "/buildings"), null).andExpect(status().isForbidden());
        call(studentToken, post(ADMIN + "/buildings"), building2).andExpect(status().isForbidden());

        // ACADEMIC_ADMIN reads everything and writes nothing.
        for (String path : List.of("/sites", "/buildings", "/rooms", "/places", "/categories", "/facilities", "/map", "/navigation/nodes", "/navigation/edges", "/navigation/destinations", "/navigation/validation"))
            call(academicToken, get(ADMIN + path), null).andExpect(status().isOk());
        call(academicToken, post(ADMIN + "/buildings"), building2).andExpect(status().isForbidden());
        call(academicToken, delete(ADMIN + "/buildings/" + building), null).andExpect(status().isForbidden());
        call(academicToken, post(ADMIN + "/categories"), body("categoryCode", "X", "categoryName", "X", "mobileGroup", "FOOD")).andExpect(status().isForbidden());

        // ADMIN manages buildings but not sites or navigation infrastructure.
        call(adminToken, post(ADMIN + "/buildings"), building2).andExpect(status().isCreated());
        call(adminToken, post(ADMIN + "/navigation/nodes"), nodeBody).andExpect(status().isForbidden());
        call(adminToken, delete(ADMIN + "/navigation/nodes/" + node), null).andExpect(status().isForbidden());
        call(adminToken, post(ADMIN + "/navigation/destinations"), body("nodeId", node, "buildingId", building)).andExpect(status().isForbidden());
        call(adminToken, post(ADMIN + "/sites"), body("siteCode", "S9", "siteName", "S9")).andExpect(status().isForbidden());
        call(adminToken, get(ADMIN + "/navigation/nodes"), null).andExpect(status().isOk());

        // SUPER_ADMIN does both.
        call(superToken, post(ADMIN + "/navigation/nodes"), nodeBody).andExpect(status().isCreated());
        call(superToken, post(ADMIN + "/sites"), body("siteCode", "S9", "siteName", "S9")).andExpect(status().isCreated());

        // A forced password change blocks campus access.
        long forced = admin("campus.forced@example.test", "SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", forced);
        call(adminLogin("campus.forced@example.test"), get(ADMIN + "/buildings"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    private Map<String, Object> with(Map<String, Object> base, Object... overrides) {
        var copy = new LinkedHashMap<>(base);
        for (int i = 0; i < overrides.length; i += 2) copy.put((String) overrides[i], overrides[i + 1]);
        return copy;
    }
}
