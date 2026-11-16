# 43 — Timetable Management: Normalized Flyway Migration and Complete Backend API Implementation

**Project:** Smart AI University Student Assistant  
**Module:** Timetable Management  
**Backend:** Java 21, Spring Boot 4.x, Maven  
**Database:** PostgreSQL 18 — `smart_university_db`  
**Authentication:** Spring Security, RS256 JWT  
**Migration:** Flyway  
**Integration:** Academic Management, Campus Management, Student Mobile, AI Assistant  
**Suggested specification:** `spec-md/42-timetable-flway-and-api.md`

---

## 1. Objective

Implement a complete Timetable Management backend that supports the Admin Web Figma design and provides accurate structured class schedules for the Student Mobile App and AI Assistant.

The implementation must include:

1. Normalized Flyway database migrations.
2. Academic Term management.
3. Relationships with existing Course Sections.
4. Relationships with Campus, Buildings and Rooms.
5. Class meeting creation, update, listing and soft deletion.
6. Weekly calendar and list views.
7. Semester, academic-year, faculty, department and course filters.
8. Room, Lecturer and Course Section conflict detection.
9. Schedule publishing and cancellation.
10. Student timetable integration.
11. Next Class calculation support.
12. PostgreSQL integration tests and Postman verification.

**Important:** Reuse the existing Course Sections, Lecturers, Campus Buildings and Rooms. Do not create a second set of academic entities.

---

## 2. Figma Design Reference

File:

`https://www.figma.com/design/FQhq9uVri66RJpADxECMtm/Smart-Ai-Admin-Web`

Screen:

```text
Node: 34:3999
Name: Timetable Screen
```

### Visible Timetable Features

The design contains:

```text
Class Timetable
│
├── Calendar View
├── List View
├── Add Class Schedule
│
├── Schedule Conflicts
│
├── Filters
│   ├── Semester
│   ├── Academic Year
│   ├── Faculty
│   ├── Department
│   └── Course
│
├── Faculty Color Legend
│
└── Weekly Calendar
    ├── Monday
    ├── Tuesday
    ├── Wednesday
    ├── Thursday
    ├── Friday
    └── Saturday
```

The visible calendar displays hourly rows from 08:00 through 17:00.

Do not restrict backend scheduling to those hours. The backend must also support Sunday and valid schedules outside the currently visible grid.

Each course card displays:

```text
Course Code
Course Name
Section Number
Room
Lecturer
```

Example design display:

```text
CS301
Data Structures & Algorithms
SEC-01 · IT-301
Wichai Saengsuwan
```

These are design examples only.

**Figma does not expose the complete Add Class Schedule form in the inspected screen.** The form contract below is a proposed backend implementation based on the existing entities and timetable requirements.

---

# PART A — MANDATORY IMPLEMENTATION RULES

## 3. Agent Instructions

Before modifying backend code:

1. Read `AGENTS.md`.
2. Read every relevant specification in `spec-md/`.
3. Inspect current Flyway migration history.
4. Inspect the existing PostgreSQL schema.
5. Inspect `semesters`.
6. Inspect `courses`.
7. Inspect `course_sections`.
8. Inspect `lectures`.
9. Inspect `enrollments`.
10. Inspect existing Campus Management migrations and entities.
11. Inspect `campus_sites`, `campus_buildings` and `campus_rooms`.
12. Search for an existing `course_section_meetings` table.
13. Search for any existing Timetable service, entity and controller.
14. Inspect Student Academic Schedule and Next Class services.
15. Inspect the current Security configuration and response wrapper.
16. Determine the next available Flyway version.

Do not create duplicate tables.

Do not modify already-applied Flyway migrations.

Do not modify unrelated backend modules without a required compatibility reason.

Do not change existing Admin or Student JWT behavior.

Do not create demonstration university data in production migrations.

---

## 4. Existing Entities to Reuse

The existing architecture includes:

```text
faculties
departments
programs
semesters
lectures
courses
course_sections
students
enrollments
```

The Campus Management implementation specifies:

```text
campus_sites
campus_buildings
campus_rooms
campus_places
campus_nav_destinations
```

The Timetable feature must use these authoritative records.

Before implementation, verify that the Campus Management migrations have actually been applied. If they have not, complete the Campus migrations first.

---

# PART B — NORMALIZED DATABASE DESIGN

## 5. Recommended Final Relationships

```text
SEMESTERS
    │
    └── ACADEMIC_TERMS
             │
             └── COURSE_SECTIONS
                      │
                      ├── COURSES
                      │     └── DEPARTMENTS
                      │           └── FACULTIES
                      │
                      ├── LECTURES
                      │
                      ├── ENROLLMENTS
                      │     └── STUDENTS
                      │
                      └── COURSE_SECTION_MEETINGS
                                   │
                                   ├── CAMPUS_ROOMS
                                   │       └── CAMPUS_BUILDINGS
                                   │               └── CAMPUS_SITES
                                   │
                                   └── TIMETABLE_MEETING_EXCEPTIONS
```

This is the intended normalized structure.

A Timetable meeting stores the Course Section and Room references.

It does not duplicate Faculty, Department, Course, Lecturer, Building or Campus names.

---

## 6. Core Normalization Rules

### Course Information

Derive:

```text
Meeting
   ↓
Course Section
   ↓
Course
```

Do not store:

```text
course_code
course_name
credit_hours
```

inside the timetable table.

### Academic Organization

Derive:

```text
Meeting
   ↓
Course Section
   ↓
Course
   ↓
Department
   ↓
Faculty
```

Do not duplicate:

```text
faculty_id
department_id
program_id
```

in the meeting record.

### Lecturer

Existing Course Sections already assign a Lecturer.

Derive:

```text
Meeting
   ↓
Course Section
   ↓
Lecture
```

Do not add `lecture_id` to the meeting table when every meeting uses the Course Section's assigned Lecturer.

If the university later requires different Lecturers for lecture and lab sessions, design a separate assignment relationship. Do not introduce an inconsistent duplicate Lecturer field now.

### Physical Location

Derive:

```text
Meeting
   ↓
Room
   ↓
Building
   ↓
Campus
```

Do not store `campus_id` and `building_id` redundantly on every timetable meeting.

### Academic Term

Derive the Semester and Academic Year from the Course Section's assigned Academic Term.

Do not repeat these values in every meeting.

---

# PART C — ACADEMIC TERMS

## 7. Why Academic Terms Are Needed

The Figma Timetable contains:

```text
1/2568
2025–2026
```

The existing `semesters` table holds Semester names, not an independent year-specific offering.

For repeated yearly scheduling, introduce `academic_terms`.

Example:

```text
Semester:
Semester 1

Academic Year Code:
2568

Display Label:
2025–2026

Academic Term:
Semester 1 / 2568
```

Do not automatically convert Buddhist Era and Gregorian year values without an approved institutional calendar policy.

---

## 8. Create academic_terms

```sql
CREATE TABLE academic_terms (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    semester_id BIGINT NOT NULL
        REFERENCES semesters(id),

    academic_year_code VARCHAR(20) NOT NULL,
    academic_year_label VARCHAR(30) NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT uk_academic_terms_semester_year
        UNIQUE (semester_id, academic_year_code),

    CONSTRAINT chk_academic_terms_dates
        CHECK (end_date >= start_date),

    CONSTRAINT chk_academic_terms_status
        CHECK (
            status IN (
                'PLANNED',
                'ACTIVE',
                'CLOSED'
            )
        )
);

CREATE INDEX idx_academic_terms_status
ON academic_terms(status)
WHERE is_deleted = FALSE;
```

### Business Rules

- A Term references one Semester.
- One Semester can have multiple year-specific Terms.
- Dates must be valid.
- Existing academic years are not changed by the Timetable implementation.
- Closed Terms remain available for historical viewing.
- Do not assume only one Term can exist for a year.
- Do not auto-create Terms using invented dates.

The Term list should be managed through authorized Admin APIs.

---

# PART D — COURSE SECTION INTEGRATION

## 9. Existing Course Sections

The existing structure includes:

```text
course_sections
├── id
├── course_id
├── section_number
├── capacity
├── lecture_id
├── room
├── schedule
├── semester_id
├── status
├── is_deleted
└── audit fields
```

The new Timetable must not create another Course Section table.

Instead, extend the existing structure safely.

---

## 10. Add Academic Term to Course Sections

If missing:

```sql
ALTER TABLE course_sections
ADD COLUMN academic_term_id BIGINT;

ALTER TABLE course_sections
ADD CONSTRAINT fk_course_sections_academic_term
FOREIGN KEY (academic_term_id)
REFERENCES academic_terms(id);

CREATE INDEX idx_course_sections_academic_term
ON course_sections(academic_term_id);
```

Do not immediately mark this FK as `NOT NULL` if existing rows have no verified Academic Term.

### Important Migration Process

1. Add nullable FK.
2. Create real Academic Terms through Admin-approved records.
3. Map existing Course Sections to their verified Terms.
4. Validate existing enrollment relationships.
5. Make new Course Section writes require `academicTermId`.
6. When all existing records are reconciled, add the NOT NULL constraint in a later migration.

Do not backfill arbitrary Academic Years.

---

## 11. Course Section Uniqueness

The previous section uniqueness may be:

```text
(course_id, section_number, semester_id)
```

That prevents reusing the same section identifier in the same Semester across different Academic Years.

The normalized target is:

```text
(course_id, section_number, academic_term_id)
```

After all Course Sections have valid `academic_term_id` values:

```sql
ALTER TABLE course_sections
ADD CONSTRAINT uk_course_sections_course_section_term
UNIQUE (
    course_id,
    section_number,
    academic_term_id
);
```

Then remove the previous Semester-based uniqueness constraint only after verifying its actual name and ensuring the migration is safe.

The target long-term design derives Semester from Academic Term.

Do not retain duplicated Semester data as competing sources of truth.

Deprecate the legacy `semester_id` column through a separate verified compatibility migration when all services have transitioned.

Keep existing Section IDs stable so Enrollment foreign keys are not broken.

---

# PART E — CLASS MEETINGS

## 12. Timetable Source of Truth

Use:

```text
course_section_meetings
```

This table may already exist from the earlier Student Schedule/Next Class implementation.

**If it exists, extend it through ALTER migrations. Do not recreate it.**

If it does not exist, use the baseline schema below.

---

## 13. course_section_meetings Fields

| Column | Type | Purpose |
|---|---|---|
| id | BIGINT PK | Meeting ID |
| course_section_id | BIGINT FK | Existing Course Section |
| room_id | BIGINT FK | Existing Campus Room |
| meeting_type | VARCHAR(30) | Lecture/Lab/Tutorial |
| day_of_week | SMALLINT | Weekday |
| start_time | TIME | Meeting start |
| end_time | TIME | Meeting end |
| effective_from | DATE | Start date |
| effective_to | DATE | End date |
| status | VARCHAR(20) | DRAFT/PUBLISHED/CANCELLED |
| notes | TEXT | Optional notes |
| is_deleted | BOOLEAN | Soft deletion |
| created_by | BIGINT FK | Admin |
| created_at | TIMESTAMP | Created |
| updated_by | BIGINT FK | Admin |
| updated_at | TIMESTAMP | Updated |

Do not store:

```text
faculty_id
department_id
course_id
lecturer_name
building_name
campus_name
academic_year
semester_name
```

on the meeting table.

---

## 14. Baseline Flyway SQL

Use this only when the table does not already exist.

```sql
CREATE TABLE course_section_meetings (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    course_section_id BIGINT NOT NULL
        REFERENCES course_sections(id),

    room_id BIGINT
        REFERENCES campus_rooms(id),

    meeting_type VARCHAR(30) NOT NULL,

    day_of_week SMALLINT NOT NULL,

    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    effective_from DATE NOT NULL,
    effective_to DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    notes TEXT,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT chk_meeting_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT chk_meeting_time
        CHECK (start_time < end_time),

    CONSTRAINT chk_meeting_effective_dates
        CHECK (effective_to >= effective_from),

    CONSTRAINT chk_meeting_type
        CHECK (
            meeting_type IN (
                'LECTURE',
                'LAB',
                'TUTORIAL'
            )
        ),

    CONSTRAINT chk_meeting_status
        CHECK (
            status IN (
                'DRAFT',
                'PUBLISHED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_published_meeting_room
        CHECK (
            status <> 'PUBLISHED'
            OR room_id IS NOT NULL
        )
);

CREATE INDEX idx_meeting_section
ON course_section_meetings(course_section_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_meeting_room_day_time
ON course_section_meetings(
    room_id,
    day_of_week,
    start_time,
    end_time
)
WHERE is_deleted = FALSE
  AND status = 'PUBLISHED';

CREATE INDEX idx_meeting_day_time
ON course_section_meetings(
    day_of_week,
    start_time
)
WHERE is_deleted = FALSE
  AND status = 'PUBLISHED';
```

