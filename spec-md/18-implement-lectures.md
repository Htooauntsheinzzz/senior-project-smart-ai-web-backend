# 19 - Lectures CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API workflow for:

```text
Academic Management
    ↓
Lectures
```

The Lectures Flyway migration is already completed and uses:

```text
lectures
```

This specification implements:

```text
Create Lecture
Get Lecture List
Get Lecture By ID
Update Lecture
Soft Delete Lecture
Search
Faculty Filter
Department Filter
Status Filter
Pagination
Sorting
Redis Cache Management
```

---

# 2. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Admin API path convention specification.
5. Read the shared Flyway migration standards.
6. Read the Lectures Flyway migration specification.
7. Inspect the existing `lectures` migration.
8. Inspect the existing Faculty implementation.
9. Inspect the existing Department implementation.
10. Inspect Authentication and RS256 security.
11. Inspect the current Redis configuration.
12. Inspect existing global response and exception conventions.
13. Follow the feature-based architecture.
14. Do NOT redesign the `lectures` table.
15. Do NOT modify previously applied Flyway migrations.
16. Re-check project structure after implementation.
17. Run tests and Maven build before completion.

---

# 3. API Base Path

Use:

```text
/api/v1/admin/lectures
```

Local development:

```text
http://localhost:8080/api/v1/admin/lectures
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/lectures")
public class LectureController {
}
```

---

# 4. Authentication

Every Lecture API requires:

```http
Authorization: Bearer <access-token>
```

Authentication must use the existing:

```text
JWT RS256
```

implementation.

Never accept:

```text
createdBy
updatedBy
```

from request JSON.

Audit values must come from the authenticated Admin user.

---

# 5. Authorization

Recommended roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Follow the project's existing Spring Security authority convention.

Example:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_ACADEMIC_ADMIN
```

---

# 6. Existing Database Table

Use:

```text
lectures
```

Columns:

| Column | Type |
|---|---|
| `id` | BIGINT |
| `lecture_name_th` | VARCHAR(255) |
| `lecture_name_en` | VARCHAR(255) |
| `lecture_nickname` | VARCHAR(100) |
| `faculty_id` | BIGINT |
| `department_id` | BIGINT |
| `status` | VARCHAR(30) |
| `is_deleted` | BOOLEAN |
| `created_by` | BIGINT |
| `created_at` | TIMESTAMP |
| `updated_by` | BIGINT |
| `updated_at` | TIMESTAMP |

Do NOT change this schema in the CRUD implementation.

---

# 7. Relationship Model

Each Lecture belongs to:

```text
one Faculty
one Department
```

Relationships:

```text
lectures.faculty_id
        ↓
faculties.id
```

```text
lectures.department_id
        ↓
departments.id
```

The application MUST verify:

```text
Department belongs to selected Faculty
```

before Create and Update.

---

# 8. Feature Package Structure

Use:

```text
feature/
└── lecture/
    ├── controller/
    │   └── LectureController.java
    │
    ├── dto/
    │   ├── LectureCreateRequest.java
    │   ├── LectureUpdateRequest.java
    │   ├── LectureResponse.java
    │   ├── LectureListResponse.java
    │   └── LectureSummaryResponse.java
    │
    ├── mapper/
    │   └── LectureMapper.java
    │
    ├── entity/
    │   └── Lecture.java
    │
    ├── service/
    │   ├── LectureService.java
    │   └── impl/
    │       └── LectureServiceImpl.java
    │
    ├── repository/
    │   └── LectureRepository.java
    │
    └── exception/
        ├── LectureNotFoundException.java
        ├── LectureAlreadyExistsException.java
        └── InvalidLectureDepartmentException.java
```

---

# 9. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/lectures

GET
/api/v1/admin/lectures

GET
/api/v1/admin/lectures/{id}

PUT
/api/v1/admin/lectures/{id}

DELETE
/api/v1/admin/lectures/{id}

GET
/api/v1/admin/lectures/summary
```

---

# 10. Create Lecture API

Endpoint:

```http
POST /api/v1/admin/lectures
```

---

# 11. LectureCreateRequest

Recommended DTO:

