# 25 - Student Feature Complete Workflow Implementation Specification

## 1. Purpose

Implement the Student feature for the **Smart University Student Assistant App** after the Student Flyway migrations.

This specification covers:

```text
Admin Student CRUD
Student Credential Management
Mobile Registration
Mobile Authentication
RS256 JWT
Redis Refresh Sessions
Redis Authentication Cache
Student Profile Cache
Academic Relationship Validation
Explicit Response / Error Contracts
PostgreSQL Transactions
Concurrency Protection
Concrete DTOs
Concrete Database Schemas
Authoritative Student Account State Rules
```

Existing tables:

```text
students
student_credentials
```

---

# 1.1 Clear Implementation Order

Implement the Student feature strictly in the following order.

Do NOT begin Mobile Authentication before the Admin CRUD, credential model, registration state rules, and PostgreSQL transactions are working correctly.

```text
PHASE 1
Database Verification
        ↓
PHASE 2
Entity and Enum Layer
        ↓
PHASE 3
Repository Layer
        ↓
PHASE 4
DTO and Mapper Layer
        ↓
PHASE 5
Exception / Response Contracts
        ↓
PHASE 6
Academic Validation
        ↓
PHASE 7
Admin Student CRUD
        ↓
PHASE 8
Student Credential Management
        ↓
PHASE 9
Mobile Registration
        ↓
PHASE 10
Mobile Login
        ↓
PHASE 11
RS256 Student JWT
        ↓
PHASE 12
Redis Refresh Sessions
        ↓
PHASE 13
Refresh / Logout
        ↓
PHASE 14
Force Password Change
        ↓
PHASE 15
Student /me
        ↓
PHASE 16
Redis Profile / Auth Cache
        ↓
PHASE 17
Security Configuration
        ↓
PHASE 18
Transaction / Concurrency Tests
        ↓
PHASE 19
Integration Tests
        ↓
PHASE 20
Final Build / Verification
```

---

# 1.2 Phase 1 - Verify Database First

Before writing Java code:

```text
1. Verify students table

2. Verify student_credentials table

3. Verify Foreign Keys

4. Verify UNIQUE constraints

5. Verify CHECK constraints

6. Verify indexes

7. Verify Flyway history
```

Check:

```sql
\d students
```

and:

```sql
\d student_credentials
```

Confirm these relationships exist:

```text
students.faculty_id
→ faculties.id

students.department_id
→ departments.id

students.program_id
→ programs.id

students.semester_id
→ semesters.id

student_credentials.student_id
→ students.id
```

Do NOT continue implementation if the Java model would conflict with the actual database schema.

---

# 1.3 Phase 2 - Create Entity and Enum Layer

Create:

```text
Student.java

StudentCredential.java

StudentAccountStatus.java
```

Implement database mappings exactly as defined in this specification.

Do not create Controller or Service logic yet.

Verify the application starts successfully with:

```text
spring.jpa.hibernate.ddl-auto=validate
```

before continuing.

---

# 1.4 Phase 3 - Create Repository Layer

Implement:

```text
StudentRepository

StudentCredentialRepository
```

Required repository features:

```text
Student lookup

Student Code lookup

University Email lookup

Duplicate checks

Search/filter support

Summary counts

Credential lookup

Credential existence check

Registration Student row lock

Credential row lock
```

Implement the pessimistic-lock queries before Registration or Login logic.

---

# 1.5 Phase 4 - Create DTO and Mapper Layer

Create Admin DTOs first:

```text
StudentCreateRequest

StudentUpdateRequest

StudentPasswordResetRequest

StudentResponse

StudentListResponse

StudentSummaryResponse
```

Then Mobile DTOs:

```text
StudentRegisterRequest

StudentRegisterResponse

StudentLoginRequest

StudentLoginResponse

StudentRefreshRequest

StudentRefreshResponse

StudentChangePasswordRequest

StudentMeResponse
```

Then create:

```text
StudentMapper
```

Do not expose JPA entities through Controllers.

---

# 1.6 Phase 5 - Implement Exception and Response Contracts

Implement stable application exceptions and error codes before Controller implementation.

Required examples:

```text
STUDENT_NOT_FOUND

STUDENT_CODE_ALREADY_EXISTS

STUDENT_EMAIL_ALREADY_EXISTS

INVALID_STUDENT_ACADEMIC_RELATIONSHIP

INVALID_STUDENT_ACCOUNT_STATUS

INVALID_STUDENT_ACCOUNT_STATE

INVALID_STUDENT_UNIVERSITY_EMAIL

STUDENT_ALREADY_REGISTERED

STUDENT_REGISTRATION_NOT_ALLOWED

STUDENT_ACCOUNT_NOT_ACTIVE

STUDENT_ACCOUNT_LOCKED

INVALID_STUDENT_CREDENTIALS

PASSWORD_CONFIRMATION_MISMATCH

CURRENT_PASSWORD_INCORRECT

PASSWORD_CHANGE_REQUIRED

INVALID_REFRESH_TOKEN

AUTH_SESSION_UNAVAILABLE
```

Integrate them with the existing Global Exception Handler.

---

# 1.7 Phase 6 - Implement Academic Validation

Before Student CRUD:

```text
Validate Faculty

Validate Department

Validate Department belongs to Faculty

Validate Program

Validate Program belongs to Department

Validate Semester
```

Required:

```text
department.faculty.id == facultyId
```

and:

```text
program.department.id == departmentId
```

This validation should be reusable by both:

```text
Admin Create

Admin Update
```

---

# 1.8 Phase 7 - Implement Admin Student CRUD

