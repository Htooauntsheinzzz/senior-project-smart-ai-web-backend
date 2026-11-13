# 45 — Step 14A: RAG Document Processing — Flyway Migration and pgvector Setup

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** PostgreSQL image with pgvector, Flyway migrations, test infrastructure. No Java feature code.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4, Flyway
**Prerequisites:** Step 12A, 12B and 13 are complete (spec files 37, 38, 39; decisions D53, D54, D55)
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

The heading number of this file matches its file number (as in files 43 and 44). Older files have headings that are one higher than their file number; always go by the file number.

---

## 1. Objective

Step 14 makes uploaded knowledge documents searchable by the AI assistant:

```text
PDF / DOCX / TXT / XLSX upload        (done, spec file 39)
        ↓
Extract Text                          (spec file 47)
        ↓
Clean Text                            (spec file 48)
        ↓
Split into Chunks                     (spec file 49)
        ↓
Create Embeddings                     (spec file 50)
        ↓
Store in Vector Database              (spec file 51)
        ↓
Semantic Search                       (spec file 52)
        ↓
AI Assistant knowledge tool           (spec file 53)
```

This first file prepares the database for all of them:

1. Run PostgreSQL with the `pgvector` extension (development Compose, tests).
2. Enable the extension through Flyway.
3. Extend `academic_document_processing` for the ingestion worker.
4. Create `academic_document_pages` (extracted and cleaned text).
5. Create `academic_document_chunks` (chunks and their embedding vectors).

Do not implement extraction, the worker, embeddings, search or any controller in this task.

---

## 2. Step 14 Map

| File | Step | Feature | What exists after it |
|---|---|---|---|
| **45** | **14A** | **Flyway migration and pgvector setup (this file)** | Schema and infrastructure |
| 46 | 14B | Ingestion worker, queue and reprocess | Jobs are queued, claimed, retried, recovered |
| 47 | 14C | Extract text | Raw text per page in `academic_document_pages` |
| 48 | 14D | Clean text | Cleaned text per page |
| 49 | 14E | Split into chunks | Rows in `academic_document_chunks` |
| 50 | 14F | Create embeddings | Vectors for every chunk |
| 51 | 14G | Store in vector database | Verified index, status `READY` |
| 52 | 14H | Semantic search | `KnowledgeSearchService` and an Admin search API |
| 53 | 14I | AI assistant knowledge tool | The assistant answers from documents, with sources |

Implement the files in this order. Each file is one feature and ends with its own tests and acceptance criteria.

### Processing status mapping

No new status is added. The seven statuses of spec file 37 are kept and mapped onto the pipeline stages:

| Stage (spec file) | `academic_document_processing.status` while it runs |
|---|---|
| waiting for the worker (46) | `QUEUED` |
| `EXTRACT` (47), `CLEAN` (48) | `PROCESSING` |
| `CHUNK` (49) | `CHUNKING` |
| `EMBED` (50), `INDEX` (51) | `EMBEDDING` |
| all stages succeeded | `READY` |
| any stage failed | `FAILED`, with the new `failed_stage` column |

---

## 3. Mandatory Agent Instructions

Before creating any file:

1. Read `Agent.md` and `spec-md/02-project-structure.md`.
2. Read spec files 37, 38 and 39 and decisions D53, D54 and D55 in `spec-md/00-changes-and-decision.md`.
3. Read all nine Step 14 files (45 to 53) once, so the schema is understood in context.
4. Inspect every existing Flyway migration and determine the real next version. On 2026-11-10 the latest was `V47__add_enrollment_due_date_to_meetings.sql`, so the expected versions are V48 to V51. **Never assume; check the folder.**
5. Inspect `compose.yaml`, `Dockerfile`, `.env.example` and `IntegrationSupport` (test containers).
6. Record the test baseline first: run `./mvnw test` before changing anything and keep the totals. D59 recorded failures that existed before this step; report new results against that baseline and never claim a green build that was not observed.
7. Use PostgreSQL SQL. Keep `spring.jpa.hibernate.ddl-auto=validate`.
8. Do not modify an applied migration.
9. Do not insert demonstration documents, pages or chunks.

Do not:

- Create a second database, a separate vector database service (Qdrant, Milvus, Chroma, Pinecone) or a second schema for AI data.
- Store vectors in Redis.
- Add Java entities, repositories, services or controllers for pages and chunks in this task (they belong to spec files 46 to 51).
- Add PDF, Office or HTTP client dependencies in this task.
- Change the upload API or its initial `UPLOADED` status.

