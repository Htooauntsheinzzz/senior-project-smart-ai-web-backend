-- Spec file 42, section 10. Nullable on purpose: existing sections have no verified term, and no academic year is
-- backfilled. The NOT NULL constraint and the (course_id, section_number, academic_term_id) uniqueness belong to a
-- later migration, after every section has been reconciled (section 11). Section ids stay unchanged.
ALTER TABLE course_sections
ADD COLUMN academic_term_id BIGINT;

ALTER TABLE course_sections
ADD CONSTRAINT fk_course_sections_academic_term
FOREIGN KEY (academic_term_id)
REFERENCES academic_terms(id);

CREATE INDEX idx_course_sections_academic_term
ON course_sections(academic_term_id);
