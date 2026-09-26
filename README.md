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

### First setup on Windows, macOS, or Linux

Install Docker Desktop with Linux containers (Windows: enable the WSL2 backend),
or Docker Engine with Compose v2 on Linux. Download **both** the backend and
frontend repositories. Compose cannot build frontend source that is not present.
The default layout is:

```text
SeniorProject/
  backend/
    senior-project-smart-ai-web-backend/  # Run Compose here
      compose.yaml
  frontend/
    senior-project-smart-ai-admin-panel/
      Dockerfile
      package.json
      package-lock.json
```

The backend folder name may differ. For any other frontend location, set
`FRONTEND_PATH` in the backend `.env`. Paths are relative to `compose.yaml`,
not to the terminal's previous directory. Windows absolute paths also work;
use forward slashes and no surrounding quotes:

```dotenv
FRONTEND_PATH=C:/Users/User/OneDrive/Documents/SeniorProject/frontend/your-frontend-folder
VITE_API_BASE_URL=/
```

In the backend directory, create your local environment file:

```powershell
# Windows PowerShell
Copy-Item .env.example .env
```

```sh
# macOS / Linux
cp .env.example .env
```

Keep an existing `.env` rather than overwriting it. Fill in PostgreSQL and Redis
passwords and check `FRONTEND_PATH`. Keep `VITE_API_BASE_URL=/` for the Compose
stack: Nginx forwards `/api/` to `backend:8080`, so browsers on other devices
do not mistakenly call their own localhost. Frontend-only Vite development can
still use `http://localhost:8080` instead.

Generate keys on a fresh device using Docker (no local OpenSSL needed):

```sh
docker compose run --rm keygen
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

Key generation refuses to overwrite either existing key. It uses the default
`./keys/private.pem` and `./keys/public.pem` paths and gives the private key to
the backend container's UID 999. Existing custom key paths must still be
provisioned separately. The first setup requires internet access for images,
Maven/npm dependencies, and OpenSSL installation inside the key-generation container.

Open `http://localhost:3000` for the frontend and `http://localhost:8080` for
direct backend/Postman requests. From another device on the same network, use
`http://<Docker-host-IP>:3000` and allow that port through the host firewall.
If you change `FRONTEND_PORT`, the same-origin API proxy follows it automatically.

For backend-only development when the frontend is not checked out:

```sh
docker compose up -d --build --wait postgres redis backend
```

`.gitattributes` preserves Linux line endings for container scripts, and the
backend Docker build normalizes `mvnw` even for older Windows checkouts.

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
credential, and role assignment. Fresh installations seed this development account
with `force_password_change = FALSE`. Flyway records the migration, so subsequent startup
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

Fresh installations allow the initial Super Admin to use the create-user endpoint
without a forced password change. In Postman, first call `POST /api/v1/admin/auth/login`:

```json
{
  "email": "smartairsu@rsu.ac.th",
  "password": "67smartaiP@ssw0rd"
}
```

If an existing account returns `forcePasswordChange: true`, or you want to change
your password, use the returned `accessToken` as a Bearer token for
`POST /api/v1/admin/auth/change-password`:

```json
{
  "currentPassword": "67smartaiP@ssw0rd",
  "newPassword": "replace with a new secure passphrase"
}
```

After changing the password, log in again to obtain a full-session access token. Role
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

## Manage faculties

Faculty endpoints live under `/api/v1/admin/faculties` and allow
`SUPER_ADMIN`, `ADMIN`, and `ACADEMIC_ADMIN` full-session tokens.

- **Create:** `POST /api/v1/admin/faculties`

  ```json
  {
    "facultyCode": "FAC-ENG",
    "facultyNameEn": "Faculty of Engineering",
    "facultyNameTh": "คณะวิศวกรรมศาสตร์",
    "isActive": true
  }
  ```

  `facultyCode` must match `^[A-Z0-9-]+$` (max 30); names max 255; `isActive`
  defaults to `true`; `facultyNameTh` is optional. Duplicate code or English
  name returns `409 FACULTY_CODE_ALREADY_EXISTS` /
  `FACULTY_NAME_ALREADY_EXISTS` (soft-deleted records stay reserved).
  Expected: `201 Created` with the created faculty (`createdBy` comes from the
  token, never the request body).
