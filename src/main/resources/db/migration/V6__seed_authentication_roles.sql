INSERT INTO app_roles (
    role_code,
    role_name,
    description,
    is_active,
    created_at
)
VALUES
    (
        'SUPER_ADMIN',
        'Super Admin',
        'Full access to all administrative modules and system configuration.',
        TRUE,
        CURRENT_TIMESTAMP
    ),
    (
        'ADMIN',
        'Admin',
        'General administrative access.',
        TRUE,
        CURRENT_TIMESTAMP
    ),
    (
        'ACADEMIC_ADMIN',
        'Academic Admin',
        'Administrative access to academic management features.',
        TRUE,
        CURRENT_TIMESTAMP
    );