The initial design supports same-day meetings only.

If overnight sessions are officially required, extend the date/time model before enabling them.

---

## 15. Weekday Rules

```text
1 = Monday
2 = Tuesday
3 = Wednesday
4 = Thursday
5 = Friday
6 = Saturday
7 = Sunday
```

Use Java:

```java
java.time.DayOfWeek
```

Convert consistently between API values and PostgreSQL.

Do not store:

```text
"Monday 09:00-11:45"
```

as the source of truth.

---

## 16. Multiple Meetings Per Course Section

One Course Section can have several recurring meetings.

Example:

```text
Course:
CSC-452 Mobile Programming

Section:
02

Meeting 1:
Tuesday 09:00–11:45
Room IT-301
LECTURE

Meeting 2:
Thursday 14:00–16:00
Room IT-LAB-01
LAB
```

Database:

```text
course_section_id = 81

Meeting A
meeting_type = LECTURE
day_of_week = 2

Meeting B
meeting_type = LAB
day_of_week = 4
```

The second meeting must not create a second Course Section or Enrollment.

---

# PART F — CAMPUS, BUILDING AND ROOM RELATIONSHIPS

## 17. Physical Location Model

Use:

```text
campus_sites
    ↓
campus_buildings
    ↓
campus_rooms
    ↑
course_section_meetings.room_id
```

The Room lookup should display:

```text
Campus
Building
Floor
Room Number
Room Name
Capacity
Status
```

These display values come from the existing Campus entities.

---

## 18. Why Not Store building_id on Timetable?

Incorrect:

```text
course_section_meetings
├── campus_id
├── building_id
├── room_id
└── room_name
```

This creates duplicated information.

For example, one meeting could accidentally reference:

```text
building_id = 1
room_id = 85
```

while Room 85 actually belongs to Building 2.

Correct:

```text
course_section_meetings.room_id
    ↓
campus_rooms.building_id
    ↓
campus_buildings.site_id
```

The API may return all three identifiers and display names, but only the authoritative Room FK is persisted.

---

## 19. Room Validation

Before publishing a meeting:

```text
Room exists
Room is not deleted
Building exists
Building is not deleted
Campus exists
Campus is active
Room is usable for scheduling
Building is not under renovation
Room capacity is suitable, if required by policy
```

Do not confuse the Room's manually maintained `AVAILABLE` status with real-time booking availability.

Actual timetable occupancy must be calculated from published meetings.

---

# PART G — SCHEDULE EXCEPTIONS

## 20. Need for Exceptions

Weekly recurring meetings alone cannot represent:

```text
Cancelled class
Temporary reschedule
One-day room change
Special make-up class
```

Create a normalized exception table rather than changing the entire weekly schedule for one date.

---

## 21. timetable_meeting_exceptions

```sql
CREATE TABLE timetable_meeting_exceptions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    meeting_id BIGINT NOT NULL
        REFERENCES course_section_meetings(id),

    original_date DATE NOT NULL,

    exception_type VARCHAR(20) NOT NULL,

    replacement_date DATE,
    replacement_start_time TIME,
    replacement_end_time TIME,

    replacement_room_id BIGINT
        REFERENCES campus_rooms(id),

    reason VARCHAR(500),

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT REFERENCES app_users(id),
    updated_at TIMESTAMP,

    CONSTRAINT chk_timetable_exception_type
        CHECK (
            exception_type IN (
                'CANCELLED',
                'RESCHEDULED'
            )
        ),

    CONSTRAINT chk_timetable_exception_replacement
        CHECK (
            (
                exception_type = 'CANCELLED'
                AND replacement_date IS NULL
                AND replacement_start_time IS NULL
                AND replacement_end_time IS NULL
                AND replacement_room_id IS NULL
            )
            OR
            (
                exception_type = 'RESCHEDULED'
                AND replacement_date IS NOT NULL
                AND replacement_start_time IS NOT NULL
                AND replacement_end_time IS NOT NULL
                AND replacement_end_time >
                    replacement_start_time
            )
        )
);

CREATE UNIQUE INDEX uk_timetable_exception_original_occurrence
ON timetable_meeting_exceptions(
    meeting_id,
    original_date
)
WHERE is_deleted = FALSE;
```

Rules:

- `original_date` must be a valid occurrence of the referenced recurring meeting.
- A cancellation removes only that occurrence.
- A reschedule replaces the original occurrence rather than creating two simultaneous classes.
- If `replacement_room_id` is null for a reschedule, reuse the original meeting Room.
- Replacement dates and times must pass conflict validation.
- Same-day replacement times must end after they start.
- Do not allow arbitrary rescheduling outside the approved academic term without Admin policy.

For entirely new one-off make-up sessions, implement an explicit additional occurrence model if the university needs it. Do not misuse a replacement of a nonexistent occurrence.

---

# PART H — FLYWAY EXECUTION PLAN

## 22. Required Migration Sequence

Inspect existing versions first.

Recommended conceptual files:

```text
src/main/resources/db/migration/

V{next}__create_academic_terms.sql

V{next+1}__link_course_sections_to_academic_terms.sql

V{next+2}__create_or_extend_course_section_meetings.sql

V{next+3}__create_timetable_meeting_exceptions.sql

V{next+4}__add_timetable_indexes.sql
```

These are placeholders, not actual Flyway numbers.

Do not include mutually exclusive `CREATE TABLE` and `ALTER TABLE` variants in the same migration.

The agent must inspect the real schema and choose the correct approach.

If Campus Rooms do not yet exist, apply the Campus Management migrations first.

---

## 23. Data Migration Rules

Existing legacy schedule fields:

```text
course_sections.schedule
course_sections.room
```

may contain free text.

Example:

```text
Mon/Tues 02:00-4:00
```

Do not silently convert ambiguous text into official meeting rows.

Migration workflow:

```text
Existing Section
      ↓
Identify verified Academic Term
      ↓
Identify verified Campus Room
      ↓
Identify meeting weekday/time
      ↓
Create normalized meeting record
      ↓
Validate conflicts
      ↓
Publish
```

Keep legacy columns temporarily for compatibility.

After migration, the normalized meeting table becomes authoritative for new timetable functionality.

Do not maintain two independently editable schedule sources.

If legacy values remain during the transition, label them legacy/unverified until reconciled.

---

# PART I — BACKEND ARCHITECTURE

