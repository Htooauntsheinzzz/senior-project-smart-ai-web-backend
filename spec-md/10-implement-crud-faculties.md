# 11 - Faculty Management CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API workflow for the **Faculty Management** feature under:

```text
Academic Management
    ↓
Faculties
```

The Faculty database migration is already defined using the normalized table:

```text
faculties
```

This specification implements:

```text
Create Faculty
Get Faculty List
Get Faculty By ID
Update Faculty
Soft Delete Faculty
Faculty Summary
Search
Filter
Pagination
Sorting
```

---

# 2. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Admin API path convention specification.
5. Read the Faculty Flyway migration specification.
6. Inspect the existing `faculties` migration.
7. Inspect existing Authentication/security implementation.
8. Inspect current project structure.
9. Inspect existing response/error conventions.
10. Inspect existing Redis configuration if caching is already enabled.
11. Follow the feature-based project architecture.
12. Do not modify existing Authentication behavior.
13. Do not redesign the `faculties` table.
14. Re-check the project structure after implementation.
15. Run tests/build before reporting completion.

---

# 3. API Base Path

All Faculty Admin APIs MUST use:

```text
/api/v1/admin/faculties
```

Local development:

```text
http://localhost:8080/api/v1/admin/faculties
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/faculties")
public class FacultyController {
}
```

---

# 4. Authentication Requirement

All Faculty APIs are protected.

Required header:

```http
Authorization: Bearer <access-token>
```

The authenticated Admin information must come from the verified RS256 JWT.

Do NOT accept:

```text
createdBy
updatedBy
```

from the request body.

Those values must come from the authenticated Admin user.

---

# 5. Authorization

Recommended initial authorization:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

These roles may access Faculty Management.

Example:

```text
SUPER_ADMIN
→ Full Faculty CRUD

ADMIN
→ Faculty CRUD

ACADEMIC_ADMIN
→ Faculty CRUD
```

Actual permission rules should follow the project's current authorization implementation.

---

# 6. Faculty Database Table

The API uses:

```text
faculties
```

Attributes:

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT | Primary key |
| `faculty_code` | VARCHAR(30) | Unique Faculty code |
| `faculty_name_en` | VARCHAR(255) | Unique English name |
| `faculty_name_th` | VARCHAR(255) | Thai name |
| `is_active` | BOOLEAN | Active status |
| `is_deleted` | BOOLEAN | Soft delete flag |
| `created_by` | BIGINT | FK → app_users.id |
| `created_at` | TIMESTAMP | Created time |
| `updated_by` | BIGINT | FK → app_users.id |
| `updated_at` | TIMESTAMP | Updated time |

The API MUST NOT expect database fields for:

```text
icon
icon_key
department_count
student_count
```

---

# 7. Feature Package Structure

Use:

```text
feature/
└── faculty/
    ├── controller/
    │   └── FacultyController.java
    │
    ├── dto/
    │   ├── FacultyCreateRequest.java
    │   ├── FacultyUpdateRequest.java
    │   ├── FacultyResponse.java
    │   ├── FacultyListResponse.java
    │   └── FacultySummaryResponse.java
    │
    ├── mapper/
    │   └── FacultyMapper.java
    │
    ├── entity/
    │   └── Faculty.java
    │
    ├── service/
    │   ├── FacultyService.java
    │   └── impl/
    │       └── FacultyServiceImpl.java
    │
    ├── repository/
    │   └── FacultyRepository.java
    │
    └── exception/
        ├── FacultyNotFoundException.java
        ├── FacultyCodeAlreadyExistsException.java
        └── FacultyNameAlreadyExistsException.java
```

Do NOT create global:

```text
controller/
service/
repository/
entity/
```

packages for Faculty.

---

# 8. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/faculties

GET
/api/v1/admin/faculties

GET
/api/v1/admin/faculties/{id}

PUT
/api/v1/admin/faculties/{id}

DELETE
/api/v1/admin/faculties/{id}

GET
/api/v1/admin/faculties/summary
```

---

# 9. CRUD Workflow

```text
CREATE
POST /faculties
        ↓
Create Faculty


READ LIST
GET /faculties
        ↓
Search / Filter / Pagination


