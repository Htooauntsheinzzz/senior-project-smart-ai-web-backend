# 29 - Academic Query Service for AI Tools

## 1. Purpose

Implement the reusable **Academic Query Service** for the:

**Smart AI University Student Assistant App**

This is:

```text
Step 3
→ Academic Query Service
```

The purpose of this layer is to provide safe, deterministic academic functions that will later be exposed as AI tools.

This step does NOT implement:

```text
LLM provider integration

OpenAI / Claude / Kimi API

Prompt engineering

RAG

Embeddings

Vector database

Curriculum PDF upload

Conversation memory
```

This step implements only:

```text
Reusable Academic Query Service
```

---

# 2. Prerequisite

Step 2 must already be complete.

Existing Student Academic APIs:

```text
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/enrollments

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/lecturers
```

Existing academic modules:

```text
Student

Faculty

Department

Program

Semester

Course

Course Section

Lecturer

Enrollment
```

---

# 3. Main Goal

Create service methods that the future AI Assistant can call safely.

Required methods:

```text
getMyAcademicProfile()

getMyEnrollments()

getMyCourses()

getMySchedule()

getMyCourseSection()

getMyCourseLecturer()

getCourseInformation()

getSectionAvailability()
```

These methods must:

```text
Use authenticated Student context

Reuse existing services/repositories

Return structured DTOs

Never expose JPA Entities

Never allow raw SQL from AI

Never accept arbitrary Student IDs from AI

Never expose another Student's private data
```

---

# 4. Target Architecture

Final architecture after Step 3:

```text
Student Mobile App
        │
        ↓
AI Assistant
        │
        ↓
Academic AI Tool Layer
        │
        ↓
AcademicQueryService
        │
        ├── Student Academic Service
        ├── Enrollment Service
        ├── Course Service
        ├── Course Section Service
        └── Lecturer Service
                │
                ↓
            PostgreSQL
```

Important:

```text
AI
↓
Academic Query Service
```

NOT:

```text
AI
↓
Repository
```

and NOT:

```text
AI
↓
PostgreSQL SQL
```

---

# 5. Why This Layer Is Needed

Without an Academic Query Service:

```text
AI
→ Controller
→ Repository
→ DB
```

can create:

```text
Tight coupling

Security problems

Duplicate business logic

Inconsistent responses

AI-specific code inside Controller

Hard-to-test tools
```

Instead use:

```text
AI Tool
    ↓
AcademicQueryService
    ↓
Existing Business Services
    ↓
Repository
```

---

# 6. Academic Query Service Responsibilities

The Academic Query Service must:

```text
Resolve current authenticated Student

Validate Student account

Understand normalized academic relationships

Call existing academic services

Filter academic data

Return AI-friendly structured results

Avoid exposing persistence details

Handle missing information safely
```

---

# 7. Package Structure

Recommended:

```text
feature/
└── assistant/
    └── academic/
        ├── service/
        │   ├── AcademicQueryService.java
        │   └── impl/
        │       └── AcademicQueryServiceImpl.java
        │
        ├── dto/
        │   ├── AcademicProfileQueryResult.java
        │   ├── AcademicEnrollmentQueryResult.java
        │   ├── AcademicCourseQueryResult.java
        │   ├── AcademicScheduleQueryResult.java
        │   ├── AcademicCourseSectionQueryResult.java
        │   ├── AcademicLecturerQueryResult.java
        │   ├── CourseInformationQueryResult.java
        │   └── SectionAvailabilityQueryResult.java
        │
        └── exception/
            ├── AcademicQueryDataNotFoundException.java
            ├── AcademicCourseNotFoundException.java
            ├── AcademicCourseSectionNotFoundException.java
            └── AcademicQueryNotAllowedException.java
```

Alternative if existing architecture prefers:

```text
feature/
└── studentacademic/
    └── query/
```

Follow project package conventions.

---

# 8. Do Not Rebuild Existing Business Logic

The Academic Query Service should reuse:

```text
StudentAcademicService

EnrollmentService

CourseService

CourseSectionService

LecturerService
```

where they already exist.

Do NOT duplicate:

```text
Student eligibility validation

Enrollment filtering

Course Section lookup

Capacity calculation

Academic relationship validation
```

unless no reusable service exists.

---

# 9. Authentication Rule

The Academic Query Service must derive the Student identity from:

```text
SecurityContext
+
Student JWT
```

Required JWT claim:

```text
studentId
```

Do NOT allow AI tools to send:

```text
studentId
```

for private "my..." queries.

Wrong:

```java
getMyCourses(Long studentId)
```

if the AI can choose the ID.

Preferred:

```java
getMyCourses()
```

with current authenticated Student resolved internally.

Alternative safe internal method:

```java
getCoursesForAuthenticatedStudent(Long authenticatedStudentId)
```

where the caller supplies an already validated ID from SecurityContext, not user/AI input.

---

# 10. Current Student Provider

Reuse Step 2:

```text
CurrentStudentProvider
```

