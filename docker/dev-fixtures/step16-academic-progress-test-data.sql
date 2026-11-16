-- Step 16A academic progress (spec file 56, section 15.2) test data for the LOCAL development database only, on top of
-- step6-assistant-test-data.sql. Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Apply:
--   docker exec -i smart-university-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step16-academic-progress-test-data.sql
--
-- The grades below are TEST VALUES, not university policy. Scheme code DEV-TEST-PROGRESS must never reach production.
-- Test account: std-test-016@rsu.ac.th (same password as step6-assistant-test-data.sql). It does not touch STD-TEST-001
-- or the DEV-TEST-ONLY scheme of step13b.
--
-- Expected for STD-TEST-016 after applying (GET /api/v1/student/academic/progress and the assistant tool):
--   cgpa 2.57 (54 grade points over 21 credits: A, A, B, B, C, C, F of 3 credits each)
--   creditsEarned 18 (six passed courses of 3 credits)
--   currentCourses 2, currentEnrolledCredits 6 (CS301 SEC-01 and CSC452 SEC-02 of step6, 3 credits each)
--   studyYear 3, academicYear 2026, dataStatus AVAILABLE
--
-- Cleanup (removes only this data):
--   DELETE FROM student_course_result_audits WHERE student_course_result_id IN (SELECT r.id FROM student_course_results r JOIN grade_definitions g ON g.id=r.grade_definition_id JOIN grading_schemes s ON s.id=g.grading_scheme_id WHERE s.scheme_code='DEV-TEST-PROGRESS');
--   DELETE FROM student_course_results WHERE grade_definition_id IN (SELECT g.id FROM grade_definitions g JOIN grading_schemes s ON s.id=g.grading_scheme_id WHERE s.scheme_code='DEV-TEST-PROGRESS');
--   DELETE FROM enrollments WHERE student_id=(SELECT id FROM students WHERE student_code='STD-TEST-016');
--   DELETE FROM course_sections WHERE course_id IN (SELECT id FROM courses WHERE course_code LIKE 'PGR10%');
--   DELETE FROM courses WHERE course_code LIKE 'PGR10%';
--   DELETE FROM semesters WHERE semester_name_th='ภาคการศึกษาที่ 2 (ทดสอบ ความก้าวหน้า)';
--   DELETE FROM grade_definitions WHERE grading_scheme_id=(SELECT id FROM grading_schemes WHERE scheme_code='DEV-TEST-PROGRESS');
--   DELETE FROM grading_schemes WHERE scheme_code='DEV-TEST-PROGRESS';
--   DELETE FROM student_credentials WHERE student_id=(SELECT id FROM students WHERE student_code='STD-TEST-016');
--   DELETE FROM students WHERE student_code='STD-TEST-016';

BEGIN;
INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,
                     academic_year,enrollment_year,study_year)
  SELECT 'STD-TEST-016', 'std-test-016@rsu.ac.th', 'Test', 'Sixteen', 'ACTIVE', d.faculty_id, d.id, p.id, s.id, 2026, 2024, 3
  FROM departments d JOIN programs p ON p.program_code='CS-TEST' JOIN semesters s ON s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)'
  WHERE d.department_code='CS-TEST';
INSERT INTO student_credentials(student_id,password_hash,force_password_change)
  SELECT id, '$2y$10$j4HDcq4bxFUQs1Dz1EkfzuzQ6Wrm/SW0nfJ9YIdbkdsS6Jx/C4yAG', FALSE FROM students WHERE student_code='STD-TEST-016';
-- Current semester: the same two sections as STD-TEST-001.
INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
  SELECT st.id, cs.id, DATE '2026-08-15', 'ACTIVE', FALSE, 1
  FROM (VALUES ('CS301','SEC-01'),('CSC452','SEC-02')) v(code,sec)
  JOIN students st ON st.student_code='STD-TEST-016'
  JOIN courses c ON c.course_code=v.code AND NOT c.is_deleted
  JOIN course_sections cs ON cs.course_id=c.id AND cs.section_number=v.sec
  JOIN semesters s ON s.id=cs.semester_id AND s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)';

INSERT INTO grading_schemes(scheme_code,scheme_name,description,repeat_attempt_policy)
  VALUES ('DEV-TEST-PROGRESS','Development progress test scheme','Test values for Postman checks, not university policy','LATEST_ATTEMPT');
INSERT INTO grade_definitions(grading_scheme_id,grade_code,grade_point,counts_in_gpa,earns_credit,is_passing)
  SELECT s.id, v.code, v.point, TRUE, v.earns, v.pass
  FROM grading_schemes s, (VALUES ('A',4.00,TRUE,TRUE),('B',3.00,TRUE,TRUE),('C',2.00,TRUE,TRUE),('F',0.00,FALSE,FALSE)) AS v(code,point,earns,pass)
  WHERE s.scheme_code='DEV-TEST-PROGRESS';

-- Past semester with seven finalized results.
INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by)
  VALUES (2025,'ภาคการศึกษาที่ 2 (ทดสอบ ความก้าวหน้า)','Semester 2',1);
INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,program_id,course_type,created_by)
  SELECT 'PGR10' || n, 'Progress Test Course ' || n, 3, d.faculty_id, d.id, p.id, 300, 1
  FROM departments d JOIN programs p ON p.program_code='CS-TEST', generate_series(1,7) n
  WHERE d.department_code='CS-TEST';
INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
  SELECT c.id, 'SEC-01', 40, l.id, s.id, 'ACTIVE', 1
  FROM courses c
  JOIN lectures l ON l.lecture_name_en='Dr. John Smith'
  JOIN departments d ON d.id=l.department_id AND d.department_code='CS-TEST'
  JOIN semesters s ON s.semester_name_th='ภาคการศึกษาที่ 2 (ทดสอบ ความก้าวหน้า)'
  WHERE c.course_code LIKE 'PGR10%';
INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
  SELECT st.id, cs.id, DATE '2025-11-10', 'ACTIVE', FALSE, 1
  FROM students st, course_sections cs JOIN courses c ON c.id=cs.course_id
  WHERE st.student_code='STD-TEST-016' AND c.course_code LIKE 'PGR10%';
INSERT INTO student_course_results(enrollment_id,grade_definition_id,credits_attempted,result_status,finalized_at,finalized_by,created_by)
  SELECT e.id, g.id, 3, 'FINALIZED', CURRENT_TIMESTAMP, 1, 1
  FROM enrollments e
  JOIN students st ON st.id=e.student_id AND st.student_code='STD-TEST-016'
  JOIN course_sections cs ON cs.id=e.course_section_id
  JOIN courses c ON c.id=cs.course_id AND c.course_code LIKE 'PGR10%'
  JOIN (VALUES ('PGR101','A'),('PGR102','A'),('PGR103','B'),('PGR104','B'),('PGR105','C'),('PGR106','C'),('PGR107','F')) v(code,grade)
    ON v.code=c.course_code
  JOIN grade_definitions g ON g.grade_code=v.grade
  JOIN grading_schemes s ON s.id=g.grading_scheme_id AND s.scheme_code='DEV-TEST-PROGRESS';
COMMIT;