READ DETAIL
GET /faculties/{id}
        ↓
Faculty Detail


UPDATE
PUT /faculties/{id}
        ↓
Update Faculty


DELETE
DELETE /faculties/{id}
        ↓
Soft Delete


SUMMARY
GET /faculties/summary
        ↓
Total / Active / Inactive
```

---

# 10. Create Faculty API

## Endpoint

```http
POST /api/v1/admin/faculties
```

Local:

```text
http://localhost:8080/api/v1/admin/faculties
```

---

# 11. Create Faculty Request

DTO:

```text
FacultyCreateRequest
```

Fields:

| Field | Type | Required |
|---|---|---|
| `facultyCode` | String | Yes |
| `facultyNameEn` | String | Yes |
| `facultyNameTh` | String | No |
| `isActive` | Boolean | No |

Example:

```json
{
  "facultyCode": "FAC-ENG",
  "facultyNameEn": "Faculty of Engineering",
  "facultyNameTh": "คณะวิศวกรรมศาสตร์",
  "isActive": true
}
```

If `isActive` is not provided:

```text
TRUE
```

should be used.

---

# 12. Create Validation

## facultyCode

```text
Required
Maximum 30 characters
Trim whitespace
Unique
```

Recommended format:

```text
FAC-ENG
FAC-IT
FAC-SCI
```

Recommended validation:

```text
^[A-Z0-9-]+$
```

---

## facultyNameEn

```text
Required
Maximum 255 characters
Trim whitespace
Unique
```

---

## facultyNameTh

```text
Optional
Maximum 255 characters
Trim whitespace when provided
```

---

## isActive

```text
Optional
Default TRUE
```

---

# 13. Create Duplicate Validation

Before insert, check:

```text
faculty_code
```

and:

```text
faculty_name_en
```

Duplicates should be checked only against records according to the project's uniqueness policy.

Because the database has UNIQUE constraints, the service must perform validation before insert and also handle database constraint violations safely.

---

# 14. Create Workflow

```text
Receive FacultyCreateRequest
        ↓
Validate request
        ↓
Get authenticated Admin ID
        ↓
Normalize facultyCode
        ↓
Trim names
        ↓
Check facultyCode duplicate
        ↓
Check facultyNameEn duplicate
        ↓
Create Faculty entity
        ↓
isDeleted = FALSE
        ↓
createdBy = current Admin
        ↓
createdAt = current timestamp
        ↓
Save Faculty
        ↓
Evict Faculty caches
        ↓
Map FacultyResponse
        ↓
Return 201 Created
```

---

# 15. Create Entity Values

When creating:

```text
id
→ generated by PostgreSQL

facultyCode
→ request

facultyNameEn
→ request

facultyNameTh
→ request or NULL

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

# 16. Create Response

HTTP:

```text
201 Created
```

Example:

```json
{
  "success": true,
  "message": "Faculty created successfully",
  "data": {
    "id": 1,
    "facultyCode": "FAC-ENG",
    "facultyNameEn": "Faculty of Engineering",
    "facultyNameTh": "คณะวิศวกรรมศาสตร์",
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-24T20:00:00"
  }
}
```

---

# 17. Get Faculty List API

## Endpoint

```http
GET /api/v1/admin/faculties
```

Local:

```text
http://localhost:8080/api/v1/admin/faculties
```

---

# 18. List Query Parameters

Support:

```text
search
status
page
size
sort
```

Example:

```text
GET /api/v1/admin/faculties?page=0&size=20
```

Search:

```text
GET /api/v1/admin/faculties?search=engineering
```

Status:

```text
GET /api/v1/admin/faculties?status=ACTIVE
```

Combined:

```text
GET /api/v1/admin/faculties?search=FAC&page=0&size=20&sort=facultyNameEn,asc
```

---

# 19. Search Behavior

Search should match:

```text
faculty_code
faculty_name_en
faculty_name_th
```

Case-insensitive where practical.

Example search:

```text
eng
```

may return:

```text
FAC-ENG
Faculty of Engineering
```

---

# 20. Status Filter

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

Do not create a separate Faculty status table.

---

# 21. Soft Delete Filter

Every normal list query MUST include:

```text
is_deleted = FALSE
```

