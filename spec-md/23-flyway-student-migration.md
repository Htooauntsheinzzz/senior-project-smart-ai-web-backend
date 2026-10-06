# 24 - Student Feature Flyway Migration Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the revised Flyway migration for the:

```text
Student Management
```

feature.

This version follows the current Admin Student UI and stores the Student's academic classification directly using foreign keys for:

```text
Faculty
Department
Program / Major
Semester
```

The Student feature must support future account creation from:

```text
Admin Web
    ↓
Admin creates Student account

Mobile App
    ↓
Student self-registers
```

This task is **Flyway migration only**.

Do NOT implement yet:

- Student Entity
- Student Repository
- Student DTOs
- Student Mapper
- Student Service
- Student Controller
- Student Admin CRUD API
- Mobile Registration API
- Student Login API
- Redis authentication sessions
- Enrollment

---

# 2. Required Tables

Create:

```text
students

student_credentials
```

Relationship:

```text
students
    1
    │
    │
    1
student_credentials
```

The Student academic/profile data and Student authentication credentials must remain separated.

---

# 3. Mandatory Agent Instructions

Before creating the migrations:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the shared Flyway migration standards.
5. Inspect all existing Flyway migrations.
6. Verify `app_users` exists.
7. Verify `faculties` exists.
8. Verify `departments` exists.
9. Verify `programs` exists.
10. Verify `semesters` exists.
11. Determine the next available Flyway version.
12. Do NOT modify previously applied migrations.
13. Create the Student migrations using the next available versions.
14. Run Flyway.
15. Verify PostgreSQL schema.
16. Verify `flyway_schema_history`.

---

# 4. Current Add Student Form

The Admin Add Student form contains:

```text
Personal Information
├── Student ID *
├── University Email *
├── First Name *
├── Last Name *
├── Phone Number
└── Date of Birth

Academic Information
├── Faculty *
├── Department *
├── Major / Program *
├── Academic Year
├── Current Semester
└── Enrollment Year

Mobile App Account
├── Account Status
├── Temporary Password *
├── Confirm Password *
└── Require password change on first login
```

---

# 5. Final Academic Design

The `students` table MUST directly store:

```text
faculty_id
department_id
program_id
semester_id
academic_year
enrollment_year
```

Relationships:

```text
students.faculty_id
→ faculties.id

students.department_id
→ departments.id

students.program_id
→ programs.id

students.semester_id
→ semesters.id
```

---

# 6. Academic Relationship Consistency

Because Faculty, Department, and Program are all stored, the application must prevent inconsistent combinations.

Expected hierarchy:

```text
Faculty
    ↓
Department
    ↓
Program
    ↓
Student
```

The future Student service MUST verify:

```text
department.faculty_id == student.faculty_id
```

and:

```text
program.department_id == student.department_id
```

Example valid:

```text
Faculty:
Information Technology

Department:
Software Engineering

Program:
Mobile Development
```

Example invalid:

```text
Faculty:
Engineering

Department:
Marketing

Program:
Computer Engineering
```

The database foreign keys verify that each ID exists.

The service layer verifies that the relationships belong together.

---

# 7. Students Table

Use exactly:

```text
students
```

---

# 8. Students Table Attributes

