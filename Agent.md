# AGENTS.md

# Smart University Student Assistant App

## Project Description

Smart University Student Assistant App is a senior Computer Science project designed to provide university students with intelligent access to academic information and university services.

The system will contain:

- Admin Web Application
- Academic Management System
- Student Academic Services
- AI-powered natural language assistant
- University knowledge retrieval
- Student timetable and academic information
- Campus information and navigation
- Student Mobile Application in a later development phase

The project is being developed incrementally.

The current development priority is:

1. Backend foundation
2. Admin authentication and authorization
3. Academic Management
4. Student academic data
5. AI integration using Google Gemini
6. AI testing through the Admin Web application
7. AI evaluation and research testing
8. Student Mobile Application later

The mobile application is NOT the current development priority.

---

# 1. MOST IMPORTANT RULE — READ `spec-md/` FIRST

Before implementing, modifying, generating, refactoring, or deleting code for ANY module, you MUST inspect the
Mandatory Agent Rule
   Before creating, modifying, moving, or deleting Java files, the agent MUST:
1. Read AGENTS.md.
2. Inspect the complete spec-md/ directory.
3. Read this 02-project-structure.md.
4. Read the specification related to the requested feature.
5. Inspect the current project structure.
6. Locate existing packages and classes.
7. Follow this project structure exactly.
8. Do NOT create duplicate packages.
9. Do NOT create a new architectural pattern without explicit permission.
10. Re-check the project structure after implementation.
```text
spec-md/


Mandatory package architecture

Follow the feature-based architecture defined in `spec-md/02-project-structure.md`. Preserve the existing base package.

Each business feature must own its layers under:

```text
<base-package>/feature/<feature-name>/
├── controller/
├── dto/
├── mapper/
├── entity/
├── service/
│   └── impl/
├── repository/
└── exception/
```

- Put service interfaces in `service` and their implementations in `service/impl`.
- Use singular package names consistently. Do not introduce `services`, `repositories`, or `exceptions` alongside the required names.
- Keep feature-specific exceptions inside the owning feature's `exception` package.
- Create packages when needed; do not generate unused placeholder classes to populate the structure.
- Keep tests in packages that mirror the main source ownership.
- Do not introduce a different architectural pattern without an explicit user request or approval.

## Shared infrastructure

Use these top-level packages under the base package:

| Package | Contents |
| --- | --- |
| `config` | Shared Redis, database infrastructure, Jackson, OpenAPI, CORS, and other application-wide configuration. |
| `security` | Shared security configuration, filters, JWT support, and security helpers. |
| `common` | Genuinely shared utilities and types. |
| `exception` | Global exception handling and shared exception types. |

Keep shared infrastructure out of business features. Authentication business endpoints and services belong in `feature.authentication`; shared security infrastructure belongs in `security`.

Place Flyway migrations in `src/main/resources/db/migration/`. Follow the existing version convention, ensure versions are globally unique, and add migrations instead of modifying migrations already applied to shared environments.

## Prevent duplication and global business layers

- Search for an existing package or class before adding one.
- Reuse existing valid configuration and implementation where appropriate.
- Do not create application-wide `controller`, `dto`, `mapper`, `entity`, `service`, or `repository` packages outside `feature`.
- Do not create duplicate feature packages, duplicate infrastructure configuration, or separate global exception handlers for each feature.
- Keep controllers focused on HTTP concerns, services on business logic, repositories on persistence, and mappers on conversions.
- Avoid unrelated changes and never commit production secrets.

## Mandatory review after every implementation

Before reporting any implementation complete:

1. Inspect the resulting source tree again.
2. Verify that every added or changed class belongs to the correct feature or shared package.
3. Verify that package declarations match file paths and imports resolve after any moves.
4. Confirm that feature layers use the required names and service implementations are in `service/impl`.
5. Confirm that no duplicate packages or global business-layer packages were introduced.
6. Confirm that shared configuration, security, utilities, and global exception handling remain in their top-level packages.
7. Confirm that Flyway migrations are in `src/main/resources/db/migration/` and have unique versions.
8. Check the implementation against the relevant module specification and run appropriate build, test, and validation checks.
9. Fix violations introduced by the implementation before completion. Report any unresolved pre-existing issues or checks that could not run accurately.

The final structure review is required after every implementation, including small fixes and refactoring.