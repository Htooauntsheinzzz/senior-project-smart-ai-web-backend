# 41 — Step 13B: Student Mobile Academic Dashboard Data Completion

**Project:** Smart AI University Student Assistant  
**Implementation:** Spring Boot backend and PostgreSQL Flyway migrations  
**Target:** Flutter Student Mobile — Home, Profile, My Courses  
**Specification location:** `spec-md/41-student-mobile-academic-dashboard-backend.md`  
**Prerequisite:** Existing Student Authentication, Academic Management, Student Academic APIs and Enrollment modules  
**No RAG or LLM implementation required**

---

## 1. Objective

Implement the missing backend data required by the Student Mobile Figma design.

The current backend already supports student identity, academic information, course enrollments and some timetable information.

This feature must extend the existing architecture to support:

1. CGPA.
2. Total earned credits.
3. Current semester course count and enrolled credits.
4. Academic study year (Year 1, Year 2, Year 3, etc.).
5. Upcoming assignments and exam deadlines.
6. Structured course meetings.
7. Accurate next-class calculation.
8. Consistent academic information across Home, Profile and My Courses.

Do not modify the existing Flutter design in this backend task.

Do not create a separate database.

Do not implement Step 14 RAG in this feature.

---

## 2. Figma Reference

**Mobile design:**

`https://www.figma.com/design/RUhIF3MsPa9jTWfT0Dou02/Smart-Ai-University-Mobile`

Reviewed components:

```text
Home Page Screen
Node: 3:209

Profile Screen
Node: 28:30

My Courses Screen
Node: 42:1018

Selected class schedule component
Node: 42:1260
```

### Home Page

Required dynamic sections:

```text
Student Greeting
Student Program / Study Year

Next Class
- Course code
- Course name
- Meeting type
- Start time
- End time
- Building
- Room
- Countdown
- View Class
- Navigate

Upcoming Deadlines
- Assignment or exam
- Title
- Course code
- Due date/time
- Due today count
```

### Profile Screen

```text
Student Name
Student ID

CGPA
Credits Earned
Current Courses

Student Information
- Major
- Academic Year / Study Year
- Faculty
- Department
- Program / Degree
- Semester
- University Email
```

### My Courses Screen

```text
Current Semester
Course Count
Enrolled Credits
Lecturer Count

Course Code
Course Name
Section Number
Credit Hours
Lecturer

Multiple Meetings
- Lecture
- Lab
- Weekday
- Start Time
- End Time
- Room
```

The displayed Figma numbers are mock data.

Never hard-code the demonstrated CGPA, credit totals, course count, course names, lecturer names or deadline dates.

---

# Part A — Mandatory Backend Rules

## 3. Coding Agent Instructions

Before any change:

1. Read `AGENTS.md`.
2. Read `spec-md/`.
3. Inspect all existing Flyway migration files.
4. Inspect `students`, `programs`, `courses`, `course_sections` and `enrollments`.
5. Inspect Student Academic Controller and `AcademicQueryService`.
6. Inspect Admin course and enrollment management implementations.
7. Check whether grade, transcript, meeting, assignment or exam features have already been implemented.
8. Reuse existing schema and services wherever possible.
9. Follow current response wrappers, package layout, error handling, and authorization.
10. Verify requirements against real PostgreSQL schema, not Figma mock data.

Never edit an already applied Flyway migration.

Use the actual next available migration versions.

Do not create redundant entities if the project already contains a suitable normalized model.

---

## 4. Existing Academic Data

Reuse:

```text
students
student_credentials
faculties
departments
programs
semesters
lectures
courses
course_sections
enrollments
```

Preserve enrollment statuses:

```text
PENDING
ACTIVE
WITHDRAWN
DROPPED
```

Do not introduce `COMPLETED` into the enrollment status enum merely to calculate earned credits.

Course completion and final grades are a separate academic-results concern.

---

# Part B — Flyway Migration

## 5. Required New Data Model

Recommended normalized tables:

```text
grading_schemes
grade_definitions
student_course_results
course_section_meetings
course_assignments
course_exams
```

Add to `students` only if missing:

```text
study_year
```

