# 50 — Step 14F: RAG Document Processing — Create Embeddings

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** An embedding client for an OpenAI-compatible provider, and the `EMBED` stage that creates a vector for every chunk and saves it
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, JDK `HttpClient`
**Prerequisites:** Spec files 45 to 49 are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → 47 extract → 48 clean → 49 chunk → **50 embed (this file)** → 51 vector store → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
Split into Chunks
    ↓
Create Embeddings     ← this file
    ↓
Store in Vector Database
```

An **embedding** is a list of numbers (here 768) that represents the meaning of a text. Two texts about the same thing get vectors that point in almost the same direction, even when they use different words or different languages. This is what later lets the question "How many credits do I need to graduate?" find the passage "To graduate, a student must complete at least 120 credits".

After this step:

1. The backend has an `EmbeddingClient` that turns texts into vectors through the provider's `/embeddings` endpoint.
2. The `EMBED` stage sends every chunk of a file to that client and saves the returned vector on the chunk row.

Creating and saving belong together: a vector that is not saved at once would have to be paid for again after a crash (R4). This file therefore also contains the two write operations of the vector store. Spec file 51 adds reading, index settings, the final consistency check and clean-up.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 45, 46 and 49, decisions D45 and D50, and the stage contract of spec file 46, section 6.
2. Inspect `ExternalLlmClient`, `AiAssistantProperties` and `ExternalLlmClientTests`. The embedding client follows the same safety pattern.
3. Check the provider's **current** documentation for: the embedding model id, whether the OpenAI-compatible `/embeddings` endpoint accepts an array as `input`, whether it accepts `dimensions`, the input token limit per text, and the rate limits of the account in use. Write what you found into the completion report. Do not rely on the numbers in this file for provider limits.
4. Run the tests and the build.

Do not:

- Add Spring AI, LangChain4j or a provider SDK. Use the JDK HTTP client as `ExternalLlmClient` does.
- Run an embedding model locally (ONNX, DJL, Ollama) in this step.
- Call the provider from an automated test (R12). The only exception is the opt-in check of section 15.
- Send anything except the embedding input of spec file 49 (section title and chunk text). Never a student id, a name, an e-mail address, a token, a file name or a storage key.
- Log the API key, any input text, any vector or any provider response body.
- Keep a database transaction open during a provider call.
- Store a vector that is not exactly 768 numbers long, contains a non-finite number or has length zero.
- Compare vectors in Java. Similarity is computed by PostgreSQL (spec files 51 and 52).

---

## 3. What Is Fixed and What Is Configurable

| Item | Value | Where it is fixed |
|---|---|---|
| Dimensions | 768 | The column type `vector(768)` (spec file 45). Changing it needs a migration and a full re-embedding. |
| Distance | cosine | The HNSW index operator class (spec file 45). |
| Vector length | 1 (L2-normalized by the backend) | This file, section 7. |
| Model | configurable, default for Gemini `gemini-embedding-001` | `AI_EMBEDDING_MODEL` |
| Provider | any provider with an OpenAI-compatible `/embeddings` endpoint | `AI_EMBEDDING_BASE_URL` |

Any model can be used as long as it can return 768 dimensions. Vectors of two different models can never be compared, so every chunk stores the model that produced its vector, and search only uses vectors of the configured model (R10).

**Privacy note for the product owner.** From this step on, the text of every uploaded knowledge document is sent to the embedding provider, chunk by chunk. From spec file 52 on, the text of search questions is sent too. Only documents that may be processed by that provider should be uploaded. The Admin Web upload screen should say so.

---

## 4. Package Structure

```text
feature/knowledge/
├── exception/
│   └── EmbeddingException.java                 (new, internal: code + retryable)
├── repository/
│   └── AcademicDocumentChunkRepository.java    (+ queries for chunks that still need a vector)
└── service/
    ├── EmbeddingClient.java                    (new)
    ├── KnowledgeVectorStore.java               (new: reuseEmbeddings, saveEmbeddings; completed by spec file 51)
    └── impl/
        ├── OpenAiCompatibleEmbeddingClient.java   (new)
        ├── PgVectorKnowledgeVectorStore.java      (new: the two write operations)
        └── CreateEmbeddingsStage.java             (new, IngestionStage EMBED)

