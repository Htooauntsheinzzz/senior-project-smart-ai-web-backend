# Smart University Student Assistant Backend

Infrastructure setup follows `Agent.md`,
`spec-md/01-docker-postgresql-backend-redis-setup.md`,
`spec-md/02-project-structure.md`, `spec-md/03-rsa-setup.md`, and the authentication
specifications. Implementation decisions are recorded in
`spec-md/00-changes-and-decision.md`.

## Requirements

- Docker with Docker Compose v2 or newer
- Java 21 when running Maven or the backend locally (Maven Wrapper is included)

## Environment

The local `.env` file contains the PostgreSQL and Redis credentials specified in
the setup specification. It is ignored by Git and excluded from Docker builds.
For a fresh checkout, copy `.env.example` to `.env` and fill in the passwords.
Keep values in plain `KEY=value` format without shell quotes, since both Compose
and Spring Boot read this file. Run commands from this project directory.

## RS256 keys

The backend loads a matching RSA key pair on startup using:

```dotenv
JWT_PRIVATE_KEY_PATH=./keys/private.pem
JWT_PUBLIC_KEY_PATH=./keys/public.pem
```

These are filesystem paths, relative to the project directory for local runs,
or absolute paths. The private key must be unencrypted PKCS#8 PEM
(`BEGIN PRIVATE KEY`), and the public key must be X.509 SubjectPublicKeyInfo PEM
(`BEGIN PUBLIC KEY`). RSA keys must be at least 2048 bits.

For a fresh checkout, generate a local pair before starting the backend:

```sh
(
  umask 077
  mkdir -p keys
  test ! -e keys/private.pem && test ! -e keys/public.pem || exit 1
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out keys/private.pem &&
  openssl pkey -in keys/private.pem -pubout -out keys/public.pem
)
```

The command refuses to overwrite existing keys. Key files are ignored by Git
and excluded from Docker builds. Compose mounts the configured host files
read-only at `/app/keys/private.pem` and `/app/keys/public.pem`, and sets the
container's environment paths accordingly. Missing source files cause Compose
to fail rather than creating directories in their place.

The container's `spring` user must have read access to both mounted files.
On Linux hosts, provision ownership or a read ACL for that container user while
keeping the private key restricted. To check access after startup:

```sh
docker compose exec backend sh -c 'test -r /app/keys/private.pem && test -r /app/keys/public.pem'
```

`security/JwtKeyConfiguration.java` exposes a validated `jwtKeyPair` bean for
RS256 signing and verification. Startup fails for missing, unreadable, malformed,
undersized, or mismatched keys. Matching is verified by signing and verifying
with `SHA256withRSA` (the signature algorithm used by RS256).

## Start the complete stack

```sh
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

The same complete `smart-university` stack can also be started from
`frontend/smart-ai-admin-web`; its Compose file includes this project's Compose
file and only overrides the Admin Web build context.

The backend is available at `http://localhost:8080`, and the Admin Web frontend
is available at `http://localhost:3000`. PostgreSQL is exposed on
`localhost:5432` and Redis on `localhost:6379`. Host ports can be changed in
`.env`; containers always communicate using the internal service names and ports.
The `admin-web` container builds from `../../frontend/smart-ai-admin-web` and
starts after the backend. Its browser-facing backend URL is configured with
`VITE_API_BASE_URL`; keep this as `http://localhost:8080` for local Docker use
because browser JavaScript cannot resolve Docker's internal `backend` hostname.
Authentication endpoints are available under `/api/v1/admin/auth`: `/login`,
`/refresh`, `/logout`, `/me`, and `/change-password`. All unconfigured routes
are denied. Super Admins with full sessions manage administrative users at
`/api/v1/admin/users`: create (`POST`), list with search/filter/sort/pagination
(`GET`), detail (`GET /{id}`), full update (`PUT /{id}`), status change
(`PATCH /{id}/status`), role replacement (`PUT /{id}/role`), and soft delete
(`DELETE /{id}`). Flyway migration V7 creates the initial development Super Admin,
credential, and role assignment. The account must change its password before
accessing business endpoints. Flyway records the migration, so subsequent startup
does not duplicate or reset the account. The migration contains only a BCrypt hash,
not the plaintext password.

