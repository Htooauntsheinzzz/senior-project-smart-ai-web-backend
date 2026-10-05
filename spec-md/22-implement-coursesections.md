# 23 - Course Sections CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API implementation workflow for:

```text
Academic Management
    ↓
Course Sections
```

The Course Sections Flyway migration is already completed using:

```text
course_sections
```

This implementation includes:

```text
Create Section
Get Section List
Get Section By ID
Update Section
Soft Delete Section

Course Filter
Semester Filter
Lecturer Filter
Status Filter

Search
Pagination
Sorting

Summary
Capacity Handling
Derived FULL State
Redis Cache Management
```

---

# 2. Important Status Requirement

The database stores only these persistent Section statuses:

```text
ACTIVE
CLOSED
```

Database column:

```text
status VARCHAR(30)
```

Do NOT persist:

```text
FULL
```

`FULL` is a derived UI/business state.

Example:

```text
status = ACTIVE
capacity = 50
enrolledStudents = 50
```

UI may display:

```text
FULL
```

but database still stores:

```text
ACTIVE
```

Correct model:

```text
Persistent status:
ACTIVE
CLOSED

Derived capacity state:
AVAILABLE
FULL
```

---

# 3. Academic Year Removal

Course Sections use only:

```text
semester_id
```

Do NOT implement:

```text
academicYear
academicYearId
year
yearId
```

in:

```text
DTO
Entity
Repository
Service
Controller
API request
API response
Filters
```

Semester data comes from:

```text
semesters
```

---

# 4. Mandatory Agent Instructions

Before implementation, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files in `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the Admin API path convention.
5. Read the shared Flyway migration standards.
6. Read the Course CRUD specification.
7. Read the Lectures CRUD specification.
8. Read the Semester CRUD specification.
9. Read the Course Sections Flyway migration specification.
10. Inspect the existing `course_sections` table.
11. Inspect current Authentication and RS256 security.
12. Inspect Redis configuration.
13. Inspect global API response and exception conventions.
14. Follow feature-based architecture.
15. Do NOT add Academic Year.
16. Do NOT persist `FULL`.
17. Do NOT redesign the Course Sections schema.
18. Re-check project structure after implementation.
19. Run tests and Maven build before completion.

---

# 5. API Base Path

Use:

```text
/api/v1/admin/course-sections
```

Local development:

```text
http://localhost:8080/api/v1/admin/course-sections
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/course-sections")
public class CourseSectionController {
}
```

---

# 6. Authentication

All Course Section APIs require:

```http
Authorization: Bearer <access-token>
```

Use existing:

```text
JWT RS256
```

authentication.

Do NOT accept:

```text
createdBy
updatedBy
```

from client requests.

Audit fields come from the authenticated Admin.

---

# 7. Authorization

Recommended roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Use the existing Spring Security authority convention.

---

# 8. Existing Database Table

Use:

```text
course_sections
```

Columns:

| Column | Type |
|---|---|
| `id` | BIGINT |
| `course_id` | BIGINT |
| `section_number` | VARCHAR(20) |
| `capacity` | INTEGER |
| `lecture_id` | BIGINT |
| `room` | VARCHAR(100) |
| `schedule` | VARCHAR(255) |
| `semester_id` | BIGINT |
| `status` | VARCHAR(30) |
| `is_deleted` | BOOLEAN |
| `created_by` | BIGINT |
| `created_at` | TIMESTAMP |
| `updated_by` | BIGINT |
| `updated_at` | TIMESTAMP |

---

# 9. Feature Package Structure

Use:

```text
feature/
└── coursesection/
    ├── controller/
    │   └── CourseSectionController.java
    │
    ├── dto/
    │   ├── CourseSectionCreateRequest.java
    │   ├── CourseSectionUpdateRequest.java
    │   ├── CourseSectionResponse.java
    │   ├── CourseSectionListResponse.java
    │   └── CourseSectionSummaryResponse.java
    │
    ├── mapper/
    │   └── CourseSectionMapper.java
    │
    ├── entity/
    │   └── CourseSection.java
    │
    ├── enums/
    │   └── CourseSectionStatus.java
    │
    ├── service/
    │   ├── CourseSectionService.java
    │   └── impl/
    │       └── CourseSectionServiceImpl.java
    │
    ├── repository/
    │   └── CourseSectionRepository.java
    │
    └── exception/
        ├── CourseSectionNotFoundException.java
        ├── CourseSectionAlreadyExistsException.java
        ├── InvalidCourseSectionStatusException.java
        ├── InvalidCourseSectionCourseException.java
        ├── InvalidCourseSectionLecturerException.java
        └── InvalidCourseSectionSemesterException.java
