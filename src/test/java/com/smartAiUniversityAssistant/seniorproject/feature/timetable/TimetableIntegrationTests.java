package com.smartAiUniversityAssistant.seniorproject.feature.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.*;

/**
 * Spec file 42: academic terms, timetable CRUD, publishing, conflicts, concurrency, calendar, list, exceptions and
 * the student schedule / next class. All names, rooms and dates are synthetic test values.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class TimetableIntegrationTests extends IntegrationSupport {
    private static final String TERMS = "/api/v1/admin/academic-terms";
    private static final String TIMETABLE = "/api/v1/admin/timetable";
    private static final String ADMIN_PASSWORD = "timetable admin test password";
    private static final String STUDENT_PASSWORD = "timetable student test password";
    // Wednesday 2026-11-04 10:00 in Bangkok.
    private static final Instant WEDNESDAY_10_BANGKOK = Instant.parse("2026-11-04T03:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;

    private long actor, faculty, department, program, lecturerA, lecturerB, semester, semester2, term, term2;
    private long courseA, courseB, courseC, sectionA, sectionB, sectionC, sectionNoTerm, site, building, roomA, roomB, roomLab, studentA;
    private String superToken, academicToken, studentToken;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(WEDNESDAY_10_BANGKOK);
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clean();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actor = admin("tt.super@example.test", "SUPER_ADMIN");
        admin("tt.academic@example.test", "ACADEMIC_ADMIN");
        superToken = adminLogin("tt.super@example.test");
        academicToken = adminLogin("tt.academic@example.test");
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ICT','College of ICT','ไอซีที',?) RETURNING id", actor);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actor);
        program = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSC-CS','Computer Science','Bachelor of Science',?,?) RETURNING id", department, actor);
        lecturerA = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('เอ','Lecturer A',?,?,?) RETURNING id", faculty, department, actor);
        lecturerB = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('บี','Lecturer B',?,?,?) RETURNING id", faculty, department, actor);
        semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'เทอม 1','Semester 1',?) RETURNING id", actor);
        semester2 = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'เทอม 2','Semester 2',?) RETURNING id", actor);
        term = term(semester, "2569", "2026-2027", "2026-08-01", "2026-12-31");
        // A second term whose calendar dates overlap the first: physical conflicts must still be found across terms.
        term2 = term(semester2, "2569", "2026-2027", "2026-11-01", "2027-03-31");
        courseA = course("CSC-452", "Mobile Programming");
        courseB = course("CSC-311", "Data Structures");
        courseC = course("CSC-999", "Other Course");
        sectionA = section(courseA, "02", lecturerA, semester, term);
        sectionB = section(courseB, "01", lecturerA, semester, term); // same lecturer as A
        sectionC = section(courseC, "01", lecturerB, semester, term);
        sectionNoTerm = section(courseC, "02", lecturerB, semester, null);
        site = id("INSERT INTO campus_sites(site_code,site_name) VALUES ('MAIN','Main Campus') RETURNING id");
        building = id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,'B11','Building 11',12) RETURNING id", site);
        roomA = room("IT-301", "CLASSROOM");
        roomB = room("IT-302", "CLASSROOM");
        roomLab = room("IT-LAB-01", "COMPUTER_LAB");
        studentA = student("6600101");
        enroll(studentA, sectionA, "ACTIVE");
        studentToken = studentLogin("6600101");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        clean();
    }

    // Academic terms

    @Test
    void academicTermCrudValidatesAndProtectsReferencedTerms() throws Exception {
        var body = body("semesterId", semester, "academicYearCode", "2570", "academicYearLabel", "2027–2028", "startDate", "2027-08-01", "endDate", "2027-12-15");
        long created = idOf(post(academicToken, TERMS, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED")).andExpect(jsonPath("$.semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("$.displayLabel").value("Semester 1 / 2570")).andExpect(jsonPath("$.academicYearLabel").value("2027–2028")));
        post(superToken, TERMS, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_TERM_ALREADY_EXISTS"));
        var reversed = new HashMap<>(body);
        reversed.put("academicYearCode", "2571");
        reversed.put("endDate", "2027-07-31");
        post(superToken, TERMS, reversed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_DATE_RANGE"));
        var missing = new HashMap<>(body);
        missing.put("semesterId", 999999);
        post(superToken, TERMS, missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SEMESTER_NOT_FOUND"));
        get(superToken, TERMS + "?semesterId=" + semester + "&sort=startDate,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.content[1].id").value(created));
        get(superToken, TERMS + "?status=PLANNED").andExpect(jsonPath("$.content.length()").value(1));
        get(superToken, TERMS + "?bogus=1").andExpect(status().isBadRequest());
        get(superToken, TERMS + "/" + created).andExpect(status().isOk()).andExpect(jsonPath("$.academicYearCode").value("2570"));
        var update = new HashMap<>(body);
        update.put("academicYearLabel", "2027-2028");
        update.put("status", "ACTIVE");
        put(superToken, TERMS + "/" + created, update).andExpect(status().isOk()).andExpect(jsonPath("$.academicYearLabel").value("2027-2028"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.updatedBy").value(actor));
        mvc.perform(auth(patch(TERMS + "/" + created + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"), superToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        // The fixture term is referenced by sections and cannot be deleted; the new one can.
        mvc.perform(auth(delete(TERMS + "/" + term), superToken)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_TERM_IN_USE"));
        mvc.perform(auth(delete(TERMS + "/" + created), superToken)).andExpect(status().isNoContent());
        get(superToken, TERMS + "/" + created).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_TERM_NOT_FOUND"));
        assertThat(db.queryForObject("SELECT is_deleted FROM academic_terms WHERE id=?", Boolean.class, created)).isTrue();
        post(studentToken, TERMS, body).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post(TERMS).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isUnauthorized());
    }

    @Test
    void courseSectionLinksToATermOfItsOwnSemester() throws Exception {
        String path = "/api/v1/admin/course-sections/" + sectionNoTerm;
        var body = body("courseId", courseC, "sectionNumber", "02", "capacity", 40, "lectureId", lecturerB, "semesterId", semester, "status", "ACTIVE",
                "academicTermId", term2);
        put(superToken, path, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACADEMIC_TERM_SEMESTER_MISMATCH"));
        body.put("academicTermId", 999999);
        put(superToken, path, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACADEMIC_TERM_NOT_FOUND"));
        body.put("academicTermId", term);
        put(superToken, path, body).andExpect(status().isOk()).andExpect(jsonPath("$.academicTermId").value(term))
                .andExpect(jsonPath("$.academicYearCode").value("2569")).andExpect(jsonPath("$.semesterNameEn").value("Semester 1"));
        get(superToken, "/api/v1/admin/course-sections?page=0&size=50").andExpect(status().isOk());
    }

    // Meetings

    @Test
    void meetingLifecycleFromDraftToPublishedToCancelled() throws Exception {
        post(superToken, TIMETABLE + "/meetings", meeting(sectionNoTerm, roomA, "LECTURE", 1, "09:00", "11:00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACADEMIC_TERM_NOT_FOUND"));
        post(superToken, TIMETABLE + "/meetings", meeting(sectionA, roomA, "LECTURE", 1, "11:00", "09:00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_TIME"));
        var outside = meeting(sectionA, roomA, "LECTURE", 1, "09:00", "11:00");
        outside.put("effectiveTo", "2027-01-15");
        post(superToken, TIMETABLE + "/meetings", outside).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_DATE_RANGE"));
        post(superToken, TIMETABLE + "/meetings", meeting(sectionA, 999999L, "LECTURE", 1, "09:00", "11:00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_NOT_FOUND"));
        var bogus = meeting(sectionA, roomA, "LECTURE", 1, "09:00", "11:00");
        bogus.put("courseId", courseA);
        post(superToken, TIMETABLE + "/meetings", bogus).andExpect(status().isBadRequest()); // redundant identifiers are refused
        post(superToken, TIMETABLE + "/meetings", meeting(sectionA, roomA, "SEMINAR", 1, "09:00", "11:00")).andExpect(status().isBadRequest());

        // Spec file 44: the enrollment due date may not be after the effective start date (2026-08-01 here).
        var lateDue = meeting(sectionA, roomA, "LECTURE", 1, "09:00", "11:00");
        lateDue.put("enrollmentDueDate", "2026-08-02");
        post(superToken, TIMETABLE + "/meetings", lateDue).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_ENROLLMENT_DUE_DATE"));
        lateDue.put("enrollmentDueDate", "2026-08-01");
        long withDue = idOf(post(superToken, TIMETABLE + "/meetings", lateDue).andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentDueDate").value("2026-08-01")));
        var movedDue = slot(roomA, "LECTURE", 1, "09:00", "11:00", null);
        movedDue.put("enrollmentDueDate", "2026-09-01");
        put(superToken, TIMETABLE + "/meetings/" + withDue, movedDue).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_ENROLLMENT_DUE_DATE"));
        movedDue.put("enrollmentDueDate", "2026-07-25");
        put(superToken, TIMETABLE + "/meetings/" + withDue, movedDue).andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentDueDate").value("2026-07-25"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> db.update("UPDATE course_section_meetings SET enrollment_due_date=DATE '2026-08-02' WHERE id=?", withDue))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(auth(MockMvcRequestBuilders.delete(TIMETABLE + "/meetings/" + withDue), superToken)).andExpect(status().isNoContent());

        long id = idOf(post(academicToken, TIMETABLE + "/meetings", meeting(sectionA, null, "LECTURE", 1, "09:00", "11:45")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.course.code").value("CSC-452"))
                .andExpect(jsonPath("$.courseSection.code").value("02")).andExpect(jsonPath("$.lecturer.name").value("Lecturer A"))
                .andExpect(jsonPath("$.faculty.id").value(faculty)).andExpect(jsonPath("$.department.id").value(department))
                .andExpect(jsonPath("$.academicTerm.academicYearCode").value("2569")).andExpect(jsonPath("$.location.roomId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dayName").value("MONDAY")).andExpect(jsonPath("$.startTime").value("09:00")));
        assertThat(db.queryForObject("SELECT is_active FROM course_section_meetings WHERE id=?", Boolean.class, id)).isFalse();
        // Drafts are invisible to students; publishing needs a room.
        get(studentToken, "/api/v1/student/academic/schedule").andExpect(jsonPath("$[0].meetings.length()").value(0));
        post(superToken, TIMETABLE + "/meetings/" + id + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_PUBLISH_NOT_ALLOWED"));
        put(superToken, TIMETABLE + "/meetings/" + id, slot(roomA, "LECTURE", 1, "09:00", "11:45", "Bring laptops")).andExpect(status().isOk())
                .andExpect(jsonPath("$.location.roomNumber").value("IT-301")).andExpect(jsonPath("$.location.buildingName").value("Building 11"))
                .andExpect(jsonPath("$.location.campusId").value(site)).andExpect(jsonPath("$.notes").value("Bring laptops"));
        post(superToken, TIMETABLE + "/meetings/" + id + "/publish", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(db.queryForObject("SELECT is_active FROM course_section_meetings WHERE id=?", Boolean.class, id)).isTrue();
        post(superToken, TIMETABLE + "/meetings/" + id + "/publish", null).andExpect(status().isOk()); // idempotent
        get(studentToken, "/api/v1/student/academic/schedule").andExpect(jsonPath("$[0].meetings.length()").value(1))
                .andExpect(jsonPath("$[0].meetings[0].meetingId").value(id)).andExpect(jsonPath("$[0].meetings[0].roomNumber").value("IT-301"))
                .andExpect(jsonPath("$[0].meetings[0].roomName").value("IT-301")).andExpect(jsonPath("$[0].meetings[0].buildingName").value("Building 11"))
                .andExpect(jsonPath("$[0].meetings[0].campusId").value(site)).andExpect(jsonPath("$[0].meetings[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].meetings[0].dayOfWeekNumber").value(1));
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-09T09:00:00+07:00"))
                .andExpect(jsonPath("$.roomName").value("IT-301")).andExpect(jsonPath("$.buildingName").value("Building 11"))
                .andExpect(jsonPath("$.meetingId").value(id)).andExpect(jsonPath("$.scheduleBasis").value("PUBLISHED_TIMETABLE"));
        // Editing a published meeting keeps it published and re-validates.
        put(superToken, TIMETABLE + "/meetings/" + id, slot(null, "LECTURE", 1, "09:00", "11:45", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMETABLE_PUBLISH_NOT_ALLOWED"));
        put(superToken, TIMETABLE + "/meetings/" + id, slot(roomB, "LECTURE", 2, "13:00", "15:00", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.dayName").value("TUESDAY"));
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-10T13:00:00+07:00"));
        post(superToken, TIMETABLE + "/meetings/" + id + "/cancel", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        get(studentToken, "/api/v1/student/academic/schedule").andExpect(jsonPath("$[0].meetings.length()").value(0));
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(content().string("null"));
        post(superToken, TIMETABLE + "/meetings/" + id + "/publish", null).andExpect(status().isConflict());
        put(superToken, TIMETABLE + "/meetings/" + id, slot(roomB, "LECTURE", 2, "13:00", "15:00", null)).andExpect(status().isBadRequest());
        mvc.perform(auth(delete(TIMETABLE + "/meetings/" + id), superToken)).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT is_deleted FROM course_section_meetings WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT updated_by FROM course_section_meetings WHERE id=?", Long.class, id)).isEqualTo(actor);
        get(superToken, TIMETABLE + "/meetings/" + id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIMETABLE_MEETING_NOT_FOUND"));
        post(studentToken, TIMETABLE + "/meetings", meeting(sectionA, roomA, "LECTURE", 1, "09:00", "11:00")).andExpect(status().isForbidden());
        get(superToken, "/api/v1/student/academic/schedule").andExpect(status().isForbidden());
    }

    @Test
    void bulkCreateIsAtomic() throws Exception {
        var bad = body("courseSectionId", sectionA, "meetings", List.of(slot(roomA, "LECTURE", 2, "09:00", "11:45", null), slot(roomLab, "LAB", 4, "16:00", "14:00", null)));
        post(superToken, TIMETABLE + "/meetings/bulk", bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_TIME"));
        assertThat(db.queryForObject("SELECT count(*) FROM course_section_meetings", Long.class)).isZero();
        var good = body("courseSectionId", sectionA, "meetings", List.of(slot(roomA, "LECTURE", 2, "09:00", "11:45", null), slot(roomLab, "LAB", 4, "14:00", "16:00", null)));
        post(superToken, TIMETABLE + "/meetings/bulk", good).andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("DRAFT")).andExpect(jsonPath("$[1].meetingType").value("LAB"));
        assertThat(db.queryForObject("SELECT count(*) FROM course_section_meetings WHERE course_section_id=?", Long.class, sectionA)).isEqualTo(2);
    }

    // Conflicts

    @Test
    void publishingRejectsRoomLecturerAndSectionConflictsAndAllowsTheRest() throws Exception {
        long a = published(sectionA, roomA, 1, "09:00", "11:00", "2026-08-01", "2026-12-15");
        // Same lecturer (A teaches both sections), other room.
        var sameLecturer = meeting(sectionB, roomB, "LECTURE", 1, "10:00", "12:00");
        post(superToken, TIMETABLE + "/conflicts/check", check(sameLecturer, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflicts").value(true)).andExpect(jsonPath("$.totalConflicts").value(1))
                .andExpect(jsonPath("$.conflicts[0].type").value("LECTURER_CONFLICT")).andExpect(jsonPath("$.conflicts[0].conflictingMeetingId").value(a))
                .andExpect(jsonPath("$.conflicts[0].date").value("2026-08-03")).andExpect(jsonPath("$.conflicts[0].overlapStart").value("10:00"))
                .andExpect(jsonPath("$.conflicts[0].overlapEnd").value("11:00"));
        long b = idOf(post(superToken, TIMETABLE + "/meetings", sameLecturer).andExpect(status().isCreated())); // drafts may overlap
        post(superToken, TIMETABLE + "/meetings/" + b + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_LECTURER_CONFLICT"));
        // Same room, other lecturer.
        long c = draft(sectionC, roomA, 1, "10:00", "12:00");
        post(superToken, TIMETABLE + "/meetings/" + c + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_CONFLICT"));
        // Touching intervals do not overlap (half-open).
        long adjacent = draft(sectionC, roomA, 1, "11:00", "12:00");
        post(superToken, TIMETABLE + "/meetings/" + adjacent + "/publish", null).andExpect(status().isOk());
        // Same section, other room: the section conflict is the one reported.
        long sameSection = draft(sectionA, roomB, 1, "10:00", "11:00");
        post(superToken, TIMETABLE + "/meetings/" + sameSection + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_SECTION_CONFLICT"));
        // Other room, other lecturer and a section whose own meeting only touches this one: fine.
        post(superToken, TIMETABLE + "/meetings/" + draft(sectionC, roomB, 1, "09:00", "11:00") + "/publish", null).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM course_section_meetings WHERE status='PUBLISHED'", Long.class)).isEqualTo(3);
    }

    @Test
    void effectiveDatesCrossTermsAndExceptionsDecideRealConflicts() throws Exception {
        long a = published(sectionA, roomA, 1, "09:00", "11:00", "2026-08-01", "2026-10-31");
        // Same room and slot but the date ranges never meet.
        long later = draft(sectionC, roomA, 1, "09:00", "11:00", "2026-11-02", "2026-12-15");
        post(superToken, TIMETABLE + "/meetings/" + later + "/publish", null).andExpect(status().isOk());
        // One shared week is enough for a conflict.
        long overlapping = draft(sectionC, roomA, 1, "09:00", "11:00", "2026-10-26", "2026-11-01");
        post(superToken, TIMETABLE + "/meetings/" + overlapping + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_CONFLICT"));
        // A shared week whose Monday falls outside both ranges is no conflict: a's last Monday is 2026-10-26, this starts Tuesday 2026-10-27.
        long tuesdayStart = draft(sectionC, roomA, 1, "09:00", "11:00", "2026-10-27", "2026-11-01");
        post(superToken, TIMETABLE + "/meetings/" + tuesdayStart + "/publish", null).andExpect(status().isOk());
        // Cross-term: section in term2 uses the same physical room while 'later' is published.
        long sectionTerm2 = section(courseC, "03", lecturerA, semester2, term2);
        long cross = draft(sectionTerm2, roomA, 1, "10:00", "11:00", "2026-11-02", "2026-11-30");
        post(superToken, TIMETABLE + "/meetings/" + cross + "/publish", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_CONFLICT"));
        // Cancelling every clashing occurrence of 'later' removes the conflict; rescheduling one back into the slot restores it.
        for (String date : List.of("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23", "2026-11-30"))
            post(superToken, TIMETABLE + "/meetings/" + later + "/exceptions", body("originalDate", date, "exceptionType", "CANCELLED", "reason", "Field trip"))
                    .andExpect(status().isCreated());
        post(superToken, TIMETABLE + "/meetings/" + cross + "/publish", null).andExpect(status().isOk());
        post(superToken, TIMETABLE + "/meetings/" + later + "/exceptions", body("originalDate", "2026-12-07", "exceptionType", "RESCHEDULED",
                "replacementDate", "2026-11-09", "replacementStartTime", "10:00", "replacementEndTime", "11:00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_CONFLICT"));
        // GET /conflicts and statistics derive the same pairs from stored rows (one is forced in by SQL).
        db.update("UPDATE course_section_meetings SET room_id=? WHERE id=?", roomA, cross);
        db.update("DELETE FROM timetable_meeting_exceptions WHERE meeting_id=?", later);
        get(superToken, TIMETABLE + "/conflicts").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.conflicts[0].type").value("ROOM_CONFLICT")).andExpect(jsonPath("$.conflicts[0].meetingA.meetingId").value(later))
                .andExpect(jsonPath("$.conflicts[0].meetingB.meetingId").value(cross)).andExpect(jsonPath("$.conflicts[0].occurrenceCount").value(5))
                .andExpect(jsonPath("$.conflicts[0].firstDate").value("2026-11-02")).andExpect(jsonPath("$.conflicts[0].roomNumber").value("IT-301"));
        get(superToken, TIMETABLE + "/conflicts?type=LECTURER_CONFLICT").andExpect(jsonPath("$.total").value(0));
        get(superToken, TIMETABLE + "/conflicts?weekStart=2026-12-07").andExpect(jsonPath("$.total").value(0));
        get(superToken, TIMETABLE + "/conflicts?roomId=" + roomB).andExpect(jsonPath("$.total").value(0));
        get(superToken, TIMETABLE + "/statistics").andExpect(jsonPath("$.totalMeetings").value(5)).andExpect(jsonPath("$.publishedMeetings").value(4))
                .andExpect(jsonPath("$.draftMeetings").value(1)).andExpect(jsonPath("$.cancelledMeetings").value(0)).andExpect(jsonPath("$.conflictCount").value(1));
        get(superToken, TIMETABLE + "/statistics?academicTermId=" + term2).andExpect(jsonPath("$.totalMeetings").value(1)).andExpect(jsonPath("$.conflictCount").value(0));
        assertThat(a).isPositive();
    }

    @Test
    void concurrentPublishingCannotDoubleBookARoom() throws Exception {
        long first = draft(sectionA, roomA, 3, "09:00", "11:00");
        long second = draft(sectionC, roomA, 3, "10:00", "12:00"); // other lecturer and section: only the room is shared
        var latch = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long id : List.of(first, second)) results.add(pool.submit(() -> {
                latch.await();
                return mvc.perform(auth(MockMvcRequestBuilders.post(TIMETABLE + "/meetings/" + id + "/publish"), superToken)).andReturn().getResponse().getStatus();
            }));
            latch.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM course_section_meetings WHERE status='PUBLISHED' AND room_id=?", Long.class, roomA)).isOne();
    }

    // Calendar, list, filters

    @Test
    void calendarGeneratesDatedOccurrencesWithExceptionsApplied() throws Exception {
        long lecture = published(sectionA, roomA, 1, "09:00", "11:45", "2026-08-01", "2026-12-15");
        long lab = published(sectionA, roomLab, 4, "14:00", "16:00", "2026-08-01", "2026-12-15");
        long sunday = published(sectionC, roomB, 7, "08:00", "09:00", "2026-11-15", "2026-11-15"); // Sunday is supported
        long draftOnly = draft(sectionC, roomB, 2, "08:00", "09:00");
        long ended = published(sectionC, roomB, 1, "13:00", "14:00", "2026-08-01", "2026-10-31");
        post(superToken, TIMETABLE + "/meetings/" + lecture + "/exceptions", body("originalDate", "2026-11-09", "exceptionType", "CANCELLED", "reason", "Holiday"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.exceptionType").value("CANCELLED"));
        post(superToken, TIMETABLE + "/meetings/" + lab + "/exceptions", body("originalDate", "2026-11-12", "exceptionType", "RESCHEDULED",
                "replacementDate", "2026-11-13", "replacementStartTime", "10:00", "replacementEndTime", "12:00", "replacementRoomId", roomB))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.replacementRoomNumber").value("IT-302"));
        // A Wednesday is normalized to its Monday; the week carries the cancelled slot, the moved lab and Sunday's class.
        var week = tree(get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-09")).andExpect(jsonPath("$.weekEnd").value("2026-11-15"))
                .andExpect(jsonPath("$.timezone").value("Asia/Bangkok")).andExpect(jsonPath("$.events.length()").value(4)).andReturn());
        var events = week.get("events");
        assertThat(events.get(0).get("meetingId").asLong()).isEqualTo(lecture);
        assertThat(events.get(0).get("occurrenceStatus").asText()).isEqualTo("CANCELLED");
        assertThat(events.get(0).get("startsAt").asText()).isEqualTo("2026-11-09T09:00:00+07:00");
        assertThat(events.get(1).get("meetingId").asLong()).isEqualTo(draftOnly);
        assertThat(events.get(1).get("status").asText()).isEqualTo("DRAFT");
        assertThat(events.get(2).get("meetingId").asLong()).isEqualTo(lab);
        assertThat(events.get(2).get("occurrenceStatus").asText()).isEqualTo("RESCHEDULED");
        assertThat(events.get(2).get("date").asText()).isEqualTo("2026-11-13");
        assertThat(events.get(2).get("originalDate").asText()).isEqualTo("2026-11-12");
        assertThat(events.get(2).get("roomNumber").asText()).isEqualTo("IT-302");
        assertThat(events.get(2).get("endsAt").asText()).isEqualTo("2026-11-13T12:00:00+07:00");
        assertThat(events.get(3).get("meetingId").asLong()).isEqualTo(sunday);
        assertThat(events.get(3).get("date").asText()).isEqualTo("2026-11-15");
        // Filters: status, faculty via course, campus via room, room, lecturer; the ended meeting is outside the week.
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&status=PUBLISHED").andExpect(jsonPath("$.events.length()").value(3));
        // Room filters follow the meeting's own room: the lab moved into IT-302 for one day is not an IT-302 meeting.
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&roomId=" + roomB).andExpect(jsonPath("$.events.length()").value(2));
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&campusId=" + site).andExpect(jsonPath("$.events.length()").value(4));
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&campusId=999999").andExpect(jsonPath("$.events.length()").value(0));
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&lecturerId=" + lecturerB).andExpect(jsonPath("$.events.length()").value(2));
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-11-09&facultyId=" + faculty + "&departmentId=" + department + "&courseId=" + courseA)
                .andExpect(jsonPath("$.events.length()").value(2));
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-10-26").andExpect(jsonPath("$.events.length()").value(4)); // lecture, draft, lab, ended
        get(superToken, TIMETABLE + "/calendar?weekStart=2026-12-21").andExpect(jsonPath("$.events.length()").value(0));
        get(superToken, TIMETABLE + "/calendar?weekStart=not-a-date").andExpect(status().isBadRequest());
        // Without weekStart the current week (Wednesday 2026-11-04 in Bangkok) is used.
        get(superToken, TIMETABLE + "/calendar").andExpect(jsonPath("$.weekStart").value("2026-11-02")).andExpect(jsonPath("$.events.length()").value(3));
        // Students: on Wednesday the 4th the next class is Thursday's regular lab.
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-05T14:00:00+07:00"));
        clock.set(Instant.parse("2026-11-08T00:00:00Z")); // Sunday: Monday 9th is cancelled, Thursday 12th moved to Friday 13th 10:00
        studentToken = studentLogin("6600101"); // the access token lifetime follows the test clock
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-13T10:00:00+07:00"))
                .andExpect(jsonPath("$.occurrenceStatus").value("RESCHEDULED")).andExpect(jsonPath("$.originalDate").value("2026-11-12"))
                .andExpect(jsonPath("$.roomName").value("IT-302")).andExpect(jsonPath("$.dayOfWeek").value("FRIDAY"));
        get(studentToken, "/api/v1/student/academic/schedule").andExpect(jsonPath("$[0].meetings.length()").value(2))
                .andExpect(jsonPath("$[0].meetings[0].exceptions[0].originalDate").value("2026-11-09"))
                .andExpect(jsonPath("$[0].meetings[0].exceptions[0].exceptionType").value("CANCELLED"))
                .andExpect(jsonPath("$[0].meetings[1].exceptions[0].replacementDate").value("2026-11-13"))
                .andExpect(jsonPath("$[0].meetings[1].exceptions[0].roomNumber").value("IT-302"));
    }

    @Test
    void listViewPagesFiltersSortsAndSearches() throws Exception {
        long a = published(sectionA, roomA, 1, "09:00", "11:45", "2026-08-01", "2026-12-15");
        long b = draft(sectionB, roomB, 3, "13:00", "15:00");
        long c = published(sectionC, roomLab, 1, "08:00", "09:00", "2026-08-01", "2026-12-15");
        get(superToken, TIMETABLE + "/meetings").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(a)).andExpect(jsonPath("$.content[1].id").value(c)).andExpect(jsonPath("$.content[2].id").value(b))
                .andExpect(jsonPath("$.content[1].courseCode").value("CSC-999")).andExpect(jsonPath("$.content[1].roomNumber").value("IT-LAB-01"))
                .andExpect(jsonPath("$.content[1].campusName").value("Main Campus")).andExpect(jsonPath("$.content[1].lecturerName").value("Lecturer B"))
                .andExpect(jsonPath("$.content[1].academicYearLabel").value("2026-2027")).andExpect(jsonPath("$.content[1].dayName").value("MONDAY"));
        get(superToken, TIMETABLE + "/meetings?sort=courseCode,asc").andExpect(jsonPath("$.content[0].courseCode").value("CSC-311"));
        get(superToken, TIMETABLE + "/meetings?sort=startTime,desc").andExpect(jsonPath("$.content[0].id").value(b));
        get(superToken, TIMETABLE + "/meetings?page=1&size=2").andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPages").value(2));
        get(superToken, TIMETABLE + "/meetings?status=DRAFT").andExpect(jsonPath("$.totalElements").value(1));
        get(superToken, TIMETABLE + "/meetings?dayOfWeek=1").andExpect(jsonPath("$.totalElements").value(2));
        get(superToken, TIMETABLE + "/meetings?lecturerId=" + lecturerA).andExpect(jsonPath("$.totalElements").value(2));
        get(superToken, TIMETABLE + "/meetings?courseSectionId=" + sectionC).andExpect(jsonPath("$.totalElements").value(1));
        get(superToken, TIMETABLE + "/meetings?buildingId=" + building + "&roomId=" + roomLab).andExpect(jsonPath("$.totalElements").value(1));
        get(superToken, TIMETABLE + "/meetings?academicTermId=" + term2).andExpect(jsonPath("$.totalElements").value(0));
        get(superToken, TIMETABLE + "/meetings?search=mobile").andExpect(jsonPath("$.totalElements").value(1));
        get(superToken, TIMETABLE + "/meetings?search=it-lab").andExpect(jsonPath("$.totalElements").value(1));
        get(superToken, TIMETABLE + "/meetings?search=lecturer").andExpect(jsonPath("$.totalElements").value(3));
        get(superToken, TIMETABLE + "/meetings?sort=lecturerId,asc").andExpect(status().isBadRequest());
        get(superToken, TIMETABLE + "/meetings?dayOfWeek=9").andExpect(status().isBadRequest());
        get(superToken, TIMETABLE + "/meetings?unknown=1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        get(superToken, TIMETABLE + "/meetings?size=500").andExpect(status().isBadRequest());
        get(academicToken, TIMETABLE + "/meetings").andExpect(status().isOk());
        get(studentToken, TIMETABLE + "/meetings").andExpect(status().isForbidden());
    }

    @Test
    void filterOptionsCascade() throws Exception {
        long otherFaculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('ENG','Engineering',?) RETURNING id", actor);
        long otherDepartment = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('EE','Electrical',?,?) RETURNING id", otherFaculty, actor);
        long otherSite = id("INSERT INTO campus_sites(site_code,site_name) VALUES ('NORTH','North Campus') RETURNING id");
        id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,'N1','North 1',2) RETURNING id", otherSite);
        get(superToken, TIMETABLE + "/filter-options").andExpect(status().isOk())
                .andExpect(jsonPath("$.academicTerms.length()").value(2)).andExpect(jsonPath("$.faculties.length()").value(2))
                .andExpect(jsonPath("$.departments.length()").value(2)).andExpect(jsonPath("$.courses.length()").value(3))
                .andExpect(jsonPath("$.courseSections.length()").value(4)).andExpect(jsonPath("$.lecturers.length()").value(2))
                .andExpect(jsonPath("$.campuses.length()").value(2)).andExpect(jsonPath("$.buildings.length()").value(2))
                .andExpect(jsonPath("$.rooms.length()").value(3)).andExpect(jsonPath("$.meetingTypes").value(org.hamcrest.Matchers.contains("LECTURE", "LAB", "TUTORIAL")))
                .andExpect(jsonPath("$.statuses").value(org.hamcrest.Matchers.contains("DRAFT", "PUBLISHED", "CANCELLED")));
        get(superToken, TIMETABLE + "/filter-options?facultyId=" + otherFaculty).andExpect(jsonPath("$.departments.length()").value(1))
                .andExpect(jsonPath("$.departments[0].id").value(otherDepartment)).andExpect(jsonPath("$.courses.length()").value(0))
                .andExpect(jsonPath("$.lecturers.length()").value(0));
        get(superToken, TIMETABLE + "/filter-options?courseId=" + courseC + "&academicTermId=" + term).andExpect(jsonPath("$.courseSections.length()").value(1));
        get(superToken, TIMETABLE + "/filter-options?campusId=" + otherSite).andExpect(jsonPath("$.buildings.length()").value(1)).andExpect(jsonPath("$.rooms.length()").value(0));
        get(superToken, TIMETABLE + "/filter-options?buildingId=" + building).andExpect(jsonPath("$.rooms.length()").value(3))
                .andExpect(jsonPath("$.rooms[0].parentId").value(building));
    }

    // Exceptions

    @Test
    void exceptionsValidateOccurrencesAndTermDates() throws Exception {
        long lecture = published(sectionA, roomA, 1, "09:00", "11:45", "2026-08-01", "2026-12-15");
        String path = TIMETABLE + "/meetings/" + lecture + "/exceptions";
        post(superToken, path, body("originalDate", "2026-11-10", "exceptionType", "CANCELLED")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_EXCEPTION_INVALID")); // a Tuesday
        post(superToken, path, body("originalDate", "2026-12-21", "exceptionType", "CANCELLED")).andExpect(status().isBadRequest()); // after effectiveTo
        post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "CANCELLED", "replacementDate", "2026-11-10")).andExpect(status().isBadRequest());
        post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "RESCHEDULED", "replacementDate", "2026-11-10")).andExpect(status().isBadRequest());
        post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "RESCHEDULED", "replacementDate", "2027-01-05",
                "replacementStartTime", "09:00", "replacementEndTime", "11:00")).andExpect(status().isBadRequest()); // outside the term
        post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "RESCHEDULED", "replacementDate", "2026-11-10",
                "replacementStartTime", "09:00", "replacementEndTime", "11:00", "replacementRoomId", 999999)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ROOM_NOT_FOUND"));
        long moved = idOf(post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "RESCHEDULED", "replacementDate", "2026-11-10",
                "replacementStartTime", "09:00", "replacementEndTime", "11:00", "reason", "Lecturer away")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.replacementRoomId").value(org.hamcrest.Matchers.nullValue())));
        post(superToken, path, body("originalDate", "2026-11-09", "exceptionType", "CANCELLED")).andExpect(status().isConflict()); // one per occurrence
        get(superToken, path).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].reason").value("Lecturer away"));
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-10T09:00:00+07:00"))
                .andExpect(jsonPath("$.roomName").value("IT-301")); // the meeting's own room is reused
        put(superToken, path + "/" + moved, body("originalDate", "2026-11-09", "exceptionType", "CANCELLED", "reason", "Cancelled instead")).andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionType").value("CANCELLED")).andExpect(jsonPath("$.replacementDate").value(org.hamcrest.Matchers.nullValue()));
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-16T09:00:00+07:00"));
        mvc.perform(auth(delete(path + "/" + moved), superToken)).andExpect(status().isNoContent());
        get(studentToken, "/api/v1/student/academic/next-class").andExpect(jsonPath("$.startsAt").value("2026-11-09T09:00:00+07:00"));
        get(superToken, path + "/" + moved).andExpect(status().isMethodNotAllowed());
        put(superToken, path + "/" + moved, body("originalDate", "2026-11-09", "exceptionType", "CANCELLED")).andExpect(status().isNotFound());
        post(studentToken, path, body("originalDate", "2026-11-16", "exceptionType", "CANCELLED")).andExpect(status().isForbidden());
    }

    // Helpers

    private long published(long section, long room, int day, String start, String end, String from, String to) throws Exception {
        long id = draft(section, room, day, start, end, from, to);
        post(superToken, TIMETABLE + "/meetings/" + id + "/publish", null).andExpect(status().isOk());
        return id;
    }
    private long draft(long section, Long room, int day, String start, String end) throws Exception {
        return draft(section, room, day, start, end, "2026-08-01", "2026-12-15");
    }
    private long draft(long section, Long room, int day, String start, String end, String from, String to) throws Exception {
        var body = meeting(section, room, "LECTURE", day, start, end);
        body.put("effectiveFrom", from);
        body.put("effectiveTo", to);
        return idOf(post(superToken, TIMETABLE + "/meetings", body).andExpect(status().isCreated()));
    }
    private static Map<String, Object> meeting(long section, Long room, String type, int day, String start, String end) {
        return body("courseSectionId", section, "roomId", room, "meetingType", type, "dayOfWeek", day, "startTime", start, "endTime", end,
                "effectiveFrom", "2026-08-01", "effectiveTo", "2026-12-15");
    }
    private static Map<String, Object> slot(Long room, String type, int day, String start, String end, String notes) {
        return body("roomId", room, "meetingType", type, "dayOfWeek", day, "startTime", start, "endTime", end, "effectiveFrom", "2026-08-01",
                "effectiveTo", "2026-12-15", "notes", notes);
    }
    private static Map<String, Object> check(Map<String, Object> meeting, Long meetingId) {
        var body = new LinkedHashMap<>(meeting);
        body.remove("notes");
        if (meetingId != null) body.put("meetingId", meetingId);
        return body;
    }
    private static Map<String, Object> body(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private void clean() {
        for (String table : List.of("timetable_meeting_exceptions", "course_section_meetings", "course_assignments", "course_exams",
                "student_course_result_audits", "student_course_results", "enrollments", "student_credentials", "students", "course_sections",
                "academic_terms", "courses", "lectures", "semesters", "programs", "departments", "faculties",
                "app_user_roles", "appuser_credentials", "app_users", "campus_rooms", "campus_buildings", "campus_sites"))
            db.update("DELETE FROM " + table);
    }
    private long admin(String email, String role) {
        long id = id("INSERT INTO app_users(employee_id,first_name,last_name,email,account_status) VALUES (?,'Timetable','Actor',?,'ACTIVE') RETURNING id", email, email);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", id, role);
        return id;
    }
    private long term(long semester, String code, String label, String from, String to) {
        return id("INSERT INTO academic_terms(semester_id,academic_year_code,academic_year_label,start_date,end_date,status,created_by) VALUES (?,?,?,?::date,?::date,'ACTIVE',?) RETURNING id",
                semester, code, label, from, to, actor);
    }
    private long course(String code, String name) {
        return id("INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,course_type,created_by) VALUES (?,?,3,?,?,300,?) RETURNING id",
                code, name, faculty, department, actor);
    }
    private long section(long course, String number, long lecturer, long semester, Long term) {
        return id("INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,academic_term_id,status,created_by) VALUES (?,?,40,?,?,?,'ACTIVE',?) RETURNING id",
                course, number, lecturer, semester, term, actor);
    }
    private long room(String number, String type) {
        return id("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type,capacity) VALUES (?,?,3,?,40) RETURNING id", building, number, type);
    }
    private long student(String code) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,academic_year,enrollment_year,study_year)
                VALUES (?,?,'First',?,'ACTIVE',?,?,?,?,2026,2024,3) RETURNING id
                """, code, code + "@rsu.ac.th", code, faculty, department, program, semester);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(STUDENT_PASSWORD));
        return id;
    }
    private void enroll(long student, long section, String status) {
        db.update("INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,created_by) VALUES (?,?,DATE '2026-08-15',?,?)", student, section, status, actor);
    }
    private String adminLogin(String email) throws Exception {
        var result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", ADMIN_PASSWORD)))).andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private String studentLogin(String code) throws Exception {
        var result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", STUDENT_PASSWORD)))).andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private ResultActions get(String token, String path) throws Exception { return mvc.perform(auth(MockMvcRequestBuilders.get(path), token)); }
    private ResultActions post(String token, String path, Object body) throws Exception {
        var builder = MockMvcRequestBuilders.post(path);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(auth(builder, token));
    }
    private ResultActions put(String token, String path, Object body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)), token));
    }
    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) { return builder.header("Authorization", "Bearer " + token); }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private long idOf(ResultActions actions) throws Exception { return tree(actions.andReturn()).get("id").asLong(); }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
