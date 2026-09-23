# 05 — Authentication Implementation Specification

**Project:** Smart University Student Assistant backend  
**Target file:** `spec-md/05-authentication-implementation.md`  
**Stack:** Spring Boot, Spring Security, JWT RS256, PostgreSQL, Redis, BCrypt  
**Prerequisites:** `02-project-structure.md`, infrastructure/key setup, and `04-authentication-flyway-migration.md`.

## 1. Scope and implementation rules

Implement login, access-token authentication, refresh-token rotation, current-session logout, current-user retrieval, and authenticated password change. Password change is included so accounts with `force_password_change = TRUE` have a complete supported flow. Public registration, forgotten-password recovery, OTP, MFA, role administration, user provisioning, and Department Management are outside this feature.

This is an implementation specification, not a claim that application code or tests already exist.

Before coding, inspect the applicable `AGENTS.md`, existing specifications, source/test tree, dependency configuration, migrations, key-loading infrastructure, Redis configuration, and exception conventions. Reuse the configured Java/Spring Boot versions and base package; do not upgrade the stack merely to implement this feature. Use compatible dependencies managed by the project's Spring Boot dependency management.

PostgreSQL is authoritative for identity, passwords, status, locks, and role assignments. Redis is authoritative for active authentication sessions and refresh-token state. Access JWTs are signed credentials, but each protected request also requires an active Redis session and current database account checks. This intentionally provides immediate session revocation on subsequent requests, with database and Redis availability required for authentication.

Do not change table names, column names, column types, constraints, or applied migrations. Do not add token tables, token columns, permission tables, version columns, or Department foreign keys. Use Hibernate schema validation, never schema generation.

## 2. Existing database mappings

Retain every attribute and constraint from specification 04. The following is the complete entity mapping inventory; that specification and the applied migrations govern lengths, defaults, uniqueness, and foreign keys.

| Entity / table | Existing attributes |
| --- | --- |
| `AppUser` / `app_users` | `id`, `employee_id`, `first_name`, `last_name`, `email`, `phone_number`, `department_id`, `account_status`, `is_deleted`, `created_by`, `created_at`, `updated_by`, `updated_at` |
| `AppRole` / `app_roles` | `id`, `role_code`, `role_name`, `description`, `is_active`, `created_at`, `updated_at` |
| `AppUserRole` / `app_user_roles` | `id`, `user_id`, `role_id`, `assigned_at`, `assigned_by` |
| `AppUserCredentials` / `appuser_credentials` | `id`, `user_id`, `password_hash`, `force_password_change`, `password_changed_at`, `failed_login_attempts`, `locked_until`, `created_at`, `updated_at` |

Mapping requirements:

- Use explicit `@Table` and `@Column` mappings, `Long` identity IDs, and `GenerationType.IDENTITY`.
- `AppUserRole` is an association entity with its own ID and assignment metadata; do not replace it with an implicit join table.
- Credential ownership is a unique user relationship. A user may lack a credential row; such a user cannot log in.
- Use lazy relationships, controlled fetch queries/projections, and no cascading deletes. Audit IDs may be scalar `Long` mappings to avoid unnecessary graph loading.
- `departmentId` is a nullable scalar `Long`, with no Department entity relationship or existence check.
- Keep `accountStatus` as a string or safely converted value. Only exact `ACTIVE` is eligible. `INACTIVE`, `LOCKED`, `SUSPENDED`, and unknown values deny authentication. Unknown stored values must not produce a server error through enum conversion.
- Use `LocalDateTime` interpreted as UTC for existing `TIMESTAMP WITHOUT TIME ZONE` columns, with explicit conversion through UTC to `Instant`. Inject a UTC `Clock`; use `Instant` for JWT and Redis timestamps. Set database/JDBC session conventions consistently to UTC.
- Update audit timestamps explicitly. Do not invent `last_login_at`. Authentication failures update credential timestamps; password change updates `password_changed_at` and `updated_at`.
- Never serialize entities directly or include password hashes in entity `toString`, equality diagnostics, DTOs, logs, or cache snapshots.

The database uses ordinary email uniqueness. Login trims surrounding email whitespace and performs exact case-sensitive matching against the stored email. Do not silently use case-insensitive matching or lowercase identifiers without a separate data-normalization decision. Never trim or normalize passwords.

## 3. Package and file structure

Use the existing base package; `com.smartuniversity` is illustrative. Singular package names are mandatory.

