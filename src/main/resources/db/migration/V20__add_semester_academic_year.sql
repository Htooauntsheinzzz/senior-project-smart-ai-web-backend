ALTER TABLE semesters ADD COLUMN academic_year INTEGER;

-- Semesters have no date fields, so existing rows take the year they were created.
UPDATE semesters SET academic_year = EXTRACT(YEAR FROM created_at)::INTEGER WHERE academic_year IS NULL;

ALTER TABLE semesters ALTER COLUMN academic_year SET NOT NULL;
ALTER TABLE semesters ADD CONSTRAINT ck_semesters_academic_year
    CHECK (academic_year BETWEEN 2000 AND 2100);

-- The same Thai/English names repeat every academic year.
ALTER TABLE semesters DROP CONSTRAINT uk_semesters_name_th_en;
ALTER TABLE semesters ADD CONSTRAINT uk_semesters_year_name_th_en
    UNIQUE (academic_year, semester_name_th, semester_name_en);

CREATE INDEX idx_semesters_academic_year ON semesters (academic_year);
