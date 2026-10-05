# 21 - Courses CRUD Workflow API Specification

## 1. Project

**Smart University Student Assistant App**

This specification defines the complete CRUD API workflow for:

```text
Academic Management
    ↓
Courses
```

The Course Flyway migration is already completed and uses:

```text
courses
```

This specification implements:

```text
Create Course
Get Course List
Get Course By ID
Update Course
Soft Delete Course

Search
Faculty Filter
Department Filter
Program Filter
Academic Year Filter
Semester Filter
Course Type Filter
Status Filter

Pagination
Sorting
Course Summary
Prerequisite Course Validation
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
6. Read the Course Flyway migration specification.
7. Inspect the existing `courses` migration.
8. Inspect Faculty implementation.
9. Inspect Department implementation.
10. Inspect Programs & Majors implementation.
11. Inspect Semester implementation.
12. Inspect Authentication and RS256 security.
13. Inspect Redis configuration.
14. Inspect global API response/error conventions.
15. Follow the feature-based architecture.
16. Do NOT modify the existing Course database schema.
17. Do NOT modify previous Flyway migrations.
18. Use numeric Course Type codes exactly as specified.
19. Validate prerequisite Course Codes before storing.
20. Re-check project structure after implementation.
21. Run tests and Maven build before completion.

---

# 3. API Base Path

Use:

```text
/api/v1/admin/courses
```

Local development:

```text
http://localhost:8080/api/v1/admin/courses
```

Controller:

```java
@RestController
@RequestMapping("/api/v1/admin/courses")
public class CourseController {
}
```

---

# 4. Authentication

Every Course API requires:

```http
Authorization: Bearer <access-token>
```

Use the existing:

```text
JWT RS256
```

authentication implementation.

Do NOT accept:

```text
createdBy
updatedBy
```

from client requests.

These values must come from the authenticated Admin.

---

# 5. Authorization

Recommended roles:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Use the project's existing Spring Security authority convention.

Example:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_ACADEMIC_ADMIN
```

---

# 6. Existing Course Table

Use the existing:

```text
courses
```

Columns:

| Column | Type |
|---|---|
| `id` | BIGINT |
| `course_code` | VARCHAR(50) |
| `course_name` | VARCHAR(255) |
| `credit_hours` | INTEGER |
| `description` | VARCHAR(1000) |
| `faculty_id` | BIGINT |
| `department_id` | BIGINT |
| `program_id` | BIGINT |
| `semester_id` | BIGINT |
| `recommended_academic_year` | SMALLINT |
| `prerequisite_courses` | VARCHAR(1000) |
| `course_type` | SMALLINT |
| `maximum_students_per_section` | INTEGER |
| `is_active` | BOOLEAN |
| `is_deleted` | BOOLEAN |
| `created_by` | BIGINT |
| `created_at` | TIMESTAMP |
| `updated_by` | BIGINT |
| `updated_at` | TIMESTAMP |

Do NOT redesign this table.

---

# 7. Academic Relationships

Course contains:

```text
faculty_id
department_id
program_id
semester_id
```

Relationships:

```text
courses.faculty_id
    ↓
faculties.id
```

```text
courses.department_id
    ↓
departments.id
```

```text
courses.program_id
    ↓
programs.id
```

```text
courses.semester_id
    ↓
semesters.id
```

The backend MUST validate that these relationships are consistent.

---

# 8. Relationship Validation

The backend must validate:

```text
Department belongs to Faculty
```

and when Program is provided:

```text
Program belongs to Department
```

Flow:

```text
Faculty
    ↓
Department
    ↓
Program
    ↓
Course
```

Validation:

```text
department.faculty.id == facultyId
```

and:

```text
program.department.id == departmentId
```

when `programId != null`.

---

# 9. Feature Package Structure

Use:

```text
feature/
└── course/
    ├── controller/
    │   └── CourseController.java
    │
    ├── dto/
    │   ├── CourseCreateRequest.java
    │   ├── CourseUpdateRequest.java
    │   ├── CourseResponse.java
    │   ├── CourseListResponse.java
    │   └── CourseSummaryResponse.java
    │
    ├── mapper/
    │   └── CourseMapper.java
    │
    ├── entity/
    │   └── Course.java
    │
    ├── enums/
    │   └── CourseType.java
    │
    ├── service/
    │   ├── CourseService.java
    │   └── impl/
    │       └── CourseServiceImpl.java
    │
    ├── repository/
    │   └── CourseRepository.java
    │
    └── exception/
        ├── CourseNotFoundException.java
        ├── CourseCodeAlreadyExistsException.java
        ├── InvalidCourseRelationshipException.java
        ├── InvalidPrerequisiteCourseException.java
        └── InvalidCourseTypeException.java
```

---

# 10. CRUD Endpoints

Implement:

```text
POST
/api/v1/admin/courses

GET
/api/v1/admin/courses

GET
/api/v1/admin/courses/{id}

PUT
/api/v1/admin/courses/{id}

DELETE
/api/v1/admin/courses/{id}

GET
/api/v1/admin/courses/summary
```

---

# 11. Course Type Enum

Course Type uses numeric database codes:

```text
100 → General Education
200 → Major Elective
300 → Major Required
```

Implement:

```java
public enum CourseType {

    GENERAL_EDUCATION(100, "General Education"),

    MAJOR_ELECTIVE(200, "Major Elective"),

    MAJOR_REQUIRED(300, "Major Required");

    private final int code;
    private final String displayName;

    CourseType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CourseType fromCode(int code) {
        for (CourseType type : values()) {
            if (type.code == code) {
                return type;
            }
        }

        throw new InvalidCourseTypeException(
            "Invalid course type: " + code
        );
    }
}
```