---

## 4. What Exists Today (verified 2026-11-10)

| Fact | Where |
|---|---|
| Documents, file revisions and processing rows | V21 to V25, `feature/knowledge/entity` |
| Upload stores the file and creates a processing row with status `UPLOADED` | `KnowledgeDocumentServiceImpl.upload` / `revision` (D55) |
| `POST /api/v1/admin/knowledge/documents/{id}/reprocess` always answers 503 `DOCUMENT_PROCESSING_NOT_AVAILABLE` | `KnowledgeDocumentServiceImpl.reprocess` (D55) |
| `GET /{id}/processing` returns `ingestionAvailable: false` | `KnowledgeDocumentProcessingResponse` |
| Allowed status transitions are coded but unused | `DocumentProcessingStatus.canTransitionTo` |
| Stored files are opened by key | `FileStorageService.open(storageKey)` |
| PostgreSQL image | `postgres:17-alpine` in `compose.yaml` and in `IntegrationSupport` |
| `pgvector` is **not** installed in that image | `postgres:17-alpine` ships no `vector` extension |
| Datasource limits | `socketTimeout=5` seconds and `jakarta.persistence.query.timeout=5000` in `application.properties`; tests use `socketTimeout=1` |
| Migration tests migrate into throwaway schemas and call `flyway.clean()` | `KnowledgeSchemaMigrationTests`, `CampusSchemaMigrationTests`, `TimetableSchemaMigrationTests`, `SuperAdminMigrationTests` |

---

## 5. Design Decisions Fixed for Step 14

These decisions apply to all nine files. A later file refers to them by number (R1 to R13).

| # | Decision | Reason |
|---|---|---|
| R1 | The vector store is `pgvector` inside the existing `smart_university_db`. | Spec file 39 section 54 proposed it; spec file 37 requires "no second AI database". One backup, one transaction model, SQL joins for access rules. |
| R2 | Embeddings come from an OpenAI-compatible `/embeddings` endpoint. Default model for Gemini: `gemini-embedding-001`, requested with **768** dimensions, L2-normalized by the backend, compared with cosine distance. | The chat client already uses the OpenAI-compatible Gemini endpoint (D45). 768 is small, indexable by HNSW (limit 2,000) and enough for this corpus. |
| R3 | The ingestion worker runs inside the Spring Boot application as a scheduled poller. The queue is the `academic_document_processing` table. | No new infrastructure. The queue survives restarts. Redis stays a cache and session store. |
| R4 | Every stage persists its output (pages → cleaned pages → chunks → vectors) and the next stage reads it from PostgreSQL. | Each stage is testable and inspectable on its own, a crash loses no finished stage, and embedding can resume without paying for the same chunks twice. |
| R5 | No new processing status. Two transitions are added in spec file 46: `QUEUED → FAILED` (cancelled) and `READY → QUEUED` (full reprocess). | Keeps V25's CHECK constraint, the dashboard counters and the Admin Web badges unchanged. |
| R6 | The upload contract does not change: an upload still commits and returns `UPLOADED`. The worker moves it to `QUEUED`. | Spec file 39 and its tests stay valid. Documents uploaded before Step 14 are picked up the same way. |
| R7 | Only the **current** revision of a non-deleted document is indexed. Pages and chunks of replaced revisions and deleted documents are removed. Archived documents keep their index but are never returned by search. | Spec file 37 section 10. Re-activating an archived document then costs no new embedding quota. |
| R8 | Extraction uses Apache PDFBox and Apache POI. No OCR. No LLM is used to extract, clean or chunk. | Deterministic, testable, free, and no document text leaves the server before the embedding stage. |
| R9 | Chunking is character-based and structure-aware, and never splits inside a Thai, Myanmar or other grapheme cluster. Defaults: target 1,000, maximum 1,500, overlap 150 characters. | The documents are Thai and English; Thai has no spaces between words, so word or whitespace token counts are unreliable. |
| R10 | A chunk is retrievable only when the document is active and not deleted, the file is current, the status is `READY`, a linked curriculum version is `ACTIVE`, and the chunk was embedded with the configured model. A Student additionally sees only university-wide documents and documents of their own department, program and active curriculum. | Spec file 37 section 10, spec file 38 section 7, spec file 39 section 35. |
| R11 | All Step 14 classes live in the existing `feature/knowledge` package with flat layer folders. Bare DTO responses, `ApiError` errors through `KnowledgeFailure`. | `Agent.md`, D54, D57, D58. |
| R12 | Automated tests never call a real provider. They use a deterministic fake embedding client. A live evaluation is opt-in. | Same rule as D50 (Step 9). The Gemini free tier is small. |
| R13 | The assistant gets one new read-only tool. The Student's scope comes from the token. Step 11 grounding is extended to document evidence. | D46, D48, D52. |