Soft-deleted Faculty records must not appear.

---

# 22. Pagination

Recommended defaults:

```text
page = 0
size = 20
```

Recommended maximum:

```text
size = 100
```

Avoid returning every Faculty record without pagination when the project grows.

---

# 23. Sorting

Recommended default:

```text
createdAt,desc
```

Supported examples:

```text
facultyCode,asc
facultyNameEn,asc
createdAt,desc
```

Validate sort fields.

Do not allow arbitrary unsafe SQL fields.

---

# 24. Faculty List Response

Recommended response item:

```json
{
  "id": 1,
  "facultyCode": "FAC-ENG",
  "facultyNameEn": "Faculty of Engineering",
  "facultyNameTh": "คณะวิศวกรรมศาสตร์",
  "departmentCount": 0,
  "studentCount": 0,
  "isActive": true,
  "createdAt": "2026-09-24T20:00:00"
}
```

Important:

```text
departmentCount
studentCount
```

are NOT stored in `faculties`.

---

# 25. Department Count Current Behavior

Department Management is not developed yet.

Therefore the current API may return:

```json
{
  "departmentCount": 0
}
```

as a temporary derived response value.

Once Department Management exists:

```text
departmentCount
```

must be calculated from actual Department records.

Do NOT add `department_count` to the Faculty database.

---

# 26. Student Count Current Behavior

Student/Program relationships are not yet complete.

Therefore the current API may return:

```json
{
  "studentCount": 0
}
```

temporarily.

Later:

```text
studentCount
```

must be derived from actual academic/student relationships.

Do NOT store it in the Faculty table.

---

# 27. Paginated List Response

Example:

```json
{
  "success": true,
  "message": "Faculties retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "facultyCode": "FAC-ENG",
        "facultyNameEn": "Faculty of Engineering",
        "facultyNameTh": "คณะวิศวกรรมศาสตร์",
        "departmentCount": 0,
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

Follow the project's existing pagination response format if one already exists.

---

# 28. Get Faculty By ID API

## Endpoint

```http
GET /api/v1/admin/faculties/{id}
```

Example:

```text
GET /api/v1/admin/faculties/1
```

---

# 29. Get By ID Workflow

```text
Receive Faculty ID
        ↓
Validate ID
        ↓
Find Faculty
        ↓
Require is_deleted = FALSE
        ↓
Faculty exists?
       / \
     NO   YES
     ↓     ↓
   404   Map response
            ↓
          200 OK