| Column             | Type         | Constraint                         | Description                          |
| ------------------ | ------------ | ---------------------------------- | ------------------------------------ |
| `id`               | BIGINT       | PK, Identity                       | Internal Student ID                  |
| `student_code`     | VARCHAR(50)  | UNIQUE, NOT NULL                   | Visible Student ID                   |
| `university_email` | VARCHAR(255) | UNIQUE, NOT NULL                   | University email                     |
| `first_name`       | VARCHAR(100) | NOT NULL                           | First name                           |
| `last_name`        | VARCHAR(100) | NOT NULL                           | Last name                            |
| `phone_number`     | VARCHAR(30)  | NULL                               | Phone number                         |
| `date_of_birth`    | DATE         | NULL                               | Date of birth                        |
| `faculty_id`       | BIGINT       | FK → `faculties.id`, NOT NULL      | Student Faculty                      |
| `department_id`    | BIGINT       | FK → `departments.id`, NOT NULL    | Student Department                   |
| `program_id`       | BIGINT       | FK → `programs.id`, NOT NULL       | Student Major / Program              |
| `semester_id`      | BIGINT       | FK → `semesters.id`, NULL          | Current Semester                     |
| `academic_year`    | SMALLINT     | NULL                               | Academic/school year such as `2026`  |
| `enrollment_year`  | SMALLINT     | NULL                               | Year Student enrolled such as `2024` |
| `account_status`   | VARCHAR(30)  | NOT NULL                           | Account state                        |
| `is_deleted`       | BOOLEAN      | NOT NULL DEFAULT FALSE             | Soft delete                          |
| `created_by`       | BIGINT       | FK → `app_users.id`, NULL          | Admin creator                        |
| `created_at`       | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP | Created timestamp                    |
| `updated_by`       | BIGINT       | FK → `app_users.id`, NULL          | Last Admin updater                   |
| `updated_at`       | TIMESTAMP    | NULL                               | Updated timestamp                    |

---

# 9. Final Students Structure

```text
students
─────────────────────────────────────
id                  BIGINT PK

student_code        VARCHAR(50)
university_email    VARCHAR(255)

first_name          VARCHAR(100)
last_name           VARCHAR(100)
phone_number        VARCHAR(30)
date_of_birth       DATE

faculty_id          BIGINT FK
department_id       BIGINT FK
program_id          BIGINT FK
semester_id         BIGINT FK

academic_year       SMALLINT
enrollment_year     SMALLINT

account_status      VARCHAR(30)
is_deleted          BOOLEAN

created_by          BIGINT FK NULL
created_at          TIMESTAMP
updated_by          BIGINT FK NULL
updated_at          TIMESTAMP
```

---

# 10. Primary Key

Use:

```sql
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
```

Do NOT use:

```text
student_code as primary key
university_email as primary key
UUID
```

Example:

```text
id = 15
student_code = STD-2024-0001
```

---

# 11. Student Code

UI:

```text
Student ID
```

Database:

```text
student_code
```

Type:

```text
VARCHAR(50)
```

Constraints:

```text
NOT NULL
UNIQUE
```

Examples:

```text
STD-2024-0001
STD-2024-0002
STD-2023-0147
```

---

# 12. Internal ID vs Student ID

Database identity:

```text
students.id = 15
```

University Student ID:

```text
students.student_code = STD-2024-0001
```

Future Enrollment must store:

```text
student_id = students.id
```

not the Student Code string.

---

# 13. University Email

Use:

```text
university_email VARCHAR(255) NOT NULL
```

Add UNIQUE constraint.

Example:

```text
thanapon.s@rsu.ac.th
```

Future service should normalize:

```text
trim
lowercase
```

before storing/checking duplicates.

---

# 14. Student Name

Use:

```text
first_name VARCHAR(100) NOT NULL

last_name VARCHAR(100) NOT NULL
```

Do NOT combine them into one `full_name` column.

Full name should be assembled by the application when needed.

---

# 15. Phone Number

Use:

```text
phone_number VARCHAR(30)
```

Nullable.

Do NOT store phone numbers as numeric database types.

---

# 16. Date of Birth

Use:

```text
date_of_birth DATE
```

Nullable.

Do NOT store Date of Birth as:

```text
VARCHAR
TIMESTAMP
```

---

# 17. Faculty Foreign Key

Column:

```text
faculty_id
```

Type:

```text
BIGINT
```

Constraint:

```text
NOT NULL
```

Relationship:

```text
students.faculty_id
        ↓
faculties.id
```

Foreign key:

```sql
CONSTRAINT fk_students_faculty
    FOREIGN KEY (faculty_id)
    REFERENCES faculties(id)
```

---

# 18. Department Foreign Key

Column:

```text
department_id
```

Type:

```text
BIGINT
```

Constraint:

```text
NOT NULL
```

Relationship:

```text
students.department_id
        ↓
departments.id
```

Foreign key:

```sql
CONSTRAINT fk_students_department
    FOREIGN KEY (department_id)
    REFERENCES departments(id)
```

---

# 19. Program / Major Foreign Key

Column:

```text
program_id
```

Type:

```text
BIGINT
```

