# 09 — Admin User CRUD Workflow

**Project:** Smart University Student Assistant backend  
**Feature:** Admin user management  
**Base path:** `/api/v1/admin/users`  
**Local base URL:** `http://localhost:8080/api/v1/admin/users`  
**Intended backend location:** `spec-md/09-admin-user-crud-workflow.md`

## 1. Purpose and scope

Continue the Admin User feature after the completed Create Admin User API described in `08-admin-user-create-api.md`. Implement list, detail, update, status change, role replacement, and soft delete. Reference the existing creation operation in the end-to-end workflow; do not reimplement it or change its request, response, or behavior.

Follow specification 02 for feature ownership, 04 for the normalized schema, 05 for authentication/session behavior, 07 for `/api/v1/admin/{feature}`, and 08 for existing profile validation and authorization. This document specifies the remaining implementation and tests; it does not claim they have already been implemented or executed.

Use only the existing tables: `app_users`, `app_roles`, `app_user_roles`, and `appuser_credentials`. Preserve their names, columns, constraints, identity generation, and applied migrations. No schema redesign, Department feature, role-definition CRUD, password reset, credential editing, restore, hard delete, bulk operation, invitation, or automatic login is included.

These routes manage records in `app_users`; the schema has no separate admin-user discriminator. Do not invent one or hide a user solely because its role is inactive or missing. Assignment through these routes remains restricted to supported administrative roles.

## 2. Endpoint contract

Every endpoint requires `Authorization: Bearer <access-token>`. JSON mutations use `Content-Type: application/json`. Return `Cache-Control: no-store` for profile responses. IDs are positive signed 64-bit integers, without string/number coercion.

