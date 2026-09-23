# 03 - RS256 JWT Key and Pre-Feature Infrastructure Setup

## 1. Project

**Smart University Student Assistant App**

This specification defines the required infrastructure setup for:

- RSA RS256 private key
- RSA RS256 public key
- Environment variable configuration
- Spring Boot JWT key configuration
- Docker key mounting
- PostgreSQL availability
- Redis availability
- Backend startup validation

This setup MUST be completed before implementing Authentication or any other business feature.

---

# 2. Mandatory Agent Instructions

Before making changes, the agent MUST:

1. Read `AGENTS.md`.
2. Inspect all files inside `spec-md/`.
3. Read `02-project-structure.md`.
4. Read the existing Docker/PostgreSQL/Redis setup specification.
5. Read this specification completely.
6. Inspect the current project structure.
7. Inspect `pom.xml`.
8. Inspect `application.yml`.
9. Inspect `docker-compose.yml`.
10. Inspect `.env`.
11. Inspect `.env.example`.
12. Inspect `.gitignore`.
13. Inspect `.dockerignore`.
14. Inspect existing `security/` and `config/` packages.
15. Do NOT implement Authentication business features in this task.

After implementation, the agent MUST verify the project structure again.

---

# 3. Current Scope

This task must configure:

```text
RS256 Key Pair
├── Private Key
└── Public Key

Environment Variables
├── JWT_PRIVATE_KEY_PATH
└── JWT_PUBLIC_KEY_PATH

Docker
├── PostgreSQL
├── Redis
└── Backend

Spring Boot
└── RS256 Key Loading Infrastructure