config/
└── KnowledgeEmbeddingProperties.java           (new, app.knowledge.embedding)
```

Test sources: `DeterministicEmbeddingClient` (section 13).

---

## 5. Configuration

`application.properties`:

```properties
# Step 14F: embeddings for knowledge chunks and search queries (OpenAI-compatible /embeddings endpoint).
app.knowledge.embedding.provider=${AI_EMBEDDING_PROVIDER:}
app.knowledge.embedding.base-url=${AI_EMBEDDING_BASE_URL:}
app.knowledge.embedding.api-key=${AI_EMBEDDING_API_KEY:}
app.knowledge.embedding.model=${AI_EMBEDDING_MODEL:}
app.knowledge.embedding.dimensions=${AI_EMBEDDING_DIMENSIONS:768}
app.knowledge.embedding.send-dimensions=${AI_EMBEDDING_SEND_DIMENSIONS:true}
app.knowledge.embedding.batch-size=${AI_EMBEDDING_BATCH_SIZE:16}
app.knowledge.embedding.max-batch-tokens=${AI_EMBEDDING_MAX_BATCH_TOKENS:8000}
app.knowledge.embedding.timeout-seconds=${AI_EMBEDDING_TIMEOUT_SECONDS:30}
app.knowledge.embedding.query-timeout-seconds=${AI_EMBEDDING_QUERY_TIMEOUT_SECONDS:10}
app.knowledge.embedding.connect-timeout-seconds=${AI_EMBEDDING_CONNECT_TIMEOUT_SECONDS:10}
app.knowledge.embedding.max-retries=${AI_EMBEDDING_MAX_RETRIES:2}
app.knowledge.embedding.retry-backoff=${AI_EMBEDDING_RETRY_BACKOFF:2s}
app.knowledge.embedding.min-interval-ms=${AI_EMBEDDING_MIN_INTERVAL_MS:1000}
```

| Property | Meaning | Validation |
|---|---|---|
| `provider` | A label for logs. Empty means the value of `AI_PROVIDER`. | — |
| `base-url` | Base URL of the OpenAI-compatible API. Empty means: use `AI_BASE_URL` of the chat client. | Same rule as `AiAssistantProperties.isBaseUrlSafe` |
| `api-key` | Empty means: use `AI_API_KEY` of the chat client. | — |
| `model` | Model id sent to the provider and stored in `embedding_model`. Empty means embeddings are not configured. | at most 100 characters |
| `dimensions` | Size of the vectors. | **must be 768**; any other value stops the application at startup |
| `send-dimensions` | Send `"dimensions": 768` in the request. Turn it off for a provider that rejects the field and already returns 768. | boolean |
| `batch-size` | Maximum number of chunks in one provider request. | 1 to 100 |
| `max-batch-tokens` | A batch is closed before the sum of `token_estimate` passes this value. A batch always holds at least one chunk. | 500 to 300,000 |
| `timeout-seconds` | Read timeout of a document request. | 1 to 120 |
| `query-timeout-seconds` | Read timeout of a query request (spec file 52). | 1 to 60 |
| `connect-timeout-seconds` | Connect timeout. | 1 to 60 |
| `max-retries` | Retries of one request inside the client. | 0 to 5 |
| `retry-backoff` | Wait before retry n is `retry-backoff × n`. | 0 to 30 s |
| `min-interval-ms` | Minimum time between two document requests of the `EMBED` stage. | 0 to 60,000 |

Rules:

- The empty-means-fallback rule for `base-url`, `api-key` and `provider` is resolved **in code** (inject `AiAssistantProperties`), not with nested `${…}` placeholders: Compose passes an unset variable as an empty string, and an empty string would not trigger a placeholder default.
- "Configured" means: model, effective base URL and effective API key all have text.
- The application starts when embeddings are not configured. Only the `EMBED` stage and the search then report it.
- `toString()` of the properties record masks the key, as `AiAssistantProperties` does.

`.env.example` (comments included):

```properties
# Step 14F embeddings. Empty AI_EMBEDDING_BASE_URL / AI_EMBEDDING_API_KEY reuse AI_BASE_URL / AI_API_KEY.
# Example for Google Gemini: AI_EMBEDDING_MODEL=gemini-embedding-001
AI_EMBEDDING_MODEL=
AI_EMBEDDING_BASE_URL=
AI_EMBEDDING_API_KEY=
AI_EMBEDDING_DIMENSIONS=768
AI_EMBEDDING_SEND_DIMENSIONS=true
AI_EMBEDDING_BATCH_SIZE=16
AI_EMBEDDING_MAX_BATCH_TOKENS=8000
AI_EMBEDDING_TIMEOUT_SECONDS=30
AI_EMBEDDING_QUERY_TIMEOUT_SECONDS=10
AI_EMBEDDING_MAX_RETRIES=2
AI_EMBEDDING_MIN_INTERVAL_MS=1000
```

Pass the same variables to the `backend` service in `compose.yaml`, next to the `AI_*` variables. Never commit a real key.

---

## 6. EmbeddingClient Contract

```java
/** Turns text into L2-normalized vectors of dimensions() numbers. Never logs text, vectors or keys. */
public interface EmbeddingClient {
    boolean configured();
    String model();                                      // stored in academic_document_chunks.embedding_model
    int dimensions();                                    // 768
    List<float[]> embedDocuments(List<String> texts);    // one vector per text, same order
    float[] embedQuery(String text);                     // used by spec file 52
}
```

```java
/** Thrown by an EmbeddingClient. Carries a code of section 10 and whether a later attempt can succeed. */
public class EmbeddingException extends RuntimeException {
    String code();
    boolean retryable();
}
```

- `embedDocuments` rejects an empty list, more than `batch-size` texts and a blank text with `IllegalArgumentException` (a programming error, not a provider error).
- `embedQuery` uses `query-timeout-seconds` and at most one retry.
- Both methods exist separately so that a provider-specific client can later send different task hints for documents and for questions without changing any caller. The OpenAI-compatible client sends the same request for both.
- The message of an `EmbeddingException` is one of the fixed safe messages of section 10. It never contains provider text.

---

## 7. OpenAI-Compatible Client

`OpenAiCompatibleEmbeddingClient` is the only `EmbeddingClient` bean in the application.

Request:

```http
POST {base-url}/embeddings
Authorization: Bearer <api-key>
Content-Type: application/json
Accept: application/json

