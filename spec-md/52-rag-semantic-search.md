# 52 — Step 14H: RAG Document Processing — Semantic Search

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** Search the indexed chunks by meaning: prepare the question, embed it, find the nearest eligible chunks, apply the access rules, and return the best passages. A search service for the backend and a search endpoint for Admins.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector 0.8 or newer, Redis 7.4
**Prerequisites:** Spec files 45 to 51 are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → 47 extract → 48 clean → 49 chunk → 50 embed → 51 vector store → **52 search (this file)** → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
Store in Vector Database
    ↓
Semantic Search              ← this file
    ↓
AI Assistant knowledge tool
```

Semantic search turns a question into a vector with the same embedding model that was used for the chunks, and returns the chunks whose vectors are closest to it. It finds a passage by meaning, not by exact words: "How many credits do I need to graduate?" finds "a student must complete at least 120 credits", although the wording is different.

After this step:

- `KnowledgeSearchService` returns the best passages for a question, each with its document, section, pages and a score;
- only passages that may be shown are returned (R10), and a search for a Student is limited to that Student's own scope;
- an Admin can try a search from Postman or the Admin Web and see what the assistant will later get;
- the assistant does **not** use the search yet. That is spec file 53.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, decision R10 (spec file 45, section 5), spec file 48 section 7 (the text normalizer), spec file 50 section 6 (`EmbeddingClient`) and spec file 51 section 8 (the query rules for the vector column).
2. Inspect `PgVectorKnowledgeVectorStore`, `KnowledgeDocumentMapper` (how the scope type of a document is derived), `KnowledgeDocumentListQuery`, `AcademicYearLabel`, `CurrentStudentProvider`, `AssistantConversationRepository` (how Redis is used) and `GlobalExceptionHandler`.
3. Run the tests and the build.

Do not:

- Add a migration, a dependency, a search engine, a full-text index or keyword search.
- Add a search endpoint for Students. Students reach the search only through the assistant (spec file 53).
- Call the search from the assistant in this step.
- Return a vector, a storage key, a checksum or a stored file name.
- Cache search results. Only the query vector is cached (section 7).
- Keep a database transaction open while the embedding provider is called.
- Put a request value into SQL text. The statement is built from fixed fragments and bind parameters only.
- Use `SET` without `LOCAL` for a pgvector setting (spec file 51, section 8).
- Log the question, a passage, a vector or a key.
- Take the Student scope from a request field of a Student API or from a model argument.

---

## 3. Package Structure

```text
feature/knowledge/
├── controller/
│   └── AdminKnowledgeSearchController.java        (new: POST /api/v1/admin/knowledge/search)
├── dto/
│   ├── KnowledgeSearchRequest.java                (new)
│   ├── KnowledgeSearchResponse.java               (new)
│   └── KnowledgeSearchResultResponse.java         (new)
├── exception/
│   └── KnowledgeFailure.java                      (+ searchNotConfigured, searchUnavailable, searchRateLimited)
├── repository/
│   └── QueryEmbeddingCache.java                   (new: query vectors in Redis)
└── service/
    ├── KnowledgeSearchService.java                (new)
    ├── KnowledgeSearchHit.java                    (new, record: one result inside the backend)
    ├── StudentKnowledgeScope.java                 (new, record)
    ├── KnowledgeSearchFilter.java                 (new, record)
    ├── KnowledgeChunkMatch.java                   (new, record: one row of the search statement)
    ├── KnowledgeVectorStore.java                  (+ search)
    └── impl/
        ├── KnowledgeSearchServiceImpl.java        (new)
        └── PgVectorKnowledgeVectorStore.java      (+ search)

config/
└── KnowledgeSearchProperties.java                 (new, app.knowledge.search)
```

---

## 4. Configuration

`application.properties`:

```properties
# Step 14H: semantic search over knowledge chunks.
app.knowledge.search.default-top-k=${KNOWLEDGE_SEARCH_DEFAULT_TOP_K:5}
app.knowledge.search.min-score=${KNOWLEDGE_SEARCH_MIN_SCORE:0.50}
app.knowledge.search.max-per-document=${KNOWLEDGE_SEARCH_MAX_PER_DOCUMENT:3}
app.knowledge.search.hnsw-ef-search=${KNOWLEDGE_SEARCH_HNSW_EF_SEARCH:100}
app.knowledge.search.query-cache-ttl=${KNOWLEDGE_SEARCH_QUERY_CACHE_TTL:24h}
```

| Property | Meaning | Validation |
|---|---|---|
| `default-top-k` | Number of passages returned when the caller gives none. | 1 to 10 |
| `min-score` | A passage with a lower score is not returned (section 11). | 0.0 to 1.0 |
| `max-per-document` | At most this many passages of one document in one result. | 1 to 10 |
| `hnsw-ef-search` | pgvector `hnsw.ef_search` for the search statement (section 10). | 50 to 1,000 |
| `query-cache-ttl` | How long a query vector stays in Redis. `0` turns the cache off. | 0 to 7 days |

An invalid value stops the application at startup, as for the other knowledge properties.

Fixed in code, not configurable:

| Value | Number |
|---|---|
| Largest `topK` | 10 |
| Longest question | 500 characters |
| Candidates read from the database | `min(50, max(20, topK × 4))` |

Add the five variables to `.env.example` and pass them to the `backend` service in `compose.yaml`:

```properties
# Step 14H semantic search. MIN_SCORE is a starting value: calibrate it for the embedding model (spec file 52, section 18).
KNOWLEDGE_SEARCH_DEFAULT_TOP_K=5
KNOWLEDGE_SEARCH_MIN_SCORE=0.50
KNOWLEDGE_SEARCH_MAX_PER_DOCUMENT=3
KNOWLEDGE_SEARCH_HNSW_EF_SEARCH=100
KNOWLEDGE_SEARCH_QUERY_CACHE_TTL=24h
```

---

## 5. Search Flow

```text
1. Check the request values (sections 6 and 13).
2. Prepare the question text (section 6).
3. embeddingClient.configured() is false → KNOWLEDGE_SEARCH_NOT_CONFIGURED.
4. Get the query vector: from the cache, otherwise embeddingClient.embedQuery(text), then store it in the cache (section 7).
5. vectorStore.search(vector, model, filter, candidateLimit) → one short read-only transaction (section 10).
6. Score, threshold, limit per document, topK, ranks (section 11).
7. Write one log line (section 16) and return.
```

No database transaction is open during steps 1 to 4. The provider call can take seconds, and a pooled connection must not wait for it. `KnowledgeSearchServiceImpl` therefore has no `@Transactional`; the transaction belongs to `PgVectorKnowledgeVectorStore.search`.

Every search reads the database again. Archiving, deleting or replacing a document takes effect on the next search.

---

## 6. Preparing the Question

```text
prepare(query):
  1. q = KnowledgeTextNormalizer.normalize(query)     same call and same thai-digits-to-ascii value as the CLEAN stage
  2. Replace every "\n" with a space. Collapse runs of spaces into one. Trim.
  3. q is empty            → VALIDATION_ERROR
  4. q.length() > 500      → VALIDATION_ERROR
