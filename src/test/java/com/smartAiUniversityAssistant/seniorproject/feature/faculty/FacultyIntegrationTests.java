package com.smartAiUniversityAssistant.seniorproject.feature.faculty;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.LoginRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.FacultyListQuery;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto.FacultyUpdateRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.faculty.service.FacultyService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Instant;
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
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class FacultyIntegrationTests extends IntegrationSupport {
    private static final String FACULTIES = "/api/v1/admin/faculties";
    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String ACTOR_PASSWORD = "super admin test password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired FacultyService facultyService;
    private long actorId;
    private String access;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        db.update("UPDATE app_users SET department_id=NULL");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("UPDATE app_users SET created_by=NULL,updated_by=NULL");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status,created_at)
                VALUES ('ACTOR-01','Super','Admin','super@example.test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ACTOR_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_at) SELECT ?,id,CURRENT_TIMESTAMP FROM app_roles WHERE role_code='SUPER_ADMIN'",
                actorId);
        access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAppliesDefaultsTrustedAuditAndSafeResponse() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("facultyCode", "  FAC-ENG  ");
        request.put("facultyNameEn", "  Faculty of Engineering  ");
        request.put("facultyNameTh", "  คณะวิศวกรรมศาสตร์  ");

        postJson(FACULTIES, request).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("facultyCode").value("FAC-ENG"))
                .andExpect(jsonPath("facultyNameEn").value("Faculty of Engineering"))
                .andExpect(jsonPath("facultyNameTh").value("คณะวิศวกรรมศาสตร์"))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("departmentCount").value(0))
                .andExpect(jsonPath("studentCount").value(0))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("isDeleted").doesNotExist())
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist());

        Map<String, Object> row = db.queryForMap("SELECT * FROM faculties WHERE faculty_code='FAC-ENG'");
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("is_deleted")).isEqualTo(false);
        assertThat(row.get("created_by")).isEqualTo(actorId);
        assertThat(row.get("updated_by")).isNull();
    }

    @Test
    void createValidationDuplicatesAndMassAssignment() throws Exception {
        Map<String, Object> valid = validCreate("FAC-IT", "Faculty of Information Technology");

        Map<String, Object> missingCode = new LinkedHashMap<>(valid);
        missingCode.remove("facultyCode");
        postJson(FACULTIES, missingCode).andExpect(status().isBadRequest());
        Map<String, Object> lowercase = new LinkedHashMap<>(valid);
        lowercase.put("facultyCode", "fac-it");
        postJson(FACULTIES, lowercase).andExpect(status().isBadRequest());
        Map<String, Object> tooLong = new LinkedHashMap<>(valid);
        tooLong.put("facultyCode", "F".repeat(31));
        postJson(FACULTIES, tooLong).andExpect(status().isBadRequest());
        Map<String, Object> nullActive = new LinkedHashMap<>(valid);
        nullActive.put("isActive", null);
        postJson(FACULTIES, nullActive).andExpect(status().isBadRequest());
        Map<String, Object> auditField = new LinkedHashMap<>(valid);
        auditField.put("createdBy", actorId);
        postJson(FACULTIES, auditField).andExpect(status().isBadRequest());
        Map<String, Object> iconField = new LinkedHashMap<>(valid);
        iconField.put("iconKey", "engineering");
        postJson(FACULTIES, iconField).andExpect(status().isBadRequest());

        postJson(FACULTIES, valid).andExpect(status().isCreated());
        postJson(FACULTIES, validCreate("FAC-DUP2", "Faculty of Information Technology"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("FACULTY_NAME_ALREADY_EXISTS"));
        postJson(FACULTIES, validCreate("FAC-IT", "Another Faculty Name"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("FACULTY_CODE_ALREADY_EXISTS"));

        db.update("UPDATE faculties SET is_deleted=TRUE WHERE faculty_code='FAC-IT'");
        postJson(FACULTIES, validCreate("FAC-IT", "Reused Deleted Code"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("FACULTY_CODE_ALREADY_EXISTS"));
        assertThat(db.queryForObject("SELECT count(*) FROM faculties", Integer.class)).isOne();
    }

    @Test
    void listPaginatesSearchesFiltersAndSorts() throws Exception {
        insertFaculty("FAC-ENG", "Faculty of Engineering", "คณะวิศวกรรมศาสตร์", true, "2026-01-01 01:00:00");
        insertFaculty("FAC-IT", "Faculty of Information Technology", null, true, "2026-01-01 02:00:00");
        insertFaculty("FAC-LAW", "Faculty of Law", "คณะนิติศาสตร์", false, "2026-01-01 03:00:00");
        long deleted = insertFaculty("FAC-OLD", "Faculty of Deleted", null, true, "2026-01-01 04:00:00");
        db.update("UPDATE faculties SET is_deleted=TRUE WHERE id=?", deleted);

        getJson(FACULTIES).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20))
                .andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("totalPages").value(1))
                .andExpect(jsonPath("first").value(true))
                .andExpect(jsonPath("last").value(true))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc"))
                .andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-LAW"))
                .andExpect(jsonPath("content[0].isActive").value(false))
                .andExpect(jsonPath("content[0].departmentCount").value(0))
                .andExpect(jsonPath("content[0].studentCount").value(0))
                .andExpect(jsonPath("content[2].facultyCode").value("FAC-ENG"));

        mvc.perform(get(FACULTIES).param("search", "engineering").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-ENG"));
        mvc.perform(get(FACULTIES).param("search", "FAC-").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(3));
        mvc.perform(get(FACULTIES).param("search", "นิติศาสตร์").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-LAW"));
        mvc.perform(get(FACULTIES).param("status", "INACTIVE").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get(FACULTIES).param("status", "ACTIVE").param("sort", "facultyCode,asc")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-ENG"))
                .andExpect(jsonPath("content[1].facultyCode").value("FAC-IT"))
                .andExpect(jsonPath("sort[0]").value("facultyCode,asc"))
                .andExpect(jsonPath("sort[1]").value("id,asc"));
        mvc.perform(get(FACULTIES).param("size", "1").param("page", "2").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("size").value(1))
                .andExpect(jsonPath("totalPages").value(3))
                .andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(true));
    }

    @Test
    void listQueryValidationRejectsBadInput() throws Exception {
        getJson(FACULTIES + "?unknown=1").andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        getJson(FACULTIES + "?page=-1").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?size=0").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?size=101").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?status=DELETED").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?status=active").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?sort=faculty_name_en,asc").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?sort=facultyCode,up").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?sort=facultyCode").andExpect(status().isBadRequest());
        getJson(FACULTIES + "?sort=facultyCode,asc&sort=facultyCode,desc").andExpect(status().isBadRequest());
    }

    @Test
    void detailReturnsFacultyAndNotFoundCases() throws Exception {
        long id = insertFaculty("FAC-SCI", "Faculty of Science", null, true, "2026-01-01 01:00:00");
        long deleted = insertFaculty("FAC-GONE", "Faculty of Gone", null, true, "2026-01-01 02:00:00");
        db.update("UPDATE faculties SET is_deleted=TRUE WHERE id=?", deleted);

        getJson(FACULTIES + "/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(id))
                .andExpect(jsonPath("facultyCode").value("FAC-SCI"))
                .andExpect(jsonPath("facultyNameTh").doesNotExist())
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("departmentCount").value(0))
                .andExpect(jsonPath("studentCount").value(0))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("isDeleted").doesNotExist());
        getJson(FACULTIES + "/" + deleted).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("FACULTY_NOT_FOUND"));
        getJson(FACULTIES + "/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("FACULTY_NOT_FOUND"));
        getJson(FACULTIES + "/abc").andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        getJson(FACULTIES + "/-1").andExpect(status().isBadRequest());
    }

    @Test
    void updateAppliesFieldsDuplicatesExcludingSelfAndAudit() throws Exception {
        long target = insertFaculty("FAC-MED", "Faculty of Medicine", null, true, "2026-01-01 01:00:00");
        insertFaculty("FAC-BIZ", "Faculty of Business Administration", null, true, "2026-01-01 02:00:00");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("facultyCode", "FAC-MED");
        request.put("facultyNameEn", "  Faculty of Medicine and Health  ");
        request.put("facultyNameTh", "คณะแพทยศาสตร์");
        request.put("isActive", false);
        putJson(FACULTIES + "/" + target, request).andExpect(status().isOk())
                .andExpect(jsonPath("facultyNameEn").value("Faculty of Medicine and Health"))
                .andExpect(jsonPath("facultyNameTh").value("คณะแพทยศาสตร์"))
                .andExpect(jsonPath("isActive").value(false))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForObject("SELECT created_at FROM faculties WHERE id=?", Timestamp.class, target))
                .isEqualTo(Timestamp.valueOf("2026-01-01 01:00:00"));

        Map<String, Object> codeClash = new LinkedHashMap<>(request);
        codeClash.put("facultyCode", "FAC-BIZ");
        putJson(FACULTIES + "/" + target, codeClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("FACULTY_CODE_ALREADY_EXISTS"));
        Map<String, Object> nameClash = new LinkedHashMap<>(request);
        nameClash.put("facultyNameEn", "Faculty of Business Administration");
        putJson(FACULTIES + "/" + target, nameClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("FACULTY_NAME_ALREADY_EXISTS"));
        Map<String, Object> missingActive = new LinkedHashMap<>(request);
        missingActive.remove("isActive");
        putJson(FACULTIES + "/" + target, missingActive).andExpect(status().isBadRequest());
        Map<String, Object> systemField = new LinkedHashMap<>(request);
        systemField.put("isDeleted", false);
        putJson(FACULTIES + "/" + target, systemField).andExpect(status().isBadRequest());

        db.update("UPDATE faculties SET is_deleted=TRUE WHERE faculty_code='FAC-BIZ'");
        Map<String, Object> deletedNameClash = new LinkedHashMap<>(request);
        deletedNameClash.put("facultyNameEn", "Faculty of Business Administration");
        putJson(FACULTIES + "/" + target, deletedNameClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("FACULTY_NAME_ALREADY_EXISTS"));
        putJson(FACULTIES + "/999999", request).andExpect(status().isNotFound());
        long deleted = insertFaculty("FAC-DEL", "Faculty of Deleted Target", null, true, "2026-01-01 03:00:00");
        db.update("UPDATE faculties SET is_deleted=TRUE WHERE id=?", deleted);
        putJson(FACULTIES + "/" + deleted, request).andExpect(status().isNotFound());
    }

    @Test
    void deleteSoftDeletesExcludesAndRepeatReturns404() throws Exception {
        long target = insertFaculty("FAC-LA", "Faculty of Liberal Arts", null, true, "2026-01-01 01:00:00");

        deleteJson(FACULTIES + "/" + target).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        Map<String, Object> row = db.queryForMap("SELECT is_deleted,updated_by,updated_at FROM faculties WHERE id=?", target);
        assertThat(row.get("is_deleted")).isEqualTo(true);
        assertThat(row.get("updated_by")).isEqualTo(actorId);
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(db.queryForObject("SELECT count(*) FROM faculties WHERE id=?", Integer.class, target)).isOne();

        getJson(FACULTIES).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        getJson(FACULTIES + "/" + target).andExpect(status().isNotFound());
        deleteJson(FACULTIES + "/" + target).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("FACULTY_NOT_FOUND"));
        deleteJson(FACULTIES + "/999999").andExpect(status().isNotFound());
    }

    @Test
    void summaryCountsExcludeDeletedFaculties() throws Exception {
        getJson(FACULTIES + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("totalFaculties").value(0))
                .andExpect(jsonPath("activeFaculties").value(0))
                .andExpect(jsonPath("inactiveFaculties").value(0))
                .andExpect(jsonPath("totalDepartments").value(0));

        insertFaculty("FAC-ENG", "Faculty of Engineering", null, true, "2026-01-01 01:00:00");
        insertFaculty("FAC-IT", "Faculty of Information Technology", null, true, "2026-01-01 02:00:00");
        insertFaculty("FAC-LAW", "Faculty of Law", null, false, "2026-01-01 03:00:00");
        long deleted = insertFaculty("FAC-OLD", "Faculty of Deleted", null, true, "2026-01-01 04:00:00");
        db.update("UPDATE faculties SET is_deleted=TRUE WHERE id=?", deleted);

        getJson(FACULTIES + "/summary").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("totalFaculties").value(3))
                .andExpect(jsonPath("activeFaculties").value(2))
                .andExpect(jsonPath("inactiveFaculties").value(1))
                .andExpect(jsonPath("totalDepartments").value(0));
    }

    @Test
    void authorizationCoversRolesRestrictionsAndMissingTokens() throws Exception {
        long id = insertFaculty("FAC-AUTH", "Faculty of Auth", null, true, "2026-01-01 01:00:00");
        Map<String, Object> create = validCreate("FAC-AUTH2", "Faculty of Auth Two");
        mvc.perform(get(FACULTIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(FACULTIES + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get(FACULTIES + "/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post(FACULTIES).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(create)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(FACULTIES + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(create)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(FACULTIES + "/" + id)).andExpect(status().isUnauthorized());

        for (String role : List.of("ADMIN", "ACADEMIC_ADMIN")) {
            fixture();
            db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
            db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_at) SELECT ?,id,CURRENT_TIMESTAMP FROM app_roles WHERE role_code=?",
                    actorId, role);
            access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
            getJson(FACULTIES).andExpect(status().isOk());
            getJson(FACULTIES + "/summary").andExpect(status().isOk());
        }

        fixture();
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        String restricted = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        mvc.perform(get(FACULTIES).header("Authorization", "Bearer " + restricted)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void methodSecurityRejectsDirectInvocationWithoutAdministrativeRole() {
        var principal = new AuthenticatedUser(actorId, "session", "revision", false, Set.of("REGISTRAR"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_REGISTRAR"))));
        var query = new FacultyListQuery(null, null, null, null, List.of(), Set.of());
        assertThatThrownBy(() -> facultyService.list(principal, query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> facultyService.detail(principal, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> facultyService.summary(principal)).isInstanceOf(AccessDeniedException.class);
        var update = new FacultyUpdateRequest();
        update.setFacultyCode("FAC-X");
        update.setFacultyNameEn("Faculty of X");
        update.setIsActive(true);
        assertThatThrownBy(() -> facultyService.update(principal, 1L, update)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> facultyService.delete(principal, 1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentDuplicateCreatesPersistExactlyOneFaculty() throws Exception {
        Map<String, Object> request = validCreate("FAC-RACE", "Faculty of Race");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return postJson(FACULTIES, request).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM faculties WHERE faculty_code='FAC-RACE'", Integer.class)).isOne();
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new LoginRequest(email, password))))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions getJson(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + access));
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(body)));
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(body)));
    }

    private ResultActions deleteJson(String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", "Bearer " + access));
    }

    private long insertFaculty(String code, String nameEn, String nameTh, boolean active, String createdAt) {
        return db.queryForObject("""
                INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,is_active,is_deleted,created_by,created_at)
                VALUES (?,?,?,?,FALSE,?,CAST(? AS TIMESTAMP)) RETURNING id
                """, Long.class, code, nameEn, nameTh, active, actorId, createdAt);
    }

    private Map<String, Object> validCreate(String code, String nameEn) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("facultyCode", code);
        request.put("facultyNameEn", nameEn);
        request.put("facultyNameTh", null);
        request.put("isActive", true);
        return request;
    }
}