If the product owner wants a different choice for any row, change it here first, then implement.

---

## 6. PostgreSQL Image with pgvector

`CREATE EXTENSION vector` only works when the extension files are installed in the PostgreSQL image.

### 6.1 Development and deployment (Compose) — Option A, default

**Confirmed by the product owner on 2026-11-10: use Option A.**

> Changed by D74: the stack now uses pgvector/pgvector:pg17. On 2026-11-10, after the restore was verified, the `docker/postgres/Dockerfile` below and the old `postgres_data` volume were removed; this section is kept for reference only.

Keep `postgres:17-alpine` as the base and compile pgvector into it. The existing `postgres_data` volume then keeps working with the same PostgreSQL major version and the same C library, so no data migration and no reindex is needed.

Create `docker/postgres/Dockerfile` (a starting point; build it and adjust until `CREATE EXTENSION vector` succeeds):

```dockerfile
# PostgreSQL 17 on Alpine (the same base as before) plus the pgvector extension.
ARG PG_IMAGE=postgres:17-alpine

FROM ${PG_IMAGE} AS build
# Pin a released pgvector tag. Check https://github.com/pgvector/pgvector/tags for the latest 0.8.x.
ARG PGVECTOR_VERSION=v0.8.0
RUN apk add --no-cache build-base git
RUN git clone --depth 1 --branch ${PGVECTOR_VERSION} https://github.com/pgvector/pgvector.git /tmp/pgvector \
 && cd /tmp/pgvector \
 && make OPTFLAGS="" with_llvm=no \
 && make install with_llvm=no

FROM ${PG_IMAGE}
COPY --from=build /usr/local/lib/postgresql/vector.so /usr/local/lib/postgresql/vector.so
COPY --from=build /usr/local/share/postgresql/extension/vector* /usr/local/share/postgresql/extension/
```

Notes:

- `OPTFLAGS=""` avoids CPU-specific instructions so the image runs on every developer machine (Intel and Apple Silicon).
- `with_llvm=no` skips JIT bitcode, so no clang toolchain is needed.
- Confirm the two copy paths with `pg_config --pkglibdir` and `pg_config --sharedir` inside the build stage.
- Use pgvector 0.8.0 or newer (spec file 52 uses `hnsw.iterative_scan`, added in 0.8.0).

Change only the `postgres` service in `compose.yaml`:

```yaml
  postgres:
    build:
      context: ./docker/postgres
    image: smart-university-postgres:17-pgvector
    # environment, ports, volumes, healthcheck and restart stay exactly as they are
```

Do not rename or recreate the `postgres_data` volume.

### 6.2 Option B (only with the product owner's approval)

> Changed by D74: the stack now uses pgvector/pgvector:pg17.

Not chosen. The product owner confirmed Option A on 2026-11-10 because both options perform the same and Option A keeps the existing data as it is. This section is kept for reference only.

Use the prebuilt `pgvector/pgvector:pg17` image. It is Debian-based (glibc) while the current data was created on Alpine (musl). Text indexes built under one C library are not guaranteed to be valid under the other. If Option B is chosen:

1. Take a `pg_dump` backup first.
2. After the first start on the new image run `REINDEX DATABASE smart_university_db;` and `ALTER DATABASE smart_university_db REFRESH COLLATION VERSION;`.
3. Record the choice in `00-changes-and-decision.md`.

### 6.3 Before switching the image

```bash
docker compose exec postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backup-before-step14.dump
docker compose build postgres
docker compose up -d postgres
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT name, default_version FROM pg_available_extensions WHERE name = '"'"'vector'"'"';"'
```

The last command must return one row. Keep the dump out of git (`*.dump` is already ignored).

### 6.4 Tests (Testcontainers)