```java
public record LectureCreateRequest(
    String lectureNameTh,
    String lectureNameEn,
    String lectureNickname,
    Long facultyId,
    Long departmentId,
    String status
) {}
```

Fields:

| Field | Type | Required |
|---|---|---|
| `lectureNameTh` | String | Yes |
| `lectureNameEn` | String | Yes |
| `lectureNickname` | String | No |
| `facultyId` | Long | Yes |
| `departmentId` | Long | Yes |
| `status` | String | No |

---

# 12. Create Request Example

```json
{
  "lectureNameTh": "อาจารย์สมชาย ใจดี",
  "lectureNameEn": "Somchai Jaidee",
  "lectureNickname": "Aj. Somchai",
  "facultyId": 1,
  "departmentId": 3,
  "status": "ACTIVE"
}
```

If `status` is missing, use:

```text
ACTIVE
```

---

# 13. Thai Name Validation

Field:

```text
lectureNameTh
```

Rules:

```text
Required
Must not be blank
Maximum 255 characters
Trim whitespace
```

Recommended:

```java
@NotBlank
@Size(max = 255)
String lectureNameTh
```

---

# 14. English Name Validation

Field:

```text
lectureNameEn
```

Rules:

```text
Required
Must not be blank
Maximum 255 characters
Trim whitespace
```

Recommended:

```java
@NotBlank
@Size(max = 255)
String lectureNameEn
```

---

# 15. Nickname Validation

Field:

```text
lectureNickname
```

Rules:

```text
Optional
Maximum 100 characters
Trim whitespace when provided
```

Recommended:

```java
@Size(max = 100)
String lectureNickname
```

Blank nickname may be normalized to:

```text
NULL
```

if that matches project conventions.

---

# 16. Status Validation

`status` is stored as:

```text
VARCHAR(30)
```

Supported values:

```text
ACTIVE
INACTIVE
```

Default:

```text
ACTIVE
```

The backend may use String validation.

Do NOT require a separate database status table.

Recommended normalization:

```text
active
→ ACTIVE

Active
→ ACTIVE

inactive
→ INACTIVE
```

Reject unsupported values.

Example invalid:

```text
PENDING
DELETED
SUSPENDED
```

Return:

```text
400 Bad Request
```

---

# 17. Faculty Validation

Before Create:

```text
Load Faculty by facultyId
        ↓
Faculty exists?
        ↓
is_deleted = FALSE?
        ↓
is_active = TRUE?
```

Only valid, non-deleted Faculties should accept new Lecture records.

---

# 18. Department Validation

Before Create:

```text
Load Department by departmentId
        ↓
Department exists?
        ↓
is_deleted = FALSE?
        ↓
is_active = TRUE?
```

Then verify:

```text
department.faculty.id == facultyId
```

If not:

```text
400 Bad Request
```

Recommended message:

```text
Selected department does not belong to the selected faculty.
```

---

# 19. Duplicate Validation

The migration uses:

```text
department_id
+
lecture_name_th
+
lecture_name_en
```

as the duplicate combination.

Before Create, check whether the same non-deleted Lecture already exists in the same Department.

Example duplicate:

```text
Department:
Computer Engineering

Thai Name:
อาจารย์สมชาย ใจดี

English Name:
Somchai Jaidee
```

must not be created twice.

Return:

```text
409 Conflict
```

---

# 20. Create Workflow

```text
Receive LectureCreateRequest
        ↓
Validate request
        ↓
Trim names
        ↓
Normalize status
        ↓
Check duplicate
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
Get authenticated Admin ID
        ↓
Create Lecture
        ↓
isDeleted = FALSE
        ↓
status = request or ACTIVE
        ↓
createdBy = Admin
        ↓
createdAt = current timestamp
        ↓
Save
        ↓
Evict Lecture caches
        ↓
Return 201
```

---

# 21. Create Response

HTTP:

```text
201 Created
```

Example:

```json
{
  "success": true,
  "message": "Lecture created successfully",
  "data": {
    "id": 1,
    "lectureNameTh": "อาจารย์สมชาย ใจดี",
    "lectureNameEn": "Somchai Jaidee",
    "lectureNickname": "Aj. Somchai",
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
    "status": "ACTIVE",
    "createdBy": 1,
    "createdAt": "2026-09-28T18:00:00"
  }
}
```

