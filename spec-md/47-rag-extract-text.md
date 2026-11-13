# 47 — Step 14C: RAG Document Processing — Extract Text

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** The `EXTRACT` stage: read a stored PDF, DOCX, XLSX or TXT file and save its text unit by unit. Two read-only Admin endpoints to inspect the result.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Apache PDFBox, Apache POI
**Prerequisites:** Spec files 45 (schema) and 46 (ingestion worker) are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → **47 extract (this file)** → 48 clean → 49 chunk → 50 embed → 51 vector store → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
PDF Upload
    ↓
Extract Text      ← this file
    ↓
Clean Text
```

After this step the backend can take the stored bytes of the current revision of a knowledge document and write its text into `academic_document_pages`:

| File type | One row per | `unit_type` |
|---|---|---|
| PDF | page | `PAGE` |
| DOCX | section (split at top-level headings) | `SECTION` |
| XLSX | visible sheet | `SHEET` |
| TXT | block (split at form feeds) | `TEXT` |

Only `raw_text` and `raw_char_count` are written here. `cleaned_text` stays NULL until spec file 48.

Extraction is deterministic and local: no OCR, no AI model, no network call (R8).

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 39, 45 and 46, and decision D55.
2. Inspect `FileStorageService` and `LocalFileStorageService` (how a file is opened and which failures `open` throws), `KnowledgeFileValidatorImpl` (what an accepted file is guaranteed to look like) and `AcademicDocumentFile`.
3. Inspect the stage contract of spec file 46, section 6. This stage must obey every rule there.
4. Check Maven Central for the latest stable Apache PDFBox 3.x and Apache POI 5.x releases and their published security advisories on the day of implementation.
5. Run the tests and the build.

Do not:

- Add Apache Tika, Tess4J or any OCR, or call an AI model to read a document.
- Read a file by building a path from `storage_key`. Use `FileStorageService.open(storageKey)` only.
- Load a whole file into a `byte[]`, or build a whole spreadsheet in memory (`XSSFWorkbook`) for extraction.
- Keep a database transaction open while a file is parsed.
- Execute, render or follow anything inside a document (JavaScript, macros, embedded files, external links, remote images).
- Log or put into an error message any extracted text, file name, storage key or path.
- Clean, normalize or re-order the text beyond section 12. That is spec file 48.
- Create chunk rows. That is spec file 49.
- Use real university documents as test fixtures.

---

## 3. Package Structure

All inside `feature/knowledge` (flat layer folders, R11):

```text
feature/knowledge/
├── controller/
│   └── AdminKnowledgeDocumentContentController.java   (new: pages; chunks are added by spec file 49)
├── dto/
│   ├── DocumentTextPageListQuery.java                 (new)
│   ├── DocumentTextPageListResponse.java              (new)
│   ├── DocumentTextPageSummaryResponse.java           (new)
│   └── DocumentTextPageResponse.java                  (new)
├── entity/
│   └── AcademicDocumentPage.java                      (new)
├── enums/
│   └── DocumentUnitType.java                          (new: PAGE, SECTION, SHEET, TEXT)
├── exception/
│   └── KnowledgeFailure.java                          (+ pageNotFound)
├── repository/
│   └── AcademicDocumentPageRepository.java            (new)
└── service/
    ├── DocumentTextExtractor.java                     (new)
    ├── ExtractedUnit.java                             (new, record)
    ├── ExtractionLimits.java                          (new, record)
    ├── KnowledgeDocumentContentService.java           (new)
    └── impl/
        ├── ExtractTextStage.java                      (new, IngestionStage EXTRACT)
        ├── PdfTextExtractor.java                      (new)
        ├── DocxTextExtractor.java                     (new)
        ├── XlsxTextExtractor.java                     (new)
        ├── PlainTextExtractor.java                    (new)
        └── KnowledgeDocumentContentServiceImpl.java   (new)

config/
└── KnowledgeExtractionProperties.java                 (new, app.knowledge.extraction)
```

`KnowledgeDocumentPageResponse` already exists and means "one page of the document **list**". Do not reuse or rename it. The new DTOs are named `DocumentTextPage…` to keep the two apart.

---

## 4. Dependencies

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>${pdfbox.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>${poi.version}</version>
</dependency>
```

- Define `pdfbox.version` (a 3.x release) and `poi.version` (a 5.x release) in `<properties>`. Spring Boot does not manage these two versions. Use the latest stable releases and write the exact versions into the completion report.
- The PDFBox calls named in this file are the PDFBox **3.x** API (`org.apache.pdfbox.Loader`). Confirm every signature against the version that is actually used.
- Run `./mvnw dependency:tree` afterwards. If a transitive logging library clashes with Spring Boot's logging, exclude only that artifact and report it.
- No other new dependency.

