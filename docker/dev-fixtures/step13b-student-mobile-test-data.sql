-- Student mobile academic data (spec file 40) test data for the LOCAL development database only, on top of
-- step6-assistant-test-data.sql. Not a Flyway migration: never copy this into src/main/resources/db/migration
-- Apply:
--   docker exec -i smart-university-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/dev-fixtures/step13b-student-mobile-test-data.sql
--
-- The grades below are TEST VALUES, not university policy. Scheme code DEV-TEST-ONLY must never reach production.
-- Test account: std-test-001@rsu.ac.th (password in step6-assistant-test-data.sql).
-- Everything is dated relative to the day this file runs.
--
-- Cleanup (removes only this data):
--   DELETE FROM student_course_result_audits WHERE student_course_result_id IN (SELECT r.id FROM student_course_results r JOIN grade_definitions g ON g.id=r.grade_definition_id JOIN grading_schemes s ON s.id=g.grading_scheme_id WHERE s.scheme_code='DEV-TEST-ONLY');
--   DELETE FROM student_course_results WHERE grade_definition_id IN (SELECT g.id FROM grade_definitions g JOIN grading_schemes s ON s.id=g.grading_scheme_id WHERE s.scheme_code='DEV-TEST-ONLY');
--   DELETE FROM grade_definitions WHERE grading_scheme_id=(SELECT id FROM grading_schemes WHERE scheme_code='DEV-TEST-ONLY');
--   DELETE FROM grading_schemes WHERE scheme_code='DEV-TEST-ONLY';
--   DELETE FROM course_section_meetings WHERE room_label LIKE 'DEV %';
--   DELETE FROM course_assignments WHERE title LIKE 'DEV %';
--   DELETE FROM course_exams WHERE title LIKE 'DEV %';
--   UPDATE students SET study_year=NULL WHERE student_code='STD-TEST-001';

BEGIN;
UPDATE students SET study_year=3 WHERE student_code='STD-TEST-001';

INSERT INTO grading_schemes(scheme_code,scheme_name,description)
  VALUES ('DEV-TEST-ONLY','Development test scheme','Test values for Postman checks, not university policy');
INSERT INTO grade_definitions(grading_scheme_id,grade_code,grade_point,counts_in_gpa,earns_credit,is_passing)
  SELECT s.id, v.code, v.point, v.gpa, v.earns, v.pass
  FROM grading_schemes s, (VALUES ('A',4.00,TRUE,TRUE,TRUE),('B',3.00,TRUE,TRUE,TRUE),('F',0.00,TRUE,FALSE,FALSE),
                                  ('P',NULL,FALSE,TRUE,TRUE),('I',NULL,FALSE,FALSE,FALSE))
       AS v(code,point,gpa,earns,pass)
  WHERE s.scheme_code='DEV-TEST-ONLY';

-- Every section STD-TEST-001 is actively enrolled in gets a lecture tomorrow and a lab the day after,
-- at a different hour per section so nothing overlaps.
INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,room_label)
  SELECT e.course_section_id, m.kind,
         EXTRACT(ISODOW FROM CURRENT_DATE + m.days)::smallint,
         make_time(8 + (row_number() OVER (PARTITION BY m.kind ORDER BY e.id))::int, 0, 0),
         make_time(8 + (row_number() OVER (PARTITION BY m.kind ORDER BY e.id))::int, 45, 0),
         CURRENT_DATE - 30, CURRENT_DATE + 120, 'DEV ' || m.kind || ' room'
  FROM enrollments e JOIN students s ON s.id=e.student_id,
       (VALUES ('LECTURE',1),('LAB',2)) AS m(kind,days)
  WHERE s.student_code='STD-TEST-001' AND e.status='ACTIVE' AND e.is_deleted=FALSE;

INSERT INTO course_assignments(course_section_id,title,description,due_at,status,created_by)
  SELECT e.course_section_id, 'DEV ' || d.title, 'Postman check', d.due, d.status, e.created_by
  FROM (SELECT e.* FROM enrollments e JOIN students s ON s.id=e.student_id
        WHERE s.student_code='STD-TEST-001' AND e.status='ACTIVE' AND e.is_deleted=FALSE ORDER BY e.id LIMIT 1) e,
       (VALUES ('due tonight (must show, counts as due today)',
                  (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date + time '23:59' , 'PUBLISHED'),
               ('due in 5 days (must show)', (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date + 5 + time '12:00', 'PUBLISHED'),
               ('draft (must stay hidden)', (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date + 2 + time '12:00', 'DRAFT'))
       AS d0(title,local_due,status)
       CROSS JOIN LATERAL (SELECT d0.title, d0.status, d0.local_due AT TIME ZONE 'Asia/Bangkok' AS due) d;

INSERT INTO course_exams(course_section_id,title,exam_type,starts_at,ends_at,status,room_label,created_by)
  SELECT e.course_section_id, 'DEV ' || d.title, 'MIDTERM', d.starts, d.starts + interval '2 hours', d.status, 'DEV exam room', e.created_by
  FROM (SELECT e.* FROM enrollments e JOIN students s ON s.id=e.student_id
        WHERE s.student_code='STD-TEST-001' AND e.status='ACTIVE' AND e.is_deleted=FALSE ORDER BY e.id LIMIT 1) e,
       (VALUES ('midterm in 3 days (must show)', 3, 'SCHEDULED'), ('cancelled exam (must stay hidden)', 4, 'CANCELLED')) AS d0(title,days,status)
       CROSS JOIN LATERAL (SELECT d0.title, d0.status,
           ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Bangkok')::date + d0.days + time '09:00') AT TIME ZONE 'Asia/Bangkok' AS starts) d;
COMMIT;
