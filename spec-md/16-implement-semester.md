# 17 - Semester CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API workflow for the:

```text
Academic Management
    ↓
Semester
```

The Semester Flyway migration is already completed and uses:

```text
semesters
```

This specification implements:

```text
Create Semester
Get Semester List
Get Semester By ID
Update Semester
Soft Delete Semester
Search
Pagination
Sorting
Redis Cache Management
```

This specification does NOT add:

```text
is_active
semester_code
academic_year_id
start_date
end_date
school_year
```

---

# 2. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Admin API path convention specification.
5. Read the shared Flyway migration standards.
6. Read the Semester Flyway migration specification.
7. Inspect the existing `semesters` migration.
8. Inspect existing project package structure.
9. Inspect Authentication and RS256 security.
10. Inspect the existing Redis configuration.
11. Inspect existing global response/error conventions.
12. Follow the feature-based project structure.
13. Do NOT modify the Semester table design.
14. Do NOT add `is_active`.
15. Do NOT add Academic Year relationships in this task.
16. Re-check the project structure after implementation.
17. Run tests and Maven build before completion.

---

# 3. API Base Path

All Admin Semester APIs MUST use:

```text
/api/v1/admin/semesters
```

Local development:

```text
http://localhost:8080/api/v1/admin/semesters
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/semesters")
public class SemesterController {
}
```

---

# 4. Authentication

All Semester APIs require authentication.

Required header:

```http
Authorization: Bearer <access-token>
```

Authentication must use the existing:

```text
JWT RS256
```

implementation.

Audit information such as:

```text
createdBy
updatedBy
```

must come from the currently authenticated Admin.

Never accept audit IDs from the client.

---

# 5. Authorization

Recommended Admin roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

These roles may manage Semester records.

Use the project's existing Spring Security authority convention.

Example:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_ACADEMIC_ADMIN
```

---

# 6. Existing Semester Table

The CRUD implementation MUST use the existing normalized table:

```text
semesters
```

Attributes:

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT | Primary key |
| `semester_name_th` | VARCHAR(150) | Thai Semester name |
| `semester_name_en` | VARCHAR(150) | English Semester name |
| `is_deleted` | BOOLEAN | Soft-delete flag |
| `created_by` | BIGINT | FK → `app_users.id` |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_by` | BIGINT | FK → `app_users.id` |
| `updated_at` | TIMESTAMP | Last update timestamp |

Do NOT redesign this table.

---

# 7. Feature Package Structure

Use:

```text
feature/
└── semester/
    ├── controller/
    │   └── SemesterController.java
    │
    ├── dto/
    │   ├── SemesterCreateRequest.java
    │   ├── SemesterUpdateRequest.java
    │   ├── SemesterResponse.java
    │   └── SemesterListResponse.java
    │
    ├── mapper/
    │   └── SemesterMapper.java
    │
    ├── entity/
    │   └── Semester.java
    │
    ├── service/
    │   ├── SemesterService.java
    │   └── impl/
    │       └── SemesterServiceImpl.java
    │
    ├── repository/
    │   └── SemesterRepository.java
    │
    └── exception/
        ├── SemesterNotFoundException.java
        └── SemesterAlreadyExistsException.java
```

Do NOT create global:

```text
controller/
service/
repository/
entity/
```

packages for the Semester feature.

---

# 8. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/semesters

GET
/api/v1/admin/semesters

GET
/api/v1/admin/semesters/{id}

PUT
/api/v1/admin/semesters/{id}