Implement in this exact endpoint order:

```text
1. POST /api/v1/admin/students

2. GET /api/v1/admin/students

3. GET /api/v1/admin/students/summary

4. GET /api/v1/admin/students/{id:\d+}

5. PUT /api/v1/admin/students/{id:\d+}

6. DELETE /api/v1/admin/students/{id:\d+}
```

Start with:

```text
Create
```

because it also verifies:

```text
Student persistence

Credential persistence

Academic validation

Password hashing

Audit fields

Transactions
```

Then implement:

```text
List
Search
Filters
Pagination
Summary
Detail
Update
Soft Delete
```

Do NOT implement Mobile Registration until Admin CRUD works correctly.

---

# 1.9 Phase 8 - Implement Credential Management

Create:

```text
StudentCredentialService
StudentCredentialServiceImpl
```

Implement:

```text
Credential creation

BCrypt encoding

Credential existence

Password reset

Failed-login tracking

locked_until

force_password_change
```

Then implement:

```text
POST /api/v1/admin/students/{id:\d+}/reset-password
```

Verify password reset does NOT automatically change:

```text
INACTIVE → ACTIVE
```

or:

```text
SUSPENDED → ACTIVE
```

---

# 1.10 Phase 9 - Implement Mobile Registration

Only after Student/Credential persistence is stable.

Implement:

```text
POST /api/v1/student/auth/register
```

Required order:

```text
Validate request
        ↓
Normalize Student Code
        ↓
Normalize Email
        ↓
Validate exact @rsu.ac.th
        ↓
BCrypt Password
        ↓
Start Transaction
        ↓
Lock Student row
        ↓
Require PENDING
        ↓
Require no Credential
        ↓
Verify identity
        ↓
Create Credential
        ↓
PENDING → ACTIVE
        ↓
Commit
        ↓
Evict Redis caches
```

Test concurrency before implementing Login.

---

# 1.11 Phase 10 - Implement Mobile Login

Implement:

```text
POST /api/v1/student/auth/login
```

First implement PostgreSQL authentication only:

```text
Student lookup
        ↓
Deleted check
        ↓
ACTIVE check
        ↓
Credential lock
        ↓
locked_until check
        ↓
BCrypt verification
        ↓
failed_login_attempts management
```

Do not add Redis tokens until this database authentication works correctly.

---

# 1.12 Phase 11 - Implement Student RS256 JWT

Reuse the project's existing RS256 infrastructure.

Add Student JWT claims:

```text
sub
studentId
studentCode
roles
iat
exp
jti
```

Authority:

```text
ROLE_STUDENT
```

Do not create a Student role table.

Verify Student access token generation independently before implementing Refresh Sessions.

---

# 1.13 Phase 12 - Implement Redis Refresh Sessions

Implement:

```text
student:auth:session:<sessionId>
```

and:

```text
student:auth:sessions:<studentId>
```

Implement:

```text
Session creation

Refresh-token hashing

TTL

Multi-device session index

Invalidate one session

Invalidate all Student sessions
```

Do NOT implement profile caching before Refresh Sessions work correctly.

---

# 1.14 Phase 13 - Implement Refresh and Logout

Implement:

```text
POST /api/v1/student/auth/refresh
```

first.

Then:

```text
POST /api/v1/student/auth/logout
```

Refresh must verify:

```text
Redis Session
+
Refresh Token Hash
+
PostgreSQL Student State
```

before issuing tokens.

Then implement token rotation.

---

# 1.15 Phase 14 - Implement Force Password Change

Implement:

```text
POST /api/v1/student/auth/change-password
```

Behavior:

```text
ACTIVE
+
force_password_change = TRUE
```

may login, but normal Student APIs are restricted.

After successful change:

```text
force_password_change = FALSE

Invalidate all Refresh Sessions

Require Login again
```

---

# 1.16 Phase 15 - Implement Student `/me`

Implement:

```text
GET /api/v1/student/auth/me
```

Initially read directly from PostgreSQL.

Do NOT begin with Redis cache.

Verify complete Student academic/profile response first.

---

# 1.17 Phase 16 - Add Redis Auth and Profile Caching

Only after all Mobile Auth APIs work correctly without caching.

Add:

```text
student:auth:account:<studentId>
```

Then:

```text
student:mobile:profile:<studentId>
```

Implement cache eviction for:

```text
Registration

Profile Update

Status Change

Password Reset

Password Change

Soft Delete
```

PostgreSQL remains the source of truth.

---

# 1.18 Phase 17 - Final Security Configuration

After endpoints work individually, configure route authorization.

Public:

```text
POST /api/v1/student/auth/register

POST /api/v1/student/auth/login

POST /api/v1/student/auth/refresh
```

Student protected:

```text
POST /api/v1/student/auth/logout

POST /api/v1/student/auth/change-password

GET /api/v1/student/auth/me
```

Require:

```text
ROLE_STUDENT
```

Admin:

```text
/api/v1/admin/students/**
```

requires:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

---

# 1.19 Phase 18 - Transaction and Concurrency Tests

Before frontend integration test:

```text
Admin Create rollback

Registration rollback

Concurrent Registration

Concurrent failed Login

Password Reset locking

Password Change locking

Unique constraint race

Redis-after-commit behavior
```

These tests are mandatory because Student Registration and Credentials have state invariants.

---

# 1.20 Phase 19 - Integration Tests

Test complete flows.

## Admin-created Student

