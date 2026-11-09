-- Spec file 42, section 14 indexes, adapted to the extended table (V30 already has idx_course_section_meetings_section).
CREATE INDEX idx_meeting_room_day_time
ON course_section_meetings(
    room_id,
    day_of_week,
    start_time,
    end_time
)
WHERE is_deleted = FALSE
  AND status = 'PUBLISHED';

CREATE INDEX idx_meeting_day_time
ON course_section_meetings(
    day_of_week,
    start_time
)
WHERE is_deleted = FALSE
  AND status = 'PUBLISHED';

CREATE INDEX idx_meeting_status
ON course_section_meetings(status)
WHERE is_deleted = FALSE;

-- Occurrence generation and conflict checks read a meeting's live exceptions, and reschedules by their new date.
CREATE INDEX idx_timetable_exceptions_meeting
ON timetable_meeting_exceptions(meeting_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_timetable_exceptions_replacement_date
ON timetable_meeting_exceptions(replacement_date)
WHERE is_deleted = FALSE
  AND exception_type = 'RESCHEDULED';
