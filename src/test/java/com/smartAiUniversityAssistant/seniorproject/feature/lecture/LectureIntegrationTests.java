package com.smartAiUniversityAssistant.seniorproject.feature.lecture;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.lecture.service.LectureService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class LectureIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/lectures";
    private static final String PASSWORD = "lecture actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired LectureService lectures;
    private long actorId, facultyA, facultyB, departmentA, departmentB;
    private String access;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM course_sections");
        db.update("DELETE FROM courses");
        db.update("DELETE FROM lectures");
        db.update("DELETE FROM semesters");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('LEC-ACTOR','Lecture','Actor','lecture.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyA = faculty("FAC-A", "Faculty A");
        facultyB = faculty("FAC-B", "Faculty B");
        departmentA = department("DEPT-A", "Department A", facultyA);
        departmentB = department("DEPT-B", "Department B", facultyB);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditAndSoftDeleteSemantics() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = request("  อาจารย์สมชาย ใจดี  ", "  Somchai Jaidee  ", "  ", facultyA, departmentA);
        body.remove("status");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("lectureNameTh").value("อาจารย์สมชาย ใจดี"))
                .andExpect(jsonPath("lectureNameEn").value("Somchai Jaidee"))
                .andExpect(jsonPath("lectureNickname").doesNotExist())
                .andExpect(jsonPath("facultyId").value(facultyA))
                .andExpect(jsonPath("facultyCode").value("FAC-A"))
                .andExpect(jsonPath("facultyNameEn").value("Faculty A"))
                .andExpect(jsonPath("departmentId").value(departmentA))
                .andExpect(jsonPath("departmentCode").value("DEPT-A"))
                .andExpect(jsonPath("departmentName").value("Department A"))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist())
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT is_deleted,status,lecture_nickname,created_by,updated_by,updated_at FROM lectures WHERE id=?", id))
                .containsEntry("is_deleted", false).containsEntry("status", "ACTIVE")
                .containsEntry("lecture_nickname", null).containsEntry("created_by", actorId)
                .containsEntry("updated_by", null).containsEntry("updated_at", null);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalLectures").value(1)).andExpect(jsonPath("activeLectures").value(1))
                .andExpect(jsonPath("inactiveLectures").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("lectureNameTh").value("อาจารย์สมชาย ใจดี"));
        var creator = db.queryForMap("SELECT created_by,created_at FROM lectures WHERE id=?", id);
        clock.advance(Duration.ofSeconds(5));
        var moved = request("อาจารย์สมชาย ใจดี", "Somchai Jaidee", "Aj. Somchai", facultyB, departmentB);
        moved.put("status", "inactive");
        perform(put(BASE + "/" + id), moved)
                .andExpect(status().isOk())
                .andExpect(jsonPath("lectureNickname").value("Aj. Somchai"))
                .andExpect(jsonPath("facultyId").value(facultyB))
                .andExpect(jsonPath("departmentId").value(departmentB))
                .andExpect(jsonPath("status").value("INACTIVE"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT created_by,created_at FROM lectures WHERE id=?", id)).isEqualTo(creator);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("activeLectures").value(0)).andExpect(jsonPath("inactiveLectures").value(1));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM lectures WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT status FROM lectures WHERE id=?", String.class, id)).isEqualTo("INACTIVE");
        assertThat(db.queryForObject("SELECT updated_by FROM lectures WHERE id=?", Long.class, id)).isEqualTo(actorId);
        assertThat(db.queryForObject("SELECT count(*) FROM lectures", Integer.class)).isOne();
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalLectures").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("LECTURE_NOT_FOUND"));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), request("x", "y", null, facultyA, departmentA)).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void duplicatesScopedToDepartmentIncludingDeleted() throws Exception {
        long first = create("อาจารย์สมชาย ใจดี", "Somchai Jaidee", facultyA, departmentA);
        long other = create("อาจารย์สมชาย ใจดี", "Somchai Jaidee", facultyB, departmentB);
        perform(post(BASE), request(" อาจารย์สมชาย ใจดี ", " Somchai Jaidee ", null, facultyA, departmentA))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("LECTURE_ALREADY_EXISTS"));
        perform(put(BASE + "/" + other), request("อาจารย์สมชาย ใจดี", "Somchai Jaidee", null, facultyA, departmentA))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("LECTURE_ALREADY_EXISTS"));
        perform(put(BASE + "/" + first), request("อาจารย์สมชาย ใจดี", "Somchai Jaidee", "Nick", facultyA, departmentA))
                .andExpect(status().isOk());
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), request("อาจารย์สมชาย ใจดี", "Somchai Jaidee", null, facultyA, departmentA))
                .andExpect(status().isConflict());
        // Exact-case uniqueness, matching the database constraint and existing features.
        perform(post(BASE), request("อาจารย์สมชาย ใจดี", "somchai jaidee", null, facultyA, departmentA))
                .andExpect(status().isCreated());
    }

    @Test
    void validatesFacultyAndDepartmentOnCreateAndUpdate() throws Exception {
        long id = create("อาจารย์ใช้ได้", "Valid Lecture", facultyA, departmentA);
        perform(post(BASE), request("x", "Mismatch", null, facultyB, departmentA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_LECTURE_DEPARTMENT"));
        perform(put(BASE + "/" + id), request("x", "Mismatch", null, facultyB, departmentA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_LECTURE_DEPARTMENT"));
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE faculties SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, facultyB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "FACULTY_NOT_FOUND" : "INVALID_FACULTY";
            perform(post(BASE), request("x", "Bad Faculty", null, facultyB, departmentB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
            perform(put(BASE + "/" + id), request("x", "Bad Faculty", null, facultyB, departmentB)).andExpect(status().is(expected));
        }
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE departments SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, departmentB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "DEPARTMENT_NOT_FOUND" : "INVALID_LECTURE_DEPARTMENT";
            perform(post(BASE), request("x", "Bad Department", null, facultyB, departmentB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
            perform(put(BASE + "/" + id), request("x", "Bad Department", null, facultyB, departmentB)).andExpect(status().is(expected));
        }
        perform(post(BASE), request("x", "Missing", null, Long.MAX_VALUE, departmentA)).andExpect(status().isNotFound());
        perform(post(BASE), request("x", "Missing", null, facultyA, Long.MAX_VALUE)).andExpect(status().isNotFound());
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("lectureNameEn").value("Valid Lecture"));
        assertThat(db.queryForObject("SELECT count(*) FROM lectures", Integer.class)).isOne();
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("lectureNameTh", "lectureNameEn", "facultyId", "departmentId")) {
            var body = request("อาจารย์สมศรี", "Somsri Jaidee", null, facultyA, departmentA);
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(256), null)) {
            for (String field : List.of("lectureNameTh", "lectureNameEn")) {
                var body = request("อาจารย์สมศรี", "Somsri Jaidee", null, facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        for (Object invalid : List.of("PENDING", "DELETED", "", " ")) {
            var body = request("อาจารย์สมศรี", "Somsri Jaidee", null, facultyA, departmentA);
            body.put("status", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        var nullStatus = request("อาจารย์สมศรี", "Somsri Jaidee", null, facultyA, departmentA);
        nullStatus.put("status", null);
        perform(post(BASE), nullStatus).andExpect(status().isBadRequest());
        var lowerCase = request("อาจารย์สมศรี", "Somsri Jaidee", null, facultyA, departmentA);
        lowerCase.put("status", "active");
        perform(post(BASE), lowerCase).andExpect(status().isCreated()).andExpect(jsonPath("status").value("ACTIVE"));
        var longNickname = request("อาจารย์สมหญิง", "Somying Jaidee", "n".repeat(101), facultyA, departmentA);
        perform(post(BASE), longNickname).andExpect(status().isBadRequest());
        for (String field : List.of("createdBy", "updatedBy", "isDeleted", "isActive", "facultyName", "departmentName")) {
            var body = request("อาจารย์สมปอง", "Sompong Jaidee", null, facultyA, departmentA);
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        for (Object invalid : List.of("1", -1, 0, 1.5)) {
            for (String field : List.of("facultyId", "departmentId")) {
                var body = request("อาจารย์สมปอง", "Sompong Jaidee", null, facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        long id = create("อาจารย์อัปเดต", "Update Lecture", facultyA, departmentA);
        for (String field : List.of("lectureNameTh", "lectureNameEn", "facultyId", "departmentId", "status")) {
            var body = request("อาจารย์อัปเดต", "Update Lecture", null, facultyA, departmentA);
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        var updated = request("อาจารย์อัปเดต", "Update Lecture", null, facultyA, departmentA);
        updated.put("status", "inactive");
        perform(put(BASE + "/" + id), updated).andExpect(status().isOk()).andExpect(jsonPath("status").value("INACTIVE"));
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsLiteralSearchFiltersAndStablePagination() throws Exception {
        long one = create("อาจารย์มาลี 100%_", "Malee Engineering", facultyA, departmentA);
        long two = create("อาจารย์สมชาย", "Somchai Technology", facultyB, departmentB);
        long three = create("อาจารย์สมศรี", "Somsri Engineering", facultyA, departmentA);
        db.update("UPDATE lectures SET status='INACTIVE' WHERE id=?", three);
        db.update("UPDATE lectures SET lecture_nickname='Aj. Malee' WHERE id=?", one);
        long gone = create("อาจารย์หายไป", "Gone Lecture", facultyA, departmentA);
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc")).andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].id").value(one));
        for (String search : List.of("engineering", "ENGINEERING"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(2));
        for (String search : List.of("100%_", "aj. malee", "สมชาย"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(1));
        perform(get(BASE).param("search", "aj. malee"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("facultyId", String.valueOf(facultyB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-B"));
        perform(get(BASE).param("departmentId", String.valueOf(departmentA)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("facultyId", String.valueOf(facultyA)).param("departmentId", String.valueOf(departmentB)), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE).param("status", "INACTIVE"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("search", "engineering").param("facultyId", String.valueOf(facultyA))
                .param("departmentId", String.valueOf(departmentA)).param("status", "ACTIVE"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("sort", "lectureNameEn,asc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(two))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("sort", "status,desc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("sort[0]").value("status,desc"));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        for (String query : List.of("size=0", "size=101", "size=99999999999999", "page=-1", "page=2147483647&size=100",
                "facultyId=0", "facultyId=abc", "departmentId=-1", "status=active", "status=PENDING", "includeDeleted=true",
                "sort=lecture_name_en,asc", "sort=id,up", "sort=id,asc&sort=id,desc", "page=0&page=1",
                "search=" + "x".repeat(256)))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create("อาจารย์" + role, role + " Lecture", facultyA, departmentA);
            perform(put(BASE + "/" + id), request("อาจารย์" + role, role + " Updated", null, facultyA, departmentA))
                    .andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('LEC_TEST_READER','Lecture test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("LEC_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), request("ภาค", "Denied", null, facultyA, departmentA)).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), request("ภาค", "Denied", null, facultyA, departmentA)).andExpect(status().isForbidden());
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?", actorId);
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1"), post(BASE), put(BASE + "/1")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        clock.advance(Duration.ofMinutes(16));
        perform(get(BASE), null).andExpect(status().isUnauthorized());
    }

    @Test
    void directServiceCallsStillRequireAdministrativeRole() {
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> lectures.detail(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> lectures.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> lectures.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> lectures.create(principal, new LectureCreateRequest())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateNameRaceIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return perform(post(BASE), request("อาจารย์แข่งขัน", "Race Lecture", null, facultyA, departmentA))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM lectures WHERE lecture_name_en='Race Lecture'", Integer.class)).isOne();
    }

    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private long faculty(String code, String name) {
        return db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, code, name, actorId);
    }
    private long department(String code, String name, long faculty) {
        return db.queryForObject(
                "INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES (?,?,?,?) RETURNING id",
                Long.class, code, name, faculty, actorId);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "lecture.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(String nameTh, String nameEn, long faculty, long department) throws Exception {
        return tree(perform(post(BASE), request(nameTh, nameEn, null, faculty, department))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(String nameTh, String nameEn, String nickname, long faculty, long department) {
        var body = new LinkedHashMap<String, Object>();
        body.put("lectureNameTh", nameTh);
        body.put("lectureNameEn", nameEn);
        body.put("lectureNickname", nickname);
        body.put("facultyId", faculty);
        body.put("departmentId", department);
        body.put("status", "ACTIVE");
        return body;
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
