package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.*;

/** Section 63 of spec file 41: what a student can read, and that hidden data stays hidden. */
class CampusStudentIntegrationTests extends CampusIntegrationSupport {
    private long site, science, libraryBuilding, library, canteen, registrar, clinic, parking, atm;

    @BeforeEach
    void campus() {
        site = site("S1");
        science = building(site, "SCI", "Science Building");
        libraryBuilding = building(site, "LIBB", "Library Hall");
        library = place(site, category("LIB", "Library", "LIBRARY"), libraryBuilding, "LIB-01", "University Library");
        canteen = place(site, category("CAF", "Cafeteria", "FOOD"), null, "CAF-01", "Main Canteen");
        registrar = place(site, category("REG", "Registrar", "STUDENT_SERVICES"), null, "REG-01", "Registrar Office");
        clinic = place(site, category("HLT", "Health Center", "HEALTH"), null, "HLT-01", "Health Center");
        parking = place(site, category("PRK", "Parking", "PARKING"), null, "PRK-01", "North Parking");
        atm = place(site, category("ATM", "ATM", "OTHER"), null, "ATM-01", "Campus ATM");
    }

    @Test
    void everyEndpointRequiresAStudentToken() throws Exception {
        for (String path : List.of("/sites", "/categories", "/locations", "/places/1", "/buildings/1", "/buildings/1/rooms", "/rooms/1", "/map")) {
            call(null, get(STUDENT + path), null).andExpect(status().isUnauthorized());
            call(superToken, get(STUDENT + path), null).andExpect(status().isForbidden());
        }
        call(null, post(STUDENT + "/nearby"), body("latitude", 1, "longitude", 1)).andExpect(status().isUnauthorized());
        call(superToken, post(STUDENT + "/routes/preview"), body("destination", body("type", "PLACE", "id", library))).andExpect(status().isForbidden());
        student("CAMPUS-002", true);
        call(studentLogin("CAMPUS-002"), get(STUDENT + "/sites"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void aStudentCannotUseAnyMutationHere() throws Exception {
        call(studentToken, post(STUDENT + "/buildings"), body("x", 1)).andExpect(status().isNotFound());
        call(studentToken, put(STUDENT + "/buildings/" + science), body("x", 1)).andExpect(status().isMethodNotAllowed());
        call(studentToken, delete(STUDENT + "/places/" + library), null).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void suspendedStudentIsRejected() throws Exception {
        db.update("UPDATE students SET account_status='SUSPENDED' WHERE id=?", studentId);
        call(studentToken, get(STUDENT + "/sites"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void sitesAndCategoryChips() throws Exception {
        call(studentToken, get(STUDENT + "/sites"), null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("S1"))
                .andExpect(jsonPath("$[0].createdBy").doesNotExist());
        call(studentToken, get(STUDENT + "/categories"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("ALL", "BUILDINGS", "FOOD", "LIBRARY", "STUDENT_SERVICES", "HEALTH", "PARKING")))
                .andExpect(jsonPath("$[4].name").value("Student Services"));
        db.update("UPDATE campus_categories SET is_active=FALSE WHERE mobile_group='HEALTH'");
        db.update("UPDATE campus_buildings SET status='INACTIVE'");
        call(studentToken, get(STUDENT + "/categories"), null).andExpect(jsonPath("$[*].code", contains("ALL", "FOOD", "LIBRARY", "STUDENT_SERVICES", "PARKING")));
    }

    @Test
    void allLocationsAndEachFilter() throws Exception {
        call(studentToken, get(STUDENT + "/locations"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.items", hasSize(8)))
                .andExpect(jsonPath("$.items[0].name").value("Campus ATM"))
                .andExpect(jsonPath("$.items[0].latitude").value(nullValue()))
                .andExpect(jsonPath("$.items[0].canNavigate").value(false));
        expectGroup("ALL", 8);
        expectGroupItem("BUILDINGS", "Library Hall", "Science Building");
        expectGroupItem("FOOD", "Main Canteen");
        expectGroupItem("LIBRARY", "University Library");
        expectGroupItem("STUDENT_SERVICES", "Registrar Office");
        expectGroupItem("HEALTH", "Health Center");
        expectGroupItem("PARKING", "North Parking");
        expectGroupItem("OTHER", "Campus ATM");
        call(studentToken, get(STUDENT + "/locations?group=MADE_UP"), null).andExpect(status().isBadRequest());
        call(studentToken, get(STUDENT + "/locations?limit=0"), null).andExpect(status().isBadRequest());
        call(studentToken, get(STUDENT + "/locations?limit=101"), null).andExpect(status().isBadRequest());
        call(studentToken, get(STUDENT + "/locations?siteId=999999"), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_NOT_FOUND"));
    }

    @Test
    void searchIsCaseInsensitiveTrimmedAndLiteral() throws Exception {
        expectSearch("  LIBRARY ", "Library Hall", "University Library");
        expectSearch("reg-01", "Registrar Office");
        expectSearch("sci", "Science Building");
        expectSearch("cafeteria", "Main Canteen");
        expectSearch("computer science");
        // Wildcards and SQL are plain text.
        expectSearch("%");
        expectSearch("_");
        expectSearch("' OR 1=1 --");
        call(studentToken, get(STUDENT + "/locations?search=" + "x".repeat(201)), null).andExpect(status().isBadRequest());
    }

    @Test
    void cursorWalksThePagesWithoutRepeats() throws Exception {
        var seen = new java.util.ArrayList<String>();
        String cursor = null;
        do {
            var page = tree(call(studentToken, get(STUDENT + "/locations?limit=3" + (cursor == null ? "" : "&cursor=" + cursor)), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(8)).andReturn());
            page.get("items").forEach(item -> seen.add(item.get("name").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
        } while (cursor != null);
        org.assertj.core.api.Assertions.assertThat(seen).hasSize(8).doesNotHaveDuplicates()
                .containsExactly("Campus ATM", "Health Center", "Library Hall", "Main Canteen", "North Parking", "Registrar Office", "Science Building", "University Library");
        call(studentToken, get(STUDENT + "/locations?cursor=not-a-cursor!"), null).andExpect(status().isBadRequest());
    }

    @Test
    void aBuildingAndItsSameNamedPlaceAppearOnce() throws Exception {
        db.update("UPDATE campus_buildings SET building_name='University Library' WHERE id=?", libraryBuilding);
        call(studentToken, get(STUDENT + "/locations"), null).andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.items[?(@.name=='University Library')].locationType", contains("PLACE")));
        // In the Buildings filter the place is not listed, so the building is the one row.
        call(studentToken, get(STUDENT + "/locations?group=BUILDINGS"), null).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void deletedInactiveAndUnpublishedRecordsAreExcluded() throws Exception {
        long room = room(science, "101", 1);
        db.update("UPDATE campus_places SET is_visible_on_mobile=FALSE WHERE id=?", canteen);
        db.update("UPDATE campus_places SET is_deleted=TRUE WHERE id=?", registrar);
        db.update("UPDATE campus_places SET status='INACTIVE' WHERE id=?", clinic);
        db.update("UPDATE campus_buildings SET status='INACTIVE' WHERE id=?", libraryBuilding);
        call(studentToken, get(STUDENT + "/locations"), null).andExpect(jsonPath("$.items[*].name", contains("Campus ATM", "North Parking", "Science Building", "University Library")));
        for (long hidden : List.of(canteen, registrar, clinic))
            call(studentToken, get(STUDENT + "/places/" + hidden), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_LOCATION_NOT_FOUND"));
        call(studentToken, get(STUDENT + "/buildings/" + libraryBuilding), null).andExpect(status().isNotFound());
        db.update("UPDATE campus_rooms SET status='INACTIVE' WHERE id=?", room);
        call(studentToken, get(STUDENT + "/rooms/" + room), null).andExpect(status().isNotFound());
        call(studentToken, get(STUDENT + "/buildings/" + science + "/rooms"), null).andExpect(jsonPath("$.total").value(0));
        db.update("UPDATE campus_sites SET is_active=FALSE WHERE id=?", site);
        call(studentToken, get(STUDENT + "/locations"), null).andExpect(jsonPath("$.total").value(0));
        call(studentToken, get(STUDENT + "/places/" + library), null).andExpect(status().isNotFound());
        call(studentToken, get(STUDENT + "/sites"), null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void placeDetailsHoursFacilitiesAndRooms() throws Exception {
        long wifi = facility("WIFI", "Wi-Fi"), print = facility("PRINT", "Printing");
        db.update("INSERT INTO campus_place_facilities(place_id,facility_id) VALUES (?,?),(?,?)", library, wifi, library, print);
        db.update("UPDATE campus_places SET description='Study', floor_label='1F', phone_number='+66 2 000 0000', is_popular=TRUE WHERE id=?", library);
        db.update("INSERT INTO campus_opening_hours(place_id,day_of_week,opens_at,closes_at) VALUES (?,1,'08:00','21:00')", library);
        room(libraryBuilding, "L1", 1);
        call(studentToken, get(STUDENT + "/places/" + library), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LIB-01"))
                .andExpect(jsonPath("$.category").value("Library"))
                .andExpect(jsonPath("$.building.name").value("Library Hall"))
                .andExpect(jsonPath("$.floorLabel").value("1F"))
                .andExpect(jsonPath("$.phoneNumber").value("+66 2 000 0000"))
                .andExpect(jsonPath("$.isPopular").value(true))
                .andExpect(jsonPath("$.openingHours[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.openingHours[0].opensAt").value("08:00"))
                .andExpect(jsonPath("$.openingHours[0].closesAt").value("21:00"))
                .andExpect(jsonPath("$.openingHours[0].closed").value(false))
                .andExpect(jsonPath("$.openingStatus.expectedOpen").value(true))
                .andExpect(jsonPath("$.openingStatus.basis").value("PUBLISHED_HOURS"))
                .andExpect(jsonPath("$.openingStatus.verifiedRealTime").value(false))
                .andExpect(jsonPath("$.facilities", contains("Printing", "Wi-Fi")))
                .andExpect(jsonPath("$.canNavigate").value(false))
                .andExpect(jsonPath("$.canViewRooms").value(true))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.distanceMeters").doesNotExist());
        // A published closure wins over regular hours.
        db.update("INSERT INTO campus_opening_exceptions(place_id,exception_date,is_closed,reason) VALUES (?,?,TRUE,'Holiday')", library, MONDAY);
        call(studentToken, get(STUDENT + "/places/" + library), null).andExpect(jsonPath("$.openingStatus.expectedOpen").value(false))
                .andExpect(jsonPath("$.openingStatus.basis").value("PUBLISHED_EXCEPTION"));
        // A place with no building, or no eligible rooms, cannot offer View Rooms.
        call(studentToken, get(STUDENT + "/places/" + canteen), null).andExpect(jsonPath("$.canViewRooms").value(false))
                .andExpect(jsonPath("$.openingStatus.expectedOpen").value(nullValue()))
                .andExpect(jsonPath("$.openingStatus.basis").value("NOT_PUBLISHED"));
    }

    @Test
    void buildingAndRoomDetails() throws Exception {
        long wifi = facility("WIFI", "Wi-Fi");
        long room = id("INSERT INTO campus_rooms(building_id,room_number,room_name,floor_number,room_type,capacity,status) VALUES (?,'201','Lab',2,'COMPUTER_LAB',40,'OCCUPIED') RETURNING id", science);
        room(science, "101", 1);
        db.update("INSERT INTO campus_room_facilities(room_id,facility_id) VALUES (?,?)", room, wifi);
        long deleted = room(science, "999", 1);
        db.update("UPDATE campus_rooms SET is_deleted=TRUE WHERE id=?", deleted);
        call(studentToken, get(STUDENT + "/buildings/" + science), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCI"))
                .andExpect(jsonPath("$.totalFloors").value(5))
                .andExpect(jsonPath("$.totalRooms").value(2))
                .andExpect(jsonPath("$.rooms[*].roomNumber", contains("101", "201")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.canNavigate").value(false));
        call(studentToken, get(STUDENT + "/buildings/" + science + "/rooms"), null).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[1].declaredStatus").value("OCCUPIED"))
                .andExpect(jsonPath("$.items[1].facilities[0]").value("Wi-Fi"));
        call(studentToken, get(STUDENT + "/rooms/" + room), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("Lab"))
                .andExpect(jsonPath("$.building.name").value("Science Building"))
                .andExpect(jsonPath("$.floorNumber").value(2))
                .andExpect(jsonPath("$.roomType").value("COMPUTER_LAB"))
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.declaredStatus").value("OCCUPIED"))
                .andExpect(jsonPath("$.createdBy").doesNotExist());
        call(studentToken, get(STUDENT + "/rooms/" + deleted), null).andExpect(status().isNotFound());
    }

    @Test
    void adminChangesAppearInStudentReads() throws Exception {
        call(studentToken, get(STUDENT + "/places/" + canteen), null).andExpect(jsonPath("$.name").value("Main Canteen"));
        call(superToken, patch(ADMIN + "/places/" + canteen + "/visibility"), body("isVisibleOnMobile", false)).andExpect(status().isOk());
        call(studentToken, get(STUDENT + "/places/" + canteen), null).andExpect(status().isNotFound());
        call(superToken, patch(ADMIN + "/places/" + canteen + "/visibility"), body("isVisibleOnMobile", true)).andExpect(status().isOk());
        call(superToken, put(ADMIN + "/places/" + canteen), body("categoryId", db.queryForObject("SELECT id FROM campus_categories WHERE category_code='CAF'", Long.class), "placeCode", "CAF-01", "placeName", "Renamed Canteen")).andExpect(status().isOk());
        call(studentToken, get(STUDENT + "/locations?search=renamed"), null).andExpect(jsonPath("$.items[0].name").value("Renamed Canteen"));
    }

    @Test
    void mapOnlyShowsVerifiedMarkers() throws Exception {
        long a = node(site, "A", 10, 20), b = node(site, "B", 10.002, 20.001);
        long edge = edge(site, "E1", a, b, 100);
        long hidden = edge(site, "E2", b, a, 50);
        db.update("UPDATE campus_nav_edges SET status='INACTIVE' WHERE id=?", hidden);
        entrance(a, science, null, true);
        entrance(b, null, library, true);
        call(studentToken, get(STUDENT + "/map"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.site.name").value("S1 Campus"))
                .andExpect(jsonPath("$.bounds.minLatitude").value(10.0))
                .andExpect(jsonPath("$.bounds.maxLongitude").value(20.001))
                .andExpect(jsonPath("$.buildings[*].code", contains("SCI")))
                .andExpect(jsonPath("$.buildings[0].latitude").value(10.0))
                .andExpect(jsonPath("$.places[*].code", contains("LIB-01")))
                .andExpect(jsonPath("$.navigationGraph").value(nullValue()))
                .andExpect(jsonPath("$.buildings[0].createdBy").doesNotExist());
        call(studentToken, get(STUDENT + "/map?siteId=" + site + "&includeNavigationGraph=true"), null)
                .andExpect(jsonPath("$.navigationGraph.nodes", hasSize(2)))
                .andExpect(jsonPath("$.navigationGraph.edges[*].id", contains((int) edge)));
        site("S2");
        call(studentToken, get(STUDENT + "/map"), null).andExpect(status().isBadRequest());
        call(studentToken, get(STUDENT + "/map?siteId=999999"), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAMPUS_NOT_FOUND"));
    }

    private void expectGroup(String group, int total) throws Exception {
        call(studentToken, get(STUDENT + "/locations?group=" + group), null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(total));
    }
    private void expectGroupItem(String group, String... names) throws Exception {
        call(studentToken, get(STUDENT + "/locations?group=" + group), null).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].name", contains(names)));
    }
    private void expectSearch(String search, String... names) throws Exception {
        var request = get(STUDENT + "/locations").param("search", search);
        var result = call(studentToken, request, null).andExpect(status().isOk());
        if (names.length == 0) result.andExpect(jsonPath("$.total").value(0));
        else result.andExpect(jsonPath("$.items[*].name", contains(names)));
    }
}
