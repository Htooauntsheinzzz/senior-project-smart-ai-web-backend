# 28 - Student Academic Query API Implementation

## 1. Purpose

Implement the Student-facing Academic API layer for the:

**Smart AI University Student Assistant App**

This is the first backend layer that will later be used by the AI Assistant.

The Student Academic APIs must expose authenticated Student academic information from the existing normalized academic database.

Implement:

```text
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/enrollments

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/lecturers
```

This implementation must work correctly **without AI**.

The future AI Assistant will call these services/tools rather than accessing PostgreSQL directly.

---

# 2. Main Goal

After this feature is complete, the backend should be able to answer deterministic academic questions such as:

```text
What is my academic profile?

What courses am I enrolled in?

Which courses am I taking?

What is my class schedule?

Who are my lecturers?

Which section am I in?

Where is my class?

What semester is my course in?
```

without requiring an LLM.

---

# 3. Architecture

Use:

```text
Student Mobile App
        │
        ↓
Student JWT
        │
        ↓
Student Academic Controller
        │
        ↓
Student Academic Service
        │
        ↓
Existing Academic Repositories / Services
        │
        ↓
PostgreSQL
        │
        ↓
Academic DTO
        │
        ↓
Student Mobile App
```

Later:

```text
AI Assistant
        ↓
Academic Tool Layer
        ↓
Student Academic Service
        ↓
PostgreSQL
```

The AI must reuse this layer.

---

# 4. Important Security Rule

The Student must NEVER send:

```text
studentId
```

as a query parameter or request body field for these APIs.

Do NOT implement:

```text
GET /api/v1/student/academic/profile?studentId=15
```

Do NOT implement:

```text
GET /api/v1/student/15/academic/profile
```

Instead:

```text
Authorization: Bearer <student-access-token>
```

must identify the Student.

Flow:

```text
Access Token
    ↓
JWT Validation
    ↓
studentId claim
    ↓
Authenticated Student
    ↓
Academic Query
```

This prevents one Student from changing an ID and reading another Student's academic information.

---

# 5. Student JWT Requirements

Existing Student RS256 JWT should contain:

```text
sub

studentId

studentCode

roles

iat

exp

jti
```

Expected authority:

```text
ROLE_STUDENT
```

The Academic API must use:

```text
studentId
```

from the authenticated security context / validated JWT.

Do NOT trust:

```text
studentCode supplied by frontend

studentId supplied by frontend

email supplied by frontend
```

for authorization.

---

# 6. Existing Academic Data

Student Academic APIs use existing modules:

```text
students

faculties

departments

programs

semesters

enrollments

course_sections

courses

lectures
```

Relationships:

```text
Student
  │
  ├── Faculty
  │
  ├── Department
  │
  ├── Program
  │
  ├── Current Semester
  │
  └── Enrollments
          │
          ↓
     Course Section
       /    |    \
      ↓     ↓     ↓
   Course Semester Lecturer
```

---

# 7. Database Source of Truth

PostgreSQL remains:

```text
source of truth
```

Redis may later be used for Student academic/profile caching.

For the first implementation:

```text
Prefer correctness first.

Implement PostgreSQL queries first.

Add cache only after all APIs are correct.
```

---

# 8. Student Eligibility for Academic APIs

For normal academic API access, authenticated Student must:

```text
exist

is_deleted = FALSE

account_status = ACTIVE

student_credentials exists
```

If:

```text
PENDING
INACTIVE
SUSPENDED
deleted
```

Student must not access normal academic data.

Recommended:

```text
403 Forbidden
STUDENT_ACCOUNT_NOT_ACTIVE
```

If:

```text
force_password_change = TRUE
```

follow the existing Student security policy.

If the existing project allows only:

```text
/me
/change-password
/logout
```

while force-password-change is active, then these Academic endpoints must also be blocked until the password is changed.

---

# 9. API Base Path

Use:

```text
/api/v1/student/academic
```

Endpoints:

```text
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/enrollments

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/lecturers
```

All require:

```text
ROLE_STUDENT
```

---

# 10. Feature Package Structure

Recommended:

```text
feature/
└── studentacademic/
    ├── controller/
    │   └── StudentAcademicController.java
    │
    ├── dto/
    │   ├── StudentAcademicProfileResponse.java
    │   ├── StudentAcademicEnrollmentResponse.java
    │   ├── StudentAcademicCourseResponse.java
    │   ├── StudentAcademicScheduleResponse.java
    │   ├── StudentAcademicLecturerResponse.java
    │   ├── AcademicReferenceResponse.java
    │   └── AcademicSemesterResponse.java
    │
    ├── mapper/
    │   └── StudentAcademicMapper.java
    │
    ├── service/
    │   ├── StudentAcademicService.java
    │   └── impl/
    │       └── StudentAcademicServiceImpl.java
    │
    └── exception/
        ├── StudentAcademicProfileNotFoundException.java
        └── StudentAcademicDataUnavailableException.java
```

