# 51 — Step 14G: RAG Document Processing — Store in Vector Database

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** The vector store on pgvector, the `INDEX` stage that verifies a file and makes it `READY`, removal of outdated vectors, and an index statistics endpoint. This file completes the ingestion pipeline.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector 0.8 or newer
**Prerequisites:** Spec files 45 to 50 are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → 47 extract → 48 clean → 49 chunk → 50 embed → **51 vector store (this file)** → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
Create Embeddings
    ↓
Store in Vector Database     ← this file
    ↓
Semantic Search
```

The vector database of this project is the table `academic_document_chunks` in the existing PostgreSQL database, with the `pgvector` extension and an HNSW index (R1). There is no separate vector database product.

Spec file 50 already writes a vector onto every chunk. A vector database is more than a place where vectors were written once. It must stay **correct**:

1. A file counts as indexed only when **every** chunk has a vector of the current model.
2. When a document gets a new revision, the vectors of the old revision must disappear.
3. When a document is deleted, its vectors must disappear.
4. When the embedding model changes, old vectors must not be mixed with new ones.
5. Queries must use the index in a way that returns the right rows.

After this step:

- the `INDEX` stage checks a file and the worker marks it `READY`;
- the pipeline has all five stages, so uploads are processed automatically;
- outdated pages, chunks and vectors are removed;
- an Admin can see the state of the index.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 45, 46, 49 and 50, and the stage contract of spec file 46, section 6.
2. Inspect `PgVectorKnowledgeVectorStore`, `DocumentIngestionServiceImpl` (`runOnce`, `requestProcessing`), `KnowledgeDocumentServiceImpl` (`replaceFile`, `delete`, `archive`, `activate`) and the processing response.
3. Read section 10 before deploying: completing the pipeline changes what happens to every existing document.
4. Run the tests and the build.

Do not:

- Add a migration. The schema of spec file 45 is sufficient.
- Add a vector database product, a pgvector Java library or `hibernate-vector`.
- Return a vector from any endpoint.
- Add a foreign key cascade. Rows are deleted explicitly, in batches (D53).
- Delete stored file bytes or rows of `academic_document_files` in this step. Only pages and chunks are removed.
- Use `SET` for a pgvector setting. Only `SET LOCAL` inside the transaction of the query (section 8).
- Mark a file `READY` from anywhere except the worker after the `INDEX` stage succeeded.

---

## 3. Package Structure

```text
feature/knowledge/
├── controller/
│   └── AdminKnowledgeIndexController.java          (new: /api/v1/admin/knowledge/index)
├── dto/
│   ├── KnowledgeIndexStatisticsResponse.java       (new)
│   └── KnowledgeDocumentProcessingResponse.java    (+ indexStale)
├── service/
│   ├── KnowledgeVectorStore.java                   (+ countByFile, deleteByFile, removeOutdated, statistics)
│   ├── KnowledgeIndexService.java                  (new: statistics for the controller)
│   └── impl/
│       ├── PgVectorKnowledgeVectorStore.java       (+ the new methods)
│       ├── FinalizeIndexStage.java                 (new, IngestionStage INDEX)
│       ├── KnowledgeIndexServiceImpl.java          (new)
│       └── DocumentIngestionServiceImpl.java       (+ clean-up pass, + stale rule in requestProcessing)
```

---

## 4. Vector Store Contract

`KnowledgeVectorStore` after this file:

```java
public interface KnowledgeVectorStore {
    int reuseEmbeddings(long fileId, String model);                             // spec file 50
    void saveEmbeddings(Map<Long, float[]> vectorsByChunkId, String model);     // spec file 50

    /** Chunk counts of one file. */
    FileIndexCounts countByFile(long fileId, String model);

    /** Deletes at most maxRows chunk rows and at most maxRows page rows of one file. Returns the rows deleted. */
    int deleteByFile(long fileId, int maxRows);

    /** Deletes outdated pages and chunks (section 7), at most maxRows rows. Returns the rows deleted. */
    int removeOutdated(int maxRows);

    /** Numbers for the statistics endpoint (section 11). */
    IndexStatistics statistics(String model);
}