```

- The raw `query` of a request must not be blank and must not be longer than 500 characters. The prepared text is checked again, because normalization can remove everything (a question made only of invisible characters) or make the text longer (ligatures).
- A character is one Java `char`, as in spec file 47.
- The text is not lower-cased, not translated, not shortened and not extended. The model receives the question as it was asked.

Why the question is normalized: a chunk was normalized before it was embedded (spec file 48). The question must go through the same steps, so that Thai digits, SARA AM and invisible characters copied from a PDF are written the same way on both sides.

---

## 7. Query Vector and Its Cache

The vector of a question comes from `embeddingClient.embedQuery(q)` (spec file 50, section 6): 768 numbers, L2-normalized, made by the same model as the chunk vectors.

`QueryEmbeddingCache` keeps that vector in Redis, so the same question costs one provider request per `query-cache-ttl` instead of one per search. The embedding quota is small (spec file 50, section 11), and students ask the same questions.

| Item | Rule |
|---|---|
| Key | `knowledge:search:query-vector:` + SHA-256 (lower-case hex) of the UTF-8 bytes of `model + "\n" + dimensions + "\n" + q` |
| Value | Base64 of 3,072 bytes: the 768 `float` values, 4 bytes each, big-endian (the default byte order of `java.nio.ByteBuffer`) |
| Time to live | `query-cache-ttl`. With `0` the cache is neither read nor written. |
| Client | `StringRedisTemplate`, as `AssistantConversationRepository` uses it |

Rules:

- The model is part of the key. After a model change the old entries are never read again and expire on their own.
- A value that cannot be decoded into exactly 768 finite numbers is a miss, and the new vector replaces it.
- A Redis failure never fails a search. A failed read is a miss; a failed write is ignored. Each failure writes one `WARN` line without a stack trace: `KNOWLEDGE_SEARCH_CACHE_FAILURE operation=read error=RedisConnectionFailureException`.
- Only the result of a successful provider call is stored.
- The key holds a hash and the value holds numbers. The text of a question is never stored in Redis.

Size: one entry is about 4.3 KB. Ten thousand different questions in one day are about 45 MB. Redis runs with `maxmemory-policy noeviction` (`compose.yaml`), so entries leave only when their time to live ends. If Redis memory is tight, lower `KNOWLEDGE_SEARCH_QUERY_CACHE_TTL` or set it to `0`.

When Redis is down, every cache call waits for the Redis timeout (2 seconds by default). A search is then slower but still answers.

---

## 8. Which Chunks May Be Returned

R10 as SQL. A chunk is returned only when **all** conditions hold:

| Condition | SQL |
|---|---|
| The chunk has a vector of the configured model. | `c.embedding IS NOT NULL AND c.embedding_model = :model` |
| Its file is the current revision of the document. | `f.is_current` |
| The file is completely indexed. | `p.status = 'READY'` |
| The document is active and not deleted. | `d.is_active AND NOT d.is_deleted` |
| A linked curriculum version is active. | `d.curriculum_version_id IS NULL OR cv.status = 'ACTIVE'` |

Consequences:

- An archived document, a deleted document and an older revision are never returned, even while their rows still exist.
- A document whose new revision is still being processed is not returned (spec file 51, section 5.2).
- A stale file (spec file 51, section 9) is not returned: none of its chunks has a vector of the configured model.
- A document linked to an archived curriculum version is not returned. When the curriculum version is activated again, its documents are searchable again without reprocessing.
- A curriculum version that has documents cannot be deleted (`CurriculumVersionServiceImpl`), so no check for a deleted curriculum version is needed.

Not a condition:

- `is_active` of the category, the program or the department. An inactive category only stops new documents from being filed under it. To hide a document, archive the document.

The conditions are checked in SQL on every search. No flag is copied onto the chunks.

---

## 9. Student Scope

A search for a Student adds one more condition. The scope is described by:

```java
/** What a Student may see in addition to university-wide documents. Both values may be null. */
public record StudentKnowledgeScope(Long programId, Long departmentId) {}
```

| Scope of the document (spec file 39) | Returned to the Student when |
|---|---|
| `UNIVERSITY` (no link) | always |
| `DEPARTMENT` | it is the Student's department, or the department of the Student's program |
| `PROGRAM` | it is the Student's program |
| `CURRICULUM` | the curriculum version belongs to the Student's program (and is `ACTIVE`, section 8) |

- A Student without a program and without a department sees university-wide documents only.
- The schema has no link between a Student and one curriculum version. "The Student's curriculum" means the active curriculum version of the Student's program.
- The values come from the Student's own record. Spec file 53 reads them with `AcademicQueryService.getMyAcademicProfile()`. They are never taken from a request field of a Student API or from an argument written by the model.
- Without a scope (`null`) there is no such condition. This is the Admin view: every eligible document.

SQL fragment:

```sql
AND (
      (d.curriculum_version_id IS NULL AND d.program_id IS NULL AND d.department_id IS NULL)
   OR d.program_id = :programId                                                                 -- only when programId is given
   OR cv.program_id = :programId                                                                -- only when programId is given
   OR d.department_id = (SELECT sp.department_id FROM programs sp WHERE sp.id = :programId)     -- only when programId is given
   OR d.department_id = :departmentId                                                           -- only when departmentId is given
)
```

A line is added to the statement only when its value is present. Never bind `NULL` to `:programId` or `:departmentId`: an untyped `NULL` parameter is a known source of PostgreSQL type errors, and leaving the line out says the same thing.

---

## 10. The Search Statement

Add to `KnowledgeVectorStore`:

```java
/** The nearest eligible chunks, closest first. Runs in its own read-only transaction. */
List<KnowledgeChunkMatch> search(float[] queryVector, String model, KnowledgeSearchFilter filter, int limit);
```

```java
/** studentScope null = no audience restriction (Admin view). The other three values may each be null. */
public record KnowledgeSearchFilter(StudentKnowledgeScope studentScope, Long categoryId,
                                    String academicYearLabel, Long documentId) {}