Constraint:

```text
NOT NULL
```

Relationship:

```text
students.program_id
        ↓
programs.id
```

Foreign key:

```sql
CONSTRAINT fk_students_program
    FOREIGN KEY (program_id)
    REFERENCES programs(id)
```

---

# 20. Semester Foreign Key

UI:

```text
Current Semester
```

Use:

```text
semester_id
```

Type:

```text
BIGINT
```

Nullable:

```text
YES
```

Relationship:

```text
students.semester_id
        ↓
semesters.id
```

Foreign key:

```sql
CONSTRAINT fk_students_semester
    FOREIGN KEY (semester_id)
    REFERENCES semesters(id)
```

Do NOT store:

```text
semester_name_en
semester_name_th
```

inside `students`.

---

# 21. Academic Year

`academic_year` represents the current university academic/school year.

Examples:

```text
2025
2026
2027
```

Use:

```text
academic_year SMALLINT
```

Nullable.

Important:

```text
academic_year = 2026
```

NOT:

```text
academic_year = 1
academic_year = 2
academic_year = 3
academic_year = 4
```

---

# 22. Academic Year Constraint

Use:

```sql
CONSTRAINT chk_students_academic_year
CHECK (
    academic_year IS NULL
    OR academic_year BETWEEN 1900 AND 9999
)
```

This prevents:

```text
0
25
-2026
```

while allowing:

```text
2026
2568
```

if Buddhist Era academic-year values are ever required.

---

# 23. Enrollment Year

`enrollment_year` means the year the Student initially enrolled at the university.

Examples:

```text
2022
2023
2024
2025
2026
```

Use:

```text
enrollment_year SMALLINT
```

Nullable.

Example:

```text
academic_year = 2026
enrollment_year = 2024
```

Meaning:

```text
Current academic year:
2026

Student originally joined:
2024
```

---

# 24. Enrollment Year Constraint

Use:

```sql
CONSTRAINT chk_students_enrollment_year
CHECK (
    enrollment_year IS NULL
    OR enrollment_year BETWEEN 1900 AND 9999
)
```

---

# 25. Academic Year vs Enrollment Year

These are different fields.

```text
academic_year
→ Current school/academic year

enrollment_year
→ Year Student joined university
```

Example:

```text
Student:
STD-2024-0001

academic_year:
2026

enrollment_year:
2024
```

Do NOT automatically force these values to be equal.

---

# 26. Account Status

Use:

```text
account_status VARCHAR(30)
```

Supported:

```text
PENDING
ACTIVE
INACTIVE
SUSPENDED
```

Meaning:

```text
PENDING
→ waiting for verification / approval

ACTIVE
→ can use mobile application

INACTIVE
→ account disabled

SUSPENDED
→ account temporarily blocked
```

---

# 27. Account Status Constraint

Use:

```sql
CONSTRAINT chk_students_account_status
CHECK (
    account_status IN (
        'PENDING',
        'ACTIVE',
        'INACTIVE',
        'SUSPENDED'
    )
)
```

Do NOT create a Student Status lookup table for the current feature.

---

# 28. No Database Default for Account Status

Recommended:

```text
account_status VARCHAR(30) NOT NULL
```

without a default.

Reason:

```text
Admin-created Student
→ normally ACTIVE

Mobile self-registration
→ normally PENDING
```

The appropriate service decides the status.

---

# 29. Soft Delete

Use:

```text
is_deleted BOOLEAN NOT NULL DEFAULT FALSE
```

Future Admin delete:

```text
is_deleted = TRUE
```

Do NOT physically delete normal Student records.

---

# 30. created_by

Use:

```text
created_by BIGINT NULL
```

FK:

```text
app_users.id
```

It MUST remain nullable because of future Mobile self-registration.

Admin creation:

```text
created_by = authenticated Admin ID
```

Mobile registration:

```text
created_by = NULL
```

---

# 31. created_at

Use:

```sql
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

---

# 32. updated_by

Use:

```text
updated_by BIGINT NULL
```

FK:

```text
app_users.id
```

Admin update:

```text
updated_by = authenticated Admin ID
```

---

# 33. updated_at

Use:

```text
updated_at TIMESTAMP NULL
```

New Student:

```text
updated_at = NULL
```

---

# 34. Student Credentials Table

Create:

```text
student_credentials
```

This table stores Student password/authentication state only.

Do NOT store passwords in:

```text
students
```

---

# 35. Student Credentials Attributes

| Column                  | Type         | Constraint                           | Description                |
| ----------------------- | ------------ | ------------------------------------ | -------------------------- |
| `id`                    | BIGINT       | PK, Identity                         | Credential ID              |
| `student_id`            | BIGINT       | UNIQUE, FK → `students.id`, NOT NULL | Student                    |
| `password_hash`         | VARCHAR(255) | NOT NULL                             | BCrypt hash                |
| `force_password_change` | BOOLEAN      | NOT NULL DEFAULT FALSE               | First-login password reset |
| `password_changed_at`   | TIMESTAMP    | NULL                                 | Last password change       |
| `failed_login_attempts` | INTEGER      | NOT NULL DEFAULT 0                   | Failed login counter       |
| `locked_until`          | TIMESTAMP    | NULL                                 | Temporary login lock       |
| `created_at`            | TIMESTAMP    | NOT NULL DEFAULT CURRENT_TIMESTAMP   | Created timestamp          |
| `updated_at`            | TIMESTAMP    | NULL                                 | Updated timestamp          |

---

# 36. Final Student Credentials Structure

```text
student_credentials
──────────────────────────────────
id                       BIGINT PK
student_id               BIGINT FK UNIQUE

password_hash            VARCHAR(255)

force_password_change    BOOLEAN
password_changed_at      TIMESTAMP

failed_login_attempts    INTEGER
locked_until             TIMESTAMP

created_at               TIMESTAMP
updated_at               TIMESTAMP
```

---

# 37. Credential Relationship

Use:

```text
student_id
```

Type:

```text
BIGINT
```

Constraints:

```text
NOT NULL
UNIQUE
```

FK:

```sql
CONSTRAINT fk_student_credentials_student
    FOREIGN KEY (student_id)
    REFERENCES students(id)
```

Relationship:

```text
students
    1
    │
    │
    1
student_credentials
```

---

# 38. Password Hash

Use:

```text
password_hash VARCHAR(255) NOT NULL
```

Store:

```text
BCrypt encoded password
```

Never store:

```text
raw password
temporary password
confirm password
```

---

# 39. Temporary Password

Admin Add Student form sends:

```text
temporaryPassword
```

Flow:

```text
Temporary Password
        ↓
Validate
        ↓
BCrypt
        ↓
password_hash
```

Do NOT create:

```text
temporary_password
```

database column.

---

# 40. Confirm Password

`confirmPassword` is request-only.

Use to validate:

```text
temporaryPassword == confirmPassword
```

Do NOT create:

```text
confirm_password
```

database column.

---

# 41. Force Password Change

Use:

```text
force_password_change BOOLEAN NOT NULL DEFAULT FALSE
```

Admin-created accounts can use:

```text
TRUE
```

when:

```text
Require password change on first login
```

is checked.

Mobile self-registration normally uses:

```text
FALSE
```

because the Student chooses their own password.

---

# 42. Failed Login Attempts

Use:

```text
failed_login_attempts INTEGER NOT NULL DEFAULT 0
```

Constraint:

```sql
CONSTRAINT chk_student_credentials_failed_attempts
CHECK (
    failed_login_attempts >= 0
)
```

---

# 43. locked_until

Use:

```text
locked_until TIMESTAMP NULL
```

Example future flow:

```text
Too many failed login attempts
        ↓
Set locked_until
        ↓
Reject login until timestamp expires
```

---

# 44. password_changed_at

Use:

```text
password_changed_at TIMESTAMP NULL
```

Set when Student changes password.

---

# 45. JWT and Refresh Sessions

Do NOT create:

```text
access_token
refresh_token
session_token
jwt_token
```

columns.

Authentication model remains:

```text
Access Token
→ RS256 JWT
→ not stored in PostgreSQL