`IntegrationSupport` starts `postgres:17-alpine`. Change it to an image that contains pgvector:

```java
protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
```

Test databases are created fresh, so the C library difference of section 6.2 does not matter there. Use `asCompatibleSubstituteFor` only if the container class rejects the image name without it.

### 6.5 Privileges

`CREATE EXTENSION vector` needs a role that may create extensions. The Compose and Testcontainers users are superusers. For a managed production database where the application role cannot create extensions, an administrator runs `CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;` once; the migration then passes because of `IF NOT EXISTS`.

---

## 7. Final Database Design

Existing tables (unchanged except where stated):

```text
academic_documents
   | 1:N
   v
academic_document_files
   | 1:1
   v
academic_document_processing      (+ 4 columns in this step)
```

New tables:

```text
academic_document_files
   | 1:N
   +------------------------+
   v                        v
academic_document_pages   academic_document_chunks
(raw + cleaned text)      (chunk text + embedding vector)
```

Both new tables belong to a **file revision**, not to the logical document, because every revision has its own text. A chunk refers to its pages by number (`page_start`, `page_end`), not by foreign key.

No foreign key cascades. Rows are deleted explicitly by the backend (R7), as everywhere else in this schema (D53).

---

## 8. Migration 1 — Enable pgvector

File: `V{next}__enable_pgvector_extension.sql`

```sql
-- Step 14A: embeddings are stored in the existing PostgreSQL database with pgvector (no second AI database).
-- The extension always lives in schema public, whatever Flyway's default schema is. Migration tests migrate into
-- throwaway schemas and clean them; an extension created inside such a schema would be dropped with CASCADE.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
```

Rules:

- Always write `WITH SCHEMA public`.
- In every migration, schema-qualify the type and the operator class: `public.vector(768)`, `public.vector_cosine_ops`. A test schema does not necessarily have `public` on its search path.
- Runtime queries (spec files 51 and 52) run in the application's normal `public` schema and may use `vector` and `<=>` unqualified.

---

## 9. Migration 2 — Extend academic_document_processing

File: `V{next+1}__extend_academic_document_processing_for_ingestion.sql`

| New column | Type | Meaning |
|---|---|---|
| `failed_stage` | VARCHAR(20) NULL | Stage of the last failure: `EXTRACT`, `CLEAN`, `CHUNK`, `EMBED`, `INDEX`. NULL when the last run did not fail in a stage, and after a full reprocess. Also the resume hint (spec file 46). |
| `queued_at` | TIMESTAMP NULL | When the row last entered `QUEUED`. The worker takes the oldest first. |
| `queued_by` | BIGINT NULL, FK `app_users.id` | Admin who requested processing (the uploader for the first run, the Admin who called reprocess later). NULL only for rows that were never queued. Never taken from a request body. |
| `next_attempt_at` | TIMESTAMP NULL | When a retryable failure may be queued again automatically. NULL means no automatic retry. |

```sql
ALTER TABLE academic_document_processing
    ADD COLUMN failed_stage VARCHAR(20),
    ADD COLUMN queued_at TIMESTAMP,
    ADD COLUMN queued_by BIGINT,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD CONSTRAINT fk_academic_document_processing_queued_by
        FOREIGN KEY (queued_by) REFERENCES app_users(id),
    ADD CONSTRAINT chk_academic_document_processing_failed_stage
        CHECK (
            failed_stage IS NULL
            OR failed_stage IN ('EXTRACT', 'CLEAN', 'CHUNK', 'EMBED', 'INDEX')
        );
```

Unchanged on purpose:

- The status CHECK constraint (no new status, R5).
- `attempt_count`, `started_at`, `completed_at`, `last_error_code`, `last_error_message`, `updated_at`. Spec file 46 defines how the worker uses them. `updated_at` is also the worker's heartbeat.
- No new index: the table has one row per file revision and `idx_academic_document_processing_status` already exists.

Page, chunk and embedded-chunk counts are **not** stored here. They are derived with `COUNT` from the two new tables (same rule as the dashboard counters, spec file 37 section 11).

---

## 10. Migration 3 — academic_document_pages

File: `V{next+2}__create_academic_document_pages.sql`

Purpose: the text of one file revision, unit by unit, before and after cleaning.

A "page" is the unit the source format provides:

| `unit_type` | Source | `page_number` | `unit_label` |
|---|---|---|---|
| `PAGE` | PDF | real page number, starting at 1 | NULL |
| `SECTION` | DOCX | running number of the section, starting at 1 | the section heading, if any |
| `SHEET` | XLSX | running number of the visible sheet, starting at 1 | the sheet name |
| `TEXT` | TXT | running number, starting at 1 | NULL |

| Column | Type | Constraint |
|---|---|---|
| `id` | BIGINT | Identity PK |
| `file_id` | BIGINT | NOT NULL, FK `academic_document_files.id` |
| `page_number` | INTEGER | NOT NULL, > 0, unique within the file |
| `unit_type` | VARCHAR(20) | NOT NULL, one of the four values above |
| `unit_label` | VARCHAR(255) | NULL |
| `raw_text` | TEXT | NOT NULL (an empty page is the empty string) |
| `raw_char_count` | INTEGER | NOT NULL, >= 0 |
| `cleaned_text` | TEXT | NULL until the `CLEAN` stage ran |
| `cleaned_char_count` | INTEGER | NULL until the `CLEAN` stage ran |
| `created_at` | TIMESTAMP | NOT NULL |

```sql
CREATE TABLE academic_document_pages (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    file_id BIGINT NOT NULL,
    page_number INTEGER NOT NULL,
    unit_type VARCHAR(20) NOT NULL DEFAULT 'PAGE',
    unit_label VARCHAR(255),
    raw_text TEXT NOT NULL,
    raw_char_count INTEGER NOT NULL,
    cleaned_text TEXT,
    cleaned_char_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_academic_document_pages_file
        FOREIGN KEY (file_id)
        REFERENCES academic_document_files(id),

    CONSTRAINT uk_academic_document_pages_file_page
        UNIQUE (file_id, page_number),

    CONSTRAINT chk_academic_document_pages_number
        CHECK (page_number > 0),

    CONSTRAINT chk_academic_document_pages_unit_type
        CHECK (unit_type IN ('PAGE', 'SECTION', 'SHEET', 'TEXT')),

    CONSTRAINT chk_academic_document_pages_counts
        CHECK (
            raw_char_count >= 0
            AND (cleaned_char_count IS NULL OR cleaned_char_count >= 0)
            AND ((cleaned_text IS NULL) = (cleaned_char_count IS NULL))
        )
);
```

The unique constraint is also the index for "all pages of a file in order". No further index.

Both texts are kept: the raw text shows what the extractor produced, the cleaned text is what gets chunked. This is small (a 500-page handbook is a few megabytes) and makes every stage inspectable for testing and for the project report.

---

## 11. Migration 4 — academic_document_chunks

File: `V{next+3}__create_academic_document_chunks.sql`

Purpose: the retrievable passages of one file revision and their embedding vectors.

| Column | Type | Constraint / meaning |
|---|---|---|
| `id` | BIGINT | Identity PK |
| `file_id` | BIGINT | NOT NULL, FK `academic_document_files.id` |
| `chunk_index` | INTEGER | NOT NULL, >= 0, order inside the file, unique within the file |
| `content` | TEXT | NOT NULL, the chunk text shown to the model and quoted to the Student |
| `section_title` | VARCHAR(500) | NULL, the heading the chunk belongs to |
| `content_hash` | CHAR(64) | NOT NULL, SHA-256 (lower-case hex) of the embedding input (spec file 49) |
| `char_count` | INTEGER | NOT NULL, > 0 |
| `token_estimate` | INTEGER | NOT NULL, > 0 |
| `page_start` | INTEGER | NOT NULL, > 0, first `page_number` the chunk covers |
| `page_end` | INTEGER | NOT NULL, >= `page_start` |
| `embedding` | `public.vector(768)` | NULL until the `EMBED` stage stored it |
| `embedding_model` | VARCHAR(100) | NULL until embedded; the model that produced the vector |
| `embedded_at` | TIMESTAMP | NULL until embedded |
| `created_at` | TIMESTAMP | NOT NULL |