Database stores:

```text
100
200
300
```

not enum names.

---

# 12. Create Course API

Endpoint:

```http
POST /api/v1/admin/courses
```

---

# 13. CourseCreateRequest

Recommended:

```java
public record CourseCreateRequest(

    String courseCode,

    String courseName,

    Integer creditHours,

    String description,

    Long facultyId,

    Long departmentId,

    Long programId,

    Long semesterId,

    Integer recommendedAcademicYear,

    String prerequisiteCourses,

    Integer courseType,

    Integer maximumStudentsPerSection,

    Boolean isActive

) {}
```

---

# 14. Create Request Example

```json
{
  "courseCode": "CS301",
  "courseName": "Data Structures & Algorithms",
  "creditHours": 3,
  "description": "Study of data structures and algorithms.",
  "facultyId": 1,
  "departmentId": 3,
  "programId": 5,
  "semesterId": 1,
  "recommendedAcademicYear": 2,
  "prerequisiteCourses": "CS101,CS201",
  "courseType": 300,
  "maximumStudentsPerSection": 50,
  "isActive": true
}
```

---

# 15. Course Code Validation

Field:

```text
courseCode
```

Rules:

```text
Required
Not blank
Maximum 50 characters
Trim whitespace
Unique
```

Recommended normalization:

```text
cs301
→ CS301
```

if uppercase normalization matches the current project convention.

---

# 16. Course Name Validation

Field:

```text
courseName
```

Rules:

```text
Required
Not blank
Maximum 255 characters
Trim whitespace
```

---

# 17. Credit Hours Validation

Field:

```text
creditHours
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
  "creditHours": 3
}
```

Invalid:

```text
0
-1
```

---

# 18. Description Validation

Field:

```text
description
```

Rules:

```text
Optional
Maximum 1000 characters
Trim when provided
```

Blank descriptions may be normalized to:

```text
NULL
```

if that matches project conventions.

---

# 19. Faculty Validation

`facultyId` is required.

Flow:

```text
Load Faculty
    ↓
Exists?
    ↓
is_deleted = FALSE?
    ↓
is_active = TRUE?
```

Invalid Faculty must reject Course creation.

---

# 20. Department Validation

`departmentId` is required.

Flow:

```text
Load Department
    ↓
Exists?
    ↓
is_deleted = FALSE?
    ↓
is_active = TRUE?
    ↓
Department belongs to selected Faculty?
```

Required:

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

# 21. Program Validation

`programId` is optional.

If:

```text
programId = null
```

Course creation may continue.

If supplied:

```text
Load Program
    ↓
Exists?
    ↓
is_deleted = FALSE?
    ↓
is_active = TRUE?
    ↓
Program belongs to selected Department?
```

Required:

```text
program.department.id == departmentId
```

If not:

```text
400 Bad Request
```

Recommended message:

```text
Selected program does not belong to the selected department.
```

---

# 22. Semester Validation

`semesterId` is optional.

If provided:

```text
Load Semester
    ↓
Exists?
    ↓
is_deleted = FALSE?
```

If not found or deleted:

```text
400 Bad Request
```

or project-standard business validation response.

---

# 23. Recommended Academic Year Validation

Field:

```text
recommendedAcademicYear
```

Optional.

When provided:

```text
> 0
```

Examples:

```text
1
2
3
4
```

Meaning:

```text
1 → Year 1
2 → Year 2
3 → Year 3
4 → Year 4
```

---

# 24. Prerequisite Courses

Field:

```text
prerequisiteCourses
```

Type:

```text
String
```

Example:

```json
{
  "prerequisiteCourses": "CS101,CS201"
}
```

Database stores:

```text
CS101,CS201
```

---

# 25. Prerequisite Normalization

If input:

```text
CS101, CS201 , CS220
```

normalize to:

```text
CS101,CS201,CS220
```

Recommended workflow:

```text
Receive prerequisiteCourses
        ↓
Split by comma
        ↓
Trim each Course Code
        ↓
Remove blank values
        ↓
Normalize code
        ↓
Remove duplicate codes
        ↓
Validate each Course
        ↓
Join with comma
```

---

# 26. Prerequisite Validation

Each prerequisite Course Code must:

```text
Exist
Not be deleted
Not equal current Course Code
```

Example invalid:

```text
Current Course:
CS301

Prerequisite:
CS301
```

Reject with:

```text
400 Bad Request
```

Recommended message:

```text
A course cannot be its own prerequisite.
```

---

# 27. Missing Prerequisite Course

Example:

```text
prerequisiteCourses = CS101,ABC999
```

If:

```text
ABC999
```

does not exist, reject the request.

Do NOT silently save nonexistent prerequisite Course Codes.

Use:

```text
InvalidPrerequisiteCourseException
```

---

# 28. Course Type Validation

Request:

```text
courseType
```

must be:

```text
100
200
300
```

Mapping:

```text
100
→ General Education

200
→ Major Elective

300
→ Major Required
```

Reject:

```text
0
50
400
999
```

---

# 29. Maximum Students Per Section

Field:

```text
maximumStudentsPerSection
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
  "maximumStudentsPerSection": 50
}
```

---

# 30. Status

Field:

```text
isActive
```

Type:

```text
Boolean
```

