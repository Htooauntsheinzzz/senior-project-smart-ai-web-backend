# 30 - Academic Query Verification Without AI

## 1. Purpose

Implement and verify:

# Step 4 — Test Academic Queries Without AI

for the:

**Smart AI University Student Assistant App**

This step verifies that the backend can correctly answer academic questions using:

```text
Student Authentication
        ↓
AcademicQueryService
        ↓
Existing Academic Services
        ↓
Repositories
        ↓
PostgreSQL
```

without using:

```text
LLM

AI Model

Prompt Engineering

Intent Classification

Embeddings

Vector Database

RAG

Curriculum Documents

Conversation Memory
```

The purpose of Step 4 is to prove:

> If the AI correctly chooses an Academic Query method later, the backend can already return the correct academic information.

---

# 2. Prerequisites

The following steps must already be implemented.

## Step 2 — Student Academic APIs

```text
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/enrollments

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/lecturers
```

## Step 3 — Academic Query Service

The following methods should exist:

```text
getMyAcademicProfile()

getMyEnrollments()

getMyCourses()

getMySchedule()

getMyCourseSection(courseIdentifier)

getMyCourseLecturer(courseIdentifier)

getCourseInformation(courseIdentifier)

getSectionAvailability(courseSectionId)
```

---

# 3. Main Goal

Verify deterministic answers for academic questions such as:

```text
"What courses am I enrolled in?"

"Which section am I in for CS301?"

"Who teaches CS301?"

"Where is my CS301 class?"

"What is my schedule?"

"How many credits is CS301?"

"What program am I in?"

"How many seats are available in my section?"
```

At this stage, the actual English sentence is **not sent to an AI model**.

Instead, each question is manually mapped to the expected backend service method.

Example:

```text
Question:

"What courses am I enrolled in?"

Expected Backend Call:

AcademicQueryService.getMyEnrollments()
```

---

# 4. Important Boundary

Step 4 tests:

```text
Question Meaning
        ↓
Known AcademicQueryService Method
        ↓
Structured Result
```

It does NOT test:

```text
Natural Language
        ↓
AI automatically chooses tool
```

That comes later.

Therefore do NOT implement a temporary keyword-based chatbot such as:

```java
if (message.contains("course")) {
    return getMyCourses();
}
```

Do NOT create fake AI logic using:

```text
if/else keyword matching

regex intent classification

hard-coded NLP

switch(message)
```

Step 4 is backend verification only.

---

# 5. Mandatory AI Coding Agent Instructions

Before modifying the project:

1. Read `AGENTS.md`.
2. Inspect `spec-md/`.
3. Inspect Step 2 Student Academic implementation.
4. Inspect Step 3 `AcademicQueryService`.
5. Inspect Student JWT authentication.
6. Inspect Student test utilities.
7. Inspect Enrollment tests.
8. Inspect Course Section tests.
9. Inspect PostgreSQL test configuration.
10. Inspect existing Testcontainers configuration if available.
11. Reuse existing response wrappers.
12. Reuse existing exception contracts.
13. Do NOT introduce H2 for these tests.
14. Do NOT change production schema only to simplify testing.
15. Do NOT create an AI provider integration.
16. Do NOT introduce test-only production endpoints.
17. Run all existing tests after implementation.
18. Run `mvn clean package`.

---

# 6. PostgreSQL Test Requirement

Academic Query integration tests must use PostgreSQL-compatible behavior.

Preferred:

```text
Testcontainers PostgreSQL
```

if already supported by the project.

Otherwise use the existing project's PostgreSQL integration-test setup.

Do NOT use:

```text
H2
```

as the authoritative integration database.

Reason:

```text
Production Database
=
PostgreSQL
```

and Academic Query tests depend on actual:

```text
relationships

constraints

enum String values

JOIN behavior

Flyway schema
```

---

# 7. Do Not Create Production Seed Data

Do NOT create a Flyway migration such as:

```text
Vxx__insert_academic_test_data.sql
```

only for these tests.

Test data must be created through:

```text
test fixtures

repository setup

SQL test resources

existing test factory/builders
```

and should be isolated from production migrations.

---

# 8. Recommended Test Package

Recommended:

```text
src/test/java/
└── .../
    └── assistant/
        └── academic/
            ├── AcademicQueryServiceTest.java
            ├── AcademicQueryServiceIntegrationTest.java
            ├── AcademicQuerySecurityTest.java
            ├── AcademicQueryCourseResolutionTest.java
            └── AcademicQueryPerformanceTest.java
```

If the project follows feature-based tests:

```text
feature/
└── assistant/
    └── academic/
```

follow the existing convention.

---

# 9. Test Levels

Use four levels.

```text
LEVEL 1
Unit Tests
        ↓
LEVEL 2
Repository / Query Tests
        ↓
LEVEL 3
Academic Query Integration Tests
        ↓
LEVEL 4
Student API / Security Tests
```

Do not rely only on Controller tests.

The most important test target is:

```text
AcademicQueryService
```

because future AI tools will call this layer.

---

# 10. Deterministic Test Dataset

Create a predictable academic test fixture.

Example Student:

```text
Student ID:
15

Student Code:
STD-2024-0001

Name:
Thanapon Srisuk

Status:
ACTIVE

is_deleted:
FALSE

Academic Year:
2026

Enrollment Year:
2024
```

Example Faculty:

```text
ID:
1

Code:
ICT

Name:
College of Information and Communication Technology
```

Example Department:

```text
ID:
3

Code:
CS

Name:
Computer Science
```

Example Program:

```text
ID:
5

Code:
BSC-CS

Name:
Bachelor of Science in Computer Science
```

Example Semester:

```text
ID:
1

Name:
Semester 1
```

Use actual entity/table fields from the project.

Do NOT blindly create fixture fields that do not exist.

---

# 11. Test Course — CS301

Create Course:

```text
Course ID:
301

Course Code:
CS301

Course Name:
Data Structures & Algorithms

Credit Hours:
3

Course Type:
300

Description:
Data structures and algorithms
```

Example prerequisite:

```text
CS201
```

only if the actual Course schema supports it.

---

# 12. Test Course Section — CS301

Create Course Section:

```text
Course Section ID:
801

Course:
CS301

Section Number:
SEC-01

Semester:
Semester 1

Capacity:
40

Status:
ACTIVE

Room:
11-402

Schedule:
Tuesday 09:00-11:45
```

Example Lecturer:

```text
Lecturer ID:
7

Name:
Dr. John Smith
```

Use the actual Lecturer fields in the project.

---

# 13. Test Enrollment — CS301

Create:

```text
Student:
STD-2024-0001

Course Section:
CS301 SEC-01

Enrollment Status:
ACTIVE

is_deleted:
FALSE
```

This record should be returned by:

```text
getMyEnrollments()

getMyCourses()

getMySchedule()

getMyCourseSection("CS301")

getMyCourseLecturer("CS301")
```

---

# 14. Additional Test Course — CSC452

Create a second Course:

```text
Course Code:
CSC452

Course Name:
Mobile Programming

Credit Hours:
3
```

Section:

```text
SEC-02

Room:
5-201

Schedule:
Friday 09:00-11:45

Status:
ACTIVE
```

Add an ACTIVE Enrollment for the same Student.

This proves list methods can return multiple records.

---

# 15. Terminal Enrollment Fixtures

Also create:

```text
WITHDRAWN Enrollment
```

and:

```text
DROPPED Enrollment
```

for separate courses.

These must NOT appear in:

```text
getMyEnrollments()
```

when Step 3 defines that method as ACTIVE-only.

---

# 16. Soft-Deleted Fixture

Create one Enrollment with:

```text
is_deleted = TRUE
```

It must never appear in normal Academic Query results.

---

# 17. Question-to-Service Verification Matrix

The AI coding agent must verify this mapping.

| Future Student Question | Backend Method |
|---|---|
| What courses am I enrolled in? | `getMyEnrollments()` |
| What courses do I have? | `getMyCourses()` |
| Which section am I in for CS301? | `getMyCourseSection("CS301")` |
| Who teaches CS301? | `getMyCourseLecturer("CS301")` |
| Where is my CS301 class? | `getMyCourseSection("CS301")` |
| What is my schedule? | `getMySchedule()` |
| How many credits is CS301? | `getCourseInformation("CS301")` |
| What program am I in? | `getMyAcademicProfile()` |
| Which department am I in? | `getMyAcademicProfile()` |
| What faculty am I in? | `getMyAcademicProfile()` |
| Is section 801 full? | `getSectionAvailability(801)` |
| How many seats are left in section 801? | `getSectionAvailability(801)` |

---

# 18. Test 1 — What Courses Am I Enrolled In?

Future question:

```text
"What courses am I enrolled in?"
```

Backend method:

```java
academicQueryService.getMyEnrollments();
```

Expected result must contain:

```text
CS301

CSC452
```

Expected fields:

```text
enrollmentId

enrollmentStatus

courseId

courseCode

courseName

creditHours

courseSectionId

sectionNumber

semesterId

semesterName

lecturerId

lecturerName

room

schedule
```

---

# 19. Test 1 Expected Assertions

Example:

```java
List<AcademicEnrollmentQueryResult> result =
    academicQueryService.getMyEnrollments();

assertThat(result)
    .hasSize(2);

assertThat(result)
    .extracting(
        AcademicEnrollmentQueryResult::courseCode
    )
    .containsExactlyInAnyOrder(
        "CS301",
        "CSC452"
    );
```

Also assert:

```text
WITHDRAWN not returned

DROPPED not returned

soft-deleted not returned
```

---

# 20. Test 2 — Which Section Am I In?

The question:

```text
"Which section am I in?"
```

is incomplete when a Student has multiple courses.

Without conversation context, Step 4 must NOT pretend that this question has one deterministic answer.

Correct Step 4 test:

```text
"Which section am I in for CS301?"
```

Backend:

```java
academicQueryService
    .getMyCourseSection("CS301");
```

Expected:

```text
courseCode = CS301

sectionNumber = SEC-01

courseSectionId = 801
```

---

# 21. Important Ambiguous Question Rule

Questions such as:

```text
"Which section am I in?"

"Where is my class?"

"Who is my lecturer?"

"How many credits is this course?"
```

require context.

Since Step 4 has:

```text
NO conversation memory
NO LLM
```