If the project already has:

```text
feature/student/
```

the Academic submodule may instead be placed under:

```text
feature/student/academic/
```

Follow the current project package convention.

---

# 11. Implementation Order

Implement strictly in this order:

```text
PHASE 1
Inspect Existing Student / Enrollment / Course APIs
        ↓
PHASE 2
Create StudentAcademicService
        ↓
PHASE 3
Implement Current Student Resolver
        ↓
PHASE 4
Implement Profile API
        ↓
PHASE 5
Implement Enrollments API
        ↓
PHASE 6
Implement Courses API
        ↓
PHASE 7
Implement Schedule API
        ↓
PHASE 8
Implement Lecturers API
        ↓
PHASE 9
Add Security Rules
        ↓
PHASE 10
Add DTO Mapping
        ↓
PHASE 11
Add Error Handling
        ↓
PHASE 12
Add Read-Only Transactions
        ↓
PHASE 13
Add Tests
        ↓
PHASE 14
Add Optional Redis Cache
        ↓
PHASE 15
Final Integration Test
```

---

# 12. StudentAcademicService Interface

Recommended:

```java
public interface StudentAcademicService {

    StudentAcademicProfileResponse getMyProfile();

    List<StudentAcademicEnrollmentResponse>
        getMyEnrollments(String status);

    List<StudentAcademicCourseResponse>
        getMyCourses(String status);

    List<StudentAcademicScheduleResponse>
        getMySchedule(String status);

    List<StudentAcademicLecturerResponse>
        getMyLecturers(String status);
}
```

Alternative if the current project explicitly passes authentication context into services:

```java
public interface StudentAcademicService {

    StudentAcademicProfileResponse
        getProfile(Long studentId);

    List<StudentAcademicEnrollmentResponse>
        getEnrollments(Long studentId, String status);

    List<StudentAcademicCourseResponse>
        getCourses(Long studentId, String status);

    List<StudentAcademicScheduleResponse>
        getSchedule(Long studentId, String status);

    List<StudentAcademicLecturerResponse>
        getLecturers(Long studentId, String status);
}
```

Recommended architecture:

```text
Controller / CurrentStudentProvider
        ↓
resolve studentId
        ↓
Service receives studentId
```

This keeps Service methods reusable for the future AI Tool layer.

---

# 13. Current Student Resolver

Create or reuse a shared component such as:

```text
CurrentStudentProvider
```

Responsibility:

```text
Read SecurityContext

Validate Student authentication

Read studentId JWT claim

Return authenticated Student ID
```

Example concept:

```java
public interface CurrentStudentProvider {

    Long getCurrentStudentId();

}
```

Do not repeat JWT parsing code inside every Controller method.

---

# 14. Current Student Validation

Recommended shared method:

```java
private Student findCurrentActiveStudent(Long studentId) {

    Student student =
        studentRepository
            .findByIdAndIsDeletedFalse(studentId)
            .orElseThrow(
                StudentAcademicProfileNotFoundException::new
            );

    if (student.getAccountStatus()
        != StudentAccountStatus.ACTIVE) {

        throw new StudentAccountNotActiveException();
    }

    return student;
}
```

Reuse existing Student domain exceptions where possible.

Do not create duplicate exception classes if the Student module already has the required exception.

---

# 15. API 1 — Academic Profile

Endpoint:

```text
GET /api/v1/student/academic/profile
```

Purpose:

Return the authenticated Student's academic identity.

Information:

```text
Student ID

Student Code

Name

University Email

Faculty

Department

Program

Current Semester

Academic Year

Enrollment Year

Account Status
```

---

# 16. Academic Profile Response DTO

Recommended:

```java
public record StudentAcademicProfileResponse(

    Long studentId,

    String studentCode,

    String firstName,

    String lastName,

    String fullName,

    String universityEmail,

    String phoneNumber,

    LocalDate dateOfBirth,

    AcademicReferenceResponse faculty,

    AcademicReferenceResponse department,

    AcademicReferenceResponse program,

    AcademicSemesterResponse semester,

    Integer academicYear,

    Integer enrollmentYear,

    String accountStatus

) {}
```

---

# 17. AcademicReferenceResponse

Recommended:

```java
public record AcademicReferenceResponse(

    Long id,

    String code,

    String nameEn,

    String nameTh

) {}
```

If a module only has one name:

```text
use the fields that really exist
```

Do NOT invent:

```text
nameTh
code
```

if the actual table/entity does not contain them.

Inspect existing entities before mapping.

---

# 18. AcademicSemesterResponse

Recommended:

```java
public record AcademicSemesterResponse(

    Long id,

    String semesterNameEn,

    String semesterNameTh

) {}
```

---

# 19. Profile Workflow

