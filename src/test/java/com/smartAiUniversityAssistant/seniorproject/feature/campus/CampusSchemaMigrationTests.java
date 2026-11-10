package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.assertj.core.api.Assertions.*;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V33 to V39 on an empty schema: nothing seeded, and the constraints in section 61 of spec file 41. */
class CampusSchemaMigrationTests extends IntegrationSupport {
    private static final String[] TABLES = {"campus_sites", "campus_categories", "campus_buildings", "campus_rooms",
            "campus_places", "campus_facilities", "campus_place_facilities", "campus_room_facilities",
            "campus_opening_hours", "campus_opening_exceptions", "campus_nav_nodes", "campus_nav_edges",
            "campus_nav_edge_points", "campus_nav_destinations"};
    private String schema;
    private Flyway flyway;
    private JdbcTemplate db;
    private long site, category, building;

    @BeforeEach
    void migrate() {
        schema = "campus_" + UUID.randomUUID().toString().replace("-", "");
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).cleanDisabled(false).load();
        flyway.migrate();
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        site = id("INSERT INTO campus_sites(site_code,site_name) VALUES ('S1','Test site') RETURNING id");
        category = id("INSERT INTO campus_categories(category_code,category_name,mobile_group) VALUES ('LIB','Library','LIBRARY') RETURNING id");
        building = building(site, "B1");
    }

    @AfterEach
    void drop() { flyway.clean(); }

    @Test
    void historySucceedsAndNothingIsSeeded() {
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version::int >= 33 ORDER BY installed_rank", String.class))
                .containsExactly("33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47");
        for (String table : TABLES) assertThat(tableExists(table)).as(table).isTrue();
        db.update("DELETE FROM campus_buildings");
        db.update("DELETE FROM campus_categories");
        db.update("DELETE FROM campus_sites");
        for (String table : TABLES)
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
    }

    @Test
    void foreignKeysExistAndNeverCascade() {
        assertThat(db.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE contype='f' AND connamespace=?::regnamespace
                    AND conrelid::regclass::text LIKE '%campus_%'""", Long.class, schema)).isGreaterThanOrEqualTo(30);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE contype='f' AND connamespace=?::regnamespace AND confdeltype='c'""",
                Long.class, schema)).isZero();
        assertInvalid(() -> db.update("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (-1,'1',1,'OTHER')"));
    }

    @Test
    void buildingAndRoomUniquenessFollowTheirParent() {
        assertInvalid(() -> building(site, "B1"));
        assertThatCode(() -> building(id("INSERT INTO campus_sites(site_code,site_name) VALUES ('S2','Other') RETURNING id"), "B1")).doesNotThrowAnyException();
        long other = building(site, "B2");
        String room = "INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,'101',1,'CLASSROOM')";
        db.update(room, building);
        assertInvalid(() -> db.update(room, building));
        assertThatCode(() -> db.update(room, other)).doesNotThrowAnyException();
        assertInvalid(() -> db.update("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type,capacity) VALUES (?,'102',1,'CLASSROOM',0)", building));
        assertInvalid(() -> db.update("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,'103',1,'MADE_UP')", building));
        assertInvalid(() -> db.update("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,'B9','x',0)", site));
    }

    @Test
    void placeCodeIsUniqueInsideTheSite() {
        String place = "INSERT INTO campus_places(site_id,category_id,place_code,place_name) VALUES (?,?,'P1','Place')";
        db.update(place, site, category);
        assertInvalid(() -> db.update(place, site, category));
        assertInvalid(() -> db.update("INSERT INTO campus_categories(category_code,category_name,mobile_group) VALUES ('X','X','ALL')"));
    }

    @Test
    void coordinatesAreRangeChecked() {
        String node = "INSERT INTO campus_nav_nodes(site_id,node_code,node_name,node_type,latitude,longitude) VALUES (?,?,'N','JUNCTION',?,?)";
        assertInvalid(() -> db.update(node, site, "BAD1", 90.5, 10.0));
        assertInvalid(() -> db.update(node, site, "BAD2", 10.0, 180.5));
        assertInvalid(() -> db.update("UPDATE campus_sites SET center_latitude=91 WHERE id=?", site));
        assertInvalid(() -> db.update("INSERT INTO campus_nav_nodes(site_id,node_code,node_name,node_type,latitude,longitude) VALUES (?,'T','N','MADE_UP',1,1)", site));
    }

    @Test
    void edgesRejectBadDistanceAndSelfLoops() {
        long a = node("A"), b = node("B");
        String edge = "INSERT INTO campus_nav_edges(site_id,edge_code,start_node_id,end_node_id,distance_meters,surface_type) VALUES (?,?,?,?,?,'PAVED')";
        assertInvalid(() -> db.update(edge, site, "E1", a, b, -5));
        assertInvalid(() -> db.update(edge, site, "E2", a, b, 0));
        assertInvalid(() -> db.update(edge, site, "E3", a, a, 10));
        db.update(edge, site, "E4", a, b, 10);
        long e = db.queryForObject("SELECT id FROM campus_nav_edges WHERE edge_code='E4'", Long.class);
        String point = "INSERT INTO campus_nav_edge_points(edge_id,point_sequence,latitude,longitude) VALUES (?,?,1,1)";
        db.update(point, e, 0);
        assertInvalid(() -> db.update(point, e, 0));
        assertInvalid(() -> db.update(point, e, -1));
    }

    @Test
    void openingHoursRejectInvalidRows() {
        String hours = "INSERT INTO campus_opening_hours(building_id,day_of_week,opens_at,closes_at,is_closed) VALUES (?,?,?::time,?::time,?)";
        assertInvalid(() -> db.update(hours, building, 8, "08:00", "17:00", false));
        assertInvalid(() -> db.update(hours, building, 1, "17:00", "08:00", false));
        assertInvalid(() -> db.update(hours, building, 1, "08:00", null, false));
        assertInvalid(() -> db.update(hours, building, 1, "08:00", "17:00", true));
        db.update(hours, building, 1, "08:00", "17:00", false);
        assertInvalid(() -> db.update(hours, building, 1, "09:00", "17:00", false));
        assertInvalid(() -> db.update("INSERT INTO campus_opening_hours(day_of_week,is_closed) VALUES (1,TRUE)"));
        String exception = "INSERT INTO campus_opening_exceptions(building_id,exception_date,opens_at,closes_at,is_closed) VALUES (?,'2026-12-25',?::time,?::time,?)";
        assertInvalid(() -> db.update(exception, building, "17:00", "08:00", false));
        db.update(exception, building, null, null, true);
        assertInvalid(() -> db.update(exception, building, null, null, true));
    }

    @Test
    void onlyOnePrimaryDestinationPerOwner() {
        long a = node("A"), b = node("B");
        String destination = "INSERT INTO campus_nav_destinations(node_id,building_id,is_primary) VALUES (?,?,?)";
        db.update(destination, a, building, true);
        assertInvalid(() -> db.update(destination, b, building, true));
        assertInvalid(() -> db.update(destination, a, building, false));
        assertThatCode(() -> db.update(destination, b, building, false)).doesNotThrowAnyException();
        assertInvalid(() -> db.update("INSERT INTO campus_nav_destinations(node_id) VALUES (?)", a));
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, schema + "." + table));
    }
    private long building(long siteId, String code) {
        return id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,?,'Building',3) RETURNING id", siteId, code);
    }
    private long node(String code) {
        return id("INSERT INTO campus_nav_nodes(site_id,node_code,node_name,node_type,latitude,longitude) VALUES (?,?,'N','JUNCTION',1,1) RETURNING id", site, code);
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private static void assertInvalid(ThrowingCallable call) { assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class); }
}
