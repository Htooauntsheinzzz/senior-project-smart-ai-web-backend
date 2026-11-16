# 46 — Step 14B: RAG Document Processing — Ingestion Worker, Queue and Reprocess

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** Backend worker that moves an uploaded file through the processing stages, and a working reprocess endpoint
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4, Spring Security JWT RS256
**Prerequisites:** Spec file 45 (Step 14A) is implemented: migrations applied, pgvector available
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → **46 worker (this file)** → 47 extract → 48 clean → 49 chunk → 50 embed → 51 vector store → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

Spec file 39 left one gap on purpose: "A future ingestion worker will own the actual stage transitions." This file builds that worker.

After this step the backend can:

1. Queue a file revision for processing.
2. Claim one queued job at a time, safely, even with more than one backend instance.
3. Run the installed processing stages in order and move the status with them.
4. Record a failure with its stage, a safe error code and a safe message.
5. Retry transient failures automatically, with a growing delay.
6. Cancel jobs whose file was replaced or whose document was deleted.
7. Recover jobs that were interrupted by a crash or restart.
8. Let an Admin restart processing with `POST /api/v1/admin/knowledge/documents/{id}/reprocess`.

This file contains **no stage**. Spec files 47 to 51 each add one. Until the first stage exists, the reprocess endpoint keeps answering 503 as it does today, so nothing visible changes for the Admin Web.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, `spec-md/02-project-structure.md`, spec files 37, 39 and 45, and decisions D53 to D55.
2. Inspect `KnowledgeDocumentServiceImpl` (`upload`, `replaceFile`, `processing`, `reprocess`), `DocumentProcessingStatus`, `AcademicDocumentProcessing`, `AdminKnowledgeDocumentController` and `KnowledgeFailure`.
3. Inspect `application.properties` (datasource timeouts) and `IntegrationSupport`.
4. Follow the flat layer layout of `feature/knowledge` (R11).
5. Run the tests and the build.

Do not:

- Add Kafka, RabbitMQ, Quartz, Spring Batch or any other queue or scheduler product.
- Use Redis as the job queue.
- Process a file inside the upload HTTP request.
- Keep a database transaction open while a stage parses a file or calls a provider.
- Put `@PreAuthorize` on the worker or call a method that has it from the worker: the worker has no `SecurityContext`.
- Let a client set a processing status directly.
- Mark a file `READY` unless every one of the five stages ran successfully.
- Change the upload endpoint, its response or its initial `UPLOADED` status (R6).
- Log document text, file names, extracted content or provider responses.

---

## 3. Architecture

```text
Admin Web                                   Scheduler (every 5 s, one thread)
   |                                              |
   | POST /documents/{id}/reprocess               v
   v                                     DocumentIngestionService.runOnce()
AdminKnowledgeDocumentController                  |
   |                                              +-- 1. cancel superseded jobs
   v                                              +-- 2. recover interrupted jobs
KnowledgeDocumentService.reprocess                +-- 3. queue due retries
   |  (Admin authorization)                       +-- 4. queue UPLOADED files (complete pipeline only)
   v                                              +-- 5. claim one QUEUED job  (FOR UPDATE SKIP LOCKED)
DocumentIngestionService.requestProcessing        +-- 6. run the stages
   |                                                      |
   v                                                      v
academic_document_processing  <---- status ----  EXTRACT → CLEAN → CHUNK → EMBED → INDEX
        (the queue)                                       |
                                                          v
                                                READY  or  FAILED (+ failed_stage)
```

PostgreSQL is the queue (R3). The stages exchange their results through PostgreSQL (R4), never through memory.

---

## 4. Package Structure

All inside `feature/knowledge` (adapt names to what exists; do not create unused classes):