## 24. Package Structure

Follow current project conventions.

Recommended:

```text
feature/
└── timetable/
    ├── controller/
    │   ├── admin/
    │   │   ├── AdminTimetableController.java
    │   │   └── AdminAcademicTermController.java
    │   │
    │   └── student/
    │       └── StudentTimetableController.java
    │
    ├── dto/
    │   ├── request/
    │   └── response/
    │
    ├── entity/
    │   ├── AcademicTerm.java
    │   ├── CourseSectionMeeting.java
    │   └── TimetableMeetingException.java
    │
    ├── repository/
    │   ├── AcademicTermRepository.java
    │   ├── CourseSectionMeetingRepository.java
    │   └── TimetableMeetingExceptionRepository.java
    │
    ├── service/
    │   ├── AcademicTermService.java
    │   ├── TimetableService.java
    │   ├── TimetableConflictService.java
    │   ├── StudentTimetableService.java
    │   └── impl/
    │
    ├── mapper/
    │   └── TimetableMapper.java
    │
    └── exception/
```

If meeting entities already exist under the Course Section module, reuse their service/repository and avoid creating competing JPA entity mappings for the same table.

---

# PART J — ADMIN ACADEMIC TERM APIs

## 25. Endpoint Contract

Base:

```http
/api/v1/admin/academic-terms
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/academic-terms` | List/filter |
| GET | `/academic-terms/{id}` | Detail |
| POST | `/academic-terms` | Create |
| PUT | `/academic-terms/{id}` | Update |
| PATCH | `/academic-terms/{id}/status` | Status |
| DELETE | `/academic-terms/{id}` | Soft delete |

Do not permit deletion of Terms referenced by existing Course Sections without an approved dependency policy.

---

## 26. Create Academic Term

```http
POST /api/v1/admin/academic-terms
```

```json
{
  "semesterId": 1,
  "academicYearCode": "2568",
  "academicYearLabel": "2025–2026",
  "startDate": "2025-08-01",
  "endDate": "2025-12-15",
  "status": "PLANNED"
}
```

Example values are illustrative.

Validation:

```text
Semester exists
Semester not deleted
Year code required
Display label required
Valid start/end dates
Unique Semester + Academic Year Code
Valid status
```

A Term's calendar dates must be verified, not generated from Figma.

---

# PART K — ADMIN TIMETABLE CRUD

## 27. Timetable API Base

```http
/api/v1/admin/timetable
```

Recommended endpoint contract:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/calendar` | Weekly grid |
| GET | `/meetings` | Paginated list |
| GET | `/meetings/{id}` | Meeting details |
| POST | `/meetings` | Add Class Schedule |
| PUT | `/meetings/{id}` | Update |
| DELETE | `/meetings/{id}` | Soft delete |
| POST | `/meetings/{id}/publish` | Publish |
| POST | `/meetings/{id}/cancel` | Cancel series |
| GET | `/conflicts` | Conflict list |
| POST | `/conflicts/check` | Pre-save check |
| GET | `/statistics` | Totals |
| GET | `/filter-options` | Dropdown data |
| GET | `/meetings/{id}/exceptions` | Exceptions |
| POST | `/meetings/{id}/exceptions` | Add exception |
| PUT | `/meetings/{id}/exceptions/{exceptionId}` | Update exception |
| DELETE | `/meetings/{id}/exceptions/{exceptionId}` | Remove exception |

Do not expose Entity objects through controllers.

---

## 28. Add Class Schedule

```http
POST /api/v1/admin/timetable/meetings
```

Proposed request:

```json
{
  "courseSectionId": 81,
  "roomId": 15,
  "meetingType": "LECTURE",
  "dayOfWeek": 2,
  "startTime": "09:00",
  "endTime": "11:45",
  "effectiveFrom": "2026-08-01",
  "effectiveTo": "2026-12-15",
  "notes": null
}
```

The backend resolves:

```text
Course
Section
Lecturer
Academic Term
Faculty
Department
Room
Building
Campus
```

from the supplied foreign keys.

Do not accept redundant identifiers such as:

```text
courseId
facultyId
departmentId
lecturerId
buildingId
campusId
```

unless a genuinely independent business relationship requires them.

The initial meeting must be created with:

```text
status = DRAFT
```

Publishing is a separate verified operation.

---

## 29. Add Schedule Validation

Required:

```text
Course Section exists
Course Section not deleted
Course Section has valid Academic Term
Course exists and is active
Lecturer assignment valid
Room exists
Room belongs to active Building and Campus
Meeting type valid
Weekday valid
startTime < endTime
effectiveFrom <= effectiveTo
Meeting dates within authorized Term dates
```

For a published meeting, also require:

```text
Course Section eligible for publishing
Room assigned
No unresolved blocking conflicts
```

The actual business rules should be applied in the Service layer.

---

## 30. Add Multiple Weekly Meetings

A Course Section commonly has more than one weekly meeting.

The backend should optionally support:

```http
POST /api/v1/admin/timetable/meetings/bulk
```

Example:

```json
{
  "courseSectionId": 81,
  "meetings": [
    {
      "meetingType": "LECTURE",
      "roomId": 15,
      "dayOfWeek": 2,
      "startTime": "09:00",
      "endTime": "11:45",
      "effectiveFrom": "2026-08-01",
      "effectiveTo": "2026-12-15"
    },
    {
      "meetingType": "LAB",
      "roomId": 18,
      "dayOfWeek": 4,
      "startTime": "14:00",
      "endTime": "16:00",
      "effectiveFrom": "2026-08-01",
      "effectiveTo": "2026-12-15"
    }
  ]
}
```

Execute atomically.

If any meeting fails validation, roll back the batch.

---

## 31. Get Meeting Detail

```http
GET /api/v1/admin/timetable/meetings/{id}
```

Response example:

```json
{
  "id": 1001,
  "courseSection": {
    "id": 81,
    "sectionNumber": "02"
  },
  "course": {
    "id": 25,
    "courseCode": "CSC-452",
    "courseName": "Mobile Programming"
  },
  "academicTerm": {
    "id": 3,
    "semesterName": "Semester 1",
    "academicYearCode": "2568",
    "academicYearLabel": "2025–2026"
  },
  "faculty": {
    "id": 1,
    "name": "Example Faculty"
  },
  "department": {
    "id": 2,
    "name": "Computer Science"
  },
  "lecturer": {
    "id": 10,
    "name": "Example Lecturer"
  },
  "location": {
    "campusId": 1,
    "buildingId": 3,
    "buildingName": "Example Academic Building",
    "roomId": 15,
    "roomNumber": "IT-301",
    "floorNumber": 3
  },
  "meetingType": "LECTURE",
  "dayOfWeek": 2,
  "startTime": "09:00",
  "endTime": "11:45",
  "effectiveFrom": "2026-08-01",
  "effectiveTo": "2026-12-15",
  "status": "DRAFT"
}
```

The sample must not be treated as an internally consistent official university academic-term record; its values illustrate response fields only.

Return the existing project's standard response wrapper.

---

## 32. Update Meeting

```http
PUT /api/v1/admin/timetable/meetings/{id}
```

Allow authorized updates to:

```text
roomId
meetingType
dayOfWeek
startTime
endTime
effectiveFrom
effectiveTo
notes
```

If moving the meeting to another Course Section, prefer an explicit administrative reassignment workflow instead of silently changing its academic ownership.

For PUBLISHED meetings:

1. Lock the relevant scheduling resources.
2. Validate the replacement schedule.
3. Recheck conflicts.
4. Apply the update atomically.
5. Audit the change.
6. Make the new timetable available to Students.

Do not leave the original meeting in an inconsistent partially updated state.

---

## 33. Delete Meeting

```http
DELETE /api/v1/admin/timetable/meetings/{id}
```

Use:

```text
is_deleted = TRUE
```

Do not physically delete published meeting history.

A deletion affecting Student timetables should have an audit record.

If an existing meeting has dependent exception records, preserve referential integrity.

---

## 34. Publish Meeting

```http
POST /api/v1/admin/timetable/meetings/{id}/publish
```

Workflow:

```text
Load Meeting
     ↓