Refresh Session
→ Redis
```

---

# 46. Student Role

Do NOT create:

```text
student_roles
student_user_roles
```

for the current design.

Authenticated Student JWT may automatically use:

```text
ROLE_STUDENT
```

---

# 47. Required Students Migration SQL

Create:

```text
V{next}__create_students.sql
```

SQL:

```sql
CREATE TABLE students (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    student_code VARCHAR(50) NOT NULL,

    university_email VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,

    last_name VARCHAR(100) NOT NULL,

    phone_number VARCHAR(30),

    date_of_birth DATE,

    faculty_id BIGINT NOT NULL,

    department_id BIGINT NOT NULL,

    program_id BIGINT NOT NULL,

    semester_id BIGINT,

    academic_year SMALLINT,

    enrollment_year SMALLINT,

    account_status VARCHAR(30) NOT NULL,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT,

    updated_at TIMESTAMP,

    CONSTRAINT uk_students_student_code
        UNIQUE (student_code),

    CONSTRAINT uk_students_university_email
        UNIQUE (university_email),

    CONSTRAINT chk_students_academic_year
        CHECK (
            academic_year IS NULL
            OR academic_year BETWEEN 1900 AND 9999
        ),

    CONSTRAINT chk_students_enrollment_year
        CHECK (
            enrollment_year IS NULL
            OR enrollment_year BETWEEN 1900 AND 9999
        ),

    CONSTRAINT chk_students_account_status
        CHECK (
            account_status IN (
                'PENDING',
                'ACTIVE',
                'INACTIVE',
                'SUSPENDED'
            )
        ),

    CONSTRAINT fk_students_faculty
        FOREIGN KEY (faculty_id)
        REFERENCES faculties(id),

    CONSTRAINT fk_students_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id),

    CONSTRAINT fk_students_program
        FOREIGN KEY (program_id)
        REFERENCES programs(id),

    CONSTRAINT fk_students_semester
        FOREIGN KEY (semester_id)
        REFERENCES semesters(id),

    CONSTRAINT fk_students_created_by
        FOREIGN KEY (created_by)
        REFERENCES app_users(id),

    CONSTRAINT fk_students_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES app_users(id)
);
```

---

# 48. Required Students Indexes

Do NOT duplicate indexes for:

```text
student_code
university_email
```

because UNIQUE constraints already index them.

Create:

```sql
CREATE INDEX idx_students_faculty_id
    ON students(faculty_id);

CREATE INDEX idx_students_department_id
    ON students(department_id);

CREATE INDEX idx_students_program_id
    ON students(program_id);

CREATE INDEX idx_students_semester_id
    ON students(semester_id);

CREATE INDEX idx_students_academic_year
    ON students(academic_year);

CREATE INDEX idx_students_enrollment_year
    ON students(enrollment_year);

CREATE INDEX idx_students_account_status
    ON students(account_status);

CREATE INDEX idx_students_is_deleted
    ON students(is_deleted);

CREATE INDEX idx_students_created_by
    ON students(created_by);

CREATE INDEX idx_students_updated_by
    ON students(updated_by);
```

Recommended composite filter index:

```sql
CREATE INDEX idx_students_academic_filters
    ON students(
        faculty_id,
        department_id,
        program_id,
        semester_id,
        academic_year,
        account_status,
        is_deleted
    );