Recommended:

```java
public interface CurrentStudentProvider {

    Long getCurrentStudentId();

}
```

AcademicQueryServiceImpl should reuse it.

---

# 11. Academic Query Service Interface

Recommended:

```java
public interface AcademicQueryService {

    AcademicProfileQueryResult getMyAcademicProfile();

    List<AcademicEnrollmentQueryResult> getMyEnrollments();

    List<AcademicCourseQueryResult> getMyCourses();

    List<AcademicScheduleQueryResult> getMySchedule();

    AcademicCourseSectionQueryResult getMyCourseSection(
        String courseIdentifier
    );

    AcademicLecturerQueryResult getMyCourseLecturer(
        String courseIdentifier
    );

    CourseInformationQueryResult getCourseInformation(
        String courseIdentifier
    );

    SectionAvailabilityQueryResult getSectionAvailability(
        Long courseSectionId
    );

}
```

---

# 12. Course Identifier Strategy

For AI queries such as:

```text
"Who teaches CS301?"

"Who teaches Data Structures?"

"What section am I in for CS301?"
```

the AI may extract either:

```text
courseCode

or

courseName
```

Therefore:

```java
String courseIdentifier
```

may support:

```text
CS301

Data Structures

Data Structures & Algorithms
```

Service should normalize:

```text
trim

case-insensitive comparison
```

Recommended resolution order:

```text
1. Exact course code match

2. Exact course name match

3. Case-insensitive course code

4. Case-insensitive course name

5. Optional partial match only if unambiguous
```

Do NOT silently choose among multiple ambiguous courses.

---

# 13. Ambiguous Course Handling

Example:

```text
"Programming"
```

may match:

```text
Programming I

Programming II

Mobile Programming
```

Do NOT guess.

Return structured ambiguity:

```text
ACADEMIC_COURSE_AMBIGUOUS
```

Future AI can ask:

```text
"Which course do you mean?"
```

---

# 14. Query Result Design

The service should return:

```text
Structured data
```

not:

```text
natural-language sentences
```

Bad:

```java
return "You are enrolled in CS301";
```

Good:

```json
{
  "courseCode": "CS301",
  "courseName": "Data Structures & Algorithms",
  "sectionNumber": "SEC-01"
}
```

The future LLM will format the final answer.

---

# 15. Method 1 — getMyAcademicProfile()

Purpose:

Return the authenticated Student's academic profile.

Signature:

```java
AcademicProfileQueryResult getMyAcademicProfile();
```

Data source:

```text
Student
→ Faculty
→ Department
→ Program
→ Semester
```

---

# 16. AcademicProfileQueryResult

Recommended:

```java
public record AcademicProfileQueryResult(

    Long studentId,

    String studentCode,

    String fullName,

    String universityEmail,

    Long facultyId,

    String facultyCode,

    String facultyName,

    Long departmentId,

    String departmentCode,

    String departmentName,

    Long programId,

    String programCode,

    String programName,

    Long semesterId,

    String semesterName,

    Integer academicYear,

    Integer enrollmentYear

) {}
```

---

# 17. getMyAcademicProfile Workflow

```text
AI Tool
    ↓
AcademicQueryService.getMyAcademicProfile()
    ↓
Resolve current Student
    ↓
Validate ACTIVE
    ↓
Load Student academic relationships
    ↓
Map Query Result
    ↓
Return structured data
```

---

# 18. Example Profile Result

```json
{
  "studentId": 15,
  "studentCode": "STD-2024-0001",
  "fullName": "Thanapon Srisuk",
  "universityEmail": "thanapon.s@rsu.ac.th",

  "facultyId": 1,
  "facultyCode": "ICT",
  "facultyName": "College of Information and Communication Technology",

  "departmentId": 3,
  "departmentCode": "CS",
  "departmentName": "Computer Science",

  "programId": 5,
  "programCode": "BSC-CS",
  "programName": "Bachelor of Science in Computer Science",

  "semesterId": 1,
  "semesterName": "Semester 1",

  "academicYear": 2026,
  "enrollmentYear": 2024
}
```

---

# 19. AI Questions Supported by getMyAcademicProfile()

Future examples:

```text
"What program am I in?"

"Which department am I in?"

"What faculty am I under?"

"What is my student ID?"

"What academic year am I in?"

"When did I enroll?"
```

---

# 20. Method 2 — getMyEnrollments()

Purpose:

Return authenticated Student's active academic enrollments.

Recommended default:

```text
ACTIVE
```

Signature:

```java
List<AcademicEnrollmentQueryResult>
getMyEnrollments();
```

Optional future overload:

```java
List<AcademicEnrollmentQueryResult>
getMyEnrollments(EnrollmentStatus status);
```

Do NOT expose `ALL` as an EnrollmentStatus enum value.

---

# 21. AcademicEnrollmentQueryResult

