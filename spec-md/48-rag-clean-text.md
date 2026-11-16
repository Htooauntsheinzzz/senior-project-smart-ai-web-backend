# 48 — Step 14D: RAG Document Processing — Clean Text

**Project:** Smart AI University Student Assistant
**Module:** Admin AI Management / AI Knowledge Base → RAG document processing
**Scope:** The `CLEAN` stage: turn the raw text of every page into clean text that can be chunked. A shared text normalizer that is reused for search queries later.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector
**Prerequisites:** Spec files 45, 46 and 47 are implemented
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Step 14 map: 45 schema → 46 worker → 47 extract → **48 clean (this file)** → 49 chunk → 50 embed → 51 vector store → 52 search → 53 assistant tool. Decisions R1 to R13 are in spec file 45, section 5.

---

## 1. Objective

```text
Extract Text
    ↓
Clean Text        ← this file
    ↓
Split into Chunks
```

Raw text from a PDF is not ready for an embedding model. It contains hard line breaks in the middle of sentences, words split by hyphens, page numbers, a header and footer repeated on every page, ligatures, invisible characters and broken Thai vowels.

After this step the `CLEAN` stage reads `raw_text` of every row in `academic_document_pages` and writes `cleaned_text` and `cleaned_char_count`. `raw_text` is never changed.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 45, 46 and 47, and the stage contract of spec file 46, section 6.
2. Inspect `AcademicDocumentPage`, its repository and `ExtractTextStage`.
3. Read this whole file before writing code: the rules depend on their order.
4. Run the tests and the build.

Do not:

- Call an AI model, a translation service or any network service.
- Translate, summarize, paraphrase, correct spelling, change letter case, remove stop words or remove numbers.
- Use Unicode NFKC or NFKD normalization (section 7 explains why).
- Change `raw_text`, `raw_char_count`, `unit_type`, `unit_label` or `page_number`.
- Clean from `cleaned_text`. The stage always starts from `raw_text`.
- Add a dependency. The JDK (`java.text.Normalizer`, `Character.UnicodeScript`, `java.util.regex`) is enough.
- Log or put into an error message any document text.

---

## 3. Principles

1. **Meaning is never changed.** Only layout, invisible characters and repeated page furniture are touched.
2. **Deterministic.** The same raw text and the same configuration always give the same cleaned text.
3. **Conservative.** When a rule is not sure, the text stays as it is.
4. **Aware of scripts without word spaces.** Thai, Lao, Khmer and Myanmar text is never given extra spaces inside a sentence.
5. **Inspectable.** Raw and cleaned text can be compared through the page endpoints of spec file 47.

---

## 4. Package Structure

```text
feature/knowledge/
└── service/
    ├── KnowledgeTextNormalizer.java       (new: character normalization, section 7)
    ├── KnowledgeTextPatterns.java         (new: line classification, section 8)
    ├── KnowledgeTextCleaner.java          (new: line and page rules, sections 9 to 11)
    └── impl/
        └── CleanTextStage.java            (new, IngestionStage CLEAN)

config/
└── KnowledgeCleaningProperties.java       (new, app.knowledge.cleaning)
```

The three helper classes are pure functions over strings: no database, no Spring Security, no I/O. They may be plain final classes or simple components, whichever matches the existing code style. `KnowledgeTextNormalizer` is used again by spec file 52 (search queries) and `KnowledgeTextPatterns` by spec file 49 (chunking).

---

## 5. Configuration

```properties
# Step 14D: cleaning of extracted knowledge document text.
app.knowledge.cleaning.unwrap-lines=${KNOWLEDGE_CLEANING_UNWRAP_LINES:true}
app.knowledge.cleaning.remove-repeated-lines=${KNOWLEDGE_CLEANING_REMOVE_REPEATED_LINES:true}
app.knowledge.cleaning.thai-digits-to-ascii=${KNOWLEDGE_CLEANING_THAI_DIGITS_TO_ASCII:true}
```

| Property | Meaning |
|---|---|
| `unwrap-lines` | Join the hard-wrapped lines of a PDF page into paragraphs (section 10.4). |
| `remove-repeated-lines` | Remove headers and footers that repeat across PDF pages (section 10.2). |
| `thai-digits-to-ascii` | Write Thai digits ๐ to ๙ as 0 to 9 (section 7). |

`thai-digits-to-ascii` also applies to search queries (spec file 52). It must have the same value for documents and queries. After changing any of the three values, every document needs a `FULL` reprocess.

