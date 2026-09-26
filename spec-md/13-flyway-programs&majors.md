# 14 - Programs & Majors Flyway Migration Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the normalized Flyway database migration for the:

```text
Academic Management
    ↓
Programs & Majors
```

This task covers only:

```text
Program / Major Table Normalization
        ↓
Department Relationship
        ↓
Degree Level as VARCHAR
        ↓
Flyway Migration
        ↓
Indexes
        ↓
Audit Fields
        ↓
Migration Validation
```

Do NOT implement yet:

- Program Entity
- Program Repository
- Program DTO
- Program Mapper
- Program Service
- Program Controller
- Program CRUD API
- Redis Cache
- Course Management
- Enrollment Management

---

# 2. Mandatory Agent Instructions

Before creating the migration, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Faculty Flyway migration specification.
5. Read the Department Flyway migration specification.
6. Inspect existing Flyway migrations.
7. Verify the `faculties` table exists.
8. Verify the `departments` table exists.
9. Verify the `app_users` table exists.
10. Determine the next available Flyway version.
11. Do NOT modify previously applied migrations.
12. Create only the migration required by this specification.
13. Run Flyway and validate the resulting schema.

---

# 3. UI Fields

The Add Program form contains:

```text
Program Code *
Degree Level *

Program Name *

Faculty *

Department *

Duration (years)

Total Credits

Status
```

The Programs & Majors list displays:

```text
Code
Program Name
Department
Degree
Duration
Total Credits
Status
Actions
```

The UI may also display the Faculty name under the Program name.

---

# 4. Normalization Decision

The Program belongs to a:

```text
Department
```

and every Department already belongs to a:

```text
Faculty
```

Existing relationship:

```text
faculties
    1
    │
    │
    N
departments
```

Therefore the Program table should store:

```text
department_id
```

but should NOT also store:

```text
faculty_id
```

because the Faculty can be determined through the Department.

Correct normalized relationship:

```text
Faculty
   1
   │
   │
   N
Department
   1
   │
   │
   N
Program
```

Faculty for a Program is derived through:

```text
programs.department_id
        ↓
departments.id
        ↓
departments.faculty_id
        ↓
faculties.id
```

---

# 5. Why faculty_id Is Not Stored

The UI requires the Administrator to select:

```text
Faculty
```

first and then:

```text
Department
```

However, the Faculty selection is used to filter the Department dropdown.

The final database record only needs:

```text
department_id
```

Incorrect normalized design:

```text
programs
├── faculty_id
└── department_id
```

This can create inconsistent data.

Example:

```text
faculty_id = Faculty of Engineering

department_id = Data Science
```

when Data Science belongs to another Faculty.

Correct:

```text
department_id = Data Science Department ID
```

and the Faculty is obtained from:

```text
departments.faculty_id
```

---

# 6. Table Name

Use exactly:

```text
programs
```

---

# 7. Table Purpose

The `programs` table stores university academic programs and majors.

Examples:

```text
PRG-CE-BS
Computer Engineering
Bachelor of Engineering

PRG-CE-MS
Computer Engineering (Graduate)
Master of Engineering

PRG-SE-BS
Software Engineering
Bachelor of Science

PRG-MKT-BS
Marketing
Bachelor of Business Administration
```

---

# 8. Normalized Table Attributes

| Column | Data Type | Constraint | Description |
|---|---|---|---|
| `id` | BIGINT | PK, Identity | Internal Program ID |
| `program_code` | VARCHAR(50) | UNIQUE, NOT NULL | Program code such as `PRG-CE-BS` |
| `program_name` | VARCHAR(255) | NOT NULL | Program/Major name |
| `degree_level` | VARCHAR(100) | NOT NULL | Degree name/level |
| `department_id` | BIGINT | FK → `departments.id`, NOT NULL | Department that owns the Program |
| `duration_years` | INTEGER | NULL | Program duration in years |
| `total_credits` | INTEGER | NULL | Total credits required |
| `is_active` | BOOLEAN | NOT NULL DEFAULT TRUE | Active/Inactive status |
| `is_deleted` | BOOLEAN | NOT NULL DEFAULT FALSE | Soft-delete flag |
| `created_by` | BIGINT | FK → `app_users.id`, NOT NULL | Admin who created the Program |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation time |
| `updated_by` | BIGINT | FK → `app_users.id`, NULL | Admin who last updated the Program |
| `updated_at` | TIMESTAMP | NULL | Last update time |