```text
GET /academic/profile
        ↓
Resolve Student ID from JWT
        ↓
Find non-deleted Student
        ↓
Require ACTIVE
        ↓
Load Faculty
        ↓
Load Department
        ↓
Load Program
        ↓
Load Semester if present
        ↓
Map DTO
        ↓
200 OK
```

---

# 20. Profile Response Example

```json
{
  "success": true,
  "message": "Academic profile retrieved successfully",
  "data": {
    "studentId": 15,
    "studentCode": "STD-2024-0001",
    "firstName": "Thanapon",
    "lastName": "Srisuk",
    "fullName": "Thanapon Srisuk",
    "universityEmail": "thanapon.s@rsu.ac.th",
    "phoneNumber": "0812345678",
    "dateOfBirth": "2004-05-12",

    "faculty": {
      "id": 1,
      "code": "ICT",
      "nameEn": "College of Information and Communication Technology",
      "nameTh": null
    },

    "department": {
      "id": 3,
      "code": "CS",
      "nameEn": "Computer Science",
      "nameTh": null
    },

    "program": {
      "id": 5,
      "code": "BSC-CS",
      "nameEn": "Bachelor of Science in Computer Science",
      "nameTh": null
    },

    "semester": {
      "id": 1,
      "semesterNameEn": "Semester 1",
      "semesterNameTh": "ภาคการศึกษาที่ 1"
    },

    "academicYear": 2026,
    "enrollmentYear": 2024,
    "accountStatus": "ACTIVE"
  },
  "timestamp": "2026-10-07T15:30:00"
}
```

---

# 21. API 2 — My Enrollments

Endpoint:

```text
GET /api/v1/student/academic/enrollments
```

Purpose:

Return the authenticated Student's Enrollment records.

Default behavior:

```text
return ACTIVE enrollments
```

Recommended optional filter:

```text
status
```

Example:

```text
GET /api/v1/student/academic/enrollments?status=ACTIVE
```

Possible:

```text
PENDING
ACTIVE
WITHDRAWN
DROPPED
ALL
```

`ALL` is request-only convenience and is NOT stored in the database.

---

# 22. Enrollment Response DTO

Recommended:

```java
public record StudentAcademicEnrollmentResponse(

    Long enrollmentId,

    LocalDate enrollmentDate,

    String enrollmentStatus,

    Long courseId,

    String courseCode,

    String courseName,

    Integer creditHours,

    Integer courseType,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterNameEn,

    String semesterNameTh,

    Long lecturerId,

    String lecturerName,

    String room,

    String schedule

) {}
```

Only expose fields that actually exist.

Do not invent missing database values.

---

# 23. Enrollment Query Relationship

Use:

```text
Enrollment
    ↓
Student
```

and:

```text
Enrollment
    ↓
Course Section
    ├── Course
    ├── Semester
    └── Lecturer
```

Do NOT query:

```text
course from Student

semester from Student
```

for Enrollment-level data.

The Enrollment's Semester comes from:

```text
course_sections.semester_id
```

---

# 24. Enrollment Filtering

Normal Student view:

```text
is_deleted = FALSE
```

Default:

```text
status = ACTIVE
```

Optional query:

```text
?status=PENDING

?status=WITHDRAWN

?status=DROPPED

?status=ALL
```

Normalize case at service boundary.

---

# 25. Enrollment Workflow

```text
GET /academic/enrollments
        ↓
Resolve Student ID
        ↓
Validate Student ACTIVE
        ↓
Query Enrollment
        ↓
Require enrollment.is_deleted = FALSE
        ↓
Apply requested status
        ↓
Join Course Section
        ↓
Join Course
        ↓
Join Semester
        ↓
Join Lecturer
        ↓
Map DTO
        ↓
Return list
```

---

# 26. Enrollment Response Example

```json
{
  "success": true,
  "message": "Academic enrollments retrieved successfully",
  "data": [
    {
      "enrollmentId": 45,
      "enrollmentDate": "2026-08-15",
      "enrollmentStatus": "ACTIVE",

      "courseId": 3,
      "courseCode": "CS301",
      "courseName": "Data Structures & Algorithms",
      "creditHours": 3,
      "courseType": 300,

      "courseSectionId": 8,
      "sectionNumber": "SEC-01",

      "semesterId": 1,
      "semesterNameEn": "Semester 1",
      "semesterNameTh": "ภาคการศึกษาที่ 1",

      "lecturerId": 7,
      "lecturerName": "Dr. John Smith",

      "room": "11-402",
      "schedule": "Tuesday 09:00-11:45"
    }
  ],
  "timestamp": "2026-10-07T15:30:00"
}
```

---

# 27. Empty Enrollment Result

No Enrollment is NOT an error.

Return:

```json
{
  "success": true,
  "message": "Academic enrollments retrieved successfully",
  "data": [],
  "timestamp": "2026-10-07T15:30:00"
}
```

Do NOT return:

```text
404
```

for an empty list.

---

# 28. API 3 — My Courses

Endpoint:

```text
GET /api/v1/student/academic/courses
```

Purpose:

Return Course-oriented data for the authenticated Student.

Unlike:

```text
/enrollments
```

which is Enrollment-focused,

```text
/courses
```

returns a simplified Course-focused view.

---

# 29. Course Response DTO

Recommended:

```java
public record StudentAcademicCourseResponse(

    Long courseId,

    String courseCode,

    String courseName,

    Integer creditHours,

    String description,

    Integer courseType,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName,

    String enrollmentStatus

) {}
```

If:

```text
credit_hours
```

is not Integer in the actual entity, use the actual Java type.

---

# 30. Course API Default Behavior

Default:

```text
ACTIVE Enrollments only
```

Optional:

```text
status
```

Example:

```text
GET /api/v1/student/academic/courses?status=ACTIVE
```

Possible request values:

```text
PENDING
ACTIVE
WITHDRAWN
DROPPED
ALL
```

---

# 31. Courses Workflow

```text
GET /academic/courses
        ↓
Student ID from JWT
        ↓
Validate Student
        ↓
Find Student Enrollments
        ↓
Filter status
        ↓
Enrollment → Section
        ↓
Section → Course
        ↓
Map Course DTO
        ↓
Return list
```

---

# 32. Courses Response Example

```json
{
  "success": true,
  "message": "Academic courses retrieved successfully",
  "data": [
    {
      "courseId": 3,
      "courseCode": "CS301",
      "courseName": "Data Structures & Algorithms",
      "creditHours": 3,
      "description": "Introduction to fundamental data structures and algorithms.",
      "courseType": 300,
      "courseSectionId": 8,
      "sectionNumber": "SEC-01",
      "semesterId": 1,
      "semesterName": "Semester 1",
      "enrollmentStatus": "ACTIVE"
    }
  ],
  "timestamp": "2026-10-07T15:30:00"
}
```

---

# 33. Avoid Course Duplicates

A Student should normally have one active Enrollment per:

```text
Course + Semester
```

because Enrollment validation already prevents multiple active/pending Sections for the same Course/Semester.

Still, the Course API should handle data defensively.

Do not return accidental duplicate Course entries caused by:

```text
bad legacy data
```

without understanding their Enrollment records.

Prefer:

```text
one item per Enrollment/Section
```

unless the project explicitly wants one item per unique Course.

For AI tools later, Enrollment-based context is usually safer because it preserves Section and Semester.

---

# 34. API 4 — My Schedule

Endpoint:

```text
GET /api/v1/student/academic/schedule
```

Purpose:

Return class schedule information for the authenticated Student.

Initial version should use the schedule data already stored in:

```text
course_sections.schedule
```

Do NOT create AI schedule interpretation in this step.

---

# 35. Important Schedule Limitation

Current Course Section design stores:

```text
schedule
```

as a field.

If it is a free-text String such as:

```text
Tuesday 09:00-11:45
```

the backend can reliably:

```text
return it
```

but should NOT yet implement complex logic such as:

```text
What is my next class?

What do I have tomorrow?

Do I have class at 2 PM?

Detect timetable conflicts
```

unless the schedule has already been normalized.

These features should eventually use a normalized table such as:

```text
course_section_schedules

id
course_section_id
day_of_week
start_time
end_time
room
```

For Step 2, return current Section schedule data as-is.

---

# 36. Schedule Response DTO

Recommended:

```java
public record StudentAcademicScheduleResponse(

    Long enrollmentId,

    Long courseId,

    String courseCode,

    String courseName,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName,

    String schedule,

    String room,

    Long lecturerId,

    String lecturerName,

    String enrollmentStatus

) {}
```

---

# 37. Schedule API Default Filter

Return:

```text
ACTIVE
```

Enrollments by default.

Optional:

```text
status
```

should primarily be used for administrative/testing purposes.

The Student's normal schedule should generally show:

```text
ACTIVE classes
```

not:

```text
WITHDRAWN
DROPPED
```

---

# 38. Schedule Workflow

```text
GET /academic/schedule
        ↓
Resolve authenticated Student
        ↓
Find ACTIVE Enrollments
        ↓
Enrollment → Course Section
        ↓
Course Section → Course
        ↓
Course Section → Semester
        ↓
Course Section → Lecturer
        ↓
Return Schedule + Room
```

---

# 39. Schedule Response Example