{
  "model": "gemini-embedding-001",
  "input": ["<embedding input 1>", "<embedding input 2>"],
  "encoding_format": "float",
  "dimensions": 768
}
```

- `input` is always an array, also for one text.
- `dimensions` is sent only when `send-dimensions` is true.
- A trailing slash of the base URL is removed before `/embeddings` is appended.

Expected response:

```json
{
  "object": "list",
  "data": [
    { "object": "embedding", "index": 0, "embedding": [0.0123, -0.0456] },
    { "object": "embedding", "index": 1, "embedding": [0.0789, 0.0012] }
  ],
  "model": "gemini-embedding-001"
}
```

(The arrays are shortened here; a real one has 768 numbers.)

Handling of the response, in this order:

1. `data` must be an array with exactly one entry per input text. Sort the entries by `index`; the indexes must be 0 to n−1, each exactly once. Otherwise `EMBEDDING_INVALID_RESPONSE`.
2. Every `embedding` must be an array of exactly `dimensions` numbers. Another length is `EMBEDDING_DIMENSION_MISMATCH`.
3. Every number must be finite, and the vector must not be all zeros. Otherwise `EMBEDDING_INVALID_RESPONSE`.
4. **L2-normalize** every vector: divide each number by the square root of the sum of the squares. Models that shorten their vectors to 768 dimensions do not return unit vectors; normalizing makes cosine distance behave the same for every model.

Safety pattern (same as `ExternalLlmClient`):

- JDK `HttpClient`, redirects are never followed, connect and read timeouts are always set.
- The response body is read up to a fixed limit (8 MB). A larger body is `EMBEDDING_INVALID_RESPONSE`.
- Retried inside the client, at most `max-retries` times: HTTP 429, HTTP 5xx and connection failures. Not retried: a read timeout, every other 4xx, an invalid response.
- Logs contain the HTTP status, the number of texts, the duration and the exception class name. Never the key, the texts, the vectors, the URL from an exception message or the response body.

HTTP status mapping:

| Provider answer | Code | Retried in the client |
|---|---|---|
| 2xx, valid body | — | — |
| 2xx, invalid body | `EMBEDDING_INVALID_RESPONSE` or `EMBEDDING_DIMENSION_MISMATCH` | no |
| 401, 403 | `EMBEDDING_AUTHENTICATION_FAILED` | no |
| 429 | `EMBEDDING_RATE_LIMITED` | yes |
| other 4xx, 3xx | `EMBEDDING_REQUEST_REJECTED` | no |
| 5xx | `EMBEDDING_PROVIDER_UNAVAILABLE` | yes |
| connection failure | `EMBEDDING_PROVIDER_UNAVAILABLE` | yes |
| read timeout | `EMBEDDING_TIMEOUT` | no |
| not configured (no request is sent) | `EMBEDDING_NOT_CONFIGURED` | no |

---

## 8. Vector Store Writes

`KnowledgeVectorStore` is created here with two methods. `PgVectorKnowledgeVectorStore` implements them with native SQL. Spec file 51 adds the remaining methods.

```java
public interface KnowledgeVectorStore {
    /** Copies vectors from other chunks with the same content_hash and model. Returns how many chunks were filled. */
    int reuseEmbeddings(long fileId, String model);