---

# 9. Final Normalized Structure

```text
programs
──────────────────────────────────
id                  BIGINT PK
program_code        VARCHAR(50)
program_name        VARCHAR(255)
degree_level        VARCHAR(100)
department_id       BIGINT FK
duration_years      INTEGER
total_credits       INTEGER
is_active           BOOLEAN
is_deleted          BOOLEAN
created_by          BIGINT FK
created_at          TIMESTAMP
updated_by          BIGINT FK
updated_at          TIMESTAMP
```

Do NOT add:

```text
faculty_id
faculty_name
department_name
degree_id
course_count
student_count
```

---

# 10. Primary Key

Use:

```sql
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
```

Do NOT use:

```text
UUID
program_code as primary key
manual numeric ID
```

Example:

```text
id = 1

program_code = PRG-CE-BS
```

---

# 11. Program Code

Column:

```text
program_code
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
PRG-CE-BS
PRG-CE-MS
PRG-EE-BS
PRG-SE-BS
PRG-DS-BS
PRG-AI-BS
PRG-MKT-BS
PRG-FIN-BS
```

Program Code is a business identifier.

It must not be used as the database primary key.

---

# 12. Program Name

Column:

```text
program_name
```

Type:

```text
VARCHAR(255)
```

Constraint:

```text
NOT NULL
```

Examples:

```text
Computer Engineering
Computer Engineering (Graduate)
Electrical Engineering
Software Engineering
Mobile Development
Data Science
Artificial Intelligence
Cybersecurity
Marketing
Finance
```

Program names should not be globally unique because similar Program names may potentially exist under different Departments or Degree Levels.

---

# 13. Program Name Uniqueness

Prevent an exact duplicate Program definition inside the same Department and Degree Level.

Use:

```sql
UNIQUE (
    department_id,
    program_name,
    degree_level
)
```

This prevents:

```text
Department:
Computer Engineering

Program:
Computer Engineering

Degree:
Bachelor of Engineering
```

from being created twice.

But it still allows:

```text
Computer Engineering
Bachelor of Engineering
```

and:

```text
Computer Engineering
Master of Engineering
```

as different Programs.

---

# 14. Degree Level

The Degree Level MUST use:

```text
VARCHAR
```

as requested.

Column:

```text
degree_level
```

Type:

```text
VARCHAR(100)
```

Constraint:

```text
NOT NULL
```

Do NOT create:

```text
degree_levels table
```

Do NOT create:

```text
PostgreSQL ENUM
```

Do NOT create:

```text
degree_level_id
```

for the current implementation.

---

# 15. Degree Level Examples

Example values:

```text
Bachelor of Engineering

Master of Engineering

Bachelor of Science

Master of Science

Bachelor of Business Administration

Master of Business Administration

Bachelor of Arts

Master of Arts

Doctor of Philosophy
```

The exact allowed values should later be validated by the application.

Database storage remains:

```text
VARCHAR(100)
```

---

# 16. Why Degree Level Is VARCHAR

Current design:

```text
Program
    ↓
degree_level VARCHAR(100)
```

Example:

```text
degree_level = Bachelor of Engineering
```

This keeps the current Program feature simple.

If Degree Management becomes a configurable feature later, it may be normalized into another table through a future migration.

For now:

```text
VARCHAR(100)
```

is required.

---

# 17. Department Relationship

Column:

```text
department_id
```

Type:

```text
BIGINT
```

Constraints:

```text
NOT NULL
FK → departments.id
```

Relationship:

```text
departments
     1
     │
     │
     N
  programs
```

Required FK:

```sql
CONSTRAINT fk_programs_department
    FOREIGN KEY (department_id)
    REFERENCES departments(id)
```

Do NOT use:

```text
ON DELETE CASCADE
```

because Departments and Programs use soft deletion.

---

# 18. Faculty Relationship

Do NOT create:

```text
programs.faculty_id
```

Faculty must be derived using:

```text
programs.department_id
        ↓
departments.id
        ↓
departments.faculty_id
        ↓
faculties.id
```

For list APIs later:

```text
Program
Department
Faculty
```

can be returned using database joins/entity relationships.

---