Optional on Create.

Default:

```text
TRUE
```

Mapping:

```text
Active
→ true

Inactive
→ false
```

---

# 31. Duplicate Course Validation

Before Create:

```text
courseCode
```

must be unique.

Check:

```text
Course exists with same code
```

If yes:

```text
409 Conflict
```

Use:

```text
CourseCodeAlreadyExistsException
```

---

# 32. Create Workflow

```text
Receive CourseCreateRequest
        ↓
Validate DTO
        ↓
Normalize Course Code
        ↓
Trim Course Name
        ↓
Check duplicate Course Code
        ↓
Validate Faculty
        ↓
Validate Department
        ↓
Verify Department → Faculty
        ↓
Validate Program if provided
        ↓
Verify Program → Department
        ↓
Validate Semester if provided
        ↓
Validate Academic Year
        ↓
Validate Course Type
        ↓
Validate Maximum Students
        ↓
Normalize prerequisite string
        ↓
Validate prerequisite Course Codes
        ↓
Get authenticated Admin
        ↓
Create Course
        ↓
isDeleted = FALSE
        ↓
isActive = request or TRUE
        ↓
createdBy = Admin
        ↓
createdAt = current timestamp
        ↓
Save
        ↓
Evict Course caches
        ↓
Return 201 Created
```

---

# 33. Create Response

HTTP:

```text
201 Created
```

Example:

```json
{
  "success": true,
  "message": "Course created successfully",
  "data": {
    "id": 10,
    "courseCode": "CS301",
    "courseName": "Data Structures & Algorithms",
    "creditHours": 3,
    "description": "Study of data structures and algorithms.",

    "facultyId": 1,
    "facultyName": "Faculty of Engineering",

    "departmentId": 3,
    "departmentName": "Computer Engineering",

    "programId": 5,
    "programName": "Computer Engineering",

    "semesterId": 1,
    "semesterNameEn": "Semester 1",
    "semesterNameTh": "ภาคการศึกษาที่ 1",

    "recommendedAcademicYear": 2,

    "prerequisiteCourses": "CS101,CS201",

    "courseType": 300,
    "courseTypeName": "Major Required",

    "maximumStudentsPerSection": 50,

    "isActive": true,

    "createdBy": 1,
    "createdAt": "2026-09-29T19:00:00"
  }
}
```

---

# 34. Get Course List API

Endpoint:

```http
GET /api/v1/admin/courses
```

Support:

```text
search
facultyId
departmentId
programId
semesterId
recommendedAcademicYear
courseType
status
page
size
sort
```

---

# 35. List Example

```text
GET /api/v1/admin/courses?page=0&size=20
```

Combined:

```text
GET /api/v1/admin/courses
    ?search=data
    &facultyId=1
    &departmentId=3
    &programId=5
    &semesterId=1
    &recommendedAcademicYear=2
    &courseType=300
    &status=ACTIVE
    &page=0
    &size=20
```

---

# 36. Search Behavior

Search:

```text
course_code
course_name
```

Example:

```text
data
```

may match:

```text
Data Structures
Data Structures & Algorithms
```

Example:

```text
CS301
```

matches Course Code.

Use case-insensitive matching where practical.

---

# 37. Faculty Filter

Example:

```text
GET /api/v1/admin/courses?facultyId=1
```

Query:

```text
courses.faculty_id = 1
```

---

# 38. Department Filter

Example:

```text
GET /api/v1/admin/courses?departmentId=3
```

Query:

```text
courses.department_id = 3
```

---

# 39. Program Filter

Example:

```text
GET /api/v1/admin/courses?programId=5
```

Query:

```text
courses.program_id = 5
```

---

# 40. Semester Filter

Example:

```text
GET /api/v1/admin/courses?semesterId=1
```

Query:

```text
courses.semester_id = 1
```

---

# 41. Recommended Academic Year Filter

Example:

```text
GET /api/v1/admin/courses?recommendedAcademicYear=2
```

Meaning:

```text
Year 2
```

---

# 42. Course Type Filter

Example:

```text
GET /api/v1/admin/courses?courseType=300
```

Meaning:

```text
Major Required
```

Supported:

```text
100
200
300
```

Invalid Course Type filter:

```text
400 Bad Request
```

---

# 43. Status Filter

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

Examples:

```text
GET /api/v1/admin/courses?status=ACTIVE
```

---

# 44. Soft Delete Filter

All normal Course queries MUST include:

```text
is_deleted = FALSE
```

Deleted Course records must not appear in:

```text
List
Search
Filters
Get By ID
Summary
Prerequisite selection
```

---

# 45. Pagination

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

# 46. Sorting

Default:

```text
createdAt,desc
```

Allowed sort fields may include:

```text
courseCode
courseName
creditHours
recommendedAcademicYear
courseType
maximumStudentsPerSection
createdAt
updatedAt
```

Example:

```text
GET /api/v1/admin/courses?sort=courseCode,asc
```

Validate sort fields.

---

# 47. CourseListResponse

Recommended:

```text
id
courseCode
courseName

creditHours

facultyId
facultyName

departmentId
departmentName

programId
programName

semesterId
semesterNameEn
semesterNameTh

recommendedAcademicYear

courseType
courseTypeName

maximumStudentsPerSection

sectionCount

isActive
```

---

# 48. List Response Example