| Method | Path | Request | Success |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/users` | `AdminUserListQuery` | `200 AdminUserPageResponse` |
| GET | `/api/v1/admin/users/{id}` | Path ID | `200 AdminUserDetailResponse` |
| PUT | `/api/v1/admin/users/{id}` | `UpdateAdminUserRequest` | `200 AdminUserDetailResponse` |
| PATCH | `/api/v1/admin/users/{id}/status` | `UpdateAdminUserStatusRequest` | `200 AdminUserDetailResponse` |
| PUT | `/api/v1/admin/users/{id}/role` | `UpdateAdminUserRoleRequest` | `200 AdminUserDetailResponse` |
| DELETE | `/api/v1/admin/users/{id}` | No body | `204`, no response body |

The singular `/role` subresource represents the form's one selected administrative role. All mutation routes share the same underlying validation, authorization, audit, locking, and session-invalidation behavior. A full update can change profile, status, department, and role atomically; the supporting routes change only their named fields.

Success examples below show payloads without an envelope. Reuse an existing standard success wrapper if present; do not add a competing response format. A `204` response always has no body.

## 3. Persistence and data ownership

| Table | CRUD responsibility |
| --- | --- |
| `app_users` | Read/write profile, account status, nullable department ID, soft-delete flag, and update audit fields |
| `app_roles` | Read/validate role definitions; never create or modify a role in this feature |
| `app_user_roles` | Read assignment joins; replace the selected assignment transactionally and record assigner/time |
| `appuser_credentials` | Remains owned by authentication; no CRUD response or update DTO exposes credentials and these operations do not modify credential state |

Keep `department_id BIGINT NULL` with **no foreign key**, no Department entity association, and no Department repository/service lookup. Null is the normal current value. A positive non-null ID is an unverified placeholder; do not invent a department name or validate department existence. Reconcile placeholder IDs in a future Department integration before adding a relationship.

Preserve the single existing entity/repository owner for each table under `feature.authentication`, as established by specification 08. Extend a narrow authentication-owned account-management service/port for user-management reads and mutations. Do not duplicate entities or repositories in `feature.user`.

## 4. Authorization and actor identity

Continue specification 08's policy: **only a currently eligible `SUPER_ADMIN` may use any endpoint in this document**, including reads and supporting mutations. `ADMIN` and `ACADEMIC_ADMIN` receive `403`; there is no implicit role hierarchy.

- Reuse existing RS256 JWT, current Redis session, account eligibility, credential revision, and current database-role checks. A stale JWT role claim never grants permission.
- Require an active, non-deleted caller with current active Super Admin authority. Authentication handles temporary credential lockouts and invalid/revoked sessions.
- A password-change-restricted caller receives `403 PASSWORD_CHANGE_REQUIRED` on these routes.
- Enforce protected HTTP routing and service-level method authorization. Recheck eligibility/authority inside the mutation transaction through the established security/domain services.
- Resolve actor ID exclusively from the verified principal. Reject client-supplied IDs for `createdBy`, `updatedBy`, `assignedBy`, or similar audit properties.
- Authenticate/authorize before target existence, duplicate checks, or other account-specific disclosures.

For this continuation, self-delete, self-change of account status, and self-replacement of roles are prohibited with `409 SELF_ACCOUNT_CHANGE_NOT_ALLOWED`. A full self-update may change profile/department only when status and the effective assignment set remain unchanged. An unchanged status/role request is a no-op. This explicit policy keeps an administrator from accidentally removing their own access; it does not introduce a new database field.

## 5. List request: pagination, search, filters, sorting

`AdminUserListQuery` accepts only the following parameters:

| Parameter | Type/default | Rules |
| --- | --- | --- |
| `page` | Integer, `0` | Zero-based; minimum 0; reject overflow |
| `size` | Integer, `20` | Range 1–100 |
| `search` | Optional string | Trim; blank means absent; maximum 255 characters |
| `accountStatus` | Optional enum | Exactly `ACTIVE`, `INACTIVE`, `LOCKED`, or `SUSPENDED` |
| `roleId` | Optional positive Long | Match an assigned role ID, whether the definition is active or inactive |
| `departmentId` | Optional positive Long | Exact stored scalar match; no existence check |
| `departmentUnassigned` | Optional boolean, `false` | `true` means `department_id IS NULL`; incompatible with `departmentId` |
| `sort` | Repeatable `field,direction` | Default `createdAt,desc`; up to three sort entries |

Allow sort fields `id`, `employeeId`, `firstName`, `lastName`, `email`, `accountStatus`, `createdAt`, and `updatedAt`. Directions are exactly `asc` or `desc`. Reject unsupported fields, repeated sort fields, malformed pairs, and unknown parameters with `400`. Map allowlisted names to known persistence expressions; never concatenate raw client input into SQL. Sort nullable timestamps with nulls last. Append `id,asc` as a stable tie-breaker unless ID is already explicitly sorted.

Search is a case-insensitive literal substring across `employee_id`, `first_name`, `last_name`, and `email`, combined with OR. Escape SQL LIKE wildcard characters so `%` and `_` in search text are literal characters. Search case folding is for discovery only; email/employee-ID uniqueness and login retain specification 08's exact-case rules. Combine search with all supplied filters using AND.

Every normal list query and count includes `app_users.is_deleted = false`. Non-deleted inactive, locked, and suspended users remain visible for administration. Do not expose `includeDeleted` or a deleted-user filter in this scope. Unknown but valid role/department filter IDs return an empty result, not `404`. Empty or out-of-range pages return `200` with an empty `content` array and correct totals.

Example:

```http
GET /api/v1/admin/users?page=0&size=20&search=mali&accountStatus=ACTIVE&sort=createdAt,desc
```

## 6. Role joins and safe query design

Join `app_users.id = app_user_roles.user_id`, then `app_user_roles.role_id = app_roles.id`. Use left joins for response projection so users with zero assignments remain visible. Return every existing assignment in the management response, including inactive role definitions; mark each role's `isActive` explicitly. These returned assignments are not effective authentication authorities: authentication uses only active roles.

Use an `EXISTS` predicate for the role filter, or equivalent distinct-user logic. A user with multiple assignments must appear once, and counts must count users rather than joined rows. Do not paginate a collection fetch join. Page user rows/IDs first, batch-fetch their assignments and role definitions, and assemble DTOs in original page order. Fetch all roles for matched users, not only the role that matched the filter. Sort response role arrays by role ID for deterministic output.

Avoid N+1 queries and credentials joins. Read and map DTOs inside a read-only transaction with Open Session in View disabled. Use a consistent database snapshot for the page, total, and role projection when claiming that they describe the same result set; offset pagination across separate requests can still shift under concurrent writes.

## 7. Response DTOs

### Role and list DTOs

`AdminUserRoleResponse` contains only:

| Field | Type |
| --- | --- |
| `id` | Long |
| `roleCode` | String |
| `roleName` | String |
| `isActive` | Boolean |
| `assignedBy` | Nullable Long |
| `assignedAt` | UTC timestamp |

`AdminUserSummaryResponse` contains `id`, `employeeId`, `firstName`, `lastName`, `email`, nullable `phoneNumber`, nullable `departmentId`, `accountStatus`, `roles: AdminUserRoleResponse[]`, `createdAt`, and nullable `updatedAt`.

`AdminUserPageResponse` contains `content: AdminUserSummaryResponse[]`, `page`, `size`, `totalElements` (Long), `totalPages`, `first`, `last`, and `sort` (the effective sort including any ID tie-breaker). Define `last` as true when there is no following page, including empty/out-of-range pages; `first` is true only for page zero.

Illustrative empty response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "sort": ["createdAt,desc", "id,asc"]
}
```

