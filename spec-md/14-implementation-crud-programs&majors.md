# 15 - Programs & Majors CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD workflow for:

```text
Academic Management
    ↓
Programs & Majors
```

The Program feature uses the existing normalized table:

```text
programs
```

This specification implements:

```text
Create Program
Get Program List
Get Program By ID
Update Program
Soft Delete Program
Search
Faculty Filter
Department Filter
Status Filter
Pagination
Sorting
Summary
Redis Cache Management
```

---

# 2. Important Degree Level Requirement

`degree_level` MUST be stored and handled as a plain String.

Database:

```text
degree_level VARCHAR(100)
```

Java:

```java
String degreeLevel
```

Frontend:

```text
Text Input
```

The user must manually type the Degree Level.

Example:

```text
Bachelor of Engineering
Master of Engineering
Bachelor of Science
Master of Science
Bachelor of Business Administration
Doctor of Philosophy
```

Do NOT implement Degree Level as:

```text
Enum
Dropdown
Select list
List<String>
Lookup table
Foreign key
degree_level_id
PostgreSQL ENUM
```

Correct:

```java
private String degreeLevel;
```

Incorrect:

```java
private DegreeLevel degreeLevel;
```

Incorrect:

```java
private Long degreeLevelId;
```

Incorrect:

```java
private List<String> degreeLevel;
```

The backend must receive Degree Level as a normal JSON string.

---

# 3. Degree Level UI

The frontend field must be:

```text
Text Box
```

Example:

```text
Degree Level *
[ Bachelor of Engineering                  ]
```

It must NOT be:

```text
Select...
Bachelor
Master
Doctorate
```

unless a future requirement explicitly changes this design.

---

# 4. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the API path convention specification.
5. Read the shared Flyway migration standards.
6. Read the Programs & Majors migration specification.
7. Inspect the existing `programs` table.
8. Inspect Faculty implementation.
9. Inspect Department implementation.
10. Inspect Authentication and RS256 security.
11. Inspect existing Redis configuration.
12. Inspect global API response/error conventions.
13. Follow feature-based architecture.
14. Do NOT change `degree_level` from `VARCHAR`.
15. Do NOT introduce a Degree Level enum.
16. Do NOT introduce a Degree Level lookup table.
17. Re-check project structure after implementation.
18. Run tests before completion.

---

# 5. API Base Path

Use:

```text
/api/v1/admin/programs
```

Local development:

```text
http://localhost:8080/api/v1/admin/programs
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/programs")
public class ProgramController {
}
```

---

# 6. Authentication

Every Program API requires:

```http
Authorization: Bearer <access-token>
```

JWT authentication must use the existing:

```text
RS256
```

implementation.

Audit values such as:

```text
createdBy
updatedBy
```

must come from the authenticated Admin.

Never accept them from request JSON.

---

# 7. Authorization

Recommended roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

These roles may manage Programs & Majors.

Follow existing Spring Security authority conventions.

---

# 8. Database Table

Use the existing table:

```text
programs
```

Attributes:

| Column | Type |
|---|---|
| `id` | BIGINT |
| `program_code` | VARCHAR(50) |
| `program_name` | VARCHAR(255) |
| `degree_level` | VARCHAR(100) |
| `department_id` | BIGINT |
| `duration_years` | INTEGER |
| `total_credits` | INTEGER |
| `is_active` | BOOLEAN |
| `is_deleted` | BOOLEAN |
| `created_by` | BIGINT |
| `created_at` | TIMESTAMP |
| `updated_by` | BIGINT |
| `updated_at` | TIMESTAMP |

Do NOT redesign this schema.

---

# 9. Academic Relationship

The normalized relationship is:

```text
Faculty
    1
    │
    N
Department
    1
    │
    N
Program
```

Stored:

```text
programs.department_id
```

Derived:

```text
Faculty
```

through:

```text
Program
    ↓
Department
    ↓
Faculty
```

Do NOT store:

```text
programs.faculty_id
```

---

# 10. Feature Package Structure