```text
src/main/java/com/smartuniversity/
├── config/
│   ├── RedisConfig.java
│   ├── AuthenticationProperties.java
│   └── ClockConfig.java
├── security/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── RsaKeyProvider.java
│   ├── JwtTokenService.java
│   ├── JwtAuthenticationConverter.java
│   ├── AuthenticatedUser.java
│   ├── SessionAccountGuard.java
│   ├── AccountSecurityReader.java
│   ├── AccountSecuritySnapshot.java
│   ├── AuthenticationSessionReader.java
│   ├── SessionSecuritySnapshot.java
│   ├── PasswordChangeAuthorizationManager.java
│   ├── RestAuthenticationEntryPoint.java
│   └── RestAccessDeniedHandler.java
├── exception/
│   ├── ApiError.java
│   ├── ApiErrorWriter.java
│   ├── GlobalExceptionHandler.java
│   └── AuthenticationInfrastructureException.java
└── feature/authentication/
    ├── controller/AuthenticationController.java
    ├── dto/
    │   ├── LoginRequest.java
    │   ├── RefreshTokenRequest.java
    │   ├── ChangePasswordRequest.java
    │   ├── TokenResponse.java
    │   ├── CurrentUserResponse.java
    │   └── RoleResponse.java
    ├── mapper/AuthenticationMapper.java
    ├── entity/
    │   ├── AppUser.java
    │   ├── AppRole.java
    │   ├── AppUserRole.java
    │   └── AppUserCredentials.java
    ├── repository/
    │   ├── AppUserRepository.java
    │   ├── AppRoleRepository.java
    │   ├── AppUserRoleRepository.java
    │   ├── AppUserCredentialsRepository.java
    │   ├── RefreshSessionRepository.java
    │   └── RedisRefreshSessionRepository.java
    ├── service/
    │   ├── AuthenticationService.java
    │   ├── CredentialVerificationService.java
    │   ├── RefreshSessionService.java
    │   ├── PasswordChangeService.java
    │   ├── RefreshSession.java
    │   ├── CredentialVerificationResult.java
    │   └── impl/
    │       ├── AuthenticationServiceImpl.java
    │       ├── CredentialVerificationServiceImpl.java
    │       ├── RefreshSessionServiceImpl.java
    │       ├── PasswordChangeServiceImpl.java
    │       ├── AccountSecurityReaderImpl.java
    │       └── AuthenticationSessionReaderImpl.java
    └── exception/
        ├── InvalidCredentialsException.java
        ├── InvalidRefreshTokenException.java
        ├── SessionRevokedException.java
        ├── PasswordChangeRequiredException.java
        └── PasswordPolicyException.java
src/main/resources/
├── application.yml
├── redis/auth/create-session.lua
├── redis/auth/rotate-refresh.lua
└── db/migration/                       # Existing migrations remain unchanged
src/test/java/com/smartuniversity/      # Mirror production ownership
src/test/resources/                    # Test-only keys and fixtures
```

Reuse existing equivalent classes instead of creating duplicates. Additional small internal types belong with their owner. Redis connection/template/script beans stay in top-level `config`; session persistence belongs to the authentication feature. JWT and HTTP security infrastructure stay in top-level `security`. Only top-level `exception` owns global advice and shared error serialization.

Shared security depends on the `AccountSecurityReader` and `AuthenticationSessionReader` ports, implemented by the feature. It does not call controllers or depend directly on feature repositories. Avoid circular bean dependencies.

## 4. Dependencies and service contracts

Use the project's compatible Spring Boot starters for web, validation, security, OAuth2 resource server, JPA, and Redis; PostgreSQL driver; existing Flyway support; and test dependencies for Spring Security, JUnit, PostgreSQL/Redis Testcontainers. JWT verification/signing uses Spring Security JOSE/Nimbus support; do not install multiple competing JWT stacks.

Spring's resource-server support separates JWT verification from application session authorization. Configure a decoder and validators explicitly. See [Spring Security JWT resource-server documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

Recommended business interface:

```java
public interface AuthenticationService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(RefreshTokenRequest request);
    void logout(AuthenticatedUser principal);
    CurrentUserResponse me(AuthenticatedUser principal);
    void changePassword(AuthenticatedUser principal, ChangePasswordRequest request);
}
```

| Component | Responsibility |
| --- | --- |
| Controller | Validate DTOs, obtain trusted principal, delegate, choose HTTP status and headers |
| Authentication service | Orchestrate credential checks, session creation/rotation, JWT issuance, and responses |
| Credential verification service | Transactional account/credential locking, BCrypt matching, durable attempt counters and lock decisions |
| Refresh session service | Secure random refresh tokens, token hashing, session lifetime policy, replay outcome translation |
| Refresh session repository | Atomic Redis create/rotate, read, delete; no HTTP or JWT logic |
| Password change service | Validate existing/new password, enforce account policy, update BCrypt hash and credential metadata atomically |
| Account security reader | Read current status, credential revision, forced-change flag, lock state, and active role codes from PostgreSQL |
| JWT token service | Sign access JWTs only; key selection, claim construction, time calculation |
| JWT converter / session guard | After signature validation, enforce session and current-account checks and create the trusted principal |
| Mapper | Explicit allowlisted entity/projection-to-DTO mapping; no security decisions |
| Global error handler / security handlers | Consistent safe JSON errors across MVC and the security filter chain |

Expose repository operations such as `findByEmail`, `findSecuritySnapshotById`, user/credential `findBy...ForUpdate`, and active role projection reads. Lock ordering is always user row, then credential row. Future status/password mutation services must follow the same order. Avoid lazy-loading queries outside service transactions; disable Open Session in View.

## 5. Passwords, account eligibility, and lockout

Use `BCryptPasswordEncoder` with configurable cost, initially 12, calibrated under production-like load. Use the encoder to match passwords; never hash with a new random salt and compare strings. Support existing valid BCrypt encodings and reject unsupported/corrupt hashes as generic authentication failure while recording an internal integrity alert.

