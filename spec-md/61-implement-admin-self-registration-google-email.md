# 61 — Admin Self-Registration: Registration Form, Google Sign-In and Registration Mail

> **Changed by spec file 62 (D87, 2026-11-17):** a proven registration no longer becomes `ACTIVE`. It becomes `PENDING_APPROVAL` and waits for a Super Admin (`POST /api/v1/admin/users/{id}/approve` or `/reject`). The first Google sign-in answers `403 ACCOUNT_PENDING_APPROVAL` instead of tokens, and the welcome mail of section 7 is replaced by the waiting and approved mails. Where this file says otherwise, spec file 62 wins.

**Project:** Smart AI University Student Assistant
**Module:** Authentication → Admin web self-registration
**Scope:** Four public endpoints under `/api/v1/admin/auth`, Google ID token verification, registration mail over SMTP, configuration, tests.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec file 60 (V57 to V59) and spec files 05 (login, sessions) and 08 (Super Admin user management).
**Status:** Written and implemented 2026-11-16 (decision D86). Requested by Hsu.
**Frontend counterpart:** not written yet. Section 15 lists what the admin web has to add.

---

## 1. Objective

A person with a university email address can create an Admin web account by themself, in two ways:

1. **Registration form.** First name, last name, email, password. The backend sends a verification link to the address. After the link is opened the account can sign in.
2. **Google sign-in.** The admin web shows Google's button. The first sign-in creates the account, every later one signs in.

After a successful registration the person receives an email ("Your registration is complete").

Decisions Hsu made for this spec:

| Decision | Value |
| --- | --- |
| Approval by a Super Admin before access | **No.** The account works as soon as the email is proven. |
| Who may register with the form | Only `@rsu.ac.th` addresses. |
| Who may sign in with Google | Only `@rsu.ac.th` addresses. The same rule for both ways. |
| Database | Cleanly normalized (spec file 60). |

Choices this spec makes where the request was silent are listed in section 16.

---

## 2. Mandatory Agent Instructions

Before implementation: read `Agent.md`, spec files 05, 08 and 60, and inspect `AuthenticationServiceImpl`, `CredentialVerificationServiceImpl`, `AccountSecurityReaderImpl`, `AuthenticationRequestFilter` and `SecurityConfig`.

Do not:

- let a caller choose a role, a status or an employee ID at registration;
- give a self-registered account the role `SUPER_ADMIN`;
- activate a form registration before its email is verified (without that the domain rule means nothing: anybody can type an `@rsu.ac.th` address);
- trust the email of a Google token before its signature, issuer, audience and expiry are checked, or when `email_verified` is not true;
- accept a Google account because its email ends with the right domain; the Workspace claim `hd` decides (section 4);
- register a second `JwtDecoder` bean (the application's only one is for its own access tokens);
- log an email address, a name, a token, a link or a password (codes and outcomes only);
- reveal through `/register` or `/resend-verification` whether an address already has an account;
- let a mail server problem fail or roll back a registration;
- change the request or response of `/login`, `/refresh`, `/me`, `/logout` or `/change-password`.

---

## 3. Account states

| `account_status` | Meaning | Can sign in |
| --- | --- | --- |
| `PENDING_VERIFICATION` | Form registration, email not verified. Set by the system only. | No |
| `ACTIVE` | Normal account. | Yes |
| `INACTIVE`, `LOCKED`, `SUSPENDED` | Set by a Super Admin (spec file 08). | No |

| `registration_source` | Meaning |
| --- | --- |
| `ADMIN_CREATED` | Created by a Super Admin or seeded. |
| `SELF_FORM` | Registered with the form. |
| `GOOGLE` | Created by the first Google sign-in. |

A self-registered account has:

- role `ADMIN_REGISTRATION_DEFAULT_ROLE` (default `ADMIN`; `ACADEMIC_ADMIN` is the only other allowed value);
- `employee_id` `NULL` (a Super Admin can enter it later with `PUT /api/v1/admin/users/{id}`);
- `created_by` `NULL`, `force_password_change` `false`;
- the email in lower case.

---

## 4. Email domain rule

One rule for the form and for Google, from `ADMIN_REGISTRATION_EMAIL_DOMAIN` (default `rsu.ac.th`):

- the address is trimmed and lower-cased first;
- exactly one `@`; the part before it uses `a-z 0-9 . _ % + -`, does not start or end with a dot and has no two dots in a row (the same rule as Student registration);
- the part after it is **exactly** the configured domain. `mail.rsu.ac.th`, `student.rsu.ac.th` and `rsu.ac.th.example.com` are refused.

For Google, additionally:

- `email_verified` must be true;
- the account must belong to the university's Google Workspace: the claim `hd` of the token must be present and equal to `GOOGLE_OAUTH_HOSTED_DOMAIN` (empty by default, which means the email domain, `rsu.ac.th`).

Why `hd` and not only the email: Google's guide says that the domain of the email is not enough to know that an account belongs to an organization, and that `hd` must be checked when a sign-in is limited to the members of a domain. Anybody who once received mail at an `@rsu.ac.th` address could have made a **personal** Google account with it; that account keeps the address even when the mailbox was closed or given to somebody else, and its token has no `hd`. Such an account is refused with `403 GOOGLE_ACCOUNT_NOT_IN_DOMAIN`. A person who really has the mailbox can still use the registration form.

The mail of `rsu.ac.th` is handled by Google (the MX records of the domain point to Google, checked on 2026-11-16), so university mailboxes are Google Workspace accounts.

---

## 5. Endpoints

All four are `POST`, public, JSON, under `/api/v1/admin/auth`, answer with `Cache-Control: no-store`, ignore an `Authorization` header (like `/login`), and are limited to `AUTH_LOGIN_PER_IP` requests a minute per IP address and endpoint. A body over 16 KB is `413 CONTENT_TOO_LARGE`. Errors use the project's error body (`timestamp`, `status`, `code`, `message`, `path`, `traceId`).

### 5.1 `POST /register`

Request:

```json
{
  "firstName": "Somchai",
  "lastName": "Jaidee",
  "email": "somchai.j@rsu.ac.th",
  "phoneNumber": "02 791 6000",
  "password": "a long passphrase for rsu",
  "confirmPassword": "a long passphrase for rsu"
}
```

| Field | Rule |
| --- | --- |
| `firstName`, `lastName` | Required, at most 100 characters, trimmed. |
| `email` | Required, a valid address of at most 255 characters, the domain rule of section 4. |
| `phoneNumber` | Optional, at most 30 characters, digits, spaces and `+ ( ) -`, at least one digit. |
| `password` | Required. The existing password policy: at least 15 characters, at most 72 UTF-8 bytes. |
| `confirmPassword` | Required, equal to `password`. |

Any other field is refused (`400 VALIDATION_ERROR`).

Response `202 Accepted`, always the same for an address of the right domain:

```json
{
  "email": "somchai.j@rsu.ac.th",
  "message": "If this address can be registered, we have sent an email to it. Open the link in that email to finish the registration."
}
```

What happens behind that answer:

| The address ... | Result | Mail |
| --- | --- | --- |
| has no account | New account `PENDING_VERIFICATION`, source `SELF_FORM`, default role, password hash, one verification row. | Verification link |
| has an unverified form registration | The newest registration wins: name, phone and password are replaced, the old link stops working. | Verification link |
| has an `ACTIVE` account | Nothing changes. | "You already have an account" |
| has a deleted, suspended, inactive or locked account | Nothing changes. | None |

Errors:

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing, too long or unknown field; bad email or phone format. |
| 400 | `EMAIL_DOMAIN_NOT_ALLOWED` | The address is not `@rsu.ac.th`. Message: `Only @rsu.ac.th email addresses can register.` |
| 400 | `PASSWORD_CONFIRMATION_MISMATCH` | `confirmPassword` differs. |
| 400 | `PASSWORD_POLICY_VIOLATION` | The password is too short or too long. |
| 403 | `REGISTRATION_DISABLED` | `ADMIN_REGISTRATION_ENABLED=false`. |
| 429 | `TOO_MANY_REQUESTS` | The IP limit, or more than `ADMIN_REGISTRATION_MAIL_PER_ADDRESS` (3) mails a minute to one address. |
| 503 | `REGISTRATION_UNAVAILABLE` | The default role is missing or inactive. |

### 5.2 `POST /verify-email`

Request: `{"token": "<the token of the link>"}`

Response `200`:

```json
{ "email": "somchai.j@rsu.ac.th", "accountStatus": "ACTIVE", "message": "Your email is verified. You can sign in now." }
```

The account becomes `ACTIVE`, `email_verified_at` is set, the verification row is deleted and the welcome mail is sent. The person then signs in with `/login`; this endpoint does not return tokens.

`400 INVALID_VERIFICATION_TOKEN` for a token that is malformed, unknown, replaced by a newer link, older than `ADMIN_REGISTRATION_VERIFICATION_TTL` (24 hours), or already used. One code for all cases.

### 5.3 `POST /resend-verification`

Request: `{"email": "somchai.j@rsu.ac.th"}`

Response `202` with the same body as `/register`, always. A new link is mailed only when the address has an unverified form registration; the old link stops working. `429 TOO_MANY_REQUESTS` after 3 mails a minute to one address (counted together with `/register`).

### 5.4 `POST /google`

Request: `{"idToken": "<the credential the Google button returned>"}` (at most 4096 characters).

Response `200`: exactly the body of `/login` (`tokenType`, `accessToken`, `expiresIn`, `refreshToken`, `refreshExpiresIn`, `forcePasswordChange`, `user`). `/me`, `/refresh` and `/logout` work with these tokens as with any other session.

Steps:

1. `503 GOOGLE_SIGN_IN_NOT_CONFIGURED` when `GOOGLE_OAUTH_CLIENT_ID` is empty.
2. Verify the ID token (section 8). `401 INVALID_GOOGLE_TOKEN` when it fails.
3. `403 GOOGLE_EMAIL_NOT_VERIFIED` when `email_verified` is not true.
4. `403 EMAIL_DOMAIN_NOT_ALLOWED` when the email is not `@rsu.ac.th` (section 4).
5. `403 GOOGLE_ACCOUNT_NOT_IN_DOMAIN` when the account is not an account of the university's Google Workspace (`hd` missing or different, section 4).
6. Find the account, in one transaction:

| Situation | Result | Mail |
| --- | --- | --- |
| This Google account (`sub`) is already linked, account `ACTIVE` | Sign in. | None |
| Not linked, no account with this email | New account `ACTIVE`, source `GOOGLE`, `email_verified_at` now, default role, name from Google, one link row. | Welcome |
| Not linked, an `ACTIVE` account has this email | The Google account is linked to it. Role, name and password stay as they are. `email_verified_at` is set if it was empty. Google calls an account with `email_verified` and `hd` the legitimate owner of its address, which is why step 5 comes first. | None |
| Not linked, an unverified form registration has this email | The account becomes `ACTIVE`, is linked, its verification row is deleted and **its password is replaced by one nobody knows** (section 9). | Welcome |
| The account is deleted, suspended, inactive or locked | `403 ACCOUNT_NOT_ACTIVE` | None |
| The account is linked to another Google account | `409 GOOGLE_ACCOUNT_MISMATCH` | None |
| No account and `ADMIN_REGISTRATION_ENABLED=false` | `403 REGISTRATION_DISABLED` | None |

7. Open a session exactly as `/login` does (`AuthenticationService.startSession`).

Emails are compared without case, so `Somchai.J@rsu.ac.th` from Google finds `somchai.j@rsu.ac.th`.

Other errors: `400 VALIDATION_ERROR` (no or too long `idToken`), `429 TOO_MANY_REQUESTS`, `503 GOOGLE_SIGN_IN_UNAVAILABLE` (Google's signing keys cannot be read).

---

## 6. Login after registration

`/login` is unchanged except for one lookup rule: the email is looked up as typed, and when nothing matches, once more in lower case. Self-registered accounts store the email in lower case, so a person who types `Somchai.J@rsu.ac.th` still finds the account. An exact match always wins, so existing accounts behave as before.

A `PENDING_VERIFICATION` account that tries to log in gets `401 INVALID_CREDENTIALS`, the same answer as any other failed login.

---

## 7. Registration mail

Plain text, UTF-8, sent with Spring's `JavaMailSender` over SMTP.

| Mail | Subject | When | Contains |
| --- | --- | --- | --- |
| Verification | `Confirm your email address` | `/register`, `/resend-verification` | The link `ADMIN_REGISTRATION_VERIFICATION_URL?token=<token>`, how long it works, "ignore this if you did not register". |
| Welcome | `Your registration is complete` | `/verify-email`, and the first Google sign-in | The login page `ADMIN_REGISTRATION_LOGIN_URL` and how to sign in (password or Google). |
| Already registered | `You already have an account` | `/register` for an address with an `ACTIVE` account | The login page. "Nothing was changed." |

Rules:

- The mail is sent **after** the database transaction has committed, in the same request.
- A mail that cannot be sent (mail switched off, server unreachable, refused) never fails the request. It is logged as `ADMIN_MAIL_SEND_FAILED kind=<kind> error=<exception class>` or `ADMIN_MAIL_SKIPPED`. The person can ask for a new link with `/resend-verification`.
- SMTP connect, read and write timeouts are 5 seconds.
- The token is 32 random bytes in URL-safe Base64 (43 characters). The database stores only its SHA-256 hash.
- A name is written into the text as one line; control characters are removed.

---

## 8. Google ID token verification

The admin web uses Google Identity Services (the "Sign in with Google" button). Google gives the browser an ID token; the browser posts it to `/google`. The backend needs **no client secret and no redirect URI**.

`GoogleIdTokenVerifierImpl` checks, with Spring Security's `NimbusJwtDecoder`:

1. the RS256 signature against Google's published keys (`GOOGLE_OAUTH_JWK_SET_URI`, default `https://www.googleapis.com/oauth2/v3/certs`; the keys are cached and re-read when Google rotates them);
2. `iss` is `accounts.google.com` or `https://accounts.google.com`;
3. `aud` contains `GOOGLE_OAUTH_CLIENT_ID` (a token issued for another website is refused);
4. `exp` is present and not passed (60 seconds of clock difference are tolerated);
5. `sub` and `email` are present.

It returns `sub`, `email`, `email_verified`, `hd`, `given_name`, `family_name` and `name`. Any failed check is `401 INVALID_GOOGLE_TOKEN`; keys that cannot be read are `503 GOOGLE_SIGN_IN_UNAVAILABLE`.

The decoder is built inside the class on first use and is not a Spring bean.

---

## 9. Accounts without a password

`appuser_credentials.password_hash` stays `NOT NULL` (spec file 60, section 4). An account created by Google, and a form registration that Google finishes, gets the BCrypt hash of 32 random bytes that are thrown away. The account has a normal credential row (sessions, lockout counters), but no password matches it.

Such a person signs in with Google only. `/change-password` asks for the current password, so it cannot be used to set one; "set a password" is not part of this spec.

Why the password of an unverified form registration is replaced when Google finishes it: before the address was proven, **anybody** could have submitted the form with that address and their own password. If that password stayed, they could log in to the real owner's account afterwards.

---

## 10. Security rules

- **Proof of the address.** Form: the link. Google: a verified token with `email_verified` from an account of the university's Google Workspace (`hd`). No account is `ACTIVE` without one of them.
- **The Google account, not the email, is the key.** After the first sign-in a person is found by Google's `sub` (spec file 60, V58). Another Google account that shows the same email later is refused (`409 GOOGLE_ACCOUNT_MISMATCH`).
- **No enumeration.** `/register` and `/resend-verification` answer the same for every address of the right domain.
- **Newest registration wins.** A second form registration of an unverified address replaces the first, and the first link stops working.
- **Mail limits.** 3 mails a minute to one address, so the endpoints cannot be used to flood somebody's inbox.
- **Roles.** The default role is validated at startup (`ADMIN` or `ACADEMIC_ADMIN`). A Super Admin can change the role, suspend or delete a self-registered account with the existing `/api/v1/admin/users` endpoints, and `GET /api/v1/admin/users?accountStatus=PENDING_VERIFICATION` lists unverified registrations.
- **Suspended means suspended.** A suspended, inactive, locked or deleted account cannot sign in with Google either, and an open Google session ends as soon as the account is suspended (the existing session check).
- **Logs.** `ADMIN_REGISTRATION outcome=...`, `ADMIN_EMAIL_VERIFIED`, `ADMIN_GOOGLE_SIGN_IN outcome=...`, `ADMIN_MAIL_SENT kind=...`. No address, name, token or link.

---

## 11. Configuration

`application.properties` → environment variable (default):

| Variable | Default | Meaning |
| --- | --- | --- |
| `ADMIN_REGISTRATION_ENABLED` | `true` | `false`: no new accounts (form and Google). Existing accounts still sign in with Google. |
| `ADMIN_REGISTRATION_EMAIL_DOMAIN` | `rsu.ac.th` | The only allowed domain. |
| `ADMIN_REGISTRATION_DEFAULT_ROLE` | `ADMIN` | `ADMIN` or `ACADEMIC_ADMIN`. |
| `ADMIN_REGISTRATION_VERIFICATION_TTL` | `24h` | 5 minutes to 7 days. |
| `ADMIN_REGISTRATION_VERIFICATION_URL` | `http://localhost:3000/verify-email` | The admin web page of the link. |
| `ADMIN_REGISTRATION_LOGIN_URL` | `http://localhost:3000/login` | The login page named in the mails. |
| `ADMIN_REGISTRATION_MAIL_PER_ADDRESS` | `3` | Mails a minute to one address. |
| `GOOGLE_OAUTH_CLIENT_ID` | empty | OAuth 2.0 Web client ID. Empty: Google sign-in is off. Not a secret. |
| `GOOGLE_OAUTH_HOSTED_DOMAIN` | empty | The Google Workspace domain (`hd`) a Google account must belong to. Empty: the same as `ADMIN_REGISTRATION_EMAIL_DOMAIN`. Needed only when the university's Workspace reports another main domain. |
| `GOOGLE_OAUTH_JWK_SET_URI` | Google's | Only changed in tests. |
| `MAIL_ENABLED` | `true` | `false`: no mail is sent. |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | The SMTP server. In Docker Compose the backend uses `mailpit`. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP login. The password belongs only in the git-ignored `.env`. |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `false` / `false` | Both `true` for a real server such as Gmail on port 587. |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `no-reply@rsu.ac.th` / `Smart AI University Assistant` | The sender. |

`compose.yaml` gets the service `mailpit` (`axllent/mailpit:v1.27`): SMTP on `127.0.0.1:1025`, inbox on <http://localhost:8025>. With no `MAIL_*` value in `.env`, every mail of the Docker stack goes there and nothing leaves the machine. `management.health.mail.enabled=false`, so an unreachable mail server does not turn the health endpoint down.

`pom.xml` gets `spring-boot-starter-mail`. Google verification uses libraries that are already there (`spring-security-oauth2-jose`).

---

## 12. Code structure

Everything is in `feature/authentication` (flat layer folders, `Agent.md`), plus two property classes in `config` and two rules in `security`.

| File | Purpose |
| --- | --- |
| `controller/AdminRegistrationController` | The four endpoints. |
| `dto/AdminRegisterRequest`, `AdminRegistrationResponse`, `VerifyEmailRequest`, `VerifyEmailResponse`, `ResendVerificationRequest`, `GoogleSignInRequest` | Requests and responses. `toString` hides secrets. |
| `entity/AppUser` | `employeeId` nullable; new `registrationSource`, `emailVerifiedAt`. |
| `entity/AppUserExternalIdentity`, `AppUserEmailVerification` | The two new tables. |
| `repository/AppUserExternalIdentityRepository`, `AppUserEmailVerificationRepository` | |
| `repository/AppUserRepository` | `findAllByEmailIgnoreCaseForUpdate`. |
| `repository/AppRoleRepository` | `findByRoleCode`. |
| `service/AdminRegistrationService` + `impl` | The flow of each endpoint. No transaction: validates, limits, hashes, calls the database step, then sends the mail. |
| `service/AdminRegistrationAccountService` + `impl` | The database step of each flow, one transaction each. Locks the user row first, then its credential and verification rows. |
| `service/GoogleIdTokenVerifier`, `GoogleIdentity` + `impl/GoogleIdTokenVerifierImpl` | Section 8. |
| `service/AdminMailService` + `impl/AdminMailServiceImpl` | Section 7. |
| `service/AuthenticationService.startSession(userId)` + `impl` | Opens a session for a person whose identity was proven without a password. `login` uses the same private method. |
| `service/impl/CredentialVerificationServiceImpl` | The lower-case lookup of section 6. |
| `service/impl/AdminAccountManagementServiceImpl` | `profileChanged` accepts a missing employee ID. |
| `config/AdminRegistrationProperties`, `AppMailProperties` | Validated settings (`app.admin-registration.*`, `app.mail.*`), registered in `ClockConfig`. |
| `security/SecurityConfig` | The four paths are public. |
| `security/AuthenticationRequestFilter` | IP limit and body size for the four paths. |
| `feature/user/dto/request/AdminUserListQuery` | `accountStatus=PENDING_VERIFICATION` is accepted as a list filter. |

---

## 13. Tests

| Class | Kind | Covers |
| --- | --- | --- |
| `AdminRegistrationIntegrationTests` (11) | Spring Boot, real PostgreSQL and Redis, mocked mail and Google check | Form → link → login; domain, password and input rules; an address that already has an account; the newest registration wins; resend, expiry and the mail limit; first and later Google sign-ins; refused Google identities (other domains, personal Google accounts, unverified emails); linking by email; Google finishing an unverified registration; the Super Admin list and profile update; the schema rules of spec file 60. |
| `GoogleIdTokenVerifierTests` (4) | Unit, with a local key server in place of Google's | A valid token is read; wrong signature, audience, issuer, expiry, changed payload, unsigned and malformed tokens are refused; unreachable keys are `503`. |
| `AdminMailServiceTests` (2) | Unit, SMTP replaced | Subjects, sender, recipient and text of the three mails; a name cannot add a mail header; failures never throw. |
| `AdminRegistrationServiceTests` (6) | Unit | Registration switched off; Google not configured; the Workspace rule and `GOOGLE_OAUTH_HOSTED_DOMAIN`; two first sign-ins at the same moment; the unusable password hash; two registrations at the same moment. |

`AuthenticationIntegrationTests.migrationHistoryAndSchemaStayValid` expects 50 public tables and 36 authentication columns.

The integration class creates only accounts whose email starts with `sr-` and removes them and their rows in the two new tables after every test, so other test classes can still run `DELETE FROM app_users`.

---

## 14. Postman checks

Base URL `http://localhost:8080`. Mail inbox: <http://localhost:8025>.

1. `POST /api/v1/admin/auth/register` with the body of section 5.1 → `202`.
2. Open Mailpit, copy the value after `token=` from the mail "Confirm your email address".
3. `POST /api/v1/admin/auth/login` now → `401 INVALID_CREDENTIALS` (not verified).
4. `POST /api/v1/admin/auth/verify-email` with `{"token":"..."}` → `200`, and the mail "Your registration is complete" arrives.
5. `POST /api/v1/admin/auth/login` with the email and password → `200` with role `ADMIN`.
6. `POST /api/v1/admin/auth/register` with `a@gmail.com` → `400 EMAIL_DOMAIN_NOT_ALLOWED`.
7. `POST /api/v1/admin/auth/resend-verification` four times within a minute for one address → the fourth is `429`.
8. `POST /api/v1/admin/auth/google` with `{"idToken":"abc"}` → `401 INVALID_GOOGLE_TOKEN` (or `503 GOOGLE_SIGN_IN_NOT_CONFIGURED` without a client ID). A real token comes from the Google button of the admin web.

---

## 15. What the admin web has to add

> **Built on 2026-11-17.** See the admin web spec `spec-md/45-api-implementation-admin-self-registration.md` in the frontend folder. Two points differ from the list below: the admin web shows its own fixed text for every refusal (same meaning, not the server `message`), and the button says "Sign in with Google" on the login page and "Sign up with Google" on the register page. `compose.yaml` passes `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_HOSTED_DOMAIN` and `ADMIN_REGISTRATION_EMAIL_DOMAIN` to the `admin-web` build as `VITE_GOOGLE_CLIENT_ID`, `VITE_GOOGLE_HOSTED_DOMAIN` and `VITE_ADMIN_EMAIL_DOMAIN`, so a changed client ID needs `docker compose up -d --build admin-web`.

1. A **Register** page with the form of section 5.1 that posts to `/register` and then shows "check your email".
2. A page at the path of `ADMIN_REGISTRATION_VERIFICATION_URL` (default `/verify-email`) that reads `token` from the URL, posts it to `/verify-email`, and shows success with a link to the login page, or "invalid or expired" with a "send a new link" button (`/resend-verification`).
3. A **Continue with Google** button (Google Identity Services, script `https://accounts.google.com/gsi/client`, the same client ID as `GOOGLE_OAUTH_CLIENT_ID`). Its JavaScript `callback` posts `response.credential` as `idToken` to `/google` and stores the returned tokens exactly like after `/login`.
   - Use the `callback` option, not `login_uri` (the redirect mode posts a form to the server and needs a CSRF check that `/google` does not have).
   - Optional: `hd: "rsu.ac.th"` in `google.accounts.id.initialize(...)`, so Google offers only university accounts. It is only a hint; the backend rule of section 4 decides.
   - Show the `message` of `403 GOOGLE_ACCOUNT_NOT_IN_DOMAIN` ("use your university Google account") and of `403 EMAIL_DOMAIN_NOT_ALLOWED`.
   - The origin of the page must be listed under **Authorized JavaScript origins** of the client ID. For local work Google asks for both `http://localhost` and `http://localhost:<port>`. If the button answers "origin not allowed" on `http://localhost`, Google's guide names the response header `Referrer-Policy: no-referrer-when-downgrade` for the page; `docker/admin-web.conf` sets no such header today.
4. Handle `employeeId: null` in the profile and in the user list.

---

## 16. Choices not given by the request

| Topic | Choice | Why |
| --- | --- | --- |
| Role of a self-registered account | `ADMIN`, configurable | Hsu chose "no approval", so a role is needed at once. `ADMIN` is the general role; `SUPER_ADMIN` is never allowed. |
| Email verification for the form | Required before the first login | Hsu asked for "no approval", not "no verification". Without the link anybody could type somebody else's `@rsu.ac.th` address. |
| Employee ID | Empty until a Super Admin enters it | Google does not know it and the form should stay short. |
| Which Google accounts count as `@rsu.ac.th` | Only accounts of the university's Google Workspace (claim `hd`) | Hsu asked for "rsu.ac.th only". Google's guide: the email domain alone does not prove that, `hd` does. A personal Google account with a university address is refused. |
| An existing account with the same email | Google links to it | The Workspace account is the owner of the address (Google's guide), so no second proof is asked. |
| Answer of `/register` for a known address | The same `202` | The endpoint is public; it must not tell who has an account. |
| Mail in development | Mailpit in Docker Compose | No real mail account is needed to try the feature. |
| Verification storage | A table (spec file 60), not Redis | The expiry follows the application clock and the link survives a Redis restart. |

---

## 17. Known limits

- **Every `@rsu.ac.th` address can become an Admin.** If students have `@rsu.ac.th` addresses too, they can register. Use `ADMIN_REGISTRATION_ENABLED=false` after the staff has registered, or ask for an approval step or an allow-list of addresses as a follow-up.
- A Google-only account cannot set a password (section 9).
- A person whose `@rsu.ac.th` address is used only by a personal Google account cannot sign in with Google. The registration form works for them.
- If the university's Google Workspace reports a main domain other than `rsu.ac.th` in `hd`, every Google sign-in is refused with `GOOGLE_ACCOUNT_NOT_IN_DOMAIN` until `GOOGLE_OAUTH_HOSTED_DOMAIN` is set to that domain. This was not tried with a real university account.
- An existing account whose email was stored with capital letters is still found only when the email is typed the same way at `/login` (as before this spec).
- A Google account whose email equals a Super Admin's `@rsu.ac.th` email is linked to that Super Admin account, like any other account.
- Failed password attempts lock an account for 15 minutes (spec file 05); during that time Google sign-in is refused as well (`403 ACCOUNT_NOT_ACTIVE`).
- The admin user list and detail do not show `registrationSource` or `emailVerifiedAt` yet.
- Mail is sent in the request (at most 5 seconds when the server does not answer). There is no retry queue.
- No HTML mail and no Thai text.

---

## 18. Acceptance Criteria

1. `./mvnw clean package` passes.
2. A form registration with an `@rsu.ac.th` address creates a `PENDING_VERIFICATION` account, mails a link, and cannot log in before the link is used.
3. The link activates the account once, within 24 hours, and the welcome mail is sent.
4. An address of another domain is refused by the form and by Google.
5. The first Google sign-in of an `@rsu.ac.th` person creates an `ACTIVE` account, returns login tokens and sends the welcome mail; the second one signs in without a new account or mail.
6. A Google token with a wrong signature, audience, issuer or expiry, without a verified email, or of an account outside the university's Google Workspace (no or another `hd`), never signs anybody in and never links to an account.
7. A suspended, inactive, locked or deleted account cannot sign in with Google.
8. `/register` and `/resend-verification` answer the same whether or not the address has an account.
9. No self-registered account has the role `SUPER_ADMIN`.
10. A mail server that is down does not fail a registration.
11. No log line contains an email address, a token or a password.
