package com.smartAiUniversityAssistant.seniorproject.feature.timetable;

import static org.assertj.core.api.Assertions.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V40 to V44 on an empty schema (spec file 42, section 58), plus the V42 backfill of legacy V30 meeting rows. */
class TimetableSchemaMigrationTests extends IntegrationSupport {
    private String schema;
    private Flyway flyway;
    private JdbcTemplate db;
    private long actor, faculty, department, lecturer, semester, course, section, site, building, room;

    @BeforeEach
    void prepare() {
        schema = "timetable_" + UUID.randomUUID().toString().replace("-", "");
        flyway = configure(null);
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @AfterEach
    void drop() { flyway.clean(); }

    private Flyway configure(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private void academicFixture() {
        actor = id("INSERT INTO app_users(employee_id,first_name,last_name,email,account_status) VALUES ('T1','T','T','t@example.test','ACTIVE') RETURNING id");
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('F','Faculty',?) RETURNING id", actor);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('D','Dept',?,?) RETURNING id", faculty, actor);
        lecturer = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('ล','L',?,?,?) RETURNING id", faculty, department, actor);
        semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'เทอม 1','Semester 1',?) RETURNING id", actor);
        course = id("INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,course_type,created_by) VALUES ('C1','Course',3,?,?,300,?) RETURNING id", faculty, department, actor);
        section = id("INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,created_by) VALUES (?,'01',40,?,?,?) RETURNING id", course, lecturer, semester, actor);
        site = id("INSERT INTO campus_sites(site_code,site_name) VALUES ('S1','Site') RETURNING id");
        building = id("INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) VALUES (?,'B1','Building',3) RETURNING id", site);
        room = id("INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) VALUES (?,'101',1,'CLASSROOM') RETURNING id", building);
    }