do not invent context.

Use explicit deterministic versions:

```text
"Which section am I in for CS301?"

"Where is my CS301 class?"

"Who teaches CS301?"

"How many credits is CS301?"
```

Later conversation context will resolve:

```text
this course

that class

the first one

it
```

---

# 22. Test 2 Expected Assertions

```java
AcademicCourseSectionQueryResult result =
    academicQueryService
        .getMyCourseSection("CS301");

assertThat(result.courseCode())
    .isEqualTo("CS301");

assertThat(result.sectionNumber())
    .isEqualTo("SEC-01");

assertThat(result.courseSectionId())
    .isEqualTo(801L);
```

Also verify:

```text
room = 11-402

schedule = Tuesday 09:00-11:45

lecturer = expected Lecturer
```

---

# 23. Test 3 — Who Teaches CS301?

Future question:

```text
"Who teaches CS301?"
```

Backend:

```java
academicQueryService
    .getMyCourseLecturer("CS301");
```

Expected result:

```text
courseCode = CS301

sectionNumber = SEC-01

lecturerId = 7

lecturerName = Dr. John Smith
```

---

# 24. Test Lecturer Must Come From Student Section

Important:

```text
Course
```

may have multiple Course Sections with different lecturers.

Therefore:

```text
getMyCourseLecturer("CS301")
```

must return the lecturer associated with:

```text
authenticated Student's Enrollment
→ Course Section
```

not just any Lecturer teaching CS301.

---

# 25. Lecturer Security Test

Create another CS301 Section:

```text
SEC-02
```

with:

```text
Dr. Jane Doe
```

The authenticated Student is enrolled in:

```text
SEC-01
```

Expected:

```text
getMyCourseLecturer("CS301")
→ Dr. John Smith
```

NOT:

```text
Dr. Jane Doe
```

This is a critical Academic Query correctness test.

---

# 26. Test 4 — Where Is My Class?

Use deterministic question:

```text
"Where is my CS301 class?"
```

Backend:

```java
academicQueryService
    .getMyCourseSection("CS301");
```

Expected:

```text
room = 11-402
```

Also useful:

```text
sectionNumber = SEC-01

schedule = Tuesday 09:00-11:45
```

---

# 27. Missing Room Test

Create another valid Student Enrollment whose Course Section has:

```text
room = null
```

Expected backend result:

```text
room = null
```

Do NOT:

```text
generate a room

copy another section's room

throw an unrelated error
```

Later AI should explain:

```text
"A room has not been assigned yet."
```

but Step 4 only verifies:

```text
null
```

is returned safely.

---

# 28. Test 5 — What Is My Schedule?

Future question:

```text
"What is my schedule?"
```

Backend:

```java
academicQueryService.getMySchedule();
```

Expected:

```text
CS301
Tuesday 09:00-11:45
11-402

CSC452
Friday 09:00-11:45
5-201
```

Only:

```text
ACTIVE

non-deleted
```

Enrollments should appear.

---

# 29. Schedule Assertions

Verify each result contains:

```text
courseCode

courseName

courseSectionId

sectionNumber

semesterId

semesterName

schedule

room

lecturerId

lecturerName
```

Do NOT infer:

```text
next class

today's class

tomorrow's class
```

from free-text schedule in Step 4.

---

# 30. Test 6 — How Many Credits Is This Course?

The original question:

```text
"How many credits is this course?"
```

requires conversational context.

Without AI/context, use:

```text
"How many credits is CS301?"
```

Backend:

```java
academicQueryService
    .getCourseInformation("CS301");
```

Expected:

```text
courseCode = CS301

creditHours = 3
```

---

# 31. Course Information Assertions

Also verify:

```text
courseName

description

courseType

prerequisiteCourses
```

where actual fields exist.

Do NOT assert a field that does not exist in the actual Course model.

---

# 32. Test 7 — What Program Am I In?

Future question:

```text
"What program am I in?"
```

Backend:

```java
academicQueryService
    .getMyAcademicProfile();
```

Expected:

```text
programCode = BSC-CS

programName =
Bachelor of Science in Computer Science
```

---

# 33. Profile Verification

Also verify:

```text
studentCode

student name

faculty

department

program

semester

academicYear

enrollmentYear
```

Do NOT expose:

```text
passwordHash

failedLoginAttempts

lockedUntil

refresh token data
```

---

# 34. Test 8 — Section Availability

Future question:

```text
"How many seats are left in section 801?"
```

Backend:

```java
academicQueryService
    .getSectionAvailability(801L);
```

Example:

```text
Capacity = 40

ACTIVE = 30

PENDING = 5

WITHDRAWN = 3

DROPPED = 2
```

Expected:

```text
occupiedSeats = 35

availableSeats = 5

isFull = false
```

---

# 35. Availability Count Rule

Only:

```text
PENDING

ACTIVE
```

reserve seats.

Exclude:

```text
WITHDRAWN

DROPPED

is_deleted = TRUE
```

Calculation:

```text
occupiedSeats
=
PENDING + ACTIVE
```

```text
availableSeats
=
max(
    0,
    capacity - occupiedSeats
)
```

```text
isFull
=
occupiedSeats >= capacity
```

---

# 36. Full Section Test