### Detail DTO

`AdminUserDetailResponse` adds nullable `createdBy` and `updatedBy` to the summary fields. Example role and actor IDs are illustrative; never hardcode seed IDs:

```json
{
  "id": 42,
  "employeeId": "RSU-002",
  "firstName": "Mali",
  "lastName": "Sukjai",
  "email": "mali.sukjai@rsu.ac.th",
  "phoneNumber": "+66 81 234 5678",
  "departmentId": null,
  "accountStatus": "ACTIVE",
  "roles": [
    {
      "id": 2,
      "roleCode": "ADMIN",
      "roleName": "Admin",
      "isActive": true,
      "assignedBy": 1,
      "assignedAt": "2026-09-19T08:00:00Z"
    }
  ],
  "createdBy": 1,
  "createdAt": "2026-09-19T08:00:00Z",
  "updatedBy": 1,
  "updatedAt": "2026-09-20T08:30:00Z"
}
```

Get-by-ID loads a non-deleted user and its role projections. Missing and deleted targets both return `404 USER_NOT_FOUND`. A missing assignment returns `roles: []`, not a missing user. No department name is returned.

Use allowlisted DTOs, never entity serialization. **Never return passwords, hashes, credential objects/IDs, `forcePasswordChange`, failed-attempt counters, lock internals, tokens, or Redis session data in these remaining CRUD responses.** The already completed create response is unchanged. These operations also reject password/credential fields in requests; password changes stay in authentication.

Use the existing UTC clock/timestamp convention: interpret stored `TIMESTAMP WITHOUT TIME ZONE` values as UTC and emit ISO 8601 timestamps with `Z`.

## 8. Full update DTO and field rules

`UpdateAdminUserRequest` is replacement of the editable profile/access fields, not replacement of the database row. All eight keys below must be present. Required values cannot be null; `phoneNumber` and `departmentId` explicitly accept null. Missing keys are `400`, so omission never silently clears data or applies create defaults.

```json
{
  "employeeId": "RSU-002",
  "phoneNumber": null,
  "firstName": "Mali",
  "lastName": "Sukjai",
  "email": "mali.updated@rsu.ac.th",
  "departmentId": null,
  "accountStatus": "ACTIVE",
  "roleId": 2
}
```

Reject unknown fields, including `id`, `isDeleted`, audit values, role arrays, and credentials. Do not bind JSON directly onto entities.

| Field | Validation and update meaning |
| --- | --- |
| `employeeId` | Required string; trim; nonblank; maximum 50 characters; preserve case; exact uniqueness excluding this target ID |
| `firstName`, `lastName` | Required strings; trim; nonblank; maximum 100 characters each; allow Unicode |
| `email` | Required string; trim; nonblank; valid syntax; maximum 255 characters; preserve case and exact-match uniqueness |
| `phoneNumber` | Nullable string; trim; blank becomes null; maximum 30 characters; if present allow digits, spaces, `+`, `-`, `(`, `)` and require at least one digit |
| `departmentId` | Null clears it; positive signed Long sets an unverified scalar; no FK/existence validation |
| `accountStatus` | Required exact enum from section 9; no create-time default |
| `roleId` | Required positive signed Long; selected role must exist, be active, and use a supported code |

Profile changes do not change IDs, `created_by`, `created_at`, credentials, or login lockout metadata. An email change affects subsequent login immediately under the existing exact-match contract. Existing sessions remain subject to the current account/session checks; changing an email does not issue new tokens.

After normalization, an entirely unchanged valid update returns `200` with the current detail and preserves update/assignment timestamps. Changed profile, status, department, or assignment data sets `updated_by` and `updated_at` once for the operation.

## 9. Account status behavior

`UpdateAdminUserStatusRequest` contains exactly one required, non-null field:

```json
{"accountStatus": "INACTIVE"}
```

