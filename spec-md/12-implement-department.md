# 13 - Department Management CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API workflow for the **Department Management** feature after the Department Flyway migrations have been completed successfully.

Feature location:

```text
Academic Management
    ↓
Departments
```

The Department feature uses the normalized database table:

```text
departments
```

and the existing relationship:

```text
departments.faculty_id
    ↓
faculties.id
```

This specification implements:

```text
Create Department
Get Department List
Get Department By ID
Update Department
Soft Delete Department
Search
Faculty Filter
Status Filter
Pagination
Sorting
Department Summary
Redis Cache Management
```

---

# 2. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Admin API path convention specification.
5. Read the Faculty feature specifications.
6. Read the Department Flyway migration specification.
7. Inspect the existing `departments` table migration.
8. Inspect the existing `faculties` implementation.
9. Inspect Authentication and JWT security implementation.
10. Inspect current Redis configuration.
11. Inspect global API response and exception conventions.
12. Follow the project's feature-based architecture.
13. Do NOT redesign the Department database table.
14. Do NOT modify previously applied Flyway migrations.
15. Re-check the project structure after implementation.
16. Run tests and Maven build before reporting completion.

---

# 3. API Base Path

All Admin Department APIs MUST use:

```text
/api/v1/admin/departments
```

Local development URL:

```text
http://localhost:8080/api/v1/admin/departments
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/departments")
public class DepartmentController {
}
```

---

# 4. Authentication

Every Department API requires an authenticated Admin user.

Header:

```http
Authorization: Bearer <access-token>
```

JWT must be verified using the existing RS256 security implementation.

Do NOT accept audit information such as:

```text
createdBy
updatedBy
```

from request bodies.

These must come from the authenticated user.

---

# 5. Authorization

Department Management may initially be available to:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Authorization must use the project's existing Spring Security role convention.

Example:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_ACADEMIC_ADMIN
```

---

# 6. Department Database Table

Use the existing normalized table:

```text
departments
```

Columns:

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT | Department primary key |
| `department_code` | VARCHAR(30) | Unique Department code |
| `department_name` | VARCHAR(255) | Department name |
| `faculty_id` | BIGINT | FK → faculties.id |
| `is_active` | BOOLEAN | Active/Inactive |
| `is_deleted` | BOOLEAN | Soft delete flag |
| `created_by` | BIGINT | FK → app_users.id |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_by` | BIGINT | FK → app_users.id |
| `updated_at` | TIMESTAMP | Last update timestamp |

Do NOT add API implementation fields to the table.

---

# 7. Derived Data

The UI displays:

```text
Programs
Courses
Students
```

These MUST NOT be stored in the `departments` table.

Current response may temporarily return:

```text
programCount = 0
courseCount = 0
studentCount = 0
```

until their respective features exist.

Later these values must be calculated from actual related tables.

---

# 8. Feature Package Structure

Use:

```text
feature/
└── department/
    ├── controller/
    │   └── DepartmentController.java
    │
    ├── dto/
    │   ├── DepartmentCreateRequest.java
    │   ├── DepartmentUpdateRequest.java
    │   ├── DepartmentResponse.java
    │   ├── DepartmentListResponse.java
    │   └── DepartmentSummaryResponse.java
    │
    ├── mapper/
    │   └── DepartmentMapper.java
    │
    ├── entity/
    │   └── Department.java
    │
    ├── service/
    │   ├── DepartmentService.java
    │   └── impl/
    │       └── DepartmentServiceImpl.java
    │
    ├── repository/
    │   └── DepartmentRepository.java
    │
    └── exception/
        ├── DepartmentNotFoundException.java
        ├── DepartmentCodeAlreadyExistsException.java
        ├── DepartmentNameAlreadyExistsException.java
        └── InvalidFacultyException.java
```

Do NOT create global Department:

```text
controller/
service/
repository/
entity/
```

packages.

---

# 9. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/departments

GET
/api/v1/admin/departments

GET
/api/v1/admin/departments/{id}

PUT
/api/v1/admin/departments/{id}

DELETE
/api/v1/admin/departments/{id}