Validate Course Section
     ↓
Validate Academic Term
     ↓
Validate Lecturer
     ↓
Validate Room / Building
     ↓
Check Room Conflicts
     ↓
Check Lecturer Conflicts
     ↓
Check Section Conflicts
     ↓
Acquire Required Locks / Revalidate
     ↓
Publish
```

Only published meetings appear in the ordinary Student timetable.

---

## 35. Cancel Meeting Series

```http
POST /api/v1/admin/timetable/meetings/{id}/cancel
```

Set:

```text
status = CANCELLED
```

A series cancellation is different from a one-day cancellation.

For a single day, use:

```text
timetable_meeting_exceptions
```

Do not use series cancellation to represent a temporary absence.

---

# PART L — TIMETABLE FILTERS AND VIEWS

## 36. Calendar View

```http
GET /api/v1/admin/timetable/calendar
```

Supported filters:

```text
academicTermId
facultyId
departmentId
courseId
courseSectionId
lecturerId
campusId
buildingId
roomId
weekStart
status
```

The backend should derive Faculty and Department via Course relationships.

Campus and Building filters should resolve through Room relationships.

`weekStart` should represent a valid Monday-aligned local calendar date, or be normalized explicitly and consistently.

Return only meetings relevant to the requested week and effective date intervals, including the effect of cancellations/reschedules.

---

## 37. Calendar Response

Example:

```json
{
  "academicTermId": 3,
  "weekStart": "2026-11-09",
  "weekEnd": "2026-11-15",
  "timezone": "Asia/Bangkok",
  "events": [
    {
      "meetingId": 1001,
      "courseSectionId": 81,
      "courseCode": "CSC-452",
      "courseName": "Mobile Programming",
      "sectionNumber": "02",
      "lecturerName": "Example Lecturer",
      "facultyId": 1,
      "departmentId": 2,
      "roomId": 15,
      "roomNumber": "IT-301",
      "buildingId": 3,
      "campusId": 1,
      "meetingType": "LECTURE",
      "startsAt": "2026-11-10T09:00:00+07:00",
      "endsAt": "2026-11-10T11:45:00+07:00",
      "status": "PUBLISHED"
    }
  ]
}
```

Generate actual event occurrences for the week instead of returning only timeless weekday rules when the API promises a calendar.

Do not generate events on dates outside an applicable meeting's effective interval.

---

## 38. List View

```http
GET /api/v1/admin/timetable/meetings
```

Support:

```text
page
size
search
sort
academicTermId
facultyId
departmentId
courseId
courseSectionId
lecturerId
campusId
buildingId
roomId
dayOfWeek
meetingType
status
```

Recommended list columns:

```text
Course Code
Course Name
Section
Faculty
Department
Lecturer
Day
Start Time
End Time
Campus
Building
Room
Meeting Type
Status
Actions
```

Use pagination and bounded sorting.

Avoid N+1 queries through projections, fetch joins or equivalent repository strategies.

---

## 39. Filter Options

```http
GET /api/v1/admin/timetable/filter-options
```

Return available:

```text
Academic Terms
Faculties
Departments
Courses
Course Sections
Lecturers
Campuses
Buildings
Rooms
Meeting Types
Statuses
```

Support cascading filters:

```text
Faculty → Department → Course → Course Section

Campus → Building → Room
```

Where existing Admin lookup APIs already provide these lists, reuse them instead of duplicating the same lookup logic.

---

# PART M — SCHEDULE CONFLICT DETECTION

## 40. Conflict Types

Required initial conflict types:

```text
ROOM_CONFLICT
LECTURER_CONFLICT
COURSE_SECTION_CONFLICT
```

Optional after reliable Student Enrollment validation:

```text
STUDENT_CONFLICT
```

The conflict counter in Figma should be derived from current scheduling records.

Do not hard-code the displayed count of four.

---

## 41. Time Overlap Rule

Two meetings overlap when:

```text
Meeting A start < Meeting B end

AND

Meeting A end > Meeting B start
```

For example:

```text
Meeting A: 09:00–11:00
Meeting B: 10:30–12:00

Conflict = TRUE
```

But:

```text
Meeting A: 09:00–11:00
Meeting B: 11:00–12:00

Conflict = FALSE
```

Use half-open time intervals.

Recurring meetings also require:

```text
Same weekday
Overlapping effective dates
Actual overlapping occurrences
```

Where Academic Terms overlap in calendar dates, do not ignore cross-term conflicts.

Exceptions must be considered when checking actual occurrence conflicts.

---

## 42. Room Conflict

A Room cannot host two conflicting published meetings.

Example:

```text
Course A
Monday 09:00–11:00
Room IT-301