```text
Admin Create
        ↓
ACTIVE + Credential
        ↓
Mobile Login
        ↓
forcePasswordChange = TRUE
        ↓
Change Password
        ↓
Login Again
        ↓
Normal Student Access
```

## Self-registration Student

```text
PENDING Profile
        ↓
Mobile Register
        ↓
ACTIVE + Credential
        ↓
Login
        ↓
Refresh
        ↓
/me
        ↓
Logout
```

## Suspend Student

```text
ACTIVE
        ↓
Admin Suspend
        ↓
SUSPENDED
        ↓
Refresh Sessions Revoked
        ↓
Login Rejected
        ↓
Refresh Rejected
```

---

# 1.21 Phase 20 - Final Verification

Run:

```text
mvn test
```

then:

```text
mvn clean package
```

Then verify:

```text
Application starts

Flyway passes

Hibernate validate passes

PostgreSQL constraints pass

Redis connects

Admin Student CRUD works

Registration works

Login works

Refresh works

Logout works

Change Password works

/me works
```

Only after this phase should the project move to:

```text
Enrollment
```

---

# 2. Critical Account-State Design

The Student feature has four different concepts that MUST NOT be mixed together:

```text
1. account_status
2. credential existence
3. temporary login lock
4. force password change
```

And one independent lifecycle flag:

```text
5. is_deleted
```

They represent different things.

---

# 3. `account_status`

The only persistent Student account statuses are:

```text
PENDING
ACTIVE
INACTIVE
SUSPENDED
```

Do NOT add:

```text
LOCKED
REGISTERED
UNREGISTERED
DELETED
PASSWORD_CHANGE_REQUIRED
```

as `account_status` values.

Those conditions are represented elsewhere.

---

# 4. Meaning of Each Account Status

## `PENDING`

Means:

```text
Academic Student profile exists
+
Mobile account registration is not complete
+
Student Credential does NOT exist
```

Therefore the valid invariant is:

```text
PENDING
+
NO student_credentials row
```

## `ACTIVE`

Means:

```text
Student account has been provisioned
+
Student Credential exists
+
Student may authenticate
```

Valid invariant:

```text
ACTIVE
+
student_credentials exists
```

Authentication may still be temporarily blocked by:

```text
locked_until
```

or restricted by:

```text
force_password_change
```

but `account_status` remains `ACTIVE`.

## `INACTIVE`

Means:

```text
Student account exists
+
Credential normally remains
+
Admin has disabled Student access
```

Student cannot:

```text
login
refresh
register
use protected Student APIs
```

Do NOT delete credentials when marking Student `INACTIVE`.

## `SUSPENDED`

Means:

```text
Student account exists
+
Credential normally remains
+
Access has been administratively suspended
```

Student cannot:

```text
login
refresh
register
use protected Student APIs
```

Do NOT delete credentials.

---

# 5. Soft Delete Is NOT an Account Status

Use:

```text
students.is_deleted
```

independently from:

```text
students.account_status
```

Example:

```text
account_status = ACTIVE
is_deleted = TRUE
```

means:

```text
historical status was ACTIVE
but Student record is now soft deleted
```

For authentication:

```text
is_deleted = TRUE
```

always overrides `account_status`.

Deleted Student cannot:

```text
register
login
refresh
use Student APIs
```

---

# 6. Account Lock Is NOT an Account Status

Temporary authentication lock is stored in:

```text
student_credentials.locked_until
```

Example:

```text
account_status = ACTIVE

locked_until = 2026-10-05T12:30:00
```

means:

```text
Student account is still ACTIVE
but login is temporarily locked
```

Do NOT change:

```text
ACTIVE → SUSPENDED
```

for automatic failed-login lockouts.

`SUSPENDED` is an Admin/business state.

`locked_until` is an authentication-security state.

---

# 7. Force Password Change Is NOT an Account Status

Use:

```text
student_credentials.force_password_change
```

Example:

```text
account_status = ACTIVE

force_password_change = TRUE
```

The Student may authenticate with the temporary password but must change it before using normal Student features.

Do NOT change status to:

```text
PENDING
```

or:

```text
PASSWORD_CHANGE_REQUIRED
```

---

# 8. Authoritative Account-State Matrix

| `account_status` | Credential | `is_deleted` | Register | Login | Refresh |
|---|---:|---:|---:|---:|---:|
| `PENDING` | No | False | Yes | No | No |
| `PENDING` | Yes | False | Invalid DB/application state | No | No |
| `ACTIVE` | Yes | False | No | Yes | Yes |
| `ACTIVE` | No | False | Invalid DB/application state | No | No |
| `INACTIVE` | Yes | False | No | No | No |
| `INACTIVE` | No | False | Invalid provisioned state | No | No |
| `SUSPENDED` | Yes | False | No | No | No |
| `SUSPENDED` | No | False | Invalid provisioned state | No | No |
| Any | Any | True | No | No | No |

This table is the authoritative rule for the feature.

---

# 9. Valid State Invariants

## Unregistered Profile

```text
account_status = PENDING

student_credentials does NOT exist

is_deleted = FALSE
```

## Registered Active Account

```text
account_status = ACTIVE

student_credentials exists

is_deleted = FALSE
```

## Disabled Account

```text
account_status = INACTIVE

student_credentials exists

is_deleted = FALSE
```

## Suspended Account

```text
account_status = SUSPENDED

student_credentials exists

is_deleted = FALSE
```

---

# 10. Invalid States

These combinations must never be produced by normal application workflows:

```text
PENDING + Credential exists

ACTIVE + no Credential

INACTIVE + no Credential

SUSPENDED + no Credential
```