```java
public record AcademicEnrollmentQueryResult(

    Long enrollmentId,

    String enrollmentStatus,

    LocalDate enrollmentDate,

    Long courseId,

    String courseCode,

    String courseName,

    Integer creditHours,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName,

    Long lecturerId,

    String lecturerName,

    String room,

    String schedule

) {}
```

---

# 22. getMyEnrollments Workflow

```text
Resolve current Student
        ↓
Validate ACTIVE
        ↓
Query non-deleted ACTIVE Enrollments
        ↓
Enrollment
→ Course Section
→ Course
→ Semester
→ Lecturer
        ↓
Map Query Results
        ↓
Return list
```

---

# 23. Empty Enrollment Result

Return:

```text
[]
```

Do NOT throw:

```text
ENROLLMENT_NOT_FOUND
```

for a Student with no active enrollments.

Future AI can say:

```text
"You currently have no active enrollments."
```

---

# 24. AI Questions Supported by getMyEnrollments()

```text
"What courses am I enrolled in?"

"Show my active enrollments."

"Which subjects am I taking?"

"How many courses am I taking?"

"What sections am I enrolled in?"
```

---

# 25. Method 3 — getMyCourses()

Purpose:

Return a simplified Course-focused view of active Student enrollments.

Signature:

```java
List<AcademicCourseQueryResult>
getMyCourses();
```

Data source:

```text
Student
→ Enrollment
→ Course Section
→ Course
```

---

# 26. AcademicCourseQueryResult

```java
public record AcademicCourseQueryResult(

    Long courseId,

    String courseCode,

    String courseName,

    Integer creditHours,

    String description,

    Integer courseType,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName

) {}
```

---

# 27. getMyCourses Workflow

```text
Current Student
    ↓
ACTIVE Enrollments
    ↓
Course Sections
    ↓
Courses
    ↓
Map simplified Course results
```

---

# 28. AI Questions Supported by getMyCourses()

```text
"What courses do I have?"

"List my subjects."

"How many credits is each course?"

"What course codes am I taking?"

"What is the description of my courses?"
```

---

# 29. Method 4 — getMySchedule()

Purpose:

Return current Student schedule information.

Signature:

```java
List<AcademicScheduleQueryResult>
getMySchedule();
```

Current source:

```text
course_sections.schedule
```

---

# 30. AcademicScheduleQueryResult

```java
public record AcademicScheduleQueryResult(

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

    String lecturerName

) {}
```

---

# 31. Schedule Limitation

If:

```text
course_sections.schedule
```

is free text such as:

```text
Tuesday 09:00-11:45
```

Step 3 should only:

```text
retrieve and return it
```

Do NOT yet perform:

```text
next-class calculation

tomorrow calculation

schedule conflict detection

time-range reasoning
```

unless schedule data is normalized.

---

# 32. Future Schedule Normalization

Recommended future table:

```text
course_section_schedules

id

course_section_id

day_of_week

start_time

end_time

room
```

Then AI can reliably answer:

```text
"What class do I have tomorrow?"

"What is my next class?"

"Do I have class after 2 PM?"

"Do I have a timetable conflict?"
```

---

# 33. AI Questions Supported by getMySchedule()

Current safe examples:

```text
"Show my schedule."

"What is my class schedule?"

"What rooms are my classes in?"

"Show the schedule information for my enrolled courses."
```

---

# 34. Method 5 — getMyCourseSection()

Purpose:

Find the authenticated Student's Course Section for a specific Course.

Signature:

```java
AcademicCourseSectionQueryResult
getMyCourseSection(
    String courseIdentifier
);
```

Example inputs:

```text
CS301

Data Structures

Data Structures & Algorithms
```

---

# 35. AcademicCourseSectionQueryResult

```java
public record AcademicCourseSectionQueryResult(

    Long courseId,

    String courseCode,

    String courseName,

    Long courseSectionId,

    String sectionNumber,

    Long semesterId,

    String semesterName,

    String room,

    String schedule,

    Long lecturerId,

    String lecturerName,

    String enrollmentStatus

) {}
```

---

# 36. getMyCourseSection Workflow

```text
Current Student
        ↓
ACTIVE Enrollments
        ↓
Resolve requested Course
        ↓
Find Enrollment for that Course
        ↓
Course Section
        ↓
Map Section Result
```

---

# 37. Course Section Security Rule

This method must search only within:

```text
authenticated Student's Enrollments
```

It must NOT allow:

```text
Student A
→ query Student B's section
```

The input contains only:

```text
courseIdentifier
```

not:

```text
studentId
```

---

# 38. Course Not Enrolled Rule

Example:

Student asks:

```text
"What section am I in for CS999?"
```

If CS999 exists globally but the Student is not enrolled:

recommended:

```text
404 or domain-specific not-found result
```

Code:

```text
STUDENT_COURSE_ENROLLMENT_NOT_FOUND
```

Do NOT expose another Student's Enrollment.

---

# 39. AI Questions Supported by getMyCourseSection()

```text
"What section am I in for CS301?"

"Which section is my Data Structures class?"

"What room is my CS301 section?"

"When does my CS301 section meet?"
```