```

---

# 10. API Endpoints

Implement:

```text
POST
/api/v1/admin/course-sections

GET
/api/v1/admin/course-sections

GET
/api/v1/admin/course-sections/{id}

PUT
/api/v1/admin/course-sections/{id}

DELETE
/api/v1/admin/course-sections/{id}

GET
/api/v1/admin/course-sections/summary
```

---

# 11. Course Section Status Enum

Recommended Java enum:

```java
public enum CourseSectionStatus {

    ACTIVE("ACTIVE"),

    CLOSED("CLOSED");

    private final String value;

    CourseSectionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CourseSectionStatus fromValue(String value) {
        for (CourseSectionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }

        throw new InvalidCourseSectionStatusException(
            "Invalid course section status: " + value
        );
    }
}
```

Database still stores:

```text
ACTIVE
CLOSED
```

---

# 12. Create Section API

Endpoint:

```http
POST /api/v1/admin/course-sections
```

---

# 13. CourseSectionCreateRequest

Recommended:

```java
public record CourseSectionCreateRequest(

    Long courseId,

    String sectionNumber,

    Integer capacity,

    Long lectureId,

    String room,

    String schedule,

    Long semesterId,

    String status

) {}
```

---

# 14. Create Request Example

```json
{
  "courseId": 1,
  "sectionNumber": "01",
  "capacity": 50,
  "lectureId": 4,
  "room": "IT-101",
  "schedule": "Mon/Wed 08:00-09:30",
  "semesterId": 1,
  "status": "ACTIVE"
}
```

Do NOT include:

```json
{
  "academicYear": 2026
}
```

---

# 15. Course Validation

`courseId` is required.

Backend must verify:

```text
Course exists
is_deleted = FALSE
is_active = TRUE
```

If invalid:

```text
400 Bad Request
```

or project-standard business validation response.

Recommended exception:

```text
InvalidCourseSectionCourseException
```

---

# 16. Section Number Validation

Field:

```text
sectionNumber
```

Rules:

```text
Required
Not blank
Maximum 20 characters
Trim whitespace
```

Examples:

```text
01
02
A
01A
LAB-01
```

Do NOT convert:

```text
01
```

into:

```text
1
```

automatically.

---

# 17. Capacity Validation

Field:

```text
capacity
```

Type:

```text
Integer
```

Rules:

```text
Required
Must be > 0
```

Example:

```json
{
  "capacity": 50
}
```

Invalid:

```text
0
-1
```

---

# 18. Capacity Default from Course

The Course table already contains:

```text
maximum_students_per_section
```

Recommended Create Section workflow:

```text
Select Course
    ↓
Load Course
    ↓
Read maximumStudentsPerSection
    ↓
Use as default Capacity
```

Example:

```text
Course default = 50

Create Section
capacity = 50
```

The Admin may override it if allowed.

---

# 19. Lecturer Validation

`lectureId` is required.

Validate:

```text
Lecture exists
is_deleted = FALSE
status = ACTIVE
```

If invalid:

```text
400 Bad Request
```

Use:

```text
InvalidCourseSectionLecturerException
```

---

# 20. Lecturer and Course Academic Validation

Recommended rule:

```text
lecture.faculty.id
==
course.faculty.id
```

and:

```text
lecture.department.id
==
course.department.id
```

This prevents assigning a Lecturer from an unrelated academic unit.

If the university later allows cross-department lecturers, change this rule through a new specification.

---

# 21. Room Validation

Field:

```text
room
```

Optional.

Rules:

```text
Maximum 100 characters
Trim whitespace
```

Examples:

```text
IT-101
IT-302
ENG-401
```

---

# 22. Schedule Validation

Field:

```text
schedule
```

Optional.

Rules:

```text
Maximum 255 characters
Trim whitespace
```

Examples:

```text
Mon/Wed 08:00-09:30

Tue/Thu 13:00-14:30

Fri 09:00-12:00
```

Schedule remains free text in this feature.

---

# 23. Semester Validation

`semesterId` is required.

Backend must verify:

```text
Semester exists
is_deleted = FALSE
```

There is no Academic Year validation.

Use:

```text
InvalidCourseSectionSemesterException
```

when invalid.

---

# 24. Status Validation

Supported request values:

```text
ACTIVE
CLOSED
```

Default on Create:

```text
ACTIVE
```

Normalize:

```text
active
→ ACTIVE

Active
→ ACTIVE