GET
/api/v1/admin/departments/summary
```

---

# 10. Create Department API

## Endpoint

```http
POST /api/v1/admin/departments
```

Local:

```text
http://localhost:8080/api/v1/admin/departments
```

---

# 11. DepartmentCreateRequest

Fields:

| Field | Type | Required |
|---|---|---|
| `departmentCode` | String | Yes |
| `departmentName` | String | Yes |
| `facultyId` | Long | Yes |
| `isActive` | Boolean | No |

Example:

```json
{
  "departmentCode": "DEPT-CE",
  "departmentName": "Computer Engineering",
  "facultyId": 1,
  "isActive": true
}
```

If `isActive` is missing:

```text
TRUE
```

should be used.

---

# 12. Create Validation

## departmentCode

Rules:

```text
Required
Maximum 30 characters
Trim whitespace
Unique
```

Recommended format:

```text
DEPT-CE
DEPT-EE
DEPT-SE
```

Recommended pattern:

```text
^[A-Z0-9-]+$
```

---

# 13. departmentName Validation

Rules:

```text
Required
Maximum 255 characters
Trim whitespace
```

The same Faculty must not have duplicate Department names.

Validation combination:

```text
facultyId + departmentName
```

must be unique.

---

# 14. facultyId Validation

`facultyId` is required.

Before creating the Department:

```text
Find Faculty by ID
        ↓
Faculty exists?
        ↓
is_deleted = FALSE?
        ↓
is_active = TRUE?
```

Recommended rule:

Only active, non-deleted Faculties may receive new Departments.

If Faculty is not found:

```text
404 Not Found
```

If Faculty is inactive and the business rule disallows assignment:

```text
400 Bad Request
```

or project-standard business-rule response.

---

# 15. Create Duplicate Checks

Before insert, validate:

```text
department_code
```

is unique.

Also validate:

```text
faculty_id + department_name
```

is unique.

Example invalid case:

```text
Faculty of Engineering
├── Computer Engineering
└── Computer Engineering
```

must be rejected.

---

# 16. Create Workflow

```text
Receive DepartmentCreateRequest
        ↓
Validate request
        ↓
Get authenticated Admin ID
        ↓
Normalize departmentCode
        ↓
Trim departmentName
        ↓
Check duplicate departmentCode
        ↓
Load Faculty
        ↓
Verify Faculty is valid
        ↓
Check duplicate name inside Faculty
        ↓
Create Department entity
        ↓
isDeleted = FALSE
        ↓
createdBy = authenticated Admin
        ↓
createdAt = current timestamp
        ↓
Save Department
        ↓
Evict Department caches
        ↓
Evict related Faculty caches if required
        ↓
Return 201 Created
```

---

# 17. Create Entity Values

```text
id
→ generated by PostgreSQL

departmentCode
→ request

departmentName
→ request

faculty
→ Faculty identified by facultyId

isActive
→ request or TRUE

isDeleted
→ FALSE

createdBy
→ authenticated Admin

createdAt
→ current timestamp

updatedBy
→ NULL

updatedAt
→ NULL
```

---

# 18. Create Response

HTTP:

```text
201 Created
```

Example:

```json
{
  "success": true,
  "message": "Department created successfully",
  "data": {
    "id": 1,
    "departmentCode": "DEPT-CE",
    "departmentName": "Computer Engineering",
    "faculty": {
      "id": 1,
      "facultyCode": "FAC-ENG",
      "facultyNameEn": "Faculty of Engineering"
    },
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-24T22:00:00"
  }
}
```

---

# 19. Get Department List API

## Endpoint

```http
GET /api/v1/admin/departments
```

Support:

```text
search
facultyId
status
page
size
sort
```

Example:

```text
GET /api/v1/admin/departments?page=0&size=20
```

Faculty filter:

```text
GET /api/v1/admin/departments?facultyId=1
```

Status filter:

```text
GET /api/v1/admin/departments?status=ACTIVE
```

Search:

```text
GET /api/v1/admin/departments?search=engineering
```

Combined:

```text
GET /api/v1/admin/departments?search=engineering&facultyId=1&status=ACTIVE&page=0&size=20
```

---

# 20. Search Behavior

Search:

```text
department_code
department_name
```

Use case-insensitive search where practical.

Example:

```text
engineering
```

may match:

```text
Computer Engineering
Electrical Engineering
Mechanical Engineering
Software Engineering
```

---

# 21. Faculty Filter

The UI contains a Faculty dropdown.

Query parameter:

```text
facultyId
```

Example:

```text
GET /api/v1/admin/departments?facultyId=1
```

This should return only Departments belonging to that Faculty.

---

# 22. Status Filter

Supported values:

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

# 23. Soft Delete Filter

All normal queries MUST include:

```text
is_deleted = FALSE
```

Deleted Departments must not appear in:

```text
List
Search
Detail
Summary
Faculty counts
```

---

# 24. Pagination

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

# 25. Sorting

Recommended default:

```text
createdAt,desc
```

Allow safe fields such as:

```text
departmentCode
departmentName
createdAt
```

Example:

```text
GET /api/v1/admin/departments?sort=departmentName,asc
```

Do not allow unsafe arbitrary database-column injection.

---

# 26. DepartmentListResponse

Recommended fields:

```text
id
departmentCode
departmentName