```text
feature/knowledge/
├── controller/
│   └── AdminKnowledgeDocumentController.java      (reprocess returns a body)
├── dto/
│   ├── KnowledgeDocumentProcessingResponse.java   (new fields)
│   └── KnowledgeDocumentReprocessRequest.java     (new)
├── entity/
│   └── AcademicDocumentProcessing.java            (4 new columns)
├── enums/
│   ├── DocumentProcessingStatus.java              (2 new transitions)
│   ├── IngestionStageType.java                    (new)
│   └── ReprocessMode.java                         (new)
├── exception/
│   ├── KnowledgeFailure.java                      (new factory methods)
│   └── KnowledgeIngestionException.java           (new, internal)
├── repository/
│   └── AcademicDocumentProcessingRepository.java  (queue queries)
└── service/
    ├── DocumentIngestionService.java              (new)
    ├── IngestionStage.java                        (new)
    ├── IngestionJob.java                          (new, record)
    ├── IngestionJobControl.java                   (new)
    └── impl/
        ├── DocumentIngestionServiceImpl.java      (new)
        └── KnowledgeIngestionScheduler.java       (new)

config/
├── KnowledgeIngestionProperties.java              (new, app.knowledge.ingestion)
└── SchedulingConfig.java                          (new, @EnableScheduling)
```

---

## 5. Configuration

`application.properties`:

```properties
# Step 14B: knowledge document ingestion worker.
app.knowledge.ingestion.enabled=${KNOWLEDGE_INGESTION_ENABLED:true}
app.knowledge.ingestion.scheduler-enabled=${KNOWLEDGE_INGESTION_SCHEDULER_ENABLED:true}
app.knowledge.ingestion.auto-queue=${KNOWLEDGE_INGESTION_AUTO_QUEUE:true}
app.knowledge.ingestion.poll-interval=${KNOWLEDGE_INGESTION_POLL_INTERVAL:5s}
app.knowledge.ingestion.max-attempts=${KNOWLEDGE_INGESTION_MAX_ATTEMPTS:4}
app.knowledge.ingestion.retry-initial-delay=${KNOWLEDGE_INGESTION_RETRY_INITIAL_DELAY:2m}
app.knowledge.ingestion.retry-multiplier=${KNOWLEDGE_INGESTION_RETRY_MULTIPLIER:5}
app.knowledge.ingestion.retry-max-delay=${KNOWLEDGE_INGESTION_RETRY_MAX_DELAY:6h}
app.knowledge.ingestion.stale-after=${KNOWLEDGE_INGESTION_STALE_AFTER:15m}
app.knowledge.ingestion.stage-timeout=${KNOWLEDGE_INGESTION_STAGE_TIMEOUT:30m}
```

| Property | Meaning | Validation |
|---|---|---|
| `enabled` | `false` turns ingestion off: nothing is queued or claimed and reprocess answers 503. | boolean |
| `scheduler-enabled` | `false` creates no background poller. Tests call `runOnce()` themselves. | boolean |
| `auto-queue` | Queue `UPLOADED` files automatically when the pipeline is complete. | boolean |
| `poll-interval` | Delay between two scheduler ticks. | 1 s to 5 min |
| `max-attempts` | Runs of one job before automatic retry stops. | 1 to 10 |
| `retry-initial-delay`, `retry-multiplier`, `retry-max-delay` | Delay before retry n is `initial × multiplier^(n-1)`, capped. Defaults give 2 min, 10 min, 50 min. | positive |
| `stale-after` | An in-progress job without a heartbeat for this long is treated as interrupted. | 1 min to 24 h, greater than `poll-interval` |
| `stage-timeout` | Wall-clock limit of one stage. | 1 min to 12 h |

Add the variables to `.env.example` with one comment line each, and to the `backend` service in `compose.yaml` the same way the `KNOWLEDGE_*` variables are passed today.

`IntegrationSupport` sets `app.knowledge.ingestion.scheduler-enabled=false`, so no test ever has a background worker.

`SchedulingConfig` only carries `@EnableScheduling`. This is the application's first scheduled task, so Spring Boot's default single scheduler thread is enough. If another scheduled task is added later, give ingestion its own scheduler so a long job cannot delay it.

---

## 6. Stage Contract

```java
public enum IngestionStageType {
    EXTRACT(DocumentProcessingStatus.PROCESSING),
    CLEAN(DocumentProcessingStatus.PROCESSING),
    CHUNK(DocumentProcessingStatus.CHUNKING),
    EMBED(DocumentProcessingStatus.EMBEDDING),
    INDEX(DocumentProcessingStatus.EMBEDDING);
    // status(): the processing status shown while this stage runs
}
```