---

# 40. Method 6 — getMyCourseLecturer()

Purpose:

Return the Lecturer for a specific Course in the authenticated Student's Enrollment.

Signature:

```java
AcademicLecturerQueryResult
getMyCourseLecturer(
    String courseIdentifier
);
```

---

# 41. AcademicLecturerQueryResult

```java
public record AcademicLecturerQueryResult(

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

---

# 42. getMyCourseLecturer Workflow

```text
Current Student
        ↓
ACTIVE Enrollment
        ↓
Resolve Course
        ↓
Get Course Section
        ↓
Get Lecturer
        ↓
Map Lecturer Result
```

---

# 43. Missing Lecturer Handling

If Course Section exists but:

```text
lecture_id = null
```

do NOT invent a lecturer.

Return:

```text
lecturerId = null

lecturerName = null
```

or a structured availability status.

Recommended future AI behavior:

```text
"A lecturer has not been assigned to this section yet."
```

---

# 44. AI Questions Supported by getMyCourseLecturer()

```text
"Who teaches CS301?"

"Who is my lecturer for Data Structures?"

"Who teaches my Mobile Programming class?"

"Who is the instructor for this course?"
```

---

# 45. Method 7 — getCourseInformation()

Purpose:

Return general Course information.

Unlike:

```text
getMyCourseSection()
```

this method may query a Course globally because Course metadata is not Student-private.

Signature:

```java
CourseInformationQueryResult
getCourseInformation(
    String courseIdentifier
);
```

---

# 46. CourseInformationQueryResult

Recommended:

```java
public record CourseInformationQueryResult(

    Long courseId,

    String courseCode,

    String courseName,

    Integer creditHours,

    String description,

    Integer courseType,

    String prerequisiteCourses,

    Integer maximumStudentsPerSection,

    Long facultyId,

    String facultyName,

    Long departmentId,

    String departmentName,

    Long programId,

    String programName

) {}
```

Map only actual existing Course fields.

---

# 47. getCourseInformation Workflow

```text
Normalize Course Identifier
        ↓
Find Course
        ↓
Require course not deleted
        ↓
Require active if project requires
        ↓
Load related academic references
        ↓
Map Course Information
```

---

# 48. Public vs Student-Specific Course Information

This service runs inside authenticated Student context, but Course information itself may be considered:

```text
non-private academic metadata
```

Still use the Student Assistant security boundary.

Do NOT expose internal:

```text
audit information

deleted records

Admin-only metadata
```

unless required.

---

# 49. AI Questions Supported by getCourseInformation()

```text
"What is CS301?"

"How many credits is CS301?"

"What are the prerequisites for CS401?"

"What type of course is CS301?"

"Tell me about Data Structures."
```

---

# 50. Prerequisite Limitation

Current Course model stores:

```text
prerequisite_courses
```

as course codes/text.

Therefore:

```text
"What are the prerequisites for CS401?"
```

can be answered.

But:

```text
"Am I eligible to take CS401?"
```

must NOT yet be answered definitively unless the system has:

```text
completed-course history

grades

course completion status
```

That is a future feature.

---

# 51. Method 8 — getSectionAvailability()

Purpose:

Return current availability for a Course Section.

Signature:

```java
SectionAvailabilityQueryResult
getSectionAvailability(
    Long courseSectionId
);
```

---

# 52. SectionAvailabilityQueryResult

Recommended:

```java
public record SectionAvailabilityQueryResult(

    Long courseSectionId,

    Long courseId,

    String courseCode,

    String courseName,

    String sectionNumber,

    Integer capacity,

    long occupiedSeats,

    long availableSeats,

    Boolean isFull,

    String sectionStatus,

    Long semesterId,

    String semesterName

) {}
```

---

# 53. Availability Calculation

Current Enrollment capacity policy:

Seat-occupying statuses:

```text
PENDING

ACTIVE
```

Not occupying:

```text
WITHDRAWN

DROPPED
```

Calculation:

```text
occupiedSeats
=
COUNT(
    PENDING
    +
    ACTIVE
)
WHERE
is_deleted = FALSE
```

Then:

```text
availableSeats
=
capacity - occupiedSeats
```

Do not allow negative output:

```text
max(0, capacity - occupiedSeats)
```

Full:

```text
occupiedSeats >= capacity
```

---

# 54. Availability Workflow

```text
courseSectionId
        ↓
Load Course Section
        ↓
Require not deleted
        ↓
Get capacity
        ↓
Count PENDING + ACTIVE Enrollments
        ↓
Calculate available seats
        ↓
Calculate isFull
        ↓
Return result
```

---

# 55. AI Questions Supported by getSectionAvailability()

```text
"Is section 8 full?"

"How many seats are left in SEC-01?"

"Is my CS301 section full?"

"How many students are enrolled in this section?"
```

For:

```text
"Is my CS301 section full?"
```

future AI should call:

```text
getMyCourseSection("CS301")
```

then:

```text
getSectionAvailability(courseSectionId)
```

This demonstrates multi-tool reasoning.

---

# 56. Service Composition

AcademicQueryService should compose reusable internal methods.

Recommended private helpers:

```java
Student getCurrentActiveStudent();