If corrupted/legacy data contains one of these combinations, do not silently repair it.

Return:

```text
409 Conflict
```

Code:

```text
INVALID_STUDENT_ACCOUNT_STATE
```

---

# 11. Authoritative State Machine

```text
                  PROFILE PROVISIONED
                         │
                         ↓
                    PENDING
                  No Credential
                         │
                         │ Mobile Registration
                         ↓
                     ACTIVE
                  Has Credential
                    /      \
                   /        \
                  ↓          ↓
             INACTIVE     SUSPENDED
                  \          /
                   \        /
                    ↓      ↓
                     ACTIVE
```

Soft delete is independent:

```text
PENDING
ACTIVE
INACTIVE
SUSPENDED
     │
     ↓
is_deleted = TRUE
```

There is NO transition back to `PENDING` after credentials exist.

---

# 12. Admin Full-Account Creation

Normal Admin Student creation:

```text
POST /api/v1/admin/students
```

includes:

```text
Temporary Password
Confirm Password
```

Therefore it creates:

```text
students
+
student_credentials
```

in one transaction.

Allowed status:

```text
ACTIVE
INACTIVE
SUSPENDED
```

Recommended default:

```text
ACTIVE
```

Reject:

```text
PENDING
```

because PENDING is reserved for profile-only Student provisioning.

---

# 13. Pre-Provisioned Mobile Student Profile

Mobile self-registration needs an academic Student profile first.

That profile may come from:

```text
University Student Import
Admin bulk import
Internal provisioning
Future profile-only workflow
```

Profile-only provisioning creates:

```text
students
```

but does NOT create:

```text
student_credentials
```

and sets:

```text
account_status = PENDING
```

---

# 14. Status Update Rules

If Credential does NOT exist:

```text
Allowed:
PENDING only
```

If Credential exists:

```text
Allowed:
ACTIVE
INACTIVE
SUSPENDED
```

Credentialed Student must never return to:

```text
PENDING
```

---

# 15. Mobile Registration Rule

Endpoint:

```text
POST /api/v1/student/auth/register
```

Registration is allowed ONLY when:

```text
Student exists

is_deleted = FALSE

account_status = PENDING

student_credentials does NOT exist

University Email matches Student profile

Email domain exactly = rsu.ac.th
```

Successful registration performs atomically:

```text
INSERT student_credentials

+

PENDING → ACTIVE
```

---

# 16. RSU Email Rule

Registration accepts only:

```text
@rsu.ac.th
```

Valid:

```text
student@rsu.ac.th
6600001@rsu.ac.th
john.s@rsu.ac.th
```

Invalid:

```text
student@gmail.com
student@mail.rsu.ac.th
student@rsu.ac.th.example.com
```

Normalize:

```text
trim
↓
lowercase
↓
parse valid email
↓
extract domain
↓
domain.equals("rsu.ac.th")
```

---

# 17. PostgreSQL `students` Schema

```sql
CREATE TABLE students (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    student_code VARCHAR(50) NOT NULL,
    university_email VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    phone_number VARCHAR(30),
    date_of_birth DATE,

    faculty_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    semester_id BIGINT,

    academic_year SMALLINT,
    enrollment_year SMALLINT,

    account_status VARCHAR(30) NOT NULL,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT,
    updated_at TIMESTAMP,

    CONSTRAINT uk_students_student_code
        UNIQUE (student_code),

    CONSTRAINT uk_students_university_email
        UNIQUE (university_email),

    CONSTRAINT chk_students_academic_year
        CHECK (
            academic_year IS NULL
            OR academic_year BETWEEN 1900 AND 9999
        ),

    CONSTRAINT chk_students_enrollment_year
        CHECK (
            enrollment_year IS NULL
            OR enrollment_year BETWEEN 1900 AND 9999
        ),

    CONSTRAINT chk_students_account_status
        CHECK (
            account_status IN (
                'PENDING',
                'ACTIVE',
                'INACTIVE',
                'SUSPENDED'
            )
        ),

    CONSTRAINT fk_students_faculty
        FOREIGN KEY (faculty_id)
        REFERENCES faculties(id),

    CONSTRAINT fk_students_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id),

    CONSTRAINT fk_students_program
        FOREIGN KEY (program_id)
        REFERENCES programs(id),

    CONSTRAINT fk_students_semester
        FOREIGN KEY (semester_id)
        REFERENCES semesters(id),

    CONSTRAINT fk_students_created_by
        FOREIGN KEY (created_by)
        REFERENCES app_users(id),

    CONSTRAINT fk_students_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES app_users(id)
);
```

---

# 18. PostgreSQL `student_credentials` Schema

```sql
CREATE TABLE student_credentials (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    student_id BIGINT NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    force_password_change BOOLEAN NOT NULL DEFAULT FALSE,

    password_changed_at TIMESTAMP,

    failed_login_attempts INTEGER NOT NULL DEFAULT 0,

    locked_until TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP,

    CONSTRAINT uk_student_credentials_student_id
        UNIQUE (student_id),

    CONSTRAINT chk_student_credentials_failed_attempts
        CHECK (
            failed_login_attempts >= 0
        ),

    CONSTRAINT fk_student_credentials_student
        FOREIGN KEY (student_id)
        REFERENCES students(id)
);
```

---

# 19. Student Account Status Enum

```java
public enum StudentAccountStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
```

Do not create PostgreSQL ENUM.

---

# 20. Admin Create DTO