- **List:** `GET /api/v1/admin/faculties` with optional `page` (default 0),
  `size` (1–100, default 20), `search` (code/English/Thai names,
  case-insensitive literal), `status` (`ACTIVE`/`INACTIVE`), and up to three
  `sort` entries (`facultyCode`, `facultyNameEn`, `facultyNameTh`, `isActive`,
  `createdAt`, `updatedAt`, `id`; default `createdAt,desc` + `id,asc`).
  Soft-deleted records are excluded.
- **Detail:** `GET /api/v1/admin/faculties/{id}` → `200` or
  `404 FACULTY_NOT_FOUND` for missing/deleted records.
- **Update:** `PUT /api/v1/admin/faculties/{id}` with all four editable fields
  (`facultyCode`, `facultyNameEn`, `facultyNameTh`, `isActive`). Also used to
  activate/deactivate (`"isActive": false`).
- **Delete:** `DELETE /api/v1/admin/faculties/{id}` soft-deletes and returns
  `204`; repeating returns `404`.
- **Summary:** `GET /api/v1/admin/faculties/summary` returns
  `totalFaculties`, `activeFaculties`, `inactiveFaculties`, and
  `totalDepartments` (the number of non-deleted departments).

Faculty icons are frontend presentation only and are never stored or returned
by the backend. Faculty `departmentCount` is calculated from non-deleted
departments, including inactive departments. `studentCount` remains `0`.

## Manage departments

All six endpoints require a full-session Bearer token with `SUPER_ADMIN`,
`ADMIN`, or `ACADEMIC_ADMIN`. Responses are plain DTOs, with no `data` envelope.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/admin/departments` | 201 |
| GET | `/api/v1/admin/departments` | 200, paginated |
| GET | `/api/v1/admin/departments/{id}` | 200 |
| PUT | `/api/v1/admin/departments/{id}` | 200 |
| DELETE | `/api/v1/admin/departments/{id}` | 204, empty body |
| GET | `/api/v1/admin/departments/summary` | 200 |

Create an active Faculty first, then use its actual returned ID in `facultyId`:

```json
{
  "departmentCode": "DEPT-CE",
  "departmentName": "Computer Engineering",
  "facultyId": 1,
  "isActive": true
}
```

POST defaults omitted `isActive` to `true`. PUT requires all four fields and
can move the department to another active Faculty. Code must use uppercase
letters/digits/hyphens (maximum 30 characters); name maximum 255 characters.
Audit fields are server-controlled. IDs must be positive integers; strings
and fractional numbers are rejected.

Examples for Postman (Bearer authorization, no GET body):

```text
GET /api/v1/admin/departments?page=0&size=20
GET /api/v1/admin/departments?search=engineering&facultyId=1&status=ACTIVE
GET /api/v1/admin/departments?sort=departmentName,asc
GET /api/v1/admin/departments/summary
```

Pagination defaults to page 0 / size 20, with size 1–100. Sort supports
`id`, `departmentCode`, `departmentName`, and `createdAt`; default
`createdAt,desc` plus `id,asc`. Search matches code/name as a literal
case-insensitive substring. Unknown filter IDs yield empty lists. Deleted
departments are excluded from lists, detail, and summaries.

Responses include flat `facultyId`, `facultyCode`, and `facultyNameEn` fields.
`programCount` counts non-deleted programs, including inactive programs;
`courseCount` and `studentCount` remain zero. Summary
returns `totalDepartments`, `activeDepartments`, and `inactiveDepartments`.
Faculty counts update after Department creation, moves, and deletion.

Errors use the shared `ApiError` shape:
- `400 VALIDATION_ERROR`: invalid JSON, fields, IDs, or query parameters.
- `404 FACULTY_NOT_FOUND`: requested Faculty is missing/deleted.
- `400 INVALID_FACULTY`: requested Faculty is inactive.
- `409 DEPARTMENT_CODE_ALREADY_EXISTS`: code is reserved, including deleted records.
- `409 DEPARTMENT_NAME_ALREADY_EXISTS`: name is reserved within the selected Faculty.
- `404 DEPARTMENT_NOT_FOUND`: department is missing/deleted, including repeated DELETE.
- `409 DEPARTMENT_IN_USE`: any non-deleted user is assigned, including inactive users.
- `409 DEPARTMENT_HAS_PROGRAMS`: non-deleted Programs still belong to the Department.

Deletion never clears user assignments. Assignments from deleted users remain
historical references and do not block soft deletion. New user assignments to
deleted departments are rejected. Faculty/Department reads query PostgreSQL
directly; application Redis caching is not currently enabled (D22/D28).

## Manage programs and majors

The six `/api/v1/admin/programs` endpoints require a full-session Bearer token
with `SUPER_ADMIN`, `ADMIN`, or `ACADEMIC_ADMIN` authority. Create an active
Faculty and an active Department under it first; use their actual IDs:

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/admin/programs` | 201 |
| GET | `/api/v1/admin/programs` | 200, paginated |
| GET | `/api/v1/admin/programs/{id}` | 200 |
| PUT | `/api/v1/admin/programs/{id}` | 200 |
| DELETE | `/api/v1/admin/programs/{id}` | 204, empty body |
| GET | `/api/v1/admin/programs/summary` | 200 |