List<Enrollment> getCurrentActiveEnrollments();

Course resolveCourse(
    String courseIdentifier
);

Enrollment resolveMyEnrollmentForCourse(
    String courseIdentifier
);

CourseSection resolveCourseSection(
    Long courseSectionId
);

long countOccupiedSeats(
    Long courseSectionId
);
```

Do not repeat logic in every public method.

---

# 57. Reuse Step 2 Service

Preferred architecture:

```text
AcademicQueryService
        ↓
StudentAcademicService
```

where possible.

Example:

```java
@Override
@Transactional(readOnly = true)
public AcademicProfileQueryResult getMyAcademicProfile() {

    StudentAcademicProfileResponse profile =
        studentAcademicService.getProfile(
            currentStudentProvider.getCurrentStudentId()
        );

    return academicQueryMapper.toProfileQueryResult(
        profile
    );
}
```

However, avoid unnecessary DTO → DTO transformations if the existing StudentAcademicService can expose internal reusable methods cleanly.

Choose one consistent architecture.

---

# 58. Recommended Separation

Use:

```text
StudentAcademicService
```

for:

```text
Student-facing REST business operations
```

Use:

```text
AcademicQueryService
```

for:

```text
AI-friendly academic query operations
```

Both may reuse:

```text
repositories / domain services
```

Do NOT make AI Tool classes directly call REST Controllers.

---

# 59. Read-Only Transactions

Use:

```java
@Transactional(readOnly = true)
```

for all Step 3 methods:

```text
getMyAcademicProfile

getMyEnrollments

getMyCourses

getMySchedule

getMyCourseSection

getMyCourseLecturer

getCourseInformation

getSectionAvailability
```

These methods must not mutate academic data.

---

# 60. No Side Effects

Academic Query methods must NOT:

```text
create Enrollment

update Student

change Course

change Course Section

change Lecturer

update Redis session

modify academic status
```

They are query-only.

---

# 61. AI-Friendly Result Rules

Each Query Result should:

```text
Use stable field names

Avoid Hibernate proxies

Avoid Entity objects

Avoid recursion

Avoid excessive nested structures

Avoid exposing internal audit details
```

Prefer:

```json
{
  "courseCode": "CS301",
  "sectionNumber": "SEC-01",
  "room": "11-402"
}
```

rather than deeply nested entity JSON.

---

# 62. Null Handling

Academic data may legitimately be missing.

Examples:

```text
room = null

lecturer = null

schedule = null

semester = null
```

Do NOT invent defaults such as:

```text
Room 101

Unknown Lecturer Name generated by AI
```

Return null or explicit availability flags.

---

# 63. Recommended Missing-Data Policy

Use:

```text
Result exists
+
specific field missing
→ return null
```

Example:

```json
{
  "courseCode": "CS301",
  "lecturerId": null,
  "lecturerName": null
}
```

Use exception only when the requested core object does not exist.

Example:

```text
Course does not exist
→ ACADEMIC_COURSE_NOT_FOUND
```

---

# 64. Exception Codes

Recommended:

```text
ACADEMIC_COURSE_NOT_FOUND

ACADEMIC_COURSE_AMBIGUOUS

ACADEMIC_COURSE_SECTION_NOT_FOUND

STUDENT_COURSE_ENROLLMENT_NOT_FOUND

ACADEMIC_DATA_UNAVAILABLE

STUDENT_ACCOUNT_NOT_ACTIVE

UNAUTHORIZED

FORBIDDEN
```

Reuse existing errors where possible.

---

# 65. Course Not Found

Example:

```text
getCourseInformation("XYZ999")
```

Result:

```text
404
ACADEMIC_COURSE_NOT_FOUND
```

---

# 66. Course Ambiguous

Example:

```text
getCourseInformation("Programming")
```

matches multiple courses.

Result:

```text
409
ACADEMIC_COURSE_AMBIGUOUS
```

Future AI can ask the Student to clarify.

---

# 67. Student Course Enrollment Not Found

Example:

```text
getMyCourseSection("CS401")
```

Course exists but Student is not enrolled.

Result:

```text
404
STUDENT_COURSE_ENROLLMENT_NOT_FOUND
```

Do not return another Student's section.

---

# 68. Query Performance

Because methods frequently navigate:

```text
Enrollment
→ Course Section
→ Course
→ Semester
→ Lecturer
```

avoid N+1 queries.

Use:

```text
JOIN FETCH

EntityGraph

