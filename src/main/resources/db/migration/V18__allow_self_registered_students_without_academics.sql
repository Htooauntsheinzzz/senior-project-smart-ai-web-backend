-- Self-registered students create their account first and choose faculty, department and program later.
ALTER TABLE students ALTER COLUMN faculty_id DROP NOT NULL;
ALTER TABLE students ALTER COLUMN department_id DROP NOT NULL;
ALTER TABLE students ALTER COLUMN program_id DROP NOT NULL;
