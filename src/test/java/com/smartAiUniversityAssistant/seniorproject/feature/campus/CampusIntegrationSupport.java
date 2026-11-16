package com.smartAiUniversityAssistant.seniorproject.feature.campus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

/** Shared campus fixture: admins of each role, one student, and helpers. All names and coordinates are synthetic test values. */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
public abstract class CampusIntegrationSupport extends IntegrationSupport {
    protected static final String ADMIN_PASSWORD = "campus admin test password";
    protected static final String STUDENT_PASSWORD = "campus student test password";
    protected static final String ADMIN = "/api/v1/admin/campus";
    protected static final String STUDENT = "/api/v1/student/campus";
    // Always ahead of the real time: Redis expires sessions in real time, so a fixed past date would break logins later on.
    protected static final java.time.LocalDate MONDAY = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bangkok")).plusDays(1)
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
    protected static final Instant MONDAY_NOON_BANGKOK = MONDAY.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Bangkok")).toInstant();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate db;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected MutableClock clock;

    protected long superAdminId, studentId;
    protected String superToken, adminToken, academicToken, studentToken;

    @BeforeEach
    protected void campusFixture() throws Exception {
        clock.set(MONDAY_NOON_BANGKOK);
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clearCampusTables();
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        // The database is shared with the other test classes, so clear whatever they left that references users.
        for (String table : List.of("student_course_result_audits", "student_course_results", "grade_definitions", "grading_schemes",
                "course_exams", "course_assignments", "timetable_meeting_exceptions", "course_section_meetings", "academic_document_processing", "academic_document_files",
                "academic_documents", "document_categories", "curriculum_versions", "student_credentials", "enrollments", "students",
                "course_sections", "academic_terms", "courses", "lectures", "semesters", "programs", "departments", "faculties"))
            db.update("DELETE FROM " + table);
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        superAdminId = admin("campus.super@example.test", "SUPER_ADMIN");
        admin("campus.admin@example.test", "ADMIN");
        admin("campus.academic@example.test", "ACADEMIC_ADMIN");
        superToken = adminLogin("campus.super@example.test");
        adminToken = adminLogin("campus.admin@example.test");
        academicToken = adminLogin("campus.academic@example.test");
        studentId = student("CAMPUS-001", false);
        studentToken = studentLogin("CAMPUS-001");
    }

    /** Other test classes delete users; leave no rows that reference them. */
    @AfterEach
    protected void clearCampusTables() {
        SecurityContextHolder.clearContext();
        // Meetings and their exceptions reference campus rooms; other classes may leave some behind.
        for (String table : List.of("timetable_meeting_exceptions", "course_section_meetings", "campus_nav_destinations", "campus_nav_edge_points", "campus_nav_edges", "campus_nav_nodes",
                "campus_opening_exceptions", "campus_opening_hours", "campus_room_facilities", "campus_place_facilities", "campus_facilities",
                "campus_places", "campus_rooms", "campus_buildings", "campus_categories", "campus_sites"))
            db.update("DELETE FROM " + table);
    }

    protected long admin(String email, String role) {
        long id = id("INSERT INTO app_users(employee_id,first_name,last_name,email,account_status) VALUES (?,'Campus','Actor',?,'ACTIVE') RETURNING id", email, email);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", id, role);
        return id;
    }
    protected long student(String code, boolean forcePasswordChange) {
        long id = id("INSERT INTO students(student_code,university_email,first_name,last_name,account_status) VALUES (?,?,'Campus','Student','ACTIVE') RETURNING id",
                code, code.toLowerCase(Locale.ROOT) + "@rsu.ac.th");
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,?)", id, encoder.encode(STUDENT_PASSWORD), forcePasswordChange);
        return id;
    }
    protected String adminLogin(String email) throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", ADMIN_PASSWORD)))).andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    protected String studentLogin(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", code.toLowerCase(Locale.ROOT) + "@rsu.ac.th", "password", STUDENT_PASSWORD)))).andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    protected ResultActions call(String token, MockHttpServletRequestBuilder builder, Object body) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    protected JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
    protected long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }

    // Direct inserts for data whose creation is not what the test is about.
    protected long site(String code) { return id("INSERT INTO campus_sites(site_code,site_name,created_by) VALUES (?,?,?) RETURNING id", code, code + " Campus", superAdminId); }
    protected long category(String code, String name, String group) {
        return id("INSERT INTO campus_categories(category_code,category_name,mobile_group) VALUES (?,?,?) RETURNING id", code, name, group);
    }
    protected long facility(String code, String name) { return id("INSERT INTO campus_facilities(facility_code,facility_name) VALUES (?,?) RETURNING id", code, name); }
    protected long building(long site, String code, String name) {
        return id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,?,?,5) RETURNING id", site, code, name);
    }
    protected long room(long building, String number, int floor) {
        return id("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,?,?,'CLASSROOM') RETURNING id", building, number, floor);
    }
    protected long place(long site, long category, Long building, String code, String name) {
        return id("INSERT INTO campus_places(site_id,category_id,building_id,place_code,place_name) VALUES (?,?,?,?,?) RETURNING id", site, category, building, code, name);
    }
    protected long node(long site, String code, double latitude, double longitude) {
        return id("INSERT INTO campus_nav_nodes(site_id,node_code,node_name,node_type,latitude,longitude) VALUES (?,?,?,'JUNCTION',?,?) RETURNING id",
                site, code, "Node " + code, latitude, longitude);
    }
    protected long edge(long site, String code, long from, long to, double distance) {
        return id("INSERT INTO campus_nav_edges(site_id,edge_code,start_node_id,end_node_id,distance_meters,surface_type,is_wheelchair_accessible) VALUES (?,?,?,?,?,'PAVED',TRUE) RETURNING id",
                site, code, from, to, distance);
    }
    protected void entrance(long node, Long building, Long place, boolean primary) {
        db.update("INSERT INTO campus_nav_destinations(node_id,building_id,place_id,is_primary) VALUES (?,?,?,?)", node, building, place, primary);
    }
    /** A body with nulls, which Map.of cannot hold. */
    protected static Map<String, Object> body(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