For newly set passwords, require at least 15 Unicode code points, at most 72 UTF-8 bytes, no NUL, and a value different from the current password. Do not silently truncate or pre-hash. Login accepts legacy shorter passwords but enforces the BCrypt byte limit. Avoid arbitrary composition rules. BCrypt has a 72-byte input limit; see [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Account eligibility requires all of:

1. User exists and `is_deleted = FALSE`.
2. `account_status = ACTIVE`.
3. A usable credential row exists.
4. `locked_until` is null or no later than the current UTC time.

Temporary credential lock and permanent `account_status = LOCKED` are independent. Expiring `locked_until` never changes account status.

Defaults: lock after five failed password matches, for 15 minutes. Under a database transaction and row locks:

- If temporarily locked, reject without BCrypt matching, counter increment, or extending the lock.
- If a previous temporary lock expired, reset its counter and timestamp before evaluating the next password.
- Wrong password increments the nonnegative counter, capped at the threshold; reaching the threshold sets `locked_until = now + duration`.
- Correct password resets the counter to zero and clears `locked_until`.
- Inactive, deleted, missing, or credential-less accounts return the same external `401 INVALID_CREDENTIALS`. Unknown users perform a dummy BCrypt match at the configured cost to reduce timing differences.
- Failures for nonexistent accounts have no credential row to mutate. Add bounded IP/identifier throttling at the gateway to limit brute-force traffic and BCrypt load; never use raw email in throttle keys.

Persist failed-attempt updates even though the endpoint returns an authentication error. Implement credential verification in a separately proxied transactional service returning an outcome; throw the public exception after that transaction commits. Do not throw a rollback-triggering exception inside the transaction and lose the counter. Concurrent failure tests must prove exact threshold behavior.

Existing sessions also fail eligibility checks while the account is locked. Lock expiry can restore such sessions if they have not otherwise expired or been revoked.

## 6. RSA PEM keys and JWT contract

Load a PKCS#8 private key (`BEGIN PRIVATE KEY`) and X.509 SubjectPublicKeyInfo public key (`BEGIN PUBLIC KEY`) from configured Spring resource paths. Reuse existing setup where present. Support at least 2048-bit RSA, prefer 3072-bit for newly provisioned keys, and validate the pair with a startup sign/verify test. Fail startup for missing, malformed, mismatched, or weak keys. Never generate replacement keys on startup.

Mount keys read-only from secrets storage or external files; do not package production private keys in the JAR or Docker image. Environment variables contain file locations, not PEM contents. Never print key material. Example resource paths: `file:/run/secrets/jwt-private.pem` and `file:/run/secrets/jwt-public.pem`.

Use `NimbusJwtEncoder` with the private key and `NimbusJwtDecoder` with trusted public keys. Explicitly allow **RS256 only**, rejecting `none`, HMAC algorithms, and other algorithms. A configured `kid` identifies the signing key. For planned key rotation, deploy an allowlisted verification key set first, then switch the signing key; retain the previous public key for the maximum access lifetime plus skew. Never follow token-provided `jku`, `x5u`, filesystem paths, or arbitrary remote keys.

Access-token contract:

| Field | Requirement |
| --- | --- |
| Header `alg`, `typ`, `kid` | `RS256`, `JWT`, configured key ID |
| `iss` | Exact configured issuer |
| `aud` | Contains the exact backend audience |
| `sub` | User ID as a positive decimal string |
| `iat`, `nbf`, `exp` | Numeric dates; 15-minute maximum access lifetime, 30-second permitted clock skew |
| `jti` | Fresh cryptographically random identifier per JWT |
| `sid` | Random session ID, bound to Redis and the subject |
| `token_use` | Exactly `access` |
| `roles` | Distinct active role codes at issuance, without `ROLE_` prefix |
| `force_password_change` | Boolean snapshot at issuance |
| `session_mode` | `FULL` or `PASSWORD_CHANGE_ONLY` |

Do not put passwords, hashes, refresh tokens, credential revisions, email, or unnecessary personal data in a JWT. JWT payloads are readable by their holders.

Validate signature, algorithm, trusted key ID, issuer, audience, required claims and types, subject format, `sid`, `token_use`, expiration, not-before, issuance time, and maximum lifetime. Reject future `iat` beyond skew and `exp <= iat`. Cap `exp` at the session's absolute expiry. Redis expiration remains a hard session limit even during JWT clock skew.

## 7. Redis session model and naming

Use a separate environment namespace:

```text
susa:<environment>:auth:session:{<userId>}:<sessionId>
```

For example: `susa:dev:auth:session:{42}:aRandomSessionId`.

Braces are intentional Redis Cluster hash tags. All future multi-key operations for one user must use the same tag. Current create/rotate/delete operations use a single session key and require no scan or secondary index.

A refresh token has this versioned opaque format:

```text
rt1.<userId>.<sessionId>.<secret>
```

Generate the session ID with at least 128 random bits and the secret with at least 256 random bits from `SecureRandom`, Base64URL encoded without padding. User/session identifiers are routing information, not authentication proof. Validate canonical numeric/Base64URL syntax and bounded lengths before constructing keys; accept no arbitrary key fragments. Hash the complete canonical token with SHA-256 and store only its hexadecimal digest. BCrypt is for human passwords; high-entropy refresh secrets use SHA-256. Never store raw refresh tokens in Redis, PostgreSQL, telemetry, or logs.

Each session is a Redis hash:

| Field | Value |
| --- | --- |
| `schemaVersion` | `1` |
| `userId`, `sessionId` | Owner and session identifiers |
| `currentHash` | SHA-256 digest of the current refresh token |
| `credentialRevision` | SHA-256 of the current stored BCrypt hash; server-only fingerprint |
| `mode` | `FULL` or `PASSWORD_CHANGE_ONLY` |
| `createdAt`, `lastRotatedAt`, `absoluteExpiresAt` | UTC epoch milliseconds |
| `rotationCount` | Number of successful rotations |
| `used:<digest>` | `1` for each previously consumed refresh-token digest |

Normal session lifetime is seven days from login, fixed and non-sliding. A forced-change session lasts ten minutes. Set key expiry atomically to `absoluteExpiresAt`; never create a persistent session key. Refresh does not extend absolute expiry. Return remaining refresh lifetime in responses. Reads and access-token use do not extend TTL.

Store consumed hashes until the session expires to detect replay. Bound growth to 2,000 rotations per session; require fresh login when the cap is reached. Rate-limit refresh requests separately. Session loss means logout; no session reconstruction from JWTs or client tokens.

`credentialRevision` is a fingerprint of a salted password hash, not an additional database field. Every authenticated operation compares it to the current database credential hash fingerprint. A password change invalidates all old sessions without a Redis scan, even if their keys remain until TTL. Do not cache password hashes or this fingerprint in user-facing profile caches.

Redis is the authentication session store for this feature. Do not apply `@Cacheable` to login, refresh, token responses, password checks, or authorization decisions. For this first implementation, read current account/roles from PostgreSQL on each protected request; optional profile caching must be separately specified with invalidation before introduction.

## 8. Login flow — `POST /auth/login`

1. Validate email/password structure and request size. Apply abuse throttling.
2. Run credential verification with committed counter/lock updates as specified above.
3. On success, obtain current active role codes and a fresh credential/account snapshot. Create a `PASSWORD_CHANGE_ONLY` session if `force_password_change` is true; otherwise create `FULL`.
4. Generate session ID and refresh secret. Prepare session metadata, hash, credential revision, and absolute expiry.
5. Sign the access JWT before writing the session so signing failure leaves no active Redis session.
6. Create the Redis hash and absolute TTL using a Lua operation that first checks key nonexistence. Regenerate the random session ID on the extremely unlikely collision. Never overwrite an existing session.
7. Return tokens only after the session write succeeds. A final account change racing with issuance is still enforced by the database/session guard on first use.

If Redis fails, return `503 AUTH_SERVICE_UNAVAILABLE`; never issue usable tokens without confirmed session creation. A successful password match may already have reset counters in PostgreSQL; this is acceptable. Lost HTTP responses can leave unused Redis sessions until TTL; the token secrets must never be logged or recovered for the caller.

## 9. Refresh flow — `POST /auth/refresh`

This endpoint authenticates with the refresh token in its JSON body and does not require an unexpired access JWT.

1. Parse and validate the token, derive its hash, and load the addressed session.
2. Reject absent, expired, invalid-schema, or mismatched sessions with `401 INVALID_REFRESH_TOKEN`.
3. Load current PostgreSQL status, credentials, temporary lock, forced-change flag, and active roles. Reject ineligible users or a credential revision mismatch. Delete a stale session when feasible; never authorize it if deletion fails.
4. Preserve restricted mode. A restricted session must never upgrade to full access through refresh. If the database now requires a password change, restrict the effective session and the newly issued token.
5. Generate a new refresh secret and access JWT using the same session ID. Set the JWT expiry to the smaller of access TTL and remaining session lifetime.
6. Atomically run the rotation script using the presented hash, replacement hash, expected credential revision/mode snapshot, and current time. Never implement read/compare/write as separate Redis calls.
7. Return the newly prepared token pair only after the script reports success. Re-read/reject or return a safe error if a concurrent mutation invalidates expected session metadata.

Rotation script behavior, in this exact priority order:

```text
missing session or expired absolute lifetime -> INVALID
presented hash exists as used:<digest> -> delete session; REPLAY
presented hash differs from currentHash -> INVALID (do not revoke)
expected metadata differs -> CONFLICT (do not rotate)
rotation cap reached -> delete session; REAUTHENTICATE
otherwise:
  record used:<oldHash> = 1
  replace currentHash with newHash
  update lastRotatedAt, mode, and rotationCount
  preserve absoluteExpiresAt and its expiry
  return ROTATED
```

Validate all script inputs and key field types before any writes. Redis scripts execute atomically with respect to other commands, but script errors do not provide rollback for earlier writes; arrange validation first and use bounded, known commands. See [Redis Lua scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/).

A confirmed replay revokes the entire session, including its current refresh token and access tokens on subsequent requests. An arbitrary incorrect secret must not revoke a victim session. Two simultaneous uses of one valid refresh token produce at most one rotation; the second triggers replay and revokes that session. Clients must serialize refresh attempts. A timeout or lost response after rotation has an ambiguous outcome: do not automatically retry the old token; require fresh login. No replay grace period is part of this specification.

Successful refresh keeps older unexpired access JWTs valid for the same session, unless the session is revoked, account policy changes, or credentials change.

## 10. Protected requests, roles, and `/auth/me`

For every protected request:

1. Spring Security extracts the Bearer access token and verifies the JWT.
2. The session guard loads the key derived from verified `sub` and `sid`; checks owner, expiry, and session mode.
3. The account reader loads current user eligibility, credentials, forced-change flag, and active role assignments from PostgreSQL.
4. Compare credential revision to the session fingerprint. Reject stale credentials, missing credentials, soft deletion, inactive status, or current temporary locks.
5. Build `AuthenticatedUser` from trusted verified identifiers and the current database snapshot.
6. Enforce effective forced-change restrictions, then endpoint/method authorization.

Map active `role_code` values to `SimpleGrantedAuthority("ROLE_" + roleCode)`. Store/return `SUPER_ADMIN`, `ADMIN`, and `ACADEMIC_ADMIN` as unprefixed codes. Use `hasRole("ADMIN")` or `hasAuthority("ROLE_ADMIN")` consistently. Never use display names as authority identifiers or double-prefix values.

JWT role claims are issuance snapshots; **current database roles** determine effective authorities. Removed/inactive roles stop granting access on subsequent requests. No implicit role hierarchy is enabled; endpoints requiring multiple roles must list them explicitly. A user with zero active roles can authenticate and use self-service endpoints but has no administrative authority.

`GET /auth/me` returns the current database profile, active roles, and effective forced-change status for the authenticated subject. Never accept a user ID query parameter as the identity. Do not return credential data, lock counters, token hashes, or session internals.

Session revocation/account changes apply to requests whose checks occur after the change. A request already authorized may complete; high-risk future operations must recheck eligibility within their business transaction where required.

## 11. Logout — `POST /auth/logout`

Require a valid Bearer JWT and current account/session checks. Derive the target session exclusively from the principal; no user-supplied session ID or arbitrary logout target is accepted. Delete that session key and return `204` only after Redis confirms the operation.

Deletion revokes the refresh token and all JWTs bound to that session. Other device sessions remain active. No separate JWT denylist is needed because protected requests require the session key.

A repeated call with the now-revoked access token returns `401`; the underlying delete operation is idempotent, but the authenticated HTTP endpoint does not bypass authentication. Redis failure returns `503`; do not report successful server-side logout. Clients should clear local credentials on logout regardless of the response, while recognizing that server revocation is unconfirmed after a failure.

## 12. Forced password change and password update

A forced-change login succeeds but issues a restricted session. Allow only `/auth/me`, `/auth/logout`, `/auth/change-password`, and refresh of that restricted session. All business endpoints return `403 PASSWORD_CHANGE_REQUIRED`, even for `SUPER_ADMIN`.

The restriction is effective when any of these is true: the current database flag, the session's restricted mode, or the access token's restricted-mode/forced-change snapshot. Never trust a false JWT flag to bypass a true database flag.

`POST /auth/change-password` requires a valid access token, current password, and new password. It works for both normal and restricted sessions:

1. Lock user then credential rows; revalidate account eligibility and session credential revision.
2. Match current password with BCrypt. Wrong current password returns generic `400 INVALID_CURRENT_PASSWORD`; apply the same durable failed-attempt and temporary-lock policy through a committed outcome.
3. Validate the new password policy and difference from current password.
4. Store a freshly salted BCrypt hash, clear `force_password_change`, set UTC `password_changed_at` and `updated_at`, reset failed attempts, and clear `locked_until` in one PostgreSQL transaction.
5. Commit, then best-effort delete the current Redis session. Return `204` after the database commit; no new tokens are issued. Require login with the new password.

All previous sessions fail their next credential-revision comparison, including refresh. Therefore a Redis cleanup failure after password commit does not undo the password change or leave old sessions authorized; report cleanup internally, without falsely reporting the password update as failed. If Redis is unavailable before initial session authentication, reject with `503` before changing the password.

Do not add a public reset endpoint or accept user IDs in this operation.

## 13. Endpoint and DTO contracts

Paths below are relative to any existing application context path. Use HTTPS and JSON. Do not silently add a second `/api` prefix.

| Method and path | Authentication | Success | Request |
| --- | --- | --- | --- |
| `POST /auth/login` | Public credentials | `200 TokenResponse` | `LoginRequest` |
| `POST /auth/refresh` | Refresh token | `200 TokenResponse` | `RefreshTokenRequest` |
| `POST /auth/logout` | Bearer access JWT | `204`, empty body | None |
| `GET /auth/me` | Bearer access JWT | `200 CurrentUserResponse` | None |
| `POST /auth/change-password` | Bearer access JWT | `204`, empty body; login again | `ChangePasswordRequest` |

Requests:

```json
{"email":"admin@university.example","password":"user-supplied-password"}
```

```json
{"refreshToken":"rt1.42.session-id.random-secret"}
```

```json
{"currentPassword":"current-password","newPassword":"new-long-passphrase"}
```

Example token response (values are illustrative placeholders):

```json
{
  "tokenType":"Bearer",
  "accessToken":"<signed-access-jwt>",
  "expiresIn":900,
  "refreshToken":"<opaque-refresh-token>",
  "refreshExpiresIn":604800,
  "forcePasswordChange":false,
  "user":{
    "id":42,
    "employeeId":"RSU-001",
    "firstName":"Example",
    "lastName":"Administrator",
    "email":"admin@university.example",
    "phoneNumber":null,
    "departmentId":null,
    "accountStatus":"ACTIVE",
    "forcePasswordChange":false,
    "roles":[{"code":"ADMIN","name":"Admin"}]
  }
}
```

`CurrentUserResponse` is the shape of `user` above. `RoleResponse` contains code/name. Durations are integer seconds remaining at response construction, never fixed constants when a session is near expiry. Restricted login returns `forcePasswordChange: true` and at most 600 seconds for both remaining session and access lifetime.

DTO validation:

- Email: nonblank, valid email syntax, maximum 255 characters after permitted trimming.
- Login/current password: nonempty, at most 72 UTF-8 bytes, no NUL. Do not expose rejected values in errors.
- New password: enforce section 5 through a dedicated validator, including byte-length checks rather than only `@Size`.
- Refresh token: nonblank, maximum 512 characters, strict canonical parser.
- Use request-body validation and bounded request sizes. Never bind requests onto JPA entities.

Responses containing tokens/profile data use `Cache-Control: no-store`; token responses also use `Pragma: no-cache`. Tokens are supplied in response JSON for this API contract. Clients use OS secure storage where available; browser clients keep access tokens in memory and must adopt a separately designed secure refresh storage strategy before browser production deployment. This specification does not silently mix JSON refresh tokens with cookie authentication.

## 14. Security configuration

Configure a `SecurityFilterChain`, `@EnableMethodSecurity`, and `SessionCreationPolicy.STATELESS`. Do not create HTTP sessions or use Spring Session Redis for this token-session design. Disable form login, HTTP Basic, default Spring logout, and request caching.

Allow anonymous access only to exact `POST /auth/login`, exact `POST /auth/refresh`, necessary CORS preflight, and explicitly approved minimal health endpoints. Other authentication paths require authentication. Protected business routes require their explicit authorities; deny unconfigured routes by default. Do not use a blanket `/auth/**.permitAll()` rule. Swagger/OpenAPI exposure is development-only unless separately authorized.

Public login/refresh requests must not be blocked by an unrelated expired Bearer header: use a narrowly scoped public filter chain without resource-server processing or a Bearer-token resolver excluding those exact method/path combinations. Protected routes always process Bearer authentication.

Disable CSRF only for this explicit Bearer/JSON contract with no automatically submitted authentication cookies. If authentication cookies are later introduced, implement CSRF protection and cookie policy before enabling them. Configure CORS with explicit environment-supplied origins, allowed methods, and `Authorization`/`Content-Type`; no wildcard origins or credentialed cookies for this contract.

Implement forced-change checks before normal business authorization through an authorization manager or equivalent centralized rule. Use the framework's JWT verification rather than a second custom JWT parsing filter. Map dependency failures in the security path to `503` explicitly; do not let generic authentication exception wrapping turn infrastructure outages into misleading `401` responses.

## 15. Errors and Redis/database failure behavior

Use one error format from both global MVC advice and security handlers:

```json
{
  "timestamp":"2026-09-18T08:00:00Z",
  "status":401,
  "code":"INVALID_CREDENTIALS",
  "message":"Unable to authenticate with the supplied credentials.",
  "path":"/auth/login",
  "traceId":"<correlation-id>"
}
```

| Condition | HTTP/code |
| --- | --- |
| Invalid DTO or malformed request | `400 VALIDATION_ERROR` |
| Login wrong password, absent/deleted/inactive/locked user | `401 INVALID_CREDENTIALS` |
| Invalid/expired JWT or missing/revoked session | `401 UNAUTHORIZED` |
| Refresh expired, invalid, replayed, or no longer eligible | `401 INVALID_REFRESH_TOKEN` |
| Authenticated but missing authority | `403 FORBIDDEN` |
| Restricted session accessing business endpoint | `403 PASSWORD_CHANGE_REQUIRED` |
| Invalid new password | `400 PASSWORD_POLICY_VIOLATION` |
| Request throttled | `429 TOO_MANY_REQUESTS`, suitable `Retry-After` |
| Redis/database dependency timeout or unavailable | `503 AUTH_SERVICE_UNAVAILABLE` |

Include `WWW-Authenticate: Bearer` on protected-resource `401` responses. Never expose whether a login identifier exists, account status, stack traces, SQL, Redis keys, hashes, passwords, or tokens. Validation errors may identify fields without rejected secret values. Log safe reason codes and trace IDs internally.

Redis failure policy is fail closed:

- Login and refresh issue no tokens unless their Redis mutation is confirmed.
- Protected requests and `/auth/me` return `503` when session validation cannot run; do not accept JWTs on signature alone.
- Missing session keys return `401`, while network/timeout failures return `503`.
- Logout returns `503` when revocation cannot be confirmed.
- Do not fall back to local memory, accept stale cached authorization, or create a PostgreSQL refresh-token store.
- Use bounded connection/command timeouts, no unbounded retries, and no automatic retry of ambiguous refresh mutations.

PostgreSQL and Redis do not share an ACID transaction. Keep explicit boundaries and return tokens only after required operations succeed. Credential revisions and current database checks close password/status races on token use; do not claim that `@Transactional` rolls back Redis.

Use a dedicated authenticated Redis deployment/ACL namespace with TLS where appropriate, memory monitoring, and `noeviction` for session data. Configure persistence and recovery deliberately. Data loss forces affected users to log in again. Restoring stale session data can resurrect revoked tokens: never restore an old authentication namespace into service; clear it or change the shared namespace before reopening traffic. Redis asynchronous replication can lose an acknowledged deletion/rotation during failover; an uncertain failover requires session namespace invalidation and fresh login to preserve this specification's revocation guarantees.

Expose internal metrics for login outcomes, lock events, refresh replay, session failures, and latency without high-cardinality secret labels. Readiness includes required database/Redis dependencies; liveness checks process health rather than causing restart loops during dependency outages.

## 16. Configuration and environment

Merge into the existing `application.yml`; preserve project conventions. The example uses contemporary `spring.data.redis` names; verify names against the project's pinned Boot version. Bind `app.auth` with validated `@ConfigurationProperties`; durations are typed `Duration` values.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD}
      database: ${REDIS_DATABASE:0}
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:2s}
      timeout: ${REDIS_COMMAND_TIMEOUT:2s}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}