```json
{
  "id": 10,
  "courseCode": "CS301",
  "courseName": "Data Structures & Algorithms",
  "creditHours": 3,

  "facultyId": 1,
  "facultyName": "Faculty of Engineering",

  "departmentId": 3,
  "departmentName": "Computer Engineering",

  "programId": 5,
  "programName": "Computer Engineering",

  "semesterId": 1,
  "semesterNameEn": "Semester 1",

  "recommendedAcademicYear": 2,

  "courseType": 300,
  "courseTypeName": "Major Required",

  "maximumStudentsPerSection": 50,

  "sectionCount": 0,

  "isActive": true
}
```

---

# 49. Section Count

The UI displays:

```text
Sections
```

This is NOT stored in `courses`.

Until Course Sections exist:

```text
sectionCount = 0
```

Once Course Sections are implemented:

```text
sectionCount
```

must be derived from actual section records.

---

# 50. Paginated Response

Example:

```json
{
  "success": true,
  "message": "Courses retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

Use the existing project pagination wrapper when available.

---

# 51. Get Course By ID API

Endpoint:

```http
GET /api/v1/admin/courses/{id}
```

Example:

```text
GET /api/v1/admin/courses/10
```

---

# 52. Get By ID Workflow

```text
Receive Course ID
        ↓
Find Course where is_deleted = FALSE
        ↓
Not found?
        ↓
404
        ↓
Load Faculty
        ↓
Load Department
        ↓
Load Program if present
        ↓
Load Semester if present
        ↓
Resolve Course Type display name
        ↓
Map response
        ↓
200 OK
```

---

# 53. Detail Response

Example:

```json
{
  "success": true,
  "message": "Course retrieved successfully",
  "data": {
    "id": 10,
    "courseCode": "CS301",
    "courseName": "Data Structures & Algorithms",
    "creditHours": 3,
    "description": "Study of data structures and algorithms.",

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

    "program": {
      "id": 5,
      "programCode": "PRG-CE-BS",
      "programName": "Computer Engineering"
    },

    "semester": {
      "id": 1,
      "semesterNameEn": "Semester 1",
      "semesterNameTh": "ภาคการศึกษาที่ 1"
    },

    "recommendedAcademicYear": 2,

    "prerequisiteCourses": "CS101,CS201",

    "courseType": 300,
    "courseTypeName": "Major Required",

    "maximumStudentsPerSection": 50,

    "isActive": true,

    "createdBy": 1,
    "createdAt": "2026-09-29T19:00:00",

    "updatedBy": null,
    "updatedAt": null
  }
}
```

---

# 54. Course Not Found

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
CourseNotFoundException
```

---

# 55. Update Course API

Endpoint:

```http
PUT /api/v1/admin/courses/{id}
```

---

# 56. CourseUpdateRequest

Recommended:

```java
public record CourseUpdateRequest(

    String courseCode,

    String courseName,

    Integer creditHours,

    String description,

    Long facultyId,

    Long departmentId,

    Long programId,

    Long semesterId,

    Integer recommendedAcademicYear,

    String prerequisiteCourses,

    Integer courseType,

    Integer maximumStudentsPerSection,

    Boolean isActive

) {}
```

---

# 57. Update Request Example

```json
{
  "courseCode": "CS301",
  "courseName": "Advanced Data Structures & Algorithms",
  "creditHours": 3,
  "description": "Updated description.",
  "facultyId": 1,
  "departmentId": 3,
  "programId": 5,
  "semesterId": 1,
  "recommendedAcademicYear": 2,
  "prerequisiteCourses": "CS101,CS201",
  "courseType": 300,
  "maximumStudentsPerSection": 60,
  "isActive": true
}
```

---

# 58. Update Rules

Allow changes to:

```text
courseCode
courseName
creditHours
description
facultyId
departmentId
programId
semesterId
recommendedAcademicYear
prerequisiteCourses
courseType
maximumStudentsPerSection
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

# 59. Update Course Code Validation

When checking duplicate Course Code:

```text
Exclude current Course ID
```

Concept:

```text
same courseCode
AND id != currentId
```

The Course may retain its current code.

---

# 60. Update Relationship Validation

When Faculty/Department/Program changes:

```text
Validate Faculty
        ↓
Validate Department
        ↓
Verify Department belongs to Faculty
        ↓
Validate Program if supplied
        ↓
Verify Program belongs to Department
        ↓
Validate Semester if supplied
```

---

# 61. Update Prerequisites

If prerequisites change:

```text
Normalize codes
        ↓
Validate every Course Code
        ↓
Reject self-reference
        ↓
Remove duplicates
        ↓
Save normalized string
```

Example:

```text
CS101, CS201, CS101
```

normalize to:

```text
CS101,CS201
```

---

# 62. Update Workflow

```text
Receive Course ID + request
        ↓
Find non-deleted Course
        ↓
Not found → 404
        ↓
Validate fields
        ↓
Check Course Code duplicate excluding current ID
        ↓
Validate Faculty
        ↓
Validate Department
        ↓
Validate Faculty/Department relationship
        ↓
Validate Program if supplied
        ↓
Validate Program/Department relationship
        ↓
Validate Semester if supplied
        ↓
Validate Academic Year
        ↓
Validate Course Type
        ↓
Validate Maximum Students
        ↓
Validate prerequisites
        ↓
Get authenticated Admin
        ↓
Update Course
        ↓
updatedBy = Admin
        ↓
updatedAt = current timestamp
        ↓
Save
        ↓
Evict Course caches
        ↓