    @Test
    void historyAppliesAndNothingIsSeeded() {
        flyway.migrate();
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version::int >= 40 ORDER BY installed_rank", String.class))
                .containsExactly("40", "41", "42", "43", "44", "45", "46", "47");
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Long.class)).isEqualTo(47);
        for (String table : new String[] {"academic_terms", "timetable_meeting_exceptions", "course_section_meetings"})
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
        assertThat(db.queryForObject("SELECT description FROM flyway_schema_history WHERE version='30'", String.class)).isEqualTo("create course section meetings");
        // Course sections gained a nullable term FK; their ids and the enrollment FK are untouched.
        assertThat(db.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=? AND table_name='course_sections' AND column_name='academic_term_id'", String.class, schema)).isEqualTo("YES");
        assertThat(db.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN ('fk_course_sections_academic_term','fk_course_section_meetings_room','fk_enrollments_course_section') AND connamespace=?::regnamespace", Long.class, schema)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND connamespace=?::regnamespace AND confdeltype='c'", Long.class, schema)).isZero();
    }

    @Test
    void academicTermConstraintsHold() {
        flyway.migrate();
        academicFixture();
        String insert = "INSERT INTO academic_terms(semester_id,academic_year_code,academic_year_label,start_date,end_date,status) VALUES (?,?,?,?::date,?::date,?)";
        db.update(insert, semester, "2569", "2026-2027", "2026-08-01", "2026-12-15", "PLANNED");
        assertInvalid(() -> db.update(insert, semester, "2569", "other label", "2026-08-01", "2026-12-15", "PLANNED")); // unique semester + year
        assertInvalid(() -> db.update(insert, semester, "2570", "2027-2028", "2027-08-01", "2027-07-31", "PLANNED")); // end before start
        assertInvalid(() -> db.update(insert, semester, "2571", "x", "2027-08-01", "2027-12-15", "OPEN")); // status
        assertInvalid(() -> db.update(insert, -1L, "2572", "x", "2027-08-01", "2027-12-15", "PLANNED")); // semester FK
        assertInvalid(() -> db.update("UPDATE course_sections SET academic_term_id=-1 WHERE id=?", section)); // section FK
    }

    @Test
    void meetingAndExceptionConstraintsHold() {
        flyway.migrate();
        academicFixture();
        String meeting = "INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,room_id,status) VALUES (?,?,?,?::time,?::time,?::date,?::date,?,?) RETURNING id";
        long draft = id(meeting, section, "LECTURE", 1, "09:00", "11:00", "2026-08-01", "2026-12-15", null, "DRAFT");
        long published = id(meeting, section, "LAB", 2, "09:00", "11:00", "2026-08-01", "2026-12-15", room, "PUBLISHED");
        assertInvalid(() -> id(meeting, section, "LECTURE", 3, "09:00", "11:00", "2026-08-01", "2026-12-15", null, "PUBLISHED")); // published needs a room
        assertInvalid(() -> id(meeting, section, "LECTURE", 3, "09:00", "11:00", "2026-08-01", "2026-12-15", -1L, "DRAFT")); // room FK
        assertInvalid(() -> id(meeting, section, "LECTURE", 3, "09:00", "11:00", "2026-08-01", "2026-12-15", room, "LIVE")); // status
        assertInvalid(() -> id(meeting, section, "LECTURE", 8, "09:00", "11:00", "2026-08-01", "2026-12-15", room, "DRAFT")); // weekday
        assertInvalid(() -> id(meeting, section, "LECTURE", 7, "11:00", "09:00", "2026-08-01", "2026-12-15", room, "DRAFT")); // time
        assertInvalid(() -> id(meeting, section, "LECTURE", 7, "09:00", "11:00", "2026-12-16", "2026-12-15", room, "DRAFT")); // dates
        assertThat(db.queryForObject("SELECT count(*) FROM course_section_meetings WHERE id=? AND is_deleted=FALSE", Long.class, draft)).isOne();

        String exception = "INSERT INTO timetable_meeting_exceptions(meeting_id,original_date,exception_type,replacement_date,replacement_start_time,replacement_end_time,replacement_room_id) VALUES (?,?::date,?,?::date,?::time,?::time,?)";
        db.update(exception, published, "2026-11-10", "CANCELLED", null, null, null, null);
        assertInvalid(() -> db.update(exception, published, "2026-11-10", "CANCELLED", null, null, null, null)); // one exception per occurrence
        db.update(exception, published, "2026-11-17", "RESCHEDULED", "2026-11-19", "13:00", "15:00", room);
        assertInvalid(() -> db.update(exception, published, "2026-11-24", "CANCELLED", "2026-11-26", null, null, null)); // cancellation with replacement
        assertInvalid(() -> db.update(exception, published, "2026-11-24", "RESCHEDULED", "2026-11-26", null, null, null)); // reschedule without times
        assertInvalid(() -> db.update(exception, published, "2026-11-24", "RESCHEDULED", "2026-11-26", "15:00", "13:00", null)); // end before start
        assertInvalid(() -> db.update(exception, published, "2026-11-24", "MOVED", null, null, null, null)); // type
        assertInvalid(() -> db.update(exception, -1L, "2026-11-24", "CANCELLED", null, null, null, null)); // meeting FK
        // A soft-deleted exception frees the occurrence for a new one.
        db.update("UPDATE timetable_meeting_exceptions SET is_deleted=TRUE WHERE original_date=DATE '2026-11-10'");
        assertThatCode(() -> db.update(exception, published, "2026-11-10", "CANCELLED", null, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void legacyMeetingsAreBackfilledWithoutLosingStudentVisibility() {
        configure("41").migrate();
        academicFixture();
        String legacy = "INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,room_id,room_label,is_active) VALUES (?,?,?,?::time,?::time,?::date,?::date,?,?,?) RETURNING id";
        long visible = id(legacy, section, "LECTURE", 1, "09:00", "11:00", "2026-08-01", "2026-12-15", null, "1103", true);
        long hidden = id(legacy, section, "LAB", 2, "09:00", "11:00", "2026-08-01", "2026-12-15", null, "1104", false);
        long dangling = id(legacy, section, "LAB", 3, "09:00", "11:00", "2026-08-01", "2026-12-15", 424242L, "1105", true);
        flyway.migrate();
        assertThat(db.queryForObject("SELECT status FROM course_section_meetings WHERE id=?", String.class, visible)).isEqualTo("PUBLISHED");
        assertThat(db.queryForObject("SELECT status FROM course_section_meetings WHERE id=?", String.class, hidden)).isEqualTo("DRAFT");
        assertThat(db.queryForObject("SELECT room_id FROM course_section_meetings WHERE id=?", Long.class, dangling)).isNull();
        assertThat(db.queryForObject("SELECT room_label FROM course_section_meetings WHERE id=?", String.class, visible)).isEqualTo("1103");
        // The legacy published row may still be soft-deleted, but any other edit needs a campus room.
        assertInvalid(() -> db.update("UPDATE course_section_meetings SET notes='x' WHERE id=?", visible));
        assertThatCode(() -> db.update("UPDATE course_section_meetings SET room_id=? WHERE id=?", room, visible)).doesNotThrowAnyException();
        assertThatCode(() -> db.update("UPDATE course_section_meetings SET is_deleted=TRUE WHERE id=?", dangling)).doesNotThrowAnyException();
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class);
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