```

---

# 49. Student Credentials Migration SQL

Create after `students`:

```text
V{next+1}__create_student_credentials.sql
```

SQL:

```sql
CREATE TABLE student_credentials (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    student_id BIGINT NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    force_password_change BOOLEAN NOT NULL DEFAULT FALSE,

    password_changed_at TIMESTAMP,

    failed_login_attempts INTEGER NOT NULL DEFAULT 0,

    locked_until TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP,

    CONSTRAINT uk_student_credentials_student_id
        UNIQUE (student_id),

    CONSTRAINT chk_student_credentials_failed_attempts
        CHECK (
            failed_login_attempts >= 0
        ),

    CONSTRAINT fk_student_credentials_student
        FOREIGN KEY (student_id)
        REFERENCES students(id)
);
```

---

# 50. Migration Files

Inspect the existing version first.

Create:

```text
src/main/resources/db/migration/
├── V{next}__create_students.sql
└── V{next+1}__create_student_credentials.sql
```

Example only:

```text
V21__create_students.sql
V22__create_student_credentials.sql
```

Do NOT assume these version numbers.

---

# 51. Add Student UI Mapping

| UI Field                | Database                                     |
| ----------------------- | -------------------------------------------- |
| Student ID              | `students.student_code`                      |
| University Email        | `students.university_email`                  |
| First Name              | `students.first_name`                        |
| Last Name               | `students.last_name`                         |
| Phone Number            | `students.phone_number`                      |
| Date of Birth           | `students.date_of_birth`                     |
| Faculty                 | `students.faculty_id`                        |
| Department              | `students.department_id`                     |
| Major / Program         | `students.program_id`                        |
| Academic Year           | `students.academic_year`                     |
| Current Semester        | `students.semester_id`                       |
| Enrollment Year         | `students.enrollment_year`                   |
| Account Status          | `students.account_status`                    |
| Temporary Password      | BCrypt → `student_credentials.password_hash` |
| Confirm Password        | Request only                                 |
| Require Password Change | `student_credentials.force_password_change`  |

---

# 52. Future Admin Create Request

Future:

```text
POST /api/v1/admin/students
```

Example:

```json
{
  "studentCode": "STD-2024-0001",
  "universityEmail": "thanapon.s@rsu.ac.th",

  "firstName": "Thanapon",
  "lastName": "Srisuk",

  "phoneNumber": "0812345678",
  "dateOfBirth": "2004-05-12",

  "facultyId": 1,
  "departmentId": 3,
  "programId": 5,
  "semesterId": 1,

  "academicYear": 2026,
  "enrollmentYear": 2024,

  "accountStatus": "ACTIVE",

  "temporaryPassword": "TemporaryPassword123!",
  "confirmPassword": "TemporaryPassword123!",

  "forcePasswordChange": true
}
```

---

# 53. Future Academic Validation

Before saving:

```text
Load Faculty
    ↓
Must exist
Must not be deleted
Must be active
    ↓
Load Department
    ↓
Must exist
Must not be deleted
Must be active
    ↓
department.faculty_id == facultyId
    ↓
Load Program
    ↓
Must exist
Must not be deleted
Must be active
    ↓
program.department_id == departmentId
```

Then validate Semester separately.

---

# 54. Semester Validation

If:

```text
semesterId != null
```

verify:

```text
Semester exists

is_deleted = FALSE
```

Store only:

```text
semester_id
```

Do not duplicate Semester names.

---

# 55. Future Mobile Registration

Future endpoint:

```text
POST /api/v1/student/auth/register
```

A Mobile Student should normally NOT be allowed to arbitrarily set:

```text
facultyId
departmentId
programId
semesterId
academicYear
accountStatus
createdBy
```

These should come from:

```text
Verified university Student data
```

or:

```text
Admin approval
```

Mobile registration should initially use:

```text
account_status = PENDING
```

when verification is required.

---

# 56. Search Fields

Future Student search should support:

```text
student_code
first_name
last_name
university_email
```

Example:

```text
GET /api/v1/admin/students?search=STD-2024
```

---

# 57. Faculty Filter

Because Faculty is now stored directly:

```text
GET /api/v1/admin/students?facultyId=1
```

filters:

```text
students.faculty_id
```

---

# 58. Department Filter

```text
GET /api/v1/admin/students?departmentId=3
```

filters:

```text
students.department_id
```

---

# 59. Program Filter

```text
GET /api/v1/admin/students?programId=5
```

filters:

```text
students.program_id
```

---

# 60. Academic Year Filter

Example:

```text
GET /api/v1/admin/students?academicYear=2026
```

Filter:

```text
students.academic_year = 2026
```

---

# 61. Semester Filter

Example:

```text
GET /api/v1/admin/students?semesterId=1
```

Filter:

```text
students.semester_id = 1
```

---

# 62. Enrollment Year Filter

Optional future filter:

```text
GET /api/v1/admin/students?enrollmentYear=2024
```

Filter:

```text
students.enrollment_year = 2024
```

---

# 63. Status Filter

Example:

```text
GET /api/v1/admin/students?status=ACTIVE
```

Filter:

```text
students.account_status = 'ACTIVE'
```

Supported:

```text
PENDING
ACTIVE
INACTIVE
SUSPENDED
```

---

# 64. Student Dashboard Counts

The UI displays:

```text
Total Students