Add the variables to `.env.example` and to the `backend` service in `compose.yaml`.

The numeric thresholds of sections 9 and 10 are constants in `KnowledgeTextCleaner`, each with a comment. `KnowledgeTextCleaner.VERSION = 1` is logged with every run; raise it whenever a rule changes.

---

## 6. Stage Flow

`CleanTextStage` implements `IngestionStage` with `type() = CLEAN`. It runs while the processing status is `PROCESSING`.

```text
1. control.checkpoint()
2. Pass 1 (only when the file has at least 4 PAGE units with text and remove-repeated-lines is on):
   read the pages in order, 50 at a time, and collect the repeated header and footer lines (section 10.2).
3. Pass 2: read the pages in order, 50 at a time. Clean each page (section 11).
   Write cleaned_text and cleaned_char_count for the batch in one transaction. control.checkpoint().
4. Fail with DOCUMENT_TEXT_EMPTY when no page contains a letter or a digit after cleaning.
5. Fail with DOCUMENT_TEXT_UNREADABLE when the text is mostly undecodable (section 13).
```

- A page whose cleaned text is empty is stored as the empty string with count 0. It is not an error.
- The stage is repeatable: it overwrites the cleaned columns of every page of the file.
- Pass 1 keeps only a few short lines per page in memory, never the whole document.
- No transaction is open while text is being cleaned.

---

## 7. Character Normalization

`KnowledgeTextNormalizer.normalize(String)` applies these steps in this order:

| # | Step | Detail |
|---|---|---|
| 1 | Unicode NFC | `Normalizer.normalize(text, Form.NFC)`. |
| 2 | Line separators | U+2028, U+2029, U+0085 and U+000B become `\n`. |
| 3 | Spaces | Tab and every Unicode space separator (U+00A0, U+1680, U+2000 to U+200A, U+202F, U+205F, U+3000) become U+0020. |
| 4 | Zero width space | U+200B becomes a space when both neighbours are Latin letters or digits; otherwise it is removed. |
| 5 | Invisible characters | Removed: U+00AD (soft hyphen), U+200E, U+200F, U+202A to U+202E, U+2060 to U+2064, U+2066 to U+2069, U+FEFF. **Kept:** U+200C and U+200D (they change the shape of letters in some scripts). |
| 6 | Latin ligatures | ﬀ → ff, ﬁ → fi, ﬂ → fl, ﬃ → ffi, ﬄ → ffl, ﬅ → st, ﬆ → st (U+FB00 to U+FB06). |
| 7 | Thai SARA AM | NIKHAHIT (U+0E4D) and SARA AA (U+0E32), with an optional tone mark (U+0E48 to U+0E4B) before or between them, become the tone mark (if any) followed by SARA AM (U+0E33). |
| 8 | Thai glyph variants | Private Use characters U+F700 to U+F71A (shifted Thai vowels and tone marks used by some Windows fonts) are mapped to their standard Thai characters. Take the mapping from a published source, cite it in a code comment, and do not guess. |
| 9 | Thai digits | When `thai-digits-to-ascii` is on: U+0E50 to U+0E59 become `0` to `9`. Myanmar digits are **not** converted. |
| 10 | Hyphens | U+2010 and U+2011 become `-`. U+2012, U+2013 and U+2212 become `-` only between two ASCII digits (`2024–2025` → `2024-2025`). Other dashes are kept. |
| 11 | Other Private Use characters | A remaining Private Use character (U+E000 to U+F8FF) at the start of a line is a symbol-font bullet and becomes `-`. Anywhere else it is removed. |
| 12 | Control characters | Every remaining control character except `\n` is removed. |

U+FFFD (the replacement character) is kept; it shows that the source could not be decoded.

**Why not NFKC.** NFKC "simplifies" compatibility characters, and that damages this text: it splits Thai SARA AM (U+0E33) into two characters, turns `m²` into `m2`, and rewrites other characters the documents use on purpose. Steps 3, 6 and 7 fix the compatibility characters that actually cause problems, explicitly.

**Why Thai digits are converted.** Official Thai documents often print numbers as ๑๒๐. Students ask with 120 and the model answers with 120. Writing all numbers the same way lets the search (spec file 52) and the answer check (spec file 53) compare them. The original digits stay in `raw_text`.