```java
/** One step of the pipeline. Implementations are Spring beans; exactly one bean per type. */
public interface IngestionStage {
    IngestionStageType type();
    void execute(IngestionJob job, IngestionJobControl control);
}
```

```java
/** The file revision being processed. Created by the worker when it claims a job; never built from request data. */
public record IngestionJob(long processingId, long documentId, long fileId, int revisionNumber, String storageKey,
        String fileExtension, String mimeType, long sizeBytes, String sha256Checksum, int attempt,
        IngestionStageType firstStage) {}
```

```java
/** Given to a stage by the worker. A stage calls checkpoint() between pages, batches or provider calls. */
public interface IngestionJobControl {
    void checkpoint();
}
```

`checkpoint()` does three things in one short transaction:

1. Heartbeat: sets `updated_at = now` on the processing row, but only if the row still has the status and `attempt_count` this run set. If the row was changed by someone else, the run has lost the job and stops quietly.
2. Cancellation: if the file is no longer current or the document is deleted, it stops the run as cancelled (section 12).
3. Deadline: if the stage has run longer than `stage-timeout`, it fails the run with `INGESTION_STAGE_TIMEOUT` (retryable).

`checkpoint()` reports all three by throwing an unchecked exception that only the worker understands. A stage never catches what `checkpoint()` throws; it uses `try`/`finally` for its own cleanup (temporary files, background tasks).

A stage must call `checkpoint()` at least once per minute of work.

```java
/** Thrown by a stage. Never reaches an HTTP response; the worker stores code and message on the processing row. */
public class KnowledgeIngestionException extends RuntimeException {
    IngestionStageType stage();
    String code();          // stored in last_error_code, max 100 characters
    boolean retryable();
    // getMessage(): safe text for last_error_message, max 500 characters
}
```

Rules for stages (binding for spec files 47 to 51):

- A stage reads its input from PostgreSQL or file storage and writes its output to PostgreSQL in short transactions.
- A stage must be **repeatable**: running it again for the same file replaces or completes its earlier output and never duplicates rows.
- A stage never changes `academic_document_processing.status`. Only the worker does.
- A stage has no `@PreAuthorize` and uses no `SecurityContext`.
- A stage throws only `KnowledgeIngestionException`. Any other exception is treated by the worker as `INGESTION_INTERNAL_ERROR`.
- The message of an exception never contains document text, a file name, a storage path, a provider response body or a stack trace.

At startup the worker collects all `IngestionStage` beans. Two beans with the same `type()` are a configuration error and must stop the application from starting.

- **installed stages**: the types that have a bean.
- **pipeline complete**: all five types have a bean.

---

## 7. Status Transitions

Update `DocumentProcessingStatus.canTransitionTo`. Two transitions are new (R5):

| From | To | When |
|---|---|---|
| `UPLOADED` | `QUEUED` | automatic queueing, or reprocess |
| `QUEUED` | `PROCESSING` | the worker claims the job |
| `QUEUED` | `FAILED` | **new**: cancelled because the file was replaced or the document deleted |
| `PROCESSING` | `CHUNKING`, `FAILED` | |
| `CHUNKING` | `EMBEDDING`, `FAILED` | |
| `EMBEDDING` | `READY`, `FAILED` | |
| `FAILED` | `QUEUED` | automatic retry, or reprocess |
| `READY` | `QUEUED` | **new**: reprocess with mode `FULL` |

Every status change goes through one method that checks `canTransitionTo` and throws on an invalid transition. Every change sets `updated_at`.

This extends spec file 37 section 9. Record it in `00-changes-and-decision.md`.

---

## 8. How a File Gets Queued

There are three ways into `QUEUED`. All of them set `queued_at = now` and `updated_at = now`.

### 8.1 Automatic queueing of uploads

Runs on every scheduler tick, only when `enabled`, `auto-queue` and **pipeline complete** are all true.

Select processing rows with status `UPLOADED` whose file is current and whose document is active and not deleted, oldest upload first, at most 50 per tick. For each: `UPLOADED → QUEUED`, `queued_by = academic_document_files.uploaded_by`, `attempt_count = 0`.

This also picks up every document that was uploaded before Step 14 existed.

While the pipeline is incomplete nothing is queued automatically. Existing documents therefore stay `UPLOADED` during the development of spec files 47 to 51 and do not turn `FAILED`.

