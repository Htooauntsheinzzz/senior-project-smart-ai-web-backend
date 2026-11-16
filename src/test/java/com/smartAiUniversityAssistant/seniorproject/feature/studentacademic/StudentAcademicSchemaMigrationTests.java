package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic;

import static org.assertj.core.api.Assertions.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V26 to V32 on an empty schema: no invented grading policy, and the constraints the spec asks for. */
class StudentAcademicSchemaMigrationTests extends IntegrationSupport {
    private String schema;
    private Flyway flyway;
    private JdbcTemplate db;

    @BeforeEach
    void migrate() {
        schema = "academic_data_" + UUID.randomUUID().toString().replace("-", "");
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).cleanDisabled(false).load();
        flyway.migrate();
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @AfterEach
    void drop() { flyway.clean(); }

    @Test
    void noGradingPolicyOrAcademicActivityIsSeeded() {
        for (String table : new String[] {"grading_schemes", "grade_definitions", "student_course_results",
                "student_course_result_audits", "course_section_meetings", "course_assignments", "course_exams"})
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
    }

    @Test
    void gradeDefinitionConstraintsHold() {
        long scheme = db.queryForObject("INSERT INTO grading_schemes(scheme_code,scheme_name) VALUES ('T','T') RETURNING id", Long.class);
        String insert = "INSERT INTO grade_definitions(grading_scheme_id,grade_code,grade_point,counts_in_gpa,earns_credit,is_passing) VALUES (?,?,?::numeric,?,?,?)";
        db.update(insert, scheme, "A", "4.00", true, true, true);
        assertThatThrownBy(() -> db.update(insert, scheme, "A", "3.00", true, true, true)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(insert, scheme, "X", null, true, true, true)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(insert, scheme, "Y", "1.00", true, true, false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE grading_schemes SET repeat_attempt_policy='MADE_UP'")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void studyYearAndEnrollmentStatusesStayAsSpecified() {
        String constraint = "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=? AND connamespace=?::regnamespace";
        assertThat(db.queryForObject(constraint, String.class, "chk_students_study_year", schema)).contains("1", "10");
        assertThat(db.queryForObject(constraint, String.class, "chk_enrollments_status", schema)).doesNotContain("COMPLETED");
        assertThat(db.queryForObject(constraint, String.class, "chk_course_section_meetings_day", schema)).contains("1", "7");
        assertThat(db.queryForObject(constraint, String.class, "chk_course_section_meetings_time", schema)).contains("end_time > start_time");
        assertThat(db.queryForObject(constraint, String.class, "chk_course_exams_time", schema)).contains("ends_at > starts_at");
        assertThat(db.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? AND table_name='course_assignments' AND column_name='due_at'", String.class, schema))
                .isEqualTo("timestamp with time zone");
    }
}