public record KnowledgeChunkMatch(
        long chunkId, int chunkIndex, String sectionTitle, int pageStart, int pageEnd, String content,
        long fileId, int revisionNumber, String fileExtension,
        long documentId, String title, String academicYearLabel,
        Long categoryId, String categoryCode, String categoryName,
        Long curriculumVersionId, Long programId, Long departmentId,
        double distance) {}
```

`search` is annotated `@Transactional(readOnly = true)` and runs two statements on the same connection.

**Statement 1: pgvector settings for this transaction**

```sql
SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true),
       set_config('hnsw.ef_search', :efSearch, true);
```

`set_config(name, value, true)` is `SET LOCAL` written as a function. It is used because `SET LOCAL` cannot take a bind parameter. The third argument must be `true`: the setting then ends with the transaction and never stays on the pooled connection (spec file 51, section 8, rule 7). `:efSearch` is the configured number as text.

**Statement 2: the search**

```sql
WITH candidates AS MATERIALIZED (
    SELECT c.id AS chunk_id, c.chunk_index, c.section_title, c.page_start, c.page_end, c.content,
           f.id AS file_id, f.revision_number, f.file_extension,
           d.id AS document_id, d.title, d.academic_year_label, d.category_id,
           d.curriculum_version_id, d.program_id, d.department_id,
           c.embedding <=> CAST(:queryVector AS vector) AS distance
    FROM academic_document_chunks c
    JOIN academic_document_files f      ON f.id = c.file_id
    JOIN academic_document_processing p ON p.file_id = f.id
    JOIN academic_documents d           ON d.id = f.document_id
    LEFT JOIN curriculum_versions cv    ON cv.id = d.curriculum_version_id
    WHERE c.embedding IS NOT NULL
      AND c.embedding_model = :model
      AND f.is_current
      AND p.status = 'READY'
      AND d.is_active
      AND NOT d.is_deleted
      AND (d.curriculum_version_id IS NULL OR cv.status = 'ACTIVE')
      -- optional fragments, see below
    ORDER BY c.embedding <=> CAST(:queryVector AS vector)
    LIMIT :limit
)
SELECT k.*, dc.category_code, dc.category_name
FROM candidates k
LEFT JOIN document_categories dc ON dc.id = k.category_id
ORDER BY k.distance, k.chunk_id;
```

Optional fragments, each a fixed piece of text that is added only when its value is present:

| Filter value | Fragment |
|---|---|
| `studentScope` | the condition of section 9 |
| `categoryId` | `AND d.category_id = :categoryId` |
| `academicYearLabel` | `AND d.academic_year_label = :academicYearLabel` |
| `documentId` | `AND d.id = :documentId` |

Rules:

- The statement follows spec file 51, section 8: the `<=>` operator, ascending order with a limit, the vector as a text literal in `CAST(… AS vector)`, the filters on `embedding IS NOT NULL` and `embedding_model`, and no `embedding` column in the result.
- The inner query can deliver its rows slightly out of order (`relaxed_order`). The outer query sorts them again by the exact distance, then by `chunk_id`, so the order is always the same for the same data.
- `search` is called without a surrounding transaction (section 5). Both statements must run in the transaction that `search` opens. Outside a transaction the settings of statement 1 would be lost before statement 2 runs.
- Map the result by column name, not by position.
- With a few thousand chunks PostgreSQL may read the table and sort instead of using the HNSW index. That result is exact. Do not force the index.
- With a very selective filter on a very large index, an iterative scan stops after `hnsw.max_scan_tuples` rows (20,000 by default) and can return fewer rows than the limit. This is accepted for the size of this knowledge base.

---

## 11. From Candidates to Results

```text
candidateLimit = min(50, max(20, topK × 4))
score          = 1 − distance          cosine similarity; both vectors are L2-normalized. Limit it to the range −1 … 1.

1. Read candidateLimit candidates (section 10). They arrive closest first.
2. Drop every candidate with score < minScore.
3. Go through the rest in order. Keep a candidate, unless max-per-document results of the same document are already kept.
   Skip this rule when the search is limited to one document (documentId).
4. Stop when topK results are kept.
5. rank = 1, 2, 3 … in that order.
```

- `topK`: 1 to 10. `minScore`: 0.0 to 1.0. Defaults come from section 4.
- The score is compared with `minScore` first and rounded afterwards. A returned score is rounded to 4 decimal places (half up).
- A search that returns no result is a normal answer, not an error.

**Why a threshold.** The nearest chunk always exists, also for a question the knowledge base cannot answer. Without a threshold every search would "find" something.

**Why a limit per document.** A long handbook with many similar passages must not take every place when another document also answers the question.

**The default threshold is a starting value.** Similarity scores depend on the embedding model: for one model unrelated texts score around 0.2, for another around 0.6. 0.50 is a cautious first value for `gemini-embedding-001`. Calibrate it with section 18 before relying on an empty result to mean "the knowledge base has no answer". Calibrate it again after a model change.

---

## 12. Service Contract

```java
public interface KnowledgeSearchService {
    /** Search for an Admin: every eligible document, or the view of a Student with the given scope. */
    KnowledgeSearchResponse adminSearch(AuthenticatedUser actor, KnowledgeSearchRequest request);

