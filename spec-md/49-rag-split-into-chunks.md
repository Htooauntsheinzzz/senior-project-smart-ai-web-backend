# 49 — Step 14E: RAG Document Processing — Split into Chunks

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** The `CHUNK` stage: split the cleaned text of a file into retrievable passages and store them. Two read-only Admin endpoints to inspect the chunks.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector
**Prerequisites:** Spec files 45, 46, 47 and 48 are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → 47 extract → 48 clean → **49 chunk (this file)** → 50 embed → 51 vector store → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
Clean Text
    ↓
Split into Chunks     ← this file
    ↓
Create Embeddings
```

An embedding model turns one piece of text into one vector. A whole handbook in one vector is useless for search, and a single sentence has too little context. A **chunk** is a passage of about one thousand characters that makes sense on its own.

After this step the `CHUNK` stage reads the cleaned pages of a file and writes rows into `academic_document_chunks` with:

- the chunk text,
- the heading it belongs to,
- the pages it comes from,
- a hash of the text that will be embedded.

The `embedding` column stays NULL until spec file 50.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 45 to 48, and the stage contract of spec file 46, section 6.
2. Inspect `KnowledgeTextPatterns` (line classes, `isHeading`, `startsBlock`), `CleanTextStage` and the format of cleaned text (spec file 48, section 12).
3. Read this whole file before writing code. Section 15 contains a worked example that the implementation must reproduce.
4. Run the tests and the build.

Do not:

- Use an AI model to split text ("semantic chunking" with a model is out of scope, R8).
- Count size in words or in whitespace-separated tokens. Thai and Myanmar have no spaces between words (R9).
- Cut a chunk inside a grapheme cluster (a base letter and its vowel or tone marks), or inside a surrogate pair.
- Add LangChain4j, Spring AI or another library for text splitting. The JDK is enough.
- Map the `embedding` column on the entity (spec file 45, section 13).
- Write embeddings or call a provider. That is spec file 50.
- Log or put into an error message any chunk text or heading.

---

## 3. Package Structure

```text
feature/knowledge/
├── controller/
│   └── AdminKnowledgeDocumentContentController.java   (+ chunks endpoints)
├── dto/
│   ├── DocumentChunkListQuery.java                    (new)
│   ├── DocumentChunkListResponse.java                 (new)
│   ├── DocumentChunkSummaryResponse.java              (new)
│   └── DocumentChunkResponse.java                     (new)
├── entity/
│   └── AcademicDocumentChunk.java                     (new)
├── exception/
│   └── KnowledgeFailure.java                          (+ chunkNotFound)
├── repository/
│   └── AcademicDocumentChunkRepository.java           (new)
└── service/
    ├── KnowledgeTextChunker.java                      (new: the algorithm, pure function)
    ├── ChunkDraft.java                                (new, record: one chunk before it is stored)
    ├── KnowledgeDocumentContentService.java           (+ chunks, chunk)
    └── impl/
        ├── SplitIntoChunksStage.java                  (new, IngestionStage CHUNK)
        └── KnowledgeDocumentContentServiceImpl.java   (+ chunks, chunk)