Use:

```text
feature/
└── program/
    ├── controller/
    │   └── ProgramController.java
    │
    ├── dto/
    │   ├── ProgramCreateRequest.java
    │   ├── ProgramUpdateRequest.java
    │   ├── ProgramResponse.java
    │   ├── ProgramListResponse.java
    │   └── ProgramSummaryResponse.java
    │
    ├── mapper/
    │   └── ProgramMapper.java
    │
    ├── entity/
    │   └── Program.java
    │
    ├── service/
    │   ├── ProgramService.java
    │   └── impl/
    │       └── ProgramServiceImpl.java
    │
    ├── repository/
    │   └── ProgramRepository.java
    │
    └── exception/
        ├── ProgramNotFoundException.java
        ├── ProgramCodeAlreadyExistsException.java
        ├── ProgramAlreadyExistsException.java
        └── InvalidProgramDepartmentException.java
```

---

# 11. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/programs

GET
/api/v1/admin/programs

GET
/api/v1/admin/programs/{id}

PUT
/api/v1/admin/programs/{id}

DELETE
/api/v1/admin/programs/{id}

GET
/api/v1/admin/programs/summary
```

---

# 12. Create Program API

Endpoint:

```http
POST /api/v1/admin/programs
```

---

# 13. ProgramCreateRequest

Use:

```java
public record ProgramCreateRequest(
    String programCode,
    String programName,
    String degreeLevel,
    Long facultyId,
    Long departmentId,
    Integer durationYears,
    Integer totalCredits,
    Boolean isActive
) {}
```

Important:

```java
String degreeLevel
```

must remain a String.

---

# 14. Create Request Example

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

The API receives:

```text
facultyId
```

for relationship validation.

The `programs` table stores only:

```text
department_id
```

---

# 15. Degree Level Request Rule

Valid:

```json
{
  "degreeLevel": "Bachelor of Engineering"
}
```

Valid:

```json
{
  "degreeLevel": "Master of Engineering"
}
```

Valid:

```json
{
  "degreeLevel": "Doctor of Philosophy"
}
```

The backend must not restrict the user to a predefined list unless a later specification explicitly adds such validation.

---

# 16. Program Code Validation

Rules:

```text
Required
Maximum 50 characters
Trim whitespace
Unique
```

Recommended format:

```text
PRG-CE-BS
```

Recommended pattern:

```text
^[A-Z0-9-]+$
```

---

# 17. Program Name Validation

Rules:

```text
Required
Maximum 255 characters
Trim whitespace
```

Examples:

```text
Computer Engineering
Artificial Intelligence
Digital Marketing
Financial Technology
```

---

# 18. Degree Level Validation

Rules:

```text
Required
String
Maximum 100 characters
Trim whitespace
Must not be blank
```

Example validation:

```java
@NotBlank
@Size(max = 100)
String degreeLevel
```

Do NOT use:

```java
@Enum
```

Do NOT convert input into an enum.

---

# 19. Faculty Validation

The Create request provides:

```text
facultyId
```

The service must:

```text
Load Faculty
    ↓
Verify Faculty exists
    ↓
Verify is_deleted = FALSE
    ↓
Verify is_active = TRUE
```

Then load the selected Department.

---

# 20. Department Validation

The service must verify:

```text
departmentId
```

exists and:

```text
is_deleted = FALSE
```

and:

```text
is_active = TRUE
```

Then verify:

```text
department.faculty.id == facultyId
```

If they do not match:

```text
400 Bad Request
```

Example:

```text
Selected department does not belong to the selected faculty.
```

---

# 21. Duration Validation

Field:

```text
durationYears
```

Type:

```text
Integer
```

Optional.

When provided:

```text
> 0
```

Example:

```json
{
  "durationYears": 4
}
```

Do NOT receive:

```json
{
  "durationYears": "4 years"
}
```

---

# 22. Total Credits Validation

Field:

```text
totalCredits
```

Type:

```text
Integer
```

Optional.

When provided:

```text
> 0
```

Example:

```json
{
  "totalCredits": 144
}
```

---

# 23. Status

Field:

```text
isActive
```

Type:

```text
Boolean
```

Default:

```text
TRUE
```

Active:

```text
true
```

Inactive:

```text
false
```

---

# 24. Duplicate Validation

Before create, verify:

```text
programCode
```

is unique.

Also enforce the existing database uniqueness rule:

```text
departmentId
+
programName
+
degreeLevel
```

Example duplicate:

```text
Department:
Computer Engineering