Active Students

New This Semester

Inactive / Suspended
```

Do NOT store these values in `students`.

---

# 65. Total Students

Derive:

```sql
SELECT COUNT(*)
FROM students
WHERE is_deleted = FALSE;
```

---

# 66. Active Students

Derive:

```sql
SELECT COUNT(*)
FROM students
WHERE is_deleted = FALSE
  AND account_status = 'ACTIVE';
```

---

# 67. Inactive / Suspended

Derive:

```sql
SELECT COUNT(*)
FROM students
WHERE is_deleted = FALSE
  AND account_status IN (
      'INACTIVE',
      'SUSPENDED'
  );
```

---

# 68. New This Semester

Do NOT add:

```text
is_new_this_semester
```

to Student.

This should be derived later using the actual project definition for what qualifies as a new Student during a Semester.

---

# 69. Enrollment Data

Do NOT add:

```text
course_id
course_section_id
enrollment_status
```

to Student.

These belong in the future:

```text
enrollments
```

table.

---

# 70. Future Enrollment Relationship

After Student exists:

```text
students
    1
    │
    │
    N
enrollments
    N
    │
    │
    1
course_sections
```

Future foreign key:

```text
enrollments.student_id
→ students.id
```

---

# 71. No Sample Data Seed

Do NOT automatically insert UI demonstration Students such as:

```text
STD-2024-0001
STD-2024-0002
STD-2023-0147
```

Students must later be created through:

```text
Admin Create API
```

or:

```text
Mobile Registration
```

---

# 72. No ON DELETE CASCADE

Do NOT use:

```text
ON DELETE CASCADE
```

for:

```text
faculty_id
department_id
program_id
semester_id
created_by
updated_by
student_credentials.student_id
```

The project uses soft deletion.

---

# 73. PostgreSQL Verification

Run:

```sql
\dt
```

Expected:

```text
students
student_credentials
```

---

# 74. Verify Students Table

Run:

```sql
\d students
```

Expected:

```text
id
student_code
university_email
first_name
last_name
phone_number
date_of_birth

faculty_id
department_id
program_id
semester_id

academic_year
enrollment_year

account_status
is_deleted

created_by
created_at
updated_by
updated_at
```

---

# 75. Verify Student Credentials

Run:

```sql
\d student_credentials
```

Expected:

```text
id
student_id
password_hash
force_password_change
password_changed_at
failed_login_attempts
locked_until
created_at
updated_at
```

There must NOT be:

```text
raw_password
temporary_password
confirm_password
access_token
refresh_token
```

---

# 76. Verify Academic Foreign Keys

Verify:

```text
students.faculty_id
→ faculties.id
```

```text
students.department_id
→ departments.id
```

```text
students.program_id
→ programs.id
```

```text
students.semester_id
→ semesters.id
```

---

# 77. Verify Audit Foreign Keys

Verify:

```text
students.created_by
→ app_users.id
```

and:

```text
students.updated_by
→ app_users.id
```

Both must remain nullable.

---

# 78. Verify Year Fields

Verify:

```text
academic_year
```

accepts:

```text
2026
```

Verify:

```text
enrollment_year
```

accepts:

```text
2024
```

Do NOT treat Academic Year as:

```text
Year 1
Year 2
Year 3
Year 4
```

---

# 79. Flyway Verification

Run:

```sql
SELECT
    installed_rank,
    version,
    description,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Both migrations must show:

```text
success = TRUE
```

---

# 80. Agent Scope

For this task create only:

```text
src/main/resources/db/migration/
├── V{next}__create_students.sql
└── V{next+1}__create_student_credentials.sql
```

Do NOT create:

```text
Student.java
StudentCredential.java

StudentRepository.java
StudentCredentialRepository.java

StudentController.java

StudentService.java
StudentServiceImpl.java

StudentCreateRequest.java
StudentUpdateRequest.java
StudentResponse.java

StudentAuthController.java
```

---

# 81. Acceptance Criteria

## Student Identity