```json
{
  "success": true,
  "message": "Academic schedule retrieved successfully",
  "data": [
    {
      "enrollmentId": 45,

      "courseId": 3,
      "courseCode": "CS301",
      "courseName": "Data Structures & Algorithms",

      "courseSectionId": 8,
      "sectionNumber": "SEC-01",

      "semesterId": 1,
      "semesterName": "Semester 1",

      "schedule": "Tuesday 09:00-11:45",
      "room": "11-402",

      "lecturerId": 7,
      "lecturerName": "Dr. John Smith",

      "enrollmentStatus": "ACTIVE"
    }
  ],
  "timestamp": "2026-10-07T15:30:00"
}
```

---

# 40. API 5 — My Lecturers

Endpoint:

```text
GET /api/v1/student/academic/lecturers
```

Purpose:

Return lecturers teaching the authenticated Student's enrolled Course Sections.

Relationship:

```text
Student
    ↓
Enrollment
    ↓
Course Section
    ↓
Lecture
```

---

# 41. Lecturer Response DTO

Recommended:

```java
public record StudentAcademicLecturerResponse(

    Long lecturerId,

    String lecturerNameEn,

    String lecturerNameTh,

    String lecturerNickname,

    Long courseId,

    String courseCode,

    String courseName,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName

) {}
```

Use the actual existing Lecturer entity fields.

If existing table uses:

```text
lecture_name_en
lecture_name_th
lecture_nickname
```

map those exact fields.

---

# 42. Lecturer API Default Filter

Use:

```text
ACTIVE Enrollments only
```

Do not show lecturers only associated with:

```text
WITHDRAWN

DROPPED
```

Enrollment by default.

---

# 43. Lecturer Workflow

```text
GET /academic/lecturers
        ↓
Authenticated Student
        ↓
Find ACTIVE Enrollments
        ↓
Course Section
        ↓
Lecturer
        ↓
Course
        ↓
Semester
        ↓
Map Lecturer DTO
        ↓
Return
```

---

# 44. Lecturer Response Example

```json
{
  "success": true,
  "message": "Academic lecturers retrieved successfully",
  "data": [
    {
      "lecturerId": 7,
      "lecturerNameEn": "Dr. John Smith",
      "lecturerNameTh": null,
      "lecturerNickname": "John",

      "courseId": 3,
      "courseCode": "CS301",
      "courseName": "Data Structures & Algorithms",

      "courseSectionId": 8,
      "sectionNumber": "SEC-01",

      "semesterId": 1,
      "semesterName": "Semester 1"
    }
  ],
  "timestamp": "2026-10-07T15:30:00"
}
```

---

# 45. Lecturer Deduplication Rule

One Lecturer may teach multiple Student courses.

Example:

```text
Dr. Smith
→ CS301
→ CS401
```

For this endpoint, recommended response is:

```text
one Lecturer-Course-Section relationship per row
```

because the future AI needs to know:

```text
which lecturer teaches which course
```

Do NOT return only:

```text
unique lecturer name list
```

because that loses course context.

---

# 46. Recommended Repository Strategy

Do not automatically create five complex repositories.

Reuse:

```text
StudentRepository

EnrollmentRepository

CourseSectionRepository
```

where possible.

The Student Academic feature should mostly orchestrate existing normalized data.

---

# 47. Enrollment Repository Query for Student

Add if not already available:

```java
List<Enrollment>
findByStudentIdAndIsDeletedFalse(
    Long studentId
);
```

Better with status:

```java
List<Enrollment>
findByStudentIdAndStatusAndIsDeletedFalse(
    Long studentId,
    EnrollmentStatus status
);
```

For production list performance, prefer fetch joins/entity graphs or projection queries when necessary.

---

# 48. Avoid N+1 Queries

These APIs traverse:

```text
Enrollment
→ Course Section
→ Course
→ Semester
→ Lecturer
```

Do not create:

```text
1 Enrollment query
+
N Course Section queries
+
N Course queries
+
N Semester queries
+
N Lecturer queries
```

Use:

```text
JOIN FETCH

@EntityGraph

DTO projection
```

consistent with the existing project.

---

# 49. Recommended Enrollment Fetch Query

Concept:

```java
@Query("""
    SELECT e
    FROM Enrollment e

    JOIN FETCH e.student s

    JOIN FETCH e.courseSection cs

    JOIN FETCH cs.course c

    JOIN FETCH cs.semester sem

    LEFT JOIN FETCH cs.lecture l

    WHERE s.id = :studentId
      AND e.isDeleted = false
      AND e.status = :status
""")
List<Enrollment> findAcademicEnrollments(
    @Param("studentId") Long studentId,
    @Param("status") EnrollmentStatus status
);
```

Adjust property names to actual entities.

Do not blindly copy if existing entity names differ.

---

# 50. ALL Status Query

When:

```text
status = ALL
```

do NOT map `ALL` to:

```text
EnrollmentStatus
```

because it does not exist in the DB enum.

Service:

```text
if status == ALL
    query all non-deleted Student Enrollments
else
    parse EnrollmentStatus
```

---