DELETE
/api/v1/admin/semesters/{id}
```

Optional summary API may be added only if required by the Admin UI later.

---

# 9. Create Semester API

Endpoint:

```http
POST /api/v1/admin/semesters
```

Local:

```text
http://localhost:8080/api/v1/admin/semesters
```

---

# 10. SemesterCreateRequest

Recommended DTO:

```java
public record SemesterCreateRequest(

    String semesterNameTh,

    String semesterNameEn

) {}
```

Request fields:

| Field | Type | Required |
|---|---|---|
| `semesterNameTh` | String | Yes |
| `semesterNameEn` | String | Yes |

---

# 11. Create Request Example

```json
{
  "semesterNameTh": "ภาคการศึกษาที่ 1",
  "semesterNameEn": "Semester 1"
}
```

Another example:

```json
{
  "semesterNameTh": "ภาคฤดูร้อน",
  "semesterNameEn": "Summer Semester"
}
```

---

# 12. Semester Thai Name Validation

Field:

```text
semesterNameTh
```

Rules:

```text
Required
Must not be blank
Maximum 150 characters
Trim whitespace
```

Recommended validation:

```java
@NotBlank
@Size(max = 150)
String semesterNameTh
```

---

# 13. Semester English Name Validation

Field:

```text
semesterNameEn
```

Rules:

```text
Required
Must not be blank
Maximum 150 characters
Trim whitespace
```

Recommended:

```java
@NotBlank
@Size(max = 150)
String semesterNameEn
```

---

# 14. Duplicate Validation

The Semester migration uses uniqueness on:

```text
semester_name_th
+
semester_name_en
```

Therefore, before creating a Semester, check whether the same Thai and English name combination already exists.

Example duplicate:

```text
semesterNameTh:
ภาคการศึกษาที่ 1

semesterNameEn:
Semester 1
```

must not be inserted twice.

Return:

```text
409 Conflict
```

Recommended exception:

```text
SemesterAlreadyExistsException
```

---

# 15. Name Normalization

Before duplicate checking:

```text
Trim semesterNameTh
Trim semesterNameEn
```

Example:

```text
" Semester 1 "
```

becomes:

```text
"Semester 1"
```

Case-insensitive duplicate validation for the English name is recommended where practical.

Do not silently change the actual semantic content.

---

# 16. Create Workflow

```text
Receive SemesterCreateRequest
        ↓
Validate request
        ↓
Trim Thai name
        ↓
Trim English name
        ↓
Check duplicate Thai + English combination
        ↓
Get authenticated Admin ID
        ↓
Create Semester entity
        ↓
isDeleted = FALSE
        ↓
createdBy = authenticated Admin
        ↓
createdAt = current timestamp
        ↓
updatedBy = NULL
        ↓
updatedAt = NULL
        ↓
Save Semester
        ↓
Evict Semester caches
        ↓
Return 201 Created
```

---

# 17. Create Entity Values

```text
id
→ generated by PostgreSQL

semesterNameTh
→ request.semesterNameTh