Compose waits for PostgreSQL and Redis health checks before starting the backend.
Flyway runs automatically on backend startup. V1 is the infrastructure baseline;
V2-V6 create and seed the authentication schema. Hibernate validates that schema
and never generates it. Add future migrations under
`src/main/resources/db/migration`; do not edit migrations already applied.

## Authentication configuration

Authentication settings are environment-driven. `.env.example` contains local
defaults for issuer, audience, key ID, token/session lifetimes, BCrypt cost,
lockout, CORS, timeouts, Redis namespace, and abuse limits. Use a distinct
`AUTH_REDIS_KEY_PREFIX` per environment. Login trims only surrounding email
whitespace and remains case-sensitive; passwords are never trimmed or normalized.

Access tokens last at most 15 minutes. Refresh sessions have a fixed seven-day
absolute lifetime, or ten minutes when password change is required. Clients must
serialize refresh calls and must log in again after a refresh timeout or lost
response; retrying an old token can trigger replay revocation. Token responses use
JSON and `Cache-Control: no-store`; browser refresh-token storage requires a
separate production design. Authentication cookies are not used.

For production, activate the `prod` profile and provide deployment-specific
database/Redis secrets, HTTPS CORS origins, Redis TLS, an isolated auth namespace,
and externally mounted RSA keys. The production profile rejects development
defaults. Redis session data requires `noeviction`; stale authentication data must
not be restored after an uncertain failover. Configure trusted proxy handling and
gateway connection/body/rate limits explicitly; the application does not trust
forwarded client-IP headers.

## Create an administrative user

The initial Super Admin must change its seeded password before it can use the
create-user endpoint. In Postman, first call `POST /api/v1/admin/auth/login`:

```json
{
  "email": "smartairsu@rsu.ac.th",
  "password": "67smartaiP@ssw0rd"
}
```

Use the returned `accessToken` as a Bearer token for
`POST /api/v1/admin/auth/change-password`:

```json
{
  "currentPassword": "67smartaiP@ssw0rd",
  "newPassword": "replace with a new secure passphrase"
}
```

Log in again with the new password to obtain a full-session access token. Role
IDs are database identifiers; list the available administrative roles with:

```sh
docker compose exec postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, role_code, role_name FROM app_roles WHERE is_active ORDER BY id;"'
```

Then call `POST /api/v1/admin/users` with the full-session Bearer token and an
active `SUPER_ADMIN`, `ADMIN`, or `ACADEMIC_ADMIN` role ID:

```json
{
  "employeeId": "RSU-ADMIN-001",
  "phoneNumber": "+66 81 234 5678",
  "firstName": "Mali",
  "lastName": "Admin",
  "email": "mali.admin@rsu.ac.th",
  "departmentId": null,
  "accountStatus": "ACTIVE",
  "roleId": 2,
  "temporaryPassword": "temporary secure passphrase",
  "confirmPassword": "temporary secure passphrase",
  "forcePasswordChange": true
}
```

`accountStatus` defaults to `ACTIVE` and `forcePasswordChange` defaults to
`true` when omitted. The response never includes either password. The new user
can log in with the temporary password but, when forced change is enabled, may
only use the password-change flow until choosing a new password.

## Manage administrative users

All routes below require a full-session Super Admin Bearer token and return
`Cache-Control: no-store`. Restricted callers receive
`403 PASSWORD_CHANGE_REQUIRED`; non-Super Admins receive `403 ACCESS_DENIED`.