Projection
```

consistent with Step 2.

---

# 69. Recommended Active Enrollment Fetch

Concept:

```java
@Query("""
    SELECT e
    FROM Enrollment e

    JOIN FETCH e.courseSection cs

    JOIN FETCH cs.course c

    JOIN FETCH cs.semester sem

    LEFT JOIN FETCH cs.lecture l

    WHERE e.student.id = :studentId
      AND e.status = 'ACTIVE'
      AND e.isDeleted = false
""")
List<Enrollment> findActiveAcademicEnrollments(
    @Param("studentId") Long studentId
);
```

Adjust actual field names.

---

# 70. Course Resolution Query

Recommended repository methods:

```java
Optional<Course>
findByCourseCodeIgnoreCaseAndIsDeletedFalse(
    String courseCode
);
```

and:

```java
List<Course>
findByCourseNameIgnoreCaseAndIsDeletedFalse(
    String courseName
);
```

If partial matching is supported:

```text
only accept one unambiguous result
```

---

# 71. Academic Query Mapper

Create:

```text
AcademicQueryMapper
```

Recommended methods:

```java
AcademicProfileQueryResult toProfileResult(...);

AcademicEnrollmentQueryResult toEnrollmentResult(...);

AcademicCourseQueryResult toCourseResult(...);

AcademicScheduleQueryResult toScheduleResult(...);

AcademicCourseSectionQueryResult toSectionResult(...);

AcademicLecturerQueryResult toLecturerResult(...);

CourseInformationQueryResult toCourseInformationResult(...);

SectionAvailabilityQueryResult toAvailabilityResult(...);
```

---

# 72. Do Not Put Natural Language in Mapper

Wrong:

```java
return new SectionAvailabilityQueryResult(
    "There are 5 seats left"
);
```

Correct:

```json
{
  "capacity": 40,
  "occupiedSeats": 35,
  "availableSeats": 5,
  "isFull": false
}
```

The LLM creates:

```text
"There are 5 seats left."
```

later.

---

# 73. AI Tool Compatibility

Design method signatures so they map cleanly to future AI tools.

Future tools:

```text
get_my_academic_profile

get_my_enrollments

get_my_courses

get_my_schedule

get_my_course_section

get_my_course_lecturer

get_course_information

get_section_availability
```

Mapping:

| Future AI Tool | Query Service |
|---|---|
| `get_my_academic_profile` | `getMyAcademicProfile()` |
| `get_my_enrollments` | `getMyEnrollments()` |
| `get_my_courses` | `getMyCourses()` |
| `get_my_schedule` | `getMySchedule()` |
| `get_my_course_section` | `getMyCourseSection(course)` |
| `get_my_course_lecturer` | `getMyCourseLecturer(course)` |
| `get_course_information` | `getCourseInformation(course)` |
| `get_section_availability` | `getSectionAvailability(sectionId)` |

---

# 74. Example Future AI Flow — My Courses

Student:

```text
"What courses am I enrolled in?"
```

Future:

```text
LLM
    ↓
tool:
get_my_enrollments
    ↓
AcademicQueryService.getMyEnrollments()
    ↓
PostgreSQL
    ↓
Structured List
    ↓
LLM
    ↓
Natural Answer
```

---

# 75. Example Future AI Flow — Lecturer

Student:

```text
"Who teaches my CS301 class?"
```

Flow:

```text
LLM
    ↓
Extract:
courseIdentifier = CS301
    ↓
get_my_course_lecturer
    ↓
AcademicQueryService
.getMyCourseLecturer("CS301")
    ↓
Enrollment
    ↓
Course Section
    ↓
Lecturer
    ↓
Structured Result
```

---

# 76. Example Future AI Flow — Section Availability

Student:

```text
"Is my CS301 section full?"
```

Flow:

```text
LLM
    ↓
get_my_course_section("CS301")
    ↓
courseSectionId = 8
    ↓
get_section_availability(8)
    ↓
{
  capacity: 40,
  occupiedSeats: 38,
  availableSeats: 2,
  isFull: false
}
    ↓
LLM
    ↓
"Your CS301 section has 2 seats available."
```

---

# 77. Example Future AI Flow — Course Information

Student:

```text
"How many credits is CS301?"
```

Flow:

```text
LLM
    ↓
get_course_information("CS301")
    ↓
{
  courseCode: "CS301",
  creditHours: 3
}
    ↓
LLM
    ↓
"CS301 is a 3-credit course."
```

---

# 78. Security Boundary

Future AI may provide arguments such as:

```text
courseIdentifier

courseSectionId
```

but must NEVER provide:

```text
studentId
```

for "my..." operations.

Safe:

```text
getMyCourseSection("CS301")
```

Unsafe:

```text
getStudentCourseSection(
    studentId = 500,
    course = "CS301"
)
```

when AI controls the Student ID.

---

# 79. Authorization Boundary

All Step 3 calls must require:

```text
ROLE_STUDENT
```

when invoked from Student Assistant context.

Future Admin AI must use a separate service/security design.

Do not reuse Student-private methods for Admin data exploration.

---

# 80. Logging

Log:

```text
query method

course identifier when non-sensitive

execution time

result count

error code
```

Do NOT log:

```text
JWT token

password

refresh token

full private Student data unnecessarily
```

---

# 81. Observability for Research

Because this is a senior project, prepare this service for future research metrics.

Possible future event fields:

```text
queryType

