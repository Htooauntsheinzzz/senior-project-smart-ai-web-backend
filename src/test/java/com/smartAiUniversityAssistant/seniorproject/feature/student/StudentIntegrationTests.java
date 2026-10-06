package com.smartAiUniversityAssistant.seniorproject.feature.student;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.nio.charset.StandardCharsets;
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
class StudentIntegrationTests extends IntegrationSupport {
    private static final String ADMIN = "/api/v1/admin/students";
    private static final String AUTH = "/api/v1/student/auth";
    private static final String ADMIN_PASSWORD = "student admin actor password";
    private static final String TEMP_PASSWORD = "temporary student password";
    private static final String NEW_PASSWORD = "brand new student password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    private long actorId, facultyA, facultyB, departmentA, departmentB, programA, programB, semester;
    private String admin;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('STU-ACTOR','Student','Actor','student.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='ACADEMIC_ADMIN'", actorId);
        admin = adminLogin();
        facultyA = id("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('ENG','Engineering',?) RETURNING id", actorId);
        facultyB = id("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('SCI','Science',?) RETURNING id", actorId);
        departmentA = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", facultyA, actorId);
        departmentB = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('BIO','Biology',?,?) RETURNING id", facultyB, actorId);
        programA = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSCS','BSc Computer Science','Bachelor',?,?) RETURNING id", departmentA, actorId);
        programB = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSBIO','BSc Biology','Bachelor',?,?) RETURNING id", departmentB, actorId);
        semester = id("INSERT INTO semesters(semester_name_th,semester_name_en,created_by) VALUES ('ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        db.update("DROP TRIGGER IF EXISTS fail_student_credentials ON student_credentials");
        db.update("DROP TRIGGER IF EXISTS fail_student_activation ON students");
        // Other feature tests delete faculties and app_users, which students reference.
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
    }

    private void cleanDatabase() {
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
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
    }

    // ---------------------------------------------------------------- Admin CRUD

    @Test
    void adminCreateListSummaryDetailUpdateAndSoftDelete() throws Exception {
        var created = asAdmin(post(ADMIN), createBody("6600001", "  John.S@RSU.ac.th ", "ACTIVE"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("studentCode").value("6600001"))
                .andExpect(jsonPath("universityEmail").value("john.s@rsu.ac.th"))
                .andExpect(jsonPath("facultyCode").value("ENG"))
                .andExpect(jsonPath("departmentCode").value("CS"))
                .andExpect(jsonPath("programCode").value("BSCS"))
                .andExpect(jsonPath("semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("forcePasswordChange").value(true))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andReturn();
        long id = tree(created).get("id").asLong();
        var credential = db.queryForMap("SELECT * FROM student_credentials WHERE student_id=?", id);
        assertThat(encoder.matches(TEMP_PASSWORD, (String) credential.get("password_hash"))).isTrue();
        assertThat(credential).containsEntry("force_password_change", true).containsEntry("failed_login_attempts", 0);

        pending("6600002", "pending.one@rsu.ac.th", null);
        asAdmin(get(ADMIN + "?search=JOHN&facultyId=" + facultyA + "&status=ACTIVE"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(id));
        asAdmin(get(ADMIN + "?status=PENDING&sort=studentCode,asc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].studentCode").value("6600002"));
        asAdmin(get(ADMIN + "?status=nonsense"), null).andExpect(status().isBadRequest());
        asAdmin(get(ADMIN + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalStudents").value(2))
                .andExpect(jsonPath("activeStudents").value(1))
                .andExpect(jsonPath("pendingStudents").value(1))
                .andExpect(jsonPath("inactiveOrSuspendedStudents").value(0));
        asAdmin(get(ADMIN + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("firstName").value("John"));
        // Route conflict: only numeric IDs reach the detail route.
        asAdmin(get(ADMIN + "/abc"), null).andExpect(status().isNotFound());
        asAdmin(get(ADMIN + "/999999"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("STUDENT_NOT_FOUND"));

        clock.advance(Duration.ofSeconds(5));
        var update = updateBody("6600001", "john.s@rsu.ac.th", "INACTIVE");
        update.put("firstName", "Johnny");
        asAdmin(put(ADMIN + "/" + id), update).andExpect(status().isOk())
                .andExpect(jsonPath("firstName").value("Johnny"))
                .andExpect(jsonPath("accountStatus").value("INACTIVE"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));

        asAdmin(delete(ADMIN + "/" + id), null).andExpect(status().isNoContent());
        // Soft delete keeps both the credential and the historical status.
        assertThat(db.queryForMap("SELECT is_deleted,account_status FROM students WHERE id=?", id))
                .containsEntry("is_deleted", true).containsEntry("account_status", "INACTIVE");
        assertThat(credentialCount(id)).isOne();
        asAdmin(get(ADMIN + "/" + id), null).andExpect(status().isNotFound());
        asAdmin(delete(ADMIN + "/" + id), null).andExpect(status().isNotFound());
        asAdmin(get(ADMIN + "/summary"), null).andExpect(jsonPath("totalStudents").value(1));
    }

    @Test
    void adminCreateValidatesStatusPasswordsAcademicsAndDuplicates() throws Exception {
        asAdmin(post(ADMIN), createBody("1", "a@rsu.ac.th", "PENDING")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATUS"));
        asAdmin(post(ADMIN), createBody("1", "a@rsu.ac.th", "LOCKED")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATUS"));
        var mismatch = createBody("1", "a@rsu.ac.th", "ACTIVE");
        mismatch.put("confirmPassword", "something else entirely");
        asAdmin(post(ADMIN), mismatch).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PASSWORD_CONFIRMATION_MISMATCH"));
        var wrongDepartment = createBody("1", "a@rsu.ac.th", "ACTIVE");
        wrongDepartment.put("departmentId", departmentB);
        wrongDepartment.put("programId", programB);
        asAdmin(post(ADMIN), wrongDepartment).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACADEMIC_RELATIONSHIP"));
        var wrongProgram = createBody("1", "a@rsu.ac.th", "ACTIVE");
        wrongProgram.put("programId", programB);
        asAdmin(post(ADMIN), wrongProgram).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACADEMIC_RELATIONSHIP"));
        var missingSemester = createBody("1", "a@rsu.ac.th", "ACTIVE");
        missingSemester.put("semesterId", 987654);
        asAdmin(post(ADMIN), missingSemester).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACADEMIC_RELATIONSHIP"));
        assertThat(studentCount()).isZero();

        createActive("1", "a@rsu.ac.th");
        asAdmin(post(ADMIN), createBody("1", "b@rsu.ac.th", "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_CODE_ALREADY_EXISTS"));
        asAdmin(post(ADMIN), createBody("2", "A@RSU.AC.TH", "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void adminCreateRollsBackStudentWhenCredentialInsertFails() throws Exception {
        failCredentialInserts();
        asAdmin(post(ADMIN), createBody("7700001", "rollback@rsu.ac.th", "ACTIVE"))
                .andExpect(status().isServiceUnavailable());
        assertThat(studentCount()).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM student_credentials", Integer.class)).isZero();
    }

    @Test
    void statusTransitionsFollowTheCredentialRules() throws Exception {
        long id = createActive("6600010", "transit@rsu.ac.th");
        for (String status : List.of("INACTIVE", "SUSPENDED", "ACTIVE", "SUSPENDED", "INACTIVE", "ACTIVE")) {
            asAdmin(put(ADMIN + "/" + id), updateBody("6600010", "transit@rsu.ac.th", status)).andExpect(status().isOk())
                    .andExpect(jsonPath("accountStatus").value(status));
            assertThat(credentialCount(id)).isOne();
        }
        for (String from : List.of("ACTIVE", "INACTIVE", "SUSPENDED")) {
            db.update("UPDATE students SET account_status=? WHERE id=?", from, id);
            asAdmin(put(ADMIN + "/" + id), updateBody("6600010", "transit@rsu.ac.th", "PENDING"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATUS"));
            assertThat(accountStatus(id)).isEqualTo(from);
        }

        long pending = pending("6600011", "pending.two@rsu.ac.th", null);
        asAdmin(put(ADMIN + "/" + pending), updateBody("6600011", "pending.two@rsu.ac.th", "ACTIVE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATUS"));
        assertThat(accountStatus(pending)).isEqualTo("PENDING");
        asAdmin(put(ADMIN + "/" + pending), updateBody("6600011", "pending.two@rsu.ac.th", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("forcePasswordChange").value(false));

        // Corrupted legacy row: ACTIVE without a credential is reported, never silently repaired.
        long broken = pending("6600012", "broken@rsu.ac.th", null);
        db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", broken);
        asAdmin(put(ADMIN + "/" + broken), updateBody("6600012", "broken@rsu.ac.th", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATE"));
    }

    @Test
    void passwordResetKeepsStatusAndRequiresCredential() throws Exception {
        long id = createActive("6600020", "reset@rsu.ac.th");
        changeStatus(id, "6600020", "reset@rsu.ac.th", "INACTIVE");
        db.update("UPDATE student_credentials SET failed_login_attempts=3, force_password_change=FALSE WHERE student_id=?", id);
        asAdmin(post(ADMIN + "/" + id + "/reset-password"),
                Map.of("temporaryPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
        assertThat(accountStatus(id)).isEqualTo("INACTIVE");
        var credential = db.queryForMap("SELECT * FROM student_credentials WHERE student_id=?", id);
        assertThat(credential).containsEntry("force_password_change", true).containsEntry("failed_login_attempts", 0);
        assertThat(encoder.matches(NEW_PASSWORD, (String) credential.get("password_hash"))).isTrue();

        long pending = pending("6600021", "reset.pending@rsu.ac.th", null);
        asAdmin(post(ADMIN + "/" + pending + "/reset-password"),
                Map.of("temporaryPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("STUDENT_NOT_REGISTERED"));
        assertThat(credentialCount(pending)).isZero();
        asAdmin(post(ADMIN + "/" + id + "/reset-password"),
                Map.of("temporaryPassword", NEW_PASSWORD, "confirmPassword", "different password here"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PASSWORD_CONFIRMATION_MISMATCH"));
    }

    // ---------------------------------------------------------------- Registration

    @Test
    void newStudentSelfRegistersThenSetsAcademicProfile() throws Exception {
        var body = newStudentBody("6700001", "new.student@rsu.ac.th");
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isCreated())
                .andExpect(jsonPath("studentCode").value("6700001"))
                .andExpect(jsonPath("firstName").value("Nadia"))
                .andExpect(jsonPath("accountStatus").value("ACTIVE"));
        long id = db.queryForObject("SELECT id FROM students WHERE student_code='6700001'", Long.class);
        assertThat(db.queryForMap("SELECT faculty_id,department_id,program_id,created_by FROM students WHERE id=?", id))
                .containsEntry("faculty_id", null).containsEntry("department_id", null)
                .containsEntry("program_id", null).containsEntry("created_by", null);
        assertThat(db.queryForObject("SELECT force_password_change FROM student_credentials WHERE student_id=?",
                Boolean.class, id)).isFalse();
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_ALREADY_REGISTERED"));

        var noNames = newStudentBody("6700002", "no.names@rsu.ac.th");
        noNames.remove("firstName");
        mvc.perform(json(post(AUTH + "/register"), noNames)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        mvc.perform(json(post(AUTH + "/register"), newStudentBody("6700001", "other@rsu.ac.th")))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("STUDENT_CODE_ALREADY_EXISTS"));
        mvc.perform(json(post(AUTH + "/register"), newStudentBody("6700003", "new.student@rsu.ac.th")))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("STUDENT_EMAIL_ALREADY_EXISTS"));
        mvc.perform(json(post(AUTH + "/register"), newStudentBody("6700004", "someone@gmail.com")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_STUDENT_UNIVERSITY_EMAIL"));
        assertThat(studentCount()).isOne();

        String access = tree(login("new.student@rsu.ac.th", NEW_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false))
                .andExpect(jsonPath("user.facultyId").doesNotExist()).andReturn()).get("accessToken").asText();
        asAdmin(get(ADMIN + "?search=6700001"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].facultyId").doesNotExist());
        asAdmin(get(ADMIN + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("programId").doesNotExist());

        var profile = new LinkedHashMap<String, Object>();
        profile.put("firstName", "Nadia");
        profile.put("lastName", "Saelim");
        profile.put("phoneNumber", "0811111111");
        profile.put("dateOfBirth", "2005-03-04");
        profile.put("facultyId", facultyA);
        profile.put("departmentId", departmentB);
        profile.put("programId", programB);
        profile.put("semesterId", semester);
        profile.put("academicYear", 2026);
        profile.put("enrollmentYear", 2026);
        asStudent(put("/api/v1/student/profile"), access, profile).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACADEMIC_RELATIONSHIP"));
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk());
        profile.put("departmentId", departmentA);
        profile.put("programId", programA);
        asStudent(put("/api/v1/student/profile"), access, profile).andExpect(status().isOk())
                .andExpect(jsonPath("facultyCode").value("ENG"))
                .andExpect(jsonPath("programCode").value("BSCS"))
                .andExpect(jsonPath("lastName").value("Saelim"));
        // The cached profile from the earlier /me call is evicted after commit.
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk())
                .andExpect(jsonPath("departmentCode").value("CS"));
        profile.put("accountStatus", "ACTIVE");
        asStudent(put("/api/v1/student/profile"), access, profile).andExpect(status().isBadRequest());
        asAdmin(put("/api/v1/student/profile"), profile).andExpect(status().isForbidden());
    }

    @Test
    void concurrentNewStudentRegistrationProducesOneAccount() throws Exception {
        var body = json.writeValueAsBytes(newStudentBody("6700010", "race.new@rsu.ac.th"));
        var statuses = concurrently(3, () -> mvc.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnlyOnce(201);
        assertThat(statuses).allMatch(code -> code == 201 || code == 409);
        assertThat(studentCount()).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM student_credentials", Integer.class)).isOne();
    }

    private Map<String, Object> newStudentBody(String code, String email) {
        var body = registerBody(code, email, LocalDate.of(2005, 3, 4), NEW_PASSWORD);
        body.put("firstName", "Nadia");
        body.put("lastName", "Saelim");
        return body;
    }

    @Test
    void registrationEnforcesExactRsuDomain() throws Exception {
        pending("6600030", "student@rsu.ac.th", null);
        for (String email : List.of("student@gmail.com", "student@mail.rsu.ac.th", "student@rsu.ac.th.example.com")) {
            mvc.perform(json(post(AUTH + "/register"), registerBody("6600030", email, null, NEW_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("INVALID_STUDENT_UNIVERSITY_EMAIL"));
        }
        mvc.perform(json(post(AUTH + "/register"), registerBody("6600030", "  Student@RSU.ac.th ", null, NEW_PASSWORD)))
                .andExpect(status().isCreated()).andExpect(jsonPath("accountStatus").value("ACTIVE"));
    }

    @Test
    void registrationActivatesPendingProfileAndRejectsEveryOtherState() throws Exception {
        long id = pending("6600040", "reg@rsu.ac.th", LocalDate.of(2004, 5, 6));
        var body = registerBody("6600040", "reg@rsu.ac.th", LocalDate.of(2004, 5, 7), NEW_PASSWORD);
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isNotFound());
        body.put("confirmPassword", "not the same password");
        body.put("dateOfBirth", "2004-05-06");
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PASSWORD_CONFIRMATION_MISMATCH"));
        assertThat(accountStatus(id)).isEqualTo("PENDING");
        assertThat(credentialCount(id)).isZero();

        body.put("confirmPassword", NEW_PASSWORD);
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isCreated())
                .andExpect(jsonPath("id").value(id)).andExpect(jsonPath("accountStatus").value("ACTIVE"));
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT force_password_change FROM student_credentials WHERE student_id=?",
                Boolean.class, id)).isFalse();
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_ALREADY_REGISTERED"));

        for (String state : List.of("INACTIVE", "SUSPENDED")) {
            db.update("UPDATE students SET account_status=? WHERE id=?", state, id);
            mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("STUDENT_REGISTRATION_NOT_ALLOWED"));
        }
        db.update("UPDATE students SET account_status='ACTIVE', is_deleted=TRUE WHERE id=?", id);
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("STUDENT_REGISTRATION_NOT_ALLOWED"));

        long corrupted = pending("6600041", "corrupt@rsu.ac.th", null);
        db.update("INSERT INTO student_credentials(student_id,password_hash) VALUES (?,?)", corrupted, encoder.encode(NEW_PASSWORD));
        mvc.perform(json(post(AUTH + "/register"), registerBody("6600041", "corrupt@rsu.ac.th", null, NEW_PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATE"));
        long activeNoCredential = pending("6600042", "nocred@rsu.ac.th", null);
        db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", activeNoCredential);
        mvc.perform(json(post(AUTH + "/register"), registerBody("6600042", "nocred@rsu.ac.th", null, NEW_PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATE"));
    }

    @Test
    void registrationIsAtomicWhenEitherWriteFails() throws Exception {
        long id = pending("6600050", "atomic@rsu.ac.th", null);
        var body = registerBody("6600050", "atomic@rsu.ac.th", null, NEW_PASSWORD);
        failCredentialInserts();
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isServiceUnavailable());
        assertThat(accountStatus(id)).isEqualTo("PENDING");
        assertThat(credentialCount(id)).isZero();
        db.update("DROP TRIGGER fail_student_credentials ON student_credentials");

        db.update("""
                CREATE OR REPLACE FUNCTION fail_student_activation() RETURNS trigger AS $$
                BEGIN IF NEW.account_status = 'ACTIVE' THEN RAISE EXCEPTION 'activation blocked'; END IF; RETURN NEW; END
                $$ LANGUAGE plpgsql""");
        db.update("CREATE TRIGGER fail_student_activation BEFORE UPDATE ON students FOR EACH ROW EXECUTE FUNCTION fail_student_activation()");
        mvc.perform(json(post(AUTH + "/register"), body)).andExpect(status().isServiceUnavailable());
        assertThat(accountStatus(id)).isEqualTo("PENDING");
        assertThat(credentialCount(id)).isZero();
    }

    @Test
    void concurrentRegistrationProducesExactlyOneSuccess() throws Exception {
        long id = pending("6600060", "race@rsu.ac.th", null);
        var body = json.writeValueAsBytes(registerBody("6600060", "race@rsu.ac.th", null, NEW_PASSWORD));
        var statuses = concurrently(4, () -> mvc.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409);
        assertThat(credentialCount(id)).isOne();
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void concurrentAdminCreatesHitTheUniqueConstraintCleanly() throws Exception {
        var statuses = concurrently(3, () -> asAdmin(post(ADMIN), createBody("8800001", "dupe@rsu.ac.th", "ACTIVE"))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409);
        assertThat(studentCount()).isOne();
    }

    // ---------------------------------------------------------------- Authentication

    @Test
    void adminCreatedStudentMustChangePasswordBeforeNormalAccess() throws Exception {
        long id = createActive("6600070", "first.login@rsu.ac.th");
        var login = login("first.login@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("tokenType").value("Bearer"))
                .andExpect(jsonPath("forcePasswordChange").value(true))
                .andExpect(jsonPath("user.id").value(id))
                .andExpect(jsonPath("user.programCode").value("BSCS")).andReturn();
        String access = tree(login).get("accessToken").asText();
        String refresh = tree(login).get("refreshToken").asText();
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        var claims = claims(access);
        assertThat(claims.get("sub").asText()).isEqualTo(Long.toString(id));
        assertThat(claims.get("studentId").asLong()).isEqualTo(id);
        assertThat(claims.get("studentCode").asText()).isEqualTo("6600070");
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("STUDENT");
        assertThat(claims.has("jti") && claims.has("iat") && claims.has("exp")).isTrue();

        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(true));
        asStudent(get("/api/v1/student/timetable"), access, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        asStudent(post(AUTH + "/change-password"), access, Map.of("currentPassword", "wrong password value!!",
                "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("CURRENT_PASSWORD_INCORRECT"));
        asStudent(post(AUTH + "/change-password"), access, Map.of("currentPassword", TEMP_PASSWORD,
                "newPassword", NEW_PASSWORD)).andExpect(status().isNoContent());
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT force_password_change FROM student_credentials WHERE student_id=?",
                Boolean.class, id)).isFalse();
        // Every session is revoked, so both old tokens stop working.
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isUnauthorized());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", refresh))).andExpect(status().isUnauthorized());
        login("first.login@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isUnauthorized());

        String fresh = tree(login("first.login@rsu.ac.th", NEW_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false)).andReturn()).get("accessToken").asText();
        asStudent(get(AUTH + "/me"), fresh, null).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false));
        // Normal access: the restriction is lifted, so an unknown student route is simply not found.
        asStudent(get("/api/v1/student/timetable"), fresh, null).andExpect(status().isNotFound());
    }

    @Test
    void selfRegisteredStudentLoginRefreshMeLogout() throws Exception {
        long id = pending("6600080", "self@rsu.ac.th", null);
        mvc.perform(json(post(AUTH + "/register"), registerBody("6600080", "self@rsu.ac.th", null, NEW_PASSWORD)))
                .andExpect(status().isCreated());
        var login = tree(login("self@rsu.ac.th", NEW_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false)).andReturn());
        String refresh = login.get("refreshToken").asText();
        assertThat(redis.opsForSet().size("student:auth:sessions:" + id)).isOne();

        clock.advance(Duration.ofSeconds(2));
        var rotated = tree(mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", refresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("user.id").value(id)).andReturn());
        String access = rotated.get("accessToken").asText();
        String nextRefresh = rotated.get("refreshToken").asText();
        assertThat(nextRefresh).isNotEqualTo(refresh);

        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk())
                .andExpect(jsonPath("studentCode").value("6600080"))
                .andExpect(jsonPath("departmentName").value("Computer Science"));
        assertThat(redis.hasKey("student:mobile:profile:" + id)).isTrue();
        asStudent(post(AUTH + "/logout"), access, null).andExpect(status().isNoContent());
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isUnauthorized());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", nextRefresh))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_REFRESH_TOKEN"));

        // Logout from every device revokes all of the student's sessions at once.
        String phone = tree(login("self@rsu.ac.th", NEW_PASSWORD).andReturn()).get("accessToken").asText();
        var tablet = tree(login("self@rsu.ac.th", NEW_PASSWORD).andReturn());
        assertThat(redis.opsForSet().size("student:auth:sessions:" + id)).isEqualTo(2);
        asStudent(post(AUTH + "/logout?allDevices=true"), phone, null).andExpect(status().isNoContent());
        asStudent(get(AUTH + "/me"), phone, null).andExpect(status().isUnauthorized());
        asStudent(get(AUTH + "/me"), tablet.get("accessToken").asText(), null).andExpect(status().isUnauthorized());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", tablet.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        assertThat(redis.hasKey("student:auth:sessions:" + id)).isFalse();
    }

    @Test
    void replayedRefreshTokenRevokesTheSession() throws Exception {
        createActive("6600090", "replay@rsu.ac.th");
        String first = tree(login("replay@rsu.ac.th", TEMP_PASSWORD).andReturn()).get("refreshToken").asText();
        String second = tree(mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", first)))
                .andExpect(status().isOk()).andReturn()).get("refreshToken").asText();
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", first))).andExpect(status().isUnauthorized());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", second))).andExpect(status().isUnauthorized());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", "garbage"))).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRespectsAccountStateMatrix() throws Exception {
        long id = createActive("6600100", "matrix@rsu.ac.th");
        for (String state : List.of("INACTIVE", "SUSPENDED", "PENDING")) {
            db.update("UPDATE students SET account_status=? WHERE id=?", state, id);
            login("matrix@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("STUDENT_ACCOUNT_NOT_ACTIVE"));
        }
        db.update("UPDATE students SET account_status='ACTIVE', is_deleted=TRUE WHERE id=?", id);
        login("matrix@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_CREDENTIALS"));
        login("nobody@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isUnauthorized());

        long broken = pending("6600101", "broken.login@rsu.ac.th", null);
        db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", broken);
        login("broken.login@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INVALID_STUDENT_ACCOUNT_STATE"));
    }

    @Test
    void failedLoginsLockTemporarilyWithoutChangingStatus() throws Exception {
        long id = createActive("6600110", "lock@rsu.ac.th");
        for (int i = 0; i < 5; i++)
            login("lock@rsu.ac.th", "definitely the wrong password").andExpect(status().isUnauthorized());
        var credential = db.queryForMap("SELECT failed_login_attempts,locked_until FROM student_credentials WHERE student_id=?", id);
        assertThat(credential.get("failed_login_attempts")).isEqualTo(5);
        assertThat(credential.get("locked_until")).isNotNull();
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
        login("lock@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isLocked())
                .andExpect(jsonPath("code").value("STUDENT_ACCOUNT_LOCKED"));

        clock.advance(Duration.ofMinutes(16));
        login("lock@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isOk());
        assertThat(db.queryForMap("SELECT failed_login_attempts,locked_until FROM student_credentials WHERE student_id=?", id))
                .containsEntry("failed_login_attempts", 0).containsEntry("locked_until", null);
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void concurrentFailedLoginsCountEveryAttempt() throws Exception {
        long id = createActive("6600120", "parallel@rsu.ac.th");
        var statuses = concurrently(4, () -> login("parallel@rsu.ac.th", "definitely the wrong password")
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(401);
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM student_credentials WHERE student_id=?",
                Integer.class, id)).isEqualTo(4);
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void concurrentPasswordChangesAndResetsSerialize() throws Exception {
        long id = createActive("6600130", "serial@rsu.ac.th");
        String access = tree(login("serial@rsu.ac.th", TEMP_PASSWORD).andReturn()).get("accessToken").asText();
        var statuses = concurrently(2, () -> asStudent(post(AUTH + "/change-password"), access,
                Map.of("currentPassword", TEMP_PASSWORD, "newPassword", NEW_PASSWORD)).andReturn().getResponse().getStatus());
        // The loser either waits for the row lock and sees the new hash, or finds its session already revoked.
        assertThat(statuses).containsOnlyOnce(204);
        assertThat(statuses).allMatch(code -> code == 204 || code == 400 || code == 401);

        var resets = concurrently(2, () -> asAdmin(post(ADMIN + "/" + id + "/reset-password"),
                Map.of("temporaryPassword", TEMP_PASSWORD, "confirmPassword", TEMP_PASSWORD))
                .andReturn().getResponse().getStatus());
        assertThat(resets).containsOnly(204);
        assertThat(credentialCount(id)).isOne();
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void suspensionRevokesSessionsAndPostgresOverridesStaleRedis() throws Exception {
        long id = createActive("6600140", "suspend@rsu.ac.th");
        var login = tree(login("suspend@rsu.ac.th", TEMP_PASSWORD).andReturn());
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();
        changeStatus(id, "6600140", "suspend@rsu.ac.th", "SUSPENDED");
        assertThat(redis.hasKey("student:auth:sessions:" + id)).isFalse();
        assertThat(credentialCount(id)).isOne();
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isUnauthorized());
        login("suspend@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isForbidden());
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", refresh))).andExpect(status().isUnauthorized());

        // Bypass the application so Redis still holds a live session and a cached ACTIVE state.
        changeStatus(id, "6600140", "suspend@rsu.ac.th", "ACTIVE");
        for (String state : List.of("INACTIVE", "SUSPENDED")) {
            String token = tree(login("suspend@rsu.ac.th", TEMP_PASSWORD).andExpect(status().isOk()).andReturn())
                    .get("refreshToken").asText();
            db.update("UPDATE students SET account_status=? WHERE id=?", state, id);
            mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", token))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("STUDENT_ACCOUNT_NOT_ACTIVE"));
            db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", id);
        }
        String token = tree(login("suspend@rsu.ac.th", TEMP_PASSWORD).andReturn()).get("refreshToken").asText();
        db.update("UPDATE students SET is_deleted=TRUE WHERE id=?", id);
        mvc.perform(json(post(AUTH + "/refresh"), Map.of("refreshToken", token))).andExpect(status().isUnauthorized());
    }

    @Test
    void redisSideEffectsRunOnlyAfterCommit() throws Exception {
        long id = createActive("6600150", "cache@rsu.ac.th");
        createActive("6600151", "other@rsu.ac.th");
        String access = tree(login("cache@rsu.ac.th", TEMP_PASSWORD).andReturn()).get("accessToken").asText();
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk());
        assertThat(redis.hasKey("student:mobile:profile:" + id)).isTrue();
        assertThat(redis.hasKey("student:auth:account:" + id)).isTrue();

        // A rolled-back update (duplicate code) leaves caches and sessions untouched.
        asAdmin(put(ADMIN + "/" + id), updateBody("6600151", "cache@rsu.ac.th", "SUSPENDED"))
                .andExpect(status().isConflict());
        assertThat(redis.hasKey("student:mobile:profile:" + id)).isTrue();
        assertThat(redis.hasKey("student:auth:sessions:" + id)).isTrue();

        var rename = updateBody("6600150", "cache@rsu.ac.th", "ACTIVE");
        rename.put("lastName", "Renamed");
        asAdmin(put(ADMIN + "/" + id), rename).andExpect(status().isOk());
        assertThat(redis.hasKey("student:mobile:profile:" + id)).isFalse();
        asStudent(get(AUTH + "/me"), access, null).andExpect(status().isOk()).andExpect(jsonPath("lastName").value("Renamed"));
    }

    @Test
    void routesAreIsolatedBetweenAdminsAndStudents() throws Exception {
        createActive("6600160", "iso@rsu.ac.th");
        String access = tree(login("iso@rsu.ac.th", TEMP_PASSWORD).andReturn()).get("accessToken").asText();
        asStudent(get(ADMIN), access, null).andExpect(status().isForbidden());
        asStudent(get("/api/v1/admin/auth/me"), access, null).andExpect(status().isForbidden());
        asAdmin(get(AUTH + "/me"), null).andExpect(status().isForbidden());
        mvc.perform(get(AUTH + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        setRole("SUPER_ADMIN");
        admin = adminLogin();
        asAdmin(get(ADMIN + "/summary"), null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private long createActive(String code, String email) throws Exception {
        return tree(asAdmin(post(ADMIN), createBody(code, email, "ACTIVE")).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }

    private void changeStatus(long id, String code, String email, String status) throws Exception {
        asAdmin(put(ADMIN + "/" + id), updateBody(code, email, status)).andExpect(status().isOk());
    }

    private long pending(String code, String email, LocalDate dateOfBirth) {
        return id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,date_of_birth,faculty_id,
                    department_id,program_id,account_status) VALUES (?,?,'Pending','Student',?,?,?,?,'PENDING') RETURNING id
                """, code, email, dateOfBirth, facultyA, departmentA, programA);
    }

    private Map<String, Object> createBody(String code, String email, String status) {
        var body = updateBody(code, email, status);
        body.put("temporaryPassword", TEMP_PASSWORD);
        body.put("confirmPassword", TEMP_PASSWORD);
        return body;
    }

    private Map<String, Object> updateBody(String code, String email, String status) {
        var body = new LinkedHashMap<String, Object>();
        body.put("studentCode", code);
        body.put("universityEmail", email);
        body.put("firstName", "John");
        body.put("lastName", "Smith");
        body.put("phoneNumber", "0812345678");
        body.put("dateOfBirth", "2004-01-02");
        body.put("facultyId", facultyA);
        body.put("departmentId", departmentA);
        body.put("programId", programA);
        body.put("semesterId", semester);
        body.put("academicYear", 2026);
        body.put("enrollmentYear", 2025);
        body.put("accountStatus", status);
        return body;
    }

    private Map<String, Object> registerBody(String code, String email, LocalDate dateOfBirth, String password) {
        var body = new LinkedHashMap<String, Object>();
        body.put("studentCode", code);
        body.put("universityEmail", email);
        if (dateOfBirth != null) body.put("dateOfBirth", dateOfBirth.toString());
        body.put("password", password);
        body.put("confirmPassword", password);
        return body;
    }

    private void failCredentialInserts() {
        db.update("""
                CREATE OR REPLACE FUNCTION fail_student_credentials() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'credential insert blocked'; END
                $$ LANGUAGE plpgsql""");
        db.update("CREATE TRIGGER fail_student_credentials BEFORE INSERT ON student_credentials FOR EACH ROW EXECUTE FUNCTION fail_student_credentials()");
    }

    private <T> List<T> concurrently(int count, Callable<T> task) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(json(post(AUTH + "/login"), Map.of("universityEmail", email, "password", password)));
    }

    private String adminLogin() throws Exception {
        var result = mvc.perform(json(post("/api/v1/admin/auth/login"),
                Map.of("email", "student.actor@example.test", "password", ADMIN_PASSWORD))).andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }

    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return asStudent(builder, admin, body);
    }

    private ResultActions asStudent(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + token);
        return mvc.perform(body == null ? builder : json(builder, body));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    }

    private JsonNode claims(String jwt) {
        return json.readTree(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8));
    }

    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private String accountStatus(long id) { return db.queryForObject("SELECT account_status FROM students WHERE id=?", String.class, id); }
    private int credentialCount(long id) {
        return db.queryForObject("SELECT count(*) FROM student_credentials WHERE student_id=?", Integer.class, id);
    }
    private int studentCount() { return db.queryForObject("SELECT count(*) FROM students", Integer.class); }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