Given:

```text
capacity = 40

occupiedSeats = 40
```

Expected:

```text
availableSeats = 0

isFull = true
```

---

# 37. Defensive Over-Capacity Test

If test data intentionally produces:

```text
capacity = 40

occupiedSeats = 41
```

result must be:

```text
availableSeats = 0

isFull = true
```

Never:

```text
availableSeats = -1
```

This is defensive query behavior.

---

# 38. Test Course Identifier by Code

Test:

```java
getCourseInformation("CS301");
```

Expected:

```text
CS301
```

---

# 39. Test Course Identifier by Name

Test:

```java
getCourseInformation(
    "Data Structures & Algorithms"
);
```

Expected:

```text
same CS301 Course
```

---

# 40. Case-Insensitive Course Lookup

Test:

```text
cs301

Cs301

CS301
```

Expected:

```text
same Course
```

if Step 3 specifies case-insensitive normalization.

---

# 41. Whitespace Normalization

Test:

```text
"  CS301  "
```

Expected:

```text
CS301
```

after:

```text
trim()
```

---

# 42. Ambiguous Course Test

Create:

```text
Introduction to Programming

Advanced Programming
```

Query:

```text
Programming
```

If partial matching is supported and multiple results exist:

expected:

```text
ACADEMIC_COURSE_AMBIGUOUS
```

Do NOT arbitrarily return the first database row.

---

# 43. Unknown Course Test

Query:

```text
XYZ999
```

Expected:

```text
ACADEMIC_COURSE_NOT_FOUND
```

for:

```text
getCourseInformation("XYZ999")
```

---

# 44. Existing Course But Student Not Enrolled

Create Course:

```text
CS999
```

but no Enrollment for authenticated Student.

Call:

```java
getMyCourseSection("CS999");
```

Expected:

```text
STUDENT_COURSE_ENROLLMENT_NOT_FOUND
```

Do NOT return another Student's CS999 section.

---

# 45. Another Student Security Fixture

Create Student B:

```text
Student ID:
16

Student Code:
STD-2024-0002

Status:
ACTIVE
```

Enroll Student B in:

```text
CS401 SEC-01
```

Student A is not enrolled.

Authenticate as Student A.

Expected:

```text
getMyEnrollments()
```

must never contain:

```text
Student B CS401 Enrollment
```

---

# 46. Current Student Isolation Test

Authenticate:

```text
Student A
```

Call:

```text
getMyAcademicProfile()

getMyEnrollments()

getMyCourses()

getMySchedule()
```

Every private result must belong to:

```text
Student A
```

No service method should accept an AI-controlled:

```text
studentId
```

for these `getMy...` operations.

---

# 47. Account Status Tests

Test authenticated Student states:

```text
ACTIVE

PENDING

INACTIVE

SUSPENDED

is_deleted = TRUE
```

Expected:

```text
ACTIVE
→ Academic queries allowed
```

Others:

```text
→ rejected according to Student security policy
```

Recommended error:

```text
STUDENT_ACCOUNT_NOT_ACTIVE
```

or reuse existing canonical Student error.

---

# 48. Force Password Change Test

If:

```text
force_password_change = TRUE
```

and existing Student security policy restricts academic APIs:

Expected:

```text
403
PASSWORD_CHANGE_REQUIRED
```

Step 4 must verify Academic Query behavior matches the existing authentication policy.

---

# 49. Empty Enrollment Test

Create an ACTIVE Student with:

```text
zero Enrollments
```

Expected:

```java
getMyEnrollments()
```

returns:

```text
[]
```

Expected:

```java
getMyCourses()
```

returns:

```text
[]
```

Expected:

```java
getMySchedule()
```

returns:

```text
[]
```

This is not:

```text
404
```

---

# 50. Missing Lecturer Test

If Student is enrolled in a Course Section where:

```text
lecture_id = null
```

then:

```java
getMyCourseLecturer(...)
```

must follow the Step 3 contract.

Recommended structured result:

```text
Course context exists

Lecturer fields = null
```

Do NOT invent a lecturer.

---

# 51. Missing Schedule Test

If:

```text
schedule = null
```

expected:

```text
schedule = null
```

The backend should not generate schedule text.

---

# 52. Missing Room Test

If:

```text
room = null
```

expected:

```text
room = null
```

No placeholder should be persisted.

---

# 53. Soft-Deleted Course Section Test

If the related Course Section is:

```text
is_deleted = TRUE
```

it must not be considered a normal active Student academic result.

Follow the existing Course Section domain policy.

Do not expose deleted academic structures through Student AI queries.

---

# 54. Closed Course Section Test

If a Student has historical Enrollment but Section status is:

```text
CLOSED
```

verify behavior against the intended domain policy.

For current:

```text
ACTIVE academic queries
```

do not silently treat unavailable/currently invalid section data as an active class if the project rules prohibit it.

Use existing Course Section business rules.

---

# 55. Direct Service Verification

The primary Step 4 tests should call:

```text
AcademicQueryService
```

directly.

Example:

```java
@Test
void shouldReturnAuthenticatedStudentCourses() {

    List<AcademicCourseQueryResult> result =
        academicQueryService.getMyCourses();

    assertThat(result)
        .isNotEmpty();
}
```