An archived document (`is_active = FALSE`) is not queued automatically. It is queued after it is activated again.

### 8.2 Automatic retry

Runs on every tick. Select rows with status `FAILED`, `next_attempt_at <= now`, file current, document not deleted. For each: `FAILED → QUEUED`, `next_attempt_at = NULL`. `failed_stage`, `attempt_count` and `queued_by` are kept.

### 8.3 Reprocess by an Admin

Section 14.

---

## 9. Claiming a Job

One short transaction:

```sql
SELECT p.id
FROM academic_document_processing p
JOIN academic_document_files f ON f.id = p.file_id
JOIN academic_documents d ON d.id = f.document_id
WHERE p.status = 'QUEUED'
  AND f.is_current
  AND NOT d.is_deleted
ORDER BY p.queued_at NULLS FIRST, p.id
LIMIT 1
FOR UPDATE OF p SKIP LOCKED;
```

Then, in the same transaction:

1. Decide the first stage (section 10.1) from `failed_stage`.
2. Update the row: `QUEUED → PROCESSING`, `attempt_count = attempt_count + 1`, `started_at = now`, `completed_at = NULL`, `failed_stage = NULL`, `last_error_code = NULL`, `last_error_message = NULL`, `next_attempt_at = NULL`.
3. Build the `IngestionJob` from the file row.

`SKIP LOCKED` makes two backend instances claim different jobs. One instance processes one job at a time: a tick claims at most one job and the next tick starts only after it finished.

---

## 10. Running the Pipeline

### 10.1 First stage and resume

| `failed_stage` when claimed | Chunks exist for the file | First stage |
|---|---|---|
| `EMBED` or `INDEX` | yes | `EMBED` (resume) |
| anything else, or NULL | any | `EXTRACT` (full run) |

A full run first deletes this file's rows from `academic_document_chunks` and then from `academic_document_pages`, in batches of at most 500 rows per statement (the tables exist since spec file 45; native SQL is enough here).

Resume exists because embeddings cost provider quota: a run that failed after embedding 400 of 600 chunks continues with the remaining 200 (spec file 50).

### 10.2 Loop

```text
for each stage type from firstStage to INDEX, in enum order:
    if no bean is installed for the type:
        fail the job: stage = type, code = INGESTION_STAGE_NOT_AVAILABLE, not retryable
    control.checkpoint()
    move the status to type.status() if it differs (PROCESSING → CHUNKING → EMBEDDING)
    start the stage deadline
    stage.execute(job, control)

after INDEX succeeded:
    EMBEDDING → READY, completed_at = now, failed_stage = NULL, errors = NULL, next_attempt_at = NULL
```

When a run resumes at `EMBED`, the status still passes through `PROCESSING → CHUNKING → EMBEDDING` so every transition stays valid; no work is done for the skipped stages.

No transaction spans a stage. The worker's own updates are single short transactions, each conditional on the row still belonging to this run (same status, same `attempt_count`).

A job that reaches a missing stage keeps the output of the stages that ran. This is how spec files 47 to 50 are checked one by one before the pipeline is complete: reprocess a test document, see it stop at the next missing stage with `INGESTION_STAGE_NOT_AVAILABLE`, and inspect the stored output.

---

## 11. Failure Handling

| Thrown by the stage | Stored code | Retryable |
|---|---|---|
| `KnowledgeIngestionException` | its `code()` | its `retryable()` |
| `org.springframework.dao.DataAccessException` | `INGESTION_DATABASE_ERROR` | yes |
| any other `Exception`, `OutOfMemoryError`, `StackOverflowError` | `INGESTION_INTERNAL_ERROR` | yes |

On failure, in one transaction (only if the row still belongs to this run):

- status → `FAILED`, `failed_stage` = the stage that failed, `completed_at = now`
- `last_error_code` = the code, `last_error_message` = the safe message (truncated to 500 characters)
- `next_attempt_at` = `now + delay(attempt_count)` when the failure is retryable **and** `attempt_count < max-attempts`; otherwise NULL

Codes defined in this file:

| Code | Retryable | Meaning |
|---|---|---|
| `INGESTION_STAGE_NOT_AVAILABLE` | no | The next stage is not installed in this build. |
| `INGESTION_STAGE_TIMEOUT` | yes | A stage exceeded `stage-timeout`. |
| `INGESTION_INTERRUPTED` | yes | The worker stopped while the job was running (section 13). |
| `INGESTION_SUPERSEDED` | no | The file was replaced or the document deleted (section 12). |
| `INGESTION_DATABASE_ERROR` | yes | A database error outside a stage's own handling. |
| `INGESTION_INTERNAL_ERROR` | yes | Unexpected error. Only the exception class name is logged. |

Stage-specific codes are defined in spec files 47 to 51.

---

## 12. Cancellation

A job is no longer wanted when its file is not current any more (a new revision was uploaded) or its document is soft-deleted.

- **Queued jobs:** on every tick, rows with status `QUEUED` that are no longer wanted go `QUEUED → FAILED` with `last_error_code = INGESTION_SUPERSEDED`, `failed_stage = NULL`, `next_attempt_at = NULL`, `completed_at = now`.
- **Running jobs:** `checkpoint()` detects it. The worker then marks the row `FAILED` with `INGESTION_SUPERSEDED` (no `failed_stage`, no retry) and deletes this file's chunks and pages.

`replaceFile` and `delete` in `KnowledgeDocumentServiceImpl` are not changed for this.

Archiving a document does **not** cancel a job. The file is processed; search ignores it while it is archived (R7).

---

## 13. Recovery of Interrupted Jobs

On every tick: rows with status `PROCESSING`, `CHUNKING` or `EMBEDDING` whose `updated_at` is older than `stale-after` belong to a run that died. They go to `FAILED` with `last_error_code = INGESTION_INTERRUPTED`, `failed_stage` = `EXTRACT`, `CHUNK` or `EMBED` (from the status), and `next_attempt_at = now` when `attempt_count < max-attempts`.

A healthy run is never treated as stale because every stage calls `checkpoint()` at least once per minute. If a slow run is nevertheless marked interrupted, its next conditional update changes no row and it stops (section 6).

---

## 14. Reprocess Endpoint

```http
POST /api/v1/admin/knowledge/documents/{id}/reprocess
Authorization: Bearer <admin-access-token>
Content-Type: application/json
```

Body (optional; an empty body means `RESUME`):

```json
{
  "mode": "FULL"
}
```

| `mode` | Meaning |
|---|---|
| `RESUME` (default) | Continue or retry: start where the last run failed when that is possible, otherwise from the beginning. |
| `FULL` | Discard pages, chunks and vectors of the current revision and process it from the beginning. |

Only `RESUME` and `FULL` are accepted. Any other value, an unknown JSON property or a non-object body is 400 `VALIDATION_ERROR`.

Checks, in this order:

| Check | Error |
|---|---|
| Admin token with role SUPER_ADMIN, ADMIN or ACADEMIC_ADMIN (existing rule for `/api/v1/admin/knowledge/**`) | 401 / 403 |
| Body valid | 400 `VALIDATION_ERROR` |
| Ingestion enabled and at least one stage installed | 503 `DOCUMENT_PROCESSING_NOT_AVAILABLE` |
| Document exists and is not deleted | 404 `DOCUMENT_NOT_FOUND` |
| Document is active | 409 `DOCUMENT_NOT_EDITABLE` |
| Document has a current file with a processing row | 404 `DOCUMENT_FILE_NOT_FOUND` |

Then lock the processing row (`FOR UPDATE`, existing 3 second lock timeout) and apply:

| Current status | `RESUME` | `FULL` |
|---|---|---|
| `UPLOADED` | → `QUEUED` | → `QUEUED` |
| `FAILED` | → `QUEUED`, `failed_stage` kept | → `QUEUED`, `failed_stage = NULL` |
| `READY` | 409 `DOCUMENT_ALREADY_PROCESSED` | → `QUEUED`, `failed_stage = NULL` |
| `QUEUED`, `PROCESSING`, `CHUNKING`, `EMBEDDING` | 409 `DOCUMENT_PROCESSING_ALREADY_RUNNING` | 409 `DOCUMENT_PROCESSING_ALREADY_RUNNING` |