    /** Stores one vector per chunk id. Chunks that already have a vector of this model are left as they are. */
    void saveEmbeddings(Map<Long, float[]> vectorsByChunkId, String model);
}
```

A vector is passed to PostgreSQL as a text literal (`[0.0123,-0.0456,…]`, written with `Float.toString`, no spaces) and cast in SQL. No pgvector Java library is added (spec file 45, section 13).

Save (one statement per chunk, one transaction per batch):

```sql
UPDATE academic_document_chunks
SET embedding = CAST(:vector AS vector),
    embedding_model = :model,
    embedded_at = :now
WHERE id = :chunkId
  AND (embedding IS NULL OR embedding_model <> :model);
```

Reuse (run for at most 500 chunks of the file per statement):

```sql
UPDATE academic_document_chunks c
SET embedding = s.embedding,
    embedding_model = s.embedding_model,
    embedded_at = :now
FROM (
    SELECT DISTINCT ON (o.content_hash) o.content_hash, o.embedding, o.embedding_model
    FROM academic_document_chunks o
    WHERE o.embedding IS NOT NULL
      AND o.embedding_model = :model
      AND o.content_hash IN (:hashes)
    ORDER BY o.content_hash, o.id
) s
WHERE c.id IN (:chunkIds)
  AND c.content_hash = s.content_hash
  AND (c.embedding IS NULL OR c.embedding_model <> :model);