closed
→ CLOSED
```

Reject:

```text
FULL
INACTIVE
PENDING
DELETED
```

for the current Course Section persistent status field.

---

# 25. Duplicate Section Validation

The database uniqueness rule is:

```text
courseId
+
sectionNumber
+
semesterId
```

Therefore reject duplicate:

```text
Course:
CS101

Semester:
Semester 1

Section:
01
```

when the same combination already exists.

Use:

```text
CourseSectionAlreadyExistsException
```

Return:

```text
409 Conflict
```

---

# 26. Create Workflow

```text
Receive CourseSectionCreateRequest
        ↓
Validate DTO
        ↓
Normalize sectionNumber
        ↓
Validate Course
        ↓
Validate Capacity
        ↓
Validate Lecturer
        ↓
Validate Lecturer ↔ Course relationship
        ↓
Validate Semester
        ↓
Normalize Status
        ↓
Check Course + Section + Semester duplicate
        ↓
Get authenticated Admin
        ↓
Create CourseSection
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
Evict Section caches
        ↓
Evict Course cache because sectionCount changed
        ↓
Return 201 Created
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
  "message": "Course section created successfully",
  "data": {
    "id": 1,

    "courseId": 1,
    "courseCode": "CS101",
    "courseName": "Introduction to Programming",

    "sectionNumber": "01",

    "capacity": 50,
    "enrolledCount": 0,
    "isFull": false,

    "lectureId": 4,
    "lectureNameEn": "Nittaya Chindakharn",
    "lectureNameTh": "อาจารย์นิตยา",

    "room": "IT-101",
    "schedule": "Mon/Wed 08:00-09:30",

    "semesterId": 1,
    "semesterNameEn": "Semester 1",
    "semesterNameTh": "ภาคการศึกษาที่ 1",

    "status": "ACTIVE",

    "createdBy": 1,
    "createdAt": "2026-10-04T12:00:00"
  }
}
```

---

# 28. Get Course Section List API

Endpoint:

```http
GET /api/v1/admin/course-sections
```

Support:

```text
search
courseId
semesterId
lectureId
status
page
size
sort
```

No:

```text
academicYear
```

filter.

---

# 29. List Example

```text
GET /api/v1/admin/course-sections?page=0&size=20
```

Combined:

```text
GET /api/v1/admin/course-sections
    ?search=CS101
    &courseId=1
    &semesterId=1
    &lectureId=4
    &status=ACTIVE
    &page=0
    &size=20
```

---

# 30. Search Behavior

Search may include:

```text
Course Code
Course Name
Section Number
Lecturer English Name
Lecturer Thai Name
Lecturer Nickname
Room
```

Examples:

```text
CS101
```

```text
Introduction to Programming
```

```text
Nittaya
```

```text
IT-101
```

Use joins where necessary.

---

# 31. Course Filter

Example:

```text
GET /api/v1/admin/course-sections?courseId=1
```

Filter:

```text
course_sections.course_id = 1
```

---

# 32. Semester Filter

Example:

```text
GET /api/v1/admin/course-sections?semesterId=1
```

Filter:

```text
course_sections.semester_id = 1
```

---

# 33. Lecturer Filter

Example:

```text
GET /api/v1/admin/course-sections?lectureId=4
```

Filter:

```text
course_sections.lecture_id = 4
```

---

# 34. Status Filter

Supported:

```text
ACTIVE
CLOSED
```

Examples:

```text
GET /api/v1/admin/course-sections?status=ACTIVE
```

```text
GET /api/v1/admin/course-sections?status=CLOSED
```

Do NOT use:

```text
status=FULL
```

for persistent status filtering.

---

# 35. FULL Filtering

If a future UI needs:

```text
Full Sections
```

the backend must derive this from:

```text
enrolledCount >= capacity
```

not from:

```text
status = FULL
```

Until Enrollment exists, do not fabricate Full results.

---

# 36. Soft Delete Filter

Every normal query MUST include:

```text
is_deleted = FALSE
```

Deleted sections must not appear in:

```text
List
Search
Get By ID
Summary
Filters
```

---

# 37. Pagination

Recommended defaults:

```text
page = 0
size = 20
```

Maximum:

```text
size = 100
```

---

# 38. Sorting

Default:

```text
createdAt,desc
```

Allowed examples:

```text
sectionNumber
capacity
status
room
createdAt
updatedAt
```

Validate sort fields.

---

# 39. CourseSectionListResponse

Recommended:

```text
id

courseId
courseCode
courseName

sectionNumber

lectureId
lectureNameEn
lectureNameTh

room
schedule

capacity
enrolledCount
isFull

semesterId
semesterNameEn
semesterNameTh

