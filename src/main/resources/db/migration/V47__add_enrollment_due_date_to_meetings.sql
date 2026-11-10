-- Spec file 44: the last day a Student or an Admin may enroll into the section, set while scheduling its timetable.
-- Nullable: existing meetings have no verified deadline, and none is invented. It may not be after the first class day.
ALTER TABLE course_section_meetings
ADD COLUMN enrollment_due_date DATE;

ALTER TABLE course_section_meetings
ADD CONSTRAINT chk_meeting_enrollment_due_date
CHECK (
    enrollment_due_date IS NULL
    OR enrollment_due_date <= effective_from
);
