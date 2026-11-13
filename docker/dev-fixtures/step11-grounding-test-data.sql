-- Step 11 (spec 36) grounding test data for the LOCAL development database only, on top of
-- step6-assistant-test-data.sql. Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Apply:
--   docker exec -i smart-university-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step11-grounding-test-data.sql
--
-- Test account (same password as Step 6: StepSix-Test-2026!)
--   std-test-003@rsu.ac.th  STD-TEST-003  ACTIVE, same faculty/program/semester as STD-TEST-001, NO enrollments
--                                          (Postman "no enrollments" check, spec §72).
--
-- Controlled changes for the Postman missing-room and changed-room checks (spec §71, §74), run one at a time
-- (updated 2026-11-10, D81: rooms are published timetable meetings since V45; see step6-assistant-test-data.sql):
--   UPDATE course_section_meetings SET status='DRAFT', is_active=FALSE WHERE course_section_id=(SELECT cs.id FROM course_sections cs JOIN courses c ON c.id=cs.course_id WHERE c.course_code='CS301' AND NOT c.is_deleted AND cs.section_number='SEC-01') AND room_label IN ('11-402','11-405');
--   INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type) SELECT id,'11-405',11,'CLASSROOM' FROM campus_buildings WHERE building_code='S6-B1' ON CONFLICT DO NOTHING;
--   UPDATE course_section_meetings SET room_id=(SELECT r.id FROM campus_rooms r JOIN campus_buildings b ON b.id=r.building_id WHERE b.building_code='S6-B1' AND r.room_number='11-405'), room_label='11-405', status='PUBLISHED', is_active=TRUE WHERE course_section_id=(SELECT cs.id FROM course_sections cs JOIN courses c ON c.id=cs.course_id WHERE c.course_code='CS301' AND NOT c.is_deleted AND cs.section_number='SEC-01') AND room_label IN ('11-402','11-405');
-- Restore the original room afterwards:
--   UPDATE course_section_meetings SET room_id=(SELECT r.id FROM campus_rooms r JOIN campus_buildings b ON b.id=r.building_id WHERE b.building_code='S6-B1' AND r.room_number='11-402'), room_label='11-402', status='PUBLISHED', is_active=TRUE WHERE course_section_id=(SELECT cs.id FROM course_sections cs JOIN courses c ON c.id=cs.course_id WHERE c.course_code='CS301' AND NOT c.is_deleted AND cs.section_number='SEC-01') AND room_label IN ('11-402','11-405');
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