| Status | Account eligibility |
| --- | --- |
| `ACTIVE` | Eligible only if all existing authentication checks also pass |
| `INACTIVE` | Login, refresh, and subsequent protected requests denied |
| `LOCKED` | Administratively locked; denied until an authorized status change |
| `SUSPENDED` | Denied until an authorized status change |

For a non-deleted target other than the actor, any transition among these four statuses is allowed. Unknown stored status values are not treated as active; the authorized status route may repair them by selecting a supported status. A same-status request is an idempotent no-op.

Setting `ACTIVE` does not clear temporary credential lockout, reset failed login attempts, change `force_password_change`, change the password, or override missing/inactive-role checks. Administrative `LOCKED` status is distinct from `appuser_credentials.locked_until`. Do not modify credentials to implement activation/unlocking.

Changing to any non-active status requests session revocation after commit when supported, as described in section 14. Activating a deleted target is prohibited (`404`); restore is outside scope.

## 10. Role replacement behavior

`UpdateAdminUserRoleRequest` contains exactly one required field:

```json
{"roleId": 2}
```

Validate the selected row in `app_roles`: it must exist, have `is_active = true`, and have code `SUPER_ADMIN`, `ADMIN`, or `ACADEMIC_ADMIN`. Invalid/inactive/unsupported selections return `400 INVALID_ROLE`; clients cannot create role definitions through this operation. Validate and lock the chosen role in the transaction against concurrent deletion/deactivation.

The normalized schema permits multiple assignments, but the current form manages exactly one selected role. Consequently, role replacement—through full update or `/role`—makes the target's assignment set contain **exactly the selected role**. It removes other existing assignments, including inactive/unsupported ones; it does not silently keep extra privileges. The UI should make this replacement behavior clear when editing a legacy multi-role account.

1. Lock the target user to serialize all changes to its assignment set.
2. Load existing `app_user_roles` rows for that user.
3. If the selected assignment exists, keep it and its original `assigned_by`/`assigned_at`; delete all other assignment rows.
4. If it does not exist, remove old assignment rows and insert a new row with `user_id`, `role_id`, trusted actor `assigned_by`, and server UTC `assigned_at`.
5. An already exact one-role set is a no-op. Otherwise update the user's update audit fields.

Delete only assignment rows during replacement; never delete `app_roles`, the user, or credentials. The join table has no soft-delete flag, so do not invent one. Its unique `(user_id, role_id)` constraint remains authoritative. The assignment table stores current assignments, not full role history; use existing audit infrastructure for change events if available, without adding a history table here.

Read operations still return all existing assignments. Current database roles determine authorization immediately on subsequent requests, even if an existing JWT contains old role claims. If indexed session revocation exists, also revoke sessions after an effective role replacement so the next login refreshes the UI/token snapshot; this is not a substitute for database authorization checks.

## 11. Duplicate validation

Reuse specification 08 normalization and exact-case uniqueness. After trimming, check email and employee ID separately using queries that exclude the target's own ID but **include soft-deleted rows**. Unchanged identifiers are valid. Soft delete does not free either identifier for reuse. Do not lowercase email/employee IDs, add case-insensitive indexes, or introduce an employee-ID pattern.

Friendly prechecks do not replace database constraints under concurrent requests:

| Constraint/conflict | Response |
| --- | --- |
| `uk_app_users_email` | `409 EMAIL_ALREADY_EXISTS` |
| `uk_app_users_employee_id` | `409 EMPLOYEE_ID_ALREADY_EXISTS` |
| Both friendly prechecks fail | `409 USER_ALREADY_EXISTS` with safe errors for both fields |

Use actual applied equivalent constraint names if necessary. Translate known violations using structured exception metadata. Do not return another user's ID, raw SQL, or database text. Unexpected assignment/credential integrity failures are not guessed duplicate-email errors.

## 12. Audit and soft deletion

`created_by` and `created_at` never change in these operations. Preserve existing null creator values from provisioning. For each effective mutation, set `app_users.updated_by` to the authenticated admin ID and `updated_at` to server UTC time. Client-supplied audit fields are invalid. A newly inserted assignment receives the same actor and operation time; preserved assignments retain their original assignment audit values.

Soft delete performs the following within one transaction:

1. Load and lock the target; enforce authorization and self-delete protection.
2. Set `app_users.is_deleted = true` and update audit fields.
3. Preserve the existing account status, profile, role assignments, and credentials. Authentication must deny deleted accounts independently of their stored status.
4. Commit and trigger supported session invalidation.
5. Return `204` with no body.