If the project already has equivalent grade, meeting, assignment or exam tables, adapt and reuse those instead.

Do not duplicate them.

---

## 6. Student Study Year

The current database uses:

```text
students.academic_year
```

to represent a value such as:

```text
2026
```

The Figma Profile and Home designs also need:

```text
Year 3
```

These are different concepts.

Add, if absent:

```sql
ALTER TABLE students
ADD COLUMN study_year SMALLINT;

ALTER TABLE students
ADD CONSTRAINT chk_students_study_year
CHECK (
    study_year IS NULL
    OR study_year BETWEEN 1 AND 10
);
```

Examples:

```text
academic_year = 2026
study_year = 3
enrollment_year = 2024
```

Do not derive study year simply from subtracting enrollment year. Students can have interruptions, transfers, changed programs or nonstandard progression.

The study-year value must come from an authorized academic record or Admin update.

Update Student Admin create/edit DTOs and validation to support this field.

Do not allow the Mobile App to submit arbitrary study-year updates.

---

# Part C — CGPA and Credits

## 7. Academic Results Architecture

Use:

```text
Student
    ↓
Enrollment
    ↓
Course Section
    ↓
Course

Enrollment
    ↓
Student Course Result
    ↓
Grade Definition
    ↓
Grading Scheme
```

The backend needs official finalized course results to compute CGPA and earned credits.

Do not calculate CGPA from active course enrollments.

---

## 8. Grading Schemes

Create:

```text
grading_schemes
```

Recommended fields:

| Column | Type |
|---|---|
| id | BIGINT identity PK |
| scheme_code | VARCHAR(50), UNIQUE |
| scheme_name | VARCHAR(150) |
| description | TEXT NULL |
| is_active | BOOLEAN |
| created_at | TIMESTAMP |

The project must support the university's approved grading rules.

Do not assume every Faculty or Program follows the same scale without verification.

The system must identify which grading scheme applies to a course result.

---

## 9. Grade Definitions

Create:

```text
grade_definitions
```

Recommended fields:

| Column | Type |
|---|---|
| id | BIGINT identity PK |
| grading_scheme_id | BIGINT FK |
| grade_code | VARCHAR(20) |
| grade_point | NUMERIC(4,2) NULL |
| counts_in_gpa | BOOLEAN |
| earns_credit | BOOLEAN |
| is_passing | BOOLEAN |
| is_active | BOOLEAN |

Unique:

```text
(grading_scheme_id, grade_code)
```

The records must represent official university grading policy.

Examples of grade categories that may require configuration:

```text
Letter grades
Pass / Fail
Withdrawn
Incomplete
Transfer / exemption
```

Do not invent an institutional letter-grade-to-point mapping.

A grade may earn credits without contributing to CGPA if university policy allows it.

---

## 10. Student Course Results

Create:

```text
student_course_results
```

Recommended fields:

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity PK | |
| enrollment_id | BIGINT FK UNIQUE | Enrollment |
| grade_definition_id | BIGINT FK | Final Grade |
| credits_attempted | NUMERIC(5,2) | Frozen at finalization |
| result_status | VARCHAR(20) | DRAFT / FINALIZED |
| finalized_at | TIMESTAMP NULL | |
| created_by | BIGINT FK | Admin |
| created_at | TIMESTAMP | |
| updated_by | BIGINT FK NULL | Admin |
| updated_at | TIMESTAMP NULL | |

The `credits_attempted` field preserves the credit value applicable to the historical course attempt.

It is deliberately a historical snapshot. Editing the current Course credit hours must not silently change past CGPA or earned-credit calculations.

Only `FINALIZED` results contribute to official progress calculations.

Do not create another Student ID on this table because the Student is already derivable through `enrollment_id`.

Grade corrections must be authorized and auditable.

---

## 11. CGPA Calculation

Compute CGPA from finalized, eligible academic results.

For a standard credit-weighted GPA policy:

```text
CGPA =
SUM(grade_point × credits_attempted)
------------------------------------
SUM(credits_attempted)
```

Only include attempts whose grade definition indicates:

```text
counts_in_gpa = TRUE
```

Apply official retake/replacement policies.