semesterNameEn
→ request.semesterNameEn

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
  "message": "Semester created successfully",
  "data": {
    "id": 1,
    "semesterNameTh": "ภาคการศึกษาที่ 1",
    "semesterNameEn": "Semester 1",
    "createdBy": 1,
    "createdAt": "2026-09-27T03:30:00"
  }
}
```

Do NOT return:

```text
isDeleted
```

in the normal response.

---

# 19. Get Semester List API

Endpoint:

```http
GET /api/v1/admin/semesters
```

Support:

```text
search
page
size
sort
```

Example:

```text
GET /api/v1/admin/semesters?page=0&size=20
```

Search:

```text
GET /api/v1/admin/semesters?search=semester
```

---

# 20. Search Behavior

Search must support:

```text
semester_name_th
semester_name_en
```

Example:

```text
Semester 1
```

may match:

```text
semester_name_en
```

Thai search:

```text
ภาคการศึกษาที่ 1
```

must match:

```text
semester_name_th
```

Use case-insensitive matching for English text where practical.

---

# 21. Soft Delete Filter

Every normal Semester query MUST include:

```text
is_deleted = FALSE
```

Soft-deleted records must not appear in:

```text
List
Search
Get By ID
Normal application selections
```

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

Example:

```text
GET /api/v1/admin/semesters?page=0&size=20
```

---

# 23. Sorting

Recommended default:

```text
createdAt,desc
```

Supported fields may include:

```text
semesterNameTh
semesterNameEn
createdAt
updatedAt
```

Examples:

```text
GET /api/v1/admin/semesters?sort=semesterNameEn,asc
```

Do not allow arbitrary unsafe database property names.

---

# 24. SemesterListResponse

Recommended fields:

```text
id
semesterNameTh
semesterNameEn
createdAt
updatedAt
```

Example:

```json
{
  "id": 1,
  "semesterNameTh": "ภาคการศึกษาที่ 1",
  "semesterNameEn": "Semester 1",
  "createdAt": "2026-09-27T03:30:00",
  "updatedAt": null
}
```

---

# 25. Paginated Semester Response

Example:

```json
{
  "success": true,
  "message": "Semesters retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "semesterNameTh": "ภาคการศึกษาที่ 1",
        "semesterNameEn": "Semester 1",
        "createdAt": "2026-09-27T03:30:00",
        "updatedAt": null
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

Use the existing project pagination format if one already exists.

---

# 26. Get Semester By ID API

Endpoint:

```http
GET /api/v1/admin/semesters/{id}
```

Example:

```text
GET /api/v1/admin/semesters/1
```

---

# 27. Get By ID Workflow

```text
Receive Semester ID
        ↓
Find Semester
        ↓
Require is_deleted = FALSE
        ↓
Semester found?
       / \
     NO   YES
     ↓     ↓
   404   Map response
            ↓
          200 OK
```

---

# 28. Semester Detail Response

Example:

```json
{
  "success": true,
  "message": "Semester retrieved successfully",
  "data": {
    "id": 1,
    "semesterNameTh": "ภาคการศึกษาที่ 1",
    "semesterNameEn": "Semester 1",
    "createdBy": 1,
    "createdAt": "2026-09-27T03:30:00",
    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 29. Semester Not Found

If:

```text
ID does not exist
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
SemesterNotFoundException
```

---

# 30. Update Semester API

Endpoint:

```http
PUT /api/v1/admin/semesters/{id}
```

Example:

```text
PUT /api/v1/admin/semesters/1
```

---

# 31. SemesterUpdateRequest

Recommended:

```java
public record SemesterUpdateRequest(

    String semesterNameTh,

    String semesterNameEn

) {}
```

Fields:

| Field | Type | Required |
|---|---|---|
| `semesterNameTh` | String | Yes |
| `semesterNameEn` | String | Yes |

---

# 32. Update Request Example

```json
{
  "semesterNameTh": "ภาคการศึกษาที่หนึ่ง",
  "semesterNameEn": "First Semester"
}
```

---

# 33. Update Rules

Allow changes to:

```text
semesterNameTh
semesterNameEn
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

These fields are system controlled.

---

# 34. Update Duplicate Validation

When updating, check:

```text
semesterNameTh + semesterNameEn
```

for duplicate records.

Exclude the current Semester ID.

Concept:

```text
same Thai name
AND
same English name
AND
id != currentId
```

The current Semester must be allowed to retain its existing names.

---

# 35. Update Workflow

```text
Receive Semester ID + Update Request
        ↓
Find non-deleted Semester
        ↓
Not found → 404
        ↓
Validate request
        ↓
Trim Thai name
        ↓
Trim English name
        ↓
Check duplicate excluding current ID
        ↓
Get authenticated Admin
        ↓
Update names
        ↓
updatedBy = authenticated Admin
        ↓
updatedAt = current timestamp
        ↓
Save Semester
        ↓
Evict Semester caches
        ↓
Return 200 OK
```

---

# 36. Update Response

Example:

```json
{
  "success": true,
  "message": "Semester updated successfully",
  "data": {
    "id": 1,
    "semesterNameTh": "ภาคการศึกษาที่หนึ่ง",
    "semesterNameEn": "First Semester",
    "updatedBy": 1,
    "updatedAt": "2026-09-27T04:00:00"
  }
}
```

---

# 37. Delete Semester API

Endpoint:

```http
DELETE /api/v1/admin/semesters/{id}
```

Example:

```text
DELETE /api/v1/admin/semesters/1
```

---

# 38. Delete Type

Semester deletion MUST be:

```text
SOFT DELETE
```

Do NOT execute:

```sql
DELETE FROM semesters
WHERE id = ?;
```

Instead:

```text
is_deleted = TRUE
```

---

# 39. Delete Workflow

```text
Receive Semester ID
        ↓
Find Semester where is_deleted = FALSE
        ↓
Not found → 404
        ↓
Check future dependent relationships if implemented
        ↓
Get authenticated Admin
        ↓
isDeleted = TRUE
        ↓
updatedBy = authenticated Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Semester caches
        ↓
Return success
```

---

# 40. Delete Response

Recommended:

```text
204 No Content
```

or use the project's existing response convention:

```json
{
  "success": true,
  "message": "Semester deleted successfully"
}
```

Use one convention consistently.

---

# 41. Future Delete Restriction

A future Academic Year / schedule structure may reference Semester.

Example future:

```text
Academic Year
      ↓
Semester
      ↓
Course Sections
```

When those relationships exist, the delete operation may require dependency checks.

Do NOT implement nonexistent relationship checks in the current Semester CRUD feature.

---

# 42. Semester Entity

Create:

```text
feature/semester/entity/Semester.java
```

Map to:

```text
semesters
```

Fields:

```java
Long id;

String semesterNameTh;

String semesterNameEn;

Boolean isDeleted;

AppUser createdBy;

LocalDateTime createdAt;

AppUser updatedBy;

LocalDateTime updatedAt;
```

---

# 43. Semester Entity Mapping

Concept:

```java
@Entity
@Table(name = "semesters")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "semester_name_th",
        nullable = false,
        length = 150
    )
    private String semesterNameTh;

    @Column(
        name = "semester_name_en",
        nullable = false,
        length = 150
    )
    private String semesterNameEn;

    @Column(
        name = "is_deleted",
        nullable = false
    )
    private Boolean isDeleted;

    // audit fields
}
```

Follow the project's actual entity style.

---

# 44. SemesterRepository Responsibilities

Repository handles:

```text
Find non-deleted Semester by ID

Duplicate Thai/English name check

Search by Thai name

Search by English name

Pagination

Sorting
```

Recommended method concepts:

```text
findByIdAndIsDeletedFalse(...)

existsBySemesterNameThAndSemesterNameEnAndIsDeletedFalse(...)

countByIsDeletedFalse()
```

For search + pagination, use:

```text
Specification
JPQL
Spring Data query
```

according to the existing project convention.

---

# 45. SemesterService

Recommended interface:

```java
SemesterResponse create(
    SemesterCreateRequest request
);

Page<SemesterListResponse> getAll(
    String search,
    Pageable pageable
);

SemesterResponse getById(
    Long id
);

SemesterResponse update(
    Long id,
    SemesterUpdateRequest request
);

void delete(
    Long id
);
```

Adapt return wrappers to existing project conventions.

---

# 46. SemesterServiceImpl Responsibilities

Business logic belongs in:

```text
SemesterServiceImpl
```

Responsibilities:

```text
Input normalization
Validation
Duplicate checking
Current authenticated Admin lookup
Audit field management
Soft delete
Transactions
Mapping
Redis cache management
```

Do NOT place this logic in the Controller.

---

# 47. SemesterController Responsibilities

Controller should only:

```text
Receive HTTP request
Validate DTO
Read path/query parameters
Create Pageable
Call SemesterService
Return response
```

Do NOT:

```text
Query repository directly
Set audit IDs manually
Handle Redis directly
Perform duplicate business logic
Implement soft-delete logic
```

inside the Controller.

---

# 48. SemesterMapper

Create:

```text
feature/semester/mapper/SemesterMapper.java
```

Responsibilities:

```text
SemesterCreateRequest
        ↓
Semester

Semester
        ↓
SemesterResponse

Semester
        ↓
SemesterListResponse
```

Do NOT map:

```text
createdBy
createdAt
updatedBy
updatedAt
isDeleted
```

from client requests.

---

# 49. Transactions

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
```

where appropriate.

---

# 50. Redis Cache Management

Use Redis for Semester read caching if cache management is enabled in the project.

Recommended cache names:

```text
semester-cache
semester-list-cache
```

PostgreSQL remains the source of truth.

---

# 51. Semester Detail Cache

Recommended logical key:

```text
semester:<id>
```

Example:

```text
semester:1
```

Flow:

```text
GET Semester
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

# 52. Semester List Cache

If list caching is implemented, include:

```text
search
page
size
sort
```

in the cache key.

Concept:

```text
semester:list:<search>:<page>:<size>:<sort>
```

Do NOT use one static cache key for all list requests.

Caching only individual Semester records is acceptable if list-cache complexity is unnecessary.

---

# 53. Cache Eviction - Create

After successful Create:

```text
Evict Semester list caches
```

There is no existing detail cache for the newly generated ID unless explicitly stored.

---

# 54. Cache Eviction - Update

After Update:

```text
Evict semester:<id>

Evict Semester list caches
```

---

# 55. Cache Eviction - Delete

After soft delete:

```text
Evict semester:<id>

Evict Semester list caches
```

Deleted data must not remain available through stale cache entries.

---

# 56. Redis Is Not Permanent Storage

Correct:

```text
PostgreSQL
→ Source of Truth

Redis
→ Cache
```

Do NOT store the Semester as Redis-only data.

---

# 57. Exceptions

Create:

```text
SemesterNotFoundException

SemesterAlreadyExistsException
```

Location:

```text
feature/semester/exception/
```

Global exception handling remains in:

```text
exception/GlobalExceptionHandler.java
```

---

# 58. Error Responses

## Validation Error

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

## Semester Not Found

```text
404 Not Found
```

## Duplicate Semester

```text
409 Conflict
```

---

# 59. Duplicate Error Example

```json
{
  "timestamp": "2026-09-27T03:45:00",
  "status": 409,
  "error": "Conflict",
  "message": "Semester already exists",
  "path": "/api/v1/admin/semesters"
}
```

Follow existing global error response format if already implemented.

---

# 60. HTTP Status Summary

| Operation | HTTP Status |
|---|---|
| Create | `201 Created` |
| List | `200 OK` |
| Get By ID | `200 OK` |
| Update | `200 OK` |
| Delete | `204 No Content` or project-standard response |
| Invalid Request | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden | `403 Forbidden` |
| Not Found | `404 Not Found` |
| Duplicate | `409 Conflict` |

---

# 61. Audit Rules

Create:

```text
createdBy
→ authenticated Admin

createdAt
→ current timestamp

updatedBy
→ NULL

updatedAt
→ NULL
```

Update:

```text
updatedBy
→ authenticated Admin

updatedAt
→ current timestamp
```

Delete:

```text
isDeleted
→ TRUE

updatedBy
→ authenticated Admin

updatedAt
→ current timestamp
```

Never trust client-provided audit values.

---

# 62. No Active Status

The Semester table does NOT contain:

```text
is_active
```

Therefore Semester CRUD must NOT implement:

```text
Activate Semester
Deactivate Semester
ACTIVE filter
INACTIVE filter
```

Current state model:

```text
Existing Semester
→ is_deleted = FALSE

Deleted Semester
→ is_deleted = TRUE
```

Do NOT add an active/inactive feature unless a later specification explicitly requires it.

---

# 63. No Academic Year Yet

Do NOT implement:

```text
academicYearId
schoolYearId
yearId
```

inside:

```text
SemesterCreateRequest
SemesterUpdateRequest
SemesterResponse
Semester entity
Semester repository
```

The Academic Year relationship will be implemented separately later.

---

# 64. Create Testing

Test:

```text
[ ] Valid Semester creates successfully

[ ] Thai name required

[ ] English name required

[ ] Blank Thai name rejected

[ ] Blank English name rejected

[ ] Thai name > 150 rejected

[ ] English name > 150 rejected

[ ] Duplicate Thai + English combination rejected

[ ] isDeleted defaults FALSE

[ ] createdBy comes from JWT

[ ] createdAt is created
```

---

# 65. List Testing

```text
[ ] List returns non-deleted Semesters

[ ] Thai search works

[ ] English search works

[ ] Pagination works

[ ] Sorting works

[ ] Deleted Semester excluded
```

---

# 66. Get By ID Testing

```text
[ ] Existing Semester returns 200

[ ] Unknown Semester returns 404

[ ] Deleted Semester returns 404

[ ] Response contains Thai and English names
```

---

# 67. Update Testing

```text
[ ] Thai name updates

[ ] English name updates

[ ] Duplicate combination rejected

[ ] Current record can keep existing names

[ ] updatedBy comes from authenticated Admin

[ ] updatedAt updates

[ ] createdBy does not change

[ ] createdAt does not change
```

---

# 68. Delete Testing

```text
[ ] Delete performs soft delete

[ ] Physical row remains in PostgreSQL

[ ] isDeleted becomes TRUE

[ ] updatedBy is set

[ ] updatedAt is set

[ ] Deleted Semester excluded from list

[ ] Deleted Semester returns 404 from detail API
```

---

# 69. Redis Testing

```text
[ ] Get By ID caches Semester when configured

[ ] Update evicts Semester detail cache

[ ] Update invalidates Semester list cache

[ ] Delete removes stale detail cache

[ ] Delete invalidates Semester list cache

[ ] Create invalidates Semester list cache

[ ] PostgreSQL remains source of truth
```

---

# 70. Security Testing

```text
[ ] Missing access token returns 401

[ ] Invalid access token returns 401

[ ] Expired access token returns 401

[ ] Unauthorized role returns 403

[ ] Authorized Admin can create Semester

[ ] Authorized Admin can read Semester

[ ] Authorized Admin can update Semester

[ ] Authorized Admin can delete Semester
```

---

# 71. Implementation Order

Implement in this order:

```text
1. Verify Semester Flyway migration
        ↓
2. Create Semester Entity
        ↓
3. Create Semester Repository
        ↓
4. Create SemesterCreateRequest
        ↓
5. Create SemesterUpdateRequest
        ↓
6. Create SemesterResponse
        ↓
7. Create SemesterListResponse
        ↓
8. Create SemesterMapper
        ↓
9. Create Semester Exceptions
        ↓
10. Create SemesterService
        ↓
11. Create SemesterServiceImpl
        ↓
12. Implement Create
        ↓
13. Implement List
        ↓
14. Implement Search
        ↓
15. Implement Pagination
        ↓
16. Implement Sorting
        ↓
17. Implement Get By ID
        ↓
18. Implement Update
        ↓
19. Implement Soft Delete
        ↓
20. Add Redis Cache Management
        ↓
21. Create SemesterController
        ↓
22. Apply Role Authorization
        ↓
23. Add Automated Tests
        ↓
24. Run Maven Tests
        ↓
25. Run Maven Package
        ↓
26. Re-check Project Structure
```

---

# 72. Complete Semester CRUD Workflow

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
                    SemesterController
                             │
                             ↓
                     SemesterService
                             │
                 ┌───────────┴───────────┐
                 │                       │
                 ↓                       ↓
              Redis                  PostgreSQL
              Cache                  semesters
```

Delete:

```text
DELETE /semesters/{id}
        ↓
Find Semester
        ↓
is_deleted = TRUE
        ↓
Set updatedBy
        ↓
Set updatedAt
        ↓
Save PostgreSQL
        ↓
Evict Redis
```

---

# 73. Admin UI Create Workflow

```text
Click Add Semester
        ↓
Enter Thai Semester Name
        ↓
Enter English Semester Name
        ↓
Submit
        ↓
POST /api/v1/admin/semesters
        ↓
Create Semester
        ↓
Refresh Semester List
```

Recommended form:

```text
Semester Name (Thai) *
[                                ]

Semester Name (English) *
[                                ]
```

---

# 74. Admin UI List Workflow

Page load:

```text
GET /api/v1/admin/semesters
```

Display:

```text
Thai Semester Name
English Semester Name
Created Date
Updated Date
Actions
```

Actions:

```text
Edit
Delete
```

---

# 75. Edit Workflow

```text
Actions
    ↓
Edit
    ↓
GET /api/v1/admin/semesters/{id}
    ↓
Populate Form
    ↓
Edit Thai / English Name
    ↓
PUT /api/v1/admin/semesters/{id}
    ↓
Success
    ↓
Refresh List
```

---

# 76. Delete Workflow

```text
Actions
    ↓
Delete
    ↓
Confirm
    ↓
DELETE /api/v1/admin/semesters/{id}
    ↓
Soft Delete
    ↓
Refresh List
```

---

# 77. Final API List

```text
POST
/api/v1/admin/semesters

GET
/api/v1/admin/semesters

GET
/api/v1/admin/semesters/{id}

PUT
/api/v1/admin/semesters/{id}

DELETE
/api/v1/admin/semesters/{id}
```

Local base:

```text
http://localhost:8080/api/v1/admin/semesters
```

---

# 78. Acceptance Criteria

## Architecture

```text
[ ] Semester feature uses feature/semester/

[ ] Controller contains no business logic

[ ] Service contains business logic

[ ] Repository handles persistence

[ ] DTOs used at API boundary

[ ] Mapper handles conversion

[ ] Feature-specific exceptions remain under Semester feature

[ ] Global exception handler remains global
```

## Create

```text
[ ] POST /api/v1/admin/semesters works

[ ] Thai name is required

[ ] English name is required

[ ] Duplicate combination rejected

[ ] createdBy comes from JWT

[ ] isDeleted defaults FALSE
```

## Read

```text
[ ] GET list works

[ ] GET by ID works

[ ] Search Thai name works

[ ] Search English name works

[ ] Pagination works

[ ] Sorting works

[ ] Deleted records are excluded
```

## Update

```text
[ ] PUT works

[ ] Thai name can update

[ ] English name can update

[ ] Duplicate validation excludes current Semester

[ ] updatedBy comes from JWT

[ ] updatedAt updates
```

## Delete

```text
[ ] DELETE works

[ ] Delete is soft delete

[ ] Physical row remains

[ ] isDeleted becomes TRUE

[ ] Deleted Semester is hidden from normal APIs
```

## Redis

```text
[ ] Semester detail may be cached

[ ] Create invalidates list cache

[ ] Update invalidates detail/list cache

[ ] Delete invalidates detail/list cache

[ ] PostgreSQL remains source of truth
```

## Schema Compliance

```text
[ ] No is_active added

[ ] No semester_code added

[ ] No academic_year_id added

[ ] No start_date added

[ ] No end_date added

[ ] Existing Semester Flyway migration remains unchanged
```

## Security

```text
[ ] Semester endpoints require RS256 JWT

[ ] Authorized Admin roles can access CRUD

[ ] Unauthorized users receive 403

[ ] Client cannot provide createdBy

[ ] Client cannot provide updatedBy
```

---

# 79. Next Step

After Semester CRUD is complete:

```text
Semester
    ↓
Academic Year / School Year
```

A future Academic Year feature can define:

```text
Academic Year
      ↓
Semester
      ↓
Academic Scheduling
```

through a separate normalized schema and Flyway migration.