facultyId
facultyCode
facultyNameEn

programCount
courseCount
studentCount

isActive
```

Example:

```json
{
  "id": 1,
  "departmentCode": "DEPT-CE",
  "departmentName": "Computer Engineering",
  "facultyId": 1,
  "facultyCode": "FAC-ENG",
  "facultyNameEn": "Faculty of Engineering",
  "programCount": 0,
  "courseCount": 0,
  "studentCount": 0,
  "isActive": true
}
```

---

# 27. Paginated List Response

Example:

```json
{
  "success": true,
  "message": "Departments retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "departmentCode": "DEPT-CE",
        "departmentName": "Computer Engineering",
        "facultyId": 1,
        "facultyCode": "FAC-ENG",
        "facultyNameEn": "Faculty of Engineering",
        "programCount": 0,
        "courseCount": 0,
        "studentCount": 0,
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

Use the project's existing pagination wrapper if already defined.

---

# 28. Get Department By ID API

## Endpoint

```http
GET /api/v1/admin/departments/{id}
```

Example:

```text
GET /api/v1/admin/departments/1
```

---

# 29. Get By ID Workflow

```text
Receive Department ID
        ↓
Find Department
        ↓
Require is_deleted = FALSE
        ↓
Found?
     /       \
   NO         YES
   ↓           ↓
 404      Load Faculty
              ↓
        Calculate available counts
              ↓
        Map response
              ↓
           200 OK
```

---

# 30. Department Detail Response

Example:

```json
{
  "success": true,
  "message": "Department retrieved successfully",
  "data": {
    "id": 1,
    "departmentCode": "DEPT-CE",
    "departmentName": "Computer Engineering",
    "faculty": {
      "id": 1,
      "facultyCode": "FAC-ENG",
      "facultyNameEn": "Faculty of Engineering"
    },
    "programCount": 0,
    "courseCount": 0,
    "studentCount": 0,
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-24T22:00:00",
    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 31. Department Not Found

If ID does not exist or:

```text
is_deleted = TRUE
```

return:

```text
404 Not Found
```

Use:

```text
DepartmentNotFoundException
```

---

# 32. Update Department API

## Endpoint

```http
PUT /api/v1/admin/departments/{id}
```

Example:

```text
PUT /api/v1/admin/departments/1
```

---

# 33. DepartmentUpdateRequest

Fields:

| Field | Type | Required |
|---|---|---|
| `departmentCode` | String | Yes |
| `departmentName` | String | Yes |
| `facultyId` | Long | Yes |
| `isActive` | Boolean | Yes |

Example:

```json
{
  "departmentCode": "DEPT-CE",
  "departmentName": "Computer Engineering",
  "facultyId": 1,
  "isActive": true
}
```

---

# 34. Update Rules

Allow updates to:

```text
departmentCode
departmentName
facultyId
isActive
```

Do NOT allow request updates to:

```text
id
isDeleted
createdBy
createdAt
updatedBy
updatedAt
```

---

# 35. Changing Faculty

The Admin may move a Department to another Faculty if business requirements allow it.

Example:

```text
Previous:
facultyId = 1

Updated:
facultyId = 2
```

Before change:

```text
Load new Faculty
        ↓
Verify exists
        ↓
Verify not deleted
        ↓
Verify active
```

Then validate:

```text
newFacultyId + departmentName
```

does not conflict with another Department.

---

# 36. Update Duplicate Checks

When checking Department code, exclude current Department ID.

Concept:

```text
departmentCode exists
AND id != currentId
```

For Department name:

```text
facultyId + departmentName exists
AND id != currentId
```

The existing record may retain its current code/name.

---

# 37. Update Workflow

```text
Receive ID + DepartmentUpdateRequest
        ↓
Find non-deleted Department
        ↓
Not found → 404
        ↓
Validate request
        ↓
Check Department Code duplicate excluding current ID
        ↓
Load requested Faculty
        ↓
Verify Faculty
        ↓
Check Department name duplicate within Faculty
        ↓
Get authenticated Admin ID
        ↓
Update allowed fields
        ↓
updatedBy = authenticated Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Department cache
        ↓
Evict list/summary caches
        ↓
Evict related Faculty caches if applicable
        ↓
Return updated response
```

---

# 38. Update Response

HTTP:

```text
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Department updated successfully",
  "data": {
    "id": 1,
    "departmentCode": "DEPT-CE",
    "departmentName": "Computer Engineering",
    "facultyId": 1,
    "facultyCode": "FAC-ENG",
    "facultyNameEn": "Faculty of Engineering",
    "isActive": true,
    "updatedBy": 1,
    "updatedAt": "2026-09-24T23:00:00"
  }
}
```

---

# 39. Activate / Deactivate

Changing status must use the Update API.

Active:

```text
is_active = TRUE
```

Inactive:

```text
is_active = FALSE
```

Do NOT treat inactive as deleted.

---

# 40. Inactive vs Deleted

Inactive:

```text
is_active = FALSE
is_deleted = FALSE
```

Department still exists.

Deleted:

```text
is_deleted = TRUE
```

Department is excluded from normal application operations.

---

# 41. Delete Department API

## Endpoint

```http
DELETE /api/v1/admin/departments/{id}
```

Example:

```text
DELETE /api/v1/admin/departments/1
```

---

# 42. Delete Type

Department deletion MUST use:

```text
SOFT DELETE
```

Do NOT run:

```sql
DELETE FROM departments
WHERE id = ?;
```

Instead:

```text
is_deleted = TRUE
```

---

# 43. Existing User Relationship

`app_users.department_id` now references:

```text
departments.id
```

Before deleting a Department, check whether active/non-deleted users are assigned to it.

Recommended behavior:

```text
Department
    ↓