Program:
Computer Engineering

Degree Level:
Bachelor of Engineering
```

must not be created twice.

---

# 25. Degree Level Comparison

When checking duplicate Programs, normalize Degree Level safely.

Recommended:

```text
Trim whitespace
```

Example:

```text
" Bachelor of Engineering "
```

becomes:

```text
"Bachelor of Engineering"
```

Do not convert it to an enum.

Case-insensitive duplicate checking may be used consistently if required.

---

# 26. Create Workflow

```text
Receive ProgramCreateRequest
        ↓
Validate input
        ↓
Get authenticated Admin ID
        ↓
Normalize programCode
        ↓
Trim programName
        ↓
Trim degreeLevel
        ↓
Check Program Code duplicate
        ↓
Load Faculty
        ↓
Validate Faculty
        ↓
Load Department
        ↓
Validate Department
        ↓
Verify Department belongs to Faculty
        ↓
Check Program duplicate
        ↓
Create Program
        ↓
isDeleted = FALSE
        ↓
createdBy = authenticated Admin
        ↓
createdAt = current timestamp
        ↓
Save
        ↓
Evict Program caches
        ↓
Evict Department caches
        ↓
Return 201
```

---

# 27. Create Response

HTTP:

```text
201 Created
```

Example:

```json
{
  "success": true,
  "message": "Program created successfully",
  "data": {
    "id": 1,
    "programCode": "PRG-CE-BS",
    "programName": "Computer Engineering",
    "degreeLevel": "Bachelor of Engineering",
    "facultyId": 1,
    "facultyName": "Faculty of Engineering",
    "departmentId": 3,
    "departmentName": "Computer Engineering",
    "durationYears": 4,
    "totalCredits": 144,
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-25T19:00:00"
  }
}
```

---

# 28. Get Program List API

Endpoint:

```http
GET /api/v1/admin/programs
```

Support:

```text
search
facultyId
departmentId
degreeLevel
status
page
size
sort
```

---

# 29. Search

Example:

```text
GET /api/v1/admin/programs?search=computer
```

Search:

```text
program_code
program_name
```

Use case-insensitive matching where appropriate.

---

# 30. Faculty Filter

Example:

```text
GET /api/v1/admin/programs?facultyId=1
```

Filter using:

```text
Program
    ↓
Department
    ↓
Faculty
```

Do NOT add `faculty_id` to the Program table.

---

# 31. Department Filter

Example:

```text
GET /api/v1/admin/programs?departmentId=3
```

Filter directly using:

```text
programs.department_id
```

---

# 32. Degree Level Filter

Because Degree Level is a plain String, filtering also uses a String.

Example:

```text
GET /api/v1/admin/programs?degreeLevel=Bachelor%20of%20Engineering
```

Backend parameter:

```java
String degreeLevel
```

Do NOT use:

```java
DegreeLevel degreeLevel
```

Do NOT expect an ID.

---

# 33. Degree Level Filter Behavior

Recommended exact case-insensitive comparison:

```text
Bachelor of Engineering
```

matches stored:

```text
Bachelor of Engineering
```

The backend may also support case-insensitive matching.

Do NOT require a predefined Degree Level list.

---

# 34. Status Filter

Supported:

```text
ACTIVE
INACTIVE
```

Mapping:

```text
ACTIVE
→ is_active = TRUE