```java
public record StudentCreateRequest(

    @NotBlank
    @Size(max = 50)
    String studentCode,

    @NotBlank
    @Email
    @Size(max = 255)
    String universityEmail,

    @NotBlank
    @Size(max = 100)
    String firstName,

    @NotBlank
    @Size(max = 100)
    String lastName,

    @Size(max = 30)
    String phoneNumber,

    @Past
    LocalDate dateOfBirth,

    @NotNull
    Long facultyId,

    @NotNull
    Long departmentId,

    @NotNull
    Long programId,

    Long semesterId,

    @Min(1900)
    @Max(9999)
    Integer academicYear,

    @Min(1900)
    @Max(9999)
    Integer enrollmentYear,

    @NotBlank
    String accountStatus,

    @NotBlank
    String temporaryPassword,

    @NotBlank
    String confirmPassword,

    Boolean forcePasswordChange

) {}
```

Allowed Admin Create statuses:

```text
ACTIVE
INACTIVE
SUSPENDED
```

Reject:

```text
PENDING
```

---

# 21. Admin Update DTO

```java
public record StudentUpdateRequest(

    @NotBlank
    @Size(max = 50)
    String studentCode,

    @NotBlank
    @Email
    @Size(max = 255)
    String universityEmail,

    @NotBlank
    @Size(max = 100)
    String firstName,

    @NotBlank
    @Size(max = 100)
    String lastName,

    @Size(max = 30)
    String phoneNumber,

    @Past
    LocalDate dateOfBirth,

    @NotNull
    Long facultyId,

    @NotNull
    Long departmentId,

    @NotNull
    Long programId,

    Long semesterId,

    @Min(1900)
    @Max(9999)
    Integer academicYear,

    @Min(1900)
    @Max(9999)
    Integer enrollmentYear,

    @NotBlank
    String accountStatus

) {}
```

---

# 22. Mobile Registration DTO

```java
public record StudentRegisterRequest(

    @NotBlank
    @Size(max = 50)
    String studentCode,

    @NotBlank
    @Email
    @Size(max = 255)
    String universityEmail,

    LocalDate dateOfBirth,

    @NotBlank
    String password,

    @NotBlank
    String confirmPassword

) {}
```

Do NOT accept academic or account-control fields.

---

# 23. Login DTO

```java
public record StudentLoginRequest(

    @NotBlank
    @Email
    String universityEmail,

    @NotBlank
    String password

) {}
```

---

# 24. Login Eligibility

Login requires:

```text
is_deleted = FALSE

account_status = ACTIVE

Credential exists

locked_until is null
OR
locked_until <= now

BCrypt password matches
```

`force_password_change = TRUE` does NOT prevent authentication.

It restricts post-login functionality.

---

# 25. Force Password Change

When:

```text
ACTIVE
+
Credential
+
force_password_change = TRUE
```

Login succeeds.

Allowed afterward:

```text
GET /api/v1/student/auth/me

POST /api/v1/student/auth/change-password

POST /api/v1/student/auth/logout
```

Other Student APIs return:

```text
403
PASSWORD_CHANGE_REQUIRED
```

---

# 26. Temporary Lock

Temporary lock:

```text
locked_until
```

does NOT change:

```text
account_status
```

Example:

```text
account_status = ACTIVE

locked_until = future timestamp
```

means temporarily locked ACTIVE account.

---

# 27. Admin Inactivate

Transition:

```text
ACTIVE → INACTIVE
```

Credential remains.

After commit:

```text
Invalidate all Refresh Sessions
Evict Auth Cache
Evict Mobile Profile Cache
```

---

# 28. Admin Reactivation

Allowed:

```text
INACTIVE → ACTIVE

SUSPENDED → ACTIVE
```

only if Credential exists.

Missing Credential:

```text
409 INVALID_STUDENT_ACCOUNT_STATE
```

Do not create credentials automatically during normal Update.

---

# 29. Suspension

Admin transition:

```text
ACTIVE → SUSPENDED
```

Credential remains.

Invalidate:

```text
all Refresh Sessions
Auth Cache
Mobile Profile Cache
```

Student cannot authenticate.

---

# 30. Credentialed Account Cannot Return to PENDING

Reject:

```text
ACTIVE → PENDING

INACTIVE → PENDING

SUSPENDED → PENDING
```

with:

```text
400 INVALID_STUDENT_ACCOUNT_STATUS
```

---

# 31. Soft Delete

Soft delete sets:

```text
is_deleted = TRUE
```

Do NOT automatically:

```text
delete Credential

change account_status
```

Authentication always checks:

```text
is_deleted = FALSE
```

first.

---

# 32. Registration Transaction

Use:

```java
@Transactional
```

and lock the Student row.

Concept:

```sql
BEGIN;

SELECT *
FROM students
WHERE student_code = :studentCode
  AND LOWER(university_email) = LOWER(:email)
  AND is_deleted = FALSE
FOR UPDATE;

-- verify PENDING
-- verify no credential

INSERT INTO student_credentials (...);

UPDATE students
SET account_status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE id = :studentId
  AND account_status = 'PENDING';

COMMIT;
```

---

# 33. Registration Concurrency

Protect using:

```text
SELECT Student FOR UPDATE
```

plus:

```text
UNIQUE(student_credentials.student_id)
```

Expected simultaneous behavior:

```text
Request A
→ success

Request B
→ waits
→ sees ACTIVE + Credential
→ 409 STUDENT_ALREADY_REGISTERED
```

---

# 34. Admin Create Transaction

Use one transaction:

```text
BEGIN

Validate Student

Validate Academic References

Validate Duplicate Code/Email

Validate status != PENDING

INSERT students

INSERT student_credentials

COMMIT
```

