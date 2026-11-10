-- Enrollment limited to the published timetable, with a due date (spec file 44) — LOCAL development database only.
-- Not a Flyway migration: never copy this into src/main/resources/db/migration.
-- Needs V47 and step14-student-self-enrollment-test-data.sql applied first (accounts ENR-001 / ENR-002).
-- Apply:
--   docker exec -i smcheck-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step15-enrollment-timetable-test-data.sql
--
-- What ENR-001 (enroll-check-001@rsu.ac.th) sees in GET /api/v1/student/enrollments/available-sections afterwards:
--   CS301 SEC-01  on the timetable, no due date          -> already enrolled
--   CS470 SEC-01  on the timetable, due date 2099-12-31  -> can enroll
--   CS470 SEC-02  on the timetable, due date 2026-01-10  -> blocked, ENROLLMENT_CLOSED
--   CS480 SEC-01  on the timetable, no due date, 1/1     -> blocked, SECTION_FULL
--   CS490 SEC-01  NOT on the timetable (draft meeting)   -> not listed; enrolling gives COURSE_SECTION_NOT_IN_TIMETABLE
-- The 2099 dates are test values only, so the checks do not depend on the day they are run.
--
-- Cleanup (run before the step14 cleanup):
--   DELETE FROM course_section_meetings WHERE course_section_id IN (SELECT id FROM course_sections WHERE semester_id IN (SELECT id FROM semesters WHERE semester_name_th LIKE '%(ตรวจ)'));
--   UPDATE course_sections SET academic_term_id=NULL WHERE academic_term_id IN (SELECT id FROM academic_terms WHERE academic_year_code='ENR-2569');
--   DELETE FROM academic_terms WHERE academic_year_code='ENR-2569';
--   DELETE FROM campus_rooms WHERE building_id IN (SELECT id FROM campus_buildings WHERE building_code='ENR-B1');
--   DELETE FROM campus_buildings WHERE building_code='ENR-B1';
--   DELETE FROM campus_sites WHERE site_code='ENR-SITE';

BEGIN;
INSERT INTO campus_sites(site_code,site_name) VALUES ('ENR-SITE','Enrollment Check Campus');
INSERT INTO campus_buildings(site_id,building_code,building_name,total_floors) SELECT id,'ENR-B1','Enrollment Check Building',5 FROM campus_sites WHERE site_code='ENR-SITE';
INSERT INTO campus_rooms(building_id,room_number,floor_number,room_type)
  SELECT b.id,v.num,4,'CLASSROOM' FROM campus_buildings b, (VALUES ('E-401'),('E-402'),('E-403'),('E-404'),('E-405')) v(num) WHERE b.building_code='ENR-B1';
INSERT INTO academic_terms(semester_id,academic_year_code,academic_year_label,start_date,end_date,status,created_by)
  SELECT id,'ENR-2569','2569 (check)',DATE '2026-01-05',DATE '2099-12-31','ACTIVE',1 FROM semesters WHERE semester_name_th='ภาค 2 (ตรวจ)';
-- A fifth course whose section only has a DRAFT meeting, so it is not on the published timetable.
INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,created_by)
  SELECT 'CS490','Capstone Project',3,'Not scheduled yet.',f.id,d.id,300,1 FROM faculties f, departments d WHERE f.faculty_code='ENR-F' AND d.department_code='ENR-D';
INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
  SELECT c.id,'SEC-01',30,l.id,s.id,'ACTIVE',1 FROM courses c, lectures l, semesters s
  WHERE c.course_code='CS490' AND c.department_id=(SELECT id FROM departments WHERE department_code='ENR-D')
    AND l.department_id=c.department_id AND s.semester_name_th='ภาค 2 (ตรวจ)';
UPDATE course_sections SET academic_term_id=(SELECT id FROM academic_terms WHERE academic_year_code='ENR-2569')
  WHERE semester_id=(SELECT id FROM semesters WHERE semester_name_th='ภาค 2 (ตรวจ)');
INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,
    enrollment_due_date,room_id,status,is_active,created_by)
  SELECT cs.id,'LECTURE',v.day,v.st::time,v.en::time,v.ef::date,DATE '2099-12-31',v.due::date,r.id,v.status,v.status='PUBLISHED',1
  FROM (VALUES ('CS301','SEC-01',1,'09:00','12:00','2026-01-05',NULL,'E-401','PUBLISHED'),
               ('CS470','SEC-01',2,'09:00','12:00','2099-12-31','2099-12-31','E-402','PUBLISHED'),
               ('CS470','SEC-02',3,'09:00','12:00','2026-01-12','2026-01-10','E-403','PUBLISHED'),
               ('CS480','SEC-01',4,'09:00','12:00','2026-01-05',NULL,'E-404','PUBLISHED'),
               ('CS490','SEC-01',5,'09:00','12:00','2026-01-05',NULL,'E-405','DRAFT')) v(code,sec,day,st,en,ef,due,room,status)
  JOIN courses c ON c.course_code=v.code
  JOIN course_sections cs ON cs.course_id=c.id AND cs.section_number=v.sec
  JOIN semesters s ON s.id=cs.semester_id AND s.semester_name_th='ภาค 2 (ตรวจ)'
  JOIN campus_rooms r ON r.room_number=v.room
  JOIN campus_buildings b ON b.id=r.building_id AND b.building_code='ENR-B1';
COMMIT;
