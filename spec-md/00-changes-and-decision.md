# Changes and decisions

## 2026-09-22 — Admin Web container joins the smart-university Compose project

### D21 — Admin Web Docker ownership
- **Conflict:** The frontend initially had its own Compose project, which created a separate `smart-ai-admin-web` group instead of grouping containers under the existing `smart-university` project.
- **Decision:** Define `admin-web` in this repository's `compose.yaml` with build context `../../frontend/smart-ai-admin-web`, port `${FRONTEND_PORT:-3000}:80`, and a dependency on `backend`. Keep a frontend `compose.yaml` that includes this file and overrides only the `admin-web` build context, so running Compose from either folder manages the same complete project.
- **Reason:** Docker Desktop groups containers by Compose project name; using the existing `name: smart-university` keeps PostgreSQL, Redis, backend, and Admin Web under one project, while the frontend include prevents orphan warnings when commands start from the frontend folder.

### D22 — Browser-facing frontend API URL
- **Decision:** `VITE_API_BASE_URL` remains `http://localhost:8080` in local Compose configuration. Docker's internal `backend` hostname is not embedded in browser JavaScript because it is resolvable only inside the Compose network.

## 2026-09-18 — Authentication implementation (specification 05)

### D01 — Existing names and configuration conventions
- **Conflict:** Spec 05 illustrates a different base package, YAML, database environment names, and a spec 04 filename that does not exist here.
- **Decision:** Preserve `com.smartAiUniversityAssistant.seniorproject`, `application.properties`, existing `POSTGRES_*` variables, `compose.yaml`, and `04-authentication-create-flyway.md`. Preserve the explicit `.env` import. Add authentication properties under `app.auth`.
- **Reason:** The specification explicitly requires reuse of existing equivalents and the pinned Java 21 / Spring Boot 4.1.1 stack.

### D02 — Existing RSA loader and Docker paths
- **Conflict:** Existing RSA setup accepts filesystem paths under `app.security.jwt`; spec 05 illustrates Spring resource paths under `app.auth.jwt` and a new provider class.
- **Decision:** Reuse `JwtKeyConfiguration`, support filesystem and Spring resource paths, and retain the old key property names as a compatibility fallback. Compose bind sources remain filesystem paths; container paths are injected explicitly. Keep the existing valid key pair. New-key documentation recommends 3072 bits.

### D03 — Schema authority and migration numbering
- **Conflict:** Spec 04 examples start at V1, while this project already has infrastructure V1 and authentication V2–V6.
- **Decision:** Reuse all six migrations unchanged. Hibernate performs validation only; no new authentication migrations or bootstrap accounts are introduced.

### D04 — Abuse controls without an existing gateway
- **Conflict:** Spec 05 requires gateway IP/identifier throttling, but this repository has no gateway deployment.
- **Decision:** Provide bounded Redis fixed-window limits in the application for direct-client IPs and hashed login identifiers, with configurable limits and `429`/`Retry-After`. Do not trust forwarded IP headers. A production gateway must additionally enforce connection/body limits and configure trusted proxy handling deliberately.

### D05 — Equivalent internal types and key rotation scope
- **Decision:** Use small shared outcome/error types where equivalent to the illustrative file list; retain service interfaces and `service/impl` ownership. Pin one configured signing/verification key ID; reject every other ID. Multi-key rollover is a future explicitly configured extension, not dynamic remote key discovery.

### D06 — UTC LocalDateTime on non-UTC developer machines
- **Conflict found in tests:** Hibernate's legacy `java.sql.Timestamp` binding combined with a UTC JDBC calendar shifts `LocalDateTime` values when the JVM default zone is not UTC, breaking exact lock-expiry boundaries.
- **Decision:** Enable Hibernate's direct Java-time JDBC binding, retain the UTC connection session and explicit `ZoneOffset.UTC` conversions, and test exact expiry boundaries on the local non-UTC JVM. No column type changes are needed.

### D07 — Redis reconnect delivery semantics
- **Conflict:** Lettuce's automatic reconnect can replay in-flight commands, which conflicts with specification 05's prohibition on automatically retrying an ambiguous refresh rotation.
- **Decision:** Disable driver automatic reconnect/replay and reject disconnected commands with a bounded queue. Enable Spring's connection validation so a later operation obtains a healthy connection before sending its command. No retry surrounds create/rotate/delete. A lost rotation response requires fresh login.