```

`idx_academic_document_chunks_content_hash` (spec file 45) serves the inner query. `:now` is UTC from the injected `Clock`.

What reuse covers:

- A **new revision** of a document: the old revision's chunks still exist until the new revision is indexed (spec file 51), so unchanged passages cost nothing.
- The same passage in **another document**.

What it does not cover: a `FULL` reprocess of the same file. Its old chunks are deleted at the start of the run (spec file 46, section 10.1), so everything is embedded again. This is accepted.

---

## 9. The EMBED Stage

`CreateEmbeddingsStage` implements `IngestionStage` with `type() = EMBED`. It runs while the processing status is `EMBEDDING`.

```text
1. control.checkpoint()
2. Not configured → fail with EMBEDDING_NOT_CONFIGURED.
3. Reuse: vectorStore.reuseEmbeddings(fileId, model), 500 chunks at a time. control.checkpoint().
4. Repeat:
     a. Read the next chunks of this file that need a vector
        (embedding IS NULL OR embedding_model <> configured model), ordered by chunk_index:
        at most batch-size chunks, and stop before the sum of token_estimate passes max-batch-tokens.
        Nothing left → finished.
     b. Build the embedding input of every chunk with the shared method of spec file 49, section 11.
        If the SHA-256 of an input differs from the chunk's content_hash → fail with DOCUMENT_CHUNKS_OUTDATED.
     c. Wait until min-interval-ms has passed since the previous provider request of this stage.
     d. vectors = embeddingClient.embedDocuments(inputs)
     e. vectorStore.saveEmbeddings(...) in one transaction.
     f. control.checkpoint()
```

Rules:

- **Resumable.** Every finished batch is committed. When the stage fails or the application stops, the next run continues with the chunks that still need a vector. A run that resumes at `EMBED` (spec file 46, section 10.1) pays only for what is missing.
- **Model change.** A chunk whose `embedding_model` is not the configured model is embedded again and overwritten.
- **Failure.** An `EmbeddingException` becomes a `KnowledgeIngestionException` with stage `EMBED`, the same code, the same `retryable` flag and the fixed safe message. Vectors saved before the failure stay.
- **Waiting** (step c, and the client's `retry-backoff`) must end at once when the thread is interrupted; then fail with `INGESTION_INTERRUPTED` (retryable).
- The stage never changes `content`, `section_title` or `content_hash`.
- No transaction is open during step d.

How the two retry levels work together: the client retries a single request a few times within seconds. If that is not enough, the job fails with a retryable code and the worker of spec file 46 tries again after 2, 10 and 50 minutes. When a daily quota is used up, all automatic attempts fail; the Admin starts the job again later with reprocess (`RESUME`), and it continues where it stopped.

---

## 10. Error Codes

Processing codes of this stage (`failed_stage = EMBED`), also the codes of `EmbeddingException`:

| Code | Retryable | Safe message |
|---|---|---|
| `EMBEDDING_NOT_CONFIGURED` | no | "The embedding model is not configured." |
| `EMBEDDING_AUTHENTICATION_FAILED` | no | "The embedding provider rejected the credentials." |
| `EMBEDDING_REQUEST_REJECTED` | no | "The embedding provider rejected the request." |
| `EMBEDDING_RATE_LIMITED` | yes | "The embedding provider is rate limiting requests." |
| `EMBEDDING_PROVIDER_UNAVAILABLE` | yes | "The embedding provider is temporarily unavailable." |
| `EMBEDDING_TIMEOUT` | yes | "The embedding provider did not answer in time." |
| `EMBEDDING_INVALID_RESPONSE` | yes | "The embedding provider returned an invalid response." |
| `EMBEDDING_DIMENSION_MISMATCH` | no | "The embedding provider returned vectors of an unexpected size." |
| `DOCUMENT_CHUNKS_OUTDATED` | no | "The stored chunks do not match this version. Reprocess the document with mode FULL." |

No new HTTP error code.

`EMBEDDING_REQUEST_REJECTED` usually means a wrong model id, a wrong base URL, a provider that does not accept `dimensions` (set `AI_EMBEDDING_SEND_DIMENSIONS=false`), a provider that does not accept an array (set `AI_EMBEDDING_BATCH_SIZE=1`), or an input that is too long for the model (lower `KNOWLEDGE_CHUNK_MAX_CHARS` and reprocess with mode `FULL`). The backend log shows the HTTP status.

---

## 11. Provider Notes (Google Gemini)

These points were true to the author's knowledge when this file was written. Verify each one (section 2, point 3).

- The OpenAI-compatible base URL is the one already used for chat (`.env.example`: `https://generativelanguage.googleapis.com/v1beta/openai`).
- `gemini-embedding-001` returns 3,072 numbers by default and can return 768 when `dimensions` is sent. Shortened vectors are not unit vectors, which is why the backend normalizes (section 7).
- The model accepts about 2,048 tokens per text. Spec file 49 keeps chunks below that.
- The model is multilingual. Thai, English and Myanmar text share one vector space, so a question in one language can find a passage in another.
- The native Gemini API has task types for documents and for questions. The OpenAI-compatible endpoint has no field for them. Not using them costs some retrieval quality. `EmbeddingClient` keeps `embedDocuments` and `embedQuery` apart so a native client can add them later.
- Free-tier accounts have small per-minute and per-day limits. `min-interval-ms`, the reuse of section 8 and the resume rule exist because of that.