Create request (PUT requires the same required fields and `isActive`):

```json
{
  "programCode": "PRG-CE-BS",
  "programName": "Computer Engineering",
  "degreeLevel": "Bachelor of Engineering",
  "facultyId": 1,
  "departmentId": 3,
  "durationYears": 4,
  "totalCredits": 144,
  "isActive": true
}
```

**`degreeLevel` is free-text JSON String**, entered in a text box. It is
trimmed, required, and limited to 100 characters; arbitrary nonblank degree
names are supported. No enum, dropdown/list, or degree lookup table is used.
`programCode` is trimmed, uppercase letters/digits/hyphens, maximum 50;
`programName` is trimmed, maximum 255. `durationYears` and `totalCredits` are
optional positive integers. POST defaults missing `isActive` to `true`.
`facultyId` validates that the selected active, non-deleted Department belongs
to the selected active, non-deleted Faculty; only `departmentId` is stored in
`programs`. Audit fields are derived from the token, never accepted in JSON.

The list supports `page=0&size=20` (default page 0 / size 20, max size 100),
`search` (literal case-insensitive code/name substring), `facultyId`,
`departmentId`, `degreeLevel` (exact, case-insensitive String match), `status`
(`ACTIVE`/`INACTIVE`), and repeatable `sort=field,direction` (max three).
Sort fields: `id`, `programCode`, `programName`, `degreeLevel`, `durationYears`,
`totalCredits`, `createdAt`; default `createdAt,desc` then `id,asc`.

Responses use the existing plain DTO/page format, with flat Faculty/Department
names/codes, `degreeLevel` as a String, and UTC audit timestamps. The summary
returns `totalPrograms`, `activePrograms`, and `inactivePrograms`. A Department's
`programCount` now counts its non-deleted programs. Deletes are soft; repeating
DELETE or requesting a deleted Program returns `404 PROGRAM_NOT_FOUND`. The
database reserves codes and (Department, name, degree level) triples after
soft deletion. Known errors include `400 INVALID_PROGRAM_DEPARTMENT` for an
inactive or mismatched Department, `404 FACULTY_NOT_FOUND` /
`DEPARTMENT_NOT_FOUND`, and `409 PROGRAM_CODE_ALREADY_EXISTS` /
`PROGRAM_ALREADY_EXISTS`. Redis application caching remains disabled; PostgreSQL
is the source of truth. A Department cannot be soft-deleted while it contains
non-deleted Programs (including inactive Programs); move or delete those Programs
first.

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