### D08 — Test isolation and production profile
- **Decision:** Context/integration tests provision dedicated PostgreSQL/Redis Testcontainers and temporary test-only RSA keys, replacing the earlier dependency on developer `.env` databases. Production validation is activated by the `prod` profile and rejects development/placeholder secrets, dev/test session namespaces, HTTP origins, and non-TLS Redis.

### D09 — Strict JWT types before Spring claim conversion
- **Conflict found in tests:** Spring's default claim converter coerces a numeric JWT subject into a string, despite the specification requiring a positive decimal **string** claim.
- **Decision:** Check original Nimbus claim types before standard Spring conversion, then run the complete algorithm/header/claim/time validator. Numeric subjects are rejected rather than silently normalized.

## 2026-09-19 — Initial Super Admin bootstrap (specification 06)

### D10 — Later seed requirement supersedes specification 05
- **Conflict:** Specification 05 excludes seeded users, while specification 06 explicitly requires one initial development Super Admin to be created automatically.
- **Decision:** Treat specification 06 as the later, narrow exception. Implement the account in the new `V7__seed_super_admin_account.sql` Flyway migration so it exists as soon as database migration completes. Keep all previously applied V1-V6 migrations unchanged.

### D11 — Required fields omitted by specification 06
- **Conflict:** `app_users.employee_id`, `first_name`, and `last_name` are required by the existing schema but specification 06 provides only email, password, role, status, force-change state, and department.
- **Decision:** Use development seed values `RSU-SUPER-ADMIN`, `Smart AI`, and `Super Admin` for those required columns.

### D12 — Migration idempotency and existing accounts
- **Decision:** Flyway records V7 and does not execute it again on subsequent startups. V7 additionally uses database conflict handling for the user, credential, and role assignment so it can safely migrate a database that already contains the exact development account. It fails when the fixed email or employee ID belongs to another account rather than granting privileges to it.

### D13 — Password representation
- **Decision:** V7 contains only the BCrypt cost-12 hash of the specification's development password, never its plaintext value. The account retains `force_password_change = TRUE`. The plaintext appears only in specification 06 and the migration acceptance test that verifies the hash. This published development credential must not be reused as a production credential.

## 2026-09-19 — Admin Web API path convention (specification 07)

### D14 — Authentication endpoint paths
- **Conflict:** Specification 05 defines authentication endpoints at `/auth/*`, while the later specification 07 requires every Admin Web feature to use `/api/v1/admin/{feature}`.
- **Decision:** Specification 07 supersedes the authentication paths only. The five authentication endpoints move to `/api/v1/admin/auth/*`. No legacy `/auth/*` aliases are retained, avoiding duplicate contracts and preventing accidental public-route expansion.

## 2026-09-19 — Create Admin User API (specification 08)

### D15 — Existing error contract and specification 08 examples
- **Conflict:** Existing authentication validation uses `VALIDATION_ERROR`, while specification 08 illustrates `VALIDATION_FAILED`; specification 05 uses `FORBIDDEN`, while specification 08 names `ACCESS_DENIED` for this endpoint.
- **Decision:** Preserve `VALIDATION_ERROR` as the shared malformed/invalid request code. Return `ACCESS_DENIED` specifically for authorization failures on `/api/v1/admin/users`, while retaining `FORBIDDEN` elsewhere. Continue using the existing `ApiError` shape without adding a feature-specific success or error envelope.

### D16 — Request strictness and provisioning ownership
- **Decision:** Enable global rejection of unknown JSON properties and scalar coercion because all current request DTOs are allowlisted and this prevents audit/credential mass assignment. Keep normalized entity/repository ownership in authentication; `feature.user` owns HTTP DTOs and orchestration, while an authentication provisioning service owns the single database transaction.

## 2026-09-20 — Admin User CRUD workflow (specification 09, file `08-crud-admin-user.md`)

### D17 — Specification file naming
- **Conflict:** The stored file `spec-md/08-crud-admin-user.md` is titled "09 — Admin User CRUD Workflow" and names `spec-md/09-admin-user-crud-workflow.md` as its intended location.
- **Decision:** Treat the stored file as the authoritative continuation of specification 08 under its actual filename; no file is renamed.