---

## 5. Configuration

`application.properties`:

```properties
# Step 14C: text extraction from stored knowledge documents.
app.knowledge.extraction.max-pages=${KNOWLEDGE_EXTRACTION_MAX_PAGES:2000}
app.knowledge.extraction.max-chars=${KNOWLEDGE_EXTRACTION_MAX_CHARS:5000000}
app.knowledge.extraction.unit-max-chars=${KNOWLEDGE_EXTRACTION_UNIT_MAX_CHARS:20000}
app.knowledge.extraction.timeout=${KNOWLEDGE_EXTRACTION_TIMEOUT:5m}
app.knowledge.extraction.pdf-sort-by-position=${KNOWLEDGE_EXTRACTION_PDF_SORT_BY_POSITION:true}
app.knowledge.extraction.temp-dir=${KNOWLEDGE_EXTRACTION_TEMP_DIR:}
```

| Property | Meaning | Validation |
|---|---|---|
| `max-pages` | Maximum number of units (PDF pages, sections, sheets, text blocks) of one file. | 1 to 10,000 |
| `max-chars` | Maximum total number of extracted characters of one file. | 10,000 to 50,000,000 |
| `unit-max-chars` | A DOCX section, sheet or text block longer than this is stored as several rows. Not applied to PDF pages. | 2,000 to 200,000 |
| `timeout` | Wall-clock limit for parsing one file. | 10 s to 30 min, and less than `app.knowledge.ingestion.stage-timeout` |
| `pdf-sort-by-position` | `true`: order PDF text by its position on the page (keeps table rows together). `false`: keep the order of the PDF content stream (better for multi-column pages). | boolean |
| `temp-dir` | Directory for the private working copy. Empty means `<java.io.tmpdir>/knowledge-extraction`. | must be creatable and writable |

Invalid values stop the application at startup, like the other `@Validated` properties.

Add the variables to `.env.example` (one comment line each) and to the `backend` service in `compose.yaml`.

**Character counts.** Everywhere in Step 14 a "character" is one Java `char` (`String.length()`). Thai, Myanmar and Latin letters are one `char` each.

---

## 6. Stage Flow

`ExtractTextStage` is a Spring bean implementing `IngestionStage` with `type() = EXTRACT`. Installing it makes the reprocess endpoint available (spec file 46, section 14).

```text
1. control.checkpoint()
2. Delete this file's rows from academic_document_pages (batches of at most 500).
3. Copy the stored bytes to a private temporary file while computing size and SHA-256.
4. Compare size and checksum with academic_document_files.
5. Choose the extractor by job.fileExtension(): pdf, docx, xlsx, txt.
6. Run the extractor on the extraction thread. The worker thread waits and calls
   control.checkpoint() at least every 10 seconds, for at most `timeout`.
7. Sanitize every unit (section 12) and number the units 1, 2, 3 … in the order returned.
8. Fail with DOCUMENT_TEXT_EMPTY when no unit contains a letter or a digit.
9. Insert the units in batches, control.checkpoint() between batches.
finally: cancel the extraction task if it still runs, delete the temporary file.
```

Details:

- **Step 2** makes the stage repeatable. The worker already clears pages and chunks before a full run; the stage does not rely on it.
- **Step 3** uses `FileStorageService.open(job.storageKey())`. The temporary file is created with owner-only permissions (as `LocalFileStorageService` does for partial files) under `temp-dir`, with a generated name (`<uuid>.<ext>`), never the original file name. The copy stops and fails if more than `job.sizeBytes()` bytes arrive.
- **Step 4** protects against a file that was damaged or replaced in storage after upload.
- **Step 6** exists because a damaged or hostile file can make a parser loop forever, and a parser never calls `checkpoint()`. Use a dedicated single-thread executor (daemon thread, name `knowledge-extract`). On timeout call `future.cancel(true)` and fail with `DOCUMENT_EXTRACTION_TIMEOUT`. If the task does not end within 5 seconds after the cancel, log one `WARN` line, discard this executor and create a new one for the next job. Shut the executor down in `@PreDestroy`.
- **Step 6, interruption:** if the worker thread itself is interrupted (application shutdown), cancel the task, restore the interrupt flag and throw `INGESTION_INTERRUPTED` (retryable).
- **Step 9:** one transaction per batch. A batch ends at 50 units or 500,000 characters, whichever comes first. All database work happens on the worker thread, never on the extraction thread.
- On startup, delete leftover files in the temporary directory that match the generated name pattern (a crash can leave one).