Credential insertion failure:

```text
ROLLBACK Student insert
```

---

# 35. Admin Update Transaction

Use:

```java
@Transactional
```

Workflow:

```text
Load Student

Check Credential existence

Validate requested status

Validate academic hierarchy

Validate duplicate Code/Email

Update Student

COMMIT

Redis side effects AFTER_COMMIT
```

---

# 36. Credential/Status Validation Rule

```java
boolean credentialExists =
    studentCredentialRepository.existsByStudentId(
        student.getId()
    );

StudentAccountStatus requestedStatus =
    StudentAccountStatus.valueOf(
        request.accountStatus()
            .trim()
            .toUpperCase()
    );

if (!credentialExists) {

    if (student.getAccountStatus()
        != StudentAccountStatus.PENDING) {

        throw new InvalidStudentAccountStateException();
    }

    if (requestedStatus
        != StudentAccountStatus.PENDING) {

        throw new InvalidStudentAccountStatusException();
    }

} else {

    if (requestedStatus
        == StudentAccountStatus.PENDING) {

        throw new InvalidStudentAccountStatusException();
    }
}
```

---

# 37. Password Reset Rule

Password reset requires Credential.

It does NOT modify:

```text
account_status
```

Example:

```text
INACTIVE
+
Password Reset
```

remains:

```text
INACTIVE
```

until explicitly reactivated.

---

# 38. Password Change

Student Change Password requires:

```text
ACTIVE
Credential exists
not deleted
authenticated Student
```

It changes:

```text
password_hash
force_password_change
password_changed_at
failed_login_attempts
locked_until
```

It does NOT change:

```text
account_status
```

---

# 39. Failed Login

Failed Login updates only:

```text
failed_login_attempts
locked_until
updated_at
```

It MUST NOT automatically:

```text
ACTIVE → SUSPENDED
```

---

# 40. Redis Auth State

Redis cache may contain:

```text
studentId
studentCode
accountStatus
isDeleted
forcePasswordChange
lockedUntil
```

but PostgreSQL is authoritative.

---

# 41. Redis Auth Cache DTO

```java
public record StudentAuthCache(

    Long studentId,

    String studentCode,

    String accountStatus,

    Boolean isDeleted,

    Boolean forcePasswordChange,

    LocalDateTime lockedUntil

) {}
```

Key:

```text
student:auth:account:<studentId>
```

---

# 42. Refresh Session DTO

```java
public record StudentRefreshSession(

    Long studentId,

    String refreshTokenHash,

    LocalDateTime createdAt,

    LocalDateTime expiresAt

) {}
```

Key:

```text
student:auth:session:<sessionId>
```

---

# 43. Student Session Index

Key:

```text
student:auth:sessions:<studentId>
```

Redis type:

```text
SET
```

Members:

```text
session IDs
```

---

# 44. Refresh Authorization

Before issuing new tokens verify PostgreSQL:

```text
Student exists

is_deleted = FALSE

account_status = ACTIVE

Credential exists
```

Then validate Redis Refresh Session.

PostgreSQL overrides stale Redis state.

---

# 45. Login State Evaluation Order

```text
1. Find Student

2. Verify is_deleted = FALSE

3. Verify account_status = ACTIVE

4. Verify Credential exists

5. Verify locked_until

6. Verify BCrypt password

7. Evaluate force_password_change for post-login restriction
```

---

# 46. Registration State Evaluation Order

```text
1. Validate exact @rsu.ac.th

2. Find Student

3. Verify is_deleted = FALSE

4. Verify account_status = PENDING

5. Verify Credential does NOT exist

6. Verify identity

7. Validate Password

8. Create Credential

9. Change PENDING → ACTIVE

10. Commit
```

---

# 47. Admin Update State Evaluation Order

```text
1. Find non-deleted Student

2. Check Credential existence

3. Parse requested status

4. Validate account-state invariant

5. Validate academic hierarchy

6. Validate duplicate Student Code

7. Validate duplicate Email

8. Persist

9. Commit

10. Redis AFTER_COMMIT side effects
```

---

# 48. Status Transition Matrix

| Transition | Allowed |
|---|---:|
| `PENDING → ACTIVE` via Registration | Yes |
| `PENDING → ACTIVE` via normal Update | No |
| `ACTIVE → INACTIVE` | Yes |
| `ACTIVE → SUSPENDED` | Yes |
| `INACTIVE → ACTIVE` | Yes |
| `INACTIVE → SUSPENDED` | Yes |
| `SUSPENDED → ACTIVE` | Yes |
| `SUSPENDED → INACTIVE` | Yes |
| `ACTIVE → PENDING` | No |
| `INACTIVE → PENDING` | No |
| `SUSPENDED → PENDING` | No |

---

# 49. Status and Password Matrix

| Status | `forcePasswordChange` | Behavior |
|---|---:|---|
| `ACTIVE` | False | Normal access |
| `ACTIVE` | True | Login allowed, restricted |
| `INACTIVE` | Any | Login denied |
| `SUSPENDED` | Any | Login denied |
| `PENDING` | N/A | Registration only |

---

# 50. Status and Temporary Lock Matrix

| Status | Locked | Authentication |
|---|---:|---|
| `ACTIVE` | No | Login allowed |
| `ACTIVE` | Yes | Temporarily denied |
| `INACTIVE` | Any | Denied |
| `SUSPENDED` | Any | Denied |
| `PENDING` | N/A | Denied |

---

# 51. Standard Invalid State Error

```text
409 Conflict
```