### D18 — No per-user session index, so CRUD performs no Redis session deletion
- **Conflict:** Specification 09 asks for post-commit session invalidation on status change, role replacement, and soft delete only when the existing design supports a reliable per-user session index.
- **Decision:** This project follows specification 05's baseline: refresh sessions are individually keyed by user and session ID with **no secondary per-user index**, and specification 05 explicitly forbids one. CRUD mutations therefore issue no Redis session deletions. Denial relies on the existing per-request and per-refresh database rechecks of `is_deleted`, account status, active roles, and credential revision. Documented limitation: a reactivated user's old sessions may become usable again until their natural expiry; "all devices signed out" is not claimed.

### D19 — Error codes and self-change no-ops for CRUD
- **Decision:** Keep the shared `VALIDATION_ERROR` code (specification 09 examples reuse `VALIDATION_FAILED` only illustratively). Unusable role selections on the update/role routes return `400 INVALID_ROLE` for missing, inactive, or unsupported roles; the completed create route keeps its specification 08 codes (`404 ROLE_NOT_FOUND`, `400 ROLE_INACTIVE`, `400 ROLE_NOT_ASSIGNABLE`) unchanged. A self-request whose status and effective assignment set are unchanged is a permitted no-op; effective self status/role changes and self-delete return `409 SELF_ACCOUNT_CHANGE_NOT_ALLOWED`. `ACCESS_DENIED` remains the authorization code for the whole `/api/v1/admin/users` tree.

### D20 — List page size limit
- **Conflict:** Specification 09 defines the list default page size as 20 with an allowed range of 1–100.
- **Decision:** Per product request (2026-09-20), the list endpoint uses a page size limit of 50 per page: default `size=50`, allowed range 1–50, larger values rejected with `400`.

## 2026-09-24 — Faculty Management (specifications 09-file `09-flyway-faculties.md` and 10-file `10-implement-crud-faculties.md`)

### D21 — Faculty authorization roles
- **Decision:** Follow specification 10's recommendation: `SUPER_ADMIN`, `ADMIN`, and `ACADEMIC_ADMIN` may use all Faculty endpoints, unlike the Super-Admin-only Admin User endpoints. Password-change-restricted callers still receive `403 PASSWORD_CHANGE_REQUIRED`; other unsupported roles receive `403 FORBIDDEN`.

### D22 — No Redis caching for Faculty
- **Conflict:** Specification 10 describes Redis caching only "if Redis cache management is enabled in the project".
- **Decision:** The project has no cache infrastructure (`@EnableCaching`, cache manager, or cache namespace); Redis serves authentication sessions and throttling under a `noeviction` policy. Faculty reads query PostgreSQL directly, which remains the sole source of truth. Caching can be introduced later as an explicitly designed extension.

### D24 — Development Super Admin seed preference
- **Decision:** Per explicit user request, the development seed uses `force_password_change = FALSE`. This supersedes D13's forced-change value for the seeded account only. Existing accounts are not reset on startup, and changing migration metadata does not change stored credentials. Other accounts retain the existing forced-change policy. Any V7 checksum mismatch on an existing installation must be verified with Flyway before an explicitly authorized repair; do not guess or manually overwrite checksums.

### D25 — Department referential integrity
- **Decision:** V9 creates departments and V10 adds the nullable `app_users.department_id` foreign key. Non-null department IDs must now reference an existing department; this supersedes the earlier unverified-placeholder contract. Invalid existing references are not rewritten by the migrations. Create/update user operations translate this known FK failure to `400 DEPARTMENT_NOT_FOUND`. Test fixtures use actual departments and clear test-only user department references before teardown.

### D23 — Faculty response, error, and uniqueness conventions
- **Decision:** Keep the project's established conventions instead of the specification's illustrative `{"success","message","data"}` envelope and `error`-style examples: plain DTO bodies, existing `ApiError` shape, `VALIDATION_ERROR`, and codes `FACULTY_NOT_FOUND` (404), `FACULTY_CODE_ALREADY_EXISTS` (409), `FACULTY_NAME_ALREADY_EXISTS` (409). Timestamps emit ISO-8601 UTC with `Z`. Following the project's uniqueness policy, soft-deleted Faculties keep `faculty_code`/`faculty_name_en` reserved (database UNIQUE constraints cover deleted rows; friendly checks include them and races translate to `409`). Faculty delete is not idempotent: a missing or already deleted target returns `404`, per the specification's delete workflow. `departmentCount`/`studentCount`/`totalDepartments` return `0` until the Department feature exists.