No transaction is open during steps 3 to 8.

---

## 7. Extractor Contract

```java
/** Reads one file and returns its text units in document order. No database, no Spring Security, no network. */
public interface DocumentTextExtractor {
    boolean supports(String fileExtension);                       // "pdf", "docx", "xlsx" or "txt"
    List<ExtractedUnit> extract(Path file, ExtractionLimits limits) throws IOException;
}

public record ExtractedUnit(DocumentUnitType unitType, String label, String text) {}

public record ExtractionLimits(int maxPages, int maxChars, int unitMaxChars) {}
```

Rules for every extractor:

- It enforces `maxPages` and `maxChars` **while reading** and stops at once when a limit is passed (`DOCUMENT_TOO_MANY_PAGES`, `DOCUMENT_TEXT_TOO_LARGE`). It never reads the whole file first and checks afterwards.
- It checks `Thread.currentThread().isInterrupted()` between pages, body elements, rows or read buffers and stops when it is set.
- It may throw `KnowledgeIngestionException` (stage `EXTRACT`) with one of the codes of section 14. Any `IOException`, `RuntimeException`, `OutOfMemoryError` or `StackOverflowError` from the parsing library is converted by the stage to `DOCUMENT_TEXT_EXTRACTION_FAILED` (not retryable). Only the exception class name is logged.
- It returns an empty unit as the empty string, never as `null`.
- Exactly one extractor supports each extension. An extension without an extractor fails with `DOCUMENT_TEXT_EXTRACTION_FAILED`.

**Splitting long units (DOCX, XLSX, TXT).** When the text collected for a unit reaches `unitMaxChars` and the current line ends, the unit is closed and a new unit of the same `unitType` and the same `label` begins. A single line longer than `unitMaxChars` is cut at `unitMaxChars` (never between the two halves of a surrogate pair). Spec file 49 treats consecutive units with the same type and label as one continuous text.

**Heading marker.** Extractors that know the document structure (DOCX) write a heading as its own line starting with `#` characters and one space, one `#` per level, at most six: `# Chapter 2 Registration`, `## 2.1 Add and Drop`. Spec files 48 and 49 recognise this marker in the text of every file type, so a TXT file may use the same convention.

---

## 8. PDF

Library: Apache PDFBox 3.x.

1. Open the temporary file with `Loader.loadPDF(...)`, using a stream cache that is not memory-only (a temporary-file or mixed cache), so a large PDF does not have to fit into the heap.
2. A PDF that needs a password to open fails with `DOCUMENT_ENCRYPTED` (`InvalidPasswordException`).
3. A PDF that opens without a password but whose permissions forbid content extraction (`getCurrentAccessPermission().canExtractContent()` is false) also fails with `DOCUMENT_ENCRYPTED`. The Admin uploads an unrestricted copy instead.
4. `getNumberOfPages()` greater than `maxPages` fails with `DOCUMENT_TOO_MANY_PAGES` before any text is read. A PDF with zero pages fails with `DOCUMENT_TEXT_EMPTY`.
5. Extract page by page with one `PDFTextStripper`:

   ```java
   stripper.setSortByPosition(properties.pdfSortByPosition());
   stripper.setLineSeparator("\n");
   stripper.setStartPage(n);
   stripper.setEndPage(n);
   String text = stripper.getText(document);
   ```

6. One `PAGE` unit per page, in order, **including empty pages**, so `page_number` always equals the page number a PDF viewer shows. `label` is NULL.
7. Only the page content is extracted. Bookmarks, annotations, form field values, attachments and metadata are ignored. Nothing in the file is executed.

PDFBox may scan the system fonts and write a font cache the first time it meets a font that is not embedded. Point that cache at the temporary directory (system property `pdfbox.fontcache`; confirm the name for the version in use) so it never writes into the home directory of the container user.

### 8.1 What PDF extraction cannot do

Record these in the completion report as known limitations; do not try to fix them in this step:

- **Scanned PDFs** contain images, not text. They produce empty pages and the job fails with `DOCUMENT_TEXT_EMPTY`. OCR is out of scope (R8).
- **Thai text** can come out with vowels and tone marks in a different order, or with SARA AM split in two characters, when the PDF's fonts carry incomplete Unicode tables. Spec file 48 repairs the SARA AM case. Where a Thai PDF extracts badly, the Admin uploads the DOCX original instead.
- **Tables** become lines of cell texts separated by spaces. Column structure is not preserved.
- **Multi-column pages** can interleave their columns when `pdf-sort-by-position` is `true`. Set it to `false` for such documents.

