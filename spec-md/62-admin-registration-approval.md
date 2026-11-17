# 62 - Admin self-registration: approval by a Super Admin, and the approval mail

Changes spec file 61 (admin self-registration with the form and with Google). Decision D87.

## 1. Request

Hsu, 2026-11-17: staff and students share the `@rsu.ac.th` domain, so with spec file 61 a student can register and becomes an Admin. Hsu chose the approval step: anyone at `@rsu.ac.th` can register, but the account cannot sign in until the admin team approves it. When it is approved, the person gets a mail.

## 2. Account states

| `account_status` | Meaning | Can sign in | Set by |
| --- | --- | --- | --- |
| `PENDING_VERIFICATION` | Form registration, email not proven yet. | No | System |
| `PENDING_APPROVAL` | **New.** Email proven (link or Google), waiting for a Super Admin. | No | System |
| `ACTIVE` | Normal account. | Yes | System (approval) or Super Admin |
| `INACTIVE`, `LOCKED`, `SUSPENDED` | Set by a Super Admin. | No | Super Admin |

`account_status` has no check constraint (V57 comment), so no migration is needed. Accounts that registered before this change keep their status.

## 3. Flows

### 3.1 Form

1. `POST /register`: unchanged (account `PENDING_VERIFICATION`, link mail, always `202`).
2. `POST /verify-email`: the account becomes **`PENDING_APPROVAL`** (was `ACTIVE`), `email_verified_at` is set. Answer `200 { email, accountStatus: "PENDING_APPROVAL", message }`. Mail: **waiting for approval** (section 5), instead of the welcome mail.
3. `POST /register` again for an address that is `PENDING_APPROVAL`: `202` as always, no change, and the waiting-for-approval mail again (so the person learns why nothing happens). `/resend-verification` sends nothing for it.

### 3.2 Google

| Situation | Before (spec 61) | Now |
| --- | --- | --- |
| No account | New `ACTIVE` account, tokens | New **`PENDING_APPROVAL`** account (linked, email proven), waiting mail, **`403 ACCOUNT_PENDING_APPROVAL`** |
| Unverified form registration | Activated, tokens | **`PENDING_APPROVAL`**, linked, form password replaced by an unknown one (as before), waiting mail, `403 ACCOUNT_PENDING_APPROVAL` |
| `PENDING_APPROVAL` account, linked or not | (did not exist) | Linked if it was not, no mail, `403 ACCOUNT_PENDING_APPROVAL` |
| `ACTIVE` account | Linked or signed in, tokens | Unchanged |
| Suspended, inactive, locked, deleted | `403 ACCOUNT_NOT_ACTIVE` | Unchanged |

A new account is saved before the `403` is answered (the database work commits first, as in spec 61).

### 3.3 Password sign-in

`POST /login` for a `PENDING_APPROVAL` account:

- right password: **`403 ACCOUNT_PENDING_APPROVAL`** "Your registration is waiting for approval by an administrator." (was `401 INVALID_CREDENTIALS`). The status is told only to somebody who knows the password, so no address can be probed with it.
- wrong password: `401 INVALID_CREDENTIALS`, and the failed attempt counts for the lock-out, as for any account.

A `PENDING_VERIFICATION` account still gets `401 INVALID_CREDENTIALS` (its owner has not proven the address).

## 4. Super Admin APIs

Under `/api/v1/admin/users`, Super Admin only, like the other user endpoints (full session, the actor checks of spec file 08).

### 4.1 `POST /{id}/approve`

- Only for a `PENDING_APPROVAL` account: it becomes `ACTIVE`, `updated_by` / `updated_at` record who and when.
- Answer `200` with the user detail (the body of `GET /{id}`).
- Then the **approved** mail (section 5). A failed mail does not undo the approval.
- `404 USER_NOT_FOUND`: unknown or deleted. `409 REGISTRATION_NOT_PENDING`: the account is not waiting for approval (also a second approve). No body is read.

### 4.2 `POST /{id}/reject`

- For a `PENDING_APPROVAL` or a `PENDING_VERIFICATION` account: the account is deleted the same way as `DELETE /{id}` (`is_deleted = TRUE`, audit columns) and a waiting verification link is removed.
- Answer `204`.
- A `PENDING_APPROVAL` account gets the **not approved** mail. A `PENDING_VERIFICATION` account gets no mail (nobody has proven the address).
- The deleted row keeps its email, so the same address cannot register again (form: the usual `202` without mail; Google: `403 ACCOUNT_NOT_ACTIVE`). This is the existing rule for every deleted account: its email stays taken.
- `404 USER_NOT_FOUND`, `409 REGISTRATION_NOT_PENDING` as in 4.1.

### 4.3 Other user endpoints

- `GET /users?accountStatus=PENDING_APPROVAL` lists the waiting registrations.
- `PATCH /{id}/status` and `PUT /{id}` cannot move an account out of `PENDING_VERIFICATION` or `PENDING_APPROVAL`: `409 REGISTRATION_DECISION_REQUIRED` "Approve or reject this registration first." Otherwise an account could be activated without the mail, or before its email was proven.
- `PUT /{id}` accepts the account's own pending status unchanged, so a Super Admin can enter the employee ID, name or role of a waiting registration before approving it. `PENDING_*` is accepted in `PUT` only when it equals the current status; `PATCH /{id}/status` still accepts only the four statuses a Super Admin can set.
- `DELETE /{id}`: unchanged (deletes without a mail).

## 5. Mails

Plain text, sent after the database work, a failed mail never fails the request (spec file 61, section 11).

