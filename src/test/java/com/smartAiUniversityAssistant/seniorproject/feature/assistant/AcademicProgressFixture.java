package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentAcademicProgressResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec file 56, section 15.1 (Testcontainers only, never a migration): on top of AssistantAcademicFixture, Student A gets
 * seven finalized results in a past semester (grades A, A, B, B, C, C, F of scheme TEST-PROGRESS, 3 credits each) and
 * study year 3. Expected: Student A cgpa 2.57, creditsEarned 18, currentCourses 3, currentEnrolledCredits 9, AVAILABLE;
 * Student B cgpa and creditsEarned null, PARTIAL; Student C everything null, PARTIAL. Call clean() before
 * AssistantAcademicFixture.create() and after the test class.
 */
public final class AcademicProgressFixture {
    private final JdbcTemplate db;

    public AcademicProgressFixture(JdbcTemplate db) { this.db = db; }

    public void create(AssistantAcademicFixture base, AcademicProgressService progress) {
        long actor = db.queryForObject("SELECT id FROM app_users WHERE employee_id='S8-ACTOR'", Long.class);
        long program = db.queryForObject("SELECT program_id FROM students WHERE id=?", Long.class, base.studentA());
        long department = db.queryForObject("SELECT department_id FROM students WHERE id=?", Long.class, base.studentA());
        long faculty = db.queryForObject("SELECT faculty_id FROM students WHERE id=?", Long.class, base.studentA());
        long lecturer = db.queryForObject("SELECT id FROM lectures WHERE lecture_name_en='Dr. John Smith'", Long.class);
        long scheme = db.queryForObject("""
                INSERT INTO grading_schemes(scheme_code,scheme_name,description,repeat_attempt_policy)
                VALUES ('TEST-PROGRESS','Progress test scheme','Test values only','LATEST_ATTEMPT') RETURNING id""", Long.class);
        db.update("""
                INSERT INTO grade_definitions(grading_scheme_id,grade_code,grade_point,counts_in_gpa,earns_credit,is_passing)
                VALUES (?,'A',4.00,TRUE,TRUE,TRUE),(?,'B',3.00,TRUE,TRUE,TRUE),(?,'C',2.00,TRUE,TRUE,TRUE),(?,'F',0.00,TRUE,FALSE,FALSE)""",
                scheme, scheme, scheme, scheme);
        long semester = db.queryForObject("""
                INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by)
                VALUES (2025,'ภาคการศึกษาที่ 2','Semester 2',?) RETURNING id""", Long.class, actor);
        String[] grades = {"A", "A", "B", "B", "C", "C", "F"};
        for (int i = 0; i < grades.length; i++) {
            long course = db.queryForObject("""
                    INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,program_id,course_type,created_by)
                    VALUES (?,?,3,?,?,?,300,?) RETURNING id""", Long.class, "PGR10" + (i + 1), "Progress Test Course " + (i + 1),
                    faculty, department, program, actor);
            long section = db.queryForObject("""
                    INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
                    VALUES (?,'SEC-01',40,?,?,'ACTIVE',?) RETURNING id""", Long.class, course, lecturer, semester, actor);
            long enrollment = db.queryForObject("""
                    INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
                    VALUES (?,?,DATE '2025-11-10','ACTIVE',FALSE,?) RETURNING id""", Long.class, base.studentA(), section, actor);
            db.update("""
                    INSERT INTO student_course_results(enrollment_id,grade_definition_id,credits_attempted,result_status,finalized_at,
                        finalized_by,created_by)
                    SELECT ?, id, 3, 'FINALIZED', now(), ?, ? FROM grade_definitions WHERE grading_scheme_id=? AND grade_code=?""",
                    enrollment, actor, actor, scheme, grades[i]);
        }
        db.update("UPDATE students SET study_year=3 WHERE id=?", base.studentA());
        check(progress.getProgress(base.studentA()), new BigDecimal("2.57"), new BigDecimal("18"), 3, 9, 3, 2026, "AVAILABLE", "A");
        check(progress.getProgress(base.studentB()), null, null, 3, 9, null, 2026, "PARTIAL", "B");
        check(progress.getProgress(base.studentC()), null, null, null, null, null, null, "PARTIAL", "C");
    }

    private static void check(StudentAcademicProgressResponse p, BigDecimal cgpa, BigDecimal credits, Integer courses, Integer current,
            Integer studyYear, Integer academicYear, String status, String student) {
        boolean ok = same(p.cgpa(), cgpa) && same(p.creditsEarned(), credits) && Objects.equals(p.currentCourses(), courses)
                && Objects.equals(p.currentEnrolledCredits(), current) && Objects.equals(p.studyYear(), studyYear)
                && Objects.equals(p.academicYear(), academicYear) && Objects.equals(p.dataStatus(), status);
        if (!ok) throw new IllegalStateException("AcademicProgressFixture: unexpected progress of Student " + student + ": " + p
                + " (did AssistantAcademicFixture change?)");
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Safe when nothing exists. */
    public void clean() {
        db.update("""
                DELETE FROM student_course_result_audits WHERE student_course_result_id IN (SELECT r.id FROM student_course_results r
                    JOIN grade_definitions g ON g.id=r.grade_definition_id JOIN grading_schemes s ON s.id=g.grading_scheme_id
                    WHERE s.scheme_code='TEST-PROGRESS')""");
        db.update("""
                DELETE FROM student_course_results WHERE grade_definition_id IN (SELECT g.id FROM grade_definitions g
                    JOIN grading_schemes s ON s.id=g.grading_scheme_id WHERE s.scheme_code='TEST-PROGRESS')""");
        db.update("""
                DELETE FROM enrollments WHERE course_section_id IN (SELECT cs.id FROM course_sections cs JOIN courses c ON c.id=cs.course_id
                    WHERE c.course_code LIKE 'PGR1%')""");
        db.update("DELETE FROM course_sections WHERE course_id IN (SELECT id FROM courses WHERE course_code LIKE 'PGR1%')");
        db.update("DELETE FROM courses WHERE course_code LIKE 'PGR1%'");
        db.update("DELETE FROM semesters WHERE academic_year=2025 AND semester_name_en='Semester 2' AND semester_name_th='ภาคการศึกษาที่ 2'");
        db.update("DELETE FROM grade_definitions WHERE grading_scheme_id IN (SELECT id FROM grading_schemes WHERE scheme_code='TEST-PROGRESS')");
        db.update("DELETE FROM grading_schemes WHERE scheme_code='TEST-PROGRESS'");
        db.update("UPDATE students SET study_year=NULL WHERE student_code='STD-TEST-001'");
    }
}