---

## 9. DOCX

Library: Apache POI (`XWPFDocument`), opened read-only from the temporary file.

Walk the body elements in document order (`getBodyElements()`):

| Element | Output |
|---|---|
| Heading paragraph | Its own line with the heading marker of section 7, a blank line before and after. |
| Normal paragraph | Its text, followed by a blank line. Line breaks inside the paragraph become `\n`. |
| List paragraph (has numbering) | `- ` + text. Consecutive list items are separated by one line break. Automatic list numbers are not reconstructed. |
| Table | One line per row: the cell texts joined with ` | `. The paragraphs of one cell are joined with a space. A nested table is flattened into its cell. A blank line before and after the table. |
| Empty paragraph | Skipped. |

A paragraph is a **heading** when its style, or a style it is based on (follow `basedOn` at most 10 levels), has the built-in name `heading 1` … `heading 9` or `Title`, or when the paragraph or style has an outline level 0 to 8. The style **name** is the same in every language version of Word; the style id is not, so do not test the id. Level = the heading number (`Title` = 1, outline level + 1), capped at 6.

**Units:** a new `SECTION` unit starts at every level-1 heading. Its `label` is the heading text (trimmed, at most 255 characters). Text before the first level-1 heading forms a unit with a NULL label. A document without level-1 headings is one section, split only by `unitMaxChars`.

Not extracted: headers, footers, comments, text that a tracked change deleted, text boxes and shapes, embedded objects, images, field codes. Text inserted by a tracked change **is** extracted. Footnote and endnote text is kept where the library returns it with its paragraph; do not write a separate parser for it.

Limitation to report: automatically numbered clauses ("ข้อ 5", "Article 5" produced by list numbering) lose their number in DOCX. The PDF version of the same document keeps them.

---

## 10. XLSX

Library: Apache POI streaming reader (`OPCPackage` opened read-only, `XSSFReader`, `XSSFSheetXMLHandler`, `ReadOnlySharedStringsTable`). Never `XSSFWorkbook`.

- One `SHEET` unit per **visible** sheet that has at least one non-empty cell, in workbook order. `label` = the sheet name (at most 255 characters).
- Hidden and very hidden sheets are skipped. The streaming reader does not report the sheet state, so read it from the workbook part (`XSSFReader.getWorkbookData()`) with a hardened XML parser (no DTD, no external entities).
- One line per non-empty row: the cell values joined with ` | ` in column order. A gap between two filled cells is kept as an empty value so columns stay aligned (`CS301 |  | 3`). Trailing empty cells are dropped.
- Values are written as the workbook displays them: `DataFormatter` with the fixed locale `Locale.US`, cached formula results (never the formula text, never recalculated).
- Not extracted: comments, charts, images, headers and footers, defined names, data of hidden sheets. Hidden rows and columns of a visible sheet **are** extracted; report this as a limitation.

The first non-empty row of a sheet is treated as its header row by spec file 49.

---

## 11. TXT

- Decode as UTF-8 with a strict decoder (`CodingErrorAction.REPORT`). The upload validator already guarantees UTF-8; a decoding error here fails with `DOCUMENT_TEXT_EXTRACTION_FAILED`.
- Read through a buffered reader. Never `readAllBytes`.
- Remove a leading byte order mark (U+FEFF).
- A form feed (U+000C) ends the current unit. Empty units created by consecutive form feeds are skipped.
- `TEXT` units, `label` NULL, split by `unitMaxChars`.

---

## 12. Sanitizing Raw Text

`raw_text` is what the extractor produced, with only the changes that are needed to store it safely:

1. Remove U+0000 (PostgreSQL cannot store it in `TEXT`).
2. Replace an unpaired surrogate with U+FFFD.
3. Convert `\r\n` and `\r` to `\n`.
4. Remove form feeds that remain inside a unit.

Nothing else. No trimming, no Unicode normalization, no whitespace changes, no removal of headers or page numbers. `raw_char_count` is the length after these four steps.

---

## 13. Persistence

```java
@Entity @Table(name = "academic_document_pages") @Getter @Setter
public class AcademicDocumentPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false) private AcademicDocumentFile file;
    @Column(name = "page_number", nullable = false) private int pageNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20) private DocumentUnitType unitType;
    @Column(name = "unit_label", length = 255) private String unitLabel;
    @Column(name = "raw_text", nullable = false, columnDefinition = "text") private String rawText;
    @Column(name = "raw_char_count", nullable = false) private int rawCharCount;
    @Column(name = "cleaned_text", columnDefinition = "text") private String cleanedText;
    @Column(name = "cleaned_char_count") private Integer cleanedCharCount;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
```

