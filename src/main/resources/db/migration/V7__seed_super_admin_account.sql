DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_users
        WHERE email = 'smartairsu@rsu.ac.th'
          AND employee_id <> 'RSU-SUPER-ADMIN'
    ) OR EXISTS (
        SELECT 1
        FROM app_users
        WHERE employee_id = 'RSU-SUPER-ADMIN'
          AND email <> 'smartairsu@rsu.ac.th'
    ) THEN
        RAISE EXCEPTION 'The Super Admin email or employee ID belongs to another account';
    END IF;
END
$$;

INSERT INTO app_users (
    employee_id,
    first_name,
    last_name,
    email,
    department_id,
    account_status,
    is_deleted,
    created_at
)
VALUES (
    'RSU-SUPER-ADMIN',
    'Smart AI',
    'Super Admin',
    'smartairsu@rsu.ac.th',
    NULL,
    'ACTIVE',
    FALSE,
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO appuser_credentials (
    user_id,
    password_hash,
    force_password_change,
    failed_login_attempts,
    created_at
)
SELECT
    id,
    '$2a$12$kyhAjias0zJ4ZClCiCkgiOj0BXAI9djZvlvIlpjLt6MmDIIsXSoNG',
    TRUE,
    0,
    CURRENT_TIMESTAMP
FROM app_users
WHERE email = 'smartairsu@rsu.ac.th'
  AND employee_id = 'RSU-SUPER-ADMIN'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO app_user_roles (
    user_id,
    role_id,
    assigned_at
)
SELECT
    users.id,
    roles.id,
    CURRENT_TIMESTAMP
FROM app_users users
JOIN app_roles roles ON roles.role_code = 'SUPER_ADMIN'
WHERE users.email = 'smartairsu@rsu.ac.th'
  AND users.employee_id = 'RSU-SUPER-ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