config/
└── KnowledgeChunkingProperties.java                   (new, app.knowledge.chunking)
```

---

## 4. Configuration

```properties
# Step 14E: splitting cleaned knowledge document text into chunks. Sizes are in characters.
app.knowledge.chunking.target-chars=${KNOWLEDGE_CHUNK_TARGET_CHARS:1000}
app.knowledge.chunking.max-chars=${KNOWLEDGE_CHUNK_MAX_CHARS:1500}
app.knowledge.chunking.min-chars=${KNOWLEDGE_CHUNK_MIN_CHARS:200}
app.knowledge.chunking.overlap-chars=${KNOWLEDGE_CHUNK_OVERLAP_CHARS:150}
app.knowledge.chunking.max-chunks=${KNOWLEDGE_CHUNK_MAX_CHUNKS:5000}
```

| Property | Meaning | Validation |
|---|---|---|
| `target-chars` | The size a chunk is filled up to. | 200 to 4,000 |
| `max-chars` | No chunk is ever longer. | `target-chars` to 8,000 |
| `min-chars` | A chunk with less body text than this is not closed at a heading. | 0 to `target-chars / 2` |
| `overlap-chars` | How much of the end of a chunk is repeated at the start of the next one. 0 turns overlap off. | 0 to `target-chars / 4` |
| `max-chunks` | Maximum number of chunks of one file. | 1 to 50,000 |

Additional rule checked at startup: `min-chars + target-chars + overlap-chars ≤ max-chars`. It lets the algorithm add a full piece to a chunk that is still below `min-chars` without exceeding `max-chars`.

Why these defaults: 1,000 characters are roughly 250 tokens of English or 500 to 700 tokens of Thai. Even the maximum of 1,500 Thai characters plus a heading stays below the 2,048-token input limit of the default embedding model (spec file 50). Four or five chunks fit comfortably into one chat prompt (spec file 53).

After changing a value, existing documents keep their old chunks until they are reprocessed with mode `FULL`.

Add the variables to `.env.example` and to the `backend` service in `compose.yaml`.

---

## 5. Stage Flow

`SplitIntoChunksStage` implements `IngestionStage` with `type() = CHUNK`. It runs while the processing status is `CHUNKING`.

```text
1. control.checkpoint()
2. Delete this file's rows from academic_document_chunks (batches of at most 500).
3. Read the pages in order, 50 at a time (page_number, unit_type, unit_label, cleaned_text).
   Feed their lines to the chunker. The chunker emits finished chunks one by one.
4. Drop a chunk whose content_hash was already emitted for this file.
5. Give the remaining chunks the indexes 0, 1, 2 … in the order they were emitted.
6. Insert them in batches (at most 100 chunks or 200,000 characters per transaction). control.checkpoint() between batches.
7. Fail with DOCUMENT_TOO_MANY_CHUNKS as soon as chunk number max-chunks + 1 would be emitted.
8. Fail with DOCUMENT_TEXT_EMPTY when no chunk was produced.
```

- The chunker keeps only the current chunk and the hashes seen so far in memory, never the whole document.
- A page whose `cleaned_text` is NULL means the `CLEAN` stage did not run: fail with `DOCUMENT_CHUNKING_FAILED`.
- The stage is repeatable (step 2) and deterministic: the same cleaned text and the same configuration give the same chunks with the same hashes. Spec file 50 relies on this to reuse vectors.

---

## 6. Input: Lines, Units and Boundaries

The chunker receives the cleaned text as a stream of lines. Every line knows its `page_number`, `unit_type`, `unit_label` and its class from `KnowledgeTextPatterns.classify`.

| Situation | Meaning for the chunker |
|---|---|
| Next line on the same page | continue |
| Blank line | ignored (only the lines matter) |
| Next PDF page | continue; a paragraph may run over the page break |
| Next unit with the same type and label | continue; it is the same section or sheet (spec file 47, section 7) |
| Next DOCX section (a different label) | continue; the section starts with its heading line, which is handled by the heading rule |
| Next sheet (a different label) | **always** close the current chunk |

---

## 7. Section Titles

The chunker tracks two titles while it reads:

- **chapter title**: the text of the last level-1 heading, which is a `MARKER_HEADING` with one `#` or a `KEYWORD_HEADING`;
- **sub title**: the text of the last deeper heading, which is a `MARKER_HEADING` with two or more `#` or a `NUMBERED_HEADING`. A new chapter title clears the sub title.

The **section title** is:

| Chapter title | Sub title | Section title |
|---|---|---|
| set | set | `<chapter title> › <sub title>` |
| set | none | `<chapter title>` |
| none | set | `<sub title>` |
| none | none | NULL |

For a `SHEET` unit the section title is the sheet name (`unit_label`).

Rules:

- The `#` marker and the space after it are removed from the heading text.
- The label of a sheet is normalized with `KnowledgeTextNormalizer` and trimmed before it is used.
- A section title longer than 500 characters is cut to 500 (never inside a grapheme cluster).
- `section_title` of a chunk is the section title **in effect when the chunk is closed**.
- A `CLAUSE` line ("ข้อ 13 …", "Article 5. …") never changes a title. It is body text.

---

## 8. Pieces

A **piece** is the smallest unit the chunker moves around. Every piece is at most `target-chars` long.

- A line that is not longer than `target-chars` is one piece.
- A heading line is always one piece (headings are at most 150 characters).
- A longer line is split into pieces, trying these boundaries in order:

| Order | Boundary | How |
|---|---|---|
| 1 | Sentence | `BreakIterator.getSentenceInstance(Locale.ROOT)`. Consecutive sentences are packed into one piece while it stays within `target-chars`. |
| 2 | Space | A sentence that is still too long is cut after the last space that keeps the piece within `target-chars`. Thai and Myanmar sentences are usually separated by spaces, so this is the normal boundary for them. |
| 3 | Word | A run without any space is cut at the last word boundary of `BreakIterator.getWordInstance(Locale.forLanguageTag("th"))` that keeps the piece within `target-chars`. |
| 4 | Grapheme cluster | Last resort: the last boundary of `BreakIterator.getCharacterInstance(Locale.ROOT)` within `target-chars`. |

Rules:

- Every cut position must be a boundary of `BreakIterator.getCharacterInstance`. Check this for boundaries 1 to 3 as well and move the cut back to the previous grapheme boundary if needed.
- A piece is a substring of its line with leading and trailing spaces removed. Nothing is rewritten.
- Pieces of a split `TABLE_ROW` are handled like any other pieces.
- Boundary 4 can separate a Myanmar consonant stack. It is reached only for a run of more than `target-chars` characters without a single space, which normal documents do not contain. Report it if a real document triggers it.

---

## 9. Packing Pieces into Chunks

The current chunk has a **length** (all its text, pieces joined with `\n`) and a **body length** (the sum of the lengths of its pieces that are neither heading lines nor overlap).

For every piece, in order:

```text
1. The piece is a heading:
      if body length >= min-chars: close the current chunk (reason: HEADING)
      update the chapter title or the sub title (section 7)
      append the heading to the current chunk
      next piece

2. The sheet changed (section 6): close the current chunk (reason: SHEET)

3. length + 1 + piece length > target-chars  and  body length >= min-chars:
      close the current chunk (reason: SIZE)

4. length + 1 + piece length > max-chars:
      close the current chunk (reason: SIZE)        (rare: only when the chunk holds many heading lines)

5. append the piece to the current chunk
```

At the end of the input the current chunk is closed (reason: END).

**Closing a chunk**

- A chunk without any letter or digit is discarded.
- A chunk that consists only of heading lines is not stored on its own: it is appended to the previous chunk when the result stays within `max-chars`; otherwise it is stored as it is. (This can only happen at the end of the document or of a sheet.)
- A closed chunk that has less body text than `min-chars`, was closed for reason END or SHEET, and contains no heading line is appended to the previous chunk when both come from the same sheet or from no sheet and the result stays within `max-chars`.
- Otherwise the chunk is emitted with the section title in effect at this moment.

**Starting the next chunk after a SIZE close** (not after HEADING, SHEET or END):

- If the closed chunk ended inside a table (its last piece is a `TABLE_ROW`) and the next piece is a `TABLE_ROW` too, the new chunk starts with the **header row** of that table: the first row of the run of consecutive `TABLE_ROW` lines. For a sheet this is the first row of the sheet. No other overlap is added.
- Otherwise the new chunk starts with the **overlap** (section 10), unless the next piece is a heading. Before a heading the overlap is dropped, so text of one section never leaks into the next.

---

## 10. Overlap