Do not physically delete the user or cascade-delete credentials, role assignments, or audit references. Never overwrite email/employee ID with synthetic values. No additional `deleted_at`/`deleted_by` columns are required; the update audit records this operation.

First delete of an existing non-deleted target returns `204`. Repeated delete of an already deleted target also returns `204` without altering its audit or credential data; an ID that never existed returns `404`. Use a narrowly scoped internal lookup including deleted rows only for this distinction, duplicate validation, and necessary security checks. Such rows must not leak through list, detail, or update routes. Repeated deletion may retry supported cleanup, but cannot create a session.

## 13. Transactional mutation flow and concurrency

Use a single externally invoked, Spring-proxied transactional account-management operation for each database mutation. User-management orchestration calls this operation through its owning service/port. Do not split profile and role writes into independent commits or `REQUIRES_NEW` operations.

1. Authenticate and authorize at the boundary; validate/normalize the allowlisted DTO.
2. Resolve the trusted actor; begin the transaction and revalidate current authority.
3. Lock the target user row, using an including-deleted lookup only when required for DELETE semantics. Reject deleted targets for updates.
4. Apply self-change checks against the proposed effective state.
5. For role changes, lock/validate the selected role and load assignments. For profile updates, check duplicates excluding the target.
6. Calculate actual changes. Validate all fields before writing; preserve audit fields on a complete no-op.
7. Update profile/status/department and replace assignments as applicable, setting trusted audit values.
8. Flush constraints; map a safe result while the persistence context is available.
9. Commit all changes together; only a successfully returned proxied service operation may produce a success response.
10. Perform supported Redis cleanup after commit through the authentication/session abstraction. Do not report a committed database update as rolled back because cleanup failed.

Use unchecked domain exceptions or explicit rollback rules. A failed profile update, role insertion, validation recheck, or database commit must not leave partially changed profile/role/audit state when rollback is confirmed. Do not catch a persistence exception and continue within a rollback-only transaction.

All assignment writers must lock the target user first. Follow authentication's user-before-credential lock ordering if a future operation needs credential locks; these CRUD mutations do not edit credentials. Acquire multiple user locks in a deterministic ID order if needed, and role locks after user locks. Use bounded lock timeouts and map retryable lock/concurrency conflicts consistently.

The current schema has no version column: serialize mutations using row locks. Successful sequential full updates have last-writer-wins semantics; do not promise stale-form detection. Do not add an undeclared version/ETag contract. If a connection fails during commit, reconcile the current resource before retrying; do not claim a guaranteed rollback when the commit outcome is unknown.

## 14. Redis and authentication-session integration

**CRUD never creates refresh sessions, refresh tokens, access tokens, or automatic login.** Reads and ordinary profile/department updates do not mutate Redis auth-session records. Reuse existing session abstractions; do not create a second session store.

Specification 05 currently stores individually keyed sessions and explicitly requires no secondary per-user index. Therefore bulk deletion of every session for a target user is **not guaranteed by that baseline design**.

- If the implementation already supports a reliable per-user session index, deletion and transitions to `INACTIVE`, `LOCKED`, or `SUSPENDED` must invalidate every indexed session for the target after the database commit. Include effective role replacements as described above. Use the existing revocation primitive and account for concurrent login/refresh according to its documented guarantees.
- Never substitute the caller's session ID for the target user's sessions. Do not use Redis `KEYS`, namespace flushing, or request-time keyspace scans to discover them. Do not add PostgreSQL refresh-token tables.
- Without indexing, document that physical session keys remain until expiry/logout. Existing authentication must recheck current database `is_deleted`, status, active roles, and credential revision on every protected request and refresh. Deleted/non-active users remain denied despite remaining keys; removed roles cannot authorize requests.
- **Reactivation limitation:** status denial alone does not permanently revoke old sessions. If a user is reactivated before its old keys expire, those sessions may become usable again when all authentication checks pass. Do not claim “all devices signed out” without indexed revocation or another already supported durable revocation mechanism. Do not mutate passwords/credential timestamps merely to simulate session revocation.
- Redis and PostgreSQL do not share a transaction. After-commit cleanup failures do not undo committed status/deletion. Record safe operational failures and use existing retry infrastructure if available; do not claim reliable retry delivery when none exists. Database checks remain the immediate denial mechanism. The reactivation limitation also applies to incomplete cleanup.
- If Redis is unavailable during initial caller session validation, fail closed with the existing `503` authentication-dependency error before any database mutation. Missing/revoked caller sessions produce `401`, distinct from infrastructure failures.