Every successful request sets `attempt_count = 0`, `queued_at = now`, `queued_by` = the authenticated Admin, `next_attempt_at = NULL`, `last_error_code = NULL`, `last_error_message = NULL`.

Response: **HTTP 202** with the processing body of section 15 (`status: "QUEUED"`), `Cache-Control: no-store`. The work happens later in the worker; the endpoint never waits for it.

A document that is `READY` stays searchable until the worker claims the job. From then on it is not `READY` and is not returned by search until the new run finishes.

Implementation: `KnowledgeDocumentServiceImpl.reprocess` keeps the Admin authorization and delegates to `DocumentIngestionService.requestProcessing(documentId, actorUserId, mode)`. The controller method returns `ResponseEntity<KnowledgeDocumentProcessingResponse>`.

---

## 15. Processing Status API

`GET /api/v1/admin/knowledge/documents/{id}/processing` keeps its existing fields and gains seven:

```json
{
  "documentId": 31,
  "fileId": 81,
  "revisionNumber": 1,
  "status": "FAILED",
  "attemptCount": 2,
  "startedAt": "2026-11-12T03:10:05Z",
  "completedAt": "2026-11-12T03:10:19Z",
  "lastErrorCode": "EMBEDDING_RATE_LIMITED",
  "ingestionAvailable": true,
  "message": "Processing failed. It will be retried automatically.",
  "failedStage": "EMBED",
  "lastErrorMessage": "The embedding provider is rate limiting requests.",
  "queuedAt": "2026-11-12T03:10:00Z",
  "nextAttemptAt": "2026-11-12T03:20:19Z",
  "pageCount": 42,
  "chunkCount": 118,
  "embeddedChunkCount": 64
}
```

Values are illustrative.

- `ingestionAvailable`: ingestion is enabled **and** the pipeline is complete.
- `pageCount`, `chunkCount`, `embeddedChunkCount`: counted from `academic_document_pages` and `academic_document_chunks` for the current file (`embedding IS NOT NULL` for the last one). Never stored.
- `lastErrorMessage` is the safe text of section 6. No stack trace, no document content.

`message`:

| Status | Message |
|---|---|
| `UPLOADED`, ingestion available | "Uploaded. Waiting to be queued for AI processing." |
| `UPLOADED`, ingestion not available | "Awaiting AI processing. Document ingestion is not available yet." (existing text) |
| `QUEUED` | "Queued for AI processing." |
| `PROCESSING` | "Extracting and cleaning text." |
| `CHUNKING` | "Splitting text into chunks." |
| `EMBEDDING` | "Creating embeddings (64 of 118 chunks)." |
| `READY` | "Indexed and ready for AI retrieval." (existing text) |
| `FAILED` with `nextAttemptAt` | "Processing failed. It will be retried automatically." |
| `FAILED` without `nextAttemptAt` | "Processing failed." |

The document list, the document detail, the revision list and the statistics endpoint do not change. The Admin Web badge mapping of spec file 39 section 31 stays valid.

---

## 16. Security

- The reprocess and processing endpoints keep the existing rule for `/api/v1/admin/knowledge/**` in `SecurityConfig` and the `@PreAuthorize` of `KnowledgeDocumentServiceImpl`.
- `queued_by` always comes from the authenticated Admin or from `uploaded_by`. It is never read from a request.
- `DocumentIngestionService`, the scheduler and the stages have no method security and are not reachable from any controller except through `KnowledgeDocumentService.reprocess`.
- The worker reads files only through `FileStorageService.open(storageKey)` with keys from the database.
- Students have no access to any endpoint in this file.

---

## 17. Logging

One line per event, logger of `DocumentIngestionServiceImpl`, with ids and codes only:

```text
KNOWLEDGE_INGESTION event=QUEUED documentId=31 fileId=81 source=AUTO|RETRY|ADMIN mode=RESUME
KNOWLEDGE_INGESTION event=CLAIMED documentId=31 fileId=81 attempt=1 firstStage=EXTRACT
KNOWLEDGE_INGESTION event=STAGE_COMPLETED documentId=31 fileId=81 stage=EXTRACT durationMs=1840
KNOWLEDGE_INGESTION event=READY documentId=31 fileId=81 attempt=1 durationMs=73211
KNOWLEDGE_INGESTION event=FAILED documentId=31 fileId=81 attempt=1 stage=EMBED code=EMBEDDING_RATE_LIMITED retryable=true nextAttemptInSeconds=120
KNOWLEDGE_INGESTION event=CANCELLED documentId=31 fileId=81 code=INGESTION_SUPERSEDED
KNOWLEDGE_INGESTION event=RECOVERED documentId=31 fileId=81 code=INGESTION_INTERRUPTED
```