INACTIVE
→ is_active = FALSE
```

---

# 35. Soft Delete Filter

Every normal query must include:

```text
is_deleted = FALSE
```

Deleted Programs must not appear in:

```text
List
Search
Filters
Detail
Summary
Department programCount
```

---

# 36. Pagination

Recommended:

```text
page = 0
size = 20
```

Maximum recommended:

```text
size = 100
```

Example:

```text
GET /api/v1/admin/programs?page=0&size=20
```

---

# 37. Sorting

Default:

```text
createdAt,desc
```

Allowed fields may include:

```text
programCode
programName
degreeLevel
durationYears
totalCredits
createdAt
```

Example:

```text
GET /api/v1/admin/programs?sort=programName,asc
```

Validate sort properties.

---

# 38. ProgramListResponse

Recommended:

```text
id
programCode
programName

facultyId
facultyName

departmentId
departmentName

degreeLevel
durationYears
totalCredits
isActive
```

Example:

```json
{
  "id": 1,
  "programCode": "PRG-CE-BS",
  "programName": "Computer Engineering",
  "facultyId": 1,
  "facultyName": "Faculty of Engineering",
  "departmentId": 3,
  "departmentName": "Computer Engineering",
  "degreeLevel": "Bachelor of Engineering",
  "durationYears": 4,
  "totalCredits": 144,
  "isActive": true
}
```

---

# 39. Paginated Response

Example:

```json
{
  "success": true,
  "message": "Programs retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "programCode": "PRG-CE-BS",
        "programName": "Computer Engineering",
        "facultyId": 1,
        "facultyName": "Faculty of Engineering",
        "departmentId": 3,
        "departmentName": "Computer Engineering",
        "degreeLevel": "Bachelor of Engineering",
        "durationYears": 4,
        "totalCredits": 144,
        "isActive": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

---

# 40. Get Program By ID

Endpoint:

```http
GET /api/v1/admin/programs/{id}
```

Example:

```text
GET /api/v1/admin/programs/1
```

---

# 41. Get By ID Workflow

```text
Receive Program ID
        ↓
Find Program where is_deleted = FALSE
        ↓
Not found?
        ↓
404
        ↓
Load Department
        ↓
Load Faculty through Department
        ↓
Map response
        ↓
200 OK
```

---

# 42. Detail Response

Example:

```json
{
  "success": true,
  "message": "Program retrieved successfully",
  "data": {
    "id": 1,
    "programCode": "PRG-CE-BS",
    "programName": "Computer Engineering",
    "degreeLevel": "Bachelor of Engineering",
    "faculty": {
      "id": 1,
      "facultyCode": "FAC-ENG",
      "facultyNameEn": "Faculty of Engineering"
    },
    "department": {
      "id": 3,
      "departmentCode": "DEPT-CE",
      "departmentName": "Computer Engineering"
    },
    "durationYears": 4,
    "totalCredits": 144,
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-25T19:00:00",
    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 43. Update Program API

Endpoint:

```http
PUT /api/v1/admin/programs/{id}
```

---

# 44. ProgramUpdateRequest

Use:

```java
public record ProgramUpdateRequest(
    String programCode,
    String programName,
    String degreeLevel,
    Long facultyId,
    Long departmentId,
    Integer durationYears,
    Integer totalCredits,
    Boolean isActive
) {}
```

Again:

```java
String degreeLevel
```

is mandatory.

---

# 45. Update Request Example

```json
{
  "programCode": "PRG-CE-BS",
  "programName": "Computer Engineering",
  "degreeLevel": "Bachelor of Engineering",
  "facultyId": 1,
  "departmentId": 3,
  "durationYears": 4,
  "totalCredits": 145,
  "isActive": true
}
```

---

# 46. Update Rules

Allow:

```text
programCode
programName
degreeLevel
facultyId validation value
departmentId
durationYears
totalCredits
isActive
```

Do NOT allow request control of:

```text
id
isDeleted
createdBy
createdAt
updatedBy
updatedAt
```

---

# 47. Update Degree Level

The Administrator may change:

```text
Bachelor of Engineering
```

to:

```text
Master of Engineering
```

by sending a new String.

Example:

```json
{
  "degreeLevel": "Master of Engineering"
}
```

No enum conversion is permitted.

---

# 48. Update Faculty/Department Validation

When Faculty or Department changes:

```text
Load Faculty
        ↓
Validate Faculty
        ↓
Load Department
        ↓
Validate Department
        ↓
Verify Department belongs to selected Faculty
```

Only `department_id` is persisted.

---

# 49. Update Duplicate Validation

Check:

```text
programCode
```

excluding current Program ID.

Also check:

```text
departmentId
+
programName
+
degreeLevel
```

excluding current Program ID.

---

# 50. Update Workflow

```text
Receive ID + Update Request
        ↓
Find non-deleted Program
        ↓
Not found → 404
        ↓
Validate input
        ↓
Trim degreeLevel String
        ↓
Check Program Code duplicate
        ↓
Load Faculty
        ↓
Load Department
        ↓
Verify relationship
        ↓
Check Program duplicate
        ↓
Get authenticated Admin
        ↓
Update fields
        ↓
updatedBy = authenticated Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Program caches
        ↓
Evict old/new Department caches if needed
        ↓
Return 200
```

---

# 51. Delete Program API

Endpoint:

```http
DELETE /api/v1/admin/programs/{id}
```

Delete must be:

```text
SOFT DELETE
```

Set:

```text
is_deleted = TRUE
```

Do NOT physically remove the row.

---

# 52. Delete Workflow

```text
Receive Program ID
        ↓
Find non-deleted Program
        ↓
Not found → 404
        ↓
Check future dependent records if implemented
        ↓
Get authenticated Admin
        ↓
isDeleted = TRUE
        ↓
updatedBy = Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Program caches
        ↓
Evict Department cache
        ↓
Return success
```

---

# 53. Future Delete Restrictions

When Course/Enrollment relationships exist, Program deletion may need to be rejected if active dependent records exist.

Future:

```text
Program
    ↓
Students / Courses / Enrollment
```

may result in:

```text
409 Conflict
```

Do not invent those checks before those modules exist.

---

# 54. Program Summary API

Endpoint:

```http
GET /api/v1/admin/programs/summary
```

Recommended response:

```json
{
  "success": true,
  "message": "Program summary retrieved successfully",
  "data": {
    "totalPrograms": 20,
    "activePrograms": 18,
    "inactivePrograms": 2
  }
}
```

---

# 55. Summary Calculation

Total:

```text
COUNT programs
WHERE is_deleted = FALSE
```

Active:

```text
COUNT programs
WHERE is_deleted = FALSE
AND is_active = TRUE
```

Inactive:

```text
COUNT programs
WHERE is_deleted = FALSE
AND is_active = FALSE
```

Do NOT store these totals.

---

# 56. Department Program Count

Department list currently displays:

```text
Programs
```

Now derive:

```text
programCount
```

using:

```sql
SELECT COUNT(*)
FROM programs
WHERE department_id = :departmentId
  AND is_deleted = FALSE;
```

Do NOT store `program_count` in `departments`.

---

# 57. Program Entity

`Program.java` should contain:

```java
Long id;

String programCode;

String programName;

String degreeLevel;

Department department;

Integer durationYears;

Integer totalCredits;

Boolean isActive;

Boolean isDeleted;

AppUser createdBy;

LocalDateTime createdAt;

AppUser updatedBy;

LocalDateTime updatedAt;
```

Important:

```java
String degreeLevel;
```

---

# 58. Forbidden Degree Level Entity Designs

Do NOT create:

```java
@Enumerated(EnumType.STRING)
private DegreeLevel degreeLevel;
```

Do NOT create:

```java
@ManyToOne
private DegreeLevel degreeLevel;
```

Do NOT create:

```java
private Long degreeLevelId;
```

Correct:

```java
@Column(name = "degree_level", nullable = false, length = 100)
private String degreeLevel;
```

---

# 59. Program Repository Responsibilities

Repository handles:

```text
Find non-deleted Program by ID
Program Code duplicate checking
Program duplicate checking
Search
Faculty filtering
Department filtering
Degree Level String filtering
Status filtering
Pagination
Summary counts
Department program counts
```

---

# 60. Repository Method Concepts

Examples:

```text
findByIdAndIsDeletedFalse(...)

existsByProgramCodeAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByIsActiveTrueAndIsDeletedFalse()

countByIsActiveFalseAndIsDeletedFalse()

countByDepartmentIdAndIsDeletedFalse(...)
```

For dynamic filtering, use:

```text
Specification
Criteria API
JPQL
```

according to existing project conventions.

---

# 61. Degree Level Repository Filter

Filter parameter must remain:

```java
String degreeLevel
```

Example:

```java
degreeLevel.equalsIgnoreCase(program.getDegreeLevel())
```

or database equivalent.

Do not use an enum parameter.

---

# 62. ProgramService

Recommended interface:

```java
ProgramResponse create(
    ProgramCreateRequest request
);

Page<ProgramListResponse> getAll(
    String search,
    Long facultyId,
    Long departmentId,
    String degreeLevel,
    String status,
    Pageable pageable
);

ProgramResponse getById(
    Long id
);

ProgramResponse update(
    Long id,
    ProgramUpdateRequest request
);

void delete(
    Long id
);

ProgramSummaryResponse getSummary();
```

Notice:

```java
String degreeLevel
```

---

# 63. ProgramServiceImpl Responsibilities

Business logic includes:

```text
Validation
String Degree Level handling
Faculty validation
Department validation
Faculty/Department consistency
Duplicate checking
Authenticated Admin lookup
Audit fields
Soft delete
Transactions
Mapping
Redis cache management
```

---

# 64. Mapper

`ProgramMapper` handles:

```text
ProgramCreateRequest
    ↓
Program

Program
    ↓
ProgramResponse

Program
    ↓
ProgramListResponse
```

Degree Level mapping:

```text
request.degreeLevel String
        ↓
entity.degreeLevel String
        ↓
response.degreeLevel String
```

No conversion to enum.

---

# 65. Transactions

Use:

```text
@Transactional
```

for:

```text
Create
Update
Delete
```

Use:

```text
@Transactional(readOnly = true)
```

for:

```text
List
Get By ID
Summary
```

---

# 66. Redis Cache Management

Recommended cache groups:

```text
program-cache
program-list-cache
program-summary-cache
```

---

# 67. Program Detail Cache

Logical key:

```text
program:<id>
```

Example:

```text
program:15
```

---

# 68. List Cache

If implemented, list cache must include:

```text
search
facultyId
departmentId
degreeLevel
status
page
size
sort
```

Example concept:

```text
program:list:<search>:<faculty>:<department>:<degreeLevel>:<status>:<page>:<size>:<sort>
```

Degree Level remains a String in the cache key.

---

# 69. Cache Eviction - Create

After Create:

```text
Evict Program list cache
Evict Program summary cache
Evict Department-related cache
```

because:

```text
department.programCount
```

changes.

---

# 70. Cache Eviction - Update

After Update:

```text
Evict program:<id>
Evict Program list cache
Evict Program summary cache
Evict old Department cache
Evict new Department cache
```

if Department changes.

---

# 71. Cache Eviction - Delete

After Delete:

```text
Evict program:<id>
Evict Program list cache
Evict Program summary cache
Evict Department cache
```

---

# 72. PostgreSQL Is Source of Truth

```text
PostgreSQL
→ Permanent Program data

Redis
→ Cache only
```

Do NOT make Redis the source of Program data.

---

# 73. Exceptions

Create:

```text
ProgramNotFoundException

ProgramCodeAlreadyExistsException

ProgramAlreadyExistsException

InvalidProgramDepartmentException
```

Feature exceptions:

```text
feature/program/exception/
```

Global handling remains in:

```text
exception/GlobalExceptionHandler.java
```

---

# 74. HTTP Status Codes

| Operation | Status |
|---|---|
| Create | `201 Created` |
| List | `200 OK` |
| Get By ID | `200 OK` |
| Update | `200 OK` |
| Delete | `204 No Content` or project-standard response |
| Summary | `200 OK` |
| Invalid Request | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden | `403 Forbidden` |
| Not Found | `404 Not Found` |
| Duplicate | `409 Conflict` |

---

# 75. Testing - Create

```text
[ ] Valid Program creation works

[ ] degreeLevel accepts normal String

[ ] "Bachelor of Engineering" works

[ ] "Master of Engineering" works

[ ] Custom valid Degree Level String works

[ ] Degree Level is not converted to enum

[ ] Blank Degree Level rejected

[ ] Degree Level longer than 100 rejected

[ ] Duplicate Program Code rejected

[ ] Invalid Faculty rejected

[ ] Invalid Department rejected

[ ] Faculty/Department mismatch rejected

[ ] durationYears positive validation works

[ ] totalCredits positive validation works

[ ] createdBy comes from JWT
```

---

# 76. Testing - List

```text
[ ] List returns non-deleted Programs

[ ] Search works

[ ] Faculty filter works

[ ] Department filter works

[ ] Degree Level String filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works
```

---

# 77. Testing - Degree Level

Explicitly test:

```text
Bachelor of Engineering

Bachelor of Science

Master of Engineering

Doctor of Philosophy

Custom Academic Degree Name
```

Verify all are stored as:

```text
String
```

No enum/list validation should reject them solely because they are not predefined.

---

# 78. Testing - Update

```text
[ ] Program name update works

[ ] Degree Level String update works

[ ] Department change works

[ ] Faculty/Department relationship validation works

[ ] Duration update works

[ ] Credit update works

[ ] Active status update works

[ ] Duplicate validation excludes current record

[ ] updatedBy comes from JWT

[ ] updatedAt updates
```

---

# 79. Testing - Delete

```text
[ ] Soft delete works

[ ] Row remains in PostgreSQL

[ ] isDeleted becomes TRUE

[ ] Program disappears from normal list

[ ] Program detail returns 404 after delete

[ ] Department programCount updates
```

---

# 80. Testing - Redis

```text
[ ] Program detail cached

[ ] Program update evicts cache

[ ] Program delete evicts cache

[ ] Program create invalidates list and summary

[ ] Department cache invalidated when programCount changes

[ ] PostgreSQL remains source of truth
```

---

# 81. Testing - Security

```text
[ ] Missing JWT returns 401

[ ] Invalid JWT returns 401

[ ] Expired JWT returns 401

[ ] Unauthorized role returns 403

[ ] Authorized Admin can access Program APIs
```

---

# 82. Implementation Order

Implement:

```text
1. Verify Programs Flyway migration
        ↓
2. Verify degree_level is VARCHAR(100)
        ↓
3. Create Program Entity
        ↓
4. Use String degreeLevel
        ↓
5. Create Program Repository
        ↓
6. Create Program DTOs
        ↓
7. Ensure DTO degreeLevel is String
        ↓
8. Create Program Mapper
        ↓
9. Create exceptions
        ↓
10. Create ProgramService
        ↓
11. Create ProgramServiceImpl
        ↓
12. Implement Faculty validation
        ↓
13. Implement Department validation
        ↓
14. Implement Faculty/Department consistency check
        ↓
15. Implement Create
        ↓
16. Implement List
        ↓
17. Implement Search
        ↓
18. Implement Faculty filter
        ↓
19. Implement Department filter
        ↓
20. Implement Degree Level String filter
        ↓
21. Implement Status filter
        ↓
22. Implement Get By ID
        ↓
23. Implement Update
        ↓
24. Implement Soft Delete
        ↓
25. Implement Summary
        ↓
26. Update Department programCount
        ↓
27. Add Redis cache management
        ↓
28. Create ProgramController
        ↓
29. Apply authorization
        ↓
30. Add tests
        ↓
31. Run Maven tests
        ↓
32. Run Maven package
        ↓
33. Re-check project structure
```

---

# 83. UI Create Workflow

```text
Click Add Program
        ↓
Enter Program Code
        ↓
TYPE Degree Level manually
        ↓
Enter Program Name
        ↓
Select Faculty
        ↓
Load Departments by Faculty
        ↓
Select Department
        ↓
Select Duration
        ↓
Enter Total Credits
        ↓
Choose Active / Inactive
        ↓
POST /api/v1/admin/programs
```

Degree Level UI must be:

```text
<input type="text">
```

not:

```text
<select>
```

---

# 84. UI List Workflow

On page load:

```text
GET /api/v1/admin/programs
        ↓
Render
Code
Program Name
Department
Degree Level
Duration
Credits
Status
```

Filters:

```text
Faculty
Department
Degree Level text
Status
```

Degree Level may use a text filter if filtering is required.

---

# 85. Final API List

```text
POST
/api/v1/admin/programs

GET
/api/v1/admin/programs

GET
/api/v1/admin/programs/{id}

PUT
/api/v1/admin/programs/{id}

DELETE
/api/v1/admin/programs/{id}

GET
/api/v1/admin/programs/summary
```

Local:

```text
http://localhost:8080/api/v1/admin/programs
```

---

# 86. Final Degree Level Rule

Across the entire Program feature:

```text
Database
degree_level VARCHAR(100)

Entity
String degreeLevel

Create DTO
String degreeLevel

Update DTO
String degreeLevel

Response DTO
String degreeLevel

List DTO
String degreeLevel

Filter Parameter
String degreeLevel

Frontend
Text Input
```

Never:

```text
Enum
List
Dropdown dependency
Lookup table
Foreign key
Numeric ID
```

unless a future specification explicitly replaces this design.

---

# 87. Acceptance Criteria

## Degree Level

```text
[ ] degree_level remains VARCHAR(100)

[ ] Entity uses String degreeLevel

[ ] CreateRequest uses String degreeLevel

[ ] UpdateRequest uses String degreeLevel

[ ] Response uses String degreeLevel

[ ] Search/filter uses String degreeLevel

[ ] User can type arbitrary valid Degree Level text

[ ] No Degree Level enum exists

[ ] No Degree Level lookup table exists

[ ] No Degree Level ID exists

[ ] Backend does not require a predefined Degree Level list
```

## Create

```text
[ ] POST /api/v1/admin/programs works

[ ] Program Code validation works

[ ] Faculty validation works

[ ] Department validation works

[ ] Department belongs to selected Faculty

[ ] Duplicate Program validation works

[ ] Audit fields use authenticated Admin
```

## Read

```text
[ ] Program list works

[ ] Program detail works

[ ] Search works

[ ] Faculty filter works

[ ] Department filter works

[ ] Degree Level String filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works
```

## Update

```text
[ ] Program updates work

[ ] Degree Level remains String during update

[ ] Department can change

[ ] Faculty/Department validation works

[ ] updatedBy and updatedAt work
```

## Delete

```text
[ ] Delete is soft delete

[ ] Physical row remains

[ ] Deleted Program excluded from normal APIs

[ ] Department programCount updates
```

## Redis

```text
[ ] Redis caches Program reads where configured

[ ] Create invalidates related cache

[ ] Update invalidates related cache

[ ] Delete invalidates related cache

[ ] Department cache invalidated when programCount changes

[ ] PostgreSQL remains source of truth
```

## Architecture

```text
[ ] Feature-based architecture followed

[ ] Controller contains no business logic

[ ] Service handles business rules

[ ] Repository handles persistence

[ ] Mapper handles DTO/entity conversion

[ ] Exceptions remain feature-specific

[ ] Security uses existing RS256 authentication
```

---

# 88. Next Feature

After Programs & Majors CRUD is completed:

```text
Faculty
    ↓
Department
    ↓
Programs & Majors
    ↓
Courses
```

Recommended next step:

```text
Course Flyway Migration
        ↓
Course Normalization
        ↓
Program/Department Relationships
        ↓
Course CRUD Workflow
```