    /** Search for the signed-in Student. Used by the assistant tool of spec file 53. No controller calls it. */
    List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, int topK);
}

public record KnowledgeSearchHit(int rank, double score, long documentId, String title, String categoryName,
        String academicYearLabel, String sectionTitle, String unitType, int pageStart, int pageEnd, String content) {}
```

Both methods run the same flow (section 5). They differ only in who may call them and in what they accept:

| | `adminSearch` | `studentSearch` |
|---|---|---|
| Caller | `AdminKnowledgeSearchController` | the assistant tool (spec file 53) |
| Guard | method-level `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")` | first line: `currentStudentProvider.getCurrentStudentId()`. It throws unless the caller is a signed-in Student. |
| Scope | none, or the `studentScope` of the request as a preview | the `scope` argument, never `null` |
| `topK` | request value or `default-top-k` | the argument, 1 to 10 |
| `minScore` | request value or `min-score` | always `min-score` |
| Filters | `categoryId`, `academicYear`, `documentId` | none |
| Invalid argument | `KnowledgeFailure.invalid()` | `KnowledgeFailure.invalid()` for the question; `IllegalArgumentException` for a `null` scope or a `topK` outside 1 to 10 (a programming error) |
| Other failures | `KnowledgeFailure` of section 14 | the same |

`KnowledgeSearchServiceImpl` does **not** carry the class-level `@PreAuthorize` of the other knowledge services, because it serves two audiences. Each method has its own guard, and tests prove both (section 17.3).

`unitType` tells a reader what `pageStart` and `pageEnd` count. It is derived from the file extension:

| Extension | `unitType` | The numbers count |
|---|---|---|
| `pdf` | `PAGE` | pages of the PDF, in file order (not a page number printed on the page) |
| `docx` | `SECTION` | sections of the document |
| `xlsx` | `SHEET` | sheets |
| `txt` | `TEXT` | text units |

---

## 13. Admin Search Endpoint

```http
POST /api/v1/admin/knowledge/search
Authorization: Bearer <admin-access-token>
Content-Type: application/json
```

Access: SUPER_ADMIN, ADMIN, ACADEMIC_ADMIN (the existing rule for `/api/v1/admin/knowledge/**` in `SecurityConfig`, plus the guard of section 12). `Cache-Control: no-store`.

It is a `POST` although it changes nothing: a question can be long, can contain any character, and must not appear in URLs, proxy logs or the browser history.

Request body (only `query` is required):

```json
{
  "query": "How many credits do I need to graduate?",
  "topK": 5,
  "minScore": 0.5,
  "categoryId": 3,
  "academicYear": "2026-2027",
  "documentId": 31,
  "studentScope": { "programId": 4, "departmentId": 2 }
}
```

| Field | Type | Rule |
|---|---|---|
| `query` | string | Required. Not blank, at most 500 characters, and not empty after preparation (section 6). |
| `topK` | integer | 1 to 10. Default `default-top-k`. |
| `minScore` | number | 0.0 to 1.0. Default `min-score`. |
| `categoryId` | integer | Greater than 0. Only documents of this category. |
| `academicYear` | string | A label such as `2026-2027`, checked and normalized with `AcademicYearLabel.normalize`. Only documents of this academic year. |
| `documentId` | integer | Greater than 0. Only this document. |
| `studentScope` | object | Leave it out for the Admin view. When present, the result is what a Student with these values would get (section 9). `programId` and `departmentId` are optional and greater than 0. `{}` is a Student without program and department. |

- An id that matches nothing gives an empty result. It is not an error, and ids are not looked up first.
- Unknown fields, wrong types and malformed JSON are answered with 400 `VALIDATION_ERROR` by the existing `GlobalExceptionHandler` (the project sets `fail-on-unknown-properties=true`).

Response 200:

```json
{
  "query": "How many credits do I need to graduate?",
  "model": "gemini-embedding-001",
  "topK": 5,
  "minScore": 0.5,
  "studentScope": null,
  "tookMs": 412,
  "results": [
    {
      "rank": 1,
      "score": 0.7312,
      "documentId": 31,
      "title": "Sample Academic Regulations 2026",
      "category": { "id": 3, "code": "REGULATION", "name": "Regulations" },
      "academicYearLabel": "2026-2027",
      "scopeType": "UNIVERSITY",
      "fileId": 81,
      "revisionNumber": 1,
      "chunkIndex": 4,
      "sectionTitle": "Chapter 5 Graduation",
      "unitType": "TEXT",
      "pageStart": 1,
      "pageEnd": 1,
      "content": "Chapter 5 Graduation\nArticle 11. To graduate, a student must complete at least 120 credits …"
    }
  ]
}
```

Values are illustrative.

| Field | Meaning |
|---|---|
| `query` | The prepared question (section 6), so the Admin sees what was embedded. |
| `model` | The embedding model in effect. |
| `topK`, `minScore`, `studentScope` | The values that were used, after defaults. `studentScope` is `null` for the Admin view. |
| `tookMs` | Duration of the whole search in milliseconds. |
| `results` | Best first. An empty list when nothing reaches `minScore`. |
| `scopeType` | `CURRICULUM`, `PROGRAM`, `DEPARTMENT` or `UNIVERSITY`, derived as in `KnowledgeDocumentMapper`. |
| `sectionTitle` | `null` when the chunk has no heading. |
| `content` | The whole chunk text, exactly as stored. |

The response is a bare DTO without a wrapper (D54). It never contains a vector, a distance, a storage key, a checksum or a file name.

---

## 14. Error Codes