Repository needs (JPA and native queries, like the other knowledge repositories):

- delete one batch of a file's pages and return the number of deleted rows;
- count the pages of a file;
- a paged **projection** for the list endpoint that does not load the full text: `page_number`, `unit_type`, `unit_label`, `raw_char_count`, `cleaned_char_count` and `LEFT(COALESCE(cleaned_text, raw_text), 200)` as the preview;
- find one page by file and page number;
- read a file's pages in order, a limited number at a time (used by spec files 48 and 49).

`created_at` is UTC from the injected `Clock`.

---

## 14. Error Codes

Processing codes of this stage (stored in `last_error_code` with `failed_stage = EXTRACT`; never an HTTP status):

| Code | Retryable | Safe message stored in `last_error_message` |
|---|---|---|
| `DOCUMENT_FILE_MISSING` | no | "The stored file could not be found." |
| `DOCUMENT_STORAGE_UNAVAILABLE` | yes | "Document storage is temporarily unavailable." |
| `DOCUMENT_CHECKSUM_MISMATCH` | no | "The stored file does not match the uploaded file." |
| `DOCUMENT_ENCRYPTED` | no | "The document is password-protected or does not allow text extraction." |
| `DOCUMENT_TEXT_EMPTY` | no | "No text could be extracted. Scanned documents are not supported." |
| `DOCUMENT_TOO_MANY_PAGES` | no | "The document has more pages than the configured limit." |
| `DOCUMENT_TEXT_TOO_LARGE` | no | "The document contains more text than the configured limit." |
| `DOCUMENT_EXTRACTION_TIMEOUT` | no | "Text extraction took too long." |
| `DOCUMENT_TEXT_EXTRACTION_FAILED` | no | "The document could not be read. It may be damaged." |

Mapping of storage failures: `FileStorageService.open` throws `KnowledgeFailure` with code `DOCUMENT_FILE_NOT_FOUND` when the bytes are gone (→ `DOCUMENT_FILE_MISSING`) and with `DOCUMENT_STORAGE_FAILED` for a read error (→ `DOCUMENT_STORAGE_UNAVAILABLE`). An `IOException` while copying is also `DOCUMENT_STORAGE_UNAVAILABLE`. A full temporary directory is `DOCUMENT_STORAGE_UNAVAILABLE`.

New HTTP error (`KnowledgeFailure.pageNotFound()`):

| Code | HTTP | Message |
|---|---:|---|
| `DOCUMENT_PAGE_NOT_FOUND` | 404 | "The requested document page does not exist." |

---

## 15. Admin Inspection Endpoints

Read-only. They exist so an Admin (and the developer) can see what the extractor produced. Same access rule as every other endpoint under `/api/v1/admin/knowledge/**` (SUPER_ADMIN, ADMIN, ACADEMIC_ADMIN); `KnowledgeDocumentContentServiceImpl` carries the same class-level `@PreAuthorize` as `KnowledgeDocumentServiceImpl`. Both always describe the **current** revision. Both answer with `Cache-Control: no-store`.

### 15.1 List pages

```http
GET /api/v1/admin/knowledge/documents/{id}/pages?page=0&size=20
Authorization: Bearer <admin-access-token>
```

- `page` default 0, `size` default 20, maximum 100. Any other query parameter, a repeated parameter or an invalid number is 400 `VALIDATION_ERROR` (same strict parsing as the document list, `ListQuerySupport`).
- Order is fixed: `pageNumber` ascending.
- A document without pages returns an empty `content` with `totalElements: 0`, not 404.