Course B
Monday 10:00–12:00
Room IT-301
```

Result:

```text
ROOM_CONFLICT
```

Compare `room_id`, not a free-text room label.

---

## 43. Lecturer Conflict

A Lecturer cannot be scheduled to teach conflicting meetings at the same time.

Example:

```text
Lecturer ID = 10

CS301
Monday 09:00–11:00

CS315
Monday 10:00–12:00
```

Result:

```text
LECTURER_CONFLICT
```

Resolve the Lecturer from the existing Course Section.

---

## 44. Course Section Conflict

A Course Section must not have mutually overlapping meetings.

Example:

```text
Section 01

Lecture:
Tuesday 09:00–11:00

Lab:
Tuesday 10:00–12:00
```

Result:

```text
COURSE_SECTION_CONFLICT
```

This prevents the same group from being scheduled for incompatible simultaneous sessions.

---

## 45. Student Conflict

If Student Enrollment data is available, the system can optionally check:

```text
One Student
   ↓
Two ACTIVE Course Enrollments
   ↓
Overlapping Published Meetings
```

Important:

- Use the current authenticated/authorized academic data.
- Do not expose individual Student schedules in ordinary Admin conflict summaries unless authorized.
- Ignore terminal enrollment states according to the existing enrollment policy.
- Enrollment changes must also be able to detect conflicts with published schedules.

Student-conflict checks may be implemented after the three core publishing conflicts.

---

## 46. Conflict Check Endpoint

```http
POST /api/v1/admin/timetable/conflicts/check
```

Use the same validated request fields as Add/Update Meeting.

For an update, include an existing meeting identifier internally so the record does not conflict with itself.

Example response:

```json
{
  "hasConflicts": true,
  "totalConflicts": 2,
  "conflicts": [
    {
      "type": "ROOM_CONFLICT",
      "conflictingMeetingId": 1050,
      "message": "The selected room is already scheduled during this interval."
    },
    {
      "type": "LECTURER_CONFLICT",
      "conflictingMeetingId": 1080,
      "message": "The assigned lecturer has another class during this interval."
    }
  ]
}
```

The detailed conflict list must be based on real database rows.

Do not use an LLM to infer scheduling conflicts.

---

## 47. Conflict List Endpoint

```http
GET /api/v1/admin/timetable/conflicts
```

Filters:

```text
academicTermId
facultyId
departmentId
courseSectionId
buildingId
roomId
type
weekStart
```

Return:

```text
Conflict Type
Meeting A
Meeting B
Affected Time
Course Section
Room
Lecturer when relevant
```

Do not store a duplicate conflict-count field in the database.

Derive conflicts from meeting data or use a controlled cached projection with reliable invalidation.

---

## 48. Concurrency Protection

A simple service-level conflict query is not sufficient under simultaneous Admin requests.

Two requests could both see an available Room and publish overlapping meetings.

Implement transactional concurrency protection.

Recommended:

1. Begin transaction.
2. Acquire database locks on relevant scheduling resources.
3. Use a consistent lock order for Course Section, Lecturer and Room.
4. Query conflicts after the locks are acquired.
5. Reject conflicting published schedules.
6. Commit the meeting change.

When changing Rooms or Lecturers, lock both old and new affected resources using a deterministic order.

Use PostgreSQL-backed integration tests to verify concurrent publishing attempts cannot produce overlapping schedules.

If adopting PostgreSQL exclusion constraints or advisory locks, document the exact implementation and ensure it covers the recurrence/date-range semantics correctly.

Do not rely on a frontend conflict preview as the final authorization to publish.

---

# PART N — TIMETABLE STATISTICS

## 49. Statistics Endpoint

```http
GET /api/v1/admin/timetable/statistics
```

Example:

```json
{
  "totalMeetings": 120,
  "publishedMeetings": 108,
  "draftMeetings": 8,
  "cancelledMeetings": 4,
  "conflictCount": 2
}
```

These are illustrative numbers.

Apply active/deleted and Academic Term filters consistently.

Keep derived totals out of the meeting tables.

---

# PART O — STUDENT MOBILE INTEGRATION

## 50. Existing Student Schedule API

Preserve:

```http
GET /api/v1/student/academic/schedule
```

Extend the existing Academic Query Service to read normalized published meeting records.

Do not break the current response without an explicit compatibility plan.

Student identity must come from:

```text
SecurityContext
     ↓
CurrentStudentProvider
```

Never accept a `studentId` from the Mobile App for personal timetable queries.

---

## 51. Student Schedule Workflow

```text
Student JWT
     ↓
Current Student
     ↓
ACTIVE Enrollments
     ↓
Course Sections
     ↓
Academic Terms
     ↓
Published Course Meetings
     ↓
Campus Rooms
     ↓
Campus Buildings
     ↓
Structured Schedule Response
```

Only return the Student's eligible enrolled sections.

Do not return another Student's private timetable.

---

## 52. Student Schedule Example

```json
{
  "courseCode": "CSC-452",
  "courseName": "Mobile Programming",
  "sectionNumber": "02",
  "lecturerName": "Example Lecturer",
  "meetings": [
    {
      "meetingId": 1001,
      "meetingType": "LECTURE",
      "dayOfWeek": 2,
      "startTime": "09:00",
      "endTime": "11:45",
      "roomId": 15,
      "roomNumber": "IT-301",
      "buildingId": 3,
      "buildingName": "Example Academic Building",
      "campusId": 1
    }
  ]
}
```

Use authoritative backend data for the Room and Building.

When a Room has a verified Campus navigation destination, expose the appropriate location reference through the existing Campus service.

---

# PART P — NEXT CLASS SUPPORT

## 53. Next Class Service

The earlier mobile specification introduced:

```http
GET /api/v1/student/academic/next-class
```

The Timetable should become its official scheduling source.

Algorithm:

```text
Authenticated Student
     ↓
Current Eligible Enrollments
     ↓
Published Meetings
     ↓
Academic Term Dates
     ↓
Generate Upcoming Occurrences
     ↓
Apply Cancellations / Reschedules
     ↓
Exclude Past Occurrences
     ↓
Select Earliest Future Class
     ↓
Return Class + Room + Building
```

Use:

```text
Asia/Bangkok
```

as the configured default university timezone.

Use date/time-aware calculations rather than free-text weekday parsing.

If no class exists, return a safe empty result.

Do not fabricate the next class.

---

# PART Q — AI ASSISTANT INTEGRATION

## 54. Existing Academic AI Tools

The AI Assistant already has the planned reusable academic query tools.

Timetable integration must allow:

```text
get_my_schedule

get_my_course_section

