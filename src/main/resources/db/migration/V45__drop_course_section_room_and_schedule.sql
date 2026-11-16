-- Spec 40: rooms and class times live on the timetable (course_section_meetings); the legacy free-text
-- room and schedule columns of a course section are no longer read or written by any API.
ALTER TABLE course_sections DROP COLUMN IF EXISTS room;
ALTER TABLE course_sections DROP COLUMN IF EXISTS schedule;