Authentication context must be prepared using the project's Student security test utilities.

---

# 56. Do Not Test Natural-Language Parsing Yet

Do NOT create a test like:

```java
academicQueryService.ask(
    "What courses am I enrolled in?"
);
```

unless such a method already legitimately exists for another reason.

Step 4 tests:

```text
Question
→ known service method
```

manually.

The natural language → tool mapping comes with the LLM/tool-calling stage.

---

# 57. Verification Scenario Definition

For documentation/tests, create a conceptual scenario record if useful:

```java
public record AcademicQueryVerificationScenario<T>(

    String question,

    String expectedMethod,

    T expectedResult

) {}
```

This may be test-only.

Do not expose it as production API.

---

# 58. Suggested Verification Matrix

Use this matrix in test documentation.

| ID | Question | Service Method | Expected |
|---|---|---|---|
| AQ-001 | What courses am I enrolled in? | `getMyEnrollments()` | Active enrollments |
| AQ-002 | Which section am I in for CS301? | `getMyCourseSection("CS301")` | `SEC-01` |
| AQ-003 | Who teaches CS301? | `getMyCourseLecturer("CS301")` | Student's section lecturer |
| AQ-004 | Where is my CS301 class? | `getMyCourseSection("CS301")` | `11-402` |
| AQ-005 | What is my schedule? | `getMySchedule()` | Active schedules |
| AQ-006 | How many credits is CS301? | `getCourseInformation("CS301")` | `3` |
| AQ-007 | What program am I in? | `getMyAcademicProfile()` | Student program |
| AQ-008 | Which department am I in? | `getMyAcademicProfile()` | Student department |
| AQ-009 | Is section 801 full? | `getSectionAvailability(801)` | availability result |
| AQ-010 | How many seats are left in section 801? | `getSectionAvailability(801)` | availableSeats |

---

# 59. Expected Structured Result Rule

Step 4 verifies:

```text
structured facts
```

not:

```text
natural language quality
```

Example expected:

```json
{
  "courseCode": "CS301",
  "sectionNumber": "SEC-01",
  "room": "11-402"
}
```

Do NOT test expected text:

```text
"Your CS301 class is conveniently located in room 11-402."
```

That belongs to the future LLM response layer.

---

# 60. Repository Query Verification

For each Academic Query method, verify underlying query behavior.

Especially inspect:

```text
getMyEnrollments()

getMyCourses()

getMySchedule()
```

because these may traverse:

```text
Enrollment
→ Course Section
→ Course
→ Semester
→ Lecturer
```

---

# 61. N+1 Verification

Enable SQL logging in test/development environment if needed.

For a Student with:

```text
10 ACTIVE Enrollments
```

avoid:

```text
1 enrollment query

10 Course Section queries

10 Course queries

10 Semester queries

10 Lecturer queries
```

Use existing:

```text
JOIN FETCH

@EntityGraph

DTO Projection
```

as appropriate.

Do NOT optimize prematurely if the existing query already performs well.

---

# 62. Query Count Goal

There is no need to hard-code an unrealistic exact query count unless the project's testing infrastructure supports it.

The goal is:

```text
No obvious N+1 behavior.
```

Document any optimization made.

---

# 63. API Verification

In addition to service tests, verify Step 2 Student APIs still return consistent data.

Test:

```text
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/enrollments

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/lecturers
```

These should agree with:

```text
AcademicQueryService
```

where they represent the same information.

---

# 64. Authentication API Test

Example:

```text
GET /api/v1/student/academic/courses
Authorization: Bearer <valid-student-token>
```

Expected:

```text
200 OK
```

---

# 65. Missing Token Test

Request:

```text
GET /api/v1/student/academic/courses
```

without Student JWT.

Expected:

```text
401 Unauthorized
```

---

# 66. Admin Token Test

Use Admin access token against:

```text
/api/v1/student/academic/**
```

Expected:

```text
rejected
```

unless Admin explicitly has Student authority, which is not recommended.

Student-specific academic APIs require:

```text
ROLE_STUDENT
```

---

# 67. Expired Token Test

Expired Student JWT:

Expected:

```text
401 Unauthorized
```

No Academic Query should execute.

---

# 68. Query Failure Must Not Leak Internals

Errors must not expose:

```text
SQL

database host

PostgreSQL error

Hibernate query

stack trace

JWT contents

repository class details
```

Use existing Global Exception Handler.

---

# 69. Course Code vs Course Name Consistency

Verify:

```java
getMyCourseSection("CS301")
```

and:

```java
getMyCourseSection(
    "Data Structures & Algorithms"
)
```

resolve to the same Student Enrollment when unambiguous.

Do the same for:

```text
getMyCourseLecturer

getCourseInformation
```

---

# 70. Course Name Ambiguity Rule

If multiple Courses match a partial name:

```text
Programming
```

do not choose:

```text
first result
```

Expected:

```text
ACADEMIC_COURSE_AMBIGUOUS
```

This is important for future AI reliability.

---

# 71. Deterministic Ordering

List responses should have deterministic ordering where meaningful.

Recommended:

```text
courseCode ASC
```

or another project-standard stable order.

For schedule, if the schedule is currently free-text, do not pretend it can be chronologically sorted reliably.