```sql
CREATE TABLE academic_document_chunks (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    file_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    section_title VARCHAR(500),
    content_hash CHAR(64) NOT NULL,
    char_count INTEGER NOT NULL,
    token_estimate INTEGER NOT NULL,
    page_start INTEGER NOT NULL,
    page_end INTEGER NOT NULL,
    embedding public.vector(768),
    embedding_model VARCHAR(100),
    embedded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_academic_document_chunks_file
        FOREIGN KEY (file_id)
        REFERENCES academic_document_files(id),

    CONSTRAINT uk_academic_document_chunks_file_index
        UNIQUE (file_id, chunk_index),

    CONSTRAINT chk_academic_document_chunks_index
        CHECK (chunk_index >= 0),

    CONSTRAINT chk_academic_document_chunks_size
        CHECK (char_count > 0 AND token_estimate > 0),

    CONSTRAINT chk_academic_document_chunks_pages
        CHECK (page_start > 0 AND page_end >= page_start),

    CONSTRAINT chk_academic_document_chunks_embedding
        CHECK (
            (embedding IS NULL AND embedding_model IS NULL AND embedded_at IS NULL)
            OR (embedding IS NOT NULL AND embedding_model IS NOT NULL AND embedded_at IS NOT NULL)
        )
);

-- Reuse of an existing vector for identical text (spec file 50).
CREATE INDEX idx_academic_document_chunks_content_hash
ON academic_document_chunks(content_hash, embedding_model)
WHERE embedding IS NOT NULL;

-- Approximate nearest-neighbour search by cosine distance (spec files 51 and 52). NULL vectors are not indexed.
CREATE INDEX idx_academic_document_chunks_embedding_hnsw
ON academic_document_chunks
USING hnsw (embedding public.vector_cosine_ops);
```

Why one table for chunk and vector: a vector has no meaning without its chunk, they are written and deleted together, and search needs both in one row. A separate embeddings table would only add a join.

**The dimension 768 is part of the schema.** `AI_EMBEDDING_DIMENSIONS` (spec file 50) must be 768. Changing the embedding dimension later needs a new migration and a full re-embedding of every document. Changing only the model (same dimension) needs no migration, but vectors of two models must never be compared, so search filters on `embedding_model` (R10).

---

## 12. Flyway File Order

```text
src/main/resources/db/migration/

V{next}__enable_pgvector_extension.sql
V{next+1}__extend_academic_document_processing_for_ingestion.sql
V{next+2}__create_academic_document_pages.sql
V{next+3}__create_academic_document_chunks.sql
```

Expected on 2026-11-10: V48, V49, V50, V51. Inspect the folder before assigning numbers.

All four migrations run on empty new tables or add nullable columns, so each finishes far below the 5 second statement limit of the datasource. A future migration that builds an index on a populated chunk table must take that limit into account.

---

## 13. JPA Notes for the Later Files

- No entity is created in this task. `ddl-auto=validate` only checks mapped entities, so the two unmapped tables and the four unmapped columns do not break startup. Verify this by starting the application.
- Spec file 46 maps the four new processing columns on `AcademicDocumentProcessing`.
- Spec file 47 creates `AcademicDocumentPage`; spec file 49 creates `AcademicDocumentChunk`.
- `content_hash CHAR(64)` needs `@JdbcTypeCode(SqlTypes.CHAR)`, like `sha256_checksum` (D53).
- The `embedding` column is **not** mapped as an entity attribute. It is written and read only through native SQL with the vector passed as a text literal and `CAST(:value AS vector)` (spec file 51). No `hibernate-vector` or `pgvector` Java dependency is added.

---

## 14. Existing Tests to Update

These tests pin migration and table counts. Update the numbers intentionally and list each change in the completion report:

| Test | Pin today | After this step |
|---|---|---|
| `KnowledgeSchemaMigrationTests` | `migrationsExecuted` = 47 | + 4 |
| `KnowledgeSchemaMigrationTests.foreignKeysExistAndNeverCascade` | 14 foreign keys on the five knowledge tables | 15 (`queued_by`) |
| `SuperAdminMigrationTests` | 47 migrations | + 4 |
| `AuthenticationIntegrationTests` | 47 rows in `flyway_schema_history`, public table count | + 4 migrations, + 2 tables |
| `TimetableSchemaMigrationTests` | versions "40" … "47", count 47 | add the new versions |
| `CampusSchemaMigrationTests` | versions "33" … "47" | add the new versions |

`KnowledgeIntegrationSupport.clearKnowledgeTables()` (and any other fixture that deletes `academic_document_files`) must delete `academic_document_chunks` and `academic_document_pages` first, otherwise the foreign keys block the cleanup of later test classes.

---