Do not double-count repeated courses for earned credits.

The official rules must define whether repeated attempts contribute to GPA, replace previous grades, or follow another calculation method.

If this policy is not configured, do not return an invented CGPA.

If no eligible finalized results exist:

```json
{
  "cgpa": null
}
```

Do not return `0.00` unless zero is an actual computed result.

Round displayed CGPA to two decimal places using the approved institutional rounding rule.

---

## 12. Earned Credits

Calculate from finalized passing course results.

General principle:

```text
creditsEarned =
sum of eligible credits from successfully completed courses
```

Grade definitions decide whether a result earns credit.

Do not count:

```text
Pending enrollment
Active but unfinished enrollment
Withdrawn enrollment
Dropped enrollment
Incomplete result
Failed course
```

unless official university policy specifically makes the result eligible.

Handle repeat-course deduplication according to the approved academic policy.

Transfer credits and credit exemptions should be included only when authoritative records and official rules support them.

If those records do not yet exist, document that the returned value covers recorded completed courses only, rather than claiming it is the student's official overall total.

---

## 13. Academic Progress API

Implement:

```http
GET /api/v1/student/academic/progress
```

Student identity must come from the JWT.

Example:

```json
{
  "cgpa": 3.42,
  "creditsEarned": 87,
  "currentCourses": 5,
  "currentEnrolledCredits": 14,
  "studyYear": 3,
  "academicYear": 2026,
  "dataStatus": "AVAILABLE"
}
```

These example numbers demonstrate the response shape only.

If grade data is unavailable:

```json
{
  "cgpa": null,
  "creditsEarned": null,
  "currentCourses": 5,
  "currentEnrolledCredits": 14,
  "studyYear": 3,
  "academicYear": 2026,
  "dataStatus": "PARTIAL"
}
```

Use `null` for unknown academic metrics.

Do not replace unavailable metrics with invented values or zeros.

---

# Part D — Course Section Meetings

## 14. Current Problem

Current schedule values are free text.

Example:

```text
Mon/Tues 02:00-4:00
```

This cannot reliably support:

```text
Next class calculation
Start-time countdown
Multiple sessions
Lecture / Lab differentiation
End-time calculation
```

Use normalized course meetings.

---

## 15. Course Section Meetings Table

Create:

```text
course_section_meetings
```

Recommended fields:

| Column | Type |
|---|---|
| id | BIGINT identity PK |
| course_section_id | BIGINT FK |
| meeting_type | VARCHAR(30) |
| day_of_week | SMALLINT |
| start_time | TIME |
| end_time | TIME |
| effective_from | DATE |
| effective_to | DATE |
| room_id | BIGINT NULL |
| room_label | VARCHAR(150) NULL |
| is_active | BOOLEAN |
| is_deleted | BOOLEAN |
| created_at | TIMESTAMP |
| updated_at | TIMESTAMP NULL |

Meeting types:

```text
LECTURE
LAB
TUTORIAL
```

Weekday convention:

```text
1 = Monday
2 = Tuesday
3 = Wednesday
4 = Thursday
5 = Friday
6 = Saturday
7 = Sunday
```

Validate:

```text
day_of_week BETWEEN 1 AND 7

end_time > start_time

effective_to >= effective_from
```

`room_id` must reference the existing Room entity if the Campus Management module has a suitable `rooms` table.

If no room table exists yet, keep the room reference optional and support an existing authoritative room label.

Do not introduce conflicting room information from multiple independent sources.

If overnight meetings are officially needed, model their date boundaries explicitly instead of forcing them into this initial same-day interval design.

---

## 16. Multiple Meeting Example

Course:

```text
CSC-452 Mobile Programming
```

Section:

```text
02
```

Meeting A:

```text
LECTURE
Monday
09:00–11:45
Room 1103
```

Meeting B:

```text
LAB
Wednesday
09:00–11:45
Room Lab 1108
```

These are illustrative records based on the kind of multi-session display used in Figma.

A course section can have many meeting rows.

Do not store multiple weekdays as one comma-separated string in the new model.

---

## 17. Existing Course Section Schedule

Preserve the existing:

```text
course_sections.schedule
```

