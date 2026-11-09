-- Spec file 42, sections 12 to 14. course_section_meetings already exists (V30), so it is extended, not recreated.
-- V30 left room_id without a foreign key because no rooms table existed; campus_rooms (V34) exists now.

-- Defensive: the V30 service refused every non-null roomId, so no row should reference a room. Any value that does not
-- name a campus room is unverified and is cleared before the foreign key is added.
UPDATE course_section_meetings m
SET room_id = NULL
WHERE room_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM campus_rooms r WHERE r.id = m.room_id);

ALTER TABLE course_section_meetings
ADD CONSTRAINT fk_course_section_meetings_room
FOREIGN KEY (room_id) REFERENCES campus_rooms(id);

ALTER TABLE course_section_meetings
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

-- Legacy rows: the V30 flag is_active meant "visible to students", which is what PUBLISHED means now. Inactive rows
-- become drafts. is_active is kept for compatibility and is maintained by the service as (status = 'PUBLISHED').
UPDATE course_section_meetings
SET status = CASE WHEN is_active THEN 'PUBLISHED' ELSE 'DRAFT' END;

ALTER TABLE course_section_meetings
ADD COLUMN notes TEXT;

ALTER TABLE course_section_meetings
ADD COLUMN created_by BIGINT REFERENCES app_users(id);

ALTER TABLE course_section_meetings
ADD COLUMN updated_by BIGINT REFERENCES app_users(id);

ALTER TABLE course_section_meetings
ADD CONSTRAINT chk_course_section_meetings_status
CHECK (
    status IN (
        'DRAFT',
        'PUBLISHED',
        'CANCELLED'
    )
);

-- A published meeting must have a verified campus room. NOT VALID: legacy published rows that only carry room_label
-- stay visible to students until an Admin assigns a room; every new row and every later edit of a legacy row must
-- comply. A soft-deleted row is exempt so legacy rows can still be removed.
ALTER TABLE course_section_meetings
ADD CONSTRAINT chk_published_meeting_room
CHECK (
    is_deleted = TRUE
    OR status <> 'PUBLISHED'
    OR room_id IS NOT NULL
) NOT VALID;

COMMENT ON COLUMN course_section_meetings.room_label IS
    'Legacy, unverified location text from before campus rooms existed. room_id is the authoritative location.';
COMMENT ON COLUMN course_section_meetings.is_active IS
    'Legacy flag kept for compatibility; always equal to (status = PUBLISHED). status is authoritative.';