Assigned Users?
   /       \
 YES        NO
  ↓          ↓
409       Continue
```

Do NOT silently leave active users pointing to a deleted Department.

---

# 44. Department Delete Restriction

If users are assigned:

Return:

```text
409 Conflict
```

Recommended message:

```text
Cannot delete department because users are assigned to it.
```

Do NOT automatically set every user's:

```text
department_id = NULL
```

unless a separate specification explicitly requests that behavior.

---

# 45. Future Program Restriction

When Program Management exists:

```text
Department
    ↓
Programs
```

Department deletion should also check active Programs.

Future:

```text
Active Programs exist
→ 409 Conflict
```

Do NOT implement Program checks before that module exists.

---

# 46. Delete Workflow

```text
Receive Department ID
        ↓
Find non-deleted Department
        ↓
Not found → 404
        ↓
Check assigned app_users
        ↓
Users assigned?
        ↓ YES
     Return 409
        ↓ NO
Get authenticated Admin
        ↓
isDeleted = TRUE
        ↓
updatedBy = Admin ID
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Department caches
        ↓
Evict related Faculty caches
        ↓
Return success
```

---

# 47. Delete Response

Recommended:

```text
204 No Content
```

or project-standard:

```json
{
  "success": true,
  "message": "Department deleted successfully"
}
```

Use the project's existing convention consistently.

---

# 48. Department Summary API

Add:

```http
GET /api/v1/admin/departments/summary
```

This can support Academic Management statistics.

Recommended response:

```json
{
  "success": true,
  "message": "Department summary retrieved successfully",
  "data": {
    "totalDepartments": 20,
    "activeDepartments": 18,
    "inactiveDepartments": 2
  }
}
```

---

# 49. Summary Calculation

Total:

```text
COUNT departments
WHERE is_deleted = FALSE
```

Active:

```text
COUNT departments
WHERE is_deleted = FALSE
AND is_active = TRUE
```

Inactive:

```text
COUNT departments
WHERE is_deleted = FALSE
AND is_active = FALSE
```

Do NOT store these totals in PostgreSQL.

---

# 50. Faculty Department Count

The existing Faculty UI displays:

```text
Departments
```

Now that Department Management exists, Faculty `departmentCount` must no longer be hard-coded to:

```text
0
```

It should be derived from:

```text
departments
```

where:

```text
faculty_id = faculty.id