for backward compatibility during migration.

New accurate scheduling must use:

```text
course_section_meetings
```

Do not automatically parse ambiguous legacy schedule strings into normalized rows and assume accuracy.

Require review or approved mapping for ambiguous records.

Eventually, the legacy string can become a derived display representation or be deprecated through a separate migration.

---

# Part E — Next Class Calculation

## 18. Next Class API

Implement:

```http
GET /api/v1/student/academic/next-class
```

Flow:

```text
Student JWT
    ↓
Current Student
    ↓
Active Enrollments
    ↓
Course Sections
    ↓
Normalized Course Meetings
    ↓
Generate Upcoming Occurrences
    ↓
Filter by Effective Date
    ↓
Select Earliest Valid Future Class
    ↓
Return Structured Result
```

Use:

```text
Asia/Bangkok
```

as the university timezone unless authoritative campus configuration specifies otherwise.

---

## 19. Next Class Response

Example:

```json
{
  "courseId": 21,
  "courseCode": "CSC-452",
  "courseName": "Mobile Programming",
  "courseSectionId": 81,
  "sectionNumber": "02",
  "meetingType": "LECTURE",
  "dayOfWeek": "MONDAY",
  "startsAt": "2026-11-09T09:00:00+07:00",
  "endsAt": "2026-11-09T11:45:00+07:00",
  "buildingName": "Building 11",
  "roomName": "1103",
  "minutesUntilStart": 45,
  "status": "UPCOMING"
}
```

Values are illustrative.

The response must contain complete date-time information, not only weekday and time strings.

For a mobile countdown, Flutter should recalculate remaining time from `startsAt` to avoid a frozen countdown after initial API loading.

---

## 20. Next Class Rules

Only include:

```text
Student's ACTIVE enrollments
Non-deleted sections
Active course meetings
Meetings within effective dates
Valid future meeting occurrences
```

Handle:

```text
No class today
Weekend
Crossing into next week
Null room
Cancelled or inactive section
Meeting already started
Meeting already ended
Semester no longer in session
```

Do not claim a class is scheduled during an official holiday or cancellation if the system has verified exception data indicating otherwise.

If no academic-calendar/exception data exists, clearly limit results to the regular recurring timetable.

If no upcoming class exists, return HTTP 200 with `data: null` or the established empty-result contract.

Never fabricate a next class.

---

# Part F — Assignments and Exam Deadlines

## 21. Objective

Provide the Home Page with:

```text
Upcoming Deadlines
```

Including:

```text
Assignment name
Exam title
Course
Due date/time
Due today count
```

First inspect whether the Assignment and Exam modules already have backend tables.

If they do, reuse them.

If not, implement the minimal normalized schemas needed by the planned Admin Academic Activities features.

---

## 22. Assignment Table

Recommended:

```text
course_assignments
```

Fields:

| Column | Type |
|---|---|
| id | BIGINT identity PK |
| course_section_id | BIGINT FK |
| title | VARCHAR(255) |
| description | TEXT NULL |
| due_at | TIMESTAMPTZ |
| status | VARCHAR(20) |
| is_deleted | BOOLEAN |
| created_by | BIGINT FK |
| created_at | TIMESTAMP |
| updated_by | BIGINT FK NULL |
| updated_at | TIMESTAMP NULL |

Status:

```text
DRAFT
PUBLISHED
CLOSED
```

Only published assignments should appear in normal upcoming deadlines.

Do not assume that an assignment is unfinished merely because it has a due date.

If assignment submission tracking is later implemented, filter completed submissions according to the relevant academic policy.

---

## 23. Exam Table

Recommended:

```text
course_exams
```

Fields:

| Column | Type |
|---|---|
| id | BIGINT identity PK |
| course_section_id | BIGINT FK |
| title | VARCHAR(255) |
| exam_type | VARCHAR(20) |
| starts_at | TIMESTAMPTZ |
| ends_at | TIMESTAMPTZ |
| status | VARCHAR(20) |
| room_id | BIGINT NULL |
| room_label | VARCHAR(150) NULL |
| is_deleted | BOOLEAN |
| created_by | BIGINT FK |
| created_at | TIMESTAMP |
| updated_at | TIMESTAMP NULL |

