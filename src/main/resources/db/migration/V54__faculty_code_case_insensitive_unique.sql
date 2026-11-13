-- Faculty codes may now contain lowercase letters (e.g. "Engineering-Technology").
-- Uniqueness becomes case-insensitive so "FAC-ENG" and "fac-eng" cannot both exist.
-- The index keeps the old constraint name so duplicate errors still map to FACULTY_CODE_ALREADY_EXISTS.
ALTER TABLE faculties DROP CONSTRAINT uk_faculties_faculty_code;
CREATE UNIQUE INDEX uk_faculties_faculty_code ON faculties (LOWER(faculty_code));