The overlap is a copy of the end of the previous chunk. It keeps a sentence that sits on a chunk border findable from both sides.

Look at the last `overlap-chars` characters of the previous chunk (the window). Choose the start of the overlap inside the window:

1. the earliest start of a line; if there is none,
2. the earliest start of a sentence (`BreakIterator` sentence instance); if there is none,
3. the position after the earliest space; if there is none,
4. the earliest word boundary (`BreakIterator` word instance, Thai locale); if there is none,
5. no overlap.

The start must be a grapheme boundary. The overlap is the text from that start to the end of the previous chunk. Heading lines are never copied: if the chosen text begins with a heading line, start after it.

The overlap counts towards the length of the new chunk but not towards its body length. Its page number is the page of the text it was copied from.

With `overlap-chars = 0` no overlap is produced.

---

## 11. Stored Fields

| Column | Value |
|---|---|
| `chunk_index` | 0, 1, 2 … in reading order, without gaps |
| `content` | The pieces joined with `\n`. Heading lines appear without their `#` marker. |
| `section_title` | Section 7 |
| `page_start`, `page_end` | The smallest and largest `page_number` of the text in the chunk, overlap and repeated header row included |
| `char_count` | `content.length()` |
| `content_hash` | SHA-256, lower-case hexadecimal, of the UTF-8 bytes of the **embedding input** |
| `token_estimate` | Estimated tokens of the embedding input |
| `embedding`, `embedding_model`, `embedded_at` | NULL |
| `created_at` | UTC from the injected `Clock` |

**Embedding input.** This is the exact text spec file 50 sends to the embedding model:

```text
section_title is NULL:      content
section_title is not NULL:  section_title + "\n\n" + content
```

Implement it once, as one static method (for example `KnowledgeTextChunker.embeddingInput(sectionTitle, content)`), and use that method for the hash here and for the provider call in spec file 50. The document title is deliberately not part of it: an Admin can rename a document without making its vectors stale.

**Token estimate.** `max(1, ceil(asciiChars / 4.0 + otherChars / 1.5))` over the embedding input. It is an estimate for limits and statistics, not an exact count.

**Duplicates.** Two chunks of the same file with the same `content_hash` are the same passage (for example a notice printed on every page). Only the first is stored.

---

## 12. Persistence

```java
@Entity @Table(name = "academic_document_chunks") @Getter @Setter
public class AcademicDocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false) private AcademicDocumentFile file;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "section_title", length = 500) private String sectionTitle;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Column(name = "char_count", nullable = false) private int charCount;
    @Column(name = "token_estimate", nullable = false) private int tokenEstimate;
    @Column(name = "page_start", nullable = false) private int pageStart;
    @Column(name = "page_end", nullable = false) private int pageEnd;
    @Column(name = "embedding_model", length = 100, insertable = false, updatable = false) private String embeddingModel;
    @Column(name = "embedded_at", insertable = false, updatable = false) private LocalDateTime embeddedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    // The embedding column is not mapped on purpose. It is written by native SQL only (spec file 51).
}
```

`embedding_model` and `embedded_at` are read-only here because the database requires them to change together with the vector (CHECK constraint of spec file 45).

Repository needs: delete one batch of a file's chunks; count the chunks of a file; a paged projection for the list endpoint (without `content`, with `embedding IS NOT NULL AS embedded` and `LEFT(content, 200)` as preview); find one chunk by file and index.

---

## 13. Error Codes

Processing codes of this stage (`failed_stage = CHUNK`):

| Code | Retryable | Safe message |
|---|---|---|
| `DOCUMENT_TOO_MANY_CHUNKS` | no | "The document produces more chunks than the configured limit." |
| `DOCUMENT_TEXT_EMPTY` | no | "No usable text remained for chunking." |
| `DOCUMENT_CHUNKING_FAILED` | no | "The document text could not be split into chunks." |

New HTTP error (`KnowledgeFailure.chunkNotFound()`):