get_my_course_lecturer
```

to use authoritative normalized timetable information.

Optional controlled future tools:

```text
get_my_next_class

get_course_section_schedule

get_class_location
```

Example:

```text
Student:
"What class do I have tomorrow?"

Backend:
Resolve Student
Find next day's class occurrences
Return actual enrolled class details

Gemini:
Generate natural-language answer using tool data
```

For:

```text
"Take me to my next class."
```

the backend should:

```text
Find Next Class
     ↓
Resolve Room
     ↓
Resolve Building
     ↓
Resolve Campus Navigation Destination
     ↓
Use Campus Navigation Service
```

The LLM must not invent buildings, room numbers, time intervals or navigation coordinates.

No RAG or Vector Database is required for this structured timetable implementation.

---

# PART R — SECURITY

## 55. Admin Security

Use existing Admin authorities.

Recommended:

```text
SUPER_ADMIN
→ Full timetable management

ADMIN
→ Authorized timetable management

ACADEMIC_ADMIN
→ Authorized academic scheduling
```

Require explicit authorization for:

```text
Create
Update
Publish
Cancel
Delete
```

Do not allow ordinary Student tokens to perform timetable mutations.

---

## 56. Student Security

Student timetable and next-class endpoints require:

```text
ROLE_STUDENT
```

Validate:

```text
Student ACTIVE
Student not deleted
Valid JWT
Password-change restrictions
```

Never return another Student's enrollment data.

---

# PART S — ERROR HANDLING

## 57. Recommended Error Codes

```text
ACADEMIC_TERM_NOT_FOUND

ACADEMIC_TERM_CLOSED

COURSE_SECTION_NOT_FOUND

COURSE_SECTION_INACTIVE

TIMETABLE_MEETING_NOT_FOUND

TIMETABLE_INVALID_TIME

TIMETABLE_INVALID_DATE_RANGE

TIMETABLE_INVALID_WEEKDAY

TIMETABLE_ROOM_NOT_FOUND

TIMETABLE_ROOM_UNAVAILABLE

TIMETABLE_LECTURER_NOT_FOUND

TIMETABLE_ROOM_CONFLICT

TIMETABLE_LECTURER_CONFLICT

TIMETABLE_SECTION_CONFLICT

TIMETABLE_STUDENT_CONFLICT

TIMETABLE_PUBLISH_NOT_ALLOWED

TIMETABLE_EXCEPTION_INVALID