| Kind | Subject | When | Content |
| --- | --- | --- | --- |
| `awaiting-approval` | `Your registration is waiting for approval` | Email verified with the link; first Google sign-in; Google finishes a form registration; `/register` again while waiting | Hsu's wording (D89), below. |
| `approved` | `Your account is approved` | `POST /{id}/approve` | Hsu's wording (D89), below. |
| `not-approved` | `Your registration was not approved` | `POST /{id}/reject` of a `PENDING_APPROVAL` account | The registration was not approved. Contact IT Support if this is a mistake. |

The welcome mail of spec file 61 ("Your registration is complete") is no longer sent; the approved mail takes its place. The verification mail and the "You already have an account" mail stay.

Text of the two mails, as Hsu gave it on 2026-11-17 (D89). `<first name>` is the account's first name ("there" when it is missing); `<login URL>` is `ADMIN_REGISTRATION_LOGIN_URL`.

Waiting for approval:

```text
Hello <first name>,

Your registration has been successfully completed and is currently awaiting admin approval. You will be notified once the approval process is complete.

Thank you for your patience.

You can sign in here:
<login URL>
```

Approved (no greeting and no link, as given):

```text
We are pleased to inform you that your submission has been successfully reviewed and approved by the administrator.
Thank you for your patience and cooperation.
Best regards,
Admin Team
```

## 6. Error codes (new)

| Status | Code | Where |
| --- | --- | --- |
| 403 | `ACCOUNT_PENDING_APPROVAL` | `/login` (right password), `/google` |
| 409 | `REGISTRATION_NOT_PENDING` | `/users/{id}/approve`, `/users/{id}/reject` |
| 409 | `REGISTRATION_DECISION_REQUIRED` | `PUT /users/{id}`, `PATCH /users/{id}/status` |

## 7. Files

| File | Change |
| --- | --- |
| `feature/authentication/entity/AppUser` | Constant `PENDING_APPROVAL`. |
| `feature/authentication/service/AdminRegistrationAccountService` (+ impl) | Verify and Google lead to `PENDING_APPROVAL`; new outcome kinds `AWAITING_APPROVAL` (register) and `PENDING_APPROVAL` (Google). |
| `feature/authentication/service/impl/AdminRegistrationServiceImpl` | Waiting mail, `403 ACCOUNT_PENDING_APPROVAL`. |
| `feature/authentication/service/AdminMailService` (+ impl) | `sendAwaitingApproval`, `sendApproved`, `sendRejected`; `sendWelcome` removed. |
| `feature/authentication/service/CredentialVerificationResult`, `CredentialVerificationServiceImpl`, `AuthenticationServiceImpl` | Section 3.3. |
| `feature/authentication/service/AdminAccountManagementService` (+ impl) | `approveRegistration`, `rejectRegistration`, the guard of 4.3. |
| `feature/user/controller/AdminUserController`, `service/AdminUserService` (+ impl) | The two endpoints; the mails after the transaction. |
| `feature/user/dto/request/UpdateAdminUserRequest`, `AdminUserListQuery` | `PENDING_*` values of 4.3. |
| Tests | `AdminRegistrationIntegrationTests`, `AdminRegistrationServiceTests`, `AdminMailServiceTests` changed for the new flow; new `AdminRegistrationApprovalIntegrationTests`. |

No Flyway migration.

## 8. Choices not given by the request

| Topic | Choice | Why |
| --- | --- | --- |
| Email check before approval | Kept | Without it somebody could register a staff member's address and, once approved, sign in with their own password. The approver only sees the name and the address. |
| Who approves | Super Admin | The user endpoints are Super Admin only today. |
| Reject | Added (`POST /{id}/reject`, with a "not approved" mail) | Without it a waiting registration can only be deleted silently. Delete stays available. |
| Rejected address | Cannot register again | The account is deleted like with `DELETE`, which keeps the email. Stops the same student from registering again and again. |
| Pending sign-in | `403 ACCOUNT_PENDING_APPROVAL` after the right password | Otherwise the person who did everything right would read "Invalid email or password." |
| Role at approval | Not chosen in the approve call | The default role stays `ADMIN`; a Super Admin can change it with `PUT /{id}` before or after approving. |
| Mail to Super Admins about a new registration | Not sent | Not asked. The admin web shows how many registrations are waiting (admin web spec 46). Can be added. |
| Accounts registered before this change | Unchanged | They are already active; a Super Admin can suspend or delete them. |

## 9. Tests

- Form: verify gives `PENDING_APPROVAL` and the waiting mail; login with the right password `403 ACCOUNT_PENDING_APPROVAL`, wrong password `401`; approve; approved mail; login works.
- Google: first sign-in creates a waiting account and answers `403`; again `403` without a new mail; after approval tokens.
- Reject: waiting account deleted, not-approved mail, no sign-in, no new registration; an unverified registration rejected without mail.
- Approve or reject a non-pending, unknown or deleted account; not a Super Admin `403`.
- `PUT` keeps a pending status and saves the employee ID; `PUT`/`PATCH` to another status `409`.
- List filter `PENDING_APPROVAL`.

## 10. Postman

```
POST {{baseUrl}}/api/v1/admin/users/{{userId}}/approve
Authorization: Bearer {{superAdminAccessToken}}

POST {{baseUrl}}/api/v1/admin/users/{{userId}}/reject
Authorization: Bearer {{superAdminAccessToken}}

GET  {{baseUrl}}/api/v1/admin/users?accountStatus=PENDING_APPROVAL
Authorization: Bearer {{superAdminAccessToken}}
```
