package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.time.*;
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

/** Spec file 40: CGPA, earned credits, meetings, next class, deadlines, home summary. All grades here are test values. */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class StudentAcademicDataIntegrationTests extends IntegrationSupport {
    private static final String ACADEMIC = "/api/v1/student/academic";
    private static final String RESULTS = "/api/v1/admin/academic/course-results";
    private static final String ADMIN_PASSWORD = "academic data actor password";
    private static final String PASSWORD = "student academic data password";
    // Wednesday 2026-11-04 10:00 in Bangkok.
    private static final Instant WEDNESDAY_10_BANGKOK = Instant.parse("2026-11-04T03:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;

    private long actor, faculty, department, program, lecturerA, lecturerB, current, past;
    private long courseA, courseB, courseC, sectionA, sectionB, sectionC, sectionAPast, sectionOther;
    private long studentA, studentB, scheme, gradeA, gradeB, gradeF, gradeP, gradeI, gradeW;
    private long site, building, room1103, roomLab;
    private String hash;

    @BeforeEach
    void fixture() {
        clock.set(WEDNESDAY_10_BANGKOK);
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clean();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actor = admin("data.super@example.test", "SUPER_ADMIN");
        hash = encoder.encode(PASSWORD);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ICT','College of ICT','ไอซีที',?) RETURNING id", actor);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actor);
        program = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSC-CS','Computer Science','Bachelor of Science',?,?) RETURNING id", department, actor);
        lecturerA = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('เอ','Lecturer A',?,?,?) RETURNING id", faculty, department, actor);
        lecturerB = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('บี','Lecturer B',?,?,?) RETURNING id", faculty, department, actor);
        past = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2025,'เทอม 1','Semester 1',?) RETURNING id", actor);
        current = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'เทอม 1','Semester 1',?) RETURNING id", actor);
        // Spec file 42: sections need an academic term and published meetings need a campus room.
        term(past, "2568", "2025-08-01", "2025-12-31");
        term(current, "2569", "2026-08-01", "2027-05-31");
        site = id("INSERT INTO campus_sites(site_code,site_name) VALUES ('MAIN','Main Campus') RETURNING id");
        building = id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,'B11','Building 11',12) RETURNING id", site);
        room1103 = room("1103");
        roomLab = room("Lab 1108");
        courseA = course("CSC-452", "Mobile Programming", 3);
        courseB = course("CSC-311", "Data Structures", 1);
        courseC = course("CSC-999", "Other Course", 3);
        sectionA = section(courseA, "02", lecturerA, current);
        sectionB = section(courseB, "01", lecturerB, current);
        sectionC = section(courseC, "01", lecturerA, past);
        sectionAPast = section(courseA, "01", lecturerA, past);
        sectionOther = section(courseC, "02", lecturerB, current);
        studentA = student("6600101", 3);
        studentB = student("6600102", 2);
        scheme = id("INSERT INTO grading_schemes(scheme_code,scheme_name) VALUES ('TEST-ONLY','Test scheme, not university policy') RETURNING id");
        gradeA = grade("A", "4.00", true, true, true);
        gradeB = grade("B", "3.00", true, true, true);
        gradeF = grade("F", "0.00", true, false, false);
        gradeP = grade("P", null, false, true, true);
        gradeI = grade("I", null, false, false, false);
        gradeW = grade("W", null, false, false, false);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        clean();
    }

    // CGPA and earned credits

    @Test
    void noFinalizedResultsLeaveMetricsNullButStillCountCurrentCourses() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        enroll(studentA, sectionB, "ACTIVE");
        // An ACTIVE row from an earlier term is history, not a current course.
        enroll(studentA, sectionC, "ACTIVE");
        // Pending and withdrawn enrollments never count.
        enroll(studentA, sectionOther, "PENDING");
        get(student("6600101"), ACADEMIC + "/progress").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cgpa").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.creditsEarned").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentCourses").value(2))
                .andExpect(jsonPath("$.currentEnrolledCredits").value(4))
                .andExpect(jsonPath("$.studyYear").value(3))
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.dataStatus").value("PARTIAL"));
    }

    @Test
    void finalizedResultsGiveWeightedCgpaAndEarnedCredits() throws Exception {
        String admin = admin();
        finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeA); // 4.00 x 3
        finalized(admin, enroll(studentA, sectionB, "ACTIVE"), gradeB); // 3.00 x 1
        get(student("6600101"), ACADEMIC + "/progress").andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(3.75))
                .andExpect(jsonPath("$.creditsEarned").value(4))
                .andExpect(jsonPath("$.dataStatus").value("AVAILABLE"));
    }

    @Test
    void failPassIncompleteDraftAndWithdrawnFollowTheirGradeDefinitions() throws Exception {
        String admin = admin();
        finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeF); // counts in GPA, earns nothing
        finalized(admin, enroll(studentA, sectionB, "ACTIVE"), gradeP); // earns 1, outside GPA
        finalized(admin, enroll(studentA, sectionC, "ACTIVE"), gradeI); // neither
        long draft = enroll(studentA, sectionOther, "ACTIVE");
        create(admin, draft, gradeA).andExpect(status().isCreated()).andExpect(jsonPath("$.resultStatus").value("DRAFT"));
        get(student("6600101"), ACADEMIC + "/progress").andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(0.0))
                .andExpect(jsonPath("$.creditsEarned").value(1));
        // A withdrawn enrollment with a W grade adds nothing either.
        long withdrawn = enroll(studentB, sectionA, "WITHDRAWN");
        finalized(admin, withdrawn, gradeW);
        get(student("6600102"), ACADEMIC + "/progress").andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.creditsEarned").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void repeatedCourseFollowsTheConfiguredPolicyAndIsUnknownWithoutOne() throws Exception {
        String admin = admin();
        finalized(admin, enroll(studentA, sectionAPast, "ACTIVE"), gradeF);
        finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeB);
        String token = student("6600101");
        get(token, ACADEMIC + "/progress")
                .andExpect(jsonPath("$.cgpa").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.creditsEarned").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dataStatus").value("PARTIAL"));
        db.update("UPDATE grading_schemes SET repeat_attempt_policy='LATEST_ATTEMPT' WHERE id=?", scheme);
        get(token, ACADEMIC + "/progress")
                .andExpect(jsonPath("$.cgpa").value(3.0))
                .andExpect(jsonPath("$.creditsEarned").value(3));
        db.update("UPDATE grading_schemes SET repeat_attempt_policy='ALL_ATTEMPTS' WHERE id=?", scheme);
        get(token, ACADEMIC + "/progress")
                .andExpect(jsonPath("$.cgpa").value(1.5))
                .andExpect(jsonPath("$.creditsEarned").value(3));
    }

    @Test
    void courseCreditSnapshotStaysAfterTheCourseChanges() throws Exception {
        String admin = admin();
        long result = finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeA);
        finalized(admin, enroll(studentA, sectionB, "ACTIVE"), gradeB);
        db.update("UPDATE courses SET credit_hours=6 WHERE id=?", courseA);
        get(student("6600101"), ACADEMIC + "/progress")
                .andExpect(jsonPath("$.cgpa").value(3.75))
                .andExpect(jsonPath("$.creditsEarned").value(4));
        assertThat(db.queryForObject("SELECT credits_attempted FROM student_course_results WHERE id=?", java.math.BigDecimal.class, result))
                .isEqualByComparingTo("3");
    }

    @Test
    void creditSnapshotFreezesAtFinalizationNotAtDraft() throws Exception {
        String admin = admin();
        long result = idOf(create(admin, enroll(studentA, sectionA, "ACTIVE"), gradeA).andExpect(status().isCreated()));
        db.update("UPDATE courses SET credit_hours=5 WHERE id=?", courseA);
        finalize(admin, result).andExpect(status().isOk()).andExpect(jsonPath("$.creditsAttempted").value(5));
    }

    @Test
    void correctionNeedsAReasonIsAuditedAndChangesTheCalculation() throws Exception {
        String admin = admin();
        long result = finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeB);
        String token = student("6600101");
        get(token, ACADEMIC + "/progress").andExpect(jsonPath("$.cgpa").value(3.0));
        put(admin, RESULTS + "/" + result, Map.of("gradeDefinitionId", gradeA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORRECTION_REASON_REQUIRED"));
        put(admin, RESULTS + "/" + result, Map.of("gradeDefinitionId", gradeA, "correctionReason", "Marking error"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gradeCode").value("A"))
                .andExpect(jsonPath("$.resultStatus").value("FINALIZED"));
        get(token, ACADEMIC + "/progress").andExpect(jsonPath("$.cgpa").value(4.0));
        var audit = db.queryForMap("SELECT * FROM student_course_result_audits WHERE student_course_result_id=? AND action='CORRECTED'", result);
        assertThat(audit.get("previous_grade_definition_id")).isEqualTo(gradeB);
        assertThat(audit.get("new_grade_definition_id")).isEqualTo(gradeA);
        assertThat(audit.get("reason")).isEqualTo("Marking error");
        assertThat(audit.get("changed_by")).isEqualTo(actor);
        assertThat(db.queryForObject("SELECT count(*) FROM student_course_result_audits WHERE student_course_result_id=?", Long.class, result)).isEqualTo(3);
    }

    @Test
    void studentsStayIsolated() throws Exception {
        String admin = admin();
        finalized(admin, enroll(studentA, sectionA, "ACTIVE"), gradeA);
        enroll(studentB, sectionB, "ACTIVE");
        meeting(sectionA, 5, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        assignment(sectionA, "A's work", "2026-11-05T12:00:00+07:00", "PUBLISHED");
        String token = student("6600102");
        get(token, ACADEMIC + "/progress")
                .andExpect(jsonPath("$.cgpa").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentCourses").value(1))
                .andExpect(jsonPath("$.studyYear").value(2));
        get(token, ACADEMIC + "/next-class").andExpect(status().isOk()).andExpect(content().string("null"));
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.total").value(0));
        // A student id in the query is simply not an input.
        get(token, ACADEMIC + "/progress?studentId=" + studentA).andExpect(jsonPath("$.currentCourses").value(1));
    }

    // Grade administration

    @Test
    void gradeApisAreRestrictedToAcademicAdministrators() throws Exception {
        long enrollment = enroll(studentA, sectionA, "ACTIVE");
        var body = Map.of("enrollmentId", enrollment, "gradeDefinitionId", gradeA);
        post(student("6600101"), RESULTS, body).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(RESULTS)).andExpect(status().isUnauthorized());
        admin("data.plain@example.test", "ADMIN");
        post(adminToken("data.plain@example.test"), RESULTS, body).andExpect(status().isForbidden());
        admin("data.academic@example.test", "ACADEMIC_ADMIN");
        String academic = adminToken("data.academic@example.test");
        long result = idOf(post(academic, RESULTS, body).andExpect(status().isCreated()));
        get(academic, RESULTS + "?studentId=" + studentA + "&status=DRAFT").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(result))
                .andExpect(jsonPath("$.content[0].courseCode").value("CSC-452"));
        get(academic, RESULTS + "?unknown=1").andExpect(status().isBadRequest());
    }

    @Test
    void finalizationValidatesEveryPrecondition() throws Exception {
        String admin = admin();
        long enrollment = enroll(studentA, sectionA, "ACTIVE");
        create(admin, 999999, gradeA).andExpect(status().isNotFound());
        create(admin, enrollment, 999999).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRADE_DEFINITION_NOT_FOUND"));
        create(admin, enroll(studentA, sectionB, "PENDING"), gradeA).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_RESULT_ENROLLMENT_NOT_ELIGIBLE"));
        db.update("UPDATE grade_definitions SET is_active=FALSE WHERE id=?", gradeI);
        create(admin, enrollment, gradeI).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_DEFINITION_NOT_USABLE"));
        long result = idOf(create(admin, enrollment, gradeA).andExpect(status().isCreated()));
        create(admin, enrollment, gradeB).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_RESULT_ALREADY_EXISTS"));
        db.update("UPDATE grading_schemes SET is_active=FALSE WHERE id=?", scheme);
        finalize(admin, result).andExpect(status().isBadRequest());
        db.update("UPDATE grading_schemes SET is_active=TRUE WHERE id=?", scheme);
        finalize(admin, result).andExpect(status().isOk()).andExpect(jsonPath("$.finalizedBy").value(actor));
        finalize(admin, result).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_RESULT_ALREADY_FINALIZED"));
        finalize(admin, 999999).andExpect(status().isNotFound());
        // The grade must not vanish through the enrollment either.
        mvc.perform(auth(delete("/api/v1/admin/enrollments/" + enrollment), admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_HAS_COURSE_RESULT"));
    }

    // Study year and profile

    @Test
    void adminSetsStudyYearAndMobileCannot() throws Exception {
        String admin = admin();
        var update = new HashMap<String, Object>(Map.of("studentCode", "6600102", "universityEmail", "6600102@rsu.ac.th",
                "firstName", "Second", "lastName", "Student", "facultyId", faculty, "departmentId", department,
                "programId", program, "accountStatus", "ACTIVE"));
        update.put("semesterId", current);
        update.put("studyYear", 4);
        put(admin, "/api/v1/admin/students/" + studentB, update).andExpect(status().isOk()).andExpect(jsonPath("$.studyYear").value(4));
        update.put("studyYear", 11);
        put(admin, "/api/v1/admin/students/" + studentB, update).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT study_year FROM students WHERE id=?", Integer.class, studentB)).isEqualTo(4);
        String token = student("6600102");
        put(token, "/api/v1/student/profile", Map.of("studyYear", 9)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT study_year FROM students WHERE id=?", Integer.class, studentB)).isEqualTo(4);
    }

    @Test
    void profileKeepsItsFieldsAndAddsStudyYearAndDegree() throws Exception {
        get(student("6600101"), ACADEMIC + "/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCode").value("6600101"))
                .andExpect(jsonPath("$.program.nameEn").value("Computer Science"))
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.studyYear").value(3))
                .andExpect(jsonPath("$.degreeLevel").value("Bachelor of Science"));
    }

    // Meetings

    @Test
    void meetingAdministrationValidatesAndSoftDeletes() throws Exception {
        String admin = admin();
        String base = "/api/v1/admin/course-sections/" + sectionA + "/meetings";
        long lecture = idOf(post(admin, base, meetingBody("LECTURE", 1, "09:00", "11:45")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("09:00")).andExpect(jsonPath("$.dayName").value("MONDAY")));
        post(admin, base, meetingBody("LAB", 3, "09:00", "11:45")).andExpect(status().isCreated());
        // Adjacent is fine, overlapping is not.
        post(admin, base, meetingBody("TUTORIAL", 1, "11:45", "12:30")).andExpect(status().isCreated());
        post(admin, base, meetingBody("LAB", 1, "11:00", "12:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMETABLE_SECTION_CONFLICT"));
        post(admin, base, meetingBody("LAB", 2, "11:00", "10:00")).andExpect(status().isBadRequest());
        post(admin, base, meetingBody("LAB", 8, "09:00", "10:00")).andExpect(status().isBadRequest());
        post(admin, base, meetingBody("SEMINAR", 2, "09:00", "10:00")).andExpect(status().isBadRequest());
        var badDates = new HashMap<>(meetingBody("LAB", 2, "09:00", "10:00"));
        badDates.put("effectiveTo", "2026-01-01");
        post(admin, base, badDates).andExpect(status().isBadRequest());
        var room = new HashMap<>(meetingBody("LAB", 2, "09:00", "10:00"));
        room.put("roomId", 999999);
        post(admin, base, room).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_NOT_FOUND"));
        // Publishing (the legacy default) needs a campus room; a label alone is not a location.
        var labelOnly = new HashMap<>(meetingBody("LAB", 2, "09:00", "10:00"));
        labelOnly.remove("roomId");
        post(admin, base, labelOnly).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_PUBLISH_NOT_ALLOWED"));
        labelOnly.put("active", false);
        post(admin, base, labelOnly).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.roomLabel").value("1103"));
        post(admin, "/api/v1/admin/course-sections/999999/meetings", meetingBody("LAB", 2, "09:00", "10:00")).andExpect(status().isNotFound());
        put(admin, base + "/" + lecture, meetingBody("LECTURE", 1, "08:00", "09:00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("08:00"));
        get(admin, base).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(auth(delete(base + "/" + lecture), admin)).andExpect(status().isNoContent());
        get(admin, base).andExpect(jsonPath("$.length()").value(3));
        assertThat(db.queryForObject("SELECT is_deleted FROM course_section_meetings WHERE id=?", Boolean.class, lecture)).isTrue();
        put(admin, "/api/v1/admin/course-sections/" + sectionB + "/meetings/" + lecture, meetingBody("LECTURE", 1, "08:00", "09:00"))
                .andExpect(status().isNotFound());
        post(student("6600101"), base, meetingBody("LAB", 2, "09:00", "10:00")).andExpect(status().isForbidden());
    }

    @Test
    void coursesAndScheduleListMeetingsAndLegacyFieldsStay() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        meeting(sectionA, 3, "09:00", "11:45", "2026-08-01", "2026-12-31", "Lab 1108", true);
        meeting(sectionA, 1, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        meeting(sectionA, 5, "09:00", "11:45", "2026-08-01", "2026-12-31", "hidden", false);
        String token = student("6600101");
        get(token, ACADEMIC + "/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseCode").value("CSC-452"))
                .andExpect(jsonPath("$[0].creditHours").value(3))
                .andExpect(jsonPath("$[0].sectionNumber").value("02"))
                .andExpect(jsonPath("$[0].lecturerName").value("Lecturer A"))
                .andExpect(jsonPath("$[0].meetings.length()").value(2))
                .andExpect(jsonPath("$[0].meetings[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].meetings[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[0].meetings[0].endTime").value("11:45"))
                .andExpect(jsonPath("$[0].meetings[0].roomName").value("1103"))
                .andExpect(jsonPath("$[0].meetings[0].dayOfWeekNumber").value(1))
                .andExpect(jsonPath("$[0].meetings[0].buildingName").value("Building 11"))
                .andExpect(jsonPath("$[0].meetings[0].roomId").value(room1103))
                .andExpect(jsonPath("$[0].meetings[1].meetingType").value("LECTURE"))
                .andExpect(jsonPath("$[0].meetings[1].roomName").value("Lab 1108"));
        get(token, ACADEMIC + "/schedule").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meetings.length()").value(2));
    }

    @Test
    void currentSemesterSummaryNeverDoubleCounts() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        enroll(studentA, sectionB, "ACTIVE");
        enroll(studentA, sectionC, "ACTIVE");
        // Two meetings on one section must not add its credits twice.
        meeting(sectionA, 1, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        meeting(sectionA, 3, "09:00", "11:45", "2026-08-01", "2026-12-31", "1108", true);
        String token = student("6600101");
        get(token, ACADEMIC + "/courses/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.semester.id").value(current))
                .andExpect(jsonPath("$.courseCount").value(2))
                .andExpect(jsonPath("$.enrolledCredits").value(4))
                .andExpect(jsonPath("$.lecturerCount").value(2));
        db.update("UPDATE students SET semester_id=NULL WHERE id=?", studentA);
        get(token, ACADEMIC + "/courses/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCount").value(org.hamcrest.Matchers.nullValue()));
        get(token, ACADEMIC + "/progress").andExpect(jsonPath("$.currentCourses").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dataStatus").value("PARTIAL"));
    }

    // Next class

    @Test
    void nextClassPicksTheEarliestFutureMeeting() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        enroll(studentA, sectionB, "ACTIVE");
        meeting(sectionA, 1, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        meeting(sectionB, 5, "13:00", "14:00", "2026-08-01", "2026-12-31", "B-201", true);
        String token = student("6600101");
        get(token, ACADEMIC + "/next-class").andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCode").value("CSC-311"))
                .andExpect(jsonPath("$.meetingType").value("LECTURE"))
                .andExpect(jsonPath("$.dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-06T13:00:00+07:00"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-06T14:00:00+07:00"))
                .andExpect(jsonPath("$.roomName").value("B-201"))
                .andExpect(jsonPath("$.roomNumber").value("B-201"))
                .andExpect(jsonPath("$.buildingName").value("Building 11"))
                .andExpect(jsonPath("$.campusId").value(site))
                .andExpect(jsonPath("$.minutesUntilStart").value(2 * 24 * 60 + 3 * 60))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.occurrenceStatus").value("REGULAR"))
                .andExpect(jsonPath("$.scheduleBasis").value("PUBLISHED_TIMETABLE"));
        clock.set(Instant.parse("2026-11-07T00:00:00Z")); // Saturday morning: both this week's are gone
        token = student("6600101");
        get(token, ACADEMIC + "/next-class").andExpect(jsonPath("$.courseCode").value("CSC-452"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-09T09:00:00+07:00"))
                .andExpect(jsonPath("$.roomName").value("1103"));
    }

    @Test
    void nextClassUsesBangkokDayAndSkipsAStartedClass() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        meeting(sectionA, 1, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        String token = student("6600101");
        // Sunday 23:30 UTC is already Monday 06:30 in Bangkok, so the class is today, not in a week.
        clock.set(Instant.parse("2026-11-08T23:30:00Z"));
        token = student("6600101");
        get(token, ACADEMIC + "/next-class")
                .andExpect(jsonPath("$.startsAt").value("2026-11-09T09:00:00+07:00"))
                .andExpect(jsonPath("$.minutesUntilStart").value(150));
        clock.set(Instant.parse("2026-11-09T02:30:00Z")); // Monday 09:30 in Bangkok: in progress
        token = student("6600101");
        get(token, ACADEMIC + "/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-16T09:00:00+07:00"));
        clock.set(Instant.parse("2026-11-09T05:00:00Z")); // Monday 12:00 in Bangkok: ended
        token = student("6600101");
        get(token, ACADEMIC + "/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-16T09:00:00+07:00"));
    }

    @Test
    void nextClassExcludesEverythingThatIsNotReal() throws Exception {
        long enrollment = enroll(studentA, sectionA, "ACTIVE");
        String token = student("6600101");
        get(token, ACADEMIC + "/next-class").andExpect(status().isOk()).andExpect(content().string("null"))
                .andExpect(header().string("Cache-Control", "no-store"));
        meeting(sectionA, 5, "09:00", "10:00", "2026-08-01", "2026-12-31", "inactive", false);
        meeting(sectionA, 4, "09:00", "10:00", "2026-01-01", "2026-06-30", "ended", true);
        meeting(sectionA, 3, "12:00", "13:00", "2027-01-01", "2027-03-31", "future term", true);
        long deleted = meeting(sectionA, 2, "09:00", "10:00", "2026-08-01", "2026-12-31", "deleted", true);
        db.update("UPDATE course_section_meetings SET is_deleted=TRUE WHERE id=?", deleted);
        get(token, ACADEMIC + "/next-class").andExpect(jsonPath("$.startsAt").value("2027-01-06T12:00:00+07:00"));
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", sectionA);
        get(token, ACADEMIC + "/next-class").andExpect(content().string("null"));
        db.update("UPDATE course_sections SET status='ACTIVE' WHERE id=?", sectionA);
        db.update("UPDATE enrollments SET status='WITHDRAWN' WHERE id=?", enrollment);
        get(token, ACADEMIC + "/next-class").andExpect(content().string("null"));
    }

    // Deadlines

    @Test
    void deadlinesMergeSortAndHideWhatStudentsShouldNotSee() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        enroll(studentA, sectionB, "ACTIVE");
        assignment(sectionA, "Draft work", "2026-11-05T12:00:00+07:00", "DRAFT");
        assignment(sectionA, "Closed work", "2026-11-05T12:00:00+07:00", "CLOSED");
        long late = assignment(sectionA, "Later work", "2026-11-20T23:59:00+07:00", "PUBLISHED");
        assignment(sectionOther, "Not my section", "2026-11-05T12:00:00+07:00", "PUBLISHED");
        assignment(sectionB, "Past work", "2026-11-01T12:00:00+07:00", "PUBLISHED");
        long today = assignment(sectionB, "Tonight", "2026-11-04T23:59:00+07:00", "PUBLISHED");
        long midterm = exam(sectionA, "Midterm", "2026-11-06T09:00:00+07:00", "SCHEDULED");
        exam(sectionA, "Cancelled exam", "2026-11-06T10:00:00+07:00", "CANCELLED");
        exam(sectionA, "Draft exam", "2026-11-06T11:00:00+07:00", "DRAFT");
        String token = student("6600101");
        get(token, ACADEMIC + "/deadlines").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.dueTodayCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(today))
                .andExpect(jsonPath("$.items[0].type").value("ASSIGNMENT"))
                .andExpect(jsonPath("$.items[0].courseCode").value("CSC-311"))
                .andExpect(jsonPath("$.items[0].deadlineAt").value("2026-11-04T23:59:00+07:00"))
                .andExpect(jsonPath("$.items[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.items[1].id").value(midterm))
                .andExpect(jsonPath("$.items[1].type").value("EXAM"))
                .andExpect(jsonPath("$.items[1].deadlineAt").value("2026-11-06T09:00:00+07:00"))
                .andExpect(jsonPath("$.items[2].id").value(late));
        // total keeps counting past the limit, and a narrower window narrows both.
        get(token, ACADEMIC + "/deadlines?limit=1").andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items.length()").value(1));
        get(token, ACADEMIC + "/deadlines?from=2026-11-05T00:00:00%2B07:00&to=2026-11-10T00:00:00%2B07:00")
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(midterm));
        get(token, ACADEMIC + "/deadlines?limit=0").andExpect(status().isBadRequest());
        get(token, ACADEMIC + "/deadlines?limit=101").andExpect(status().isBadRequest());
        get(token, ACADEMIC + "/deadlines?limit=abc").andExpect(status().isBadRequest());
        get(token, ACADEMIC + "/deadlines?from=not-a-date").andExpect(status().isBadRequest());
        get(token, ACADEMIC + "/deadlines?from=2026-11-10T00:00:00%2B07:00&to=2026-11-05T00:00:00%2B07:00").andExpect(status().isBadRequest());
        get(token, ACADEMIC + "/deadlines?from=2026-01-01T00:00:00%2B07:00&to=2028-01-01T00:00:00%2B07:00").andExpect(status().isBadRequest());
    }

    @Test
    void dueTodayFollowsTheBangkokCalendarDay() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        // 2026-11-04 17:30 UTC is 00:30 on 2026-11-05 in Bangkok.
        assignment(sectionA, "Bangkok tomorrow, UTC today", "2026-11-04T17:30:00Z", "PUBLISHED");
        assignment(sectionA, "Last minute of the Bangkok day", "2026-11-04T16:59:00Z", "PUBLISHED");
        String token = student("6600101");
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.dueTodayCount").value(1)).andExpect(jsonPath("$.total").value(2));
        clock.set(Instant.parse("2026-11-04T17:00:00Z")); // Bangkok has moved to the 5th
        token = student("6600101");
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.dueTodayCount").value(1)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void noDeadlinesIsAnEmptyList() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        get(student("6600101"), ACADEMIC + "/deadlines").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.dueTodayCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void assignmentAndExamAdministrationFollowsTheirStatuses() throws Exception {
        String admin = admin();
        String assignments = "/api/v1/admin/assignments";
        var body = new HashMap<String, Object>(Map.of("courseSectionId", sectionA, "title", " Prototype ", "dueAt", "2026-11-10T23:59:00+07:00"));
        long assignment = idOf(post(admin, assignments, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.title").value("Prototype"))
                .andExpect(jsonPath("$.dueAt").value("2026-11-10T23:59:00+07:00")));
        enroll(studentA, sectionA, "ACTIVE");
        String token = student("6600101");
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.total").value(0));
        post(admin, assignments + "/" + assignment + "/publish", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        post(admin, assignments + "/" + assignment + "/publish", Map.of()).andExpect(status().isConflict());
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.total").value(1));
        body.put("title", "Prototype 2");
        put(admin, assignments + "/" + assignment, body).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Prototype 2"));
        get(admin, assignments + "?courseSectionId=" + sectionA + "&status=PUBLISHED").andExpect(jsonPath("$.totalElements").value(1));
        get(admin, assignments + "?status=NOPE").andExpect(status().isBadRequest());
        post(admin, assignments + "/" + assignment + "/close", Map.of()).andExpect(status().isOk());
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.total").value(0));
        put(admin, assignments + "/" + assignment, body).andExpect(status().isConflict());
        mvc.perform(auth(delete(assignments + "/" + assignment), admin)).andExpect(status().isNoContent());
        get(admin, assignments + "/" + assignment).andExpect(status().isNotFound());
        body.put("courseSectionId", 999999);
        post(admin, assignments, body).andExpect(status().isNotFound());

        String exams = "/api/v1/admin/exams";
        var exam = new HashMap<String, Object>(Map.of("courseSectionId", sectionA, "title", "Midterm", "examType", "MIDTERM",
                "startsAt", "2026-11-12T09:00:00+07:00", "endsAt", "2026-11-12T11:00:00+07:00"));
        long examId = idOf(post(admin, exams, exam).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")));
        exam.put("endsAt", "2026-11-12T08:00:00+07:00");
        post(admin, exams, exam).andExpect(status().isBadRequest());
        exam.put("endsAt", "2026-11-12T11:00:00+07:00");
        exam.put("examType", "ORAL");
        post(admin, exams, exam).andExpect(status().isBadRequest());
        post(admin, exams + "/" + examId + "/complete", Map.of()).andExpect(status().isConflict());
        post(admin, exams + "/" + examId + "/schedule", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.items[0].type").value("EXAM"));
        post(admin, exams + "/" + examId + "/cancel", Map.of()).andExpect(status().isOk());
        get(token, ACADEMIC + "/deadlines").andExpect(jsonPath("$.total").value(0));
        post(admin, exams + "/" + examId + "/schedule", Map.of()).andExpect(status().isConflict());
        mvc.perform(auth(delete(exams + "/" + examId), admin)).andExpect(status().isNoContent());
        post(token, assignments, body).andExpect(status().isForbidden());
    }

    // Home summary

    @Test
    void homeSummaryAggregatesTheSameServices() throws Exception {
        enroll(studentA, sectionA, "ACTIVE");
        meeting(sectionA, 1, "09:00", "11:45", "2026-08-01", "2026-12-31", "1103", true);
        for (int i = 1; i <= 6; i++) assignment(sectionA, "Work " + i, "2026-11-1" + i + "T12:00:00+07:00", "PUBLISHED");
        assignment(sectionA, "Tonight", "2026-11-04T23:00:00+07:00", "PUBLISHED");
        String token = student("6600101");
        var home = get(token, "/api/v1/student/home/summary").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.student.displayName").value("First 6600101"))
                .andExpect(jsonPath("$.student.programName").value("Computer Science"))
                .andExpect(jsonPath("$.student.studyYear").value(3))
                .andExpect(jsonPath("$.nextClass.courseCode").value("CSC-452"))
                .andExpect(jsonPath("$.nextClass.startsAt").value("2026-11-09T09:00:00+07:00"))
                .andExpect(jsonPath("$.deadlines.dueTodayCount").value(1))
                .andExpect(jsonPath("$.deadlines.total").value(7))
                .andExpect(jsonPath("$.deadlines.items.length()").value(5));
        JsonNode tree = json.readTree(home.andReturn().getResponse().getContentAsByteArray());
        assertThat(tree.has("cgpa")).isFalse();
    }

    @Test
    void homeSummaryCopesWithNothing() throws Exception {
        get(student("6600102"), "/api/v1/student/home/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.nextClass").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.deadlines.total").value(0));
    }

    @Test
    void newStudentEndpointsRejectAnonymousAndForcedPasswordChange() throws Exception {
        for (String path : List.of(ACADEMIC + "/progress", ACADEMIC + "/next-class", ACADEMIC + "/deadlines",
                ACADEMIC + "/courses/summary", "/api/v1/student/home/summary"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
        db.update("UPDATE student_credentials SET force_password_change=TRUE WHERE student_id=?", studentA);
        get(student("6600101"), ACADEMIC + "/progress").andExpect(status().isForbidden());
    }

    // Helpers

    private void clean() {
        db.update("DELETE FROM student_course_result_audits");
        db.update("DELETE FROM student_course_results");
        db.update("DELETE FROM grade_definitions");
        db.update("DELETE FROM grading_schemes");
        db.update("DELETE FROM timetable_meeting_exceptions");
        db.update("DELETE FROM course_section_meetings");
        db.update("DELETE FROM course_assignments");
        db.update("DELETE FROM course_exams");
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
        db.update("DELETE FROM course_sections");
        db.update("DELETE FROM academic_terms");
        db.update("DELETE FROM courses");
        db.update("DELETE FROM lectures");
        db.update("DELETE FROM semesters");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
        db.update("DELETE FROM campus_rooms");
        db.update("DELETE FROM campus_buildings");
        db.update("DELETE FROM campus_sites");
    }
    private long admin(String email, String role) {
        long id = id("INSERT INTO app_users(employee_id,first_name,last_name,email,account_status) VALUES (?,'Data','Actor',?,'ACTIVE') RETURNING id", email, email);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", id, role);
        return id;
    }
    private long course(String code, String name, int credits) {
        return id("INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,course_type,created_by) VALUES (?,?,?,?,?,300,?) RETURNING id",
                code, name, credits, faculty, department, actor);
    }
    private long section(long course, String number, long lecturer, long semester) {
        return id("INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,academic_term_id,status,created_by) VALUES (?,?,40,?,?,(SELECT id FROM academic_terms WHERE semester_id=?),'ACTIVE',?) RETURNING id",
                course, number, lecturer, semester, semester, actor);
    }
    private long term(long semester, String code, String from, String to) {
        return id("INSERT INTO academic_terms(semester_id,academic_year_code,academic_year_label,start_date,end_date,status) VALUES (?,?,?,?::date,?::date,'ACTIVE') RETURNING id",
                semester, code, code, from, to);
    }
    private long room(String number) {
        Long existing = db.query("SELECT id FROM campus_rooms WHERE building_id=? AND room_number=?", rs -> rs.next() ? rs.getLong(1) : null, building, number);
        return existing != null ? existing
                : id("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,?,1,'CLASSROOM') RETURNING id", building, number);
    }
    private long student(String code, int studyYear) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,academic_year,enrollment_year,study_year)
                VALUES (?,?,'First',?,'ACTIVE',?,?,?,?,2026,2024,?) RETURNING id
                """, code, code + "@rsu.ac.th", code, faculty, department, program, current, studyYear);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private long grade(String code, String point, boolean gpa, boolean earns, boolean passing) {
        return id("INSERT INTO grade_definitions(grading_scheme_id,grade_code,grade_point,counts_in_gpa,earns_credit,is_passing) VALUES (?,?,?::numeric,?,?,?) RETURNING id",
                scheme, code, point, gpa, earns, passing);
    }
    private long enroll(long student, long section, String status) {
        return id("INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,created_by) VALUES (?,?,DATE '2026-08-15',?,?) RETURNING id",
                student, section, status, actor);
    }
    // room is a campus room number (created on demand); active=true is a PUBLISHED meeting, false a DRAFT.
    private long meeting(long section, int day, String start, String end, String from, String to, String room, boolean active) {
        return id("""
                INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,room_id,room_label,status,is_active)
                VALUES (?,'LECTURE',?,?::time,?::time,?::date,?::date,?,?,?,?) RETURNING id
                """, section, day, start, end, from, to, room == null ? null : room(room), room, active ? "PUBLISHED" : "DRAFT", active);
    }
    private long assignment(long section, String title, String due, String status) {
        return id("INSERT INTO course_assignments(course_section_id,title,due_at,status,created_by) VALUES (?,?,?::timestamptz,?,?) RETURNING id",
                section, title, due, status, actor);
    }
    private long exam(long section, String title, String starts, String status) {
        return id("INSERT INTO course_exams(course_section_id,title,exam_type,starts_at,ends_at,status,created_by) VALUES (?,?,'MIDTERM',?::timestamptz,?::timestamptz + interval '2 hours',?,?) RETURNING id",
                section, title, starts, starts, status, actor);
    }
    private Map<String, Object> meetingBody(String type, int day, String start, String end) {
        return Map.of("meetingType", type, "dayOfWeek", day, "startTime", start, "endTime", end,
                "effectiveFrom", "2026-08-01", "effectiveTo", "2026-12-31", "roomId", room1103, "roomLabel", "1103");
    }
    private ResultActions create(String token, long enrollment, long grade) throws Exception {
        return post(token, RESULTS, Map.of("enrollmentId", enrollment, "gradeDefinitionId", grade));
    }
    private ResultActions finalize(String token, long result) throws Exception {
        return post(token, RESULTS + "/" + result + "/finalize", Map.of());
    }
    private long finalized(String token, long enrollment, long grade) throws Exception {
        long result = idOf(create(token, enrollment, grade).andExpect(status().isCreated()));
        finalize(token, result).andExpect(status().isOk()).andExpect(jsonPath("$.resultStatus").value("FINALIZED"));
        return result;
    }
    private String admin() throws Exception { return adminToken("data.super@example.test"); }
    private String adminToken(String email) throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private String student(String code) throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private ResultActions get(String token, String path) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(path)), token));
    }
    private ResultActions post(String token, String path, Object body) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path), token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    private ResultActions put(String token, String path, Object body) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
    private long idOf(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsByteArray()).get("id").asLong();
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