toolName

success

executionTimeMs

resultCount

errorCode
```

Example:

```json
{
  "toolName": "get_my_course_lecturer",
  "success": true,
  "executionTimeMs": 24,
  "resultCount": 1
}
```

Do NOT implement heavy analytics yet if not needed.

But structure the service so these metrics can be added later.

---

# 82. Redis Recommendation

Do NOT make Redis mandatory in Step 3.

First:

```text
PostgreSQL Query Service
```

must work reliably.

Optional later cache:

```text
student:academic:profile:<studentId>

student:academic:enrollments:<studentId>

course:information:<courseId>

section:availability:<sectionId>
```

Be careful with:

```text
section availability
```

because it changes frequently as Enrollments change.

Use short TTL or event invalidation.

---

# 83. Cache Safety

If caching is added later:

```text
PostgreSQL remains authoritative
```

Invalidate on:

```text
Student update

Enrollment create

Enrollment update

Enrollment status change

Enrollment delete

Course update

Course Section update

Lecturer update
```

---

# 84. Unit Tests — Profile

```text
[ ] ACTIVE Student profile returned

[ ] Student ID derived from authentication

[ ] Faculty mapped correctly

[ ] Department mapped correctly

[ ] Program mapped correctly

[ ] Semester mapped correctly

[ ] Academic Year mapped correctly

[ ] Enrollment Year mapped correctly

[ ] No password/security fields exposed
```

---

# 85. Unit Tests — Enrollments

```text
[ ] ACTIVE enrollments returned

[ ] Soft-deleted enrollments excluded

[ ] Course mapped

[ ] Section mapped

[ ] Semester mapped

[ ] Lecturer mapped

[ ] Empty list returns []

[ ] Other Student's enrollments never returned
```

---

# 86. Unit Tests — Courses

```text
[ ] Active Student courses returned

[ ] Course Code correct

[ ] Course Name correct

[ ] Credit Hours correct

[ ] Course Type correct

[ ] Section retained

[ ] Semester retained
```

---

# 87. Unit Tests — Schedule

```text
[ ] ACTIVE schedule returned

[ ] Course returned

[ ] Section returned

[ ] Room returned

[ ] Schedule returned

[ ] Lecturer returned

[ ] Missing room handled safely

[ ] Missing schedule handled safely
```

---

# 88. Unit Tests — My Course Section

```text
[ ] Course code lookup works

[ ] Course name lookup works

[ ] Case-insensitive lookup works

[ ] Student Enrollment section returned

[ ] Course exists but Student not enrolled returns correct error

[ ] Ambiguous Course Name handled

[ ] Other Student section never returned
```

---

# 89. Unit Tests — My Course Lecturer

```text
[ ] Lecturer returned for enrolled Course

[ ] Course Code lookup works

[ ] Course Name lookup works

[ ] Lecturer context includes Section

[ ] Missing Lecturer returns null fields safely

[ ] Student not enrolled returns correct error
```

---

# 90. Unit Tests — Course Information

```text
[ ] Course Code lookup works

[ ] Course Name lookup works

[ ] Course credits returned

[ ] Description returned

[ ] Course Type returned

[ ] Prerequisites returned

[ ] Deleted Course excluded

[ ] Unknown Course returns not found

[ ] Ambiguous Course handled
```

---

# 91. Unit Tests — Section Availability

```text
[ ] Course Section found

[ ] Capacity returned

[ ] PENDING counted

[ ] ACTIVE counted

[ ] WITHDRAWN excluded

[ ] DROPPED excluded

[ ] Soft-deleted Enrollment excluded

[ ] availableSeats calculated

[ ] availableSeats never negative

[ ] isFull true when occupied >= capacity

[ ] Deleted Section rejected
```

---

# 92. Security Tests

```text
[ ] Missing Student authentication rejected

[ ] Invalid Student JWT rejected

[ ] Expired Student JWT rejected

[ ] Student ID cannot be overridden

[ ] "my" queries access only current Student

[ ] Admin JWT does not silently become Student context
```

---

# 93. Performance Tests

For Student with:

```text
10 active Enrollments
```

verify service does not create excessive queries.

Check:

```text
getMyEnrollments()

getMyCourses()

getMySchedule()
```

Use optimized fetching if necessary.

---

# 94. Read-Only Guarantee Tests

Verify Query Service never writes:

```text
students

enrollments

courses

course_sections

lectures
```

No:

```text
save()

delete()

status update
```

belongs in this feature.

---

# 95. Integration Test — Profile

Flow:

```text
Login Student
    ↓
Authenticated JWT
    ↓
getMyAcademicProfile()
    ↓
Student Academic Profile
```

Expected:

```text
correct authenticated Student only
```

---

# 96. Integration Test — Lecturer

Flow:

```text
Student enrolled in CS301
        ↓
CS301 Section has Lecturer 7
        ↓
getMyCourseLecturer("CS301")
        ↓