If existing query has deterministic ID/order, document that behavior.

---

# 72. Data Freshness Test

AcademicQueryService should query PostgreSQL source-of-truth data.

Test:

1. Read Section room.
2. Update room using normal academic management workflow.
3. Query again.
4. Verify new room is returned.

If Redis caching exists, verify cache invalidation correctly reflects the new data.

---

# 73. Enrollment Status Freshness

Scenario:

```text
Enrollment status:
ACTIVE
```

Query:

```text
getMyEnrollments()
```

Expected:

```text
included
```

Then Admin changes:

```text
ACTIVE → WITHDRAWN
```

Query again.

Expected:

```text
not included
```

for ACTIVE-only `getMyEnrollments()`.

This is important before AI integration.

---

# 74. Enrollment Creation Freshness

Scenario:

Student initially has:

```text
2 ACTIVE Enrollments
```

Admin creates another ACTIVE Enrollment.

Expected next query:

```text
getMyEnrollments()
```

returns:

```text
3
```

without stale data.

---

# 75. Enrollment Delete Freshness

If Admin soft-deletes an Enrollment:

Expected:

```text
getMyEnrollments()
```

no longer returns it.

---

# 76. Lecturer Update Freshness

If Admin changes the Course Section Lecturer:

Expected:

```text
getMyCourseLecturer("CS301")
```

returns the newly assigned Lecturer.

If caching is active, cache invalidation must be correct.

---

# 77. Room Update Freshness

If Admin changes:

```text
Room 11-402
→
Room 11-405
```

Expected:

```text
getMyCourseSection("CS301")
```

returns:

```text
11-405
```

---

# 78. Credit Update Freshness

If Course credit hours are changed through authorized Academic Management:

Expected:

```text
getCourseInformation("CS301")
```

returns the updated value.

The Academic Query Service must not maintain an independent duplicate source of truth.

---

# 79. No Mutation Test

Calling:

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

must not modify:

```text
students

enrollments

courses

course_sections

lectures

semesters
```

---

# 80. Read-Only Transaction Verification

Query methods should use:

```java
@Transactional(readOnly = true)
```

where consistent with the architecture.

No:

```text
save()

delete()

flush() for mutation
```

should be performed.

---

# 81. Unit Test Group

Create unit tests covering:

```text
Academic Profile Mapping

Enrollment Mapping

Course Mapping

Schedule Mapping

Section Mapping

Lecturer Mapping

Course Resolution

Section Availability Calculation

Null Handling

Exception Mapping
```

---

# 82. Integration Test Group

Create PostgreSQL-backed integration tests covering:

```text
Real Student relationships

Real Enrollment relationships

Course → Section → Lecturer

Semester joins

Status filtering

Soft-delete filtering

Section capacity counts

Security isolation
```

---

# 83. Security Test Group

Verify:

```text
Student A cannot see Student B data

Missing JWT rejected

Expired JWT rejected

Admin JWT rejected

Inactive Student rejected

Deleted Student rejected

force-password-change restriction honored
```

---

# 84. Negative Test Group

Verify:

```text
Unknown Course

Ambiguous Course

Course exists but Student not enrolled

Deleted Course

Deleted Section

Missing Lecturer

Missing Room

Missing Schedule

No Enrollments

Invalid Student account state
```

---

# 85. Performance Test Group

Verify:

```text
No obvious N+1 queries

Lists remain efficient with multiple enrollments

Availability count query is direct

Course resolution queries use indexes where applicable
```

Do not turn Step 4 into premature benchmarking.

The goal is identifying major query problems.

---

# 86. Test Naming Convention

Recommended:

```text
shouldReturnAcademicProfileForAuthenticatedStudent

shouldReturnOnlyActiveEnrollments

shouldReturnCourseSectionForEnrolledCourse

shouldReturnLecturerFromStudentsOwnSection

shouldReturnRoomForStudentsCourseSection

shouldReturnActiveAcademicSchedule

shouldReturnCourseCreditHours

shouldCalculateAvailableSeats

shouldRejectOtherStudentDataAccess

shouldRejectAmbiguousCourseIdentifier
```

Use the project's test naming convention if already established.

---

# 87. No AI Dependencies

Step 4 must not add dependencies for:

```text
OpenAI

Anthropic

Gemini

Kimi

LangChain

Spring AI

Embedding models

Vector stores
```

Those belong to later steps.

---

# 88. No RAG Dependencies

Do NOT install:

```text
pgvector

Pinecone

Qdrant

Milvus

Chroma
```

in Step 4.

Step 4 tests structured academic data only.

---

# 89. No New Chat Endpoint

Do NOT create:

```text
POST /api/v1/student/assistant/chat
```

during Step 4.

That is the next AI Assistant API stage.

---

# 90. Optional Manual Verification

After automated tests pass, manually verify using Postman or another API client.

Examples:

```text
GET /api/v1/student/academic/profile
```

```text
GET /api/v1/student/academic/enrollments
```

```text
GET /api/v1/student/academic/courses
```

```text
GET /api/v1/student/academic/schedule
```

```text
GET /api/v1/student/academic/lecturers
```

with a real Student JWT from the development environment.

Do not commit real tokens.

