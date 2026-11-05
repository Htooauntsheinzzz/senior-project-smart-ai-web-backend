-- Year 1, Year 2, ... Separate from students.academic_year (for example 2026); set only by an Admin.
ALTER TABLE students
ADD COLUMN study_year SMALLINT;

ALTER TABLE students
ADD CONSTRAINT chk_students_study_year
CHECK (
    study_year IS NULL
    OR study_year BETWEEN 1 AND 10
);