Return 200
```

---

# 63. Update Response

HTTP:

```text
200 OK
```

Example:

```json
{
  "success": true,
  "message": "Course updated successfully",
  "data": {
    "id": 10,
    "courseCode": "CS301",
    "courseName": "Advanced Data Structures & Algorithms",
    "creditHours": 3,
    "courseType": 300,
    "courseTypeName": "Major Required",
    "maximumStudentsPerSection": 60,
    "isActive": true,
    "updatedBy": 1,
    "updatedAt": "2026-09-29T20:00:00"
  }
}
```

---

# 64. Activate / Deactivate Course

Use the Update API.

Active:

```text
isActive = true
```

Inactive:

```text
isActive = false
```

Do NOT treat:

```text
Inactive
```

as deleted.

---

# 65. Inactive vs Deleted

Inactive:

```text
is_active = FALSE
is_deleted = FALSE
```

Course remains stored and can still be viewed by appropriate administrative operations.

Deleted:

```text
is_deleted = TRUE
```

Course is excluded from normal application usage.

---

# 66. Delete Course API

Endpoint:

```http
DELETE /api/v1/admin/courses/{id}
```

---

# 67. Delete Type

Use:

```text
SOFT DELETE
```

Do NOT execute:

```sql
DELETE FROM courses
WHERE id = ?;
```

Instead:

```text
is_deleted = TRUE
```

---

# 68. Delete Workflow

```text
Receive Course ID
        ↓
Find non-deleted Course
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
Evict Course caches
        ↓
Return success
```

---

# 69. Future Delete Restrictions

When Course Sections and Enrollment exist:

```text
Course
    ↓
Course Sections
    ↓
Enrollment
```

Course deletion may need to be blocked if active dependent data exists.

Future behavior:

```text
409 Conflict
```

Do NOT implement checks for tables that do not exist yet.

---

# 70. Course Summary API

Endpoint:

```http
GET /api/v1/admin/courses/summary
```

Recommended response:

```json
{
  "success": true,
  "message": "Course summary retrieved successfully",
  "data": {
    "totalCourses": 20,
    "activeCourses": 18,
    "inactiveCourses": 2,
    "currentSemesterCourses": 0
  }
}
```

---

# 71. Summary Calculations

Total:

```text
COUNT courses
WHERE is_deleted = FALSE
```

Active:

```text
COUNT courses
WHERE is_deleted = FALSE
AND is_active = TRUE
```

Inactive:

```text
COUNT courses
WHERE is_deleted = FALSE
AND is_active = FALSE
```

Current Semester:

Until the project has a clearly defined current-semester configuration:

```text
currentSemesterCourses = 0
```

Do NOT hard-code a particular Semester ID as the current Semester.

---

# 72. Course Entity

Create:

```text
feature/course/entity/Course.java
```

Recommended fields:

```java
Long id;

String courseCode;

String courseName;

Integer creditHours;

String description;

Faculty faculty;

Department department;

Program program;

Semester semester;

Integer recommendedAcademicYear;

String prerequisiteCourses;

Integer courseType;

Integer maximumStudentsPerSection;

Boolean isActive;

Boolean isDeleted;

AppUser createdBy;

LocalDateTime createdAt;

AppUser updatedBy;

LocalDateTime updatedAt;
```

---

# 73. Course Type Entity Mapping

Database:

```text
course_type SMALLINT
```

Entity may store:

```java
Integer courseType;
```

and convert through:

```java
CourseType.fromCode(courseType)
```

This is the simplest mapping for the current numeric database design.

Do NOT use:

```java
@Enumerated(EnumType.STRING)
```

because that would store enum names instead of:

```text
100
200
300
```

---

# 74. Faculty Relationship

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "faculty_id", nullable = false)
private Faculty faculty;
```

---

# 75. Department Relationship

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "department_id", nullable = false)
private Department department;
```

---

# 76. Program Relationship

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "program_id")
private Program program;
```

Nullable.

---

# 77. Semester Relationship

Recommended:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "semester_id")
private Semester semester;
```

Nullable.

---

# 78. CourseResponse

Recommended fields:

```text
id

courseCode
courseName
creditHours
description

facultyId
facultyCode
facultyNameEn

departmentId
departmentCode
departmentName

programId
programCode
programName

semesterId
semesterNameTh
semesterNameEn

recommendedAcademicYear

prerequisiteCourses

courseType
courseTypeName

maximumStudentsPerSection

isActive

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

# 79. CourseSummaryResponse

Recommended:

```text
totalCourses
activeCourses
inactiveCourses
currentSemesterCourses
```

---

# 80. CourseRepository Responsibilities

Repository handles:

```text
Find non-deleted Course by ID

Find Course by Course Code

Duplicate Course Code checking

Search

Faculty filter

Department filter

Program filter

Semester filter

Academic Year filter

Course Type filter

Status filter

Pagination

Sorting

Summary counts
```

---

# 81. Repository Method Concepts

Examples:

```text
findByIdAndIsDeletedFalse(...)

findByCourseCodeIgnoreCaseAndIsDeletedFalse(...)

existsByCourseCodeIgnoreCaseAndIsDeletedFalse(...)

countByIsDeletedFalse()

countByIsActiveTrueAndIsDeletedFalse()

countByIsActiveFalseAndIsDeletedFalse()
```

For combined filtering, prefer:

```text
Spring Data Specification
Criteria API
```

or the project's existing dynamic filtering convention.

---

# 82. CourseService

Recommended interface:

```java
CourseResponse create(
    CourseCreateRequest request
);

Page<CourseListResponse> getAll(
    String search,
    Long facultyId,
    Long departmentId,
    Long programId,
    Long semesterId,
    Integer recommendedAcademicYear,
    Integer courseType,
    String status,
    Pageable pageable
);

CourseResponse getById(
    Long id
);

CourseResponse update(
    Long id,
    CourseUpdateRequest request
);

void delete(
    Long id
);

CourseSummaryResponse getSummary();
```

---

# 83. CourseServiceImpl Responsibilities

Business logic belongs in:

```text
CourseServiceImpl
```

Responsibilities:

```text
Course Code normalization

Basic field validation

Faculty validation

Department validation

Faculty/Department consistency

Program validation

Program/Department consistency

Semester validation

Academic Year validation

Course Type validation

Prerequisite parsing and validation

Maximum Students validation

Authenticated Admin retrieval

Audit fields

Soft delete

Transactions

Mapping

Redis cache management
```

Do NOT place these rules in the Controller.

---

# 84. CourseController Responsibilities

Controller should only:

```text
Receive HTTP request

Validate DTO

Read path/query parameters

Create Pageable

Call CourseService

Return API response
```

Do NOT:

```text
Query repositories directly

Validate Faculty/Department relationships

Parse prerequisite Course Codes

Set audit fields

Handle Redis directly

Perform soft delete logic
```

inside Controller.

---

# 85. CourseMapper

Responsibilities:

```text
CourseCreateRequest
        ↓
Course

Course
        ↓
CourseResponse

Course
        ↓
CourseListResponse
```

Do NOT map client-controlled values into:

```text
isDeleted
createdBy
createdAt
updatedBy
updatedAt
```

---

# 86. Transactions

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

# 87. Redis Cache Management

Recommended caches:

```text
course-cache
course-list-cache
course-summary-cache
```

PostgreSQL remains the source of truth.

---

# 88. Course Detail Cache

Recommended logical key:

```text
course:<id>
```

Example:

```text
course:10
```

Flow:

```text
GET Course
    ↓
Redis
 /       \
HIT      MISS
 ↓         ↓
Return   PostgreSQL
            ↓
          Cache
            ↓
          Return
```

---

# 89. Course List Cache

If implemented, list cache key must include:

```text
search
facultyId
departmentId
programId
semesterId
recommendedAcademicYear
courseType
status
page
size
sort
```

Example concept:

```text
course:list:
<search>:
<facultyId>:
<departmentId>:
<programId>:
<semesterId>:
<academicYear>:
<courseType>:
<status>:
<page>:
<size>:
<sort>
```

If this becomes unnecessarily complex, cache only detail and summary.

---

# 90. Course Summary Cache

Recommended key:

```text
course:summary
```

---

# 91. Cache Eviction - Create

After Create:

```text
Evict Course list cache

Evict Course summary cache
```

---

# 92. Cache Eviction - Update

After Update:

```text
Evict course:<id>

Evict Course list cache

Evict Course summary cache
```

---

# 93. Cache Eviction - Delete

After Delete:

```text
Evict course:<id>

Evict Course list cache

Evict Course summary cache
```

Deleted Course data must never remain visible through stale Redis entries.

---

# 94. Exceptions

Create:

```text
CourseNotFoundException

CourseCodeAlreadyExistsException

InvalidCourseRelationshipException

InvalidPrerequisiteCourseException

InvalidCourseTypeException
```

Location:

```text
feature/course/exception/
```

Global error mapping remains in:

```text
exception/GlobalExceptionHandler.java
```

---

# 95. Error Cases

## Validation

```text
400 Bad Request
```

## Invalid Faculty

```text
400 Bad Request
```

## Invalid Department

```text
400 Bad Request
```

## Department Does Not Belong to Faculty

```text
400 Bad Request
```

## Invalid Program

```text
400 Bad Request
```

## Program Does Not Belong to Department

```text
400 Bad Request
```

## Invalid Semester

```text
400 Bad Request
```

## Invalid Course Type

```text
400 Bad Request
```

## Invalid Prerequisite

```text
400 Bad Request
```

## Course Not Found

```text
404 Not Found
```

## Duplicate Course Code

```text
409 Conflict
```

## Unauthorized

```text
401 Unauthorized
```

## Forbidden

```text
403 Forbidden
```

---

# 96. Duplicate Error Example

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Course code already exists"
}
```

---

# 97. Invalid Relationship Example

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Selected program does not belong to the selected department"
}
```

---

# 98. Invalid Prerequisite Example

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Prerequisite course CS999 does not exist"
}
```

---

# 99. Create Testing

```text
[ ] Valid Course creation succeeds

[ ] Course Code required

[ ] Course Name required

[ ] Credit Hours > 0

[ ] Description optional

[ ] Faculty validation works

[ ] Department validation works

[ ] Department belongs to Faculty

[ ] Program optional

[ ] Program validation works

[ ] Program belongs to Department

[ ] Semester optional

[ ] Semester validation works

[ ] Recommended Academic Year positive

[ ] Course Type 100 accepted

[ ] Course Type 200 accepted

[ ] Course Type 300 accepted

[ ] Invalid Course Type rejected

[ ] Maximum Students > 0

[ ] Prerequisite Course validation works

[ ] Self prerequisite rejected

[ ] Duplicate prerequisite codes normalized

[ ] Duplicate Course Code rejected

[ ] createdBy comes from JWT

[ ] isDeleted defaults FALSE
```

---

# 100. List Testing

```text
[ ] List returns non-deleted Courses

