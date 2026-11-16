-- Student self-enrollment (spec file 43): created_by references app_users, and a Student is not an app user.
-- A NULL created_by therefore means the Student created the Enrollment from the mobile application.
ALTER TABLE enrollments ALTER COLUMN created_by DROP NOT NULL;