TIMETABLE_CONCURRENT_MODIFICATION
```

Use existing project exception and response conventions.

Recommended HTTP mapping:

| Condition | HTTP |
|---|---:|
| Create successful | 201 |
| Read successful | 200 |
| Update successful | 200 |
| Delete successful | 200 or 204 |
| Invalid request | 400 |
| Missing record | 404 |
| Schedule conflict | 409 |
| Unauthorized | 401 |
| Forbidden | 403 |

---

# PART T — TESTING

## 58. Flyway Tests

Verify:

```text
[ ] academic_terms created
[ ] Valid Semester FK
[ ] Academic Year and Semester uniqueness
[ ] Academic Term date constraint
[ ] Course Section Academic Term FK
[ ] Course Section identifiers remain stable
[ ] Existing Enrollment FKs remain valid
[ ] Course Meeting table created or extended
[ ] Room FK references campus_rooms
[ ] Meeting time constraints work
[ ] Meeting date constraints work
[ ] Weekday constraints work
[ ] Exceptions constraints work
[ ] Existing migrations unchanged
[ ] Flyway succeeds
[ ] Hibernate validation succeeds
```

---

## 59. Scheduling Tests

Verify:

```text
[ ] Create draft meeting
[ ] Update meeting
[ ] Soft delete meeting
[ ] Publish valid meeting
[ ] Cancel meeting series
[ ] One-day cancellation
[ ] One-day reschedule
[ ] Multiple weekly meetings per Section
[ ] Same Room consecutive non-overlapping classes allowed
[ ] Same Room overlapping classes rejected
[ ] Same Lecturer overlapping classes rejected
[ ] Same Section overlapping classes rejected
[ ] Different Rooms allowed when no Lecturer/Section conflict
[ ] Effective-date overlap correctly evaluated
[ ] Cross-term physical conflicts detected
[ ] Concurrent publishing cannot double-book a Room
[ ] Rescheduling cannot bypass conflict checking
```

---

## 60. Calendar and Filter Tests

```text
[ ] Academic Term filter
[ ] Academic Year filter via Term
[ ] Faculty filter
[ ] Department filter
[ ] Course filter
[ ] Course Section filter
[ ] Lecturer filter
[ ] Campus filter
[ ] Building filter
[ ] Room filter
[ ] Week filter
[ ] Published/Draft filter
[ ] Calendar view correct
[ ] List view correct
[ ] Sunday supported
[ ] Exceptions applied correctly
```

---

## 61. Student API Tests

```text
[ ] Student JWT required
[ ] Student identity derived from JWT
[ ] Only enrolled Sections returned
[ ] Only published meetings returned
[ ] Correct Room
[ ] Correct Building
[ ] Correct Campus
[ ] Correct Lecturer
[ ] Multiple sessions displayed
[ ] Cancelled occurrence excluded
[ ] Rescheduled occurrence displayed correctly
[ ] No schedule returns safe empty response
[ ] Next Class calculation correct
[ ] Student isolation maintained
```

---

## 62. PostgreSQL Integration Tests

Use:

```text
JUnit
Mockito
Spring Boot Test
PostgreSQL Testcontainers
```

Prefer PostgreSQL-backed tests for actual foreign keys, migrations, locking and conflict behavior.

Do not use H2 as the only database verification.

Normal tests must not call the Gemini API.

Run:

```bash
mvn test
mvn clean package
```

---

# PART U — POSTMAN VERIFICATION

## 63. Test Order

Use development data.

First create or verify:

```text
Faculty
Department
Course
Lecturer
Semester
Academic Term
Course Section
Campus
Building
Room
```

Then test Timetable APIs.

### Create Term

```http
POST /api/v1/admin/academic-terms
```

### Create Meeting

```http
POST /api/v1/admin/timetable/meetings
```

### Check Conflict

```http
POST /api/v1/admin/timetable/conflicts/check
```

### Publish Meeting

```http
POST /api/v1/admin/timetable/meetings/{id}/publish
```

### Get Weekly Timetable

```http
GET /api/v1/admin/timetable/calendar
```

### Get List View

```http
GET /api/v1/admin/timetable/meetings
```

### Student Schedule

```http
GET /api/v1/student/academic/schedule
```

### Next Class

```http
GET /api/v1/student/academic/next-class
```

Test with valid Admin and Student JWTs.

Check that published Admin changes appear in the corresponding Student API responses.

Do not mark the feature complete from HTTP 200 alone.

Verify the actual Room, Course Section, Lecturer and date/time values against PostgreSQL.

---

# PART V — IMPLEMENTATION WORKFLOW

## 64. Required Phase Order

### Phase 1 — Repository Inspection

```text
AGENTS.md
spec-md/
Flyway migrations
Existing entities
Existing APIs
Security
```

### Phase 2 — Academic and Campus Schema Verification

Verify:

```text
semesters
courses
course_sections
lectures
campus_sites
campus_buildings
campus_rooms
```

### Phase 3 — Academic Term Migration

Create or reuse:

```text
academic_terms
```

### Phase 4 — Course Section Migration

Introduce:

```text
academic_term_id
```

with safe backfill planning.

### Phase 5 — Normalized Class Meeting Migration

Create or extend:

```text
course_section_meetings
```

### Phase 6 — Exception Migration

Create:

```text
timetable_meeting_exceptions
```

### Phase 7 — PostgreSQL Verification

Run Flyway and validate schema.

### Phase 8 — JPA Entities and Repositories

Implement only missing mappings.

### Phase 9 — Academic Term APIs

CRUD, status and validation.

### Phase 10 — Timetable CRUD APIs

Create, read, update, soft delete.

### Phase 11 — Publishing Workflow

Validation and lifecycle rules.

### Phase 12 — Conflict Detection

Room, Lecturer and Section conflicts with transaction/concurrency safety.

### Phase 13 — Calendar View

Weekly date-based events.

### Phase 14 — List View

Pagination, filters, searches and sorting.

### Phase 15 — Exceptions

One-day cancellation and rescheduling.

### Phase 16 — Campus Integration

Room → Building → Campus.

### Phase 17 — Student Schedule Integration

Use existing Student APIs.

### Phase 18 — Next Class Integration

Use actual normalized meeting occurrences.

### Phase 19 — Security Tests

Admin/Student authorization.

### Phase 20 — Automated Testing

Service, Controller and PostgreSQL integration tests.

### Phase 21 — Postman Testing

Verify Admin writes and Student reads.

### Phase 22 — Maven Build

```bash
mvn test
mvn clean package
```

### Phase 23 — Frontend Integration

Connect the existing Figma-based Admin Timetable UI after all backend endpoints pass validation.

Do not redesign the frontend as part of this backend implementation.

---

# PART W — ACCEPTANCE CRITERIA

## 65. Database

- [ ] Academic Terms normalized.
- [ ] Course Sections linked to correct Academic Terms.
- [ ] Existing Courses reused.
- [ ] Existing Lecturers reused.
- [ ] Existing Campus Sites reused.
- [ ] Existing Buildings reused.
- [ ] Existing Rooms reused.
- [ ] Meetings reference Course Sections.
- [ ] Meetings reference Rooms.
- [ ] No duplicated Faculty/Department/Building names.
- [ ] Multiple meetings per Section supported.
- [ ] Exceptions stored separately.
- [ ] Safe legacy migration implemented.
- [ ] Flyway passes.

## 66. Admin APIs

- [ ] Academic Term CRUD.
- [ ] Meeting CRUD.
- [ ] Meeting publish.
- [ ] Meeting cancellation.
- [ ] One-day exceptions.
- [ ] Calendar view.
- [ ] List view.
- [ ] Filters.
- [ ] Conflict preview.
- [ ] Conflict list.
- [ ] Statistics.
- [ ] Authorization.
- [ ] Audit support.

## 67. Student Integration

- [ ] Existing Student Schedule works.
- [ ] Only authorized Student enrollments returned.
- [ ] Correct Course and Section.
- [ ] Correct Lecturer.
- [ ] Correct Room.
- [ ] Correct Building.
- [ ] Correct Campus.
- [ ] Multiple weekly sessions supported.
- [ ] Next Class uses normalized data.
- [ ] Cancelled/rescheduled classes handled.
- [ ] AI tools can reuse structured timetable data.

## 68. Quality and Testing

- [ ] No duplicate academic entities.
- [ ] No duplicate campus entities.
- [ ] No raw schedule strings used for new scheduling decisions.
- [ ] No conflicting published meetings.
- [ ] Concurrent double-booking prevented.
- [ ] PostgreSQL tests pass.
- [ ] Postman tests pass.
- [ ] Existing endpoints remain compatible.
- [ ] `mvn test` passes.
- [ ] `mvn clean package` passes.

---

# PART X — REQUIRED AGENT COMPLETION REPORT

After implementation, report:

```text
1. Existing backend modules inspected
2. Existing schema compatibility findings
3. Files created
4. Files modified
5. Actual Flyway versions
6. Academic Term implementation
7. Course Section migration strategy
8. Timetable Meeting implementation
9. Room / Building / Campus relationships
10. Admin endpoints implemented
11. Student endpoints integrated
12. Conflict detection design
13. Concurrency protection
14. Exception handling
15. Security changes
16. PostgreSQL verification
17. Automated test results
18. Postman verification
19. Maven build results
20. Remaining legacy-data migration requirements
21. Known limitations
```

Do not claim successful migration or implementation without executing the required checks against the actual project.

---

# FINAL TARGET ARCHITECTURE

```text
                  ADMIN TIMETABLE
                         |
                         v
                 Timetable APIs
                         |
                         v
                  Meeting Service
                         |
             +-----------+-----------+
             |                       |
             v                       v
      Academic Services       Campus Services
             |                       |
             v                       v
       Course Section                Room
             |                       |
        +----+-----+                 v
        |          |               Building
      Course     Lecturer             |
        |                            v
    Department                      Campus
        |
      Faculty
             \                       /
              \                     /
               +---- PostgreSQL ----+
                         |
                         v
                Published Meetings
                         |
               +---------+---------+
               |                   |
               v                   v
       Student Timetable       Next Class
               |                   |
               +---------+---------+
                         |
                         v
                    AI Tools
                         |
                         v
                Student AI Assistant
```

**Final goal:** Build a normalized, conflict-safe Timetable Management feature in which the Admin Web controls class meetings and the Student Mobile App and AI Assistant retrieve consistent academic schedule and campus-location information from PostgreSQL.