public record FileIndexCounts(long chunks, long embeddedWithModel, int maxChunkIndex, int maxPageEnd, int pageCount) {}
```

Spec file 52 adds the search method.

Rules for the implementation:

- Native SQL only. The entity does not map the vector (spec file 45, section 13).
- Every statement touches at most 500 rows, so it stays far below the datasource limit of 5 seconds (1 second in tests).
- Chunks are deleted before pages.
- The class has no method security. It is called by the worker and by services that check authorization themselves.

---

## 5. The INDEX Stage

`FinalizeIndexStage` implements `IngestionStage` with `type() = INDEX`. It runs while the processing status is `EMBEDDING`. It creates no data. It is the gate between "vectors were written" and "this file may be searched".

```text
1. control.checkpoint()
2. counts = vectorStore.countByFile(fileId, configured model)
3. Verify (section 5.1).
4. Remove the pages and chunks of every other file of the same document (older revisions),
   500 rows at a time, control.checkpoint() between batches.
5. Return. The worker then sets READY (spec file 46, section 10.2).
```

### 5.1 Verification

| Check | Fails with | Retryable |
|---|---|---|
| `chunks > 0` | `INDEX_INCOMPLETE` | yes |
| `embeddedWithModel = chunks` (every chunk has a vector of the configured model) | `INDEX_INCOMPLETE` | yes |
| `maxChunkIndex = chunks − 1` (indexes 0 … n−1 without a gap) | `INDEX_VERIFICATION_FAILED` | no |
| `maxPageEnd ≤ pageCount` (no chunk points past the last page) | `INDEX_VERIFICATION_FAILED` | no |
| the embedding client is configured | `EMBEDDING_NOT_CONFIGURED` | no |

Counts in one statement:

```sql
SELECT COUNT(*)                                                                       AS chunks,
       COUNT(*) FILTER (WHERE embedding IS NOT NULL AND embedding_model = :model)     AS embedded_with_model,
       COALESCE(MAX(chunk_index), -1)                                                 AS max_chunk_index,
       COALESCE(MAX(page_end), 0)                                                     AS max_page_end
FROM academic_document_chunks
WHERE file_id = :fileId;
```

`pageCount` is `MAX(page_number)` of the file's pages (0 when there are none).

`INDEX_INCOMPLETE` is retryable on purpose: the failed stage is `INDEX`, so the next run resumes at `EMBED` (spec file 46, section 10.1), fills the missing vectors and comes back here. `INDEX_VERIFICATION_FAILED` means the chunks themselves are damaged; only a reprocess with mode `FULL` repairs that.

### 5.2 Why old revisions are removed here

When an Admin uploads a new revision, the old revision is no longer current, so search already ignores it (R10). Its chunks are still kept for a while on purpose: the `EMBED` stage of the new revision copies the vectors of unchanged passages from them (spec file 50, section 8). Once the new revision has passed verification, the old data has no further use and step 4 removes it.

While a new revision is being processed, the document is **not searchable**. This is intended: a file is often replaced because the old one was wrong.

---

## 6. Processing Becomes Complete

With this stage all five stage types have a bean. From now on (spec file 46, sections 6 and 8):

- `ingestionAvailable` is `true` in the processing response;
- every current file with status `UPLOADED` whose document is active and not deleted is queued automatically on the next scheduler tick;
- a job that runs without error ends as `READY`.

`READY` is the only status in which a document can be found by search.

---

## 7. Removing Outdated Data

Pages and chunks are outdated when they belong to:

| Case | When they are removed |
|---|---|
| A. an older revision of a document whose current revision is `READY` | by the `INDEX` stage of the new revision (section 5, step 4), and by the clean-up pass as a safety net |
| B. any revision of a deleted document (`is_deleted = TRUE`) | by the clean-up pass |
| C. a file whose job was cancelled because it was replaced or its document was deleted | by the worker at the moment of cancellation (spec file 46, section 12) |

Not outdated:

- Data of an **archived** document. It stays indexed and is only hidden from search, so activating it again costs nothing (R7).
- Data of an older revision while the current revision is not `READY` yet (it is the source for reuse).

### 7.1 Clean-up pass

Add a last step to `DocumentIngestionServiceImpl.runOnce()`, after the job of the tick (if any) has finished:

```text
7. clean up: vectorStore.removeOutdated(1000)
```

It runs when ingestion is enabled, whether or not a job was processed in this tick. Find the files first, then delete by file:

```sql
SELECT f.id
FROM academic_document_files f
JOIN academic_documents d ON d.id = f.document_id
WHERE (
        d.is_deleted
        OR (
            NOT f.is_current
            AND EXISTS (
                SELECT 1
                FROM academic_document_files cf
                JOIN academic_document_processing cp ON cp.file_id = cf.id
                WHERE cf.document_id = f.document_id
                  AND cf.is_current
                  AND cp.status = 'READY'
            )
        )
      )
  AND (
        EXISTS (SELECT 1 FROM academic_document_chunks c WHERE c.file_id = f.id)
        OR EXISTS (SELECT 1 FROM academic_document_pages p WHERE p.file_id = f.id)
      )