```text
[ ] students table exists

[ ] id is BIGINT Identity PK

[ ] student_code is UNIQUE

[ ] university_email is UNIQUE

[ ] first_name is required

[ ] last_name is required

[ ] phone_number nullable

[ ] date_of_birth uses DATE
```

## Academic Foreign Keys

```text
[ ] faculty_id exists

[ ] faculty_id references faculties.id

[ ] department_id exists

[ ] department_id references departments.id

[ ] program_id exists

[ ] program_id references programs.id

[ ] semester_id exists

[ ] semester_id references semesters.id

[ ] semester_id may be nullable
```

## Academic Hierarchy

```text
[ ] Future backend validates Department belongs to Faculty

[ ] Future backend validates Program belongs to Department

[ ] Foreign keys alone are not treated as hierarchy validation
```

## Years

```text
[ ] academic_year is SMALLINT

[ ] academic_year stores values such as 2026

[ ] enrollment_year is SMALLINT

[ ] enrollment_year stores values such as 2024

[ ] Academic Year is NOT Year 1/2/3/4
```

## Account Status

```text
[ ] PENDING supported

[ ] ACTIVE supported

[ ] INACTIVE supported

[ ] SUSPENDED supported

[ ] Invalid status rejected
```

## Soft Delete

```text
[ ] is_deleted exists

[ ] is_deleted defaults FALSE
```

## Audit

```text
[ ] created_by references app_users.id

[ ] created_by nullable for future self-registration

[ ] created_at defaults CURRENT_TIMESTAMP

[ ] updated_by references app_users.id

[ ] updated_by nullable

[ ] updated_at nullable
```

## Student Credentials

```text
[ ] student_credentials table exists

[ ] student_id references students.id

[ ] student_id is UNIQUE

[ ] password_hash required

[ ] force_password_change supported

[ ] failed_login_attempts defaults 0

[ ] locked_until nullable

[ ] password_changed_at nullable
```

## Password Security

```text
[ ] Raw password not stored

[ ] Temporary password not stored

[ ] Confirm password not stored

[ ] BCrypt password hash stored

[ ] Access JWT not stored in PostgreSQL

[ ] Refresh session not stored in PostgreSQL
```

## Flyway

```text
[ ] Existing migration versions inspected first

[ ] Correct next versions used

[ ] Existing migrations remain unchanged

[ ] Students migration succeeds

[ ] Student credentials migration succeeds

[ ] flyway_schema_history reports success
```

---

# 82. Final Structure

```text
faculties
    │
    │
    ├────────────────────────────┐
    │                            │
departments                      │
    │                            │
    ├──────────────────────┐     │
    │                      │     │
programs                   │     │
    │                      │     │
    └──────────────┐       │     │
                   ↓       ↓     ↓
                    students
                   /   |    \
                  /    |     \
                 ↓     ↓      ↓
 student_credentials  semesters  enrollments (future)
```

Detailed:

```text
students
│
├── id
├── student_code
├── university_email
├── first_name
├── last_name
├── phone_number
├── date_of_birth
│
├── faculty_id ─────────────→ faculties.id
├── department_id ──────────→ departments.id
├── program_id ─────────────→ programs.id
├── semester_id ────────────→ semesters.id
│
├── academic_year            // e.g. 2026
├── enrollment_year          // e.g. 2024
│
├── account_status
├── is_deleted
│
├── created_by ─────────────→ app_users.id
├── created_at
├── updated_by ─────────────→ app_users.id
└── updated_at
```

Credentials:

```text
student_credentials
│
├── id
├── student_id ─────────────→ students.id
├── password_hash
├── force_password_change
├── password_changed_at
├── failed_login_attempts
├── locked_until
├── created_at
└── updated_at
```

---

# 83. Next Step

After the Student Flyway migrations succeed:

```text
Student Migration
        ↓
Student Credentials Migration
        ↓
Admin Student CRUD
        ↓
Academic Relationship Validation
        ↓
Student Search / Filters
        ↓
Mobile Registration
        ↓
Student Authentication
        ↓
Enrollment Flyway Migration
        ↓
Enrollment CRUD
        ↓
Course Section enrolledCount
        ↓
FULL Section Calculation
```
