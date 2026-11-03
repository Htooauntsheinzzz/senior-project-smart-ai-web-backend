-- Step 6 (spec 31) assistant test data for the LOCAL development database only.
-- Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Already applied to the local dev database on 2026-10-20. To apply again on a fresh database:
--   docker exec -i smart-university-postgres-1 psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" < docker/dev-fixtures/step6-assistant-test-data.sql
--
-- Test accounts (password for both: StepSix-Test-2026!)
--   std-test-001@rsu.ac.th  STD-TEST-001  ACTIVE in CS301 SEC-01 (Dr. John Smith, 11-402, Tuesday 09:00-11:45)
--                                          and CSC452 SEC-02 (Dr. Jane Lee, 5-201, Friday 09:00-11:45)
--   std-test-002@rsu.ac.th  STD-TEST-002  ACTIVE in CSC452 SEC-02 and CSC453 SEC-01 (no room/schedule),
--                                          so "my Programming course" is ambiguous for this student only.
-- Faculty IT-TEST "Information Technology", department/program CS-TEST "Computer Science",
-- semester "Semester 1" 2026 (Thai name ends with "(ทดสอบ)"). created_by = admin user 1.
--
-- Cleanup (removes only this data):
--   DELETE FROM enrollments WHERE student_id IN (SELECT id FROM students WHERE student_code IN ('STD-TEST-001','STD-TEST-002'));
--   DELETE FROM student_credentials WHERE student_id IN (SELECT id FROM students WHERE student_code IN ('STD-TEST-001','STD-TEST-002'));
--   DELETE FROM students WHERE student_code IN ('STD-TEST-001','STD-TEST-002');
--   DELETE FROM course_sections WHERE semester_id IN (SELECT id FROM semesters WHERE semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)');
--   DELETE FROM courses WHERE department_id IN (SELECT id FROM departments WHERE department_code='CS-TEST');
--   DELETE FROM lectures WHERE department_id IN (SELECT id FROM departments WHERE department_code='CS-TEST');
--   DELETE FROM semesters WHERE semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)';
--   DELETE FROM programs WHERE program_code='CS-TEST';
--   DELETE FROM departments WHERE department_code='CS-TEST';
--   DELETE FROM faculties WHERE faculty_code='IT-TEST';

BEGIN;
WITH f AS (INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('IT-TEST','Information Technology','เทคโนโลยีสารสนเทศ',1) RETURNING id),
d AS (INSERT INTO departments(department_code,department_name,faculty_id,created_by) SELECT 'CS-TEST','Computer Science',id,1 FROM f RETURNING id, faculty_id),
p AS (INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) SELECT 'CS-TEST','Computer Science','Bachelor',id,1 FROM d RETURNING id)
SELECT 1;
INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาคการศึกษาที่ 1 (ทดสอบ)','Semester 1',1);
INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by)
  SELECT n_th, n_en, d.faculty_id, d.id, 1 FROM departments d, (VALUES ('จอห์น สมิธ','Dr. John Smith'),('เจน ลี','Dr. Jane Lee')) v(n_th,n_en) WHERE d.department_code='CS-TEST';
INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,prerequisite_courses,created_by)
  SELECT c, n, 3, dsc, d.faculty_id, d.id, 300, pre, 1 FROM departments d,
  (VALUES ('CS301','Data Structures','Linear and non-linear data structures and their algorithms.','CS201'),
          ('CSC452','Mobile Programming','Building mobile applications.',NULL),
          ('CSC453','Web Programming','Building web applications.',NULL)) v(c,n,dsc,pre) WHERE d.department_code='CS-TEST';
INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,room,schedule,semester_id,status,created_by)
  SELECT c.id, v.sec, 40, l.id, v.room, v.sched, s.id, 'ACTIVE', 1
  FROM (VALUES ('CS301','SEC-01','Dr. John Smith','11-402','Tuesday 09:00-11:45'),
               ('CSC452','SEC-02','Dr. Jane Lee','5-201','Friday 09:00-11:45'),
               ('CSC453','SEC-01','Dr. Jane Lee',NULL,NULL)) v(code,sec,lect,room,sched)
  JOIN courses c ON c.course_code=v.code AND NOT c.is_deleted
  JOIN lectures l ON l.lecture_name_en=v.lect
  JOIN departments d ON d.id=l.department_id AND d.department_code='CS-TEST'
  JOIN semesters s ON s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)';
INSERT INTO students(student_code,university_email,first_name,last_name,account_status,faculty_id,department_id,program_id,semester_id,academic_year,enrollment_year)
  SELECT v.code, lower(v.code)||'@rsu.ac.th', v.fn, 'Student', 'ACTIVE', d.faculty_id, d.id, p.id, s.id, 2026, 2026
  FROM (VALUES ('STD-TEST-001','Test One'),('STD-TEST-002','Test Two')) v(code,fn)
  JOIN departments d ON d.department_code='CS-TEST' JOIN programs p ON p.program_code='CS-TEST'
  JOIN semesters s ON s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)';
INSERT INTO student_credentials(student_id,password_hash,force_password_change)
  SELECT id, '$2y$10$j4HDcq4bxFUQs1Dz1EkfzuzQ6Wrm/SW0nfJ9YIdbkdsS6Jx/C4yAG', FALSE FROM students WHERE student_code IN ('STD-TEST-001','STD-TEST-002');
INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
  SELECT st.id, cs.id, DATE '2026-08-15', 'ACTIVE', FALSE, 1
  FROM (VALUES ('STD-TEST-001','CS301'),('STD-TEST-001','CSC452'),('STD-TEST-002','CSC452'),('STD-TEST-002','CSC453')) v(stu,code)
  JOIN students st ON st.student_code=v.stu
  JOIN courses c ON c.course_code=v.code AND NOT c.is_deleted
  JOIN course_sections cs ON cs.course_id=c.id
  JOIN semesters s ON s.id=cs.semester_id AND s.semester_name_th='ภาคการศึกษาที่ 1 (ทดสอบ)';
COMMIT;