For unexpected exceptions log the exception class name, never its message (it can contain document text or a path).

A scheduler tick that fails (for example the database is down) is logged once at `WARN` and the next tick runs normally. A tick never throws out of the scheduled method.

---

## 18. Error Codes

HTTP errors (`KnowledgeFailure`, existing `ApiError` format):

| Code | HTTP | New |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | no |
| `DOCUMENT_NOT_FOUND` | 404 | no |
| `DOCUMENT_FILE_NOT_FOUND` | 404 | no |
| `DOCUMENT_NOT_EDITABLE` | 409 | no |
| `DOCUMENT_PROCESSING_ALREADY_RUNNING` | 409 | yes (named in spec file 39 section 36) |
| `DOCUMENT_ALREADY_PROCESSED` | 409 | yes |
| `DOCUMENT_PROCESSING_NOT_AVAILABLE` | 503 | no (message changes to "Document ingestion is not available.") |

Processing codes (stored in `last_error_code`, never an HTTP status): section 11.

---

## 19. Transactions and Timeouts

- The datasource allows 5 seconds per statement (`socketTimeout=5`, query timeout 5000 ms); tests allow 1 second. Every worker statement touches few rows. Deletes and inserts are batched (at most 500 rows per statement).
- Use `TransactionTemplate` with a short timeout, as `KnowledgeDocumentServiceImpl` does. Do not put `@Transactional` on `runOnce()` or on a stage's `execute`.
- Timestamps are UTC `LocalDateTime` from the injected `Clock`, like the rest of the feature.

---

## 20. Tests

Tests must stay valid after spec files 47 to 51 add real stages. Therefore build `DocumentIngestionServiceImpl` by hand in tests with a list of **fake stages** (constructor argument) instead of registering fake stage beans in the Spring context.

### 20.1 Unit tests

- `DocumentProcessingStatus.canTransitionTo`: every allowed and every forbidden pair of section 7.
- `IngestionStageType.status()` mapping.
- Retry delay: 2 min, 10 min, 50 min, cap at `retry-max-delay`, none after `max-attempts`.
- Two stages with the same type are rejected at construction.
- No stage installed: `requestProcessing` throws `DOCUMENT_PROCESSING_NOT_AVAILABLE`.
- `enabled = false`: `runOnce()` does nothing and `requestProcessing` throws `DOCUMENT_PROCESSING_NOT_AVAILABLE`.

### 20.2 Integration tests (`DocumentIngestionIntegrationTests`, Testcontainers PostgreSQL, extends `KnowledgeIntegrationSupport`)

Upload documents through the real upload endpoint, then drive the worker with `runOnce()`.

- Five fake stages succeed: `UPLOADED → QUEUED → PROCESSING → CHUNKING → EMBEDDING → READY`; `attempt_count = 1`; `started_at`, `completed_at`, `queued_at`, `queued_by` (the uploader) are set; the stages ran in order.
- Incomplete pipeline (only `EXTRACT`): automatic queueing does nothing; after reprocess the job fails with `INGESTION_STAGE_NOT_AVAILABLE`, `failed_stage = CLEAN`, no `next_attempt_at`.
- A stage throws a retryable exception: `FAILED`, `failed_stage`, code, message and `next_attempt_at` are stored; after the clock passes `next_attempt_at` the job is queued and run again; after `max-attempts` no retry is scheduled.
- A stage throws a non-retryable exception: `FAILED`, no `next_attempt_at`.
- A stage throws an unexpected `RuntimeException`: `INGESTION_INTERNAL_ERROR`; the stored message does not contain the exception message.
- Resume: a job that failed in `EMBED` with chunks present restarts at `EMBED` (the `EXTRACT`, `CLEAN` and `CHUNK` fakes are not called again); without chunks it restarts at `EXTRACT`.
- A full run deletes the file's earlier pages and chunks before `EXTRACT`.
- Reprocess matrix of section 14: every status with both modes, including the two 409 codes; `queued_by` is the calling Admin; `attempt_count` is reset.
- Reprocess of an archived document is 409 `DOCUMENT_NOT_EDITABLE`; of a deleted document 404.
- Invalid `mode`, unknown property: 400.
- Cancellation: replacing the file while the job is `QUEUED` gives `FAILED` / `INGESTION_SUPERSEDED` for the old revision; replacing it during a stage (the fake stage triggers the replacement, then calls `checkpoint()`) cancels the run and removes the old revision's pages and chunks.
- Recovery: a row left in `EMBEDDING` with an old `updated_at` becomes `FAILED` / `INGESTION_INTERRUPTED` and is retried.
- Two workers: two `runOnce()` calls on two threads with two queued jobs process each job exactly once.
- Processing API returns the new fields and derived counts.
- Authorization: no token 401, Student token 403, every Admin role allowed.