| HTTP | Code | When | Message |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | The request is invalid (sections 6 and 13). | "The request is invalid." (existing) |
| 401 / 403 | existing codes | No token, a Student token, or an Admin role without access. | existing |
| 429 | `KNOWLEDGE_SEARCH_RATE_LIMITED` | The embedding provider answered with a rate limit. | "Semantic search is busy. Try again shortly." |
| 503 | `KNOWLEDGE_SEARCH_NOT_CONFIGURED` | No embedding model is configured. | "Semantic search is not configured." |
| 503 | `KNOWLEDGE_SEARCH_UNAVAILABLE` | Any other failure of the embedding provider, or a database failure during the search. | "Semantic search is temporarily unavailable." |

Mapping inside `KnowledgeSearchServiceImpl`:

| Cause | Result |
|---|---|
| `embeddingClient.configured()` is false, or `EmbeddingException` with `EMBEDDING_NOT_CONFIGURED` | `KNOWLEDGE_SEARCH_NOT_CONFIGURED` |
| `EmbeddingException` with `EMBEDDING_RATE_LIMITED` | `KNOWLEDGE_SEARCH_RATE_LIMITED` |
| every other `EmbeddingException` | `KNOWLEDGE_SEARCH_UNAVAILABLE` |
| `DataAccessException` from the vector store | `KNOWLEDGE_SEARCH_UNAVAILABLE` |

The embedding code (for example `EMBEDDING_TIMEOUT`) is written to the log line, never to the response. Add the three factory methods to `KnowledgeFailure`.

An empty result is HTTP 200 with `"results": []`.

---

## 15. What This Search Does Not Do

Known limits of Step 14. Do not work around them in this step.

- **No keyword search.** The search compares meaning. An exact identifier, such as a form number or a course code, can rank badly. Course questions are answered by the database tools of the assistant, not by this search. Combining vector search with PostgreSQL full-text search is a possible later step.
- **No second ranking model** and no rewriting or translation of the question. A question in one language finds a passage in another language only as well as the embedding model allows.
- **No preference for the newest academic year.** When the same regulation exists for two academic years and both documents are active, both can be returned. Every result carries `academicYearLabel`. Archive documents that are no longer valid.
- **No removal of duplicates.** The same passage in two active documents is returned twice.
- **No paging.** A search returns at most 10 passages.
- **Text that was not extracted cannot be found:** scanned PDFs, text inside images, and tables that lost their structure (spec file 47, section 8.1).

---

## 16. Logging

One line per search:

```text
KNOWLEDGE_SEARCH audience=ADMIN actorUserId=7 queryChars=38 cache=MISS candidates=20 returned=3 topScore=0.7312 embedMs=388 dbMs=9 totalMs=412
KNOWLEDGE_SEARCH audience=STUDENT queryChars=41 cache=HIT candidates=20 returned=0 topScore=0.4120 embedMs=0 dbMs=7 totalMs=9
KNOWLEDGE_SEARCH audience=ADMIN actorUserId=7 failed code=KNOWLEDGE_SEARCH_UNAVAILABLE cause=EMBEDDING_TIMEOUT
```

- `audience` is `ADMIN`, `STUDENT_PREVIEW` (an Admin search with `studentScope`) or `STUDENT`.
- `cache` is `HIT`, `MISS` or `OFF`.
- `topScore` is the score of the nearest candidate **before** the threshold, or `-` when there is no candidate. It shows how far a question was from the threshold and is the number needed for calibration.
- Successful searches are logged at `INFO`, failures at `WARN`.
- Never logged: the question, a passage, a title, a vector, a key.

---

## 17. Tests

Tests never call a real provider (R12). Unless stated otherwise they use `DeterministicEmbeddingClient` (spec file 50, section 13).

### 17.1 Unit tests (no Spring context)

Question preparation:

| Input | Result |
|---|---|
| `"  How many   credits?\n"` | `"How many credits?"` |
| a question with Thai digits `๑๒๐ หน่วยกิต` | `120 หน่วยกิต` (with `thai-digits-to-ascii` on) |
| only spaces, or only U+200B and U+FEFF | `VALIDATION_ERROR` |
| 500 characters | accepted |
| 501 characters | `VALIDATION_ERROR` |
| 499 characters that become more than 500 through ligatures | `VALIDATION_ERROR` |

From candidates to results (a fake `KnowledgeVectorStore` returns fixed matches):

- Distances 0.10, 0.20, 0.60 with `minScore` 0.5 give two results with scores 0.9 and 0.8 and ranks 1 and 2.
- A score exactly equal to `minScore` is kept.
- Five candidates of one document and one of another, `max-per-document` 3, `topK` 5: three results of the first document, then the one of the other document, in distance order, ranks 1 to 4.
- The same input with `documentId` set: five results of the first document.
- `topK` 1 returns one result; `candidateLimit` is 20 for `topK` 1 to 5, 24 for 6, 40 for 10.
- The score is rounded to four decimals after the comparison with `minScore`.
- `unitType` for `pdf`, `docx`, `xlsx`, `txt`.
- `scopeType` for a curriculum, program, department and university document.

Errors:

- Not configured: `KNOWLEDGE_SEARCH_NOT_CONFIGURED`, and neither the cache nor the vector store is called.
- `EMBEDDING_RATE_LIMITED` gives `KNOWLEDGE_SEARCH_RATE_LIMITED`; `EMBEDDING_TIMEOUT`, `EMBEDDING_AUTHENTICATION_FAILED` and `EMBEDDING_INVALID_RESPONSE` give `KNOWLEDGE_SEARCH_UNAVAILABLE`.
- A `DataAccessException` from the vector store gives `KNOWLEDGE_SEARCH_UNAVAILABLE`.
- No message of a `KnowledgeFailure` contains provider text.

### 17.2 `QueryEmbeddingCache` (Redis Testcontainer)

- A stored vector is read back with exactly the same 768 values.
- Another model, or another question, is a miss.
- The entry has the configured time to live. With `0` nothing is read and nothing is written.
- A value that is not Base64, or has the wrong length, is a miss and is replaced by the next write.
- With an unreachable Redis, a read returns a miss and a write returns normally.
- The key contains no part of the question.

### 17.3 Vector store search (PostgreSQL Testcontainer)