| Code | HTTP | Message |
|---|---:|---|
| `DOCUMENT_CHUNK_NOT_FOUND` | 404 | "The requested document chunk does not exist." |

---

## 14. Admin Inspection Endpoints

Read-only, same access rule, service class and controller as the page endpoints of spec file 47 (section 15). Both describe the **current** revision and answer with `Cache-Control: no-store`. The vector itself is never returned by any endpoint.

### 14.1 List chunks

```http
GET /api/v1/admin/knowledge/documents/{id}/chunks?page=0&size=20
Authorization: Bearer <admin-access-token>
```

`page` default 0, `size` default 20, maximum 100, strict parameter parsing, fixed order by `chunkIndex`.

```json
{
  "documentId": 31,
  "fileId": 81,
  "revisionNumber": 1,
  "content": [
    {
      "chunkIndex": 4,
      "sectionTitle": "Chapter 5 Graduation",
      "pageStart": 1,
      "pageEnd": 1,
      "charCount": 273,
      "tokenEstimate": 74,
      "embedded": false,
      "preview": "Chapter 5 Graduation\nArticle 11. To graduate, a student must complete at least 120 credits required by the curriculum …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 6,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

(The example shows one of the six items.)

### 14.2 One chunk

```http
GET /api/v1/admin/knowledge/documents/{id}/chunks/{chunkIndex}
Authorization: Bearer <admin-access-token>
```

```json
{
  "documentId": 31,
  "fileId": 81,
  "revisionNumber": 1,
  "chunkIndex": 4,
  "sectionTitle": "Chapter 5 Graduation",
  "pageStart": 1,
  "pageEnd": 1,
  "charCount": 273,
  "tokenEstimate": 74,
  "contentHash": "<64 hexadecimal characters>",
  "embedded": false,
  "embeddingModel": null,
  "embeddedAt": null,
  "content": "Chapter 5 Graduation\nArticle 11. To graduate, a student must complete at least 120 credits required by the curriculum and have a cumulative grade point average of at least 2.00.\nArticle 12. A student must apply for graduation within the first 30 days of the final semester."
}
```

Errors: 401 / 403; 400 `VALIDATION_ERROR` for a negative or non-numeric `chunkIndex` or invalid paging; 404 `DOCUMENT_NOT_FOUND`; 404 `DOCUMENT_FILE_NOT_FOUND`; 404 `DOCUMENT_CHUNK_NOT_FOUND`. `chunkIndex` 0 is valid.

---

## 15. Worked Example

The sample document of spec file 47, section 19, with the default configuration, must give exactly **six** chunks:

| `chunkIndex` | `sectionTitle` | `charCount` | Starts with |
|---:|---|---:|---|
| 0 | `Chapter 1 General Provisions` | 425 | `SAMPLE DATA FOR DEVELOPMENT ONLY` |
| 1 | `Chapter 2 Registration` | 631 | `Chapter 2 Registration` |
| 2 | `Chapter 3 Tuition and Fees` | 306 | `Chapter 3 Tuition and Fees` |
| 3 | `Chapter 4 Examinations` | 315 | `Chapter 4 Examinations` |
| 4 | `Chapter 5 Graduation` | 273 | `Chapter 5 Graduation` |
| 5 | `หมวด 6 การลาพักการศึกษา` | 208 | `หมวด 6 การลาพักการศึกษา` |

Why:

- The two title lines at the top have only 154 characters of body text (70 + 84), less than `min-chars`, so the heading "Chapter 1 General Provisions" does not close a chunk. Chunk 0 therefore holds the title lines and Chapter 1, and its section title is the one in effect when it is closed.
- Every later chapter heading finds at least 200 characters of body text and closes the chunk before it.
- "Article …" and "ข้อ …" lines are clauses, not headings.
- No chunk reaches `target-chars`, so there is no overlap in this example.
- The last chunk has less than 200 characters of body text but contains a heading, so it is kept on its own.

Turn this table into a test. If the implementation gives other numbers, the implementation or this file is wrong: find out which before going on.

---

## 16. Effect on Existing Endpoints

- `GET /{id}/processing` shows the real `chunkCount`; `embeddedChunkCount` is 0.
- Until spec file 50 exists, every job ends as `FAILED` with `failedStage: "EMBED"` and `INGESTION_STAGE_NOT_AVAILABLE`.
- Note for manual checks: after such a failure a reprocess with the default mode `RESUME` starts again at `EMBED` (spec file 46, section 10.1) and does **not** chunk again. Use `{ "mode": "FULL" }` to run extraction, cleaning and chunking again.

---

## 17. Logging

```text
KNOWLEDGE_CHUNK documentId=31 fileId=81 pages=42 chunks=118 duplicatesDropped=3 avgChars=932 maxChars=1498 splitLines=7 durationMs=95
```

Counts only. Never chunk text or headings.

---

## 18. Tests

### 18.1 `KnowledgeTextChunker` (unit tests, no Spring context)

Sizes:

- No chunk is longer than `max-chars`, for English, Thai, Myanmar and mixed input, including a 20,000-character paragraph without line breaks.
- Chunks closed for SIZE have at least `min-chars` of body text.
- Configuration that breaks `min + target + overlap ≤ max` is rejected.

Boundaries:

- A long English paragraph is split at sentence ends.
- A long Thai paragraph without full stops is split at spaces.
- A Thai run of 3,000 characters without any space is split at word boundaries; a run with no usable word boundary is split at grapheme boundaries.
- For every chunk border the cut position in the original text is a `BreakIterator.getCharacterInstance` boundary (no split between a Thai consonant and its vowel or tone mark, none inside a surrogate pair).
- Lines are never merged into one line; `content` keeps one piece per line.

Headings and titles:

- A heading closes the chunk before it when that chunk has enough body text, and does not when it has too little.
- Chapter and sub title: `Chapter 2 Registration` followed by `2.1 Add and Drop` gives `Chapter 2 Registration › 2.1 Add and Drop`; the next chapter clears the sub title.
- `# ` markers are removed from `content` and from titles.
- Clause lines do not change the title.
- A document without headings has NULL titles.
- A 600-character title is cut to 500.