Exam types:

```text
QUIZ
MIDTERM
FINAL
PRACTICAL
```

Status:

```text
DRAFT
SCHEDULED
CANCELLED
COMPLETED
```

Validate end time occurs after start time.

Only officially scheduled, non-cancelled exams should appear as upcoming.

If the existing Exam Management module defines compatible status values or fields, follow that contract rather than adding duplicate tables or statuses.

---

## 24. Upcoming Deadline API

Implement:

```http
GET /api/v1/student/academic/deadlines
```

Recommended parameters:

```text
limit
from
to
```

Defaults should be bounded and configurable.

Only return deadlines for the authenticated student's eligible course sections.

Merge:

```text
Published Assignments
+
Scheduled Exams
```

Sort by upcoming date-time ascending.

---

## 25. Deadline Response

Example:

```json
{
  "total": 3,
  "dueTodayCount": 1,
  "items": [
    {
      "id": 31,
      "type": "ASSIGNMENT",
      "title": "Mobile UI Prototype",
      "courseCode": "CSC-401",
      "courseName": "Mobile Programming",
      "deadlineAt": "2026-11-04T23:59:00+07:00",
      "status": "UPCOMING"
    },
    {
      "id": 42,
      "type": "EXAM",
      "title": "Data Structures Midterm",
      "courseCode": "CSC-311",
      "courseName": "Data Structures",
      "deadlineAt": "2026-11-06T09:00:00+07:00",
      "status": "UPCOMING"
    }
  ]
}
```

For exams, `deadlineAt` represents the exam start time for this unified mobile timeline. An exam is an upcoming event, not an assignment submission deadline.

Count `dueTodayCount` using the university's local calendar date.

Do not send deadlines belonging to another Student's un-enrolled section.

---

# Part G — Student Information

## 26. Profile Field Mapping

Use the existing Student Academic Profile API:

```http
GET /api/v1/student/academic/profile
```

Extend it without removing or renaming existing response fields.

| Figma Field | Backend Source |
|---|---|
| Student Name | `students.first_name`, `last_name` |
| Student ID | `students.student_code` |
| University Email | `students.university_email` |
| Faculty | `faculties` |
| Department | `departments` |
| Major | Official Program/Major name |
| Program / Degree | Existing Program and `degree_level` |
| Academic Year | `students.academic_year` |
| Study Year | `students.study_year` |
| Semester | `semesters` |
| CGPA | Academic Progress Service |
| Earned Credits | Academic Progress Service |

For the current model, Program/Major is stored through:

```text
students.program_id
    ↓
programs.id
```

Use the Program's official name for the Major label if that accurately represents the university's program structure.

Use `programs.degree_level` for the degree/program qualification label where appropriate.

Do not create a redundant Student `major_name` string column simply to match Figma wording.

If the real university distinguishes a degree program and a separate major, design a normalized Major relationship later rather than silently conflating them.

---

## 27. Profile API Response

Example:

```json
{
  "studentCode": "6512345678",
  "fullName": "Example Student",
  "universityEmail": "student@rsu.ac.th",
  "faculty": {
    "id": 1,
    "name": "Faculty of Information Technology"
  },
  "department": {
    "id": 2,
    "name": "Computer Science"
  },
  "program": {
    "id": 5,
    "name": "Computer Science",
    "degreeLevel": "Bachelor of Science"
  },
  "semester": {
    "id": 3,
    "name": "Semester 1"
  },
  "academicYear": 2026,
  "studyYear": 3
}
```

Student-specific values are illustrative.

Preserve current DTO compatibility. The shape above is a target concept, not permission to replace an existing established response contract.

Unknown optional fields must remain null instead of being fabricated.

---

# Part H — My Courses

## 28. Current Semester Summary

The My Courses Figma screen shows:

```text
5 Courses
14 Credits
Lecturers
```

Calculate from:

```text
ACTIVE student enrollments
    ↓
Course Sections
    ↓
Courses
```

Current semester count:

```text
COUNT(DISTINCT enrolled course)
```

Current enrolled credits:

```text
SUM(credit_hours of unique enrolled courses)
```

Do not sum duplicate course credits if a join creates multiple rows per course because of multiple meetings or lecturers.

Lecturer count should use distinct assigned lecturer identifiers.

Use appropriate filtering for the student's current academic term based on verified current data.

Do not assume all historical ACTIVE enrollment rows belong to the current semester.

---

## 29. Existing Courses API

Reuse:

```http
GET /api/v1/student/academic/courses
```

Extend the response with normalized meeting data.

Example:

```json
{
  "courseCode": "CSC-452",
  "courseName": "Mobile Programming",
  "creditHours": 3,
  "sectionNumber": "02",
  "lecturerName": "Example Lecturer",
  "meetings": [
    {
      "meetingType": "LECTURE",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00",
      "endTime": "11:45",
      "roomName": "1103"
    },
    {
      "meetingType": "LAB",
      "dayOfWeek": "WEDNESDAY",
      "startTime": "09:00",
      "endTime": "11:45",
      "roomName": "Lab 1108"
    }
  ]
}
```

Preserve compatibility with existing Student mobile consumers.

Do not replace existing meeting-related fields without an intentional API migration.

---

# Part I — Home Dashboard API

## 30. Student Home Summary

Create an aggregation endpoint:

```http
GET /api/v1/student/home/summary
```

This is a read-only mobile convenience endpoint.

It should call existing services rather than duplicate academic business logic.

Recommended:

```text
StudentHomeService
    ├── StudentAcademicProfileService
    ├── AcademicProgressService
    ├── AcademicScheduleService
    ├── AcademicDeadlineService
    └── Existing Announcement Service
```

Do not let the controller execute independent SQL queries.

---

## 31. Example Home Summary Response

```json
{
  "student": {
    "displayName": "Example Student",
    "programName": "Computer Science",
    "studyYear": 3
  },
  "nextClass": {
    "courseCode": "CSC-452",
    "courseName": "Mobile Programming",
    "meetingType": "LECTURE",
    "startsAt": "2026-11-09T09:00:00+07:00",
    "endsAt": "2026-11-09T11:45:00+07:00",
    "buildingName": "Building 11",
    "roomName": "1103"
  },
  "deadlines": {
    "dueTodayCount": 1,
    "items": []
  }
}
```

The real response should populate `items` from the academic deadline service.

If Announcements already has a Student API, reuse it or aggregate a limited preview through the existing service.

Do not fabricate announcements or notification counts.

---

# Part J — Admin APIs

## 32. Grade Management

Implement minimal authorized Admin academic-result endpoints:

```http
GET  /api/v1/admin/academic/course-results

POST /api/v1/admin/academic/course-results

PUT  /api/v1/admin/academic/course-results/{id}

POST /api/v1/admin/academic/course-results/{id}/finalize
```

Require suitable academic-record permissions.

Grade finalization must validate:

```text
Enrollment exists
Student exists
Course exists
Grade definition valid
Grading scheme applies
Result is finalizable
```

Prevent unauthorized students from writing their own results.

Preserve an audit trail for grade corrections.

Avoid physically deleting historical academic results.

Do not expose a public Student grade-editing endpoint.

---

## 33. Course Meeting Management

Implement within existing Course Sections management:

```http
GET  /api/v1/admin/course-sections/{id}/meetings

POST /api/v1/admin/course-sections/{id}/meetings

PUT  /api/v1/admin/course-sections/{id}/meetings/{meetingId}

DELETE /api/v1/admin/course-sections/{id}/meetings/{meetingId}
```

Match the existing Admin Course Sections API prefix if it differs.

Validate:

```text
Course Section exists
Correct day and time
No invalid date range
Room exists when referenced
No duplicate/overlapping meeting for the same section
```

If an Admin Timetable feature already manages meetings, integrate with it instead of introducing a competing schedule authority.

---

## 34. Assignment and Exam Management

If these modules are not yet implemented, create minimal Admin endpoints:

```http
/api/v1/admin/assignments

/api/v1/admin/exams
```

Support:

```text
Create
Read
Update
Publish / Schedule
Cancel / Close
Soft Delete
```