## 2026-09-24 — Department CRUD (`12-implement-department.md`)

### D26 — Department contracts and Faculty selection
- **Decision:** Keep plain DTO responses and the existing paginated shape. The specification mixes nested and flat Faculty response examples; use its section 52 flat `facultyId`, `facultyCode`, `facultyNameEn` fields consistently for create/detail/update and list items. All three administrative roles may use these routes. Default page size is 20, maximum 100; literal case-insensitive search, allowlisted sorting, and `id,asc` tie-breaking match Faculty conventions.
- **Decision:** Codes are trimmed and match `^[A-Z0-9-]+$`. Names are trimmed and unique within the Faculty using exact-case database semantics, rather than the illustrative IgnoreCase repository example. Both unique constraints reserve values after soft deletion. Every create/update requires an active, non-deleted selected Faculty; missing/deleted returns `404 FACULTY_NOT_FOUND`, inactive returns `400 INVALID_FACULTY`.

### D27 — Assignment-safe soft deletion
- **Decision:** Interpret assigned users as all non-deleted users, including inactive/locked/suspended accounts; otherwise later activation would leave a live user attached to a deleted department. Deleted users do not block deletion and retain their historical department reference. Missing/already-deleted departments return `404`; assigned departments return `409 DEPARTMENT_IN_USE`; successful soft deletion returns `204`.
- **Implementation:** Department deletion and application user-assignment writers share a pessimistic department row lock with a PostgreSQL lock timeout. User create/full-update operations reject missing or deleted department references with `400 DEPARTMENT_NOT_FOUND`. This closes the assignment-vs-delete race without clearing anyone's department ID. Direct SQL writes are outside this application-level soft-delete guarantee. Authentication owns the narrow assigned-user read service.

### D28 — Live Faculty counts and conditional caching
- **Decision:** Faculty `departmentCount` and summary `totalDepartments` now count non-deleted departments (including inactive ones). List counts are fetched in one batch; Department list uses a to-one Faculty entity graph. Multi-query lists/summaries use repeatable-read snapshots. Program/course/student counts remain zero because those features do not exist.
- **Decision:** Continue D22: application caching is not enabled. The Department spec's conditional Redis caching does not require a new cache subsystem. PostgreSQL reads immediately reflect successful writes and Faculty moves; no cache eviction is necessary.

### D29 — Fractional ID rejection
- **Finding:** Disabling scalar coercion alone still allowed Jackson to truncate a floating-point JSON ID to an integer.
- **Decision:** Disable `accept-float-as-int` globally to enforce the existing integer ID contract; fractional IDs now return `400 VALIDATION_ERROR`.

## 2026-09-26 — Programs & Majors CRUD (`14-implementation-crud-programs&majors.md`)

### D30 — Free-text degree level across the Program feature
- **Decision:** Store and expose `degreeLevel` as a trimmed, nonblank JSON/Java `String` (maximum 100) without an enum, list, or lookup. Degree filtering compares the full string case-insensitively; uniqueness follows the database's exact-case `(department_id, program_name, degree_level)` constraint. Reject numeric/array JSON values rather than coercing them to Strings. `facultyId` is used only to validate the Department's current Faculty; it is not persisted in `programs`.

### D31 — Program API, uniqueness and related counts
- **Decision:** Use existing plain DTOs, standard pagination shape (default 20, max 100), allowlisted sorting and `id,asc` tie-breaker, with flat Faculty/Department identifiers, codes and names instead of illustrative nested/enveloped response examples. Authorize `SUPER_ADMIN`, `ADMIN`, and `ACADEMIC_ADMIN` full sessions. Match current Faculty/Department conventions: codes and scoped name/degree triples are reserved after soft deletion; known database uniqueness races map to `409 PROGRAM_CODE_ALREADY_EXISTS` or `409 PROGRAM_ALREADY_EXISTS`. Missing/deleted targets and repeated DELETE yield `404 PROGRAM_NOT_FOUND`. Inactive or mismatched Departments yield `400 INVALID_PROGRAM_DEPARTMENT`.
- **Decision:** Department `programCount` now derives from non-deleted Programs, including inactive ones, using a single grouped query per page. Course and student counts remain zero. Keep program read paths in PostgreSQL: caching remains conditional because this project has no application cache manager or cache namespace (D22/D28). No additional migrations or degree lookup tables are introduced.
- **Decision:** With Programs implemented, Department soft deletion also rejects any non-deleted Program (including inactive ones) with `409 DEPARTMENT_HAS_PROGRAMS`. Program create and reassignment lock the Department row before locking its Faculty, matching Department mutation lock order and serializing against deletion; deleted Programs do not block later Department deletion.