**Why Myanmar digits are not converted.** In typed Myanmar text the digit ၀ (U+1040) and the letter ဝ (U+101D) are often used for each other. Converting would damage words.

`normalize` is idempotent: `normalize(normalize(x))` equals `normalize(x)`.

---

## 8. Line Classification

`KnowledgeTextPatterns.classify(String line)` looks at one trimmed, normalized line:

| Class | Rule | Examples |
|---|---|---|
| `MARKER_HEADING` | Starts with one to six `#` and a space (spec file 47, section 7). | `# Chapter 2 Registration` |
| `KEYWORD_HEADING` | Starts with a heading keyword followed by a number (digits or a Roman numeral) or, for the appendix keywords, a single letter. The whole line has at most 150 characters and does not end with `.`, `,` or `;`. | `Chapter 2 Registration`, `SECTION IV`, `Part 1: General`, `Appendix A`, `หมวด 6 การลาพักการศึกษา`, `หมวดที่ 3`, `บทที่ 2 การลงทะเบียน`, `ส่วนที่ 1`, `ภาคผนวก ก` |
| `NUMBERED_HEADING` | Starts with a multi-level number (two to five levels, each one or two digits without a leading zero) and a space. The whole line has at most 100 characters and does not end with `.`, `,`, `;` or `:`. | `2.1 Add and Drop`, `3.1.1 จำนวนหน่วยกิต` |
| `CLAUSE` | Starts with a clause keyword followed by a number. | `Article 5. A student may …`, `ข้อ 13 นักศึกษา…`, `มาตรา 5` |
| `LIST_ITEM` | Starts with a bullet (`-`, `•`, `*`, `–`, `▪`, `●`, `○`, `◦`) and a space, or with a short label and a space: `1.` `1)` `(1)` `a.` `a)` `(a)` `iv.` `(iv)` `ก.` `(ก)`. | `- Tuition fee`, `(2) Late registration`, `ก. หมวดวิชาศึกษาทั่วไป` |
| `TABLE_ROW` | Contains ` | `. | `CS301 | Algorithms | 3` |
| `TEXT` | Everything else. | |

Keyword lists (constants, matched at the start of the line; Latin keywords ignore case):

- Heading keywords: `Chapter`, `Section`, `Part`, `Appendix`, `Annex`, `หมวด`, `หมวดที่`, `ส่วนที่`, `บทที่`, `ตอนที่`, `ภาคผนวก`.
- Clause keywords: `Article`, `Clause`, `ข้อ`, `มาตรา`.

Important negative examples (all are `TEXT`):

| Line | Why |
|---|---|
| `120 credits are required by the curriculum` | A number alone is not a list label. |
| `2.00 or higher in every semester` | `00` has a leading zero, so this is not a multi-level number. |
| `หมวดวิชาศึกษาทั่วไป 30 หน่วยกิต` | `หมวด` is not followed by a number. |
| `ข้อมูลนักศึกษา` | `ข้อ` is not followed by a number. |
| `Section 3 of these regulations applies to all students.` | Ends with a full stop. |

Two helper predicates are built on the classes:

- `startsBlock(line)`: the class is anything except `TEXT`.
- `isHeading(line)`: the class is `MARKER_HEADING`, `KEYWORD_HEADING` or `NUMBERED_HEADING`.

A `CLAUSE` is a block start but **not** a heading: "ข้อ 13 …" is the rule itself, not a title above it.

---

## 9. Line Rules for All Unit Types

After character normalization the text is split into lines.

1. **Trim** every line and collapse runs of spaces inside it to one space. Before trimming, remember for each line whether it ended with a space (needed in section 10.4).
2. **Decoration lines** are removed: a line that consists only of three or more punctuation or symbol characters (`-----`, `_____`, `=====`, `*****`, `. . . .`). A `MARKER_HEADING` is never a decoration line.
3. **Dot leaders** are collapsed: four or more dots in a row, with or without spaces between them, become one space (`Chapter 5 Graduation ........ 12` → `Chapter 5 Graduation 12`). The line itself is kept.
4. **Blank lines**: two or more blank lines in a row become one. Blank lines at the start and the end of a unit are removed.

In a `TABLE_ROW`, each cell is trimmed and the separator is written as ` | ` again. A row whose cells are all empty is removed.

---

## 10. Rules for PDF Pages Only

These rules apply to `PAGE` units. DOCX sections, sheets and text blocks have real paragraphs and no page furniture, so they are left alone.

