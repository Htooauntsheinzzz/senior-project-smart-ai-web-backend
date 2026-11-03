-- Step 11 (spec 36) grounding test data for the LOCAL development database only, on top of
-- step6-assistant-test-data.sql. Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Apply:
--   docker exec -i smart-university-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step11-grounding-test-data.sql
--
-- Test account (same password as Step 6: StepSix-Test-2026!)
--   std-test-003@rsu.ac.th  STD-TEST-003  ACTIVE, same faculty/program/semester as STD-TEST-001, NO enrollments
--                                          (Postman "no enrollments" check, spec §72).
--
-- Controlled changes for the Postman missing-room and changed-room checks (spec §71, §74), run one at a time:
--   UPDATE course_sections SET room=NULL     WHERE section_number='SEC-01' AND course_id=(SELECT id FROM courses WHERE course_code='CS301' AND NOT is_deleted);
--   UPDATE course_sections SET room='11-405' WHERE section_number='SEC-01' AND course_id=(SELECT id FROM courses WHERE course_code='CS301' AND NOT is_deleted);
-- Restore the original room afterwards:
--   UPDATE course_sections SET room='11-402' WHERE section_number='SEC-01' AND course_id=(SELECT id FROM courses WHERE course_code='CS301' AND NOT is_deleted);
--
-- Cleanup (removes only this data):
--   DELETE FROM student_credentials WHERE student_id IN (SELECT id FROM students WHERE student_code='STD-TEST-003');
--   DELETE FROM students WHERE student_code='STD-TEST-003';

BEGIN;
INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,academic_year,enrollment_year)
  SELECT 'STD-TEST-003', 'std-test-003@rsu.ac.th', 'Test Three', 'Student', 'ACTIVE', d.faculty_id, d.id, p.id, s.id, 2026, 2026
  FROM departments d JOIN programs p ON p.program_code='CS-TEST'
  JOIN semesters s ON s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)'
  WHERE d.department_code='CS-TEST';
INSERT INTO student_credentials(student_id,password_hash,force_password_change)
  SELECT id, password_hash, FALSE FROM students, (SELECT password_hash FROM student_credentials
    WHERE student_id=(SELECT id FROM students WHERE student_code='STD-TEST-001')) h
  WHERE student_code='STD-TEST-003';
COMMIT;