# 19. Faculty Selection in UI

The Add Program form contains:

```text
Faculty *
Department *
```

Expected frontend flow:

```text
Select Faculty
        ↓
Load Departments belonging to Faculty
        ↓
Select Department
        ↓
Submit departmentId
```

The frontend request may use `facultyId` temporarily for validation/filtering if needed, but the persistent Program database record should store only:

```text
department_id
```

Recommended future Create API request:

```json
{
  "programCode": "PRG-CE-BS",
  "programName": "Computer Engineering",
  "degreeLevel": "Bachelor of Engineering",
  "facultyId": 1,
  "departmentId": 1,
  "durationYears": 4,
  "totalCredits": 144,
  "isActive": true
}
```

The service must verify:

```text
department.faculty_id == requested facultyId
```

but only:

```text
departmentId
```

is persisted in `programs`.

---

# 20. Duration

UI field:

```text
Duration (years)
```

Database column:

```text
duration_years
```

Type:

```text
INTEGER
```

Nullable:

```text
YES
```

Examples:

```text
4
2
3
```

Do NOT store:

```text
4 years
2 yrs
```

as text.

Store:

```text
4
```

and let the frontend display:

```text
4 yrs
```

---

# 21. Duration Constraint

Recommended database check:

```sql
CHECK (
    duration_years IS NULL
    OR duration_years > 0
)
```

This prevents invalid values such as:

```text
0
-1
-4
```

---

# 22. Total Credits

UI field:

```text
Total Credits
```

Database column:

```text
total_credits
```

Type:

```text
INTEGER
```

Nullable:

```text
YES
```

Examples:

```text
144
138
132
130
128
126
36
```

Do NOT store:

```text
144 cr
```

Store:

```text
144
```

Frontend may display:

```text
144 cr
```

---

# 23. Total Credits Constraint

Recommended:

```sql
CHECK (
    total_credits IS NULL
    OR total_credits > 0
)
```

Invalid:

```text
0
-10
```

Valid:

```text
36
120
132
144
```

---

# 24. Active Status

The UI supports:

```text
Active
Inactive
```

Store:

```text
is_active BOOLEAN
```

Mapping:

```text
Active
→ TRUE

Inactive
→ FALSE
```

Default:

```text
TRUE
```

SQL:

```sql
is_active BOOLEAN NOT NULL DEFAULT TRUE
```

Do NOT create a separate Program status table.

---

# 25. Soft Delete

Column:

```text
is_deleted
```

Type:

```text
BOOLEAN
```

Default:

```text
FALSE
```

SQL:

```sql
is_deleted BOOLEAN NOT NULL DEFAULT FALSE
```

Later Program delete operations must use:

```text
is_deleted = TRUE
```

instead of physical deletion.

---

# 26. created_by

Column:

```text
created_by
```

Type:

```text
BIGINT
```

Constraints:

```text
NOT NULL
FK → app_users.id
```

Future Create API:

```text
created_by
```

must come from the authenticated Admin.

Do NOT accept it from the frontend.

---

# 27. created_at

Column:

```text
created_at
```

Type:

```text
TIMESTAMP
```

Constraints:

```text
NOT NULL
DEFAULT CURRENT_TIMESTAMP
```

---

# 28. updated_by

Column:

```text
updated_by
```

Type:

```text
BIGINT
```

Constraints:

```text
NULL
FK → app_users.id
```

New Program:

```text
updated_by = NULL
```

After update:

```text
updated_by = authenticated Admin ID
```

---

# 29. updated_at

Column:

```text
updated_at
```

Type:

```text
TIMESTAMP
```

Nullable:

```text
YES
```

New Program:

```text
updated_at = NULL
```

After update:

```text
updated_at = current timestamp
```

---

# 30. Required Flyway Migration

Create migration under:

```text
src/main/resources/db/migration/
```

The agent MUST inspect the existing migration versions.

Example only:

```text
V{next}__create_programs.sql
```

If the latest migration is:

```text
V12
```

create:

```text
V13__create_programs.sql
```

Do NOT assume `V13` without inspecting the project.

---

# 31. Required Migration SQL