No guarantee is made to cancel an already executing request authorized before the state change. Test denial on subsequent authorization checks. Preserve existing authentication behavior and explicitly record whether the deployed project supports per-user indexed revocation.

## 15. Feature-based implementation responsibilities

Extend equivalent existing types rather than adding duplicate classes. Paths below are relative to the configured Java base package:

```text
feature/user/
  controller/AdminUserController.java
  dto/request/UpdateAdminUserRequest.java
  dto/request/UpdateAdminUserStatusRequest.java
  dto/request/UpdateAdminUserRoleRequest.java
  dto/request/AdminUserListQuery.java
  dto/response/AdminUserSummaryResponse.java
  dto/response/AdminUserDetailResponse.java
  dto/response/AdminUserRoleResponse.java
  dto/response/AdminUserPageResponse.java
  mapper/AdminUserMapper.java
  service/AdminUserService.java
  service/impl/AdminUserServiceImpl.java
  exception/                         # User-feature domain exceptions
feature/authentication/
  entity/                            # Existing four normalized entity mappings
  repository/                        # Existing repositories/projections/locking
  service/                           # Narrow account-management and session ports
  service/impl/                      # Transactional persistence implementation
security/                            # Existing shared JWT/principal/authorization
exception/                           # Existing global advice and API errors
config/                              # Existing shared configuration
```

| Layer | Responsibility |
| --- | --- |
| Controller | Exact route/method mappings, validated DTO/query binding, trusted principal access, response/status selection; no repository calls |
| User service | Method authorization, normalized commands, orchestration through the owning account-management port, safe mapping; no authentication token issuance |
| Authentication-owned account-management service | Transaction boundary, eligibility rechecks, locks, business invariants, duplicates, assignment replacement, audit values, after-commit session integration |
| Existing repositories | Non-deleted reads/counts, batch role projections, existence/duplicate checks including deleted rows, locked mutation lookups; no HTTP behavior |
| Mapper | Explicit allowlisted projection to DTOs, UTC conversion, stable role order; no secret mapping, database calls, or lazy loads outside transactions |
| Feature exceptions | Typed domain failures such as missing user, invalid role, duplicates, prohibited self-change |
| Global exception advice/security handlers | Consistent safe error serialization and status codes, including authentication/filter-layer errors |

Authentication-owned ports should accept their own commands/results or shared neutral values, not import `feature.user` HTTP DTOs. This preserves dependency direction. Keep shared Redis/security infrastructure outside business features; no root-level business controller/service/repository packages.

## 16. Errors and exception handling

Reuse existing `ApiError` and security error handling. Use one established validation code consistently; examples use `VALIDATION_FAILED` as in specification 08. Never expose passwords, hashes, tokens, credential metadata, SQL, stack traces, or rejected secret values.

```json
{
  "timestamp": "2026-09-20T08:30:00Z",
  "status": 409,
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email is already in use.",
  "path": "/api/v1/admin/users/42",
  "fieldErrors": [
    {"field": "email", "message": "Must be unique."}
  ]
}
```

Include the existing trace/correlation ID if supported. Only authorized callers receive target-specific conflicts.

| HTTP | Cases |
| --- | --- |
| `200` | List/detail/update/status/role success, including valid no-ops |
| `204` | Successful/idempotent soft delete, empty body |
| `400` | `VALIDATION_FAILED`: malformed JSON, invalid IDs/types/enums, missing/unknown fields, unsupported query/sort, incompatible filters; `INVALID_ROLE` for unusable role selection |
| `401` | Missing/invalid/expired JWT, revoked/missing session, or caller no longer eligible under authentication rules; include `WWW-Authenticate: Bearer` |
| `403` | `FORBIDDEN` for insufficient current authority; `PASSWORD_CHANGE_REQUIRED` for restricted callers |
| `404` | `USER_NOT_FOUND`: missing user or deleted target for detail/update; never-existing delete target |
| `409` | Duplicate identifier codes; `SELF_ACCOUNT_CHANGE_NOT_ALLOWED`; `CONCURRENT_MODIFICATION` for retryable lock/concurrency conflicts |
| `405` | Unsupported method; use standard framework handling |
| `415` | Unsupported content type |
| `429` | Existing rate-limit policy, with appropriate `Retry-After` when applied |
| `503` | Database/Redis dependency unavailable; reuse `AUTH_SERVICE_UNAVAILABLE` for authentication-path failures and the established service-unavailable code elsewhere |
| `500` | Unexpected internal/integrity failure with a generic safe message |