## 15. New Tests

Create `feature/knowledge/RagSchemaMigrationTests` (extends `IntegrationSupport`, same style as `KnowledgeSchemaMigrationTests`: fresh schema, migrate, assert, clean).

Verify:

1. The four new versions are in the history and succeeded.
2. `SELECT extname FROM pg_extension WHERE extname = 'vector'` returns one row and its schema is `public`.
3. After `flyway.clean()` of the test schema the extension still exists (run two migrate/clean cycles in one test).
4. Both tables exist and are empty.
5. The new foreign keys exist and none cascades (`confdeltype = 'a'`).
6. `uk_academic_document_pages_file_page` and `uk_academic_document_chunks_file_index` reject duplicates.
7. Every CHECK constraint rejects an invalid row: page number 0, unknown `unit_type`, cleaned text without its count, negative `chunk_index`, `char_count` 0, `page_end < page_start`, an embedding without a model, an unknown `failed_stage`.
8. A chunk accepts a 768-dimension vector and rejects 767 and 769 dimensions.
9. A cosine query works: insert three chunks with known unit vectors and assert the order of `ORDER BY embedding <=> CAST(? AS public.vector)`.
10. `idx_academic_document_chunks_embedding_hnsw` exists with access method `hnsw`.
11. A processing row accepts every valid `failed_stage` and a valid `queued_by`.

Insert test vectors as text literals: `'[' + 768 comma-separated numbers + ']'`.

---

## 16. PostgreSQL Verification

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 6;

SELECT e.extname, e.extversion, n.nspname
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'vector';

\d academic_document_processing
\d academic_document_pages
\d academic_document_chunks

SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'academic_document_chunks';
```

Also verify:

- Existing documents, files and processing rows are unchanged (same row counts before and after).
- Existing processing rows have NULL in the four new columns and keep their status.
- The application starts and Hibernate validation passes.
- `GET /api/v1/admin/knowledge/documents` and `/statistics` answer as before.

---

## 17. Configuration and Documentation

- `compose.yaml`: only the `postgres` service changes (section 6.1).
- `.env.example`: no new variable in this task.
- `README.md`: in the infrastructure section, state that PostgreSQL now needs the `pgvector` extension, how the image is built (`docker compose build postgres`) and that a backup is taken before the first switch.
- No Postman test belongs to this task: it adds no endpoint. Check with Postman only that the existing knowledge endpoints still answer:

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents?page=0&size=10
Authorization: Bearer <admin-access-token>
```

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/statistics
Authorization: Bearer <admin-access-token>
```

Expected: the same documents and counters as before the migration.

---

## 18. Acceptance Criteria

- [ ] PostgreSQL runs with pgvector 0.8.0 or newer in Compose, on the existing data volume.
- [ ] A database backup was taken before the image switch.
- [ ] Testcontainers uses a pgvector-capable PostgreSQL 17 image.
- [ ] `vector` extension is created by Flyway in schema `public`.
- [ ] Four migrations created with the real next version numbers.
- [ ] `academic_document_processing` has `failed_stage`, `queued_at`, `queued_by`, `next_attempt_at`.
- [ ] `academic_document_pages` and `academic_document_chunks` created with the constraints above.
- [ ] `embedding` is `vector(768)` with an HNSW cosine index.
- [ ] No second database, no vector data in Redis.
- [ ] No cascading foreign key.
- [ ] No applied migration was edited.
- [ ] No seed rows.
- [ ] Flyway succeeds on a fresh database and on the existing development database.
- [ ] Hibernate validation succeeds.
- [ ] Pinned tests updated, `RagSchemaMigrationTests` passes, no new failure compared with the baseline.
- [ ] Existing knowledge endpoints behave as before.

---

## 19. Agent Completion Report

Report:

1. Image option used (Option A, as confirmed by the product owner) and the pgvector version installed.
2. Backup taken (file name, not its content).
3. Migration files and their real version numbers.
4. Tables, columns, constraints and indexes created.
5. Existing tests changed (which pin, old and new value).
6. New tests and their results.
7. Test baseline before and after (`./mvnw test` totals).
8. Flyway and Hibernate validation results on the development database.
9. Any schema compatibility issue found.
10. A new decision entry in `00-changes-and-decision.md` for every deviation from this file.

**Stop after this step. Do not start the ingestion worker (spec file 46) in the same task.**
