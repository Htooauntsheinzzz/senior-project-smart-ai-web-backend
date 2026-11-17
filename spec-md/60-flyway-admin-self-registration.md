# 60 — Flyway: Admin Self-Registration (User Facts, Google Links, Email Verifications)

**Project:** Smart AI University Student Assistant
**Module:** Authentication → database changes for Admin self-registration
**Scope:** Three Flyway migrations (V57, V58, V59). No Java code is described here; the API is spec file 61.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec files 04 and 05 (authentication tables and login). Latest Flyway migration is V56.
**Status:** Written and implemented 2026-11-16 (decision D86). Requested by Hsu.

---

## 1. Objective

Until now only a Super Admin could create an Admin web account. Spec file 61 adds two ways for a person to register by themself:

1. a registration form (email and password, then a verification link by email), and
2. Google sign-in.

Both are limited to `@rsu.ac.th` addresses. This spec defines what the database must store for that, and nothing more.

Hsu's requirement for this spec: **the structure must be cleanly normalized.** Every new table or column has to hold one kind of fact, in one place.

---

## 2. Mandatory Agent Instructions

Before implementation: read `Agent.md`, spec file 04 (the four authentication tables) and the migrations V2 to V7.

Do not:

- edit an applied migration (V1 to V56);
- put a Google id, a verification token or its expiry into `app_users` (see section 4);
- copy the user's email, name or status into a new table (they are read from `app_users`);
- add a cascading foreign key (`CampusSchemaMigrationTests` checks that none exists);
- store a verification token in readable form (only its SHA-256 hash);
- add a check constraint to `app_users.account_status` (tests insert other values on purpose; the application validates the status, as before).

---

## 3. What has to be stored

