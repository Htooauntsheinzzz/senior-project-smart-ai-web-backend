# 44 — Enrollment Limited to the Published Timetable, with a Due Date

- **Date:** 2026-11-10
- **Requested by:** Hsu (product owner)
- **Decision:** D62 in `00-changes-and-decision.md`
- **Changes:** spec files 26 (admin enrollment), 42 (timetable) and 43 (student self-enrollment)
- **Mobile remark:** mobile `spec-md/10-student-self-enrollment.md`, section 12 (DEC-026)

## 1. Goal

1. A Student or an Admin can only enroll into a Course Section that is on the **published timetable**.
2. The timetable schedule carries an **enrollment due date**. It may not be after the schedule's effective start date.
3. Enrollment is refused after the due date, and (as before) when the section is full.

## 2. Enrollment due date on the timetable

- Migration `V47__add_enrollment_due_date_to_meetings.sql`: `course_section_meetings.enrollment_due_date DATE NULL` and
  `chk_meeting_enrollment_due_date CHECK (enrollment_due_date IS NULL OR enrollment_due_date <= effective_from)`.
- New optional field `enrollmentDueDate` (`yyyy-MM-dd`) on:
  - `POST /api/v1/admin/timetable/meetings`, each item of `POST /meetings/bulk`, `PUT /meetings/{id}` (`TimetableMeetingRequest`, `TimetableMeetingSlot`)
  - `POST`, `PUT /api/v1/admin/course-sections/{id}/meetings` (the compatibility facade)
  - and in every meeting response (`TimetableMeetingResponse`, `TimetableMeetingListItem`, `CourseSectionMeetingResponse`).
- Validation: `enrollmentDueDate > effectiveFrom` is 400 `TIMETABLE_INVALID_ENROLLMENT_DUE_DATE`. The same day is allowed.
- **PUT is a full replace:** a PUT without `enrollmentDueDate` clears it, like `notes`.
- `POST /conflicts/check` ignores the field (it does not affect conflicts).
- `null` means **no deadline**. Existing meetings have none, and none is invented.

## 3. The enrollment window of a section

`EnrollmentWindowService` (`feature/enrollment/service`, impl in `service/impl`) answers one `EnrollmentWindow(scheduled, dueDate, closed)` per section:

| Field | Meaning |
|---|---|
| `scheduled` | the section has at least one live `PUBLISHED` meeting. `DRAFT` and `CANCELLED` meetings do not count |
| `dueDate` | the **earliest** non-null `enrollment_due_date` of its published meetings, or `null` |
| `closed` | today is after `dueDate`. Today is the calendar date in `app.student-academic.timezone` (Asia/Bangkok). Enrollment is still open **on** the due date |

`requireOpen(sectionId)` throws:

| Case | Error |
|---|---|
| not `scheduled` | 409 `COURSE_SECTION_NOT_IN_TIMETABLE` |
| `closed` | 409 `ENROLLMENT_DUE_DATE_PASSED` |

It runs under the Course Section row lock the enrollment workflows already hold, before the capacity check.

## 4. Where the rules apply

| Operation | Timetable + due date | Capacity (unchanged) |
|---|---|---|
| Student `POST /api/v1/student/enrollments` | yes | yes, 409 `COURSE_SECTION_FULL` |
| Admin `POST /api/v1/admin/enrollments` with `PENDING` or `ACTIVE` | yes | yes |
| Admin `PUT /api/v1/admin/enrollments/{id}` moving a pending enrollment to another section | yes | yes |
| Admin create with `WITHDRAWN` or `DROPPED` (a record that holds no seat) | no | no |
| Student lock (`PENDING` to `ACTIVE`), admin status change `PENDING` to `ACTIVE` | no: the seat was taken while enrollment was open | no |
| Withdraw, drop, delete | no | no |

Check order for a student request: section exists, section available, own duplicate, dropped, same course, timetable, due date, capacity.

## 5. Lists

### 5.1 Student: `GET /api/v1/student/enrollments/available-sections`

- Only `scheduled` sections are returned. A section without a published meeting is not listed.
- A section whose due date has passed **stays listed** with `canEnroll: false`, so the student can see why.
- New fields: `enrollmentDueDate` (date or `null`) and `enrollmentClosed` (boolean).
- New `blockedReason` value `ENROLLMENT_CLOSED`. Order, first match wins: `ALREADY_ENROLLED`, `DROPPED_BY_UNIVERSITY`, `ALREADY_ENROLLED_IN_COURSE`, `ENROLLMENT_CLOSED`, `SECTION_FULL`.

### 5.2 Admin: `GET /api/v1/admin/course-sections`

- New optional filter `scheduled=true|false` (any other value is 400 `VALIDATION_ERROR`). `true` returns only sections on the published timetable.
- Each row gains `scheduled`, `enrollmentDueDate` and `enrollmentOpen` (`scheduled` and not closed).
- The list is **not** filtered by default, because the same endpoint feeds the Course Sections page and the timetable form, which must see unscheduled sections. The admin web's enrollment section picker sends `status=ACTIVE&scheduled=true`.

## 6. Tests

- `StudentEnrollmentIntegrationTests` (11): two new cases. Unscheduled and draft-only sections are neither listed nor enrollable; the due date itself is open, the day after is closed (`ENROLLMENT_CLOSED`, `ENROLLMENT_DUE_DATE_PASSED`), a withdrawn request cannot be made again after the due date, and the earliest due date of two published meetings decides.
- `EnrollmentIntegrationTests` (11): one new case for the admin create, the `scheduled` filter, the new list fields, and a `WITHDRAWN` record after the due date.
- `TimetableIntegrationTests`: create and update refuse a due date after `effectiveFrom`, accept the same day, return the field, and the database check refuses a bad value.
- Migration pins moved from 46 to 47.

## 7. Out of scope

A due date per section or per academic term instead of per meeting, a separate opening date, an admin override after the due date, hiding sections whose classes have already ended, and timetable clash checks between a student's sections.