---

# 91. Manual Question Checklist

The developer should be able to manually answer:

```text
"What courses am I enrolled in?"
```

from:

```text
/enrollments
```

---

```text
"Which section am I in for CS301?"
```

from:

```text
AcademicQueryService
.getMyCourseSection("CS301")
```

---

```text
"Who teaches CS301?"
```

from:

```text
AcademicQueryService
.getMyCourseLecturer("CS301")
```

---

```text
"Where is my CS301 class?"
```

from:

```text
AcademicQueryService
.getMyCourseSection("CS301")
```

---

```text
"What is my schedule?"
```

from:

```text
/schedule
```

---

```text
"How many credits is CS301?"
```

from:

```text
AcademicQueryService
.getCourseInformation("CS301")
```

---

# 92. Verification Result Documentation

Optionally maintain a simple test matrix in:

```text
spec-md/
```

or test documentation.

Example:

```text
AQ-001 PASS
getMyEnrollments()
Correct active Enrollment results.

AQ-002 PASS
getMyCourseSection("CS301")
Correct SEC-01.

AQ-003 PASS
getMyCourseLecturer("CS301")
Correct Lecturer from Student Section.
```

Do not manually mark tests as PASS unless automated/manual verification actually succeeded.

---

# 93. Failure Rule

If any question cannot be answered deterministically from the backend:

Do NOT:

```text
add AI first
```

Instead determine whether the problem is:

```text
Missing database data

Missing relationship

Missing service method

Incorrect query

Missing normalized model

Ambiguous business rule
```

Fix the academic data layer before AI integration.

---

# 94. Example — Backend Data Missing

Question:

```text
"What room is my CS301 class in?"
```

If Course Section does not contain room information:

Do NOT expect the future AI to solve it.

Required outcome:

```text
room = null
```

Then either:

```text
accept that the information is unavailable
```

or improve the Academic Management data model.

---

# 95. Example — Schedule Not Normalized

Question:

```text
"What class do I have tomorrow?"
```

Current data:

```text
schedule = "Tuesday 09:00-11:45"
```

Do NOT implement fragile date reasoning in Step 4.

Record as future requirement:

```text
Normalize Course Section Schedule
```

before supporting advanced timetable queries.

---

# 96. Step 4 Supported Question Level

Supported reliably:

```text
What courses am I enrolled in?

What program am I in?

Which section am I in for CS301?

Who teaches my CS301 section?

Where is my CS301 class?

What schedule information is stored for my courses?

How many credits is CS301?

What are the prerequisites for CS301?

How many seats are available in section 801?
```

---

# 97. Step 4 Unsupported Context Questions

Do NOT claim support yet for:

```text
"Who teaches the first one?"

"Where is it?"

"What about tomorrow?"

"How many credits is this one?"

"Is that section full?"
```

These require:

```text
conversation context
```

which comes later.

---

# 98. Step 4 Unsupported Advanced Schedule Questions

Do NOT claim reliable support yet for:

```text
"What is my next class?"

"What do I have tomorrow?"

"Do I have classes this afternoon?"

"Do I have timetable conflicts?"
```

unless schedule has already been normalized into:

```text
day_of_week

start_time

end_time
```

---

# 99. Step 4 Unsupported Graduation Questions

Do NOT support yet:

```text
"How many credits do I need to graduate?"

"What requirements am I missing?"

"Am I ready to graduate?"
```

These require future:

```text
Curriculum Management

RAG / structured curriculum rules

Course completion history

Grades / academic history
```

---

# 100. Step 4 Completion Criteria

Step 4 is complete only when:

```text
"What courses am I enrolled in?"
→ deterministic backend result

"Which section am I in for CS301?"
→ deterministic backend result

"Who teaches CS301?"
→ deterministic backend result

"Where is my CS301 class?"
→ deterministic backend result

"What is my schedule?"
→ deterministic backend result

"How many credits is CS301?"
→ deterministic backend result
```

No LLM must be required to obtain these facts.

---

# 101. Recommended Execution Order

Implement and test in this order:

```text
PHASE 1
Review Step 2 + Step 3
        ↓
PHASE 2
Prepare Test Fixtures
        ↓
PHASE 3
Verify Authentication Context
        ↓
PHASE 4
Test getMyAcademicProfile
        ↓
PHASE 5
Test getMyEnrollments
        ↓
PHASE 6
Test getMyCourses
        ↓
PHASE 7
Test getMySchedule
        ↓
PHASE 8
Test getMyCourseSection
        ↓
PHASE 9
Test getMyCourseLecturer
        ↓
PHASE 10
Test getCourseInformation
        ↓
PHASE 11
Test getSectionAvailability
        ↓
PHASE 12
Test Missing Data
        ↓
PHASE 13
Test Course Ambiguity
        ↓
PHASE 14
Test Student Isolation
        ↓
PHASE 15
Test Account Restrictions
        ↓
PHASE 16
Check N+1 Queries
        ↓
PHASE 17
Verify Student Academic REST APIs
        ↓
PHASE 18
Run Full Integration Tests
        ↓
PHASE 19
Run Full Project Tests
        ↓
PHASE 20
Run Maven Package
```

---

# 102. Final Test Flow

