# Smart University Student Assistant — Spring Boot Project Structure

## Purpose

The backend must use a feature-based package structure. Each business feature owns its controllers, DTOs, mappers, entities, services, repositories, and feature-specific exceptions. Shared infrastructure belongs outside business features.

Store this specification at `spec-md/02-project-structure.md` in the backend repository.

## Required structure

Use the base package already configured in the project. The example below uses `com.smartuniversity` as a placeholder; do not rename the existing base package merely to match this example.

```text
backend/
├── AGENTS.md
├── pom.xml
├── spec-md/
│   ├── 02-project-structure.md
│   └── <module-specifications>.md
└── src/
    ├── main/
    │   ├── java/com/smartuniversity/
    │   │   ├── SmartUniversityApplication.java
    │   │   ├── config/
    │   │   │   ├── RedisConfig.java
    │   │   │   └── <other-shared-configuration>.java
    │   │   ├── security/
    │   │   │   └── <shared-security-classes>.java
    │   │   ├── common/
    │   │   │   └── <shared-utilities-and-types>.java
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── <shared-exception-types>.java
    │   │   └── feature/
    │   │       ├── authentication/
    │   │       │   ├── controller/
    │   │       │   ├── dto/
    │   │       │   ├── mapper/
    │   │       │   ├── entity/
    │   │       │   ├── service/
    │   │       │   │   └── impl/
    │   │       │   ├── repository/
    │   │       │   └── exception/
    │   │       ├── faculty/
    │   │       │   ├── controller/
    │   │       │   ├── dto/
    │   │       │   ├── mapper/
    │   │       │   ├── entity/
    │   │       │   ├── service/
    │   │       │   │   └── impl/
    │   │       │   ├── repository/
    │   │       │   └── exception/
    │   │       └── <other-business-feature>/
    │   │           ├── controller/
    │   │           ├── dto/
    │   │           ├── mapper/
    │   │           ├── entity/
    │   │           ├── service/
    │   │           │   └── impl/
    │   │           ├── repository/
    │   │           └── exception/
    │   └── resources/
    │       ├── application.yml
    │       └── db/
    │           └── migration/
    │               └── V1__initial_schema.sql
    └── test/
        ├── java/com/smartuniversity/
        │   └── <packages-mirroring-main-source>/
        └── resources/
```

The tree defines ownership and placement, not a requirement to generate unused classes or empty-package marker files. Create each package when its first required class is implemented.

## Business feature responsibilities

| Package | Responsibility |
| --- | --- |
| `feature.<feature>.controller` | HTTP endpoints, request validation, and delegation to services. |
| `feature.<feature>.dto` | Feature request and response models; optional `request` and `response` subpackages. |
| `feature.<feature>.mapper` | Conversion between feature entities and DTOs. |
| `feature.<feature>.entity` | Persistence entities owned by the feature. |
| `feature.<feature>.service` | Service interfaces defining the feature's business operations. |
| `feature.<feature>.service.impl` | Implementations of those interfaces, business rules, and transaction boundaries. |
| `feature.<feature>.repository` | Persistence access for the feature's entities. |
| `feature.<feature>.exception` | Exceptions specific to the feature, such as `FacultyNotFoundException`. |

Use singular package names: `service`, `repository`, and `exception`. Do not create parallel packages named `services`, `repositories`, or `exceptions`.

Controllers must not access repositories directly. Keep business rules in services, persistence access in repositories, and mapping in mappers. Feature-specific types stay with their owning feature.

## Shared package responsibilities

- `config`: Shared Redis, database infrastructure, Jackson, OpenAPI, CORS, and other application-wide configuration. Database connection properties remain in application configuration, with environment-specific values supplied externally.
- `security`: Shared security configuration, JWT support, filters, authentication infrastructure, and authorization helpers. Authentication endpoints, DTOs, and business services remain in `feature.authentication`.
- `common`: Utilities and types genuinely reused across features. Do not move feature-specific classes here merely for convenience.
- `exception`: Central global exception handling and shared exception types. The global handler may handle exceptions declared inside features; do not duplicate global handlers in each feature.

Do not duplicate Redis, database, or shared security infrastructure inside feature packages. Do not create application-wide business-layer packages such as root-level `controller`, `dto`, `mapper`, `entity`, `service`, or `repository`.

## Resources and migrations

Place Flyway migrations exclusively in `src/main/resources/db/migration/`. Use Flyway filenames such as `V1__initial_schema.sql` and `V2__create_faculty_table.sql`, following the project's existing version convention. Keep versions unique across all features. Add new migrations rather than rewriting migrations already applied to shared environments.

Keep application configuration under `src/main/resources/`. Reuse the project's existing `application.yml` or `application.properties` convention. Never commit production secrets.

## Mandatory implementation workflow

Before coding, agents must:

1. Read the applicable `AGENTS.md` instructions.
2. Inspect all files in `spec-md/`.
3. Read `spec-md/02-project-structure.md` completely.
4. Read the relevant module specification and any related dependency specifications.
5. Inspect the current main and test source trees, existing packages, and relevant classes.
6. Reuse existing classes and packages, assigning new classes to their owning feature or shared package.

After every implementation, and before reporting completion, agents must re-check the source tree, package declarations, imports, feature ownership, shared infrastructure placement, and migration location. Correct duplicate packages or misplaced classes introduced by the change, and run the checks appropriate to the implementation.