ORDER BY f.id
LIMIT 5;
```

For each returned file call `deleteByFile` until the row budget of the pass is used up. A large document is therefore cleaned over several ticks. A failure of the clean-up pass is logged once at `WARN` and never fails the tick.

Deleting a document, replacing a file, archiving and activating keep their current behaviour and response times: `KnowledgeDocumentServiceImpl` is **not** changed for clean-up.

---

## 8. How the Vector Column Must Be Queried

These rules are binding for spec file 52 and for every later query on `embedding`.

1. **Operator.** Cosine distance is `<=>`. The index is built with `vector_cosine_ops`, so only `<=>` can use it. Similarity is `1 − distance`.
2. **Shape.** The index is used only for `ORDER BY embedding <=> CAST(:query AS vector) LIMIT :n`, ascending, with a limit. A query without `LIMIT`, with `DESC`, or with an expression around the distance cannot use it.
3. **Parameter.** The query vector is passed as a text literal and cast: `CAST(:query AS vector)`. It must have exactly 768 numbers and must be L2-normalized like the stored vectors.
4. **NULL vectors.** Always filter `embedding IS NOT NULL`. Chunks without a vector are not in the index.
5. **Model.** Always filter `embedding_model = :model` (R10).
6. **Filters and the HNSW index.** An HNSW scan finds the nearest vectors first and applies the `WHERE` conditions afterwards. With strict filters it can return fewer rows than the limit. pgvector 0.8 solves this with iterative scans. Set inside the transaction of the query:

   ```sql
   SET LOCAL hnsw.iterative_scan = 'relaxed_order';
   SET LOCAL hnsw.ef_search = 100;
   ```

   With `relaxed_order` the rows can arrive slightly out of order. Therefore select the candidates in an inner query and sort them by distance again in the outer query.
7. **`SET LOCAL` only.** Connections come from a pool. A plain `SET` would stay on the connection and change the behaviour of unrelated requests. `SET LOCAL` ends with the transaction. The search therefore always runs inside a (read-only) transaction. `set_config(name, value, true)` is `SET LOCAL` written as a function and is equally allowed; spec file 52 uses it because it accepts a bind parameter.
8. **Small tables.** With a few thousand chunks PostgreSQL may decide to read the table and sort instead of using the index. That is correct and exact. Do not force the index.
9. **No vector in a result.** Select the distance and the chunk columns, never `embedding`.

Index parameters: the index of spec file 45 uses the pgvector defaults (`m = 16`, `ef_construction = 64`). They are appropriate for this corpus (well under one million vectors). Changing them needs a new migration and is not part of Step 14.

---

## 9. Changing the Embedding Model

When `AI_EMBEDDING_MODEL` is changed, every stored vector belongs to another model. Search ignores those vectors (R10), so **all documents stop being searchable until they are embedded again**. Nothing is deleted.

A file is **stale** when its status is `READY` and at least one of its chunks has no vector of the configured model.

- `GET /{id}/processing` gains the field `indexStale` (boolean). `embeddedChunkCount` now counts only vectors of the configured model.
- For a stale file the message is "Indexed with a different embedding model. Reprocess the document to make it searchable again."
- The reprocess matrix of spec file 46, section 14, gets one more row:

| Current status | `RESUME` | `FULL` |
|---|---|---|
| `READY`, not stale | 409 `DOCUMENT_ALREADY_PROCESSED` (unchanged) | → `QUEUED` (unchanged) |
| `READY`, **stale** | → `QUEUED` with `failed_stage = 'EMBED'` | → `QUEUED` (unchanged) |

`failed_stage = 'EMBED'` is the resume hint: the run starts at `EMBED`, embeds every chunk again with the new model (spec file 50, section 9) and passes `INDEX`. Extraction, cleaning and chunking are not repeated.

Stale files are **not** queued automatically: re-embedding the whole knowledge base can use a large part of a provider quota, so an Admin starts it per document.

---

## 10. Before Deploying This Step

Completing the pipeline turns automatic processing on. On the first scheduler tick after the deployment, **every** document that was uploaded since spec file 39 and still has status `UPLOADED` is queued and processed, one after the other, and each one uses embedding quota.

Before the first start:

1. Count the waiting documents:

   ```sql
   SELECT COUNT(*)
   FROM academic_document_processing p
   JOIN academic_document_files f ON f.id = p.file_id
   JOIN academic_documents d ON d.id = f.document_id
   WHERE p.status = 'UPLOADED' AND f.is_current AND d.is_active AND NOT d.is_deleted;
   ```

2. If the provider quota is small, start with `KNOWLEDGE_INGESTION_AUTO_QUEUE=false` and process documents one by one with the reprocess endpoint. Turn automatic queueing on afterwards.
3. Make sure `AI_EMBEDDING_MODEL` is set. Without it every job fails at `EMBED` with `EMBEDDING_NOT_CONFIGURED` (not retried).

Old test documents that should not be indexed can be archived first: archived documents are not queued automatically.

---

## 11. Index Statistics Endpoint

```http
GET /api/v1/admin/knowledge/index/statistics
Authorization: Bearer <admin-access-token>
```

Access: SUPER_ADMIN, ADMIN, ACADEMIC_ADMIN (the existing rule for `/api/v1/admin/knowledge/**`; `KnowledgeIndexServiceImpl` carries the same class-level `@PreAuthorize` as the other knowledge services). `Cache-Control: no-store`.

```json
{
  "ingestionAvailable": true,
  "embeddingConfigured": true,
  "embeddingModel": "gemini-embedding-001",
  "dimensions": 768,
  "readyDocuments": 12,
  "staleDocuments": 0,
  "searchableDocuments": 11,
  "totalChunks": 1840,
  "searchableChunks": 1795,
  "chunksOfOtherModels": 0,
  "outdatedChunks": 0,
  "totalPages": 512,
  "chunkTableBytes": 14237696,
  "vectorIndexBytes": 9461760
}
```

Values are illustrative.

| Field | Meaning |
|---|---|
| `ingestionAvailable` | Ingestion is enabled and the pipeline is complete. |
| `embeddingConfigured`, `embeddingModel`, `dimensions` | The embedding settings in effect. The key is never returned. |
| `readyDocuments` | Non-deleted documents whose current file is `READY`. |
| `staleDocuments` | Of those, the ones that are stale (section 9). |
| `searchableDocuments` | Documents that search can return right now: active, not deleted, current file `READY` and not stale, and a linked curriculum version that is `ACTIVE` (R10). |
| `totalChunks` | All rows of `academic_document_chunks`. |
| `searchableChunks` | Chunks of the searchable documents. |
| `chunksOfOtherModels` | Chunks with a vector of a model other than the configured one. |
| `outdatedChunks` | Chunks waiting for the clean-up pass (section 7). Normally 0. |
| `totalPages` | All rows of `academic_document_pages`. |
| `chunkTableBytes` | `pg_total_relation_size('academic_document_chunks')`. |
| `vectorIndexBytes` | `pg_relation_size('idx_academic_document_chunks_embedding_hnsw')`. |

The numbers are counted when the endpoint is called. They are not stored and not cached. The existing dashboard endpoint `GET /api/v1/admin/knowledge/documents/statistics` is not changed.

---

## 12. Error Codes

Processing codes of this stage (`failed_stage = INDEX`):

| Code | Retryable | Safe message |
|---|---|---|
| `INDEX_INCOMPLETE` | yes | "Some chunks have no embedding yet." |
| `INDEX_VERIFICATION_FAILED` | no | "The stored chunks are inconsistent. Reprocess the document with mode FULL." |
| `EMBEDDING_NOT_CONFIGURED` | no | "The embedding model is not configured." |

A database error while old revisions are removed is the generic `INGESTION_DATABASE_ERROR` of spec file 46 (retryable).

No new HTTP error code.

---

## 13. Capacity and Operations

- One vector takes about 3 KB (768 numbers × 4 bytes) plus its share of the index. Ten thousand chunks need roughly 30 MB for vectors and a similar amount for the index.
- The vectors are part of the normal PostgreSQL backup (`pg_dump`). The database a dump is restored into needs the `pgvector` extension.
- Deleted vectors leave dead entries in the HNSW index until autovacuum has run. No manual maintenance is needed at this size.
- More than one backend instance may run the worker. `SKIP LOCKED` (spec file 46) keeps them apart; the clean-up pass is safe to run twice because it only deletes rows that are outdated.

---

## 14. Logging

```text
KNOWLEDGE_INDEX documentId=31 fileId=81 chunks=118 model=gemini-embedding-001 verified=true removedOldChunks=112 removedOldPages=40 durationMs=64
KNOWLEDGE_INDEX documentId=31 fileId=81 failed code=INDEX_INCOMPLETE chunks=118 embedded=96
KNOWLEDGE_CLEANUP files=2 chunks=214 pages=77 durationMs=38
```

The clean-up line is written only when something was deleted.

---

## 15. Tests

Unless stated otherwise, tests use `DeterministicEmbeddingClient` (spec file 50, section 13) and call `runOnce()` themselves.

### 15.1 Vector store (Testcontainers)

- `countByFile` returns chunk count, vectors of the given model, highest index, highest page and page count; a vector of another model is not counted.
- `deleteByFile` removes chunks and pages of exactly one file, respects `maxRows`, and reaches zero when called repeatedly.
- `removeOutdated` removes the data of a deleted document and of an older revision whose current revision is `READY`.
- `removeOutdated` keeps: an older revision whose current revision is not `READY`; an archived document; the current revision of every document.
- `statistics` matches a known set of documents (ready, stale, archived, deleted, linked to an archived curriculum version).

### 15.2 `FinalizeIndexStage` (worker built by hand with all five real stages)

- A complete file passes and the worker sets `READY`, with `completed_at` set and `failed_stage`, error code and `next_attempt_at` empty.
- One vector set to NULL by SQL before the stage: `FAILED`, `failed_stage = INDEX`, `INDEX_INCOMPLETE`, retryable. The next run resumes at `EMBED`, fills the gap and ends `READY`. The provider is called only for the missing chunk.
- A chunk row deleted from the middle: `INDEX_VERIFICATION_FAILED`, no retry.
- A file without chunks: `INDEX_INCOMPLETE`.
- New revision: after it is `READY`, the pages and chunks of the old revision are gone and the new ones are complete. With one changed paragraph the provider was called only for the changed chunks (reuse happened before the old data was removed).
- A new revision that fails (for example `DOCUMENT_TEXT_EMPTY`) leaves the old revision's data in place.

### 15.3 End to end through the Spring context (`KnowledgeIngestionEndToEndTests`)

Replace only the `EmbeddingClient` bean with `DeterministicEmbeddingClient`. Everything else is the real application.

- Upload a generated PDF through the upload endpoint → response `UPLOADED` → `runOnce()` → the processing endpoint shows `READY`, `ingestionAvailable: true`, counts filled, `indexStale: false`.
- The same for DOCX, XLSX and TXT.
- An archived document is not queued automatically; after activation it is.
- With `auto-queue=false` nothing is queued until reprocess is called.
- Delete a `READY` document → `runOnce()` → no pages and no chunks remain for its files; the file rows and the stored bytes are unchanged.
- Reprocess of a `READY` document: `RESUME` → 409 `DOCUMENT_ALREADY_PROCESSED`; `FULL` → `READY` again.
- Model change: process a document, switch the client's model id, the processing endpoint shows `indexStale: true`; reprocess `RESUME` → 202; `runOnce()` → `READY`, not stale, the chunk row ids are unchanged, every vector has the new model.
- Statistics endpoint: values for the documents above; no token 401; Student token 403; each Admin role 200.

### 15.4 Existing tests

The Spring context now contains a complete pipeline. Any existing test that expected `ingestionAvailable: false` or a 503 from reprocess without disabling ingestion must already have been rewritten in spec file 46 (section 20.3). Verify that no such assertion is left, and list what was changed.

---

## 16. Postman Verification

Read section 10 first. Use the sample document of spec file 47, section 19.

**1. Finish the sample document**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/reprocess
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 202, `status: "QUEUED"`.

**2. Processing status (after about 10 seconds)**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/processing
Authorization: Bearer <admin-access-token>
```

Expected: `status: "READY"`, `ingestionAvailable: true`, `failedStage: null`, `lastErrorCode: null`, `pageCount: 1`, `chunkCount: 6`, `embeddedChunkCount: 6`, `indexStale: false`, `message: "Indexed and ready for AI retrieval."`

**3. Index statistics**

```http
GET http://localhost:8080/api/v1/admin/knowledge/index/statistics
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 200, `embeddingConfigured: true`, `readyDocuments` and `searchableDocuments` at least 1, `searchableChunks` at least 6, `outdatedChunks: 0`.

**4. Automatic processing of a new upload**

Upload any small TXT file with `POST /api/v1/admin/knowledge/documents/upload` (as in spec file 47, section 21). Do **not** call reprocess.

Expected: the upload answers `processingStatus: "UPLOADED"`. Within about 15 seconds the processing endpoint shows `QUEUED`, then `PROCESSING`, `CHUNKING`, `EMBEDDING` and finally `READY`.

**5. Replace the file**

Save a copy of the sample document in which Article 11 says "at least 126 credits" and upload it as a new revision:

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/files
Authorization: Bearer <admin-access-token>
Content-Type: multipart/form-data
```

Form Data:

```text
file = rag-sample-regulations-v2.txt
```

Expected: HTTP 201, `revisionNumber: 2`. After about 15 seconds the processing endpoint shows `READY` for revision 2, `GET /chunks/4` contains "126 credits", and the backend log shows `KNOWLEDGE_EMBED … reused=5 embedded=1 providerCalls=1`.

**6. The old revision is gone from the vector database**

```sql
SELECT f.revision_number, f.is_current, COUNT(c.id) AS chunks
FROM academic_document_files f
LEFT JOIN academic_document_chunks c ON c.file_id = f.id
WHERE f.document_id = <documentId>
GROUP BY f.revision_number, f.is_current
ORDER BY f.revision_number;
```

Expected: revision 1 has 0 chunks, revision 2 has 6.

**7. Reprocess a READY document**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/reprocess
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 409, `code: "DOCUMENT_ALREADY_PROCESSED"`.

**8. Delete a document**

Delete the document of request 4 (`DELETE /api/v1/admin/knowledge/documents/{id}`), wait about 10 seconds and call the statistics endpoint.

Expected: HTTP 204 for the delete; `totalChunks` has dropped by that document's chunks and `outdatedChunks` is 0.

**9. Inspect the index**

```sql
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'academic_document_chunks';
SELECT COUNT(*) FILTER (WHERE embedding IS NOT NULL) AS with_vector, COUNT(*) AS total FROM academic_document_chunks;
```

Expected: the HNSW index of spec file 45 is listed; for processed documents `with_vector` equals `total`.

---

## 17. Acceptance Criteria

- [ ] `FinalizeIndexStage` is the only bean for `INDEX` and follows the stage contract of spec file 46.
- [ ] A file becomes `READY` only when every chunk has a vector of the configured model and the chunk set is consistent.
- [ ] `INDEX_INCOMPLETE` is retried and resumes at `EMBED`; `INDEX_VERIFICATION_FAILED` is not retried.
- [ ] The pipeline is complete: `ingestionAvailable` is `true` and uploads are processed automatically.
- [ ] Old revisions are removed after the new revision is verified, never before.
- [ ] Data of deleted documents is removed by the clean-up pass; data of archived documents is kept.
- [ ] No cascade, no deletion of file rows or stored bytes, all deletes in batches of at most 500 rows.
- [ ] `KnowledgeDocumentServiceImpl` delete, replace, archive and activate behave and answer as before.
- [ ] Stale files are reported (`indexStale`) and can be re-embedded with reprocess `RESUME` without repeating extraction, cleaning and chunking.
- [ ] The statistics endpoint returns the fields of section 11 for Admin roles only and never a key or a vector.
- [ ] The query rules of section 8 are documented in the code of the vector store (class comment).
- [ ] No migration added; no new dependency.
- [ ] Tests of section 15 pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 18. Agent Completion Report

Report: files created and modified; the verification queries; how many existing documents were waiting before deployment and how they were handled (section 10); the end-to-end result for each file type; how removal of old revisions and deleted documents was tested; the statistics output on the development database; provider requests used during manual checks; changed existing tests; test totals against the baseline; Postman results actually performed; decision entries added to `00-changes-and-decision.md`.

Documents can now reach `READY`. Do not claim that the assistant can answer from them: search is spec file 52 and the assistant tool is spec file 53.

**Next: spec file 52 — Step 14H: Semantic Search.**