status
createdAt
```

---

# 40. List Response Example

```json
{
  "id": 1,

  "courseId": 1,
  "courseCode": "CS101",
  "courseName": "Introduction to Programming",

  "sectionNumber": "01",

  "lectureId": 4,
  "lectureNameEn": "Nittaya Chindakharn",

  "room": "IT-101",
  "schedule": "Mon/Wed 08:00-09:30",

  "capacity": 50,
  "enrolledCount": 0,
  "isFull": false,

  "semesterId": 1,
  "semesterNameEn": "Semester 1",

  "status": "ACTIVE"
}
```

---

# 41. Enrolled Count

`enrolledCount` is NOT stored in `course_sections`.

Before Enrollment exists:

```text
enrolledCount = 0
```

is acceptable as a temporary derived response value.

Once Enrollment exists, calculate from actual enrollment records.

---

# 42. Full State

Recommended response field:

```text
isFull
```

Type:

```text
Boolean
```

Derivation:

```text
isFull =
enrolledCount >= capacity
```

Example:

```text
47 / 50
→ isFull = false

50 / 50
→ isFull = true
```

Do NOT persist `isFull`.

---

# 43. Get Section By ID API

Endpoint:

```http
GET /api/v1/admin/course-sections/{id}
```

Example:

```text
GET /api/v1/admin/course-sections/1
```

---

# 44. Get By ID Workflow

```text
Receive Section ID
        ↓
Find where is_deleted = FALSE
        ↓
Not found → 404
        ↓
Load Course
        ↓
Load Lecturer
        ↓
Load Semester
        ↓
Resolve Enrolled Count
        ↓
Calculate isFull
        ↓
Map response
        ↓