### 10.1 Page-number lines

Among the first two and the last two non-empty lines of a page, a line that is only a page number is removed:

| Pattern | Examples |
|---|---|
| digits only (1 to 4) | `12`, `๑๒` |
| digits between dashes | `- 12 -`, `– ๕ –` |
| page word and digits | `Page 12`, `page 12 of 40`, `หน้า 12`, `หน้าที่ ๓`, `หน้า 3 จาก 40` |
| digits, slash, digits | `12 / 40`, `12/40` |

Both ASCII and Thai digits match, whatever `thai-digits-to-ascii` is set to. A number alone on a line in the middle of a page is kept.

### 10.2 Repeated headers and footers

Pass 1 finds lines that are printed on most pages:

1. For every page that has text, apply section 7, section 9 and section 10.1, then take its first two and its last two non-empty lines (top zone and bottom zone).
2. Build a key for each of these lines by replacing every run of digits with `#`. Ignore lines whose key has fewer than three letters.
3. A key that occurs in the same zone on at least 60 percent of the pages with text, and on at least three pages, is a **repeated line**.

Pass 2 removes a repeated line from the zone where it was found on every page **except the first page on which it appears**. Keeping the first occurrence preserves a document title that is used as running header, and the column header of a table that continues over many pages.

The rule needs at least four pages with text. Shorter documents are not checked.

### 10.3 Words split by a hyphen

When a line ends with a lower-case Latin letter followed by `-`, and the next line is a `TEXT` line that starts with a lower-case Latin letter, the two lines are joined without the hyphen: `regis-` + `tration` → `registration`.

This is always applied, whatever the line length. It can join a real compound (`well-` + `known` → `wellknown`); this is accepted.

### 10.4 Unwrapping lines

A PDF stores visual lines. This rule joins the lines of one paragraph again (only when `unwrap-lines` is on).

Reference length of a page: sort the lengths of its `TEXT` lines in ascending order and take the value at the 90th percentile (index `ceil(0.9 × n) − 1`). The page is **not** unwrapped when it has fewer than three `TEXT` lines or when the reference length is below 40 characters (such pages are usually tables or lists).

Two consecutive non-blank lines A and B are joined when all of these hold:

1. A is not a heading and not a `TABLE_ROW`.
2. B is a `TEXT` line (it does not start a block).
3. The physical line A has at least 70 percent of the reference length. A shorter line is the last line of its paragraph.

The comparison in point 3 always uses the length of the original line, never the length of an already joined result.

How they are joined:

| Case | Joined with |
|---|---|
| A ends with a letter or digit followed by `-` (not handled by 10.3) | nothing, the hyphen stays: `2024-` + `2025` → `2024-2025` |
| The last character of A and the first character of B both belong to a script written without word spaces (Thai, Lao, Khmer, Myanmar, Han, Hiragana, Katakana) and line A did not end with a space | nothing |
| Everything else | one space |

Use `Character.UnicodeScript.of(codePoint)` for the script. Digits, punctuation and spaces are not letters of these scripts, so a Thai line that ends with a number is joined with a space.

A paragraph that continues on the next page is not joined across the page boundary.

Unwrapping is a heuristic. Its known weak spot is a PDF page that consists of long table rows: rows can be joined into one line. The text is still complete and in order.

---

## 11. Rules per Unit Type

| Rule | `PAGE` | `SECTION` | `SHEET` | `TEXT` |
|---|---|---|---|---|
| Character normalization (7) | yes | yes | yes | yes |
| Trim, spaces, blank lines (9) | yes | yes | yes | yes |
| Decoration lines, dot leaders (9) | yes | yes | no | yes |
| Page-number lines (10.1) | yes | no | no | no |
| Repeated headers and footers (10.2) | yes | no | no | no |
| Hyphen-split words (10.3) | yes | no | no | no |
| Unwrapping (10.4) | yes | no | no | no |

Order for one `PAGE` unit:

```text
7 normalize → split into lines → 9 trim and spaces → 9 decoration lines and dot leaders
  → 10.1 page numbers → 10.2 repeated lines → 10.3 hyphen-split words → 10.4 unwrapping → 9 blank lines
```

---

## 12. Format of Cleaned Text

Spec file 49 relies on this format:

- Lines are separated by `\n`. No `\r`, no tab, no control character.
- No line has leading or trailing spaces; no two spaces in a row.
- At most one blank line in a row; none at the start or end.
- A heading written by the extractor keeps its `#` marker.
- Table rows keep the ` | ` separator.

