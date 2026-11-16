-- Student self-enrollment (spec file 43) test data for a LOCAL development database only.
-- Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Self-contained (does not need the other fixtures). Needs V46 and the seeded super admin (id 1).
-- Apply (isolated copy used on 2026-11-10, Compose project smcheck):
--   docker exec -i smcheck-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step14-student-self-enrollment-test-data.sql
--
-- Test accounts (password for both: Enroll-Check-2026!)
--   enroll-check-001@rsu.ac.th  ENR-001  ACTIVE in CS301 SEC-01 and in last year's CS210; can request CS470 SEC-01 or SEC-02
--   enroll-check-002@rsu.ac.th  ENR-002  holds the only seat of CS480 SEC-01, so it shows as Full to ENR-001
--
-- Cleanup (removes only this data):
--   DELETE FROM enrollments WHERE student_id IN (SELECT id FROM students WHERE student_code LIKE 'ENR-%');
--   DELETE FROM student_credentials WHERE student_id IN (SELECT id FROM students WHERE student_code LIKE 'ENR-%');
--   DELETE FROM students WHERE student_code LIKE 'ENR-%';
--   DELETE FROM course_sections WHERE semester_id IN (SELECT id FROM semesters WHERE semester_name_th LIKE '%(ตรวจ)');
--   DELETE FROM courses WHERE department_id IN (SELECT id FROM departments WHERE department_code='ENR-D');
--   DELETE FROM lectures WHERE department_id IN (SELECT id FROM departments WHERE department_code='ENR-D');
--   DELETE FROM semesters WHERE semester_name_th LIKE '%(ตรวจ)';
--   DELETE FROM programs WHERE program_code='ENR-P';
--   DELETE FROM departments WHERE department_code='ENR-D';
--   DELETE FROM faculties WHERE faculty_code='ENR-F';

BEGIN;
INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ENR-F','Enrollment Check Faculty','คณะทดสอบ',1);
INSERT INTO departments(department_code,department_name,faculty_id,created_by) SELECT 'ENR-D','Computer Science',id,1 FROM faculties WHERE faculty_code='ENR-F';
INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) SELECT 'ENR-P','BSc Computer Science','Bachelor',id,1 FROM departments WHERE department_code='ENR-D';
INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) SELECT 'จอห์น','Dr. John Smith',f.id,d.id,1 FROM faculties f, departments d WHERE f.faculty_code='ENR-F' AND d.department_code='ENR-D';
INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาค 2 (ตรวจ)','Semester 2',1);
INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2025,'ภาค 1 (ตรวจ)','Semester 1',1);
INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,created_by)
  SELECT v.code,v.name,3,v.descr,f.id,d.id,300,1 FROM faculties f, departments d,
  (VALUES ('CS301','Data Structures','Lists, trees and graphs.'),('CS470','Machine Learning','Supervised and unsupervised learning.'),
          ('CS480','Cloud Computing','Deploying services on the cloud.'),('CS210','Discrete Math','Logic, sets and proofs.')) v(code,name,descr)
  WHERE f.faculty_code='ENR-F' AND d.department_code='ENR-D';
INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
  SELECT c.id,v.sec,v.cap,l.id,s.id,'ACTIVE',1 FROM courses c, lectures l, semesters s,
  (VALUES ('CS301','SEC-01',40,'ภาค 2 (ตรวจ)'),('CS470','SEC-01',30,'ภาค 2 (ตรวจ)'),('CS470','SEC-02',30,'ภาค 2 (ตรวจ)'),
          ('CS480','SEC-01',1,'ภาค 2 (ตรวจ)'),('CS210','SEC-01',40,'ภาค 1 (ตรวจ)')) v(code,sec,cap,sem)
  WHERE c.course_code=v.code AND l.lecture_name_en='Dr. John Smith' AND s.semester_name_th=v.sem;
INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,academic_year,study_year)
  SELECT v.code,v.email,v.first,'Check','ACTIVE',f.id,d.id,p.id,s.id,2026,3 FROM faculties f, departments d, programs p, semesters s,
  (VALUES ('ENR-001','enroll-check-001@rsu.ac.th','Mali'),('ENR-002','enroll-check-002@rsu.ac.th','Somchai')) v(code,email,first)
  WHERE f.faculty_code='ENR-F' AND d.department_code='ENR-D' AND p.program_code='ENR-P' AND s.semester_name_th='ภาค 2 (ตรวจ)';
INSERT INTO student_credentials(student_id,password_hash,force_password_change) SELECT id,'$2a$10$dgi.5Y7lkeTi7nSQPTrxNOXIU82NUr6p6vb6PV.560VGXDexmfEF2',FALSE FROM students WHERE student_code LIKE 'ENR-%';
-- ENR-001: active in CS301, finished CS210 last year. ENR-002 holds the only seat of CS480.
INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,created_by)
  SELECT st.id,cs.id,DATE '2026-08-15','ACTIVE',1 FROM students st, course_sections cs JOIN courses c ON c.id=cs.course_id
  WHERE (st.student_code='ENR-001' AND c.course_code IN ('CS301','CS210')) OR (st.student_code='ENR-002' AND c.course_code='CS480');
COMMIT;