```json
{
  "success": false,
  "code": "INVALID_STUDENT_ACCOUNT_STATE",
  "message": "Student account configuration is inconsistent. Please contact the administrator.",
  "fieldErrors": null,
  "timestamp": "2026-10-05T11:00:00",
  "path": "/api/v1/student/auth/login"
}
```

---

# 52. Already Registered Error

```text
409 Conflict
```

```json
{
  "success": false,
  "code": "STUDENT_ALREADY_REGISTERED",
  "message": "Student account is already registered. Please sign in.",
  "fieldErrors": null,
  "timestamp": "2026-10-05T11:00:00",
  "path": "/api/v1/student/auth/register"
}
```

---

# 53. Registration Not Allowed

For:

```text
INACTIVE
SUSPENDED
Deleted
```

return:

```text
403 Forbidden
STUDENT_REGISTRATION_NOT_ALLOWED
```

---

# 54. Account Not Active

For Login/Refresh with:

```text
PENDING
INACTIVE
SUSPENDED
```

return:

```text
403 Forbidden
STUDENT_ACCOUNT_NOT_ACTIVE
```

---

# 55. Locked Account

Temporary lock:

```text
423 Locked
STUDENT_ACCOUNT_LOCKED
```

This is not the same as:

```text
account_status = SUSPENDED
```

---

# 56. Password Change Required

For authenticated `ACTIVE` Student with:

```text
force_password_change = TRUE
```

normal Student feature APIs return:

```text
403 Forbidden
PASSWORD_CHANGE_REQUIRED
```

---

# 57. Admin Routes

Use:

```text
POST   /api/v1/admin/students

GET    /api/v1/admin/students

GET    /api/v1/admin/students/summary

POST   /api/v1/admin/students/{id:\d+}/reset-password

GET    /api/v1/admin/students/{id:\d+}

PUT    /api/v1/admin/students/{id:\d+}

DELETE /api/v1/admin/students/{id:\d+}
```

Java:

```java
@GetMapping("/{id:\\d+}")
```

---

# 58. Mobile Routes

```text
POST /api/v1/student/auth/register

POST /api/v1/student/auth/login

POST /api/v1/student/auth/refresh

POST /api/v1/student/auth/logout

POST /api/v1/student/auth/change-password

GET  /api/v1/student/auth/me
```

No generic:

```text
/api/v1/student/auth/{id}
```

route.

---

# 59. Redis Cache Eviction

## Registration

After DB commit:

```text
Evict Auth Cache

Evict Mobile Profile Cache

Evict Admin Profile Cache

Evict Student List Cache

Evict Student Summary Cache
```

## Deactivate / Suspend

After commit:

```text
Delete all Refresh Sessions

Evict Auth Cache

Evict Mobile Profile Cache

Evict Admin Profile Cache

Evict List / Summary
```

## Reactivate

After commit:

```text
Evict Auth Cache

Evict Mobile Profile Cache

Evict Admin Profile Cache

Evict List / Summary
```

No Login session is automatically created.

---

# 60. PostgreSQL vs Redis

PostgreSQL is authoritative.

Rules:

```text
DB transaction fails
→ no Redis side effect

DB commit succeeds
→ Redis side effects

Redis profile cache fails
→ fallback to PostgreSQL

Redis session creation fails during Login
→ Login fails

Redis revocation fails after Suspension
→ Refresh checks PostgreSQL and still fails
```

---

# 61. Transaction Isolation

Use:

```text
READ COMMITTED
```

for normal operations.

Use targeted:

```text
PESSIMISTIC_WRITE
```

for:

```text
Registration

Failed Login Counter Update

Password Change

Password Reset
```

---

# 62. Required Account-State Tests

```text
[ ] PENDING always means no Credential

[ ] PENDING + Credential is invalid

[ ] ACTIVE requires Credential

[ ] ACTIVE + no Credential is invalid

[ ] INACTIVE retains Credential

[ ] SUSPENDED retains Credential

[ ] Soft Delete retains Credential

[ ] Soft Delete overrides account status

[ ] locked_until does not change account status

[ ] forcePasswordChange does not change account status

[ ] Password Reset does not activate Student

[ ] Change Password does not change account status

[ ] Failed Login never changes ACTIVE to SUSPENDED

[ ] Admin Create rejects PENDING

[ ] Credentialed Student cannot transition to PENDING

[ ] PENDING cannot become ACTIVE through normal Update

[ ] PENDING → ACTIVE occurs only through Registration
```

---

# 63. Required Transition Tests

```text
[ ] PENDING → ACTIVE Registration works

[ ] ACTIVE → INACTIVE works

[ ] ACTIVE → SUSPENDED works

[ ] INACTIVE → ACTIVE works with Credential

[ ] SUSPENDED → ACTIVE works with Credential

[ ] INACTIVE → SUSPENDED works

[ ] SUSPENDED → INACTIVE works

[ ] ACTIVE → PENDING rejected

[ ] INACTIVE → PENDING rejected

[ ] SUSPENDED → PENDING rejected

[ ] PENDING → ACTIVE normal Admin Update rejected
```

---

# 64. Required Authentication Tests

```text
[ ] ACTIVE + Credential Login succeeds

[ ] ACTIVE + forcePasswordChange Login succeeds but access restricted

[ ] ACTIVE + lockedUntil future returns locked

[ ] ACTIVE + no Credential returns invalid state

[ ] PENDING cannot Login

[ ] INACTIVE cannot Login

[ ] SUSPENDED cannot Login

[ ] Deleted Student cannot Login

[ ] INACTIVE cannot Refresh

[ ] SUSPENDED cannot Refresh

[ ] Deleted Student cannot Refresh

[ ] PostgreSQL overrides stale Redis state
```