---

## 12. Effect on Existing Endpoints

- `GET /{id}/processing`: `embeddedChunkCount` rises while the stage runs, and the message reads "Creating embeddings (64 of 118 chunks)."
- `GET /{id}/chunks` and `/chunks/{chunkIndex}`: `embedded`, `embeddingModel` and `embeddedAt` are filled.
- Until spec file 51 exists, every job ends as `FAILED` with `failedStage: "INDEX"` and `INGESTION_STAGE_NOT_AVAILABLE`. The vectors are kept.
- `ingestionAvailable` is still `false`.

---

## 13. Deterministic Embedding Client for Tests

Create `DeterministicEmbeddingClient` in the **test** sources. It implements `EmbeddingClient` without any network access and is used by the tests of spec files 50 to 53.

```text
model()      = "test-embedding-768"
dimensions() = 768
configured() = true

vector(text):
  1. t = KnowledgeTextNormalizer.normalize(text), lower-cased with Locale.ROOT, spaces collapsed.
  2. v = 768 zeros.
  3. For every window w of three consecutive code points of t (the whole text when it is shorter):
        h = w.hashCode()                       (String.hashCode is defined by the Java specification)
        v[Math.floorMod(h, 768)] += ((h >>> 16) & 1) == 0 ? 1 : -1
  4. If v is all zeros, set v[0] = 1.
  5. L2-normalize v.
```

Properties that the tests rely on:

- The same text always gives the same vector.
- Texts that share many character sequences get a higher cosine similarity than unrelated texts. This works for Thai and Myanmar too, because it does not need spaces between words.
- It is **not** a semantic model. It cannot match a question to a passage that uses different words. Tests that need that are live checks (section 15).

Give it switches for failure tests: fail the n-th call with a chosen `EmbeddingException`, count calls and count texts.

It is never a bean of the running application. Stage tests pass it to the stage's constructor. Tests that go through the Spring context replace the `EmbeddingClient` bean the same way the existing assistant tests replace `LlmClient`.

---

## 14. Tests

### 14.1 `OpenAiCompatibleEmbeddingClient` (unit tests with a local JDK `HttpServer` stub, as in `ExternalLlmClientTests`)