Lecturer 7 returned
```

---

# 97. Integration Test — Availability

Given:

```text
capacity = 40

PENDING = 3

ACTIVE = 35

WITHDRAWN = 5

DROPPED = 2
```

Expected:

```text
occupiedSeats = 38

availableSeats = 2

isFull = false
```

WITHDRAWN/DROPPED do not count.

---

# 98. Implementation Order

Implement strictly in this order:

```text
PHASE 1
Review Step 2 Student Academic APIs
        ↓
PHASE 2
Create Academic Query DTOs
        ↓
PHASE 3
Create AcademicQueryService Interface
        ↓
PHASE 4
Implement Current Student Resolution
        ↓
PHASE 5
Implement getMyAcademicProfile
        ↓
PHASE 6
Implement getMyEnrollments
        ↓
PHASE 7
Implement getMyCourses
        ↓
PHASE 8
Implement getMySchedule
        ↓
PHASE 9
Implement Course Identifier Resolver
        ↓
PHASE 10
Implement getMyCourseSection
        ↓
PHASE 11
Implement getMyCourseLecturer
        ↓
PHASE 12
Implement getCourseInformation
        ↓
PHASE 13
Implement getSectionAvailability
        ↓
PHASE 14
Create AcademicQueryMapper
        ↓
PHASE 15
Add Exception Handling
        ↓
PHASE 16
Optimize Queries / N+1
        ↓
PHASE 17
Add Security Tests
        ↓
PHASE 18
Add Unit Tests
        ↓
PHASE 19
Add Integration Tests
        ↓
PHASE 20
Run Maven Build
```

---

# 99. Final Service Contract

Final Academic Query Service:

```text
AcademicQueryService
│
├── getMyAcademicProfile()
│
├── getMyEnrollments()
│
├── getMyCourses()
│
├── getMySchedule()
│
├── getMyCourseSection(courseIdentifier)
│
├── getMyCourseLecturer(courseIdentifier)
│
├── getCourseInformation(courseIdentifier)
│
└── getSectionAvailability(courseSectionId)
```

---

# 100. Final AI Tool Mapping

Future Step 4 or Step 5:

```text
AI Assistant Tool
                        AcademicQueryService

get_my_academic_profile
        ↓
getMyAcademicProfile()


get_my_enrollments
        ↓
getMyEnrollments()


get_my_courses
        ↓
getMyCourses()


get_my_schedule
        ↓
getMySchedule()


get_my_course_section
        ↓
getMyCourseSection(course)


get_my_course_lecturer
        ↓
getMyCourseLecturer(course)


get_course_information
        ↓
getCourseInformation(course)


get_section_availability
        ↓
getSectionAvailability(sectionId)
```

---

# 101. Acceptance Criteria

```text
ARCHITECTURE
[ ] AcademicQueryService created
[ ] AcademicQueryServiceImpl created
[ ] AI does not access Repository directly
[ ] AI does not access PostgreSQL directly
[ ] Query Results use DTOs
[ ] JPA Entities not exposed

SECURITY
[ ] Current Student resolved from JWT
[ ] Student ID not accepted for "my" methods
[ ] ROLE_STUDENT respected
[ ] Other Student data cannot be queried

PROFILE
[ ] getMyAcademicProfile implemented

ENROLLMENT
[ ] getMyEnrollments implemented

COURSE
[ ] getMyCourses implemented

SCHEDULE
[ ] getMySchedule implemented

COURSE SECTION
[ ] getMyCourseSection implemented
[ ] Course Code lookup
[ ] Course Name lookup
[ ] Ambiguity handling

LECTURER
[ ] getMyCourseLecturer implemented
[ ] Missing Lecturer handled safely

COURSE INFORMATION
[ ] getCourseInformation implemented
[ ] Credits returned
[ ] Description returned
[ ] Prerequisites returned

SECTION AVAILABILITY
[ ] getSectionAvailability implemented
[ ] PENDING counted
[ ] ACTIVE counted
[ ] WITHDRAWN excluded
[ ] DROPPED excluded
[ ] isFull derived
[ ] availableSeats derived

DATABASE
[ ] PostgreSQL remains source of truth
[ ] No query method performs database mutation

PERFORMANCE
[ ] N+1 checked
[ ] Fetch strategy optimized

TESTING
[ ] Unit tests pass
[ ] Security tests pass
[ ] Integration tests pass

BUILD
[ ] mvn test passes
[ ] mvn clean package passes
```

---

# 102. Next Step

After Step 3 is stable, move to:

# Step 4 — AI Assistant Chat API

The architecture becomes:

```text
Student Mobile App
        ↓
POST /api/v1/student/assistant/chat
        ↓
AI Assistant Service
        ↓
LLM
        ↓
Academic AI Tool
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
Structured Academic Result
        ↓
LLM
        ↓
Natural-Language Response
```

At that stage, the LLM is added.

Do not begin Curriculum RAG until:

```text
Academic Query Service
```

is stable, secure, and fully tested.