app:
  auth:
    jwt:
      issuer: ${JWT_ISSUER}
      audience: ${JWT_AUDIENCE}
      key-id: ${JWT_KEY_ID}
      private-key-path: ${JWT_PRIVATE_KEY_PATH}
      public-key-path: ${JWT_PUBLIC_KEY_PATH}
      access-token-ttl: ${JWT_ACCESS_TOKEN_TTL:15m}
      clock-skew: ${JWT_CLOCK_SKEW:30s}
    session:
      key-prefix: ${AUTH_REDIS_KEY_PREFIX}
      refresh-ttl: ${AUTH_REFRESH_TTL:7d}
      password-change-ttl: ${AUTH_PASSWORD_CHANGE_TTL:10m}
      max-rotations: ${AUTH_MAX_ROTATIONS:2000}
    password:
      bcrypt-strength: ${BCRYPT_STRENGTH:12}
      min-code-points: 15
      max-utf8-bytes: 72
    lockout:
      max-failed-attempts: ${AUTH_MAX_FAILED_ATTEMPTS:5}
      duration: ${AUTH_LOCK_DURATION:15m}
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

Example `.env.example` values, with secrets supplied locally/deployment-side:

```dotenv
DB_URL=jdbc:postgresql://postgres:5432/smart_university
DB_USERNAME=smart_university
DB_PASSWORD=REPLACE_WITH_SECRET
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=REPLACE_WITH_SECRET
REDIS_DATABASE=0
REDIS_SSL_ENABLED=false
JWT_ISSUER=smart-university-auth
JWT_AUDIENCE=smart-university-backend
JWT_KEY_ID=auth-key-1
JWT_PRIVATE_KEY_PATH=file:/run/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=file:/run/secrets/jwt-public.pem
AUTH_REDIS_KEY_PREFIX=susa:dev:auth
JWT_ACCESS_TOKEN_TTL=15m
AUTH_REFRESH_TTL=7d
AUTH_PASSWORD_CHANGE_TTL=10m
BCRYPT_STRENGTH=12
AUTH_MAX_FAILED_ATTEMPTS=5
AUTH_LOCK_DURATION=15m
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

The key prefix includes environment isolation and excludes the `:session:` suffix, which the repository appends. Redis Cluster uses database zero. Docker service hostnames work from containers; local execution must use reachable hostnames/ports. Production secrets, TLS, and origins differ from these development examples.

Spring Boot does not automatically load arbitrary `.env` files. Inject variables through the shell, IDE, deployment secret manager, or explicit Docker Compose `environment`/`env_file`; Compose substitution alone does not guarantee variables reach the application. Keep real `.env` and private keys out of source control and Docker build context.

Validate positive durations, lock threshold and rotation cap; supported BCrypt cost; nonempty issuer/audience/key ID/prefix/origins; and readable keys. Reject unsafe placeholder secrets in production. Configure UTC at the PostgreSQL role/session level as well as application conversion boundaries.

## 17. Required testing

Use deterministic clocks and real PostgreSQL/Redis containers for integration tests. Apply the existing Flyway migrations and Hibernate validation. Do not rely on H2 or Redis mocks for locking, expiration, Lua, or concurrency claims. Generated test keys must be test-only.

### Unit tests

- DTO validation, exact email matching, UTF-8 byte-length password boundary, no trimming, BCrypt verification, and corrupt hash handling.
- Every account status, unknown status, soft deletion, missing credentials, active/inactive role mapping, and duplicate authority removal.
- Lock expiry at the exact boundary, successful counter reset, threshold locking, and permanent status lock remaining after temporary expiry.
- Claims, algorithm allowlist, wrong issuer/audience/key, malformed subject/session ID, future issuance, missing claims, expiry, skew, and restricted mode.
- Refresh parsing/hashing, key construction, secret redaction, credential fingerprint invalidation, and response lifetime calculations.

### Database and Redis integration tests

- Mappings match all four existing tables; no extra columns/tables or migration checksum changes.
- Failed login increments survive endpoint failure and concurrent attempts do not lose increments.
- Successful login creates exactly one TTL-bound session with no plaintext refresh token.
- Create operation cannot overwrite an existing session. Every mutation preserves a finite absolute expiry.
- Refresh changes the secret/hash, records the old hash, preserves the session ID/lifetime, and rejects expiry and rotation-cap exhaustion.
- A previously consumed token revokes the session; an unknown wrong secret does not.
- Concurrent refresh yields at most one rotation and replay invalidates the resulting session; simulate lost responses and timeout ambiguity.
- Logout invalidates both refresh and current access tokens. Other sessions remain usable.
- Password change invalidates all sessions through credential revision, including when Redis cleanup fails after commit.
- Session data loss rejects old JWTs. Redis/database outages produce the specified `503`, with no fallback or token issuance.
- Role removal, inactive roles, status changes, soft deletion, temporary locks, and forced-change updates take effect on subsequent protected requests.

### HTTP/security tests

- Verify all five endpoint contracts, status codes, empty `204` bodies, no-store headers, and secret-free error bodies.
- Login and refresh work despite an unrelated stale Authorization header.
- `/auth/me` uses the authenticated identity and current data; users cannot select another profile.
- Missing/invalid Bearer tokens return `401`; insufficient authority returns `403`.
- Forced-change users cannot reach business routes, including through method-security calls, but can complete password change and log in again.
- Exact public matcher coverage, unsupported methods, denied unknown routes, CORS preflight, allowed/disallowed origins, and no HTTP session creation.
- Invalid PEM formats, mismatched keys, weak RSA, missing files, unknown key IDs, and invalid configuration fail safely.
- Scan logs and serialized DTOs for accidental secrets. Test trusted key rollover if multiple verification keys are supported.

## 18. Step-by-step implementation order

1. Inspect repository instructions, feature architecture, existing specifications, migrations, dependencies, configuration, and key setup. Record compatibility assumptions.
2. Confirm the four existing tables and seeded roles. Configure Hibernate validation and UTC handling; do not redesign migrations.
3. Add only missing compatible dependencies, validated properties, UTC clock, and shared Redis configuration.
4. Implement entities and repository projections/locking; verify against PostgreSQL migrations.
5. Implement safe DTOs, mapping, shared error format, global advice, and security error handlers.
6. Implement BCrypt policy and transactional credential verification; prove durable lockout counters before endpoint orchestration.
7. Implement PEM loading, startup key validation, encoder/decoder, and strict JWT validators.
8. Implement session/token models, secure random generation, hashes, bounded key parsing, and atomic Redis scripts; test TTL and concurrency.
9. Implement login and refresh orchestration, including failure boundaries and replay handling.
10. Implement current-account/session security ports, principal conversion, current role authorities, and centralized forced-change enforcement.
11. Wire the security filter chain with exact public endpoints, stateless behavior, CORS, and safe default denial.
12. Implement `/auth/me`, current-session logout, and password change with credential-revision invalidation.
13. Complete HTTP, failure, concurrency, and integration tests. Document local key/env setup and supported token transport.
14. Recheck package declarations, class ownership, imports, schema/migration invariants, and secret exclusions. Run the project build and required tests; report actual outcomes and any remaining blockers.

## 19. Acceptance criteria

- [ ] A complete implementation exists under the required feature architecture, with shared security/configuration/global exceptions in their top-level packages.
- [ ] Existing tables and migrations remain unchanged; `appuser_credentials` retains its exact name and `department_id` remains nullable without a foreign key.
- [ ] Valid credentials yield RS256 access JWTs and high-entropy refresh tokens; no usable pair is returned without confirmed Redis session creation.
- [ ] Private/public PEM loading, strict algorithm/claim validation, trusted key IDs, and startup key validation work.
- [ ] Redis stores only refresh-token digests with bounded replay history and finite absolute TTLs.
- [ ] Refresh rotation is atomic, does not extend absolute lifetime, detects consumed-token replay, and handles concurrent/ambiguous requests as specified.
- [ ] Logout revokes the current session and blocks its tokens on subsequent requests.
- [ ] `/auth/me` returns only the caller's current safe profile and active roles.
- [ ] Database account status, soft deletion, temporary locks, active roles, and credential changes are enforced on every protected request and refresh.
- [ ] Failed login counters persist, lockout is concurrency-safe, and authentication failures do not disclose account existence.
- [ ] Forced-change sessions remain restricted until a successful password change followed by fresh login.
- [ ] Password change uses BCrypt and invalidates every prior session without schema changes or Redis scans.
- [ ] Redis/database outages fail closed with consistent errors; no local fallback accepts stale authorization.
- [ ] Configuration is environment-driven, secrets are excluded from source/logs/artifacts, and browser/cookie assumptions are explicit.
- [ ] Integration, security, expiry, failure, and concurrency tests pass against real PostgreSQL and Redis; the final source tree follows specification 02.