AND

is_deleted = FALSE
```

---

# 51. Faculty Count Integration

When Faculty APIs return:

```text
departmentCount
```

calculate:

```sql
COUNT(*)
FROM departments
WHERE faculty_id = :facultyId
  AND is_deleted = FALSE
```

Do NOT store this count in `faculties`.

---

# 52. DepartmentResponse

Recommended:

```text
DepartmentResponse
```

Fields:

```text
id
departmentCode
departmentName

facultyId
facultyCode
facultyNameEn

programCount
courseCount
studentCount

isActive

createdBy
createdAt
updatedBy
updatedAt
```

Do NOT return:

```text
isDeleted
```

in normal API responses.

---

# 53. DepartmentSummaryResponse

Fields:

```text
totalDepartments
activeDepartments
inactiveDepartments
```

---

# 54. Department Entity

`Department.java` maps to:

```text
departments
```

Fields:

```text
id
departmentCode
departmentName
faculty
isActive
isDeleted
createdBy
createdAt
updatedBy
updatedAt
```

Recommended Faculty relationship:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "faculty_id", nullable = false)
private Faculty faculty;
```

Follow existing entity conventions.

---

# 55. Do Not Add Derived Entity Fields

Do NOT persist:

```text
programCount
courseCount
studentCount
facultyName
```

inside the Department entity as database columns.

They belong to response DTO calculation only.

---

# 56. DepartmentRepository Responsibilities

Repository handles:

```text
Find Department by ID and not deleted

Check Department Code duplicate

Check Faculty + Department Name duplicate

Search

Faculty filter

Status filter

Pagination

Summary counts
```

Example method concepts:

```text
findByIdAndIsDeletedFalse(...)

existsByDepartmentCodeAndIsDeletedFalse(...)

existsByFacultyIdAndDepartmentNameIgnoreCaseAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByIsActiveTrueAndIsDeletedFalse()

countByIsActiveFalseAndIsDeletedFalse()

countByFacultyIdAndIsDeletedFalse(...)
```

Use specifications/query methods as appropriate.

---

# 57. User Assignment Check

Before Department delete, check:

```text
app_users.department_id
```

Recommended repository behavior:

```text
exists active/non-deleted user by departmentId
```

Reuse the User feature repository/service where appropriate.

Prefer cross-feature service communication rather than putting User SQL logic directly inside the Controller.

---

# 58. DepartmentService

Recommended operations:

```java
DepartmentResponse create(
    DepartmentCreateRequest request
);

Page<DepartmentListResponse> getAll(
    String search,
    Long facultyId,
    String status,
    Pageable pageable
);

DepartmentResponse getById(
    Long id
);

DepartmentResponse update(
    Long id,
    DepartmentUpdateRequest request
);

void delete(
    Long id
);

DepartmentSummaryResponse getSummary();
```

Adapt to the project's existing API wrapper conventions.

---

# 59. DepartmentServiceImpl

Responsibilities:

```text
Business validation
Faculty validation
Duplicate validation
Current Admin lookup
Audit field assignment
Soft delete
User-assignment delete restriction
Transactions
Mapping
Redis cache management
```

Do NOT put this logic inside the Controller.

---

# 60. DepartmentController

Responsibilities only:

```text
Receive HTTP request
Validate DTO
Read path/query parameters
Create Pageable
Call DepartmentService
Return response
```