- The request goes to `/embeddings` with the bearer key and the JSON of section 7; `dimensions` is absent when `send-dimensions` is false.
- An empty `AI_EMBEDDING_BASE_URL` and `AI_EMBEDDING_API_KEY` fall back to the chat values.
- Entries returned in a different order are sorted by `index`.
- Every returned vector has length 768 and norm 1 (tolerance 1e-5), also when the provider's vector was not normalized.
- Wrong number of entries, duplicate index, missing `data`, non-numeric values, `NaN`, an all-zero vector, invalid JSON, a body above the limit → `EMBEDDING_INVALID_RESPONSE`.
- 767 or 3,072 numbers → `EMBEDDING_DIMENSION_MISMATCH`.
- 401 and 403 → `EMBEDDING_AUTHENTICATION_FAILED`, one request only.
- 429 → retried `max-retries` times, then `EMBEDDING_RATE_LIMITED` (retryable). A 429 followed by a 200 succeeds.
- 500 and a refused connection → retried, then `EMBEDDING_PROVIDER_UNAVAILABLE`.
- 400 and 404 → `EMBEDDING_REQUEST_REJECTED`, one request only.
- A response slower than the timeout → `EMBEDDING_TIMEOUT`.
- A 302 is not followed.
- Not configured → `EMBEDDING_NOT_CONFIGURED`, no request.
- No log line contains the key or the input text.
- `embedQuery` uses the query timeout.

### 14.2 `KnowledgeEmbeddingProperties`

- `dimensions` other than 768 is rejected.
- An unsafe base URL is rejected.
- `toString()` does not contain the key.

### 14.3 Stage tests (Testcontainers)

Build `DocumentIngestionServiceImpl` by hand with the real stages `EXTRACT`, `CLEAN`, `CHUNK` and `CreateEmbeddingsStage`, the last one with a `DeterministicEmbeddingClient`.

- After `runOnce()` every chunk has a vector (`vector_dims(embedding) = 768`), `embedding_model = 'test-embedding-768'` and `embedded_at`; the job is `FAILED` at `INDEX` with `INGESTION_STAGE_NOT_AVAILABLE`.
- Batching: 10 chunks with `batch-size` 4 → three provider calls (4, 4, 2). A low `max-batch-tokens` gives smaller batches, never an empty one.
- Resume: the client fails on the second call with `EMBEDDING_RATE_LIMITED` → the job is `FAILED` at `EMBED`, retryable, with `next_attempt_at`; the vectors of the first batch are stored. After the retry time has passed, the next run sends only the remaining chunks.
- Non-retryable failure (`EMBEDDING_AUTHENTICATION_FAILED`): no `next_attempt_at`.
- Reuse across documents: a second document with the same text is embedded with zero provider calls.
- Reuse across revisions: a new revision with one changed paragraph sends only the changed chunks.
- Model change: chunks stored with model A are all embedded again when the client reports model B.
- `DOCUMENT_CHUNKS_OUTDATED` when a chunk's `content_hash` does not match its text.
- Not configured: `EMBEDDING_NOT_CONFIGURED`.
- A stored vector read back as text equals the vector that was sent (tolerance 1e-6).
- Replacing the file between two batches cancels the run (spec file 46).
- The processing API shows `embeddedChunkCount` equal to `chunkCount`.

---

## 15. Opt-in Live Check

One test class, for example `LiveEmbeddingCheck`, that runs only when the environment variable `KNOWLEDGE_EVAL_ENABLED=true` is set in the shell (same idea as `AI_EVAL_ENABLED`, D50). It is skipped in every normal test run.

It sends **one** request with three texts and asserts:

- three vectors with 768 numbers and norm 1;
- "How many credits do I need to graduate?" is closer (cosine) to "To graduate, a student must complete at least 120 credits." than to "Tuition must be paid within 14 days after the first day of the semester."

It prints the model id, the duration and the three similarity values, never the key. Maximum provider usage: one request.

```bash
KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=LiveEmbeddingCheck
```

---

## 16. Postman Verification

Preparation: set the embedding model in `.env` and restart the backend.

```properties
AI_EMBEDDING_MODEL=gemini-embedding-001
```

Provider usage of this check: one request (the six chunks of the sample document fit into one batch).

**1. Continue the sample document**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/reprocess
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 202, `status: "QUEUED"`. (The last run failed at `EMBED`, so the default mode `RESUME` continues there.)