Overlap:

- After a SIZE close the next chunk starts with the end of the previous chunk, at most `overlap-chars` long, starting at a line start when one exists in the window, else at a sentence start, else after a space.
- No overlap after a HEADING close; no overlap when the next piece is a heading; no overlap with `overlap-chars = 0`.
- Overlap text is not counted as body length.

Tables and sheets:

- A long table is split between rows and every following chunk starts with the header row.
- Two sheets never share a chunk; `section_title` is the sheet name; the header row of each sheet is repeated in its later chunks.

Pages:

- A chunk that takes text from pages 3 and 4 has `page_start = 3` and `page_end = 4`.
- Continuation units (same type and label) are treated as one text.

Fields:

- `content_hash` equals SHA-256 of the embedding input (computed independently in the test), with and without a title.
- Token estimate: `abcd` → 1; 1,500 Thai characters → 1,000.
- The same input chunked twice gives identical chunks.
- Duplicate chunks are dropped and the indexes stay without gaps.
- More chunks than `max-chunks` → `DOCUMENT_TOO_MANY_CHUNKS`.
- The worked example of section 15.

### 18.2 Stage tests (Testcontainers)

Build `DocumentIngestionServiceImpl` by hand with the real `ExtractTextStage`, `CleanTextStage` and `SplitIntoChunksStage` only.

- After `runOnce()` the chunk rows exist with `embedding`, `embedding_model` and `embedded_at` NULL; the job is `FAILED` at `EMBED` with `INGESTION_STAGE_NOT_AVAILABLE`.
- Reprocess `FULL` replaces the chunks (same content, no duplicates).
- Reprocess `RESUME` after the failure at `EMBED` leaves the chunk rows untouched (same ids).
- Pages without cleaned text → `DOCUMENT_CHUNKING_FAILED`.
- Saving a chunk entity never writes `embedding_model` or `embedded_at`.