`cleaned_char_count` is the length of this text.

---

## 13. Quality Checks and Error Codes

Processing codes of this stage (`failed_stage = CLEAN`):

| Code | Retryable | When | Safe message |
|---|---|---|---|
| `DOCUMENT_TEXT_EMPTY` | no | No page contains a letter or digit after cleaning. | "No usable text remained after cleaning." |
| `DOCUMENT_TEXT_UNREADABLE` | no | More than 20 percent of the non-space characters of the file are U+FFFD or Private Use characters that step 11 of section 7 had to remove. | "The document text could not be decoded. Upload a different version of the file." |
| `DOCUMENT_TEXT_CLEANING_FAILED` | no | Any unexpected error inside the cleaner. | "The document text could not be cleaned." |

`DOCUMENT_TEXT_UNREADABLE` catches PDFs whose fonts have no usable Unicode table. It cannot catch every kind of garbled text; an Admin should look at the cleaned preview of an important document once.

No new HTTP error code.

---

## 14. Effect on Existing Endpoints

- `GET /{id}/pages` now fills `cleanedCharCount`, and `preview` shows the cleaned text.
- `GET /{id}/pages/{pageNumber}` returns `cleanedText` next to `rawText`.
- Until spec file 49 exists, every job ends as `FAILED` with `failedStage: "CHUNK"` and `INGESTION_STAGE_NOT_AVAILABLE`. Pages with raw and cleaned text are kept.
- No new endpoint.

---

## 15. Logging

```text
KNOWLEDGE_CLEAN documentId=31 fileId=81 version=1 pages=42 rawChars=118233 cleanedChars=109870 pageNumberLines=42 repeatedLines=80 joinedLines=1630 hyphenJoins=12 undecodable=0 durationMs=310
```

Counts only. Never a line of text, not even a removed header.

---

## 16. Tests

### 16.1 `KnowledgeTextNormalizer` (unit tests, literal strings)

Write the inputs with `\u` escapes so the test source shows the exact code points.

| Input | Expected |
|---|---|
| `e` + U+0301 | `é` (NFC) |
| `m²` | unchanged (no NFKC) |
| `ﬁnal ofﬃce` | `final office` |
| `A` U+00A0 `B`, `A` tab `B` | `A B` |
| `ab` U+200B `cd` | `ab cd` |
| Thai `นัก` U+200B `ศึกษา` | `นักศึกษา` |
| `co` U+00AD `operate` | `cooperate` |
| U+FEFF at the start, U+200E inside | removed |
| U+200C and U+200D inside a word | kept |
| U+0E17 U+0E4D U+0E32 | U+0E17 U+0E33 (`ทำ`) |
| U+0E19 U+0E4D U+0E49 U+0E32 | U+0E19 U+0E49 U+0E33 (`น้ำ`) |
| U+0E19 U+0E49 U+0E4D U+0E32 | U+0E19 U+0E49 U+0E33 (`น้ำ`) |
| `๑๒๐ หน่วยกิต` | `120 หน่วยกิต` (and unchanged when the flag is off) |
| a Myanmar sentence with Myanmar digits | unchanged |
| `2024–2025`, `09:00–12:00` | `2024-2025`, `09:00-12:00` |
| `Monday – Friday` | unchanged |
| a Private Use character at line start followed by text | `- ` + text |
| U+0000 to U+001F except `\n` | removed, `\n` kept |
| any of the above, normalized twice | same as normalized once |

### 16.2 `KnowledgeTextPatterns` (unit tests)

Every example and every negative example of section 8, plus: Roman numerals (`Chapter IV`), lower and upper case keywords, `หมวดที่ ๓` after digit conversion, a 151-character keyword line (`TEXT`), `1.1` at the start of a 120-character sentence (`TEXT`).

### 16.3 `KnowledgeTextCleaner` (unit tests)