```sql
CREATE TABLE programs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    program_code VARCHAR(50) NOT NULL,

    program_name VARCHAR(255) NOT NULL,

    degree_level VARCHAR(100) NOT NULL,

    department_id BIGINT NOT NULL,

    duration_years INTEGER,

    total_credits INTEGER,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT,

    updated_at TIMESTAMP,

    CONSTRAINT uk_programs_program_code
        UNIQUE (program_code),

    CONSTRAINT uk_programs_department_name_degree
        UNIQUE (
            department_id,
            program_name,
            degree_level
        ),

    CONSTRAINT fk_programs_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id),

    CONSTRAINT fk_programs_created_by
        FOREIGN KEY (created_by)
        REFERENCES app_users(id),

    CONSTRAINT fk_programs_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES app_users(id),

    CONSTRAINT chk_programs_duration_years
        CHECK (
            duration_years IS NULL
            OR duration_years > 0
        ),

    CONSTRAINT chk_programs_total_credits
        CHECK (
            total_credits IS NULL
            OR total_credits > 0
        )
);
```

---

# 32. Required Indexes

The following already receive indexes from UNIQUE constraints:

```text
program_code

(
    department_id,
    program_name,
    degree_level
)
```

Do NOT create duplicate indexes for those exact constraints.

Create:

```sql
CREATE INDEX idx_programs_department_id
    ON programs(department_id);

CREATE INDEX idx_programs_degree_level
    ON programs(degree_level);

CREATE INDEX idx_programs_is_active
    ON programs(is_active);

CREATE INDEX idx_programs_is_deleted
    ON programs(is_deleted);

CREATE INDEX idx_programs_created_by
    ON programs(created_by);

CREATE INDEX idx_programs_updated_by
    ON programs(updated_by);
```

Recommended combined index:

```sql
CREATE INDEX idx_programs_department_active_deleted
    ON programs(
        department_id,
        is_active,
        is_deleted
    );
```

This supports common Department + Status filtering.

---

# 33. Search Requirements

The Programs & Majors page contains:

```text
Search program name or code...
```

Future search should use:

```text
program_code
program_name
```

No separate search table is required.

---

# 34. Faculty Filter

The UI contains:

```text
Faculty
```

Filter must be performed through the relationship:

```text
programs.department_id
        ↓
departments.faculty_id
```

Do NOT add:

```text
faculty_id
```

to the Program table just for filtering.

Future query concept:

```sql
SELECT p.*
FROM programs p
JOIN departments d
    ON d.id = p.department_id
WHERE d.faculty_id = :facultyId;
```

---

# 35. Department Filter

The UI contains:

```text
Department
```

Filter directly using:

```text
programs.department_id
```

Example:

```text
departmentId = 5
```

---

# 36. Degree Level Filter

The UI contains:

```text
Degree Level
```

Filter using:

```text
programs.degree_level
```

Example:

```text
Bachelor of Science
```

Because `degree_level` is:

```text
VARCHAR(100)
```

the backend can filter directly using the stored value.

---

# 37. Status Filter

The UI contains:

```text
Status
```

Filter using:

```text
is_active
```

Mapping:

```text
ACTIVE
→ TRUE

INACTIVE
→ FALSE
```

---

# 38. Department programCount Integration

The Department page currently displays:

```text
Programs
```

Now that the Program feature exists, Department `programCount` should later be calculated from:

```text
programs
```

Condition:

```text
programs.department_id = departments.id

AND

programs.is_deleted = FALSE
```

Future calculation:

```sql
SELECT COUNT(*)
FROM programs
WHERE department_id = :departmentId
  AND is_deleted = FALSE;
```

Do NOT store:

```text
program_count
```

inside the Department table.

---

# 39. Course Count

The Program list does not require a Course count in the current UI.

Do NOT add:

```text
course_count
```

to `programs`.

Courses will later reference the appropriate academic structure.

---

# 40. Student Count

Do NOT add:

```text
student_count
```

to `programs`.

Student counts must later be derived from actual Student/Enrollment relationships.

---

# 41. UI to Database Mapping

| UI Field | Database |
|---|---|
| Program Code | `programs.program_code` |
| Program Name | `programs.program_name` |
| Degree Level | `programs.degree_level` |
| Faculty | Derived through Department |
| Department | `programs.department_id` |
| Duration (years) | `programs.duration_years` |
| Total Credits | `programs.total_credits` |
| Status | `programs.is_active` |
| Actions | Frontend only |

---