[ ] Search by Course Code works

[ ] Search by Course Name works

[ ] Faculty filter works

[ ] Department filter works

[ ] Program filter works

[ ] Semester filter works

[ ] Academic Year filter works

[ ] Course Type filter works

[ ] Status filter works

[ ] Combined filters work

[ ] Pagination works

[ ] Sorting works

[ ] Deleted Courses excluded
```

---

# 101. Detail Testing

```text
[ ] Existing Course returns 200

[ ] Unknown ID returns 404

[ ] Deleted Course returns 404

[ ] Faculty information returned

[ ] Department information returned

[ ] Program information returned when present

[ ] Semester information returned when present

[ ] Course Type name correctly resolved
```

---

# 102. Update Testing

```text
[ ] Course Code updates

[ ] Course Name updates

[ ] Credit Hours updates

[ ] Description updates

[ ] Faculty updates

[ ] Department updates

[ ] Program updates

[ ] Semester updates

[ ] Recommended Academic Year updates

[ ] Prerequisite Courses update

[ ] Course Type updates

[ ] Maximum Students updates

[ ] Active status updates

[ ] Relationship validations still work

[ ] Duplicate Course Code excludes current Course

[ ] updatedBy comes from JWT

[ ] updatedAt updates

[ ] createdBy remains unchanged

[ ] createdAt remains unchanged
```

---

# 103. Delete Testing

```text
[ ] DELETE performs soft delete

[ ] Physical Course row remains

[ ] isDeleted becomes TRUE

[ ] updatedBy is set

[ ] updatedAt is set

[ ] Deleted Course excluded from list

[ ] Deleted Course returns 404 from normal detail API

[ ] Deleted Course cannot be selected as prerequisite
```

---

# 104. Summary Testing

```text
[ ] totalCourses correct

[ ] activeCourses correct

[ ] inactiveCourses correct

[ ] Deleted Courses excluded

[ ] currentSemesterCourses does not use arbitrary hard-coded Semester ID
```

---

# 105. Redis Testing

```text
[ ] Get By ID can cache Course

[ ] Create invalidates list/summary

[ ] Update invalidates detail/list/summary

[ ] Delete invalidates detail/list/summary

[ ] Deleted Course not returned from stale cache

[ ] PostgreSQL remains source of truth
```

---

# 106. Security Testing

```text
[ ] Missing JWT returns 401

[ ] Invalid JWT returns 401

[ ] Expired JWT returns 401

[ ] Unauthorized role returns 403

[ ] Authorized Admin can create Course

[ ] Authorized Admin can update Course

[ ] Authorized Admin can delete Course

[ ] Client cannot provide createdBy

[ ] Client cannot provide updatedBy
```

---

# 107. Implementation Order

Implement in this order:

```text
1. Verify Course Flyway migration
        ↓
2. Create Course Entity
        ↓
3. Create CourseType Enum
        ↓
4. Create Course Repository
        ↓
5. Create Course DTOs
        ↓
6. Create Course Mapper
        ↓
7. Create Course Exceptions
        ↓
8. Create CourseService
        ↓
9. Create CourseServiceImpl
        ↓
10. Implement Course Code normalization
        ↓
11. Implement Faculty validation
        ↓
12. Implement Department validation
        ↓
13. Implement Faculty/Department consistency
        ↓
14. Implement Program validation
        ↓
15. Implement Program/Department consistency
        ↓
16. Implement Semester validation
        ↓
17. Implement Academic Year validation
        ↓
18. Implement Course Type validation
        ↓
19. Implement Maximum Students validation
        ↓
20. Implement prerequisite parsing
        ↓
21. Implement prerequisite validation
        ↓
22. Implement Create
        ↓
23. Implement List
        ↓
24. Implement Search
        ↓
25. Implement Faculty filter
        ↓
26. Implement Department filter
        ↓
27. Implement Program filter
        ↓
28. Implement Semester filter
        ↓
29. Implement Academic Year filter
        ↓
30. Implement Course Type filter
        ↓
31. Implement Status filter
        ↓
32. Implement Pagination
        ↓
33. Implement Sorting
        ↓
34. Implement Get By ID
        ↓
35. Implement Update
        ↓
36. Implement Soft Delete
        ↓
37. Implement Summary
        ↓
38. Add Redis cache management
        ↓
39. Create CourseController
        ↓
40. Apply authorization
        ↓
41. Add automated tests
        ↓
42. Run Maven tests
        ↓
43. Run Maven package
        ↓
44. Re-check project structure
```

---

# 108. UI Create Workflow

```text
Click Add Course
        ↓
Enter Course Code
        ↓
Enter Credit Hours
        ↓
Enter Course Name
        ↓
Enter Description
        ↓
Select Faculty
        ↓
Load Departments by Faculty
        ↓
Select Department
        ↓
Load Programs by Department
        ↓
Select Program if required
        ↓
Select Recommended Academic Year
        ↓
Select Semester
        ↓
Enter Prerequisite Course Codes
        ↓
Select Course Type
        ↓
Enter Maximum Students per Section
        ↓
Choose Active / Inactive
        ↓
POST /api/v1/admin/courses
        ↓
Success
        ↓
Refresh Course List
        ↓
Refresh Summary
```

---

# 109. Dependent Dropdown Workflow

Faculty:

```text
Select Faculty
    ↓
Load Departments
```

Department:

```text
Select Department
    ↓
Load Programs
```

Example:

```text
Faculty of Information Technology
        ↓