Translate known unique-constraint failures after transaction failure, including failures surfaced at commit. Ensure error handling covers both MVC advice and security filters. Do not return a success-shaped body for a failed transaction or a false database-failure response for best-effort cleanup after commit.

## 17. Testing matrix

Use deterministic clocks for audit assertions, actual PostgreSQL migrations for constraints/locking/queries, and real Redis integration for session behavior. Mocks alone do not establish concurrency or revocation guarantees. Preserve the existing Create Admin User regression suite without redefining its contract.

| Area | Cases | Expected result |
| --- | --- | --- |
| Authorization | Missing/expired token; revoked session; ADMIN/ACADEMIC_ADMIN; forced-change Super Admin; direct proxied service invocation | Correct `401`/`403`; no disclosure or writes |
| Caller dependencies | Redis/database unavailable during authorization | `503`, no mutation or fallback |
| List defaults | No parameters; empty dataset; out-of-range page | Correct defaults/metadata; `200` and safe DTOs |
| Query validation | Negative page, size 0/101, overflow, unknown query, invalid sort/type, conflicting department filters | `400` |
| Search/filter | Whitespace, Unicode, mixed case, literal `%`/`_`, combined status/role/department filters | Correct AND/OR/literal semantics |
| Pagination/joins | Multi-role and zero-role users; inactive roles; equal sort values | One row per user, accurate totals, deterministic ordering, all assigned roles, bounded query count |
| Soft-delete reads | Deleted target; deleted users with matching search/roles | Excluded from list/count; detail/update `404` |
| Full update | All fields; missing required key; null required field; unknown audit/password property | Valid change or `400`; no mass assignment |
| Nullable fields | Clear phone/department, positive unverified department, negative/overflow department | Correct null persistence; no Department query/FK |
| Uniqueness | Unchanged identifiers; another live/deleted user's values; simultaneous conflicting updates | Self excluded; others `409`; constraints preserve atomicity |
| Case semantics | Email/employee-ID case variations and exact-match login | Match existing ordinary uniqueness/login semantics; search behavior remains separate |
| Roles | Missing/inactive/unsupported role; same selection; multi-role replacement; inactive assignment cleanup | Validation/no-op/exact-one-set rules and audit preserved |
| Role races | Concurrent assignment changes or role deactivation | Serialization and locked validation; no duplicate/partial assignment set |
| Status | All four states/transitions, same-state request, unknown input, activation with temporary credential lock | Correct eligibility; no-op audit; credential state unchanged |
| Self protection | Self-delete, effective status/role changes, profile-only self-update | `409` for prohibited changes; permitted profile update succeeds |
| Audit | Spoofed actor; profile/role/status mutation; no-op; soft delete | Trusted updater/assigner, preserved creator, correct UTC times |
| Delete | First delete, repeated delete, absent ID, attempted restore | `204`, unchanged repeated audit, `404`; no hard deletion |
| Transaction rollback | Failure after profile write/before role insert; unique violation at flush/commit | No partial committed profile/role/audit changes |
| Concurrency | Update vs delete; concurrent full updates; lock timeout | Serialized state, deleted targets unavailable, documented last-writer-wins/conflict behavior |
| Credentials/secrets | Every success/error DTO and logs; before/after credential row comparison | No credentials/secrets returned or modified |
| Sessions with indexing | Target has multiple devices; deactivate/delete/role change; cleanup failure | All indexed sessions revoked when successful; no caller-session mix-up; committed DB state survives cleanup failure |
| Sessions without indexing | Deactivation/deletion with existing tokens; reactivation before expiry | Subsequent requests/refresh denied while ineligible; documented possible reuse after reactivation |
| Session creation | Every remaining CRUD route | No token issuance or refresh-session creation |
| End-to-end | Existing creation → list → detail → update → status/role changes → soft delete | Safe consistent contracts, persistence, audit, and authentication behavior |

## 18. Implementation order