200 OK
```

---

# 45. Detail Response Example

```json
{
  "success": true,
  "message": "Course section retrieved successfully",
  "data": {
    "id": 1,

    "course": {
      "id": 1,
      "courseCode": "CS101",
      "courseName": "Introduction to Programming"
    },

    "sectionNumber": "01",

    "capacity": 50,
    "enrolledCount": 0,
    "isFull": false,

    "lecturer": {
      "id": 4,
      "lectureNameEn": "Nittaya Chindakharn",
      "lectureNameTh": "อาจารย์นิตยา",
      "lectureNickname": "Aj. Nittaya"
    },

    "room": "IT-101",
    "schedule": "Mon/Wed 08:00-09:30",

    "semester": {
      "id": 1,
      "semesterNameEn": "Semester 1",
      "semesterNameTh": "ภาคการศึกษาที่ 1"
    },

    "status": "ACTIVE",

    "createdBy": 1,
    "createdAt": "2026-10-04T12:00:00",

    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 46. Course Section Not Found

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
CourseSectionNotFoundException
```

---

# 47. Update Section API

Endpoint:

```http
PUT /api/v1/admin/course-sections/{id}
```

---

# 48. CourseSectionUpdateRequest

Recommended:

```java
public record CourseSectionUpdateRequest(

    Long courseId,

    String sectionNumber,

    Integer capacity,

    Long lectureId,

    String room,

    String schedule,

    Long semesterId,

    String status

) {}
```

No Academic Year.

---

# 49. Update Request Example

```json
{
  "courseId": 1,
  "sectionNumber": "01",
  "capacity": 60,
  "lectureId": 5,
  "room": "IT-102",
  "schedule": "Tue/Thu 10:00-11:30",
  "semesterId": 1,
  "status": "ACTIVE"
}
```

---

# 50. Update Rules

Allow changes to:

```text
courseId
sectionNumber
capacity
lectureId
room
schedule
semesterId
status
```

Do NOT allow:

```text
id
isDeleted
createdBy
createdAt
updatedBy
updatedAt
enrolledCount
isFull
academicYear
```

---

# 51. Update Duplicate Validation

When changing:

```text
courseId
sectionNumber
semesterId
```

check duplicate combination while excluding the current Section ID.

Concept:

```text
same courseId
AND same sectionNumber
AND same semesterId
AND id != currentId
```

---

# 52. Update Capacity Validation

Before lowering capacity, future Enrollment-aware logic should verify:

```text
newCapacity >= enrolledCount
```

Example:

```text
Enrolled = 45
New Capacity = 40
```

must be rejected.

Until Enrollment exists, only enforce:

```text
capacity > 0
```

---

# 53. Update Status

Allowed:

```text
ACTIVE
CLOSED
```

Example close:

```json
{
  "status": "CLOSED"
}
```

Closing a Section means:

```text
status = CLOSED
```

It does NOT mean:

```text
is_deleted = TRUE
```

---

# 54. Reopen Section

To reopen:

```json
{
  "status": "ACTIVE"
}
```

Result:

```text
status = ACTIVE
```

---

# 55. Update Workflow

```text
Receive ID + Update Request
        ↓
Find non-deleted Section
        ↓
Not found → 404
        ↓
Validate Course
        ↓
Validate Section Number
        ↓
Validate Capacity
        ↓
Validate Lecturer
        ↓
Validate Lecturer ↔ Course relationship
        ↓
Validate Semester
        ↓
Validate Status
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
Evict Section caches
        ↓
Evict Course cache if course changed
        ↓
Return 200
```

---

# 56. Delete Section API

Endpoint:

```http
DELETE /api/v1/admin/course-sections/{id}
```

---

# 57. Delete Type

Use:

```text
SOFT DELETE
```

Set:

```text
is_deleted = TRUE
```

Do NOT physically delete:

```sql
DELETE FROM course_sections;
```

---

# 58. Delete vs Close

These are different operations.

## Close

```text
status = CLOSED
is_deleted = FALSE
```

The Section still exists and remains visible to Admin management.

## Delete

```text
is_deleted = TRUE
```

The Section is removed from normal API results.

---

# 59. Delete Workflow

```text
Receive Section ID
        ↓
Find non-deleted Section
        ↓
Not found → 404
        ↓
Check future enrollments if implemented
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
Evict Section caches
        ↓
Evict Course cache because sectionCount changed
        ↓
Return success
```

---

# 60. Future Enrollment Delete Restriction

Once Enrollment exists:

```text
Section
    ↓
Enrolled Students
```

a Section with existing students may need to be blocked from deletion.

Future:

```text
409 Conflict
```

Recommended message:

```text
Cannot delete course section because students are enrolled.
```

Do NOT implement nonexistent Enrollment checks before that module exists.

---

# 61. Section Summary API

Endpoint:

```http
GET /api/v1/admin/course-sections/summary
```

Recommended response:

```json
{
  "success": true,
  "message": "Course section summary retrieved successfully",
  "data": {
    "totalSections": 23,
    "activeSections": 21,
    "closedSections": 2,
    "fullSections": 0,
    "totalEnrolled": 0
  }
}
```

---

# 62. Total Sections

Calculate:

```text
COUNT course_sections
WHERE is_deleted = FALSE
```

---

# 63. Active Sections

Calculate:

```text
COUNT course_sections
WHERE is_deleted = FALSE
AND status = 'ACTIVE'
```

---

# 64. Closed Sections

Calculate:

```text
COUNT course_sections
WHERE is_deleted = FALSE
AND status = 'CLOSED'
```

---

# 65. Full Sections

After Enrollment exists:

```text
COUNT sections
WHERE enrolledCount >= capacity
AND is_deleted = FALSE
```

Until Enrollment exists:

```text
fullSections = 0
```

Do not derive Full from `status`.

---

# 66. Total Enrolled

After Enrollment exists:

```text
SUM enrolled students across non-deleted Sections
```

Until then:

```text
totalEnrolled = 0
```

---

# 67. Course Section Entity

Recommended:

```java
Long id;

Course course;

String sectionNumber;

Integer capacity;

Lecture lecture;

String room;

String schedule;

Semester semester;

String status;

Boolean isDeleted;

AppUser createdBy;

LocalDateTime createdAt;

AppUser updatedBy;

LocalDateTime updatedAt;
```

Do NOT add:

```java
Integer academicYear;

Integer enrolledCount;

Boolean isFull;
```

as persisted Entity fields.

---

# 68. Course Relationship Mapping

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "course_id", nullable = false)
private Course course;
```

---

# 69. Lecturer Relationship Mapping

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "lecture_id", nullable = false)
private Lecture lecture;
```

---

# 70. Semester Relationship Mapping

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "semester_id", nullable = false)
private Semester semester;
```

---

# 71. Status Entity Mapping

Recommended:

```java
@Column(name = "status", nullable = false, length = 30)
private String status;
```

Use `CourseSectionStatus` for validation/conversion.

Do NOT use a separate status table.

---

# 72. CourseSectionResponse

Recommended fields:

```text
id

courseId
courseCode
courseName

sectionNumber

capacity
enrolledCount
isFull

lectureId
lectureNameTh
lectureNameEn
lectureNickname

room
schedule

semesterId
semesterNameTh
semesterNameEn

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

normally.

---

# 73. CourseSectionSummaryResponse

Fields:

```text
totalSections
activeSections
closedSections
fullSections
totalEnrolled
```

---

# 74. Repository Responsibilities

Repository handles:

```text
Find non-deleted Section by ID

Duplicate Course + Section + Semester check

Search

Course filter

Semester filter

Lecturer filter

Status filter

Pagination

Sorting

Summary counts

Course section count
```

---

# 75. Repository Method Concepts

Examples:

```text
findByIdAndIsDeletedFalse(...)

existsByCourseIdAndSectionNumberAndSemesterIdAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByStatusAndIsDeletedFalse(...)

countByCourseIdAndIsDeletedFalse(...)
```

For dynamic filtering, use:

```text
Specification
Criteria API
```

or existing project conventions.

---

# 76. CourseSectionService

Recommended:

```java
CourseSectionResponse create(
    CourseSectionCreateRequest request
);

Page<CourseSectionListResponse> getAll(
    String search,
    Long courseId,
    Long semesterId,
    Long lectureId,
    String status,
    Pageable pageable
);

CourseSectionResponse getById(
    Long id
);

CourseSectionResponse update(
    Long id,
    CourseSectionUpdateRequest request
);

void delete(
    Long id
);

CourseSectionSummaryResponse getSummary();
```

---

# 77. Service Responsibilities

`CourseSectionServiceImpl` handles:

```text
Course validation
Section number normalization
Capacity validation
Lecturer validation
Lecturer/Course academic compatibility
Semester validation
Status validation
Duplicate validation
Current Admin retrieval
Audit field assignment
Soft delete
Derived enrolledCount
Derived isFull
Transactions
Mapping
Redis cache management
```

---

# 78. Controller Responsibilities

Controller only:

```text
Receive HTTP requests
Validate DTO
Read query/path parameters
Create Pageable
Call Service
Return response
```

Do NOT:

```text
Query repositories directly
Validate Lecturer relationships
Calculate audit fields
Handle Redis
Implement soft delete logic
```

inside Controller.

---

# 79. Mapper Responsibilities

Map:

```text
CourseSectionCreateRequest
    ↓
CourseSection

CourseSection
    ↓
CourseSectionResponse

CourseSection
    ↓
CourseSectionListResponse
```

Derived fields:

```text
enrolledCount
isFull
```

must not be mapped as persisted Entity properties.

---

# 80. Transactions

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

# 81. Course sectionCount Integration

Courses currently expose:

```text
sectionCount
```

Now calculate from:

```text
course_sections
```

Query:

```sql
SELECT COUNT(*)
FROM course_sections
WHERE course_id = :courseId
  AND is_deleted = FALSE;
```

Do NOT store:

```text
section_count
```

in `courses`.

---

# 82. Redis Cache Management

Recommended:

```text
course-section-cache
course-section-list-cache
course-section-summary-cache
```

---

# 83. Detail Cache

Logical key:

```text
course-section:<id>
```

Example:

```text
course-section:10
```

---

# 84. List Cache

If implemented, include:

```text
search
courseId
semesterId
lectureId
status
page
size
sort
```

in the cache key.

---

# 85. Cache Eviction - Create

After Create:

```text
Evict Course Section list cache

Evict Section summary

Evict related Course cache
```

because Course `sectionCount` changed.

---

# 86. Cache Eviction - Update

After Update:

```text
Evict course-section:<id>

Evict Course Section list cache

Evict Section summary

Evict old Course cache

Evict new Course cache
```

if Course changes.

---

# 87. Cache Eviction - Delete

After Delete:

```text
Evict course-section:<id>

Evict Course Section list cache

Evict Section summary

Evict related Course cache
```

---

# 88. Exceptions

Create:

```text
CourseSectionNotFoundException

CourseSectionAlreadyExistsException

InvalidCourseSectionStatusException

InvalidCourseSectionCourseException

InvalidCourseSectionLecturerException

InvalidCourseSectionSemesterException
```

Location:

```text
feature/coursesection/exception/
```

Global error mapping remains in the existing `GlobalExceptionHandler`.

---

# 89. HTTP Status Codes

| Operation | Status |
|---|---|
| Create | `201 Created` |
| List | `200 OK` |
| Detail | `200 OK` |
| Update | `200 OK` |
| Delete | `204 No Content` or project-standard success |
| Summary | `200 OK` |
| Validation | `400 Bad Request` |
| Unauthorized | `401 Unauthorized` |
| Forbidden | `403 Forbidden` |
| Not Found | `404 Not Found` |
| Duplicate Section | `409 Conflict` |

---

# 90. Create Testing

```text
[ ] Valid Section creates successfully

[ ] Course required

[ ] Invalid Course rejected

[ ] Inactive Course rejected for new Section

[ ] Section Number required

[ ] Leading-zero Section Number preserved

[ ] Capacity > 0

[ ] Lecturer required

[ ] Invalid Lecturer rejected

[ ] Closed/Inactive Lecturer rejected

[ ] Lecturer academic relationship validated

[ ] Semester required

[ ] Deleted Semester rejected

[ ] ACTIVE accepted

[ ] CLOSED accepted

[ ] FULL rejected as persistent status

[ ] Duplicate Course + Section + Semester rejected

[ ] createdBy comes from JWT
```

---

# 91. List Testing

```text
[ ] List non-deleted Sections

[ ] Search Course Code

[ ] Search Course Name

[ ] Search Section Number

[ ] Search Lecturer name

[ ] Search Room

[ ] Course filter works

[ ] Semester filter works

[ ] Lecturer filter works

[ ] ACTIVE filter works

[ ] CLOSED filter works

[ ] Pagination works

[ ] Sorting works

[ ] No Academic Year filter exists
```

---

# 92. Detail Testing

```text
[ ] Existing Section returns 200

[ ] Unknown ID returns 404

[ ] Deleted Section returns 404

[ ] Course data returned

[ ] Lecturer data returned

[ ] Semester data returned

[ ] enrolledCount returned

[ ] isFull derived correctly
```

---

# 93. Update Testing

```text
[ ] Course can update

[ ] Section Number can update

[ ] Capacity can update

[ ] Lecturer can update

[ ] Room can update

[ ] Schedule can update

[ ] Semester can update

[ ] Status can update

[ ] ACTIVE → CLOSED works

[ ] CLOSED → ACTIVE works

[ ] FULL cannot be persisted

[ ] Duplicate validation excludes current record

[ ] updatedBy comes from JWT

[ ] updatedAt updates
```

---

# 94. Delete Testing

```text
[ ] DELETE performs soft delete

[ ] Physical row remains

[ ] isDeleted becomes TRUE

[ ] updatedBy set

[ ] updatedAt set

[ ] Deleted Section excluded from list

[ ] Deleted Section returns 404

[ ] Course sectionCount changes
```

---

# 95. Summary Testing

```text
[ ] totalSections correct

[ ] activeSections correct

[ ] closedSections correct

[ ] Deleted Sections excluded

[ ] fullSections is derived

[ ] totalEnrolled is derived

[ ] No FULL database status required
```

---

# 96. Status Acceptance Criteria

```text
[ ] Database status remains VARCHAR(30)

[ ] ACTIVE supported

[ ] CLOSED supported

[ ] ACTIVE is default

[ ] FULL is not stored

[ ] INACTIVE is not used for Course Sections

[ ] Closed Section remains non-deleted

[ ] Reopening CLOSED → ACTIVE works

[ ] Status filter supports ACTIVE and CLOSED
```

---

# 97. Semester Acceptance Criteria

```text
[ ] Semester dropdown uses semesters table

[ ] semesterId required

[ ] semester_id FK used

[ ] Deleted Semester rejected

[ ] semesterName is derived

[ ] No Academic Year stored

[ ] No Academic Year request property

[ ] No Academic Year filter
```

---

# 98. Implementation Order

Implement:

```text
1. Verify Course Sections Flyway migration
        ↓
2. Create CourseSection Entity
        ↓
3. Create CourseSectionStatus enum
        ↓
4. Create CourseSection Repository
        ↓
5. Create DTOs
        ↓
6. Create Mapper
        ↓
7. Create Exceptions
        ↓
8. Create CourseSectionService
        ↓
9. Create CourseSectionServiceImpl
        ↓
10. Implement Course validation
        ↓
11. Implement Section Number normalization
        ↓
12. Implement Capacity validation
        ↓
13. Implement Lecturer validation
        ↓
14. Implement Lecturer/Course compatibility
        ↓
15. Implement Semester validation
        ↓
16. Implement Status validation
        ↓
17. Implement Duplicate validation
        ↓
18. Implement Create
        ↓
19. Implement List
        ↓
20. Implement Search
        ↓
21. Implement Course filter
        ↓
22. Implement Semester filter
        ↓
23. Implement Lecturer filter
        ↓
24. Implement Status filter
        ↓
25. Implement Pagination
        ↓
26. Implement Sorting
        ↓
27. Implement Get By ID
        ↓
28. Implement Update
        ↓
29. Implement Close/Reopen status flow
        ↓
30. Implement Soft Delete
        ↓
31. Implement Summary
        ↓
32. Integrate Course sectionCount
        ↓
33. Implement derived enrolledCount/isFull placeholders
        ↓
34. Add Redis cache management
        ↓
35. Create Controller
        ↓
36. Apply authorization
        ↓
37. Add tests
        ↓
38. Run Maven tests
        ↓
39. Run Maven package
        ↓
40. Re-check project structure
```

---

# 99. UI Create Workflow

```text
Click Create Section
        ↓
Select Course
        ↓
Load Course default capacity
        ↓
Enter Section Number
        ↓
Set / Adjust Capacity
        ↓
Select Lecturer
        ↓
Enter Room
        ↓
Enter Schedule
        ↓
Select Semester
        ↓
Choose ACTIVE / CLOSED
        ↓
POST /api/v1/admin/course-sections
        ↓
Refresh Section List
        ↓
Refresh Summary
        ↓
Refresh Course sectionCount
```

---

# 100. Course Section List Workflow

Page load:

```text
GET /api/v1/admin/course-sections

GET /api/v1/admin/course-sections/summary
```

Display:

```text
Course
Section
Lecturer
Room / Schedule
Capacity
Semester
Status
Actions
```

No:

```text
Academic Year
```

column or filter.

---

# 101. Close Section Workflow

```text
Actions
    ↓
Close Section
    ↓
PUT /api/v1/admin/course-sections/{id}
    ↓
status = CLOSED
    ↓
Section remains stored
    ↓
Refresh List
    ↓
Refresh Summary
```

Do NOT soft-delete when closing.

---

# 102. Reopen Section Workflow

```text
Edit Section
    ↓
Change CLOSED → ACTIVE
    ↓
PUT /api/v1/admin/course-sections/{id}
    ↓
Section becomes active again
```

---

# 103. Final API List

```text
POST
/api/v1/admin/course-sections

GET
/api/v1/admin/course-sections

GET
/api/v1/admin/course-sections/{id}

PUT
/api/v1/admin/course-sections/{id}

DELETE
/api/v1/admin/course-sections/{id}

GET
/api/v1/admin/course-sections/summary
```

Local:

```text
http://localhost:8080/api/v1/admin/course-sections
```

---

# 104. Final Status Model

```text
Course Section Status
│
├── ACTIVE
│
│   ├── Enrollment available
│   └── May derive FULL when capacity reached
│
└── CLOSED
    └── Enrollment should not accept new students
```

Derived:

```text
ACTIVE + enrolled < capacity
→ Available

ACTIVE + enrolled >= capacity
→ Full

CLOSED
→ Closed
```

Database never needs:

```text
FULL
```

---

# 105. Final Acceptance Criteria

## Architecture

```text
[ ] feature/coursesection package used

[ ] Controller has no business logic

[ ] Service handles validation and status rules

[ ] Repository handles persistence

[ ] DTOs used at API boundary

[ ] Mapper handles conversions
```

## CRUD

```text
[ ] Create works

[ ] List works

[ ] Detail works

[ ] Update works

[ ] Soft Delete works
```

## Status

```text
[ ] ACTIVE persisted

[ ] CLOSED persisted

[ ] FULL is derived only

[ ] Close Section works

[ ] Reopen Section works

[ ] Delete and Close remain separate operations
```

## Course

```text
[ ] courseId validated

[ ] Deleted Course rejected

[ ] Course data returned

[ ] Course sectionCount derived from course_sections
```

## Lecturer

```text
[ ] lectureId validated

[ ] Deleted Lecturer rejected

[ ] Lecturer must be ACTIVE for new Section

[ ] Academic compatibility validated
```

## Semester

```text
[ ] semesterId required

[ ] Semester validated

[ ] Semester names returned

[ ] No Academic Year exists in Section API
```

## Capacity

```text
[ ] capacity > 0

[ ] Course maximumStudentsPerSection can provide default

[ ] enrolledCount is derived

[ ] isFull is derived
```

## Filters

```text
[ ] Search works

[ ] Course filter works

[ ] Semester filter works

[ ] Lecturer filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works
```

## Audit

```text
[ ] createdBy from authenticated Admin

[ ] createdAt populated

[ ] updatedBy from authenticated Admin

[ ] updatedAt populated
```

## Redis

```text
[ ] Detail caching supported

[ ] Create invalidates related caches

[ ] Update invalidates related caches

[ ] Delete invalidates related caches

[ ] Related Course cache invalidated when sectionCount changes

[ ] PostgreSQL remains source of truth
```

---

# 106. Next Feature

After Course Sections CRUD is complete:

```text
Course
    ↓
Course Section
    ↓
Enrollment
```

Recommended next flow:

```text
Enrollment Flyway Migration
        ↓
Student → Course Section Relationship
        ↓
Enrollment CRUD
        ↓
Actual enrolledCount
        ↓
FULL state calculation
        ↓
View Students
        ↓
Total Enrolled
        ↓
Capacity enforcement
```