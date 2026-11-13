package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The synthetic academic data shared by the Step 8 regression tests and the Step 9 evaluation (Testcontainers only,
 * never a Flyway migration). Student A (STD-TEST-001, Computer Science): CS301 SEC-01 (Dr. John Smith, 11-402,
 * Tuesday 09:00-11:45), CSC452 SEC-02 (Dr. Jane Lee, 5-201, Friday 09:00-11:45), CSC460 SEC-01 (no published timetable),
 * CSC453 DROPPED. Student B (STD-TEST-002, Information Systems): CS301 SEC-02 (Dr. Jane Doe, 11-500), CSC452 SEC-02,
 * CSC453 SEC-01. Student C (STD-TEST-003) has no enrollments. CS301 SEC-01 holds 3 of 40 seats (1 ACTIVE + 2 PENDING),
 * CSC470 SEC-01 is full (2 of 2), CSC470 SEC-02 is CLOSED, DEL100 is deleted, OLD200 inactive, CSC499 has no section.
 * Placeholders such as ${CS301_SEC01} stand for generated ids (1501 and up) in the datasets.
 */
public final class AssistantAcademicFixture {
    public static final String PASSWORD = "intent regression password";
    public static final String FIXTURE_VERSION = "step08-step09-v3";
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final Map<String, Object> placeholders = new HashMap<>();
    private long actorId, faculty, department, studentA, studentB, studentC;