---

# 22. Get Lecture List API

Endpoint:

```http
GET /api/v1/admin/lectures
```

Support:

```text
search
facultyId
departmentId
status
page
size
sort
```

Example:

```text
GET /api/v1/admin/lectures?page=0&size=20
```

Combined:

```text
GET /api/v1/admin/lectures?search=somchai&facultyId=1&departmentId=3&status=ACTIVE&page=0&size=20
```

---

# 23. Search Behavior

Search should include:

```text
lecture_name_th
lecture_name_en
lecture_nickname
```

Examples:

```text
Somchai
```

or:

```text
สมชาย
```

or:

```text
Aj. Somchai
```

Use case-insensitive search for English text where practical.

---

# 24. Faculty Filter

Example:

```text
GET /api/v1/admin/lectures?facultyId=1
```

Filter:

```text
lectures.faculty_id = 1
```

---

# 25. Department Filter

Example:

```text
GET /api/v1/admin/lectures?departmentId=3
```

Filter:

```text
lectures.department_id = 3
```

---

# 26. Faculty + Department Filter

When both are provided:

```text
facultyId=1
departmentId=3
```

the query must require both conditions.

The backend should not return a Department belonging to a different Faculty.

---

# 27. Status Filter

Examples:

```text
GET /api/v1/admin/lectures?status=ACTIVE
```

```text
GET /api/v1/admin/lectures?status=INACTIVE
```

Supported:

```text
ACTIVE
INACTIVE
```

Invalid status filter should return:

```text
400 Bad Request
```

---

# 28. Soft Delete Filter

All normal queries MUST include:

```text
is_deleted = FALSE
```

Deleted Lecture records must not appear in:

```text
List
Search
Filters
Get By ID
Summary
```

---

# 29. Pagination

Recommended defaults:

```text
page = 0
size = 20
```

Recommended maximum:

```text
size = 100
```

---

# 30. Sorting

Recommended default:

```text
createdAt,desc
```

Allowed sort fields may include:

```text
lectureNameTh
lectureNameEn
lectureNickname
status
createdAt
updatedAt
```

Example:

```text
GET /api/v1/admin/lectures?sort=lectureNameEn,asc
```

Validate allowed sort fields.

---

# 31. LectureListResponse

Recommended fields:

```text
id
lectureNameTh
lectureNameEn
lectureNickname

facultyId
facultyCode
facultyNameEn

departmentId
departmentCode
departmentName

status
createdAt
updatedAt
```

Example:

```json
{
  "id": 1,
  "lectureNameTh": "อาจารย์สมชาย ใจดี",
  "lectureNameEn": "Somchai Jaidee",
  "lectureNickname": "Aj. Somchai",
  "facultyId": 1,
  "facultyCode": "FAC-ENG",
  "facultyNameEn": "Faculty of Engineering",
  "departmentId": 3,
  "departmentCode": "DEPT-CE",
  "departmentName": "Computer Engineering",
  "status": "ACTIVE"
}
```

---

# 32. Paginated List Response

Example:

```json
{
  "success": true,
  "message": "Lectures retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "lectureNameTh": "อาจารย์สมชาย ใจดี",
        "lectureNameEn": "Somchai Jaidee",
        "lectureNickname": "Aj. Somchai",
        "facultyId": 1,
        "facultyCode": "FAC-ENG",
        "facultyNameEn": "Faculty of Engineering",
        "departmentId": 3,
        "departmentCode": "DEPT-CE",
        "departmentName": "Computer Engineering",
        "status": "ACTIVE"
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

Use the project's existing pagination wrapper if already implemented.

---

# 33. Get Lecture By ID

Endpoint:

```http
GET /api/v1/admin/lectures/{id}
```

Example:

```text
GET /api/v1/admin/lectures/1
```

---

# 34. Get By ID Workflow

```text
Receive Lecture ID
        ↓
Find where is_deleted = FALSE
        ↓
Not found?
        ↓
404
        ↓
Load Faculty
        ↓
Load Department
        ↓
Map response
        ↓