Do NOT:

```text
Query repositories directly
Set audit fields
Perform Faculty validation
Perform Redis operations
Implement business rules
```

inside the Controller.

---

# 61. Mapper

`DepartmentMapper` handles:

```text
DepartmentCreateRequest
        ↓
Department

Department
        ↓
DepartmentResponse

Department
        ↓
DepartmentListResponse
```

System-controlled values must not be mapped from client input.

---

# 62. Transactions

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

# 63. Redis Cache Management

Use Redis for Department read caching if caching is enabled in the project.

Recommended caches:

```text
department-cache

department-list-cache

department-summary-cache
```

---

# 64. Department Detail Cache

Recommended logical key:

```text
department:<id>
```

Example:

```text
department:10
```

Flow:

```text
GET Department 10
        ↓
Redis cache
    /       \
 HIT         MISS
  ↓            ↓
Return       PostgreSQL
                ↓
             Cache
                ↓
             Return
```

---

# 65. Department Summary Cache

Recommended key:

```text
department:summary
```

May cache:

```text
totalDepartments
activeDepartments
inactiveDepartments
```

---

# 66. Department List Cache

If list caching is implemented, cache key must account for:

```text
search
facultyId
status
page
size
sort
```

Example concept:

```text
department:list:<search>:<facultyId>:<status>:<page>:<size>:<sort>
```

Do NOT use one cache entry for every list variation.

If this becomes unnecessarily complex, caching detail and summary only is acceptable.

---

# 67. Cache Eviction on Create

After Create:

```text
Evict Department list caches
Evict Department summary cache
Evict Faculty detail/list cache if departmentCount is included
```

because Faculty Department counts have changed.

---

# 68. Cache Eviction on Update

After Update:

```text
Evict department:<id>

Evict Department list caches

Evict Department summary

Evict old Faculty cache

Evict new Faculty cache
```

The last two are especially important if:

```text
facultyId
```

changes.

---

# 69. Cache Eviction on Delete

After soft delete:

```text
Evict department:<id>

Evict Department list

Evict Department summary

Evict related Faculty cache
```

because the Faculty's derived Department count changes.

---

# 70. PostgreSQL Is Source of Truth

Redis must remain only a cache.

```text
PostgreSQL
→ Source of Truth

Redis
→ Read Cache
```

Do NOT depend on Redis for persistent Department data.

---

# 71. Exceptions

Create:

```text
DepartmentNotFoundException

DepartmentCodeAlreadyExistsException

DepartmentNameAlreadyExistsException

InvalidFacultyException

DepartmentInUseException
```

Feature exceptions belong in:

```text
feature/department/exception/
```

Global handling belongs in:

```text
exception/GlobalExceptionHandler.java
```

---

# 72. Error Cases

## Invalid Request

```text
400 Bad Request
```

## Invalid / Inactive Faculty

```text
400 Bad Request
```

or project-standard business-rule status.

## Unauthorized

```text
401 Unauthorized
```

## Forbidden

```text
403 Forbidden
```

## Department Not Found

```text
404 Not Found
```

## Duplicate Department Code

```text
409 Conflict
```

## Duplicate Department Name in Faculty

```text
409 Conflict
```

## Department Has Assigned Users

```text
409 Conflict
```

---

# 73. Error Example

```json
{
  "timestamp": "2026-09-24T22:30:00",
  "status": 409,
  "error": "Conflict",
  "message": "Department code already exists",
  "path": "/api/v1/admin/departments"
}
```

Use the existing global error format if already implemented.

---

# 74. Security Rules

All endpoints:

```text
/api/v1/admin/departments/**
```

must require authentication.

Example security:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

may manage Departments.

Use RS256 JWT authentication.

---

# 75. Audit Rules

Create:

```text
createdBy
→ current authenticated Admin

createdAt
→ current timestamp
```

Update:

```text
updatedBy
→ current authenticated Admin

updatedAt
→ current timestamp
```

Soft delete:

```text
updatedBy
→ current authenticated Admin

updatedAt
→ current timestamp
```

Never trust client-provided audit IDs.

---

# 76. Testing - Create

Test:

```text
[ ] Valid Department creation

[ ] Default isActive = TRUE

[ ] Invalid Faculty rejected

[ ] Deleted Faculty rejected

[ ] Inactive Faculty rejected if policy requires

[ ] Duplicate Department code rejected

[ ] Duplicate Department name in same Faculty rejected

[ ] Same Department name in different Faculty follows defined uniqueness rule

[ ] createdBy comes from JWT

[ ] isDeleted = FALSE
```

---

# 77. Testing - List

```text
[ ] Get all non-deleted Departments

[ ] Pagination works

[ ] Search by Department code works

[ ] Search by Department name works

[ ] Faculty filter works

[ ] Active filter works

[ ] Inactive filter works

[ ] Sorting works

[ ] Deleted Departments excluded
```

---

# 78. Testing - Detail

```text
[ ] Existing Department returns 200

[ ] Unknown ID returns 404

[ ] Soft-deleted Department returns 404

[ ] Faculty information returned correctly
```

---

# 79. Testing - Update

```text
[ ] Update Department code

[ ] Update Department name

[ ] Update Faculty

[ ] Update status

[ ] Duplicate code rejected

[ ] Duplicate Faculty/name pair rejected

[ ] Existing values can remain unchanged

[ ] updatedBy comes from JWT

[ ] updatedAt updated
```

---

# 80. Testing - Delete

```text
[ ] Soft delete works

[ ] Physical row remains in PostgreSQL

[ ] isDeleted becomes TRUE

[ ] updatedBy is set

[ ] updatedAt is set

[ ] Deleted Department excluded from normal list

[ ] Deleted Department returns 404 on detail

[ ] Department with assigned users cannot be deleted
```

---

# 81. Testing - Summary

```text
[ ] Total Department count correct

[ ] Active Department count correct

[ ] Inactive Department count correct

[ ] Deleted Departments excluded
```

---

# 82. Testing - Redis

```text
[ ] Get by ID caches Department

[ ] Update invalidates detail cache

[ ] Delete invalidates cache

[ ] Create invalidates list/summary

[ ] Faculty cache invalidated when Department count changes

[ ] Moving Department between Faculties invalidates both Faculty caches
```

---

# 83. Testing - Security

```text
[ ] Missing JWT returns 401

[ ] Invalid JWT returns 401

[ ] Expired JWT returns 401

[ ] Unauthorized role returns 403

[ ] Valid authorized Admin succeeds
```

---

# 84. Implementation Order

Implement exactly in this order:

```text
1. Verify Department Flyway migrations
        ↓
2. Verify app_users.department_id FK
        ↓
3. Create Department Entity
        ↓
4. Create Department Repository
        ↓
5. Create Department DTOs
        ↓
6. Create Department Mapper
        ↓
7. Create Department Exceptions
        ↓
8. Create DepartmentService
        ↓
9. Create DepartmentServiceImpl
        ↓
10. Implement Faculty validation
        ↓
11. Implement Create
        ↓
12. Implement List
        ↓
13. Implement Search
        ↓
14. Implement Faculty filter
        ↓
15. Implement Status filter
        ↓
16. Implement Get By ID
        ↓
17. Implement Update
        ↓
18. Implement User-assignment validation
        ↓
19. Implement Soft Delete
        ↓
20. Implement Summary
        ↓
21. Update Faculty departmentCount logic
        ↓
22. Add Redis Cache Management
        ↓
23. Implement DepartmentController
        ↓
24. Apply authorization
        ↓
25. Add automated tests
        ↓
26. Run Maven tests
        ↓
27. Run Maven package
        ↓
28. Re-check project structure
```

---

# 85. Complete Department Workflow

```text
                         Admin Web
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ↓                   ↓                   ↓
       Create              Read               Update
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                             ↓
                   DepartmentController
                             │
                             ↓
                    DepartmentService
                             │
               ┌─────────────┼─────────────┐
               │             │             │
               ↓             ↓             ↓
           Faculty        PostgreSQL      Redis
          Validation      Departments      Cache
               │
               ↓
           faculties
```

Delete:

```text
DELETE Department
       ↓
Department exists?
       ↓
Assigned users?
      /    \
    YES     NO
     ↓       ↓
   409     Soft Delete
             ↓
         Evict Cache
```