## 2026-09-27 — Semester CRUD (`16-implement-semester.md`)

### D32 — Semester contracts and scope limits
- **Decision:** Implement the five specified routes only (no summary endpoint; the spec makes it optional and the Admin UI does not require it). The Semester table has no `is_active`, so there is no activate/deactivate route and no status filter. Use plain DTOs, the existing page shape (default page 0 / size 20, max 100), literal case-insensitive search across both names, allowlisted sorting (`id`, `semesterNameTh`, `semesterNameEn`, `createdAt`, `updatedAt`) with the `id,asc` tie-breaker, and the three administrative roles. Names are trimmed, required, and limited to 150 characters; audit fields come from the token.
- **Decision:** Duplicate checks and the database constraint use exact-case paired Thai/English names, and the pair stays reserved after soft deletion (`409 SEMESTER_ALREADY_EXISTS`). Missing/deleted targets and repeated DELETE return `404 SEMESTER_NOT_FOUND`. Redis caching remains conditional (D22/D28): no application cache manager exists, so reads query PostgreSQL directly. No Academic Year relationship, `semester_code`, or date fields are introduced.

## 2026-09-28 — Lecture CRUD (`18-implement-lectures.md`)

### D33 — Lecture contracts and Faculty/Department validation
- **Decision:** Keep the project's established conventions instead of the specification's illustrative `{"success","message","data"}` envelope and nested Faculty/Department response examples: plain DTO bodies, existing `ApiError` shape, `VALIDATION_ERROR`, and flat `facultyId`/`facultyCode`/`facultyNameEn` + `departmentId`/`departmentCode`/`departmentName` fields (matching D23/D26/D31). All six specified routes are implemented, including `/summary`. Default page size 20, maximum 100; literal case-insensitive search across `lectureNameTh`, `lectureNameEn`, and `lectureNickname`; allowlisted sorting (`id`, `lectureNameTh`, `lectureNameEn`, `lectureNickname`, `status`, `createdAt`, `updatedAt`) with the `id,asc` tie-breaker; the three administrative roles.
- **Decision:** `status` stays a free `String` mapped to `VARCHAR(30)` (no enum/table), normalized to uppercase on write so `active`/`inactive` are accepted, and restricted to `ACTIVE`/`INACTIVE` (others yield `400`); the list status filter requires the exact uppercase value, matching Department conventions. Create defaults to `ACTIVE` when the field is absent and rejects an explicit JSON `null` (`Nulls.FAIL`, matching `isActive` handling). Blank nicknames normalize to `NULL`.
- **Decision:** Duplicate checks and the `uk_lectures_department_names` constraint use the exact-case `(department_id, lecture_name_th, lecture_name_en)` triple, which stays reserved after soft deletion (`409 LECTURE_ALREADY_EXISTS`); uniqueness races translate to the same `409`. Faculty/Department selection mirrors Program (D31): missing/deleted Faculty → `404 FACULTY_NOT_FOUND`, inactive Faculty → `400 INVALID_FACULTY`, missing/deleted Department → `404 DEPARTMENT_NOT_FOUND`, inactive or Faculty-mismatched Department → `400 INVALID_LECTURE_DEPARTMENT`. Create/update lock the Department row before the Faculty row, and update/delete lock the Lecture row, all under the shared 3-second lock timeout. Missing/deleted targets and repeated DELETE return `404 LECTURE_NOT_FOUND`. Per specification section 50, no course/section/schedule dependency checks exist yet, so delete is always a plain soft delete.

### D34 — No Redis caching for Lectures
- **Decision:** Continue D22/D28/D31/D32: the project has no application cache manager or cache namespace, and Redis serves authentication sessions and throttling only. Lecture reads query PostgreSQL directly, which remains the sole source of truth, so the specification's cache names/keys/eviction steps are not implemented; caching can be introduced later as an explicitly designed extension.

## 2026-10-04 — Course Section CRUD (`22-implement-coursesections.md`)