---

# 65. Required Registration Tests

```text
[ ] PENDING + no Credential can register

[ ] Registration creates Credential

[ ] Registration changes PENDING → ACTIVE

[ ] Credential creation + status update are atomic

[ ] Failed Credential insert leaves PENDING + no Credential

[ ] Failed ACTIVE update rolls back Credential

[ ] Concurrent registration produces one success

[ ] ACTIVE + Credential returns already registered

[ ] PENDING + Credential returns invalid state

[ ] INACTIVE cannot register

[ ] SUSPENDED cannot register

[ ] Deleted Student cannot register

[ ] Exact @rsu.ac.th accepted

[ ] Gmail rejected

[ ] mail.rsu.ac.th rejected

[ ] rsu.ac.th.example.com rejected
```

---

# 66. Implementation Completion Checklist

Do not mark Student complete until all stages below are done.

```text
DATABASE
[ ] Flyway migrations verified
[ ] Hibernate validate passes

DOMAIN
[ ] Student entity
[ ] StudentCredential entity
[ ] StudentAccountStatus

REPOSITORY
[ ] StudentRepository
[ ] StudentCredentialRepository
[ ] Registration row lock
[ ] Credential row lock

DTO
[ ] Admin DTOs
[ ] Mobile DTOs
[ ] Response DTOs
[ ] Error response contract

ADMIN
[ ] Create
[ ] List
[ ] Search
[ ] Filters
[ ] Summary
[ ] Detail
[ ] Update
[ ] Soft Delete
[ ] Password Reset

REGISTRATION
[ ] RSU email validation
[ ] PENDING eligibility
[ ] Row locking
[ ] Credential creation
[ ] PENDING → ACTIVE

AUTHENTICATION
[ ] Login
[ ] Failed Login handling
[ ] Temporary lock
[ ] RS256 JWT
[ ] ROLE_STUDENT

REDIS
[ ] Refresh Session
[ ] Student Session Index
[ ] Refresh rotation
[ ] Logout
[ ] Logout all sessions
[ ] Auth Cache
[ ] Mobile Profile Cache
[ ] Cache eviction

PASSWORD
[ ] forcePasswordChange
[ ] Change Password
[ ] Password Reset
[ ] Session invalidation

PROFILE
[ ] /me

SECURITY
[ ] Public routes
[ ] Student-protected routes
[ ] Admin-protected routes
[ ] Password-change-required restriction

TESTING
[ ] CRUD tests
[ ] Account-state tests
[ ] Registration concurrency
[ ] Login concurrency
[ ] Redis tests
[ ] Route conflict tests
[ ] Integration tests

BUILD
[ ] mvn test
[ ] mvn clean package
```

---

# 67. Final State Model

```text
                      students
                         │
                  account_status
                         │
         ┌───────────────┼──────────────────┐
         │               │                  │
      PENDING          ACTIVE        INACTIVE/SUSPENDED
         │               │                  │
   no Credential      Credential         Credential
         │               │                  │
         │               ├── locked_until   │
         │               │                  │
         │               └── force_password_change
         │
         ↓
 Mobile Registration
         │
         ↓
       ACTIVE
```

Independent:

```text
is_deleted
```

overrides all authentication access.

---

# 68. Final Rule Summary

```text
PENDING
= pre-provisioned Student
= no Credential
= Mobile Registration allowed
```

```text
ACTIVE
= provisioned Student
= Credential required
= authentication allowed
```

```text
INACTIVE
= provisioned Student disabled by Admin
= Credential retained
```

```text
SUSPENDED
= provisioned Student suspended by Admin
= Credential retained
```

```text
locked_until
= temporary Login security restriction
= NOT account status
```

```text
force_password_change
= post-login access restriction
= NOT account status
```

```text
is_deleted
= soft-delete lifecycle flag
= overrides all account access
```

---

# 69. Feature Handoff to Enrollment

Only after every Student phase is complete:

```text
Student
        ↓
Student Credential
        ↓
Mobile Registration/Auth
        ↓
Stable students.id
        ↓
Enrollment
```

Future Enrollment:

```text
enrollments.student_id
→ students.id
```

Then Course Section can derive:

```text
enrolledCount

isFull

totalEnrolled

fullSections
```
---

# 70. Amendment (2026-10-06) - Open Self-Registration

Decided by the project owner after implementation. Where this section conflicts with earlier sections, this section wins.

```text
POST /api/v1/student/auth/register
```

- Matching student code + university email on a PENDING profile: activate it as before (sections 15, 32, 46).
- No matching student: create a NEW student directly as ACTIVE with a credential.
  - Requires firstName and lastName; phoneNumber and dateOfBirth are optional.
  - Email must still be exactly @rsu.ac.th.
  - faculty_id, department_id and program_id stay NULL (Flyway V18 makes them nullable).
  - force_password_change = FALSE, because the student chose the password.
  - Student code or email already used by another student: 409 STUDENT_CODE_ALREADY_EXISTS / STUDENT_EMAIL_ALREADY_EXISTS.

Students set their own academic placement after login:

```text
PUT /api/v1/student/profile      (ROLE_STUDENT, full session)
```

Body: firstName, lastName, phoneNumber, dateOfBirth, facultyId, departmentId, programId, semesterId, academicYear, enrollmentYear.
Faculty / department / program are required and validated with the same rules as Admin Create (section 1.7).
Student code, university email and account status remain admin-only.
