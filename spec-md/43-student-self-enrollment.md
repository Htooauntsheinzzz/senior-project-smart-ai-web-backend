# 43 — Student Self-Enrollment (Mobile)

- **Date:** 2026-11-10
- **Requested by:** Hsu (product owner), for the student mobile application
- **Decision:** D59 in `00-changes-and-decision.md`
- **Mobile remark:** mobile `spec-md/10-student-self-enrollment.md` (DEC-021)
- **Lifts:** "Student Mobile Self-Enrollment" from the *Do NOT implement yet* list of spec file 26.

## 1. Goal

Let a student request course sections, lock the enrollment, cancel a request, and withdraw from an active course from the mobile app. A student request is `PENDING` (spec file 25: "Student self-enrollment → PENDING"). As in the university's registration system, the student then **locks** the enrollment and every pending request becomes `ACTIVE` automatically (section 2.4, D60); no admin approval is needed. An admin can still activate, drop or edit a single enrollment with the existing admin API.

## 2. Endpoints

Package `feature/enrollment` (it owns enrollments, `Agent.md`): `StudentEnrollmentController`, `StudentEnrollmentService`, `StudentEnrollmentServiceImpl`, DTOs `StudentEnrollRequest` and `StudentAvailableSectionResponse`. Security: the existing `/api/v1/student/**` rule (student token, full access). The student id comes only from the token.

| Method | Path | Body | Success |
|---|---|---|---|
| `GET` | `/api/v1/student/enrollments/available-sections` | — | `200` `StudentAvailableSectionResponse[]`, `Cache-Control: no-store` |
| `POST` | `/api/v1/student/enrollments` | `{"courseSectionId": <id>}` | `201` `StudentAcademicEnrollmentResponse` (`PENDING`) |
| `POST` | `/api/v1/student/enrollments/lock` | — | `200` `StudentAcademicEnrollmentResponse[]` (the rows it activated) |
| `POST` | `/api/v1/student/enrollments/{id}/lock` | — | `200` `StudentAcademicEnrollmentResponse` (`ACTIVE`); idempotent for an active row |
| `POST` | `/api/v1/student/enrollments/{id}/withdraw` | — | `200` `StudentAcademicEnrollmentResponse` (`WITHDRAWN`); idempotent |

`StudentAcademicEnrollmentResponse` is the existing row of `GET /api/v1/student/academic/enrollments` (spec file 27), so the app reads one shape.

### 2.1 Available sections

Sections of the **student's own `semester_id`** (the only verified current term, spec file 40) that are `ACTIVE`, not deleted, of a live course with `is_active = TRUE`, ordered by course code, section number, id. A student without a semester gets `[]`.

Each row: section, course, semester and lecturer fields; `capacity`, `enrolledCount` (`PENDING` + `ACTIVE`), `seatsAvailable`, `full`; the token student's own `enrollmentId` and `enrollmentStatus` in this section (or `null`); `canEnroll`; `blockedReason`; published `meetings` (spec file 42).

`blockedReason`, first match wins: `ALREADY_ENROLLED` (own `PENDING`/`ACTIVE` row here), `DROPPED_BY_UNIVERSITY` (own `DROPPED` row here), `ALREADY_ENROLLED_IN_COURSE` (seat held in another section of the same course and semester), `SECTION_FULL`, else `null`.

### 2.2 Enroll

Same locking and rules as the admin create (spec file 26): `SET LOCAL lock_timeout = '3s'`, lock the Student then the Course Section, then:

| Check | Error |
|---|---|
| Student eligible (active account, credential exists) | existing student errors (403/404) |
| Section missing or deleted | 404 `COURSE_SECTION_NOT_FOUND` |
| Section not `ACTIVE`, course deleted or inactive, or not the student's semester | 409 `COURSE_SECTION_NOT_AVAILABLE` |
| Own `PENDING`/`ACTIVE` row in this section | 409 `ENROLLMENT_ALREADY_EXISTS` |
| Own `DROPPED` row in this section | 409 `ENROLLMENT_DROPPED_BY_UNIVERSITY` (new) |
| Seat held in another section of the course and semester | 409 `STUDENT_ALREADY_ENROLLED_IN_COURSE` |
| Seats taken ≥ capacity | 409 `COURSE_SECTION_FULL` |
| Missing or non-positive `courseSectionId` | 400 `VALIDATION_ERROR` |