---

# 86. UI Page Load Workflow

When opening:

```text
Academic Management
    ↓
Departments
```

Frontend should request:

```text
GET /api/v1/admin/departments

GET /api/v1/admin/departments/summary
```

The Faculty filter dropdown can use the existing Faculty API.

Example:

```text
GET /api/v1/admin/faculties?status=ACTIVE
```

---

# 87. Add Department UI Workflow

```text
Click Add Department
        ↓
Load active Faculties
        ↓
User enters:
- Department Code
- Department Name
- Faculty
- Status
        ↓
POST /api/v1/admin/departments
        ↓
Success
        ↓
Close Drawer
        ↓
Refresh Department List
        ↓
Refresh Summary
        ↓
Faculty Department Count Updates
```

---

# 88. Edit Workflow

```text
Actions
    ↓
Edit
    ↓
GET /api/v1/admin/departments/{id}
    ↓
Populate Form
    ↓
Update Values
    ↓
PUT /api/v1/admin/departments/{id}
    ↓
Success
    ↓
Refresh List
```

---

# 89. Delete Workflow

```text
Actions
    ↓
Delete
    ↓
Confirmation Dialog
    ↓
DELETE /api/v1/admin/departments/{id}
    ↓
Check Assigned Users
      /             \
   Assigned        None
      ↓              ↓
   Show Error     Soft Delete
                      ↓
                 Refresh List
                      ↓
                 Refresh Summary
```

---

# 90. Final API List

```text
POST
/api/v1/admin/departments

GET
/api/v1/admin/departments

GET
/api/v1/admin/departments/{id}

PUT
/api/v1/admin/departments/{id}

DELETE
/api/v1/admin/departments/{id}

GET
/api/v1/admin/departments/summary
```

Local base:

```text
http://localhost:8080/api/v1/admin/departments
```

---

# 91. Acceptance Criteria

## Architecture

```text
[ ] Feature-based package structure followed

[ ] Department Controller contains no business logic

[ ] Department Service contains business logic

[ ] Repository handles persistence only

[ ] DTOs used at API boundary

[ ] Mapper handles entity/DTO mapping

[ ] Feature exceptions remain inside Department feature

[ ] Global exception handling remains global
```

## Create

```text
[ ] POST Department works

[ ] Faculty validation works

[ ] Duplicate code validation works

[ ] Duplicate Faculty/name validation works

[ ] Audit fields set from authentication
```

## Read

```text
[ ] List works

[ ] Detail works

[ ] Search works

[ ] Faculty filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works

[ ] Deleted Departments excluded
```

## Update

```text
[ ] Department can be updated

[ ] Faculty can be changed

[ ] Duplicate validation works

[ ] Audit update fields work
```

## Delete

```text
[ ] Delete uses soft delete

[ ] Physical database record remains

[ ] Assigned-user restriction works

[ ] Deleted Department disappears from normal queries
```

## Summary

```text
[ ] Total count works

[ ] Active count works

[ ] Inactive count works

[ ] Deleted Departments excluded
```

## Faculty Integration

```text
[ ] Department belongs to Faculty

[ ] Faculty response can derive departmentCount

[ ] Faculty count updates after Department create

[ ] Faculty count updates after Department delete

[ ] Faculty count updates when Department changes Faculty
```

## Redis

```text
[ ] Department detail can be cached

[ ] Cache invalidated after create

[ ] Cache invalidated after update

[ ] Cache invalidated after delete

[ ] Related Faculty cache invalidated when necessary

[ ] PostgreSQL remains source of truth
```

## Security

```text
[ ] APIs require RS256 JWT

[ ] createdBy comes from authenticated user

[ ] updatedBy comes from authenticated user

[ ] Client cannot control audit fields

[ ] Unauthorized access rejected
```

---

# 92. Next Feature

After Department CRUD is completed:

```text
Faculty
    ↓
Department
    ↓
Programs & Majors
```

Recommended next implementation sequence:

```text
Program / Major Flyway Migration
        ↓
department_id FK
        ↓
Program CRUD
        ↓
Department programCount becomes real derived data
```