### 20.3 Existing tests

- `KnowledgeUploadIntegrationTests` asserts 503 on reprocess and `ingestionAvailable: false`. Both stay true only until stages exist. Rewrite them now so they do not depend on that: keep the upload assertions (`UPLOADED`, `attemptCount` 0) and move the "ingestion not available" check into a test that sets `app.knowledge.ingestion.enabled=false`.
- No test may rely on a background thread.

---

## 21. Postman Verification

No stage is installed after this step, so Postman shows the new fields and the unchanged 503. The worker itself is verified by the automated tests of section 20.

**1. Processing status has the new fields**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{id}/processing
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 200, `status: "UPLOADED"`, `ingestionAvailable: false`, `failedStage: null`, `pageCount: 0`, `chunkCount: 0`, `embeddedChunkCount: 0`.

**2. Reprocess is still unavailable (no stage yet)**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{id}/reprocess
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 503, `code: "DOCUMENT_PROCESSING_NOT_AVAILABLE"`. The status stays `UPLOADED`.

**3. Invalid mode**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{id}/reprocess
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{ "mode": "FAST" }
```

Expected: HTTP 400, `code: "VALIDATION_ERROR"`.

**4. Upload is unchanged**

Repeat Postman Test 1 of spec file 39 (upload a PDF). Expected: HTTP 201 and `processingStatus: "UPLOADED"`; the document stays `UPLOADED` because the pipeline is not complete.

---

## 22. Acceptance Criteria

- [ ] `AcademicDocumentProcessing` maps the four new columns; Hibernate validation passes.
- [ ] Stage contract (`IngestionStageType`, `IngestionStage`, `IngestionJob`, `IngestionJobControl`, `KnowledgeIngestionException`) exists.
- [ ] Status transitions match section 7 and are enforced in one place.
- [ ] Jobs are claimed with `FOR UPDATE SKIP LOCKED`; two workers never run the same job.
- [ ] Automatic queueing only with a complete pipeline; uploads still return `UPLOADED`.
- [ ] Failures store stage, code and a safe message.
- [ ] Retryable failures are retried with growing delay up to `max-attempts`.
- [ ] Superseded and deleted work is cancelled.
- [ ] Interrupted jobs are recovered.
- [ ] Resume at `EMBED` works; a full run clears earlier output.
- [ ] Reprocess returns 202 with the processing body and follows the matrix of section 14.
- [ ] Processing API returns the new fields with derived counts.
- [ ] No transaction is open during a stage.
- [ ] The worker has no `SecurityContext` dependency; Admin authorization is unchanged.
- [ ] Logs carry ids and codes only.
- [ ] `KNOWLEDGE_INGESTION_*` variables documented in `.env.example` and passed in `compose.yaml`.
- [ ] Unit and integration tests pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 23. Agent Completion Report

Report: files created and modified; the final transition matrix; the claim query; retry delays in effect; how cancellation and recovery were tested; the reprocess contract; changed existing tests and why; test totals against the baseline; Postman results actually performed; known limitations; decision entries added to `00-changes-and-decision.md`.

Do not claim that documents are processed or searchable: no stage exists yet.

**Next: spec file 47 — Step 14C: Extract Text.**