```

---

# 30. Get By ID Response

Example:

```json
{
  "success": true,
  "message": "Faculty retrieved successfully",
  "data": {
    "id": 1,
    "facultyCode": "FAC-ENG",
    "facultyNameEn": "Faculty of Engineering",
    "facultyNameTh": "คณะวิศวกรรมศาสตร์",
    "departmentCount": 0,
    "studentCount": 0,
    "isActive": true,
    "createdBy": 1,
    "createdAt": "2026-09-24T20:00:00",
    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 31. Faculty Not Found

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

Recommended exception:

```text
FacultyNotFoundException
```

---

# 32. Update Faculty API

## Endpoint

```http
PUT /api/v1/admin/faculties/{id}
```

Example:

```text
PUT /api/v1/admin/faculties/1
```

---

# 33. Update Request

DTO:

```text
FacultyUpdateRequest
```

Fields:

| Field | Type | Required |
|---|---|---|
| `facultyCode` | String | Yes |
| `facultyNameEn` | String | Yes |
| `facultyNameTh` | String | No |
| `isActive` | Boolean | Yes |

Example:

```json
{
  "facultyCode": "FAC-ENG",
  "facultyNameEn": "Faculty of Engineering",
  "facultyNameTh": "คณะวิศวกรรมศาสตร์",
  "isActive": true
}
```

---

# 34. Update Rules

The following may be updated:

```text
faculty_code
faculty_name_en
faculty_name_th
is_active
```

Do NOT update through request:

```text
id
is_deleted
created_by
created_at
updated_by
updated_at
```

These are system-controlled.

---

# 35. Update Duplicate Validation

When checking:

```text
faculty_code
```

exclude the current Faculty ID.

Concept:

```text
exists by facultyCode
AND id != currentId
```

Do the same for:

```text
faculty_name_en
```

This allows a Faculty to retain its existing code/name.

---

# 36. Update Workflow

```text
Receive Faculty ID + request
        ↓
Find non-deleted Faculty
        ↓
If not found → 404
        ↓
Validate fields
        ↓
Check code uniqueness excluding current ID
        ↓
Check English name uniqueness excluding current ID
        ↓
Get authenticated Admin ID
        ↓
Update allowed fields
        ↓
updatedBy = current Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Faculty cache
        ↓
Return updated response
```

---

# 37. Update Response

HTTP:

```text
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Faculty updated successfully",
  "data": {
    "id": 1,
    "facultyCode": "FAC-ENG",
    "facultyNameEn": "Faculty of Engineering",
    "facultyNameTh": "คณะวิศวกรรมศาสตร์",
    "isActive": true,
    "updatedBy": 1,
    "updatedAt": "2026-09-24T21:00:00"
  }
}
```

---

# 38. Activate / Deactivate Faculty

Faculty status changes can be performed through:

```text
PUT /api/v1/admin/faculties/{id}
```

Example deactivate:

```json
{
  "facultyCode": "FAC-LAW",
  "facultyNameEn": "Faculty of Law",
  "facultyNameTh": "คณะนิติศาสตร์",
  "isActive": false
}
```

Result:

```text
is_active = FALSE
```

Do not soft delete when simply deactivating.

---

# 39. Inactive vs Deleted

These states are different.

## Inactive

```text
is_active = FALSE
is_deleted = FALSE
```

Meaning:

```text
Faculty still exists
but is currently inactive
```

## Deleted

```text
is_deleted = TRUE
```

Meaning:

```text
Faculty is removed from normal application usage
```

Do not confuse these two states.

---

# 40. Delete Faculty API

## Endpoint

```http
DELETE /api/v1/admin/faculties/{id}
```

Example:

```text
DELETE /api/v1/admin/faculties/1
```

---

# 41. Delete Type

Faculty deletion MUST be:

```text
SOFT DELETE
```

Do NOT execute:

```sql
DELETE FROM faculties
WHERE id = ?;
```

Instead:

```text
is_deleted = TRUE
```

---

# 42. Delete Workflow

```text
Receive Faculty ID
        ↓
Find Faculty where is_deleted = FALSE
        ↓
Not found?
        ↓
404
        ↓
Check future relationship restrictions if applicable
        ↓
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
Evict Faculty cache
        ↓
Return success
```

---

# 43. Delete Response

Recommended:

```text
204 No Content
```

or if the project uses message responses:

```json
{
  "success": true,
  "message": "Faculty deleted successfully"
}
```

Use one consistent project-wide convention.

---

# 44. Future Delete Relationship Rule

Once Department Management exists:

```text
Faculty
    ↓
Departments
```

Faculty deletion may need to be prevented when active Departments exist.

Future example:

```text
Cannot delete Faculty because active Departments are assigned.
```

Do NOT invent Department validation now because the Department module does not yet exist.

---

# 45. Faculty Summary API

The Faculty UI displays:

```text
Total Faculties
Active Faculties
Total Departments
```

Add:

```http
GET /api/v1/admin/faculties/summary
```

---

# 46. Summary Current Response

Example:

```json
{
  "success": true,
  "message": "Faculty summary retrieved successfully",
  "data": {
    "totalFaculties": 7,
    "activeFaculties": 6,
    "inactiveFaculties": 1,
    "totalDepartments": 0
  }
}
```

---

# 47. Summary Calculations

## Total Faculties

```text
COUNT faculties
WHERE is_deleted = FALSE
```

## Active Faculties

```text
COUNT faculties
WHERE is_deleted = FALSE
AND is_active = TRUE
```

## Inactive Faculties

```text
COUNT faculties
WHERE is_deleted = FALSE
AND is_active = FALSE
```

## Total Departments

Current:

```text
0
```

Later calculated from the Department table.

Do NOT store any of these totals in the Faculty table.

---

# 48. FacultyResponse

Recommended:

```text
FacultyResponse
```

Fields:

```text
id
facultyCode
facultyNameEn
facultyNameTh
isActive
departmentCount
studentCount
createdBy
createdAt
updatedBy
updatedAt
```

Do NOT include:

```text
isDeleted
```

in normal client responses unless needed for an administrative audit endpoint.

---

# 49. FacultySummaryResponse

Recommended:

```text
FacultySummaryResponse
```

Fields:

```text
totalFaculties
activeFaculties
inactiveFaculties
totalDepartments
```

---

# 50. Faculty Entity

Map:

```text
Faculty.java
```

to:

```text
faculties
```

Fields:

```text
id
facultyCode
facultyNameEn
facultyNameTh
isActive
isDeleted
createdBy
createdAt
updatedBy
updatedAt
```

Do NOT create entity fields for:

```text
icon
departmentCount
studentCount
```

---

# 51. Repository Responsibilities

`FacultyRepository` handles:

```text
Find non-deleted Faculty by ID
Search Faculties
Pagination
Status filter
Duplicate Faculty Code check
Duplicate Faculty English Name check
Summary counts
```

Example method concepts:

```text
findByIdAndIsDeletedFalse(...)

existsByFacultyCodeAndIsDeletedFalse(...)

existsByFacultyNameEnAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByIsActiveTrueAndIsDeletedFalse()

countByIsActiveFalseAndIsDeletedFalse()
```

Use appropriate Spring Data naming or specifications.

---

# 52. Service Responsibilities

`FacultyService` should define the business operations.

Example:

```java
FacultyResponse create(FacultyCreateRequest request);

Page<FacultyListResponse> getAll(
    String search,
    String status,
    Pageable pageable
);

FacultyResponse getById(Long id);

FacultyResponse update(
    Long id,
    FacultyUpdateRequest request
);

void delete(Long id);

FacultySummaryResponse getSummary();
```

Adapt signatures to existing project conventions.

---

# 53. FacultyServiceImpl Responsibilities

Business logic belongs in:

```text
FacultyServiceImpl
```

Responsibilities:

```text
Validation
Duplicate checking
Current Admin retrieval
Audit field assignment
Soft delete
Transaction management
Cache management
Mapping
```

Do NOT place this logic in the Controller.

---

# 54. Controller Responsibilities

`FacultyController` should only:

```text
Receive HTTP request
Validate DTO
Read path/query parameters
Call FacultyService
Return HTTP response
```

Do NOT:

```text
Query Repository directly
Perform duplicate checks
Set audit timestamps
Implement soft-delete logic
Manage Redis directly
```

inside the Controller.

---

# 55. Mapper Responsibilities

`FacultyMapper` handles:

```text
FacultyCreateRequest → Faculty

Faculty → FacultyResponse

Faculty → FacultyListResponse
```

Do not map system-controlled fields from incoming request DTOs.

---

# 56. Transactions

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

# 57. Redis Cache Management

If Redis cache management is enabled in the project, Faculty read operations should use Redis caching.

Recommended cache names:

```text
faculty-cache
faculty-list-cache
faculty-summary-cache
```

---

# 58. Faculty Detail Cache

Recommended key:

```text
faculty:<id>
```

Example:

```text
faculty:1
```

Concept:

```text
GET Faculty 1
        ↓
Check Redis
       / \
   Found  Missing
     ↓       ↓
   Return   PostgreSQL
              ↓
           Cache result
              ↓
            Return
```

---

# 59. Faculty List Cache

Because lists can have search/filter/page parameters, cache keys must include those parameters if list caching is implemented.

Example concept:

```text
faculty:list:<search>:<status>:<page>:<size>:<sort>
```

Do NOT use one static cache key for every list query.

If list caching becomes unnecessarily complex, caching only individual Faculty details and summary is acceptable.

---

# 60. Faculty Summary Cache

Recommended key:

```text
faculty:summary
```

Cache:

```text
totalFaculties
activeFaculties
inactiveFaculties
totalDepartments
```

---

# 61. Cache Eviction

After:

```text
Create
Update
Delete
```

evict affected Faculty caches.

Create:

```text
Evict list cache
Evict summary cache
```

Update:

```text
Evict faculty:<id>
Evict list cache
Evict summary cache
```

Delete:

```text
Evict faculty:<id>
Evict list cache
Evict summary cache
```

---

# 62. Redis Is Not Source of Truth

PostgreSQL remains authoritative.

```text
PostgreSQL
→ Source of Truth

Redis
→ Cache
```

If Redis is unavailable, Faculty data must not be lost.

The application should be designed so database integrity does not depend on cache state.

---

# 63. Exception Classes

Create:

```text
FacultyNotFoundException

FacultyCodeAlreadyExistsException

FacultyNameAlreadyExistsException
```

Feature exceptions belong in:

```text
feature/faculty/exception/
```

Global handling remains in:

```text
exception/GlobalExceptionHandler.java
```

---

# 64. Error Responses

## Duplicate Faculty Code

HTTP:

```text
409 Conflict
```

Example:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Faculty code already exists"
}
```

---

## Duplicate Faculty Name

HTTP:

```text
409 Conflict
```

---

## Faculty Not Found

HTTP:

```text
404 Not Found
```

---

## Validation Error

HTTP:

```text
400 Bad Request
```

---

## Unauthorized

HTTP:

```text
401 Unauthorized
```

---

## Forbidden

HTTP:

```text
403 Forbidden
```

---

# 65. HTTP Status Summary

| Operation | Status |
|---|---|
| Create | `201 Created` |
| Get List | `200 OK` |
| Get By ID | `200 OK` |
| Update | `200 OK` |
| Delete | `204 No Content` or project-standard success response |
| Summary | `200 OK` |
| Validation Error | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden | `403 Forbidden` |
| Not Found | `404 Not Found` |
| Duplicate | `409 Conflict` |

---

# 66. Testing Requirements

## Create

```text
[ ] Create valid Faculty
[ ] Default isActive = TRUE
[ ] Duplicate code rejected
[ ] Duplicate English name rejected
[ ] createdBy from JWT
[ ] isDeleted = FALSE
```

## List

```text
[ ] List non-deleted Faculties
[ ] Pagination works
[ ] Search by code works
[ ] Search English name works
[ ] Search Thai name works
[ ] Active filter works
[ ] Inactive filter works
[ ] Sorting works
```

## Detail

```text
[ ] Existing Faculty returned
[ ] Deleted Faculty returns 404
[ ] Unknown ID returns 404
```

## Update

```text
[ ] Update name
[ ] Update Thai name
[ ] Update status
[ ] Duplicate code rejected
[ ] Duplicate name rejected
[ ] Current record may keep existing code/name
[ ] updatedBy comes from JWT
[ ] updatedAt changes
```

## Delete

```text
[ ] Delete performs soft delete
[ ] Record remains in PostgreSQL
[ ] isDeleted becomes TRUE
[ ] Deleted record disappears from list
[ ] Deleted record returns 404 through normal detail API
```

## Summary

```text
[ ] Total Faculty count correct
[ ] Active count correct
[ ] Inactive count correct
[ ] Deleted records excluded
```

## Security

```text
[ ] Missing JWT returns 401
[ ] Invalid JWT returns 401
[ ] Unauthorized role returns 403
```

## Cache

```text
[ ] Get by ID caches result
[ ] Update invalidates cache
[ ] Delete invalidates cache
[ ] Create invalidates list/summary
[ ] Cached data never overrides new PostgreSQL state after eviction
```

---

# 67. Implementation Order

Implement in this order:

```text
1. Verify Faculty Flyway migration
        ↓
2. Create Faculty Entity
        ↓
3. Create Faculty Repository
        ↓
4. Create DTOs
        ↓
5. Create Faculty Mapper
        ↓
6. Create Faculty exceptions
        ↓
7. Create FacultyService
        ↓
8. Create FacultyServiceImpl
        ↓
9. Implement Create
        ↓
10. Implement Get List
        ↓
11. Implement Get By ID
        ↓
12. Implement Update
        ↓
13. Implement Soft Delete
        ↓
14. Implement Summary
        ↓
15. Add Redis Cache Management
        ↓
16. Create FacultyController
        ↓
17. Apply authorization
        ↓
18. Add tests
        ↓
19. Run Maven tests
        ↓
20. Re-check project structure
```

---

# 68. Complete API Workflow

```text
ADMIN WEB
    │
    ├── POST /faculties
    │       ↓
    │   Create Faculty
    │
    ├── GET /faculties
    │       ↓
    │   Search / Filter / List
    │
    ├── GET /faculties/{id}
    │       ↓
    │   Faculty Detail
    │
    ├── PUT /faculties/{id}
    │       ↓
    │   Update Faculty
    │
    ├── DELETE /faculties/{id}
    │       ↓
    │   Soft Delete
    │
    └── GET /faculties/summary
            ↓
        Dashboard Counts

                 │
                 ↓
          FacultyService
                 │
         ┌───────┴────────┐
         ↓                ↓
      Redis           PostgreSQL
      Cache            faculties
```

---

# 69. UI Workflow Mapping

Faculty page:

```text
Open Faculties Page
        ↓
GET /api/v1/admin/faculties
        +
GET /api/v1/admin/faculties/summary
```

Add Faculty:

```text
Click "Add Faculty"
        ↓
Open Add Faculty Drawer
        ↓
Enter Faculty information
        ↓
POST /api/v1/admin/faculties
        ↓
Success
        ↓
Refresh list
        ↓
Refresh summary
```

Edit:

```text
Actions
    ↓
Edit
    ↓
GET /api/v1/admin/faculties/{id}
    ↓
Edit values
    ↓
PUT /api/v1/admin/faculties/{id}
    ↓
Refresh list
```

Delete:

```text
Actions
    ↓
Delete
    ↓
Confirm
    ↓
DELETE /api/v1/admin/faculties/{id}
    ↓
Soft delete
    ↓
Refresh list
    ↓
Refresh summary
```

---

# 70. Acceptance Criteria

## Create

```text
[ ] POST /api/v1/admin/faculties works
[ ] Duplicate Faculty Code rejected
[ ] Duplicate English name rejected
[ ] createdBy taken from authenticated Admin
```

## Read

```text
[ ] GET /api/v1/admin/faculties works
[ ] GET /api/v1/admin/faculties/{id} works
[ ] Search works
[ ] Filtering works
[ ] Pagination works
[ ] Sorting works
[ ] Deleted records excluded
```

## Update

```text
[ ] PUT /api/v1/admin/faculties/{id} works
[ ] Allowed fields update
[ ] Audit fields update
[ ] Duplicate validation works
```

## Delete

```text
[ ] DELETE /api/v1/admin/faculties/{id} works
[ ] Physical delete is not performed
[ ] is_deleted becomes TRUE
[ ] Deleted Faculty excluded from APIs
```

## Summary

```text
[ ] Total count works
[ ] Active count works
[ ] Inactive count works
[ ] Deleted Faculties excluded
```

## Normalization

```text
[ ] Icon is not stored
[ ] Department count is not stored
[ ] Student count is not stored
```

## Security

```text
[ ] Faculty API requires RS256 JWT
[ ] Current Admin is obtained from authentication
[ ] createdBy cannot be supplied by client
[ ] updatedBy cannot be supplied by client
```

## Redis

```text
[ ] Faculty cache uses Redis if caching is enabled
[ ] Cache is invalidated after create
[ ] Cache is invalidated after update
[ ] Cache is invalidated after delete
[ ] PostgreSQL remains source of truth
```

## Architecture

```text
[ ] Feature-based structure followed
[ ] Controller contains no business logic
[ ] Service contains business logic
[ ] Repository contains persistence logic
[ ] DTOs used at API boundary
[ ] Mapper used for entity/DTO conversion
[ ] Feature exceptions remain under Faculty feature
[ ] Global exception handling remains global
```

---

# 71. Final Faculty CRUD API

```text
POST
/api/v1/admin/faculties

GET
/api/v1/admin/faculties

GET
/api/v1/admin/faculties/{id}

PUT
/api/v1/admin/faculties/{id}

DELETE
/api/v1/admin/faculties/{id}

GET
/api/v1/admin/faculties/summary
```

Local:

  

---

# 72. Next Feature

After Faculty CRUD is complete:

```text
Faculty
    ↓
Department Management
    ↓
Department Flyway Migration
    ↓
faculty_id FK → faculties.id
    ↓
Department CRUD
    ↓
Faculty departmentCount becomes derived from real data
```