`UNIQUE(student_id, course_section_id)` keeps one lifecycle per pair (spec file 25). An own `WITHDRAWN` row (or a soft-deleted one) is therefore **reused**: it returns to `PENDING` with today's date instead of inserting a second row. `enrollment_date` is today in `app.student-academic.timezone`. `created_by` is `NULL` (see section 3); `updated_at` is set when a row is reused.

### 2.3 Withdraw

Locks the enrollment. Another student's enrollment, or a deleted one, is 404 `ENROLLMENT_NOT_FOUND` (no information leak). `WITHDRAWN` answers 200 unchanged. Only `PENDING` and `ACTIVE` may move to `WITHDRAWN` (the existing transition matrix); `DROPPED` is 409 `INVALID_ENROLLMENT_STATUS_TRANSITION`. An enrollment with a recorded course result is 409 `ENROLLMENT_HAS_COURSE_RESULT`, as for the admin delete, because it would leave the CGPA inconsistent. Withdrawing releases the seat (`WITHDRAWN` does not hold one).

### 2.4 Lock (D60)

One transaction: lock the Student row, then each of the student's `PENDING` enrollments **in their own semester** (by id) and its Course Section; set each to `ACTIVE` with `updated_at`. All or nothing: a section that is deleted or no longer `ACTIVE` fails the whole lock with 409 `ENROLLMENT_LOCK_SECTION_CLOSED`, naming the course and section, and nothing changes. No pending request (or no semester) is 409 `NO_PENDING_ENROLLMENTS`. A pending row already holds its seat and passed the same-course check when it was requested, so locking needs no capacity check. Pending requests of other semesters, and other students' requests, are untouched. Locked rows can still be withdrawn (2.3). The transition `PENDING → ACTIVE` is the existing matrix (spec file 26).

### 2.5 Lock one subject (D61)

The mobile app locks subjects one at a time. `POST /{id}/lock` locks the Student, then the enrollment. Another student's or a deleted enrollment is 404 `ENROLLMENT_NOT_FOUND`. `ACTIVE` answers 200 unchanged. Anything other than `PENDING` is 409 `ENROLLMENT_NOT_PENDING`. The section is locked and must still be live and `ACTIVE`, else 409 `ENROLLMENT_LOCK_SECTION_CLOSED`. Then `PENDING → ACTIVE` with `updated_at`. Section 2.4's lock-all shares the same activation step.

## 3. Migration

`V46__allow_student_self_enrollment.sql`: `ALTER TABLE enrollments ALTER COLUMN created_by DROP NOT NULL`. `created_by` references `app_users`; a student is not an app user, and inventing a system user would misattribute the row. `NULL` means "created by the student". `Enrollment.createdBy` is nullable to match. Admin rows are unchanged.

## 4. Repositories

- `EnrollmentRepository.findByStudentIdAndCourseSectionId` (includes soft-deleted rows, like the existing duplicate check).
- `CourseSectionRepository.findOpenForSelfEnrollment(semesterId)` (one query, fetches course, lecturer and semester).
- Seats use the existing grouped `countOccupiedSeats(ids, SEAT_HOLDING)`; meetings use `StudentTimetableService`.

## 5. Tests

`StudentEnrollmentIntegrationTests` (9): one subject is locked on its own (others stay pending), another student gets 404, a second lock is harmless, a withdrawn request is `ENROLLMENT_NOT_PENDING`, a closed section is refused. Before that (8): lock activates exactly the token student's pending requests of their semester, leaves another semester's request pending, shows them in `/academic/courses`, allows withdrawal afterwards, and refuses an empty lock; a closed section fails the whole lock and nothing is activated. Earlier six: open sections only for the student's semester; enroll creates a `PENDING` row for the token student with `created_by` NULL and today's date, visible in `/academic/enrollments?status=PENDING`; duplicate, same-course, full, closed, inactive-course, other-semester, missing and invalid requests; another student's withdraw is 404, withdraw is idempotent and releases the seat, a withdrawn request is reused; `DROPPED` cannot be re-requested or withdrawn and `ACTIVE` can be withdrawn; no token is 401.

## 6. Out of scope

Enrollment periods (open/close dates), credit limits, prerequisite checks (`prerequisite_courses` is free text), timetable clash checks between the student's sections, waitlists, and notifications to the student when an admin decides.