- **List:** `GET /api/v1/admin/users` with optional `page` (0-based, default 0),
  `size` (1–50, default 50), `search` (case-insensitive literal substring of
  employee ID, names, email; `%`/`_` are literal), `accountStatus`,
  `roleId`, `departmentId`, `departmentUnassigned`, and up to three `sort`
  entries as `field,direction` (fields: `id`, `employeeId`, `firstName`,
  `lastName`, `email`, `accountStatus`, `createdAt`, `updatedAt`; default
  `createdAt,desc` plus an `id,asc` tie-breaker). Example:
  `GET /api/v1/admin/users?page=0&size=20&search=mali&accountStatus=ACTIVE&sort=createdAt,desc`
- **Detail:** `GET /api/v1/admin/users/{id}` returns the profile, all role
  assignments (including inactive ones, each with `isActive`, `assignedBy`,
  `assignedAt`), and audit fields. Missing or soft-deleted users return
  `404 USER_NOT_FOUND`.
- **Full update:** `PUT /api/v1/admin/users/{id}` replaces the editable fields
  atomically and requires all eight keys (`employeeId`, `phoneNumber`,
  `firstName`, `lastName`, `email`, `departmentId`, `accountStatus`, `roleId`);
  `phoneNumber` and `departmentId` accept explicit null. Duplicate email or
  employee ID (including soft-deleted users) returns `409`. Unusable roles
  return `400 INVALID_ROLE`.
- **Status:** `PATCH /api/v1/admin/users/{id}/status` with
  `{"accountStatus": "ACTIVE|INACTIVE|LOCKED|SUSPENDED"}`. Non-active users are
  denied login and protected routes. Credentials and lockout metadata are never
  modified by this route.
- **Role:** `PUT /api/v1/admin/users/{id}/role` with `{"roleId": 2}` makes the
  assignment set exactly the selected active administrative role, preserving a
  kept assignment's original `assignedBy`/`assignedAt`.
- **Delete:** `DELETE /api/v1/admin/users/{id}` soft-deletes (sets
  `is_deleted`) and returns `204`. Repetition returns `204`; a never-existing ID
  returns `404`. Identifiers stay reserved and related rows are preserved.
- Self-delete and effective self status/role changes return
  `409 SELF_ACCOUNT_CHANGE_NOT_ALLOWED`; a self profile update that keeps
  status and roles unchanged is allowed.
- Session note: refresh sessions are individually keyed with no per-user index
  (decision D18), so these operations rely on per-request database rechecks
  instead of bulk session revocation. A reactivated user's old sessions may work
  again until they expire.

## Run the backend locally

```sh
docker compose up -d --wait postgres redis
./mvnw spring-boot:run
```

Spring Boot loads `.env` automatically from the working directory. Stop the
containerized backend first (`docker compose stop backend`) if it uses the same
HTTP port. Environment variables override values from `.env`.

## Verify infrastructure and run tests

```sh
docker compose exec postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT current_database(), current_user;"'
docker compose exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --user "$REDIS_USERNAME" -n "$REDIS_DATABASE" ping'
./mvnw test
```

Redis should return `PONG`. Redis uses the named ACL user from `.env`; the default
user is disabled. The test suite uses isolated PostgreSQL and Redis Testcontainers
and temporary test-only RSA keys. Docker must be running; developer databases are
not modified. Tests cover schema validation, JWT contracts, session replay,
concurrent lockout/refresh, authentication and admin user management HTTP
endpoints (create, list, detail, update, status, role, soft delete), dependency
outages, concurrency, transactional rollback, authorization, self-change
protection, and secret-safe responses. They also cover the Super Admin seed
migration and its idempotency. RSA startup-validation tests can run independently:

```sh
./mvnw -Dtest=JwtKeyConfigurationTests test
```

## Stop and persistence

```sh
docker compose down
```

PostgreSQL data and Redis append-only data are retained in named Docker volumes.
`docker compose down -v` deletes those volumes and all local database data.
PostgreSQL initialization credentials apply only when the data volume is empty;
changing `.env` alone does not change an existing database user's password.