# 42. Program List Mapping

| List Column | Data Source |
|---|---|
| Code | `programs.program_code` |
| Program Name | `programs.program_name` |
| Faculty subtitle | Join Department → Faculty |
| Department | Join `departments` |
| Degree | `programs.degree_level` |
| Duration | `duration_years` |
| Total Credits | `total_credits` |
| Status | `is_active` |
| Actions | Frontend only |

---

# 43. Example Database Record

UI:

```text
Code:
PRG-CE-BS

Program:
Computer Engineering

Faculty:
Engineering

Department:
Computer Engineering

Degree:
Bachelor of Engineering

Duration:
4 years

Total Credits:
144

Status:
Active
```

Stored:

```text
program_code
= PRG-CE-BS

program_name
= Computer Engineering

degree_level
= Bachelor of Engineering

department_id
= <Computer Engineering Department ID>

duration_years
= 4

total_credits
= 144

is_active
= TRUE

is_deleted
= FALSE
```

Not stored:

```text
Faculty of Engineering
Engineering
4 years
144 cr
```

Faculty is derived and display suffixes are frontend concerns.

---

# 44. Academic Relationship After Migration

```text
faculties
    1
    │
    │
    N
departments
    1
    │
    │
    N
programs
```

Detailed:

```text
programs.department_id
        ↓
departments.id

departments.faculty_id
        ↓
faculties.id
```

---

# 45. Audit Relationship

```text
app_users
    1
    │
    ├────────── N programs.created_by
    │
    └────────── N programs.updated_by
```

Required:

```text
programs.created_by
→ app_users.id
```

and:

```text
programs.updated_by
→ app_users.id
```

---

# 46. Delete Behavior

Do NOT use:

```text
ON DELETE CASCADE
```

for:

```text
department_id
created_by
updated_by
```

The project uses soft deletion.

Departments should not be physically removed as part of normal operations.

Programs should also not be physically removed through normal CRUD.

---

# 47. No Sample Program Seed

Do NOT seed the sample Program records shown in the UI.

Do NOT automatically create:

```text
PRG-CE-BS
PRG-CE-MS
PRG-EE-BS
PRG-ME-BS
PRG-SE-BS
PRG-MOB-BS
PRG-DS-BS
PRG-AI-BS
PRG-CYB-BS
PRG-MKT-BS
PRG-DMKT-BS
PRG-FIN-BS
PRG-FINT-BS
PRG-IB-BS
PRG-MTH-BS
```

Those are UI demonstration data.

Actual Program records must later be created through the Program CRUD API.

---

# 48. Future Create API Request

The future API will use:

```text
POST /api/v1/admin/programs
```

Recommended request:

```json
{
  "programCode": "PRG-CE-BS",
  "programName": "Computer Engineering",
  "degreeLevel": "Bachelor of Engineering",
  "facultyId": 1,
  "departmentId": 3,
  "durationYears": 4,
  "totalCredits": 144,
  "isActive": true
}
```

Important:

```text
facultyId
```

may be received for validation because the UI selects Faculty first.

But the database stores only:

```text
departmentId
```

The service must verify that the selected Department belongs to the selected Faculty.

---

# 49. Future Program Validation

## programCode

```text
Required: YES
Maximum: 50
Unique: YES
```

Recommended format:

```text
PRG-CE-BS
```

---

## programName

```text
Required: YES
Maximum: 255
```

---

## degreeLevel

```text
Required: YES
Maximum: 100
Database Type: VARCHAR
```

---

## facultyId

```text
UI/API validation value
Required when creating through current UI
Must match selected Department's Faculty
Not stored in programs
```

---

## departmentId

```text
Required: YES
FK → departments.id
```

---

## durationYears

```text
Required: NO
Type: Integer
Must be > 0 when provided
```

---

## totalCredits

```text
Required: NO
Type: Integer
Must be > 0 when provided
```

---

## isActive

```text
Required: NO
Default: TRUE
```

---

# 50. PostgreSQL Verification

After Flyway runs:

```bash
docker exec -it smart-university-postgres \
psql \
-U smartrsu \
-d smart_university_db
```

Run:

```sql
\dt
```

Expected new table:

```text
programs
```

---

# 51. Verify Program Table

Run:

```sql
\d programs
```

Expected columns:

```text
id
program_code
program_name
degree_level
department_id
duration_years
total_credits
is_active
is_deleted
created_by
created_at
updated_by
updated_at
```

There must NOT be:

```text
faculty_id
faculty_name
department_name
degree_level_id
program_count
student_count
```

---

# 52. Verify Degree Level Type

Verify:

```text
degree_level
```

is:

```text
VARCHAR(100)
```

It must NOT be:

```text
ENUM
BIGINT FK
INTEGER
```

---

# 53. Verify Department FK

Verify:

```text
programs.department_id
        ↓
departments.id
```

Foreign key must exist.

---

# 54. Verify Audit FKs

Verify:

```text
programs.created_by
        ↓
app_users.id
```

and:

```text
programs.updated_by
        ↓
app_users.id
```

---

# 55. Verify Constraints

Verify:

```text
program_code UNIQUE
```

Verify:

```text
UNIQUE (
    department_id,
    program_name,
    degree_level
)
```

Verify:

```text
duration_years > 0
```

when provided.

Verify:

```text
total_credits > 0
```

when provided.

---

# 56. Flyway Verification

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

The Program migration must show:

```text
success = TRUE
```

---

# 57. Agent Scope

For this task create only:

```text
src/main/resources/db/migration/
└── V{next}__create_programs.sql
```

Do NOT create:

```text
Program.java

ProgramController.java

ProgramService.java

ProgramServiceImpl.java

ProgramRepository.java

ProgramMapper.java

ProgramCreateRequest.java

ProgramUpdateRequest.java

ProgramResponse.java
```

Those belong to the Program CRUD implementation specification.

---

# 58. Acceptance Criteria

## Table

```text
[ ] programs table exists

[ ] id is BIGINT Identity PK

[ ] program_code is VARCHAR(50)

[ ] program_code is NOT NULL

[ ] program_code is UNIQUE

[ ] program_name is VARCHAR(255)

[ ] program_name is NOT NULL

[ ] degree_level is VARCHAR(100)

[ ] degree_level is NOT NULL

[ ] department_id is BIGINT

[ ] department_id is NOT NULL

[ ] duration_years is INTEGER nullable

[ ] total_credits is INTEGER nullable

[ ] is_active defaults TRUE

[ ] is_deleted defaults FALSE
```

## Normalization

```text
[ ] faculty_id is NOT stored

[ ] faculty_name is NOT stored

[ ] department_name is NOT stored

[ ] degree_level_id is NOT used

[ ] degree_level table is NOT created

[ ] program_count is NOT stored

[ ] student_count is NOT stored
```

## Relationships

```text
[ ] department_id references departments.id

[ ] created_by references app_users.id

[ ] updated_by references app_users.id

[ ] ON DELETE CASCADE is not used
```

## Constraints

```text
[ ] program_code unique constraint exists

[ ] Department + Program Name + Degree Level unique constraint exists

[ ] duration_years positive check exists

[ ] total_credits positive check exists
```

## Flyway

```text
[ ] Existing Flyway migrations inspected first

[ ] Next available version used

[ ] Existing migrations untouched

[ ] Migration executes successfully

[ ] flyway_schema_history reports success
```

---

# 59. Final Normalized Structure

```text
faculties
    │
    │ 1
    │
    │ N
departments
    │
    │ 1
    │
    │ N
programs
    │
    ├── id
    ├── program_code
    ├── program_name
    ├── degree_level VARCHAR(100)
    ├── department_id
    ├── duration_years
    ├── total_credits
    ├── is_active
    ├── is_deleted
    ├── created_by
    ├── created_at
    ├── updated_by
    └── updated_at
```

Faculty is derived:

```text
Program
    ↓
Department
    ↓
Faculty
```

not duplicated inside the Program table.

---

# 60. Next Step

After the Program Flyway migration succeeds:

```text
Program Migration
        ↓
Program Entity
        ↓
Program Repository
        ↓
Program DTO
        ↓
Program Mapper
        ↓
Program Service
        ↓
Program Create API
        ↓
Program List API
        ↓
Search
        ↓
Faculty Filter
        ↓
Department Filter
        ↓
Degree Level Filter
        ↓
Status Filter
        ↓
Program Update API
        ↓
Program Soft Delete API
        ↓
Department programCount Integration
        ↓
Redis Cache Management
```