- Spaces, decoration lines, dot leaders and blank lines (section 9).
- Page numbers: every pattern of 10.1 at the top and at the bottom; a number in the middle of the page is kept.
- Repeated lines: five pages with the same header and a footer that contains the page number. The header and footer are removed from pages 2 to 5 and kept on page 1. With three pages nothing is removed.
- A line that repeats on only two of five pages is kept.
- Hyphen: `regis-` / `tration` → `registration`; `Well-` / `Known` keeps the hyphen; `2024-` / `2025` → `2024-2025`.
- Unwrapping English: three full lines and a short last line become one line; the next paragraph stays separate.
- Unwrapping Thai: two full Thai lines are joined without a space; with a trailing space on the first line they are joined with one space.
- A heading line, a clause line and a list item are never appended to the line before them; a line is never appended to a heading.
- A page with fewer than three text lines, and a page with a reference length below 40, is not unwrapped.
- `SECTION`, `SHEET` and `TEXT` units are not unwrapped and lose no line.
- With `unwrap-lines=false` and `remove-repeated-lines=false` those two rules do nothing.
- The output always satisfies the format of section 12.

### 16.4 Stage tests (Testcontainers)

Build `DocumentIngestionServiceImpl` by hand with the real `ExtractTextStage` and `CleanTextStage` only.

- After `runOnce()` every page has `cleaned_text` and `cleaned_char_count`; `raw_text` is byte-for-byte what it was; the job is `FAILED` at `CHUNK` with `INGESTION_STAGE_NOT_AVAILABLE`.
- A generated PDF with a repeated header, page numbers and wrapped paragraphs is cleaned as expected.
- Running the job twice gives identical cleaned text.
- A file whose pages contain only page numbers fails with `DOCUMENT_TEXT_EMPTY` and `failed_stage = CLEAN`.
- A file with mostly U+FFFD characters fails with `DOCUMENT_TEXT_UNREADABLE`.
- The page endpoints return `cleanedText`, and the list preview comes from the cleaned text.
- No log line and no stored error message contains document text.

---

## 17. Postman Verification

Use the sample document of spec file 47, section 19.

**1. Process again**

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

Expected while spec file 49 is not implemented: `status: "FAILED"`, `failedStage: "CHUNK"`, `lastErrorCode: "INGESTION_STAGE_NOT_AVAILABLE"`, `pageCount: 1`.

**3. Compare raw and cleaned text**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{documentId}/pages/1
Authorization: Bearer <admin-access-token>
```

Expected: `cleanedText` is not null, has no double blank lines, and still contains "at least 120 credits" and "ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน".

**4. A real PDF**

Upload a text-based PDF that has a header or footer and page numbers on every page, reprocess it, then read page 2 and page 3.

Expected: in `cleanedText` the page number and the repeated header or footer are gone, paragraphs are single lines, and no sentence was lost compared with `rawText`.

**5. Thai digits**

Upload a TXT file that contains the line `ต้องสอบได้ไม่น้อยกว่า ๑๒๐ หน่วยกิต`, reprocess, read page 1.

Expected: `rawText` contains `๑๒๐`, `cleanedText` contains `120`.

---

## 18. Acceptance Criteria

- [ ] `CleanTextStage` is the only bean for `CLEAN` and follows the stage contract of spec file 46.
- [ ] `raw_text` is never modified; cleaning always starts from it.
- [ ] Character normalization follows section 7 in the given order, uses NFC, and is idempotent.
- [ ] Thai SARA AM is repaired; Thai glyph variants are mapped from a cited source.
- [ ] Thai digits are converted only when the flag is on; Myanmar digits never.
- [ ] Line classification matches every example of section 8.
- [ ] Page numbers and repeated headers and footers are removed from PDF pages only, and the first occurrence of a repeated line is kept.
- [ ] Unwrapping never adds a space inside Thai, Lao, Khmer or Myanmar text and never touches headings, clauses, list items or table rows.
- [ ] Cleaned text satisfies the format of section 12.
- [ ] Empty and unreadable results fail with the codes of section 13.
- [ ] No AI model, no network call, no new dependency.
- [ ] No document text in logs or error messages.
- [ ] `KNOWLEDGE_CLEANING_*` variables documented in `.env.example` and passed in `compose.yaml`.
- [ ] Tests of section 16 pass; no new failure compared with the baseline.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 19. Agent Completion Report

Report: files created and modified; the rule order as implemented; the source used for the Thai glyph variant mapping; thresholds in effect; before and after examples for one English and one Thai page (from generated fixtures, not from real documents); known weak spots observed (tables in PDFs, table-of-contents lines, multi-column pages); changed existing tests; test totals against the baseline; Postman results actually performed; decision entries added to `00-changes-and-decision.md`.

Do not claim that documents are searchable: only extraction and cleaning exist.

**Next: spec file 49 — Step 14E: Split into Chunks.**
