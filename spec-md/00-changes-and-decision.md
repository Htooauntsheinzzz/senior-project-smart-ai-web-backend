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
