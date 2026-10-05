package com.smartAiUniversityAssistant.seniorproject.feature.semester;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.semester.service.SemesterService;
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
class SemesterIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/semesters";
    private static final String PASSWORD = "semester actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired SemesterService semesters;
    private long actorId;
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
                VALUES ('SEM-ACTOR','Semester','Actor','semester.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditAndSoftDeleteSemantics() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = new LinkedHashMap<String, Object>();
        body.put("semesterNameTh", "  ภาคการศึกษาที่ 1  ");
        body.put("semesterNameEn", "  Semester 1  ");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("semesterNameTh").value("ภาคการศึกษาที่ 1"))
                .andExpect(jsonPath("semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist())
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT is_deleted,created_by,updated_by,updated_at FROM semesters WHERE id=?", id))
                .containsEntry("is_deleted", false).containsEntry("created_by", actorId)
                .containsEntry("updated_by", null).containsEntry("updated_at", null);
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("semesterNameTh").value("ภาคการศึกษาที่ 1"));
        clock.advance(Duration.ofSeconds(5));
        perform(put(BASE + "/" + id), Map.of("semesterNameTh", "ภาคการศึกษาที่หนึ่ง", "semesterNameEn", "First Semester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForObject("SELECT created_by FROM semesters WHERE id=?", Long.class, id)).isEqualTo(actorId);
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM semesters WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT updated_by FROM semesters WHERE id=?", Long.class, id)).isEqualTo(actorId);
        assertThat(db.queryForObject("SELECT count(*) FROM semesters", Integer.class)).isOne();
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEMESTER_NOT_FOUND"));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), Map.of("semesterNameTh", "x", "semesterNameEn", "y")).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void duplicatesPairedNamesReservedAfterSoftDelete() throws Exception {
        long first = create("ภาคการศึกษาที่ 1", "Semester 1");
        perform(post(BASE), Map.of("semesterNameTh", "  ภาคการศึกษาที่ 1 ", "semesterNameEn", " Semester 1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEMESTER_ALREADY_EXISTS"));
        perform(post(BASE), Map.of("semesterNameTh", "ภาคการศึกษาที่ 1", "semesterNameEn", "First Semester"))
                .andExpect(status().isCreated());
        perform(post(BASE), Map.of("semesterNameTh", "ภาคการศึกษาที่หนึ่ง", "semesterNameEn", "Semester 1"))
                .andExpect(status().isCreated());
        perform(put(BASE + "/" + first), Map.of("semesterNameTh", "ภาคการศึกษาที่ 2", "semesterNameEn", "Semester 2"))
                .andExpect(status().isOk());
        var renamed = db.queryForObject("SELECT updated_at FROM semesters WHERE id=?", java.sql.Timestamp.class, first);
        perform(put(BASE + "/" + first), Map.of("semesterNameTh", "ภาคการศึกษาที่ 1", "semesterNameEn", "First Semester"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEMESTER_ALREADY_EXISTS"));
        perform(get(BASE + "/" + first), null).andExpect(status().isOk())
                .andExpect(jsonPath("semesterNameEn").value("Semester 2"));
        assertThat(db.queryForObject("SELECT updated_at FROM semesters WHERE id=?", java.sql.Timestamp.class, first)).isEqualTo(renamed);
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), Map.of("semesterNameTh", "ภาคการศึกษาที่ 2", "semesterNameEn", "Semester 2"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEMESTER_ALREADY_EXISTS"));
        // Exact-case uniqueness, matching the database constraint and existing features.
        perform(post(BASE), Map.of("semesterNameTh", "ภาคการศึกษาที่ 1", "semesterNameEn", "semester 1"))
                .andExpect(status().isCreated());
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("semesterNameTh", "semesterNameEn")) {
            var body = new LinkedHashMap<String, Object>();
            body.put("semesterNameTh", "ภาคการศึกษาที่ 1");
            body.put("semesterNameEn", "Semester 1");
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(151), null)) {
            for (String field : List.of("semesterNameTh", "semesterNameEn")) {
                var body = new LinkedHashMap<String, Object>();
                body.put("semesterNameTh", "ภาคการศึกษาที่ 1");
                body.put("semesterNameEn", "Semester 1");
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        for (String field : List.of("createdBy", "updatedBy", "isDeleted", "isActive", "semesterCode", "academicYearId")) {
            var body = new LinkedHashMap<String, Object>();
            body.put("semesterNameTh", "ภาคการศึกษาที่ 1");
            body.put("semesterNameEn", "Semester 1");
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        long id = create("ภาคการศึกษาที่ 9", "Ninth Semester");
        for (String field : List.of("semesterNameTh", "semesterNameEn")) {
            var body = new LinkedHashMap<String, Object>();
            body.put("semesterNameTh", "ภาคการศึกษาที่ 9");
            body.put("semesterNameEn", "Ninth Semester");
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSearchesThaiAndEnglishLiterallyWithStablePagination() throws Exception {
        long one = create("ภาคการศึกษาที่ 1", "Engineering 100%_มาลี");
        long two = create("ภาคฤดูร้อน", "Summer Semester");
        long three = create("ภาคการศึกษาที่ 2", "Semester 2");
        long gone = create("ภาคการศึกษาที่ 3", "Gone Semester");
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk())
                .andExpect(jsonPath("page").value(0)).andExpect(jsonPath("size").value(20))
                .andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc"))
                .andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("search", "semester"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("search", "SUMMER"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two));
        perform(get(BASE).param("search", "ภาคการศึกษาที่"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        for (String search : List.of("100%_", "มาลี"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("sort", "semesterNameEn,asc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("sort", "semesterNameTh,asc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("sort[0]").value("semesterNameTh,asc"));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        for (String query : List.of("size=0", "size=101", "size=99999999999999", "page=-1", "page=2147483647&size=100",
                "includeDeleted=true", "sort=semester_name_en,asc", "sort=id,up", "sort=id,asc&sort=id,desc",
                "page=0&page=1", "search=" + "x".repeat(151), "status=ACTIVE"))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create(role + " ภาค", role + " Semester");
            perform(put(BASE + "/" + id), Map.of("semesterNameTh", role + " ภาคอัปเดต", "semesterNameEn", role + " Updated"))
                    .andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('SEM_TEST_READER','Semester test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("SEM_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), Map.of("semesterNameTh", "ภาค", "semesterNameEn", "Denied")).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), Map.of("semesterNameTh", "ภาค", "semesterNameEn", "Denied")).andExpect(status().isForbidden());
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?", actorId);
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (var route : List.of(get(BASE), get(BASE + "/1"), delete(BASE + "/1"), post(BASE), put(BASE + "/1")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        clock.advance(Duration.ofMinutes(16));
        perform(get(BASE), null).andExpect(status().isUnauthorized());
    }

    @Test
    void directServiceCallsStillRequireAdministrativeRole() {
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> semesters.detail(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> semesters.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> semesters.create(principal, new SemesterCreateRequest())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateNameRaceIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return perform(post(BASE), Map.of("semesterNameTh", "ภาคการศึกษาที่แข่ง", "semesterNameEn", "Race Semester"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM semesters WHERE semester_name_en='Race Semester'", Integer.class)).isOne();
    }

    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "semester.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(String nameTh, String nameEn) throws Exception {
        return tree(perform(post(BASE), Map.of("semesterNameTh", nameTh, "semesterNameEn", nameEn))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