1. Read repository instructions and specifications 02/04/05/07/08; inspect actual existing controller, DTO, entity, repository, security, and exception types. Confirm create is complete and preserve its behavior.
2. Confirm exact column/constraint mappings, status enum, case-sensitive identifier rules, and nullable department scalar. Record whether per-user session indexing actually exists.
3. Extend authentication-owned repositories with safe projections, distinct user pagination/counts, duplicate checks, and locked lookups. Preserve entity ownership and migrations.
4. Add list/detail DTOs and explicit safe mapper; implement and validate GET list/detail first.
5. Add update/status/role DTO validation and shared normalization. Define typed errors and map them through existing advice.
6. Implement atomic profile/status/role mutation and trusted audit logic; prove rollback, no-op behavior, duplicate races, and self-change protection.
7. Implement soft delete and deleted-row exclusion across all ordinary paths, including counts and security checks.
8. Integrate after-commit session invalidation if the existing index supports it; otherwise document the precise non-indexed limitation and verify current database eligibility checks.
9. Extend the existing controller/service with the specified routes and method authorization; keep completed creation untouched.
10. Run the testing matrix and existing creation/authentication regressions. Verify no credentials in responses/logs, no schema drift, and no refresh-session creation. Report actual results and limitations when implementation is performed.

## 19. Acceptance criteria

- [ ] All remaining endpoints use `/api/v1/admin/users` and the specified HTTP methods; the existing creation contract is unchanged.
- [ ] List supports validated pagination, literal search, combined filters, allowlisted stable sorting, accurate totals, and duplicate-free role joins.
- [ ] Detail returns a safe user profile with role assignments and audit values; deleted/missing users return `404`.
- [ ] Full update and supporting status/role routes share business rules and cannot partially commit profile/role changes.
- [ ] Nullable `departmentId` can be cleared and remains a scalar with no FK or Department dependency.
- [ ] Duplicate checks exclude the current user but include deleted rows, preserve exact-case semantics, and map database races to `409`.
- [ ] Role replacement results in exactly one validated active supported role; preserved/new assignment audit fields follow this contract.
- [ ] Status transitions preserve credentials and correctly distinguish administrative status from temporary login lockout.
- [ ] Soft delete sets `is_deleted = true`, preserves related data/unique identifiers, and excludes deleted users from ordinary queries and authentication.
- [ ] Creator audit values are immutable; updater/assigner IDs come from the authenticated admin; unchanged operations preserve timestamps.
- [ ] All routes require current Super Admin authority and reject restricted callers; self-delete/status/role protections are enforced.
- [ ] Passwords, credential fields, tokens, and session data never appear in these response DTOs; no CRUD operation creates refresh sessions.
- [ ] Indexed revocation runs when available; non-indexed/incomplete-cleanup limitations, especially reactivation, are documented and tested.
- [ ] Shared exception handling, feature ownership, transaction boundaries, and safe dependency-failure behavior follow existing architecture.
- [ ] Applicable tests pass against actual PostgreSQL/Redis behavior; completion reports distinguish executed checks from remaining work.

## 20. End-to-end CRUD workflow

1. An eligible Super Admin signs in through the existing authentication flow and uses its access token; this is separate from user CRUD.
2. The already completed Create Admin User operation creates the new account according to specification 08. Use its returned ID; do not change its implementation in this continuation.
3. The admin opens the user list. GET `/api/v1/admin/users` returns the new non-deleted user; search, filters, and pagination locate it without duplicate rows from role joins.
4. The admin selects the user. GET `/api/v1/admin/users/{id}` returns profile, nullable department ID, role assignment details, status, and audit fields without credentials.
5. The admin submits all editable fields to PUT `/api/v1/admin/users/{id}`. The backend validates uniqueness/role/status, atomically updates profile and assignments, and returns refreshed detail after commit.
6. For a standalone role change, PUT `/api/v1/admin/users/{id}/role` replaces the assignment set with the selected role and records any new assignment's trusted assigner. Current authorization reflects database roles on subsequent requests.
7. PATCH `/api/v1/admin/users/{id}/status` with `INACTIVE` disables the account. Subsequent protected requests and refresh are denied; indexed sessions are invalidated when supported. A later change to `ACTIVE` restores only status eligibility and carries the documented old-session limitation if bulk revocation was unavailable/incomplete.
8. DELETE `/api/v1/admin/users/{id}` soft-deletes the target and returns `204`. Credentials and assignments remain stored; identifiers remain reserved; supported session cleanup runs after commit.
9. Refreshing the list excludes the deleted user, detail/update return `404`, and authentication remains denied. Repeating DELETE returns `204` without changing audit history. Restore and credential management remain outside this workflow.