These APIs should be reusable by the full future Academic Activities screens.

Do not build duplicate assignment/exam tables solely for the Student Home screen.

---

# Part K — Security

## 35. Student Authorization

Every Student endpoint must use:

```http
Authorization: Bearer <student-access-token>
```

Resolve identity through:

```text
SecurityContext
    ↓
CurrentStudentProvider
    ↓
Student ID
```

Never accept:

```text
studentId
```

from Student request bodies for these personal academic queries.

Student must be:

```text
ACTIVE
is_deleted = FALSE
```

Respect the existing forced-password-change restrictions.

Students must not access another student's grades, academic progress or deadlines.

---

## 36. Admin Security

Academic result mutations must be restricted to explicitly authorized academic administrators.

Protect finalized grades from ordinary Student and unauthorized Admin changes.

Audit:

```text
createdBy
updatedBy
finalizedBy where required
timestamp
previous value
new value
reason for correction
```

Do not log unnecessary private grade data in ordinary request logs.

---

# Part L — AI Assistant Integration

## 37. Keep Existing AI Working

Do not break:

```http
POST /api/v1/student/assistant/chat
```

After these backend features are verified, existing Gemini tool calling may be extended with controlled read-only tools:

```text
get_my_academic_progress

get_my_next_class

get_my_upcoming_deadlines

get_my_current_courses
```

All tools must reuse backend services.

Do not allow Gemini to write grades, create assignments, or modify schedules.

The assistant must never fabricate CGPA, earned credits or deadline dates when backend data is missing.

RAG is unnecessary for these structured academic facts.

---

# Part M — Required Tests

## 38. CGPA Tests

Verify:

```text
No finalized results → CGPA null

Valid finalized results → correctly weighted CGPA

Credits correctly weighted

Failed grades follow configured GPA policy

Pass/fail grades follow configured GPA policy

Incomplete results not included prematurely

Repeated courses follow official policy

Different students remain isolated

Grade correction updates calculation correctly
```

---

## 39. Earned Credit Tests

Verify:

```text
Current ACTIVE enrollment does not count as earned credit

Finalized passing course earns credits

Failed course does not earn credits

Withdrawn enrollment does not earn credits

Repeated courses not double-counted improperly

Course credit snapshot remains historically correct

Unknown transfer credits are not invented
```

---

## 40. Schedule and Next Class Tests

Verify:

```text
Multiple weekly meetings supported

Lecture and Lab separated

Monday-to-Sunday rollover correct

Time calculations use Asia/Bangkok

No upcoming class returns safe empty response

Inactive meetings excluded

Outside effective dates excluded

Current class start/end handled correctly

Null room does not crash

Student cannot retrieve another student's schedule
```

---

## 41. Deadline Tests

Verify:

```text
Only enrolled sections shown

Published assignments shown

Draft assignments hidden

Cancelled exams hidden

Deadlines sorted correctly

Due-today count uses Bangkok date

Date/time serialization correct

No deadlines returns empty list

Different Students remain isolated
```

---

## 42. API Tests

Use:

```text
Spring Boot integration tests

JUnit

Mockito

PostgreSQL Testcontainers
```

Test authenticated access with Student JWTs.

Do not use a real Gemini API for these tests.

Use development/test academic data only.

Required verification:

```bash
mvn test
mvn clean package
```

---

# Part N — Postman Verification

## 43. Test Endpoints

After implementation:

```http
GET /api/v1/student/academic/profile

GET /api/v1/student/academic/progress

GET /api/v1/student/academic/courses

GET /api/v1/student/academic/schedule

GET /api/v1/student/academic/next-class

GET /api/v1/student/academic/deadlines

GET /api/v1/student/home/summary
```

Use valid Student JWT authentication.

Verify all results against PostgreSQL test fixtures.

Do not mark the feature complete based only on HTTP 200 responses.

Also verify that returned values are mathematically correct and belong to the authenticated Student.

---

# Part O — Implementation Workflow

## 44. Required Development Order