```json
{
  "documentId": 31,
  "fileId": 81,
  "revisionNumber": 1,
  "content": [
    {
      "pageNumber": 1,
      "unitType": "TEXT",
      "unitLabel": null,
      "rawCharCount": 2185,
      "cleanedCharCount": null,
      "preview": "SAMPLE DATA FOR DEVELOPMENT ONLY — NOT AN OFFICIAL UNIVERSITY DOCUMENT\n\nSample University Undergraduate Academic Regulations (fictional) …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

`preview` is the first 200 characters of the cleaned text when it exists, otherwise of the raw text.

### 15.2 One page

```http
GET /api/v1/admin/knowledge/documents/{id}/pages/{pageNumber}
Authorization: Bearer <admin-access-token>
```

```json
{
  "documentId": 31,
  "fileId": 81,
  "revisionNumber": 1,
  "pageNumber": 1,
  "unitType": "TEXT",
  "unitLabel": null,
  "rawCharCount": 2185,
  "rawText": "SAMPLE DATA FOR DEVELOPMENT ONLY — NOT AN OFFICIAL UNIVERSITY DOCUMENT\n\n…",
  "cleanedCharCount": null,
  "cleanedText": null
}
```

Errors for both:

| Case | Error |
|---|---|
| No token / Student token | 401 / 403 |
| `id` or `pageNumber` not a positive number | 400 `VALIDATION_ERROR` |
| Document missing or deleted | 404 `DOCUMENT_NOT_FOUND` |
| Document has no current file | 404 `DOCUMENT_FILE_NOT_FOUND` |
| Page number does not exist (15.2 only) | 404 `DOCUMENT_PAGE_NOT_FOUND` |

An archived document can be inspected. Students have no endpoint that returns page text.

---

## 16. Effect on Existing Endpoints

- `POST /{id}/reprocess` now answers **202** (one stage is installed).
- Until spec file 48 exists, every job ends as `FAILED` with `failedStage: "CLEAN"` and `lastErrorCode: "INGESTION_STAGE_NOT_AVAILABLE"`. This is expected (spec file 46, section 10.2). The extracted pages are kept and can be inspected.
- `GET /{id}/processing` shows the real `pageCount`.
- `ingestionAvailable` stays `false` and uploads are still not queued automatically, because the pipeline is not complete.

---

## 17. Logging

```text
KNOWLEDGE_EXTRACT documentId=31 fileId=81 type=pdf units=42 emptyUnits=1 chars=118233 durationMs=1840
KNOWLEDGE_EXTRACT documentId=31 fileId=81 type=pdf failed code=DOCUMENT_ENCRYPTED
```

Ids, counts, codes and durations only. Never text, file names, sheet names, headings, storage keys or library exception messages.

---

## 18. Resource Notes

- One job runs at a time per backend instance, so at most one file is parsed at a time.
- Parsing a 50 MB PDF or XLSX can need several hundred megabytes of heap. If the backend container has a memory limit below 1 GB, either raise it or lower `KNOWLEDGE_MAX_FILE_SIZE_MB`. Report the largest file that was actually tried and the memory setting used.
- POI's archive limits are JVM-wide static settings (`ZipSecureFile`). Set them once at startup to match the upload validator: minimum inflate ratio 0.01 (100:1), maximum entry size 256 MB. A file that POI rejects for these limits fails with `DOCUMENT_TEXT_EXTRACTION_FAILED`.
- A parser thread that ignores the interrupt cannot be stopped by Java. It keeps running until the application restarts. This is logged (section 6) and accepted, because only Admins can upload files.

---

## 19. Sample Document for Manual Checks

Create `docker/dev-fixtures/rag-sample-regulations.txt` (UTF-8, no BOM) with exactly this content. It is fictional and is used for the Postman checks of spec files 47 to 53. Do not load it automatically and do not seed it through Flyway.

```text
SAMPLE DATA FOR DEVELOPMENT ONLY — NOT AN OFFICIAL UNIVERSITY DOCUMENT

Sample University Undergraduate Academic Regulations (fictional), Academic Year 2026

Chapter 1 General Provisions

Article 1. These sample regulations apply to all undergraduate students of Sample University.

Article 2. An academic year has two regular semesters and one summer session. A regular semester lasts 15 weeks. The summer session lasts 8 weeks.

Chapter 2 Registration

Article 3. In a regular semester a student must register for at least 9 credits and at most 22 credits. In the summer session a student may register for at most 9 credits.

Article 4. A student may add or drop a course within the first 14 days of a regular semester. A course dropped in this period does not appear on the transcript.

Article 5. A student may withdraw from a course until the end of week 10. A withdrawn course is recorded with the grade W and is not counted in the grade point average.

Article 6. A student who registers after the registration period pays a late registration fee of 500 baht.

Chapter 3 Tuition and Fees

Article 7. Tuition must be paid within 14 days after the first day of the semester. A late payment fine of 50 baht per day is charged, up to a maximum of 1,500 baht.

Article 8. A student who has not paid tuition by the end of week 4 is not allowed to sit the midterm examination.

Chapter 4 Examinations

Article 9. A student must attend at least 80 percent of the classes of a course to be allowed to sit its final examination.

Article 10. Final grades are announced within 14 days after the last examination day. A student may request a grade review within 7 days after the grades are announced.

Chapter 5 Graduation

Article 11. To graduate, a student must complete at least 120 credits required by the curriculum and have a cumulative grade point average of at least 2.00.

Article 12. A student must apply for graduation within the first 30 days of the final semester.

หมวด 6 การลาพักการศึกษา

ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน

ข้อ 14 นักศึกษาต้องชำระค่าธรรมเนียมรักษาสถานภาพนักศึกษา ภาคการศึกษาละ 1,000 บาท ในระหว่างที่ลาพักการศึกษา
```

For a PDF check, open this file in a word processor and export it as PDF, or use any text-based PDF you are allowed to use.

---

## 20. Tests

### 20.1 Test fixtures

`KnowledgeTestFiles` builds files that pass upload validation but are **not** parseable. Keep it: those files are the fixtures for the "damaged file" cases here.

Add a helper (for example `KnowledgeRealFiles`) that **generates** real files in test code:

- PDF with PDFBox: several pages of known English text in a standard font; a variant with an empty page in the middle; a variant with a page that contains only a drawing (no text); a password-protected variant; a variant whose permissions forbid extraction.
- DOCX with POI: headings of level 1 and 2, normal paragraphs, a list, a table, a header and a footer, Thai text, a tracked deletion.
- XLSX with POI: two visible sheets and one hidden sheet, text, numbers, a date, a formula with a cached result, a row with a gap.
- TXT: UTF-8 with BOM, CRLF line ends, a form feed, Thai and Myanmar text.

A Thai **PDF** fixture needs an embedded Thai font. Add one only if its licence allows it (for example an OFL-licensed font, with its licence file, under `src/test/resources/fonts/`). Otherwise cover Thai through DOCX and TXT and state in the report that Thai PDF extraction was checked manually.

### 20.2 Unit tests (one class per extractor, no Spring context)

PDF:

- Three pages give three `PAGE` units numbered 1 to 3 with the expected text on each.
- An empty page in the middle is stored as an empty unit; the numbering is not shifted.
- A PDF without any text fails with `DOCUMENT_TEXT_EMPTY`.
- A password-protected PDF and a PDF without extraction permission fail with `DOCUMENT_ENCRYPTED`.
- More pages than `maxPages` fails with `DOCUMENT_TOO_MANY_PAGES`; more text than `maxChars` fails with `DOCUMENT_TEXT_TOO_LARGE`.
- A damaged PDF (`KnowledgeTestFiles`) fails with `DOCUMENT_TEXT_EXTRACTION_FAILED`.

DOCX:

- Level-1 headings start new `SECTION` units with the heading as label; text before the first heading has a NULL label.
- Headings carry the `#` marker with the right level.
- Table rows are single lines joined with ` | `; list items start with `- `.
- Header, footer and the tracked deletion are absent; Thai text is returned unchanged.
- A long section is split by `unitMaxChars` into units with the same label.

XLSX:

- Two units for the two visible sheets, labelled with the sheet names; the hidden sheet is absent.
- Numbers and the date appear as displayed; the formula shows its cached result.
- A gap between cells is kept; empty rows are skipped; an empty sheet gives no unit.

TXT:

- The BOM is removed, CRLF becomes LF, the form feed splits the text into two units, Thai and Myanmar text is unchanged.
- A long text is split by `unitMaxChars` at a line end.

Sanitizing: U+0000 removed, unpaired surrogate replaced, nothing else changed.

### 20.3 Stage tests (Testcontainers, extends `KnowledgeIntegrationSupport`)

As in spec file 46, build `DocumentIngestionServiceImpl` by hand, here with **only** the real `ExtractTextStage`, so the assertions stay valid after later stages are added.

- Upload a generated PDF through the real upload endpoint, call reprocess, call `runOnce()`: the page rows exist with the right numbers, types and counts; the job is `FAILED` with `failed_stage = CLEAN` and `INGESTION_STAGE_NOT_AVAILABLE`; `cleaned_text` is NULL.
- The same for DOCX, XLSX and TXT.
- Running the job again (reprocess `FULL`) replaces the rows; no duplicates.
- The stored file is changed on disk after upload: `DOCUMENT_CHECKSUM_MISMATCH`, no page rows.
- The stored file is deleted on disk: `DOCUMENT_FILE_MISSING`.
- A damaged file: `FAILED`, `failed_stage = EXTRACT`, `DOCUMENT_TEXT_EXTRACTION_FAILED`, no `next_attempt_at`.
- A slow fake extractor with a 200 ms timeout: `DOCUMENT_EXTRACTION_TIMEOUT`; the temporary file is gone afterwards.
- The temporary file is deleted after success and after every failure.
- `last_error_message` never contains text from the document or the file name.

### 20.4 Endpoint tests

- List and detail return the stored values; the list does not contain `rawText`.
- Paging: `size` 1, second page, `size` 101 → 400, unknown parameter → 400.
- 404 codes of section 15; an archived document is readable; a deleted document is 404.
- After a file replacement the endpoints describe the new revision (empty until it is processed).
- No token 401; Student token 403; each Admin role 200.
- `Cache-Control: no-store` is present.