**2. Processing status (after about 15 seconds)**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/processing
Authorization: Bearer <admin-access-token>
```

Expected while spec file 51 is not implemented: `status: "FAILED"`, `failedStage: "INDEX"`, `lastErrorCode: "INGESTION_STAGE_NOT_AVAILABLE"`, `chunkCount: 6`, `embeddedChunkCount: 6`.

**3. A chunk has a vector**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/chunks/4
Authorization: Bearer <admin-access-token>
```

Expected: `embedded: true`, `embeddingModel: "gemini-embedding-001"`, `embeddedAt` is set. The response contains no vector.

**4. Check the vectors in PostgreSQL**

```sql
SELECT c.chunk_index, vector_dims(c.embedding) AS dims,
       round(vector_norm(c.embedding)::numeric, 4) AS norm, c.embedding_model
FROM academic_document_chunks c
JOIN academic_document_files f ON f.id = c.file_id
WHERE f.document_id = <documentId> AND f.is_current
ORDER BY c.chunk_index;
```

Expected: six rows, `dims` 768, `norm` 1.0000.

**5. No second charge**

Call request 1 again and read the processing status.

Expected: the same result, and the backend log shows a `KNOWLEDGE_EMBED` line with `providerCalls=0`.

**6. Not configured**

Remove `AI_EMBEDDING_MODEL` from `.env`, restart, upload the sample document as a new document and call reprocess.

Expected: `status: "FAILED"`, `failedStage: "EMBED"`, `lastErrorCode: "EMBEDDING_NOT_CONFIGURED"`, `nextAttemptAt: null`. Put the variable back afterwards.

---

## 17. Logging

```text
KNOWLEDGE_EMBED documentId=31 fileId=81 model=gemini-embedding-001 chunks=118 reused=40 embedded=78 providerCalls=6 durationMs=9120
KNOWLEDGE_EMBED documentId=31 fileId=81 failed code=EMBEDDING_RATE_LIMITED embeddedBeforeFailure=32
EMBEDDING_PROVIDER status=429 texts=16 durationMs=210
```

Ids, counts, codes, the model id and durations only.

---

## 18. Acceptance Criteria

- [ ] `EmbeddingClient` and one implementation for the OpenAI-compatible `/embeddings` endpoint, built on the JDK HTTP client.
- [ ] No Spring AI, LangChain4j, provider SDK or local model.
- [ ] `AI_EMBEDDING_*` configuration with the fallback to the chat settings resolved in code; the key is masked.
- [ ] `dimensions` other than 768 stops the application at startup.
- [ ] Responses are validated (count, order, size, finite values) and vectors are L2-normalized before they are stored.
- [ ] Redirects are not followed; timeouts are always set; only 429, 5xx and connection failures are retried in the client.
- [ ] `CreateEmbeddingsStage` is the only bean for `EMBED` and follows the stage contract of spec file 46.
- [ ] Vectors are saved batch by batch; a failed or interrupted run resumes without embedding a chunk twice.
- [ ] Identical passages reuse an existing vector of the same model.
- [ ] A chunk embedded with another model is embedded again.
- [ ] Only the embedding input of spec file 49 is sent to the provider.
- [ ] No key, text, vector or provider body in logs or error messages.
- [ ] Automated tests use the local stub or `DeterministicEmbeddingClient` only; the live check is opt-in and uses one request.
- [ ] `.env.example` and `compose.yaml` updated; no real key committed.
- [ ] Tests of section 14 pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 19. Agent Completion Report

Report: files created and modified; what the provider documentation said on the day (model id, array input, `dimensions`, token limit, rate limits) and the settings chosen because of it; whether the live check was run and its three similarity values; how resume and reuse were tested; changed existing tests; test totals against the baseline; Postman results actually performed, including the number of provider requests used; known limitations (no task types, `FULL` reprocess embeds again); decision entries added to `00-changes-and-decision.md`.

Do not claim that documents are searchable: the pipeline still stops before `READY`.

**Next: spec file 51 — Step 14G: Store in Vector Database.**