Create a small helper that inserts a document, a file, a processing row and chunks with **chosen** vectors (for example vectors that are 1 at one position and 0 elsewhere, and a query vector with a known cosine to each). The expected order and scores are then exact numbers.

Order and limit:

- Rows come back closest first; equal distances are ordered by chunk id; `limit` is respected.
- `distance` matches the value calculated in the test.

Never returned (one test each):

- a chunk without a vector; a chunk with a vector of another model;
- a chunk of a revision that is not current;
- a file with status `UPLOADED`, `QUEUED`, `PROCESSING`, `CHUNKING`, `EMBEDDING` or `FAILED`;
- an archived document; a deleted document;
- a document linked to an `ARCHIVED` curriculum version.

Returned: a document linked to an `ACTIVE` curriculum version; a document in an inactive category.

Student scope (one data set, one test per row):

| Document | Scope `(programId = P1, departmentId = D1)`; P1 belongs to D1 | Scope `(null, null)` |
|---|---|---|
| university-wide | returned | returned |
| department D1 | returned | not returned |
| department D2 | not returned | not returned |
| program P1 | returned | not returned |
| program P2 | not returned | not returned |
| active curriculum of P1 | returned | not returned |
| active curriculum of P2 | not returned | not returned |

Also: scope `(P1, null)` returns the documents of department D1 (through the program); scope `(null, D2)` returns the documents of department D2 and no program document.

Filters: `categoryId`, `academicYearLabel` and `documentId`, each alone and all three together; a filter that matches nothing returns an empty list.

Settings:

- Configure `hnsw-ef-search=123` for the test. After `search` returns, `SELECT current_setting('hnsw.ef_search')` on the pooled connections never gives `123`. Repeat the pair twenty times.
- `search` works when it is called without a transaction, and the two statements run in one transaction.

### 17.4 Search through the Spring context (`KnowledgeSearchIntegrationTests`)

Replace the `EmbeddingClient` bean with `DeterministicEmbeddingClient`. Upload the sample document of spec file 47, section 19, through the upload endpoint and call `runOnce()` until it is `READY`.

The deterministic client compares character sequences, not meaning. These questions reuse words of the document. For each one the expected chunk must have rank 1 (send `minScore: 0`):

| Question | Expected `chunkIndex` |
|---|---:|
| `a student must complete at least 120 credits required by the curriculum` | 4 |
| `late registration fee of 500 baht` | 1 |
| `late payment fine of 50 baht per day` | 2 |
| `attend at least 80 percent of the classes` | 3 |
| `นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษา` | 5 |
| `An academic year has two regular semesters and one summer session` | 0 |
| `How many credits do I need to graduate?` | 4 |

When this file was written, a simulation of the deterministic client gave rank 1 for all seven, with scores above 0.50 for the first, fifth and sixth question, and a best score below 0.25 for `What are the opening hours of the swimming pool?`. Use these two facts for the threshold tests: with `minScore: 0.30` the first question returns chunk 4 and the swimming pool question returns an empty list. If the implementation gives another order, find out whether the client, the chunker or this file is wrong before changing a test.

Further cases:

- The first question of the table, with the sample document as the only searchable document: without `documentId` it returns exactly 3 results (`max-per-document`); with `documentId` it returns 5.
- The same question twice: the second search does not call the embedding client (count the calls) and returns the same results.
- With `query-cache-ttl=0` every search calls the client.
- Archive the document: no result. Activate it: the results are back, and no job was queued.
- Delete the document: no result, before and after the clean-up pass.
- Replace the file: no result while the new revision is not `READY`; afterwards the results come from the new revision only.
- Switch the model id of the client: no result (the index is stale). Reprocess: the results are back.
- A client that fails: 503 `KNOWLEDGE_SEARCH_UNAVAILABLE`, 429 `KNOWLEDGE_SEARCH_RATE_LIMITED`, and 503 `KNOWLEDGE_SEARCH_NOT_CONFIGURED` when it reports not configured.
- Inside `embedQuery` no transaction is active (`TransactionSynchronizationManager.isActualTransactionActive()` is false). Assert it from a client that records the value.

Endpoint contract:

- No token 401; Student token 403; SUPER_ADMIN, ADMIN and ACADEMIC_ADMIN 200.
- 400 `VALIDATION_ERROR` for: missing `query`, blank `query`, 501 characters, `topK` 0, `topK` 11, `topK` as text, `minScore` −0.1, `minScore` 1.1, `categoryId` 0, `academicYear` `2026`, `studentScope.programId` 0, an unknown field, malformed JSON.
- The response has `Cache-Control: no-store` and contains none of: `embedding`, `distance`, `storageKey`, `sha256`, `originalFilename`.
- `studentScope: {}` returns university-wide documents only; `studentScope` with a program returns that program's documents too.

Guards of the service:

- `studentSearch` in a Student security context returns results for a university-wide document.
- `studentSearch` in an Admin security context, and without authentication, is refused.
- `adminSearch` in a Student security context is refused.
- `studentSearch` with a `null` scope or `topK` 0 throws `IllegalArgumentException`.

### 17.5 Existing tests

No existing endpoint, table or bean changes its behaviour. The full suite must pass without changes to existing tests. List any test that had to be touched and why.

---

## 18. Retrieval Evaluation and Threshold Calibration

Section 17 proves that the search works. It cannot prove that the search finds the **right** passage for a real question, because the test client does not understand meaning. That needs the real model.

### 18.1 Evaluation cases

Create `KnowledgeRetrievalEvaluationDataset` in the test sources (a Java class, like `IntentEvaluationDataset` of Step 9). Each case has: `caseId`, `language`, `question`, `expectation` (`FOUND` or `NOT_FOUND`) and, for `FOUND`, `mustContain` (text that appears in exactly one chunk of the sample document).

Start with these 24 cases for the sample document:

| `caseId` | Question | `mustContain` |
|---|---|---|
| EN-01 | How many credits do I need to graduate? | `at least 120 credits` |
| EN-02 | What is the minimum GPA required for graduation? | `at least 2.00` |
| EN-03 | What is the maximum number of credits I can register for in a regular semester? | `at most 22 credits` |
| EN-04 | Until when can I drop a course without it showing on my transcript? | `first 14 days of a regular semester` |
| EN-05 | How much is the late registration fee? | `500 baht` |
| EN-06 | What happens if I pay my tuition late? | `50 baht per day` |
| EN-07 | What attendance do I need to take the final exam? | `80 percent` |
| EN-08 | How long do I have to ask for a grade review? | `within 7 days` |
| EN-09 | When must I apply for graduation? | `first 30 days of the final semester` |
| EN-10 | How many weeks is the summer session? | `lasts 8 weeks` |
| EN-11 | What grade is recorded if I withdraw from a course? | `grade W` |
| EN-12 | Can I take the midterm if I have not paid tuition? | `end of week 4` |
| TH-01 | ลาพักการศึกษาได้นานที่สุดกี่ภาคการศึกษา | `ไม่เกิน 2 ภาคการศึกษา` |
| TH-02 | ค่าธรรมเนียมรักษาสถานภาพนักศึกษาเท่าไร | `1,000 บาท` |
| X-01 | ต้องเรียนกี่หน่วยกิตจึงจะสำเร็จการศึกษา | `at least 120 credits` |
| X-02 | ค่าปรับลงทะเบียนล่าช้าเท่าไร | `500 baht` |
| X-03 | How long can a student take a leave of absence? | `ไม่เกิน 2 ภาคการศึกษา` |
| X-04 | ဘွဲ့ရရန် ခရက်ဒစ် မည်မျှ လိုအပ်သနည်း | `at least 120 credits` |
| NA-01 | What are the opening hours of the swimming pool? | — |
| NA-02 | How do I apply for a dormitory room? | — |
| NA-03 | What scholarships are available for international students? | — |
| NA-04 | ห้องสมุดเปิดกี่โมง | — |
| NA-05 | How do I reset my university email password? | — |
| NA-06 | What is the capital of France? | — |

The X cases ask in one language about a passage in another. The NA cases have no answer in the sample document. A native speaker should check the Thai and Myanmar questions before they are used.

### 18.2 Dataset test (every test run, no provider)

`KnowledgeRetrievalEvaluationDatasetTests`:

- Case ids are unique. Every `FOUND` case has a `mustContain`; no `NOT_FOUND` case has one.
- Every `mustContain` occurs in exactly one chunk of the processed sample document.
- At least 5 `NOT_FOUND` cases and at least 2 cases per language group (EN, TH, X).

### 18.3 Live evaluation (opt-in)

`KnowledgeLiveRetrievalEvaluationTests` runs only with `KNOWLEDGE_EVAL_ENABLED=true` (the switch of spec file 50, section 15) and a configured embedding model. It is skipped in every normal test run and must never run in CI.

```text
1. Process the sample document with the real embedding client (one request for 6 chunks).
2. For every case: search with topK = 5, minScore = 0 and documentId of the sample document (one request per case).
3. For a FOUND case: position = rank of the first result whose content contains mustContain (none = miss);
   expectedScore = the score of that result.
4. For every case: topScore = the score of rank 1.
5. Write target/knowledge-eval/retrieval-report.json and retrieval-report.md.
```

```bash
KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=KnowledgeLiveRetrievalEvaluationTests
```

The report contains, in total and per language group:

| Metric | Meaning |
|---|---|
| `hit@1`, `hit@3`, `hit@5` | Share of `FOUND` cases whose expected passage is within the first 1, 3, 5 results |
| `MRR` | Mean of `1 / position` over the `FOUND` cases (0 for a miss) |
| `lowestExpectedScore` | The lowest `expectedScore` among the `FOUND` cases that were hit |
| `highestUnansweredScore` | The highest `topScore` among the `NOT_FOUND` cases |

and one line per case with its position and scores. It contains no key.

Provider usage: one request for the document and one per case, 25 requests for the cases above. The test refuses to start when the number of cases is larger than `KNOWLEDGE_EVAL_MAX_REQUESTS` (default 40), and it waits `AI_EMBEDDING_MIN_INTERVAL_MS` between requests.

The test fails only when the run itself breaks (a provider error, no report written). It does not fail on a low metric: the numbers are a measurement, not a build gate. A reasonable expectation for same-language cases is `hit@3` of at least 80 percent. Report what was measured.

### 18.4 Choosing `KNOWLEDGE_SEARCH_MIN_SCORE`

```text
low  = lowestExpectedScore
high = highestUnansweredScore
```

- `high < low`: the two groups are separated. Set the threshold to the middle between them, rounded down to two decimals.
- `high ≥ low`: no threshold separates them. Choose the highest value that still keeps at least 90 percent of the `FOUND` cases, and write both numbers into the decision log. Prefer keeping an answerable question over rejecting an unanswerable one: the assistant has a second check for passages that do not answer the question (spec file 53).

Run the calibration again when the embedding model, the chunk sizes of spec file 49 or the embedding input change.

The sample document is small and partly artificial. Before the knowledge base is used with real students, repeat the evaluation with at least 30 questions on real university documents.

---

## 19. Postman Verification

Use the sample document of spec file 47, section 19. It must be `READY` (spec file 51, section 16). If you replaced the file in spec file 51, step 5, Article 11 says 126 credits instead of 120.

Requests 1 to 3 send `"minScore": 0`, so their result does not depend on the threshold. Requests 2 to 6 and 9 use the endpoint and headers of request 1; only the body changes.

**1. Search in English**

```http
POST http://localhost:8080/api/v1/admin/knowledge/search
Authorization: Bearer <admin-access-token>
Content-Type: application/json
```

```json
{
  "query": "How many credits do I need to graduate?",
  "minScore": 0
}
```

Expected: HTTP 200. `results[0].chunkIndex: 4`, `results[0].sectionTitle: "Chapter 5 Graduation"`, and `results[0].content` contains Article 11. The scores go down from rank 1. While the sample document is the only searchable document there are exactly 3 results. Note the score of rank 1.