Software Engineering Department
        ↓
Software Engineering Program
```

The backend MUST still validate all relationships.

Frontend filtering alone is not sufficient.

---

# 110. Prerequisite UI Workflow

Recommended UI behavior:

```text
User enters:
CS101, CS201
        ↓
Frontend sends:
CS101,CS201
        ↓
Backend splits
        ↓
Backend validates CS101
        ↓
Backend validates CS201
        ↓
Backend saves normalized string
```

---

# 111. Course Type UI Workflow

Frontend display:

```text
General Education
Major Elective
Major Required
```

Send:

```text
General Education
→ 100

Major Elective
→ 200

Major Required
→ 300
```

---

# 112. Course List Workflow

On page load:

```text
GET /api/v1/admin/courses

GET /api/v1/admin/courses/summary
```

List displays:

```text
Course Code
Course Name
Course Type
Credits
Department
Faculty
Semester
Recommended Year
Sections
Status
Actions
```

---

# 113. Edit Workflow

```text
Actions
    ↓
Edit
    ↓
GET /api/v1/admin/courses/{id}
    ↓
Populate Add/Edit Course form
    ↓
Update values
    ↓
PUT /api/v1/admin/courses/{id}
    ↓
Success
    ↓
Refresh Course List
```

---

# 114. Delete Workflow

```text
Actions
    ↓
Delete
    ↓
Confirmation
    ↓
DELETE /api/v1/admin/courses/{id}
    ↓
Soft Delete
    ↓
Refresh Course List
    ↓
Refresh Summary
```

---

# 115. Final API List

```text
POST
/api/v1/admin/courses

GET
/api/v1/admin/courses

GET
/api/v1/admin/courses/{id}

PUT
/api/v1/admin/courses/{id}

DELETE
/api/v1/admin/courses/{id}

GET
/api/v1/admin/courses/summary
```

Local:

```text
http://localhost:8080/api/v1/admin/courses
```

---

# 116. Acceptance Criteria

## Architecture

```text
[ ] feature/course structure used

[ ] Controller contains no business logic

[ ] Service contains Course business logic

[ ] Repository handles persistence

[ ] Mapper handles DTO/entity conversion

[ ] Feature exceptions remain inside Course feature

[ ] Global exception handler remains global
```

## Course Type

```text
[ ] CourseType enum exists

[ ] GENERAL_EDUCATION = 100

[ ] MAJOR_ELECTIVE = 200

[ ] MAJOR_REQUIRED = 300

[ ] Database stores numeric values

[ ] API returns display name where useful

[ ] Invalid numeric Course Type rejected
```

## Relationships

```text
[ ] Faculty FK used

[ ] Department FK used

[ ] Program FK used when supplied

[ ] Semester FK used when supplied

[ ] Department belongs to Faculty

[ ] Program belongs to Department

[ ] Deleted relationship entities rejected
```

## Prerequisites

```text
[ ] prerequisiteCourses accepts String

[ ] Comma-separated Course Codes supported

[ ] Whitespace normalized

[ ] Duplicate prerequisite codes removed

[ ] Each Course Code validated

[ ] Missing prerequisite Course rejected

[ ] Deleted prerequisite Course rejected

[ ] Course cannot reference itself
```

## Maximum Students

```text
[ ] maximumStudentsPerSection supported

[ ] Value stored in courses

[ ] Positive value validation enforced

[ ] NULL supported
```

## Create

```text
[ ] POST /api/v1/admin/courses works

[ ] Duplicate Course Code rejected

[ ] Default isActive TRUE when omitted

[ ] Audit fields come from authenticated Admin
```

## Read

```text
[ ] GET list works

[ ] GET by ID works

[ ] Search works

[ ] Faculty filter works

[ ] Department filter works

[ ] Program filter works

[ ] Semester filter works

[ ] Academic Year filter works

[ ] Course Type filter works

[ ] Status filter works

[ ] Pagination works

[ ] Sorting works

[ ] Deleted records excluded
```

## Update

```text
[ ] PUT works

[ ] Basic information updates

[ ] Academic relationships update

[ ] Prerequisites update

[ ] Course Type updates

[ ] Maximum Students updates

[ ] Status updates

[ ] Relationship validation reruns

[ ] updatedBy and updatedAt work
```

## Delete

```text
[ ] DELETE works

[ ] Soft delete used

[ ] Physical row remains

[ ] isDeleted becomes TRUE

[ ] Deleted Course excluded from normal APIs
```

## Summary

```text
[ ] Total Course count works

[ ] Active Course count works

[ ] Inactive Course count works

[ ] Deleted Courses excluded
```

## Redis

```text
[ ] Detail cache supported

[ ] Create invalidates appropriate caches

[ ] Update invalidates appropriate caches

[ ] Delete invalidates appropriate caches

[ ] PostgreSQL remains source of truth
```

## Security

```text
[ ] APIs require RS256 JWT

[ ] Authorized roles can manage Courses

[ ] Unauthorized role returns 403

[ ] Client cannot control createdBy

[ ] Client cannot control updatedBy
```

---

# 117. Next Step

After Course CRUD is completed:

```text
Course
    ↓
Course Sections
```

Recommended next feature:

```text
Course Sections Flyway Migration
        ↓
course_id FK
        ↓
Lecture Assignment
        ↓
Section Number
        ↓
Room
        ↓
Schedule
        ↓
Capacity
        ↓
Semester
        ↓
Academic Year
        ↓
Course Section CRUD
```