200 OK
```

---

# 35. Lecture Detail Response

Example:

```json
{
  "success": true,
  "message": "Lecture retrieved successfully",
  "data": {
    "id": 1,
    "lectureNameTh": "อาจารย์สมชาย ใจดี",
    "lectureNameEn": "Somchai Jaidee",
    "lectureNickname": "Aj. Somchai",
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
    "status": "ACTIVE",
    "createdBy": 1,
    "createdAt": "2026-09-28T18:00:00",
    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 36. Lecture Not Found

If:

```text
id does not exist
```

or:

```text
is_deleted = TRUE
```

return:

```text
404 Not Found
```

Use:

```text
LectureNotFoundException
```

---

# 37. Update Lecture API

Endpoint:

```http
PUT /api/v1/admin/lectures/{id}
```

Example:

```text
PUT /api/v1/admin/lectures/1
```

---

# 38. LectureUpdateRequest

Recommended:

```java
public record LectureUpdateRequest(
    String lectureNameTh,
    String lectureNameEn,
    String lectureNickname,
    Long facultyId,
    Long departmentId,
    String status
) {}
```

Fields:

| Field | Type | Required |
|---|---|---|
| `lectureNameTh` | String | Yes |
| `lectureNameEn` | String | Yes |
| `lectureNickname` | String | No |
| `facultyId` | Long | Yes |
| `departmentId` | Long | Yes |
| `status` | String | Yes |

---

# 39. Update Request Example

```json
{
  "lectureNameTh": "อาจารย์สมชาย ใจดี",
  "lectureNameEn": "Somchai Jaidee",
  "lectureNickname": "Somchai",
  "facultyId": 1,
  "departmentId": 3,
  "status": "INACTIVE"
}
```

---

# 40. Update Rules

Allow changes to:

```text
lectureNameTh
lectureNameEn
lectureNickname
facultyId
departmentId
status
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

# 41. Update Faculty/Department Validation

When updating:

```text
Load selected Faculty
        ↓
Validate Faculty
        ↓
Load selected Department
        ↓
Validate Department
        ↓
Verify Department belongs to Faculty
```

The Lecture may be moved to another Faculty/Department only when the selected relationship is valid.

---

# 42. Update Duplicate Validation

Check:

```text
departmentId
+
lectureNameTh
+
lectureNameEn
```

excluding the current Lecture ID.

Concept:

```text
duplicate exists
AND id != currentId
```

The current Lecture may retain its existing names and Department.

---

# 43. Update Workflow

```text
Receive Lecture ID + request
        ↓
Find non-deleted Lecture
        ↓
Not found → 404
        ↓
Validate request
        ↓
Trim names
        ↓
Normalize status
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
Check duplicate excluding current ID
        ↓
Get authenticated Admin
        ↓
Update fields
        ↓
updatedBy = Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Lecture caches
        ↓
Return 200
```

---

# 44. Update Response

Example:

```json
{
  "success": true,
  "message": "Lecture updated successfully",
  "data": {
    "id": 1,
    "lectureNameTh": "อาจารย์สมชาย ใจดี",
    "lectureNameEn": "Somchai Jaidee",
    "lectureNickname": "Somchai",
    "facultyId": 1,
    "departmentId": 3,
    "status": "INACTIVE",
    "updatedBy": 1,
    "updatedAt": "2026-09-28T19:00:00"
  }
}
```

---

# 45. Status Change

Lecture status changes through:

```text
PUT /api/v1/admin/lectures/{id}
```

Active:

```json
{
  "status": "ACTIVE"
}
```

Inactive:

```json
{
  "status": "INACTIVE"
}
```

Do not confuse:

```text
INACTIVE
```

with:

```text
isDeleted = TRUE
```

---

# 46. Status vs Deleted

Inactive:

```text
status = INACTIVE
is_deleted = FALSE
```

Meaning:

```text
Lecture still exists but is not currently active.
```

Deleted:

```text
is_deleted = TRUE
```

Meaning:

```text
Lecture is excluded from normal application use.
```

---

# 47. Delete Lecture API

Endpoint:

```http
DELETE /api/v1/admin/lectures/{id}
```

Example:

```text
DELETE /api/v1/admin/lectures/1
```

---

# 48. Delete Type

Lecture deletion MUST use:

```text
SOFT DELETE
```

Do NOT execute:

```sql
DELETE FROM lectures
WHERE id = ?;
```

Instead:

```text
is_deleted = TRUE
```

---

# 49. Delete Workflow

```text
Receive Lecture ID
        ↓
Find non-deleted Lecture
        ↓
Not found → 404
        ↓
Check future dependent relationships if implemented
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
Evict Lecture caches
        ↓
Return success
```

---

# 50. Future Delete Restrictions

When Lecture assignments exist, such as:

```text
Lecture
    ↓
Course
Section
Schedule
Teaching Assignment
```

the application may need to prevent deletion while active dependencies exist.

Future behavior may return:

```text
409 Conflict
```

Do NOT implement nonexistent dependency checks in the current Lecture CRUD task.

---

# 51. Lecture Summary API

Endpoint:

```http
GET /api/v1/admin/lectures/summary
```

Recommended response:

```json
{
  "success": true,
  "message": "Lecture summary retrieved successfully",
  "data": {
    "totalLectures": 30,
    "activeLectures": 26,
    "inactiveLectures": 4
  }
}
```

---

# 52. Summary Calculations

Total:

```text
COUNT lectures
WHERE is_deleted = FALSE
```

Active:

```text
COUNT lectures
WHERE is_deleted = FALSE
AND status = 'ACTIVE'
```

Inactive:

```text
COUNT lectures
WHERE is_deleted = FALSE
AND status = 'INACTIVE'
```

Do NOT store these values in PostgreSQL.

---

# 53. Lecture Entity

Create:

```text
feature/lecture/entity/Lecture.java
```

Recommended fields:

```java
Long id;

String lectureNameTh;

String lectureNameEn;

String lectureNickname;

Faculty faculty;

Department department;

String status;

Boolean isDeleted;

AppUser createdBy;

LocalDateTime createdAt;

AppUser updatedBy;

LocalDateTime updatedAt;
```

---

# 54. Entity Relationships

Faculty:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "faculty_id", nullable = false)
private Faculty faculty;
```

Department:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "department_id", nullable = false)
private Department department;
```

Use existing project entity conventions if different.

---

# 55. Status Entity Mapping

Because the database uses:

```text
VARCHAR(30)
```

the entity may use:

```java
String status;
```

to match the schema directly.

Recommended:

```java
@Column(name = "status", nullable = false, length = 30)
private String status;
```

If the project later chooses a Java enum, it must still persist as a String and remain compatible with the existing database constraint.

Do NOT create a status entity/table.

---

# 56. LectureResponse

Recommended fields:

```text
id
lectureNameTh
lectureNameEn
lectureNickname

facultyId
facultyCode
facultyNameEn

departmentId
departmentCode
departmentName

status

createdBy
createdAt
updatedBy
updatedAt
```

Do NOT expose:

```text
isDeleted
```

in normal responses.

---

# 57. LectureSummaryResponse

Recommended:

```text
totalLectures
activeLectures
inactiveLectures
```

---

# 58. LectureRepository Responsibilities

Repository handles:

```text
Find non-deleted Lecture by ID

Duplicate check

Search

Faculty filter

Department filter

Status filter

Pagination

Sorting

Summary counts
```

Method concepts:

```text
findByIdAndIsDeletedFalse(...)

existsByDepartmentIdAndLectureNameThAndLectureNameEnAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByStatusAndIsDeletedFalse(...)
```

Use Spring Data Specification or equivalent for dynamic filters.

---

# 59. Dynamic Filtering

Recommended query behavior:

```text
isDeleted = FALSE
AND optional search
AND optional facultyId
AND optional departmentId
AND optional status
```

Do not create many unnecessary repository methods for every possible filter combination if Specification/Criteria is already used in the project.

---

# 60. LectureService

Recommended interface:

```java
LectureResponse create(
    LectureCreateRequest request
);

Page<LectureListResponse> getAll(
    String search,
    Long facultyId,
    Long departmentId,
    String status,
    Pageable pageable
);

LectureResponse getById(
    Long id
);

LectureResponse update(
    Long id,
    LectureUpdateRequest request
);

void delete(
    Long id
);

LectureSummaryResponse getSummary();
```

Adapt return wrappers to project conventions.

---

# 61. LectureServiceImpl Responsibilities

Business logic belongs in:

```text
LectureServiceImpl
```

Responsibilities:

```text
Input validation
Name normalization
Status normalization
Faculty validation
Department validation
Faculty/Department consistency
Duplicate validation
Current Admin retrieval
Audit fields
Soft delete
Transactions
Mapping
Redis cache management
```

---

# 62. LectureController Responsibilities

Controller should only:

```text
Receive HTTP request
Validate request DTO
Read query/path parameters
Create Pageable
Call LectureService
Return response
```

Do NOT:

```text
Query Repository directly
Perform relationship validation
Set audit fields
Implement duplicate checks
Handle Redis directly
Implement soft delete
```

inside Controller.

---

# 63. LectureMapper

Responsibilities:

```text
LectureCreateRequest
        ↓
Lecture

Lecture
        ↓
LectureResponse

Lecture
        ↓
LectureListResponse
```

Do not map system-controlled values from incoming requests.

---

# 64. Transactions

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

where appropriate.

---

# 65. Redis Cache Management

Recommended cache names:

```text
lecture-cache
lecture-list-cache
lecture-summary-cache
```

PostgreSQL remains the source of truth.

---

# 66. Lecture Detail Cache

Recommended key:

```text
lecture:<id>
```

Example:

```text
lecture:1
```

Flow:

```text
GET Lecture 1
        ↓
Check Redis
    /       \
 HIT         MISS
  ↓            ↓
Return      PostgreSQL
                ↓
              Cache
                ↓
              Return
```

---

# 67. Lecture List Cache

If list caching is implemented, include:

```text
search
facultyId
departmentId
status
page
size
sort
```

Example:

```text
lecture:list:<search>:<facultyId>:<departmentId>:<status>:<page>:<size>:<sort>
```

If list caching becomes unnecessarily complex, detail and summary caching is sufficient.

---

# 68. Summary Cache

Recommended key:

```text
lecture:summary
```

May cache:

```text
totalLectures
activeLectures
inactiveLectures
```

---

# 69. Cache Eviction - Create

After Create:

```text
Evict Lecture list cache

Evict Lecture summary cache
```

---

# 70. Cache Eviction - Update

After Update:

```text
Evict lecture:<id>

Evict Lecture list cache

Evict Lecture summary cache
```

---

# 71. Cache Eviction - Delete

After soft delete:

```text
Evict lecture:<id>

Evict Lecture list cache

Evict Lecture summary cache
```

Deleted Lecture data must not remain visible from stale Redis entries.

---

# 72. Exceptions

Create:

```text
LectureNotFoundException

LectureAlreadyExistsException

InvalidLectureDepartmentException
```

Location:

```text
feature/lecture/exception/
```

Use the existing global:

```text
GlobalExceptionHandler
```

for API error mapping.

---

# 73. Error Cases

## Validation

```text
400 Bad Request
```

## Invalid Faculty

```text
400 Bad Request
```

or project-standard business-rule response.

## Invalid Department

```text
400 Bad Request
```

## Department Does Not Belong to Faculty

```text
400 Bad Request
```

## Unauthorized

```text
401 Unauthorized
```

## Forbidden

```text
403 Forbidden
```

## Lecture Not Found

```text
404 Not Found
```

## Duplicate Lecture

```text
409 Conflict
```

---

# 74. Duplicate Error Example

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Lecture already exists in this department"
}
```

Use the project's existing global error structure if different.

---

# 75. Security Testing

```text
[ ] Missing JWT returns 401

[ ] Invalid JWT returns 401

[ ] Expired JWT returns 401

[ ] Unauthorized role returns 403

[ ] SUPER_ADMIN can manage Lectures

[ ] ADMIN can manage Lectures if allowed

[ ] ACADEMIC_ADMIN can manage Lectures if allowed
```

---

# 76. Create Testing

```text
[ ] Valid Lecture creation succeeds

[ ] Thai name required

[ ] English name required

[ ] Nickname optional

[ ] ACTIVE accepted

[ ] INACTIVE accepted

[ ] Invalid status rejected

[ ] Faculty required

[ ] Department required

[ ] Invalid Faculty rejected

[ ] Invalid Department rejected

[ ] Department/Faculty mismatch rejected

[ ] Duplicate Lecture rejected

[ ] createdBy comes from authenticated Admin

[ ] isDeleted defaults FALSE
```

---

# 77. List Testing

```text
[ ] Non-deleted Lectures returned

[ ] Search by Thai name works

[ ] Search by English name works

[ ] Search by nickname works

[ ] Faculty filter works

[ ] Department filter works

[ ] Status filter works

[ ] Combined filters work

[ ] Pagination works

[ ] Sorting works

[ ] Deleted records excluded
```

---

# 78. Detail Testing

```text
[ ] Existing Lecture returns 200

[ ] Unknown ID returns 404

[ ] Deleted Lecture returns 404

[ ] Faculty data returned

[ ] Department data returned
```

---

# 79. Update Testing

```text
[ ] Thai name updates

[ ] English name updates

[ ] Nickname updates

[ ] Faculty can change

[ ] Department can change

[ ] Relationship consistency validated

[ ] Status can change

[ ] Duplicate validation excludes current record

[ ] updatedBy comes from JWT

[ ] updatedAt updates

[ ] createdBy remains unchanged

[ ] createdAt remains unchanged
```

---

# 80. Delete Testing

```text
[ ] Delete performs soft delete

[ ] Physical row remains

[ ] isDeleted becomes TRUE

[ ] updatedBy is set

[ ] updatedAt is set

[ ] Deleted Lecture excluded from list

[ ] Deleted Lecture returns 404 on detail
```

---

# 81. Summary Testing

```text
[ ] totalLectures correct

[ ] activeLectures correct

[ ] inactiveLectures correct

[ ] Deleted Lecture records excluded
```

---

# 82. Redis Testing

```text
[ ] Detail can be cached

[ ] Create invalidates list/summary cache

[ ] Update invalidates detail/list/summary cache

[ ] Delete invalidates detail/list/summary cache

[ ] Deleted Lecture not returned from stale cache

[ ] PostgreSQL remains source of truth
```

---

# 83. Implementation Order

Implement in this order:

```text
1. Verify Lectures Flyway migration
        ↓
2. Create Lecture Entity
        ↓
3. Create Lecture Repository
        ↓
4. Create LectureCreateRequest
        ↓
5. Create LectureUpdateRequest
        ↓
6. Create LectureResponse
        ↓
7. Create LectureListResponse
        ↓
8. Create LectureSummaryResponse
        ↓
9. Create LectureMapper
        ↓
10. Create Lecture Exceptions
        ↓
11. Create LectureService
        ↓
12. Create LectureServiceImpl
        ↓
13. Implement Faculty validation
        ↓
14. Implement Department validation
        ↓
15. Implement Faculty/Department consistency
        ↓
16. Implement Status validation
        ↓
17. Implement Create
        ↓
18. Implement List
        ↓
19. Implement Search
        ↓
20. Implement Faculty filter
        ↓
21. Implement Department filter
        ↓
22. Implement Status filter
        ↓
23. Implement Pagination
        ↓
24. Implement Sorting
        ↓
25. Implement Get By ID
        ↓
26. Implement Update
        ↓
27. Implement Soft Delete
        ↓
28. Implement Summary
        ↓
29. Add Redis Cache Management
        ↓
30. Create LectureController
        ↓
31. Apply authorization
        ↓
32. Add automated tests
        ↓
33. Run Maven tests
        ↓
34. Run Maven package
        ↓
35. Re-check project structure
```

---

# 84. Complete CRUD Workflow

```text
                         Admin Web
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ↓                    ↓                    ↓
      Create               Read                Update
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                             ↓
                     LectureController
                             │
                             ↓
                      LectureService
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ↓                ↓                ↓
         Faculty         Department       PostgreSQL
        Validation       Validation        lectures
                             │
                             ↓
                           Redis
                           Cache
```

---

# 85. UI Create Workflow

```text
Click Add Lecture
        ↓
Enter Thai Name
        ↓
Enter English Name
        ↓
Enter Nickname
        ↓
Select Faculty
        ↓
Load Departments for Faculty
        ↓
Select Department
        ↓
Select Status
        ↓
POST /api/v1/admin/lectures
        ↓
Create
        ↓
Refresh List
```

---

# 86. UI Faculty and Department Workflow

Recommended:

```text
Select Faculty
        ↓
GET active Departments by Faculty
        ↓
Populate Department dropdown
        ↓
Select Department
```

The backend must still validate the relationship even if the frontend already filters Departments correctly.

Never rely only on frontend validation.

---

# 87. UI List Workflow

Page load:

```text
GET /api/v1/admin/lectures
```

Optional:

```text
GET /api/v1/admin/lectures/summary
```

Display:

```text
Thai Name
English Name
Nickname
Faculty
Department
Status
Actions
```

---

# 88. Edit Workflow

```text
Actions
    ↓
Edit
    ↓
GET /api/v1/admin/lectures/{id}
    ↓
Populate form
    ↓
Edit values
    ↓
PUT /api/v1/admin/lectures/{id}
    ↓
Success
    ↓
Refresh list
```

---

# 89. Delete Workflow

```text
Actions
    ↓
Delete
    ↓
Confirmation
    ↓
DELETE /api/v1/admin/lectures/{id}
    ↓
Soft Delete
    ↓
Refresh List
    ↓
Refresh Summary
```

---

# 90. Final API List

```text
POST
/api/v1/admin/lectures

GET
/api/v1/admin/lectures

GET
/api/v1/admin/lectures/{id}

PUT
/api/v1/admin/lectures/{id}

DELETE
/api/v1/admin/lectures/{id}

GET
/api/v1/admin/lectures/summary
```

Local:

```text
http://localhost:8080/api/v1/admin/lectures
```

---

# 91. Acceptance Criteria

## Architecture

```text
[ ] feature/lecture structure used

[ ] Controller contains no business logic

[ ] Service handles business logic

[ ] Repository handles persistence

[ ] DTOs used at API boundary

[ ] Mapper handles conversions

[ ] Feature-specific exceptions remain under lecture feature
```

## Create

```text
[ ] POST /api/v1/admin/lectures works

[ ] Thai name validation works

[ ] English name validation works

[ ] Nickname is optional

[ ] Faculty validation works

[ ] Department validation works

[ ] Department belongs to selected Faculty

[ ] Status validation works

[ ] Duplicate validation works

[ ] Audit fields come from authenticated Admin
```

## Read

```text
[ ] GET list works

[ ] GET by ID works

[ ] Search works

[ ] Faculty filter works

[ ] Department filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works

[ ] Deleted records excluded
```

## Update

```text
[ ] PUT works

[ ] Names can update

[ ] Nickname can update

[ ] Faculty can update

[ ] Department can update

[ ] Faculty/Department consistency enforced

[ ] Status can update

[ ] updatedBy and updatedAt work
```

## Delete

```text
[ ] DELETE works

[ ] Delete is soft delete

[ ] Physical record remains

[ ] isDeleted becomes TRUE

[ ] Deleted Lecture excluded from normal APIs
```

## Summary

```text
[ ] Total Lecture count works

[ ] Active Lecture count works

[ ] Inactive Lecture count works

[ ] Deleted Lectures excluded
```

## Redis

```text
[ ] Detail cache supported

[ ] Create invalidates list/summary

[ ] Update invalidates detail/list/summary

[ ] Delete invalidates detail/list/summary

[ ] PostgreSQL remains source of truth
```

## Security

```text
[ ] APIs require RS256 JWT

[ ] Authorized Admin roles can access CRUD

[ ] Unauthorized access returns 403

[ ] Client cannot provide createdBy

[ ] Client cannot provide updatedBy
```

---

# 92. Next Step

After Lectures CRUD is complete:

```text
Lectures
    ↓
Course / Teaching Assignment
```

A future feature may connect:

```text
Program
    ↓
Course
    ↓
Lecture
    ↓
Semester
    ↓
Class Schedule
```

through separate normalized Flyway migrations and CRUD specifications.