**2. Search in Thai**

```json
{
  "query": "ลาพักการศึกษาได้นานที่สุดกี่ภาคการศึกษา",
  "minScore": 0
}
```

Expected: HTTP 200. `results[0].chunkIndex: 5`, `results[0].sectionTitle: "หมวด 6 การลาพักการศึกษา"`.

**3. A question in another language than the passage**

```json
{
  "query": "How long can a student take a leave of absence?",
  "minScore": 0
}
```

Expected: HTTP 200. With a multilingual model such as `gemini-embedding-001` the Thai chunk 5 is normally the first result. Note its score and compare it with request 2: it is usually lower.

**4. The threshold**

```json
{
  "query": "How many credits do I need to graduate?",
  "minScore": 0.99
}
```

Expected: HTTP 200, `"results": []`.

**5. A question the document cannot answer**

```json
{
  "query": "What are the opening hours of the swimming pool?",
  "minScore": 0
}
```

Expected: HTTP 200 with results (the nearest chunks always exist). Note the score of rank 1 and compare it with the score of request 1. The threshold must lie between the two. If they are close together, read section 18.4.

**6. Filters**

```json
{
  "query": "How many credits do I need to graduate?",
  "minScore": 0,
  "documentId": <documentId>
}
```

Expected: 5 results, ranks 1 to 5, all with that `documentId`.

```json
{
  "query": "How many credits do I need to graduate?",
  "minScore": 0,
  "academicYear": "2020-2021"
}
```

Expected: `"results": []`.

**7. Student scope**

Upload the sample file a second time for one program:

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/upload
Authorization: Bearer <admin-access-token>
Content-Type: multipart/form-data
```

Form Data:

```text
title = Sample Program Regulations 2026
categoryId = <an existing category id>
academicYearLabel = 2026-2027
programId = <an existing program id>
file = rag-sample-regulations.txt
```

Wait until its status is `READY`. The backend log shows `KNOWLEDGE_EMBED … reused=6 embedded=0 providerCalls=0`: the text is the same, so no provider request is needed. (If the first document holds the changed file of spec file 51, step 5, it is `reused=5 embedded=1 providerCalls=1`.)

Then search three times with `"query": "How many credits do I need to graduate?", "minScore": 0` and:

| `studentScope` | Expected |
|---|---|
| `{}` | No result has the title "Sample Program Regulations 2026". |
| `{ "programId": <that program id> }` | Results of both documents. |
| `{ "programId": <another program id> }` | No result has the title "Sample Program Regulations 2026". |

**8. Archive and activate**

```http
PATCH http://localhost:8080/api/v1/admin/knowledge/documents/{secondDocumentId}/archive
Authorization: Bearer <admin-access-token>
```

Repeat request 1. Expected: no result of the archived document.

```http
PATCH http://localhost:8080/api/v1/admin/knowledge/documents/{secondDocumentId}/activate
Authorization: Bearer <admin-access-token>
```

Repeat request 1. Expected: its results are back at once. `GET …/{secondDocumentId}/processing` still shows `READY`, and no new job ran.

**9. Validation**

Send each body on its own:

```json
{ "query": "   " }
{ "query": "credits", "topK": 0 }
{ "query": "credits", "topK": 11 }
{ "query": "credits", "minScore": 1.5 }
{ "query": "credits", "academicYear": "2026" }
{ "query": "credits", "unknownField": true }
```

Expected: HTTP 400, `code: "VALIDATION_ERROR"` for every one.

**10. Access**

Send request 1 without a token, then with a Student access token.

Expected: HTTP 401 without a token; HTTP 403 with a Student token.

**11. The query cache**

Send request 1 twice and read the backend log.

Expected: the first line has `cache=MISS` and an `embedMs` above 0; the second has `cache=HIT embedMs=0`. The provider received one request.

---

## 20. Acceptance Criteria

- [ ] `KnowledgeSearchService` exists with `adminSearch` and `studentSearch`, and both run the flow of section 5.
- [ ] A question is prepared with the same normalizer and the same settings as the documents.
- [ ] No database transaction is open while the embedding provider is called.
- [ ] The search statement follows spec file 51, section 8, and the pgvector settings never stay on a pooled connection.
- [ ] Only chunks that satisfy R10 are returned (section 8), checked on every search.
- [ ] A search with a Student scope returns only university-wide documents and documents of that Student's department, program and active curriculum (section 9).
- [ ] The scope of `studentSearch` is an argument built by backend code; no request field or model argument can set it.
- [ ] Threshold, limit per document, `topK` and ranks work as in section 11.
- [ ] The Admin endpoint accepts and returns exactly the fields of section 13, for Admin roles only, and never returns a vector, a distance, a storage key, a checksum or a file name.
- [ ] The three new error codes and their mapping work as in section 14; an empty result is HTTP 200.
- [ ] The query cache works as in section 7, and a Redis failure never fails a search.
- [ ] No Student search endpoint, no keyword search, no caching of results, no migration, no new dependency.
- [ ] The question, passages, vectors and keys are never logged.
- [ ] The tests of section 17 pass; no existing test was changed; no new failure compared with the baseline.
- [ ] The evaluation dataset and its dataset test exist. The live evaluation was run and `KNOWLEDGE_SEARCH_MIN_SCORE` was calibrated, **or** the report says clearly that it was not run and that 0.50 is still the uncalibrated default.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 21. Agent Completion Report

Report: files created and modified; the final search statement; how the transaction boundary was verified; the result of every scope test; the scores of the seven questions of section 17.4 with the deterministic client; whether the live evaluation was run, with its metrics, the two calibration numbers and the chosen threshold; provider requests used for manual checks and for the evaluation; Postman results actually performed; test totals against the baseline; decision entries added to `00-changes-and-decision.md`.

Documents can now be searched by an Admin. Do not claim that the assistant answers from documents: that is spec file 53.

**Next: spec file 53 — Step 15A: Connect Curriculum RAG to the AI Assistant (knowledge search tool).**