### D35 — Course Section contracts and validation
- **Decision:** Keep the project's established conventions instead of the specification's illustrative envelope/nested response examples: plain DTO bodies, existing `ApiError` shape, `VALIDATION_ERROR`, and flat related-entity fields (`courseId`/`courseCode`/`courseName`, `lectureId`/`lectureNameEn`/`lectureNameTh`/`lectureNickname`, `semesterId`/`semesterNameEn`/`semesterNameTh`), matching D23/D26/D31/D33. All six specified routes are implemented under `/api/v1/admin/course-sections`, including `/summary`. Default page 0/size 20, max 100; literal case-insensitive search across course code/name, section number, lecturer Thai/English names and nickname, and room; allowlisted sorting (`id`, `sectionNumber`, `capacity`, `status`, `room`, `createdAt`, `updatedAt`) with the `id,asc` tie-breaker; the three administrative roles. No Academic Year property, filter, or storage is introduced (unknown JSON properties and query parameters already fail with `400`).
- **Decision:** `status` stays a `String` mapped to `VARCHAR(30)`, validated through the new `CourseSectionStatus` enum (`ACTIVE`/`CLOSED`, case-insensitive `fromValue`, canonical value persisted); unsupported values (`FULL`, `INACTIVE`, `PENDING`, ...) yield `400 INVALID_COURSE_SECTION_STATUS`. Create defaults to `ACTIVE` when absent and rejects an explicit JSON `null` (`Nulls.FAIL`); update requires `status`. The list status filter requires the exact uppercase value. `FULL` is never persisted: `enrolledCount`/`isFull` are derived response-only values fixed at `0`/`false` until the Enrollment feature exists, and summary `fullSections`/`totalEnrolled` are likewise `0`.
- **Decision:** Duplicate checks and the `uk_course_sections_course_section_semester` constraint use the exact `(course_id, section_number, semester_id)` triple (leading zeros preserved, no numeric conversion), reserved after soft deletion (`409 COURSE_SECTION_ALREADY_EXISTS`); races translate to the same `409`. On create, an omitted `capacity` falls back to the course's `maximum_students_per_section`; if neither exists the request is rejected with `400 VALIDATION_ERROR`. Update always requires an explicit positive capacity. The capacity-vs-enrollment guard from specification section 52 is deferred until Enrollment exists.
- **Decision:** Related-entity selection mirrors Lecture/Course (D33): missing/deleted Course → `404 COURSE_NOT_FOUND`, inactive Course → `400 INVALID_COURSE_SECTION_COURSE`; missing/deleted Lecturer → `404 LECTURE_NOT_FOUND`, non-`ACTIVE` Lecturer or Lecturer/Course faculty-department mismatch → `400 INVALID_COURSE_SECTION_LECTURER`; missing/deleted Semester → `404 SEMESTER_NOT_FOUND` (Semesters have no active flag, so `InvalidCourseSectionSemesterException` from the specification's illustrative package list is not created). Create/update lock the Course, Lecturer, and Semester rows; update/delete lock the Section row, all under the shared 3-second lock timeout. Missing/deleted targets and repeated DELETE return `404 COURSE_SECTION_NOT_FOUND`. Per specification section 60, no enrollment dependency check exists yet, so delete is always a plain soft delete. Closing (`status=CLOSED`) and deleting remain separate operations.

### D36 — Course `sectionCount` derives from `course_sections`; still no Redis caching
- **Decision:** Course list `sectionCount` now counts non-deleted `course_sections` per course, fetched in one grouped query per page (the `DepartmentStatisticsService` batch pattern); Course detail response and summary shapes are unchanged. Course-section mutations need no cross-feature cache eviction because application caching remains disabled (D22/D28/D31/D32/D34): PostgreSQL reads immediately reflect successful writes, and the specification's `course-section-cache` names/keys/eviction steps are not implemented.

## 2026-10-06 — Enrollment Flyway migration (`25-flyway-enrollment-migration.md`)

### D37 — Enrollment migration version and scope
- **Conflict:** The specification's heading numbers it 26 while the file is `25-...`, and it illustrates `V25__create_enrollments.sql`. The latest existing migration is `V18__allow_self_registered_students_without_academics.sql`.
- **Decision:** Create `V19__create_enrollments.sql`, the next free version; V1-V18 are unchanged. The table, columns, constraint names, and the nine indexes match specification sections 44-46 exactly, written in the compact style of V15/V16. No `academic_year`, `semester_id`, `course_id`, capacity, enrolled-count, or full-flag columns; no ON DELETE CASCADE; no status default. Per section 73, no entity, repository, DTO, service, controller, or cache code is added.
- **Verification:** All migrations applied to a scratch PostgreSQL 17 database; a duplicate Student + Section insert fails on `uk_enrollments_student_course_section`, `APPROVED`/lowercase/null statuses are rejected, and an unknown `student_id` fails `fk_enrollments_student`. Flyway applies V19 on application startup in the Testcontainers context and Student integration tests.

### D38 — Enrollment eligibility vs. open student self-registration (for the Enrollment CRUD spec)
- **Note:** Self-registered students are `ACTIVE` and may have no Faculty, Department, or Program (V18). Specification section 38's "ACTIVE only" rule therefore admits such students into enrollment. The migration does not depend on this; the Enrollment service specification must decide whether enrollment also requires a complete academic profile.
- **Resolution (2026-10-06, `26-implement-enrollment-crud-workflow.md`):** Follow specification 26 section 30 as written: a Student is eligible when the row exists, is not soft-deleted, and has `account_status = ACTIVE`. A complete academic profile (Faculty, Department, Program) is **not** required, so self-registered students can be enrolled. Revisit if enrollment must be limited to fully profiled students.

## 2026-10-06 — Enrollment CRUD (`26-implement-enrollment-crud-workflow.md`)

### D39 — Enrollment contracts, schema reuse, and validation
- **Conflict:** The specification's heading numbers it 27 while the file is `26-...`; it asks for a new `V{next}__create_enrollments.sql`, illustrates a `{success, message, data}` envelope, an error body with `fieldErrors`, `200` for delete, `AppUser` audit relationships, and a duplicate `CourseSectionNotFoundException` inside the enrollment package.
- **Decision:** Reuse `V19__create_enrollments.sql` (D37), whose schema already matches sections 9 and 12 exactly; no second enrollment migration. Keep the project conventions (D15/D23/D35): plain DTO bodies, the existing `ApiError` shape, `VALIDATION_ERROR`, `204 No Content` for DELETE, `Cache-Control: no-store`, `Long createdBy/updatedBy` audit columns, UTC `Instant` timestamps, and the existing `coursesection` `CourseSectionNotFoundException` (`404 COURSE_SECTION_NOT_FOUND`). `status` is an `EnrollmentStatus` enum mapped with `EnumType.STRING`; API input is trimmed and upper-cased, unknown values yield `400 INVALID_ENROLLMENT_STATUS`.
- **Decision:** All seven routes live under `/api/v1/admin/enrollments` with numeric `{id:\d+}` paths, for `SUPER_ADMIN`/`ADMIN`/`ACADEMIC_ADMIN` (security matcher plus service `@PreAuthorize`). Create accepts only `studentId`, `courseSectionId`, optional `enrollmentDate` (defaults to today's UTC date, D06) and `status`; course, semester, academic year, capacity and audit fields are rejected as unknown properties. List supports `search` (student code, first/last name, university email, course code/name), `studentId`, `courseId`, `courseSectionId`, `semesterId`, exact-uppercase `status`, ISO `enrollmentDate`, page 0/size 20 (max 100) and allowlisted sort (`enrollmentDate`, `status`, `createdAt`, `updatedAt`, `id`; default `enrollmentDate,desc` with `id,asc` tie-breaker). No Academic Year filter or storage.
- **Decision:** Missing Student → `404 STUDENT_NOT_FOUND`; soft-deleted or non-`ACTIVE` Student → `409 ENROLLMENT_STUDENT_NOT_ELIGIBLE`. Missing/deleted Section → `404 COURSE_SECTION_NOT_FOUND`; `CLOSED` Section → `409 COURSE_SECTION_NOT_AVAILABLE`. Exact Student + Section pairs are rejected with `409 ENROLLMENT_ALREADY_EXISTS` **including soft-deleted rows**, because the unconditional UNIQUE constraint still reserves the pair (re-enrollment needs a separate workflow, section 69); constraint races translate to the same code and any other integrity failure to `409 DATABASE_CONSTRAINT_VIOLATION`. Same Course + Semester holds and capacity count only `PENDING`/`ACTIVE` rows; a record created directly as `WITHDRAWN`/`DROPPED` skips both checks.
- **Decision:** Concurrency: create/move lock the Student row and then the Course Section row (`PESSIMISTIC_WRITE`, 3-second lock timeout), always in that order. The Section lock serializes the final-seat race; the Student lock serializes two requests for different sections of the same course and semester. Isolation stays `READ COMMITTED`.
- **Decision:** PUT requires `status` and applies the section 19 transition matrix. Student/Section may change only while the stored status is `PENDING`, revalidating eligibility, duplicate, same Course/Semester and capacity against the target; otherwise `409 ENROLLMENT_UPDATE_NOT_ALLOWED`. An omitted `enrollmentDate` keeps the stored date. PATCH `/status` is idempotent for the same status (no audit change); `WITHDRAWN` and `DROPPED` are terminal (`409 INVALID_ENROLLMENT_STATUS_TRANSITION`). Withdraw/drop never soft-delete; DELETE is a soft delete and a repeated DELETE returns `404 ENROLLMENT_NOT_FOUND`.

### D40 — Course Section occupancy now derives from Enrollment; still no Redis caching
- **Decision:** Per specification 26 section 127 and specification 22 section 52, Course Section `enrolledCount` counts non-deleted `PENDING`/`ACTIVE` enrollments (one grouped query per list page), `isFull` is `enrolledCount >= capacity`, and the summary's `fullSections`/`totalEnrolled` use the same rule. Course Section update rejects a capacity below the current `enrolledCount` with `409 COURSE_SECTION_CAPACITY_BELOW_ENROLLED`; the Section row lock already held by update makes this check race-free against concurrent enrollment.
- **Decision:** Continue D22/D28/D34/D36: no application cache manager exists, so the specification's `enrollment-cache`, list/summary caches, `course-section-cache` eviction and `AFTER_COMMIT` listeners are not implemented. Reads query PostgreSQL directly, which therefore reflects every committed write immediately.
- **Verification:** `EnrollmentIntegrationTests` (Testcontainers PostgreSQL 17 + Redis) cover create/derived fields, eligibility, duplicate and same Course/Semester rules, capacity, the transition matrix, PENDING-only moves, list search/filters/sort/validation, summary, soft delete, roles, CHECK-constraint statuses, Course Section occupancy and capacity guard, and concurrent final-seat, duplicate and same-course races. Existing feature test fixtures now clear `enrollments` before deleting Students, Sections or users.

## 2026-10-06 — Semester Academic Year (user request)

### D41 — `academic_year` column on `semesters`
- **Conflict:** Specifications 15/16 (and D32) excluded Academic Year from Semester and anticipated a future Academic Year feature referenced through `academic_year_id`. The user has now asked for an Academic Year column directly on the `semesters` table.
- **Decision:** Add `V20__add_semester_academic_year.sql` (V1-V19 unchanged, since V18/V19 may already be applied locally). `academic_year` is a required `INTEGER` holding a 4-digit Gregorian year (for example `2026`), restricted by `ck_semesters_academic_year` to 2000-2100. Semesters have no date fields, so existing rows are backfilled from `EXTRACT(YEAR FROM created_at)` before the column becomes `NOT NULL`. No Academic Year table or foreign key is introduced.
- **Decision:** Because the same Thai/English names repeat every year, `uk_semesters_name_th_en` is replaced by `uk_semesters_year_name_th_en (academic_year, semester_name_th, semester_name_en)`; the triple stays reserved after soft deletion and duplicates or races still return `409 SEMESTER_ALREADY_EXISTS`. Create and PUT require `academicYear` (missing, `null`, or out-of-range values yield `400 VALIDATION_ERROR`); detail and list responses include it. The list accepts an optional exact `academicYear` filter and `academicYear` as a sort field. Course Section and Enrollment responses are unchanged (they still expose only Semester ids and names).
- **Verification:** `SemesterIntegrationTests` cover required/range validation on create and update (including the inclusive 2000/2100 bounds and rejected string, decimal and boolean JSON), same names in different years, year-scoped duplicates on create and update, the list filter and sort. Fixtures that insert semesters directly now supply `academic_year`, and the Flyway history assertions expect 20 migrations and check V20's description and the `NOT NULL` column. The README Semester section documents the new field, filter, sort and uniqueness.