---

## 21. Postman Verification

Log in as an Admin first (existing login endpoint) and use the access token. A document category must exist (`GET /api/v1/admin/knowledge/categories`).

**1. Upload the sample document**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/upload
Authorization: Bearer <admin-access-token>
Content-Type: multipart/form-data
```

Form Data:

```text
title = Sample Academic Regulations 2026
categoryId = <an existing category id>
academicYearLabel = 2026-2027
description = Fictional sample for RAG testing
file = rag-sample-regulations.txt
```

Expected: HTTP 201, `processingStatus: "UPLOADED"`. Note `documentId`.

**2. Start processing**

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/reprocess
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 202, `status: "QUEUED"`.

**3. Check the result (after about 10 seconds)**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/processing
Authorization: Bearer <admin-access-token>
```

Expected while spec file 48 is not implemented: `status: "FAILED"`, `failedStage: "CLEAN"`, `lastErrorCode: "INGESTION_STAGE_NOT_AVAILABLE"`, `pageCount: 1`, `chunkCount: 0`, `ingestionAvailable: false`.

**4. List the extracted pages**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/pages?page=0&size=20
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 200, one item, `unitType: "TEXT"`, `cleanedCharCount: null`.

**5. Read one page**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/pages/1
Authorization: Bearer <admin-access-token>
```

Expected: `rawText` contains "at least 120 credits" and the Thai line "ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน" exactly as in the file.

**6. PDF**

Upload a text-based PDF with several pages, reprocess, then list the pages.

Expected: `unitType: "PAGE"`, `totalElements` equals the number of pages in a PDF viewer, and page 3 of the API shows the text of page 3 of the viewer.

**7. Scanned or password-protected PDF**

Upload one, reprocess, read the processing status.

Expected: `status: "FAILED"`, `failedStage: "EXTRACT"`, `lastErrorCode: "DOCUMENT_TEXT_EMPTY"` or `"DOCUMENT_ENCRYPTED"`, `nextAttemptAt: null`, and a `lastErrorMessage` without any document content.

**8. Missing page**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/pages/9999
Authorization: Bearer <admin-access-token>
```

Expected: HTTP 404, `code: "DOCUMENT_PAGE_NOT_FOUND"`.

**9. Student token**

Call request 4 with a Student access token. Expected: HTTP 403.

---

## 22. Acceptance Criteria

- [ ] PDFBox and POI added with explicit, current versions; no Tika, no OCR.
- [ ] `ExtractTextStage` is the only bean for `EXTRACT` and follows the stage contract of spec file 46.
- [ ] Files are read only through `FileStorageService.open`; size and SHA-256 are verified before parsing.
- [ ] PDF: one row per page, empty pages kept, encrypted and scanned files rejected with the right code.
- [ ] DOCX: sections by level-1 heading, heading markers, tables as rows, tracked deletions excluded.
- [ ] XLSX: streaming reader, one row per visible sheet, hidden sheets skipped, displayed values.
- [ ] TXT: strict UTF-8, BOM removed, split at form feeds.
- [ ] Limits (`max-pages`, `max-chars`, `unit-max-chars`, `timeout`) are enforced while reading.
- [ ] Parsing runs under a hard timeout on its own thread; the worker keeps its heartbeat.
- [ ] No database transaction is open during parsing; inserts are batched.
- [ ] The temporary file is always deleted.
- [ ] Raw text is stored with only the four changes of section 12.
- [ ] Error codes and safe messages of section 14 are stored; nothing from the document reaches logs or messages.
- [ ] The two inspection endpoints work for Admin roles only and never expose a storage key or path.
- [ ] `KNOWLEDGE_EXTRACTION_*` variables documented in `.env.example` and passed in `compose.yaml`.
- [ ] Sample document created under `docker/dev-fixtures/`, not seeded.
- [ ] Tests of section 20 pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 23. Agent Completion Report

Report: files created and modified; exact PDFBox and POI versions and the result of the dependency check; the extraction rules as implemented for each file type; limits in effect; how the timeout and the temporary file cleanup were tested; the Thai PDF check (automated or manual, and what was observed); known limitations (section 8.1, DOCX numbering, hidden rows); the largest file tried and the memory used; changed existing tests; test totals against the baseline; Postman results actually performed; decision entries added to `00-changes-and-decision.md`.

Do not claim that documents are searchable: only text extraction exists.

**Next: spec file 48 — Step 14D: Clean Text.**