# 51. Status Parsing

Create reusable parser:

```java
private EnrollmentStatus parseEnrollmentStatus(
        String value) {

    try {

        return EnrollmentStatus.valueOf(
            value.trim().toUpperCase(Locale.ROOT)
        );

    } catch (Exception ex) {

        throw new InvalidEnrollmentStatusException();
    }
}
```

Default:

```text
ACTIVE
```

when parameter omitted.

---

# 52. Student Academic Controller

Recommended:

```java
@RestController
@RequestMapping("/api/v1/student/academic")
public class StudentAcademicController {

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        // ...
    }

    @GetMapping("/enrollments")
    public ResponseEntity<?> getEnrollments(
            @RequestParam(
                required = false,
                defaultValue = "ACTIVE"
            )
            String status) {
        // ...
    }

    @GetMapping("/courses")
    public ResponseEntity<?> getCourses(
            @RequestParam(
                required = false,
                defaultValue = "ACTIVE"
            )
            String status) {
        // ...
    }

    @GetMapping("/schedule")
    public ResponseEntity<?> getSchedule() {
        // ...
    }

    @GetMapping("/lecturers")
    public ResponseEntity<?> getLecturers() {
        // ...
    }
}
```

Schedule and Lecturer endpoints may simply use ACTIVE records by default.

---

# 53. Recommended Service Reuse

Do not duplicate query logic five times.

Create an internal helper such as:

```text
getStudentAcademicEnrollments(
    studentId,
    requestedStatus
)
```

Then reuse it for:

```text
/enrollments

/courses

/schedule

/lecturers
```

Example flow:

```text
getAcademicEnrollments()
      │
      ├── Enrollment DTO mapping
      ├── Course DTO mapping
      ├── Schedule DTO mapping
      └── Lecturer DTO mapping
```

This reduces inconsistent behavior.

---

# 54. Read-Only Transactions

Use:

```java
@Transactional(readOnly = true)
```

for all five APIs.

Methods:

```text
getProfile

getEnrollments

getCourses

getSchedule

getLecturers
```

No Student Academic GET endpoint should mutate:

```text
students

enrollments

courses

course_sections
```

---

# 55. API Response Wrapper

Reuse existing project response wrapper.

Success structure:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "timestamp": "2026-10-07T15:30:00"
}
```

Do not create another response-wrapper standard specifically for Academic APIs.

---

# 56. Error Response

Reuse:

```json
{
  "success": false,
  "code": "STUDENT_ACCOUNT_NOT_ACTIVE",
  "message": "Student account is not active",
  "fieldErrors": null,
  "timestamp": "2026-10-07T15:30:00",
  "path": "/api/v1/student/academic/profile"
}
```

---

# 57. Required Error Codes

Reuse existing Student codes whenever possible:

```text
STUDENT_NOT_FOUND

STUDENT_ACCOUNT_NOT_ACTIVE

INVALID_STUDENT_ACCOUNT_STATE

PASSWORD_CHANGE_REQUIRED

UNAUTHORIZED

FORBIDDEN

INVALID_ENROLLMENT_STATUS
```

Add only if needed:

```text
STUDENT_ACADEMIC_DATA_UNAVAILABLE
```

Avoid creating separate errors such as:

```text
COURSES_NOT_FOUND

LECTURERS_NOT_FOUND