    public AssistantAcademicFixture(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    public long studentA() { return studentA; }
    public long studentB() { return studentB; }
    public long studentC() { return studentC; }
    public Map<String, Object> placeholders() { return Collections.unmodifiableMap(placeholders); }

    /** The database id of a dataset student code. */
    public long studentId(String code) {
        return switch (code) {
            case "STD-TEST-002" -> studentB;
            case "STD-TEST-003" -> studentC;
            default -> studentA;
        };
    }

    public void create() {
        clean();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('S8-ACTOR','Intent','Actor','intent.actor@example.test','ACTIVE') RETURNING id
                """);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('IT','Information Technology','เทคโนโลยีสารสนเทศ',?) RETURNING id", actorId);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actorId);
        long cs = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('CS','Computer Science','Bachelor',?,?) RETURNING id", department, actorId);
        long is = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('IS','Information Systems','Bachelor',?,?) RETURNING id", department, actorId);
        long smith = lecturer("จอห์น", "Dr. John Smith");
        long lee = lecturer("เจน", "Dr. Jane Lee");
        long doe = lecturer("โด", "Dr. Jane Doe");
        long semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        long cs301 = course("CS301", "Data Structures", "Linear and non-linear data structures", "CS201", cs);
        long csc452 = course("CSC452", "Mobile Programming", "Mobile app development", null, cs);
        long csc453 = course("CSC453", "Web Programming", "Web development", null, cs);
        long csc460 = course("CSC460", "Cloud Computing", "Cloud services", null, cs);
        long csc470 = course("CSC470", "Capstone Lab", "Project lab", null, cs);
        course("CSC499", "Special Topics", null, null, null);
        long retired = course("DEL100", "Retired course", "Deleted from the catalogue", null, cs);
        long old = course("OLD200", "Old Systems", "No longer offered", null, cs);
        db.update("UPDATE courses SET is_deleted=TRUE WHERE id=?", retired);
        db.update("UPDATE courses SET is_active=FALSE WHERE id=?", old);
        // Section ids from 1501 up, so "section <id>" never reads like a section label such as SEC-01 (live run, D50).
        db.queryForObject("SELECT setval(pg_get_serial_sequence('course_sections','id'), GREATEST(1500, "
                + "nextval(pg_get_serial_sequence('course_sections','id'))))", Long.class);
        long cs301A = section(cs301, "SEC-01", smith, semester, "11-402", "Tuesday 09:00-11:45", 40, "ACTIVE");
        long cs301B = section(cs301, "SEC-02", doe, semester, "11-500", "Monday 13:00-15:45", 40, "ACTIVE");
        long csc452A = section(csc452, "SEC-02", lee, semester, "5-201", "Friday 09:00-11:45", 40, "ACTIVE");
        long csc453A = section(csc453, "SEC-01", lee, semester, null, null, 40, "ACTIVE");
        long csc460A = section(csc460, "SEC-01", lee, semester, null, null, 40, "ACTIVE");
        long csc470A = section(csc470, "SEC-01", smith, semester, "9-101", "Wednesday 13:00-15:45", 2, "ACTIVE");
        long csc470B = section(csc470, "SEC-02", smith, semester, "9-102", "Thursday 13:00-15:45", 30, "CLOSED");
        String hash = encoder.encode(PASSWORD);
        studentA = student("STD-TEST-001", hash);
        studentB = student("STD-TEST-002", hash);
        studentC = student("STD-TEST-003", hash);
        db.update("UPDATE students SET faculty_id=?,department_id=?,program_id=?,semester_id=?,academic_year=2026,enrollment_year=2024 WHERE id=?",
                faculty, department, cs, semester, studentA);
        db.update("UPDATE students SET faculty_id=?,department_id=?,program_id=?,semester_id=?,academic_year=2026,enrollment_year=2025 WHERE id=?",
                faculty, department, is, semester, studentB);
        enroll(studentA, cs301A, "ACTIVE");
        enroll(studentA, csc452A, "ACTIVE");
        enroll(studentA, csc460A, "ACTIVE");
        enroll(studentA, csc453A, "DROPPED");
        enroll(studentB, cs301B, "ACTIVE");
        enroll(studentB, csc452A, "ACTIVE");
        enroll(studentB, csc453A, "ACTIVE");
        // CS301 SEC-01: A's ACTIVE seat + 2 PENDING hold seats; WITHDRAWN and DROPPED do not (3 of 40).
        // CSC470 SEC-01: 1 PENDING + 1 ACTIVE fill both seats; WITHDRAWN and DROPPED do not count.
        String[] statuses = {"PENDING", "PENDING", "WITHDRAWN", "DROPPED"};
        for (int i = 0; i < statuses.length; i++) enroll(student("STD-SEAT-A" + i, hash), cs301A, statuses[i]);
        String[] full = {"PENDING", "ACTIVE", "WITHDRAWN", "DROPPED"};
        for (int i = 0; i < full.length; i++) enroll(student("STD-SEAT-B" + i, hash), csc470A, full[i]);
        placeholders.put("CS301_SEC01", cs301A);
        placeholders.put("CSC470_SEC01", csc470A);
        placeholders.put("CSC470_SEC02", csc470B);
        placeholders.put("STUDENT_B", studentB);
    }

    /** A fingerprint of every enrollment row, to prove a chat changed nothing. */
    public String enrollmentFingerprint() {
        return db.queryForObject("""
                SELECT count(*) || ':' || coalesce(md5(string_agg(id || '/' || student_id || '/' || course_section_id || '/' || status
                       || '/' || is_deleted, ',' ORDER BY id)), '-') FROM enrollments
                """, String.class);
    }

    public void clean() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        // Step 17: stored assistant conversations reference the Student (no cascade, as everywhere in the schema).
        db.update("DELETE FROM assistant_messages");
        db.update("DELETE FROM assistant_conversations");
        db.update("DELETE FROM students");
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        PublishedMeetingTestData.clean(db);
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

    private long course(String code, String name, String description, String prerequisites, Long program) {
        return id("""
                INSERT INTO courses(course_code,course_name,credit_hours,description,prerequisite_courses,faculty_id,department_id,
                                    program_id,course_type,created_by)
                VALUES (?,?,3,?,?,?,?,?,300,?) RETURNING id
                """, code, name, description, prerequisites, faculty, department, program, actorId);
    }
    private long lecturer(String nameTh, String nameEn) {
        return id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                nameTh, nameEn, faculty, department, actorId);
    }
    private long section(long course, String number, long lecture, long semester, String room, String schedule, int capacity,
            String status) {
        long id = id("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """, course, number, capacity, lecture, semester, status, actorId);
        PublishedMeetingTestData.publish(db, id, room, schedule);
        return id;
    }
    private long student(String code, String hash) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,'Test','Student','ACTIVE') RETURNING id
                """, code, code + "@rsu.ac.th");
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private void enroll(long student, long section, String status) {
        db.update("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
                VALUES (?,?,DATE '2026-08-15',?,FALSE,?)
                """, student, section, status, actorId);
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