```text
Test Student JWT
       ↓
CurrentStudentProvider
       ↓
AcademicQueryService
       ↓
Existing Academic Services
       ↓
Repositories
       ↓
PostgreSQL
       ↓
Structured Query Result
       ↓
Assertions
```

No AI exists in this flow.

---

# 103. Final Academic Query Verification Map

```text
Question
    │
    ├── "What courses am I enrolled in?"
    │           ↓
    │      getMyEnrollments()
    │
    ├── "Which section am I in for CS301?"
    │           ↓
    │      getMyCourseSection("CS301")
    │
    ├── "Who teaches CS301?"
    │           ↓
    │      getMyCourseLecturer("CS301")
    │
    ├── "Where is my CS301 class?"
    │           ↓
    │      getMyCourseSection("CS301")
    │
    ├── "What is my schedule?"
    │           ↓
    │      getMySchedule()
    │
    ├── "How many credits is CS301?"
    │           ↓
    │      getCourseInformation("CS301")
    │
    ├── "What program am I in?"
    │           ↓
    │      getMyAcademicProfile()
    │
    └── "Is section 801 full?"
                ↓
       getSectionAvailability(801)
```

---

# 104. Acceptance Criteria

```text
TEST ENVIRONMENT
[ ] PostgreSQL-compatible integration tests
[ ] Existing Flyway migrations used
[ ] No H2 source-of-truth tests
[ ] No production test-data migration
[ ] Predictable academic fixtures

AUTHENTICATION
[ ] Student identity comes from authentication
[ ] Student A cannot access Student B data
[ ] Missing JWT rejected
[ ] Expired JWT rejected
[ ] Account-status restrictions work
[ ] forcePasswordChange restriction respected

ACADEMIC PROFILE
[ ] getMyAcademicProfile tested
[ ] Program correct
[ ] Department correct
[ ] Faculty correct
[ ] Semester correct

ENROLLMENTS
[ ] getMyEnrollments tested
[ ] ACTIVE returned
[ ] WITHDRAWN excluded by default
[ ] DROPPED excluded by default
[ ] Soft-deleted excluded
[ ] Empty list supported

COURSES
[ ] getMyCourses tested
[ ] Course data correct
[ ] Credits correct

COURSE SECTION
[ ] getMyCourseSection tested
[ ] Course code resolution works
[ ] Course name resolution works
[ ] Correct Student Section returned
[ ] Other Section not returned
[ ] Room correct
[ ] Schedule correct

LECTURER
[ ] getMyCourseLecturer tested
[ ] Lecturer comes from Student's own Section
[ ] Multiple Section scenario tested
[ ] Missing Lecturer handled

COURSE INFORMATION
[ ] getCourseInformation tested
[ ] Course Code lookup works
[ ] Course Name lookup works
[ ] Credits correct
[ ] Description correct
[ ] Prerequisite information correct
[ ] Unknown Course handled
[ ] Ambiguous Course handled

SCHEDULE
[ ] getMySchedule tested
[ ] ACTIVE classes only
[ ] Room correct
[ ] Schedule correct
[ ] Missing values handled safely

AVAILABILITY
[ ] getSectionAvailability tested
[ ] PENDING counted
[ ] ACTIVE counted
[ ] WITHDRAWN excluded
[ ] DROPPED excluded
[ ] Soft-deleted excluded
[ ] availableSeats correct
[ ] isFull correct
[ ] negative available seats prevented

DATA FRESHNESS
[ ] Enrollment creation reflected
[ ] Enrollment status change reflected
[ ] Enrollment delete reflected
[ ] Lecturer update reflected
[ ] Room update reflected
[ ] Course update reflected

PERFORMANCE
[ ] N+1 checked
[ ] Major query inefficiencies resolved

BOUNDARIES
[ ] No LLM added
[ ] No AI provider dependency
[ ] No RAG added
[ ] No embeddings added
[ ] No chatbot keyword parser added
[ ] No temporary production test endpoint added

BUILD
[ ] Unit tests pass
[ ] Integration tests pass
[ ] Security tests pass
[ ] mvn test passes
[ ] mvn clean package passes
```

---

# 105. Output Expected From AI Coding Agent

When implementation is complete, the coding agent should report:

```text
1. Tests/files created or modified

2. Academic Query methods verified

3. Test fixture strategy used

4. PostgreSQL integration strategy used

5. Security tests performed

6. Query/N+1 issues discovered and fixed

7. Any unsupported queries due to current data model

8. mvn test result

9. mvn clean package result
```

Do not simply report:

```text
"Step 4 completed."
```

without actual test results.

---

# 106. Next Step

After Step 4 passes, the Academic backend is ready for:

# Step 5 — AI Assistant Chat API

At that stage the architecture will change from:

```text
Question
    ↓
Manually Selected Academic Query Method
    ↓
AcademicQueryService
```

to:

```text
Student Natural-Language Question
        ↓
AI Assistant Chat API
        ↓
LLM
        ↓
Tool Selection
        ↓
AcademicQueryService
        ↓
Structured Academic Result
        ↓
LLM
        ↓
Natural-Language Answer
```

Step 5 should only begin after Step 4 proves the Academic Query Service can return correct, secure, deterministic academic information without any AI.