| Fact | Belongs to | How many per user |
| --- | --- | --- |
| How the account came into the system (Super Admin, form, Google) | the user | exactly one |
| When the owner proved the email address | the user | zero or one |
| The link to a Google account (Google's stable id of the person) | a user **and** a provider | zero or one per provider |
| A verification link that is waiting to be used (token hash, expiry) | a user, only for a limited time | zero or one |

The first two are facts about the user itself, so they are columns of `app_users`. The last two are separate things with their own keys and their own lifetime, so each gets its own table.

---

## 4. Normalization

**First normal form.** Every column holds one value. There is no list of providers in one column and no `google_id`, `facebook_id`, ... column group: a second provider is a new **row** in `app_user_external_identities`, not a new column.

**Second normal form.** Every table has the single-column key `id`, so no column can depend on only a part of a key. The two alternate keys of `app_user_external_identities` are `(provider, provider_subject)` and `(user_id, provider)`; `linked_at`, the only other column, depends on the whole link.

**Third normal form.** No column depends on another non-key column.

- `app_user_external_identities` does not store the email or the name of the person. Those depend on the user, not on the link, and already exist in `app_users`. Storing them twice would allow the two copies to disagree.
- `app_user_email_verifications` does not store the email either. The link is always sent to the current `app_users.email`.
- `app_users.email_verified_at` is not derived from another column: an account created by a Super Admin has no proof (`NULL`), a form registration gets it from the link, a Google account from Google.
- `app_users.registration_source` cannot be derived from the Google link: a person who registered with the form can link Google later and is still a form registration.

**No stale rows.** A row of `app_user_email_verifications` exists only while a link is waiting. It is replaced when a new link is sent and deleted when the email is verified; the lasting result is `app_users.email_verified_at`. So the table never holds "used" or "old" tokens.

**Why not columns on `app_users`?** `google_subject`, `verification_token_hash` and `verification_expires_at` on `app_users` would be `NULL` for almost every row, would mix a short-lived secret into the main user table, and would need new columns for every new sign-in provider.

**What is not changed:** `appuser_credentials` keeps `password_hash NOT NULL`. That table is the account's security record: the login code builds the "credential revision" of every session from it, so an account without a row cannot have a session. A Google-only account therefore gets the hash of a random password that nobody knows (spec file 61, section 9). No new column is needed for that.

---

## 5. V57 — `app_users` (file `V57__allow_self_registered_admin_users.sql`)

| Change | Details |
| --- | --- |
| `employee_id` | `DROP NOT NULL`. A self-registered user has no employee ID until a Super Admin enters it. `uk_app_users_employee_id` stays: PostgreSQL allows many `NULL`s in a unique column, and two users still cannot share one employee ID. |
| `registration_source` | New, `VARCHAR(20) NOT NULL DEFAULT 'ADMIN_CREATED'`. Check constraint `chk_app_users_registration_source`: `ADMIN_CREATED`, `SELF_FORM` or `GOOGLE`. Every existing row becomes `ADMIN_CREATED`. |
| `email_verified_at` | New, `TIMESTAMP` (nullable, same type as the other time columns of this table). `NULL` for every existing row. |

`account_status` gets one more value, `PENDING_VERIFICATION` (a form registration whose email is not verified yet). The column has no check constraint and gets none.

---

## 6. V58 — `app_user_external_identities` (file `V58__create_app_user_external_identities.sql`)

The link between an Admin web user and an account at an external sign-in provider.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `BIGINT` identity PK | |
| `user_id` | `BIGINT NOT NULL` | FK `fk_app_user_external_identities_user` to `app_users(id)`. |
| `provider` | `VARCHAR(30) NOT NULL` | Check constraint `chk_app_user_external_identities_provider`: `GOOGLE`. |
| `provider_subject` | `VARCHAR(255) NOT NULL` | The `sub` claim of the provider's ID token. It never changes, unlike an email address. |
| `linked_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | |

Constraints:

- `uk_app_user_external_identities_provider_subject` `UNIQUE (provider, provider_subject)`: one external account signs in as exactly one user.
- `uk_app_user_external_identities_user_provider` `UNIQUE (user_id, provider)`: one user has at most one account per provider. This index also serves "find the link of this user".

No extra index is needed: both lookups of spec file 61 (by provider and subject, by user and provider) use a unique constraint.

---

## 7. V59 — `app_user_email_verifications` (file `V59__create_app_user_email_verifications.sql`)

The pending email verification of a form registration.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `BIGINT` identity PK | |
| `user_id` | `BIGINT NOT NULL` | FK `fk_app_user_email_verifications_user` to `app_users(id)`. |
| `token_hash` | `VARCHAR(64) NOT NULL` | SHA-256 of the token, in hex. The token itself exists only in the email. |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | The time the link was sent. |

Constraints:

- `uk_app_user_email_verifications_user` `UNIQUE (user_id)`: at most one waiting link per user. A new link replaces the row.
- `uk_app_user_email_verifications_token_hash` `UNIQUE (token_hash)`: the lookup of a link.
- `chk_app_user_email_verifications_expiry` `CHECK (expires_at > created_at)`.

---

## 8. Relationships

```text
app_users 1 ──── 0..1 appuser_credentials            (existing)
app_users 1 ──── 0..n app_user_roles ──── app_roles   (existing)
app_users 1 ──── 0..n app_user_external_identities    (new; 0..1 per provider)
app_users 1 ──── 0..1 app_user_email_verifications    (new; only while a link is waiting)
```

A user that still has a link or a waiting verification cannot be deleted from `app_users` (the foreign keys do not cascade). The application never deletes users; it sets `is_deleted`.

---

## 9. Deployment

- The three migrations run on the next backend start. They only add: no existing row changes its meaning, and every existing account stays `ADMIN_CREATED` with its employee ID.
- Back up the database first, as before every migration (`pg_dump`).
- No data is seeded.

---

## 10. Acceptance Criteria

1. `./mvnw clean package` passes and Flyway reports version 59.
2. `app_users` has `registration_source` and `email_verified_at`, `employee_id` is nullable, and no column for a Google id or a token.
3. The same Google account cannot be linked to two users, and one user cannot have two Google accounts (unique constraints).
4. A user cannot have two waiting verifications, and two users cannot have the same token hash.
5. A provider other than `GOOGLE`, a registration source outside the three values, a link to a missing user and an expiry before the creation time are rejected by the database.
6. No new foreign key cascades.
7. The public table count is 50 and the four authentication tables have 36 columns together (`AuthenticationIntegrationTests.migrationHistoryAndSchemaStayValid`).