### 18.3 Endpoint tests

- List and detail return the stored values; the list has no `content`; no response contains a vector.
- Paging and validation as for the page endpoints; `chunkIndex` 0 works; a missing index is 404 `DOCUMENT_CHUNK_NOT_FOUND`.
- No token 401; Student token 403; each Admin role 200; `Cache-Control: no-store`.

---

## 19. Postman Verification

Use the sample document of spec file 47, section 19.

**1. Process again from the start**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/reprocess
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{ "mode": "FULL" }
```

Expected: HTTP 202, `status: "QUEUED"`.

**2. Processing status (after about 10 seconds)**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/processing
Authorization: Bearer <admin-access-token>
```

Expected while spec file 50 is not implemented: `status: "FAILED"`, `failedStage: "EMBED"`, `lastErrorCode: "INGESTION_STAGE_NOT_AVAILABLE"`, `pageCount: 1`, `chunkCount: 6`, `embeddedChunkCount: 0`.

**3. List the chunks**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/chunks?page=0&size=20
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 200, `totalElements: 6`, the section titles of section 15, `embedded: false` everywhere.

**4. Read one chunk**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/chunks/4
Authorization: Bearer <admin-access-token>
```

Expected: `sectionTitle: "Chapter 5 Graduation"`, and `content` contains "at least 120 credits".

**5. The Thai chunk**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/chunks/5
Authorization: Bearer <admin-access-token>
```

Expected: `sectionTitle: "หมวด 6 การลาพักการศึกษา"`, and the Thai text is complete and unbroken.

**6. A long PDF**

Upload a PDF of at least ten pages, reprocess it with mode `FULL`, list its chunks with `size=100`.

Expected: no `charCount` above 1,500; chunks that follow each other inside one section share a short overlap; `pageStart` and `pageEnd` match the pages in a PDF viewer.

**7. Missing chunk**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/chunks/9999
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 404, `code: "DOCUMENT_CHUNK_NOT_FOUND"`.

---

## 20. Acceptance Criteria

- [ ] `SplitIntoChunksStage` is the only bean for `CHUNK` and follows the stage contract of spec file 46.
- [ ] Sizes are measured in characters; no chunk exceeds `max-chars`.
- [ ] No cut inside a grapheme cluster or surrogate pair, verified by tests with Thai and Myanmar text.
- [ ] Headings start new chunks when the previous chunk has enough body text; clause lines are not headings.
- [ ] `section_title` follows section 7.
- [ ] Overlap follows section 10; tables and sheets repeat their header row instead.
- [ ] `content_hash` is the SHA-256 of the embedding input, built by one shared method.
- [ ] Chunking is deterministic; duplicates inside a file are dropped; indexes have no gaps.
- [ ] `page_start` and `page_end` are correct.
- [ ] The worked example of section 15 is reproduced by a test.
- [ ] The `embedding` column is not mapped; `embedding_model` and `embedded_at` are read-only on the entity.
- [ ] The two chunk endpoints work for Admin roles only and never return a vector.
- [ ] No AI model, no network call, no new dependency.
- [ ] No chunk text in logs or error messages.
- [ ] `KNOWLEDGE_CHUNK_*` variables documented in `.env.example` and passed in `compose.yaml`.
- [ ] Tests of section 18 pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 21. Agent Completion Report

Report: files created and modified; the configuration in effect; the result of the worked example; chunk statistics for one generated long document (count, average and maximum size, number of split lines); how grapheme safety was tested; any case where boundary 4 of section 8 was reached; changed existing tests; test totals against the baseline; Postman results actually performed; decision entries added to `00-changes-and-decision.md`.

Do not claim that documents are searchable: chunks have no embeddings yet.

**Next: spec file 50 — Step 14F: Create Embeddings.**