```text
PHASE 1
Inspect existing schema, backend and APIs
    ↓
PHASE 2
Confirm university grading and credit policies
    ↓
PHASE 3
Create Flyway migration for Study Year
    ↓
PHASE 4
Create normalized Grading Scheme tables
    ↓
PHASE 5
Create Student Course Results
    ↓
PHASE 6
Run Flyway and verify PostgreSQL
    ↓
PHASE 7
Implement Admin Grade APIs
    ↓
PHASE 8
Implement CGPA/Earned Credits Service
    ↓
PHASE 9
Implement Student Academic Progress API
    ↓
PHASE 10
Create normalized Course Section Meetings
    ↓
PHASE 11
Implement Admin Course Meeting APIs
    ↓
PHASE 12
Implement Next Class Calculation
    ↓
PHASE 13
Create/reuse Assignment and Exam tables
    ↓
PHASE 14
Implement Admin Assignment and Exam APIs
    ↓
PHASE 15
Implement Student Upcoming Deadlines API
    ↓
PHASE 16
Extend Profile and My Courses response DTOs
    ↓
PHASE 17
Implement Student Home Summary API
    ↓
PHASE 18
Run Unit and PostgreSQL Integration Tests
    ↓
PHASE 19
Run Maven Build
    ↓
PHASE 20
Manual Postman Verification
    ↓
PHASE 21
Connect existing Flutter screens to APIs
```

For implementation safety, complete and verify the Flyway migration phase before writing dependent services.

Do not continue past a failed migration or unresolved grading policy.

---

# Part P — Acceptance Criteria

## 45. Database

- [ ] Existing PostgreSQL database reused.
- [ ] No duplicate Student or Enrollment tables.
- [ ] Study Year stored separately from Academic Year.
- [ ] Grading schemes normalized.
- [ ] Grade definitions normalized.
- [ ] Student course results implemented.
- [ ] Historical attempted credits preserved correctly.
- [ ] Course meetings normalized.
- [ ] Multiple lecture/lab meetings supported.
- [ ] Assignments and exams reuse existing modules where available.
- [ ] Flyway migrations pass.
- [ ] Hibernate validation passes.

## 46. APIs

- [ ] Student academic progress works.
- [ ] CGPA correctly computed.
- [ ] Earned credits correctly computed.
- [ ] Current enrolled credits computed separately.
- [ ] Current course count accurate.
- [ ] Student study year returned.
- [ ] Major/Program names mapped correctly.
- [ ] Next Class calculated from normalized meetings.
- [ ] Upcoming deadlines returned.
- [ ] Home Summary API implemented.
- [ ] Profile backward compatibility preserved.
- [ ] My Courses API supports multiple meeting sessions.

## 47. Security and Correctness

- [ ] Student identity comes from JWT.
- [ ] No studentId required in personal query requests.
- [ ] Student cannot access another Student's academic data.
- [ ] Grade writes restricted to authorized Admin users.
- [ ] No fabricated academic metrics.
- [ ] Missing data represented accurately.
- [ ] No real Gemini calls required.
- [ ] No RAG changes.
- [ ] No production data migration required.
- [ ] Unit and integration tests pass.
- [ ] Postman tests pass.

---

# Part Q — Agent Completion Report

After each implementation phase, report:

```text
1. Existing schema inspected

2. Existing endpoints reused

3. New migration files

4. Actual Flyway versions

5. New database entities and relationships

6. New Admin endpoints

7. New Student endpoints

8. CGPA calculation rules

9. Earned-credit calculation rules

10. Schedule/next-class algorithm

11. Deadline filtering behavior

12. Profile and My Courses compatibility

13. Security changes

14. Automated tests

15. Postman verification

16. Maven build result

17. Missing official policies/data

18. Remaining limitations
```

Do not claim implementation completed without inspecting the actual repository, running the migration and verifying the endpoints.

---

# Final Goal

The Student Mobile application should display accurate backend information instead of hard-coded Figma values.

```text
Flutter Mobile
      ↓
Student JWT
      ↓
Spring Boot REST API
      ↓
Academic Services
      ↓
PostgreSQL
      ↓
Verified Student Data
      ↓
Flutter Home / Profile / My Courses
```

This phase should be completed before the final mobile integration tests.

**Step 14 — RAG and Vector Database remains a separate future implementation.**