SCHEDULE_NOT_FOUND
```

for empty list results.

Empty academic lists are legitimate.

---

# 58. HTTP Contracts

| Condition | HTTP |
|---|---:|
| Profile success | `200` |
| Empty Enrollments | `200` |
| Empty Courses | `200` |
| Empty Schedule | `200` |
| Empty Lecturers | `200` |
| Invalid status filter | `400` |
| Missing/invalid JWT | `401` |
| Student not allowed | `403` |
| Password change required | `403` |
| Student record missing | `404` |
| Invalid internal Student state | `409` |

---

# 59. Security Configuration

Protect:

```text
/api/v1/student/academic/**
```

with:

```text
ROLE_STUDENT
```

Concept:

```java
.requestMatchers(
    "/api/v1/student/academic/**"
)
.hasRole("STUDENT")
```

Follow current Spring Security convention.

---

# 60. No Admin Role Access Through Student APIs

Do not automatically let:

```text
ADMIN

SUPER_ADMIN

ACADEMIC_ADMIN
```

call Student-specific:

```text
/api/v1/student/academic/**
```

unless explicitly impersonating a Student through a separate secure Admin feature.

Admin academic access should remain under:

```text
/api/v1/admin/**
```

---

# 61. Profile Data Privacy

Student Academic APIs may return the authenticated Student's own:

```text
Student Code
Name
University Email
Academic Program
Enrollment information
Schedule
Lecturer information
```

Do not return:

```text
password_hash

failed_login_attempts

locked_until

refresh-token information

other Students' enrollment data

Admin audit internals unless necessary
```

---

# 62. Schedule Privacy

Schedule response should expose only:

```text
authenticated Student's enrolled Course Sections
```

Do not expose:

```text
full Student roster

other Students' schedules
```

---

# 63. Redis Cache - Initial Recommendation

For Step 2:

```text
Implement APIs first without Redis.
```

After correctness is verified, optionally cache:

```text
Student Academic Profile

Active Enrollment View
```

Recommended future logical keys:

```text
student:academic:profile:<studentId>

student:academic:enrollments:<studentId>
```

But avoid premature cache complexity.

---

# 64. Future Cache Invalidation

If caching is added, invalidate academic cache when:

```text
Student profile updated

Student status changed

Enrollment created

Enrollment updated

Enrollment status changed

Enrollment soft-deleted

Course updated

Course Section updated

Semester updated

Lecturer updated
```

This is why Step 2 should work from PostgreSQL first.

---

# 65. Future AI Tool Mapping

These APIs/services will become the basis for AI tools.

Mapping:

```text
GET /academic/profile
→ get_my_academic_profile
```

```text
GET /academic/enrollments
→ get_my_enrollments
```

```text
GET /academic/courses
→ get_my_courses
```

```text
GET /academic/schedule
→ get_my_schedule
```

```text
GET /academic/lecturers
→ get_my_lecturers
```

AI should call:

```text
Service / Tool Layer
```

not make raw HTTP requests internally unless the architecture explicitly uses service-to-service APIs.

---

# 66. Example Future AI Question Mapping

Question:

```text
"What program am I in?"
```

Use:

```text
get_my_academic_profile
```

---

Question:

```text
"What courses am I enrolled in?"
```

Use:

```text
get_my_enrollments
```

or:

```text
get_my_courses
```

---

Question:

```text
"What is my schedule?"
```

Use:

```text
get_my_schedule
```

---

Question:

```text
"Who are my lecturers?"
```

Use:

```text
get_my_lecturers
```

---

# 67. Important Step 2 Boundary

Do NOT implement:

```text
LLM API

OpenAI/Kimi/Claude integration

Prompt engineering

Embeddings

Vector Database

RAG

Curriculum PDF processing

Intent classification
```

inside this Step 2 feature.

This step is:

```text
Academic Data Access Layer
```

only.

AI comes after this layer is correct.

---

# 68. Profile Tests

```text
[ ] ACTIVE Student receives academic profile

[ ] Student ID comes from JWT

[ ] Request cannot override Student ID

[ ] Faculty returned

[ ] Department returned

[ ] Program returned

[ ] Semester returned when present

[ ] Academic Year returned

[ ] Enrollment Year returned

[ ] Deleted Student rejected

[ ] INACTIVE Student rejected

[ ] SUSPENDED Student rejected

[ ] PENDING Student rejected

[ ] Password-change restriction respected
```

---

# 69. Enrollment API Tests

```text
[ ] ACTIVE enrollments returned by default

[ ] PENDING filter works

[ ] ACTIVE filter works

[ ] WITHDRAWN filter works

[ ] DROPPED filter works

[ ] ALL filter works

[ ] Soft-deleted Enrollments excluded

[ ] Course derived correctly

[ ] Section derived correctly

[ ] Semester derived correctly

[ ] Lecturer derived correctly

[ ] Empty list returns 200

[ ] Other Students' Enrollments never returned

[ ] Invalid status returns 400
```

---

# 70. Course API Tests

```text
[ ] Courses come only from Student Enrollments

[ ] ACTIVE is default

[ ] Course Code returned

[ ] Course Name returned

[ ] Credit Hours returned

[ ] Course Type returned

[ ] Section returned

[ ] Semester returned

[ ] Enrollment Status returned

[ ] Soft-deleted Enrollment excluded

[ ] Empty list returns 200
```

---

# 71. Schedule API Tests

```text
[ ] Only authenticated Student schedule returned

[ ] ACTIVE Enrollments used

[ ] Course returned

[ ] Section returned

[ ] Semester returned

[ ] Schedule returned

[ ] Room returned

[ ] Lecturer returned

[ ] Empty schedule returns 200

[ ] Withdrawn Enrollment excluded by default

[ ] Dropped Enrollment excluded by default
```

---

# 72. Lecturer API Tests

```text
[ ] Lecturer returned from Course Section

[ ] Course context returned

[ ] Section context returned

[ ] Semester context returned

[ ] ACTIVE Enrollments only by default

[ ] Lecturer relationships remain distinguishable

[ ] Empty lecturer list returns 200
```

---

# 73. Security Tests

```text
[ ] No JWT → 401

[ ] Invalid JWT → 401

[ ] Expired JWT → 401

[ ] Admin JWT without ROLE_STUDENT rejected

[ ] Student JWT accepted

[ ] Student cannot specify another studentId

[ ] Student cannot access another Student's data
```

---

# 74. Query Performance Tests

Verify no major N+1 problem.

For a Student with:

```text
10 Enrollments
```

the backend should not issue:

```text
50+ avoidable SQL queries
```

Inspect SQL in development/test.

Use:

```text
JOIN FETCH

EntityGraph

DTO Projection
```

if required.

---

# 75. Recommended Controller Flow

```text
HTTP Request
    ↓
Spring Security
    ↓
Validated Student JWT
    ↓
CurrentStudentProvider
    ↓
studentId
    ↓
StudentAcademicService
    ↓
Repository
    ↓
PostgreSQL
    ↓
Mapper
    ↓
DTO
    ↓
ApiResponse
```

---

# 76. Recommended Service Flow

Common:

```text
studentId
    ↓
findCurrentActiveStudent()
    ↓
Academic Query
    ↓
Map normalized relationships
    ↓
DTO
```

Avoid:

```text
Controller
→ Repository directly
```

Controllers should call Services only.

---

# 77. Final API Contract

## Profile

```text
GET
/api/v1/student/academic/profile
```

Returns authenticated Student academic profile.

---

## Enrollments

```text
GET
/api/v1/student/academic/enrollments
```

Optional:

```text
?status=ACTIVE
```

Returns Enrollment-focused information.

---

## Courses

```text
GET
/api/v1/student/academic/courses
```

Optional:

```text
?status=ACTIVE
```

Returns Course-focused information.

---

## Schedule

```text
GET
/api/v1/student/academic/schedule
```

Returns ACTIVE Course Section schedule information.

---

## Lecturers

```text
GET
/api/v1/student/academic/lecturers
```

Returns lecturers associated with ACTIVE Student Enrollments.

---

# 78. Final Development Workflow

```text
Existing Academic Management
        ↓
Student Authentication
        ↓
Current Student Resolver
        ↓
Student Academic Profile
        ↓
My Enrollments
        ↓
My Courses
        ↓
My Schedule
        ↓
My Lecturers
        ↓
Security Tests
        ↓
Repository Performance Tests
        ↓
Integration Tests
        ↓
Ready for AI Tool Layer
```

---

# 79. Acceptance Criteria

```text
SECURITY
[ ] Student ID resolved from JWT
[ ] No studentId request parameter
[ ] ROLE_STUDENT required
[ ] Other Student data inaccessible

PROFILE
[ ] Profile endpoint implemented
[ ] Faculty returned
[ ] Department returned
[ ] Program returned
[ ] Semester returned
[ ] Academic year returned
[ ] Enrollment year returned

ENROLLMENTS
[ ] Enrollment endpoint implemented
[ ] Default ACTIVE
[ ] Status filters supported
[ ] Course derived
[ ] Section derived
[ ] Semester derived
[ ] Lecturer derived

COURSES
[ ] Course endpoint implemented
[ ] Courses derived through Enrollment
[ ] Credit information returned
[ ] Section information retained
[ ] Semester returned

SCHEDULE
[ ] Schedule endpoint implemented
[ ] ACTIVE classes returned
[ ] Room returned
[ ] Schedule returned
[ ] Lecturer returned
[ ] No unsupported timetable inference

LECTURERS
[ ] Lecturer endpoint implemented
[ ] Lecturer-Course relationship returned
[ ] Section context returned
[ ] Semester context returned

ARCHITECTURE
[ ] Controller does not access Repository directly
[ ] Service layer owns queries
[ ] DTOs used
[ ] Entities not exposed
[ ] @Transactional(readOnly = true) used
[ ] PostgreSQL remains source of truth

PERFORMANCE
[ ] N+1 query problem checked
[ ] Fetch strategy optimized if necessary

ERROR HANDLING
[ ] Empty lists return 200
[ ] Invalid status returns 400
[ ] Inactive Student returns 403
[ ] Invalid JWT returns 401

TESTING
[ ] Profile tests pass
[ ] Enrollment tests pass
[ ] Course tests pass
[ ] Schedule tests pass
[ ] Lecturer tests pass
[ ] Security tests pass
[ ] Integration tests pass

BUILD
[ ] mvn test passes
[ ] mvn clean package passes
```

---

# 80. Next Step After Completion

Once all five Academic APIs work correctly:

```text
GET /academic/profile

GET /academic/enrollments

GET /academic/courses

GET /academic/schedule

GET /academic/lecturers
```

move to:

# Step 3 — Academic Query Service for AI Tools

The next architecture becomes:

```text
Student Question
        ↓
AI Assistant
        ↓
Academic Tool
        ↓
Student Academic Service
        ↓
PostgreSQL
        ↓
Structured Academic Data
        ↓
AI Response
```

Do not begin Curriculum RAG until this Academic Data Access layer is stable and tested.