-- Spec file 60: an Admin web user can register without a Super Admin (registration form or Google sign-in).
-- Only facts about the user itself are added to app_users. A Google link and a pending email verification are
-- separate facts with their own keys, so they live in their own tables (V58, V59).

-- A self-registered user has no employee ID until a Super Admin enters it. The unique constraint stays:
-- PostgreSQL allows many NULLs in a unique column, and two users still cannot share one employee ID.
ALTER TABLE app_users
    ALTER COLUMN employee_id DROP NOT NULL;

-- How the account came into the system. Existing rows were all created by a Super Admin (or seeded).
ALTER TABLE app_users
    ADD COLUMN registration_source VARCHAR(20) NOT NULL DEFAULT 'ADMIN_CREATED';

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_registration_source
        CHECK (registration_source IN ('ADMIN_CREATED', 'SELF_FORM', 'GOOGLE'));

-- When the owner proved the email address (verification link, or a Google sign-in with a verified email).
-- NULL: never proven, which is the case for every account a Super Admin created by hand.
ALTER TABLE app_users
    ADD COLUMN email_verified_at TIMESTAMP;

-- account_status gains the value PENDING_VERIFICATION (a form registration whose email is not verified yet).
-- The column never had a check constraint; the application validates the values, as before.
