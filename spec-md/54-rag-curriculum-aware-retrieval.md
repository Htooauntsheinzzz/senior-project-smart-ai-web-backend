# 54 — Step 15B: Connect Curriculum RAG to the AI Assistant — Curriculum-Aware Retrieval

**Project:** Smart AI University Student Assistant
**Module:** AI Knowledge Base search → passages for the Student AI Assistant
**Scope:** Make the passages that the assistant receives fit curriculum questions: say whom a passage applies to, keep room for the Student's own program among university-wide passages, and add the neighbouring part of a section that was split into two chunks. A sample curriculum document for development.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Step 14 (spec files 45 to 52) and spec file 53 are implemented.
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Roadmap: Step 15: 53 knowledge search tool → **54 curriculum-aware retrieval (this file)** → 55 curriculum questions. Step 16: 56 academic progress tool → 57 hybrid answers.

---

## 1. Objective

```text
Question → AI → RAG Search → Relevant Curriculum Chunks → LLM → Answer
                                   ↑
                              this file
```

Spec file 52 returns the passages that are closest to a question. That is enough for a regulation, but not for a curriculum, for three reasons:

| Problem | Example with the sample documents | Solution |
|---|---|---|
| The model cannot see whom a passage applies to. | The university regulations say "at least 120 credits", the program curriculum says "at least 132 credits". Both are correct, for different readers. | Every passage says whom it applies to: `appliesTo` is "your program", "your department" or "all students" (section 6). |
| University-wide documents can take every place. | With a regulations document and a handbook uploaded, "When must tuition be paid?" fills all four places with university-wide passages, and the program curriculum never reaches the model. | Up to `own-program-quota` places are kept for passages of the Student's own program, when they reach the threshold (section 7). |
| A list can be split into two chunks. | The 24 major electives of the sample curriculum are stored in chunks 3 and 4. A question that matches the end of the list finds chunk 4 only, and the model lists half of the electives. | The chunk before or after a hit is added when it continues the same section (section 8). |

After this step the assistant receives, for every curriculum question, passages that say whom they apply to, include the Student's own program when it has an answer, and contain whole lists. Spec file 55 writes the rules for how the model uses them.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec file 49 (sections 7, 9 and 15), spec file 52 (sections 8 to 13), spec file 53 (sections 5, 7, 13 and 14) and the decision entries of the Step 14 and spec file 53 implementations in `00-changes-and-decision.md`.
2. Inspect `KnowledgeSearchServiceImpl`, `PgVectorKnowledgeVectorStore`, `KnowledgeSearchHit`, `KnowledgeChunkMatch`, `KnowledgeSearchFilter`, the three Admin search DTOs, the `search_university_knowledge` case of `AcademicAiToolExecutorImpl`, `AcademicAiToolResultMapper.knowledgePassage` and `AssistantKnowledgeSettings`.
3. Record the test baseline before changing anything.

Do not:

- Change the passages, their order, ranks or scores for a call without the new options. `studentSearch(query, scope, topK)` and an Admin request without the two new fields return the same passages as before. The only difference is the new `audience` and `neighbor` values.
- Add a migration. `curriculum_versions.program_id` already exists.
- Give the model a new tool argument. The quota and the neighbours are configuration.
- Put a program id, a document id or any other id into the passage map.
- Read a neighbour without the eligibility conditions of spec file 52, section 8.
- Add keyword search, a second ranking model or result caching.

---

## 3. What Stays and What Changes

| Stays | Changes |
|---|---|
| The search statement of spec file 52, its eligibility rules and the Student scope | One more column (`curriculum_program_id`) and one more optional condition (section 7.2) |
| Threshold, limit per document, `topK` and ranks of spec file 52 | A reserve for the Student's own program before the places are filled (section 7) |
| `studentSearch(query, scope, topK)` | A second `studentSearch` with `StudentSearchOptions` (section 9) |
| The passage map of spec file 53 | `appliesTo` is added (section 10.1) |
| The context budget of spec file 53 | Hits first, neighbours afterwards (section 10.2) |
| The Admin search endpoint | Two optional request fields and two result fields (section 11) |
| `sources` of the chat response | Nothing: a neighbour belongs to the same document as its hit |

---

## 4. Package Structure

```text
feature/knowledge/
├── dto/
│   ├── KnowledgeSearchRequest.java                (+ ownProgramQuota, maxNeighborPassages)
│   ├── KnowledgeSearchResponse.java               (+ ownProgramQuota, maxNeighborPassages)
│   └── KnowledgeSearchResultResponse.java         (+ audience, neighbor)
└── service/
    ├── KnowledgeAudience.java                     (new enum: PROGRAM, DEPARTMENT, UNIVERSITY)
    ├── KnowledgeChunkMatch.java                   (+ curriculumProgramId)
    ├── KnowledgeChunkPosition.java                (new record: fileId, chunkIndex)
    ├── KnowledgeSearchFilter.java                 (+ programDocumentsOnly)
    ├── KnowledgeSearchHit.java                    (+ audience, neighbor)
    ├── KnowledgeSearchService.java                (+ studentSearch with StudentSearchOptions)
    ├── KnowledgeVectorStore.java                  (+ findChunks)
    ├── StudentSearchOptions.java                  (new record)
    └── impl/
        ├── KnowledgeSearchServiceImpl.java        (audience, reserve, neighbours)
        └── PgVectorKnowledgeVectorStore.java      (new column, new condition, findChunks)

feature/assistant/
├── mapper/
│   └── AcademicAiToolResultMapper.java            (+ appliesTo)
└── service/
    ├── AssistantKnowledgeSettings.java            (+ ownProgramQuota, maxNeighborPassages)
    └── impl/
        └── AcademicAiToolExecutorImpl.java        (search options, context budget in two passes)

config/
└── AiAssistantKnowledgeProperties.java            (+ two properties)

docker/dev-fixtures/
└── rag-sample-curriculum-cs.txt                   (new, section 13)
```

---

## 5. Configuration

`application.properties`, next to the properties of spec file 53:

```properties
# Step 15B: curriculum-aware retrieval for the assistant.
app.ai.knowledge.own-program-quota=${AI_KNOWLEDGE_OWN_PROGRAM_QUOTA:2}
app.ai.knowledge.max-neighbor-passages=${AI_KNOWLEDGE_MAX_NEIGHBOR_PASSAGES:2}
```

| Property | Meaning | Validation |
|---|---|---|
| `own-program-quota` | Places of one search that are kept for passages of the Student's own program (section 7). `0` turns the reserve off. | 0 to 10 |
| `max-neighbor-passages` | Neighbouring chunks that one search may add (section 8). `0` turns neighbours off. | 0 to 4 |

Add both to `AiAssistantKnowledgeProperties`. If tests of spec file 53 build that record by hand, keep a constructor with the four earlier values (it means 0 and 0). Add `ownProgramQuota()` and `maxNeighborPassages()` to `AssistantKnowledgeSettings`; `inactive()` returns 0 for both.

Add the two variables to `.env.example` and to the `backend` service in `compose.yaml`.

A quota larger than `top-k` is allowed and acts as `top-k`.

---

## 6. Audience of a Passage

Every passage of a search with a Student scope gets an **audience**: whom the passage applies to, seen from that Student.

```java
public enum KnowledgeAudience { PROGRAM, DEPARTMENT, UNIVERSITY }
```

| Audience | When (checked in this order) |
|---|---|
| `PROGRAM` | `scope.programId` is not null, and the document's `program_id` or the `program_id` of its curriculum version equals it |
| `DEPARTMENT` | the document has a `department_id` |
| `UNIVERSITY` | otherwise |

- The audience is computed in `KnowledgeSearchServiceImpl` from the row and the scope. It needs the program of the curriculum version, so the search statement of spec file 52 selects one more column, `cv.program_id AS curriculum_program_id`, and `KnowledgeChunkMatch` gets one more component, `Long curriculumProgramId`. Keep a constructor with the earlier 19 components; it sets `curriculumProgramId` to null.
- A search without a Student scope (the Admin view) has no audience: `null`.
- The audience never widens what a Student may see. It only describes passages that already passed the scope condition of spec file 52, section 9.
- A document linked to a department and to another program of that department reaches the Student through the department link (spec file 52, section 9). Its audience is `DEPARTMENT`.

---

## 7. Reserve for the Student's Own Program

### 7.1 Rule

Only for a Student search (or an Admin preview, section 11) with `scope.programId` set and a quota greater than 0:

```text
1. general  = vectorStore.search(vector, model, filter, candidateLimit)                          spec file 52, unchanged
2. program  = vectorStore.search(vector, model, filter.programDocuments(programId), candidateLimit)
3. merged   = general and program together, each chunk once (same chunkId), ordered by distance, then chunkId
4. Drop every candidate with score < minScore.                                                      as spec file 52
5. A. Reserve: go through merged in order. Keep a candidate whose audience is PROGRAM, while fewer than
      min(quota, topK) candidates are kept and fewer than max-per-document of its document are kept.
6. B. Fill:    go through merged in order. Keep a candidate that is not kept yet, while fewer than topK
      candidates are kept and fewer than max-per-document of its document are kept.
7. C. Order:   sort the kept candidates by distance, then chunkId. rank = 1, 2, 3 …
```

With `scope.programId` null, or with a quota of 0, steps 2 and 5 are skipped. The result is then exactly the result of spec file 52, section 11, and the second statement is not run.

- The reserve chooses only among candidates that reach `min-score`. It never adds a passage below the threshold, so a question that the program documents cannot answer gets no program passage.
- The reserve does not move a program passage to the top. Step C sorts by distance again, so the ranks still go from the closest passage to the farthest.
- `max-per-document` still holds for every document. Skipping it for a search limited to one document (`documentId`) stays as in spec file 52.

### 7.2 Why a second statement

The first statement reads only the `candidateLimit` nearest chunks of all eligible documents (20 for `topK` 4). With many university-wide chunks near the question, the program's chunks may not be among them at all, even when they reach the threshold. The second statement reads the nearest chunks of the Student's own program documents only.

It is the same statement, with one more optional fragment. `KnowledgeSearchFilter` gets one more component, `Long programDocumentsOnly`, and a method that returns a copy with it set:

```java
public record KnowledgeSearchFilter(StudentKnowledgeScope studentScope, Long categoryId, String academicYearLabel,
        Long documentId, Long programDocumentsOnly) {

    /** Spec file 52 call sites: no program limit. */
    public KnowledgeSearchFilter(StudentKnowledgeScope studentScope, Long categoryId, String academicYearLabel, Long documentId) {
        this(studentScope, categoryId, academicYearLabel, documentId, null);
    }

    /** The same filter, limited to documents of this program: its own link or a curriculum version of the program. */
    public KnowledgeSearchFilter programDocuments(long programId) { … }
}
```

| Filter value | Fragment |
|---|---|
| `programDocumentsOnly` | `AND (d.program_id = :programDocumentsOnly OR cv.program_id = :programDocumentsOnly)` |

The fragment is added to the others; the scope fragment stays. Both statements use the same pgvector settings, the same eligibility conditions and the same rules as spec file 52, section 10. They run one after the other, each in its own short read-only transaction, and no transaction is open while the embedding provider is called. A document that is archived between the two statements can still come from the first one; that is the same as a search a moment earlier.

Cost: one more short SQL statement per Student search with a program. No second embedding request: both statements use the same query vector.

### 7.3 Example

`topK` 4, `minScore` 0.50, `max-per-document` 3, quota 2. Documents A and B are university-wide, C is the curriculum of the Student's program.

| Candidate | Document | Score | In list |
|---|---|---:|---|
| a1 | A | 0.81 | general |
| b1 | B | 0.78 | general |
| a2 | A | 0.74 | general |
| b2 | B | 0.70 | general |
| a3 | A | 0.66 | general |
| c1 | C | 0.63 | program |
| c2 | C | 0.58 | program |
| c3 | C | 0.41 | program |

| | Result |
|---|---|
| Quota 0 (spec file 52) | a1, b1, a2, b2 |
| Quota 2 | a1, b1, c1, c2 (c3 is below `minScore`) |
| Quota 2, and c1 and c2 are below `minScore` | a1, b1, a2, b2 |

Turn this table into a unit test with a fake `KnowledgeVectorStore`.

---

## 8. Neighbour Passages

### 8.1 Rule

After the hits are chosen (section 7, or spec file 52 without a reserve), and only when `maxNeighborPassages` is greater than 0:

```text
budget = maxNeighborPassages
for every hit h, in rank order:
    skip h when its section title is null
    for the chunk before h (chunkIndex − 1), then the chunk after h (chunkIndex + 1), in the same file:
        stop when budget is 0
        it is a neighbour of h when
            it is eligible (read with findChunks, section 8.2),
            its section title is exactly the section title of h,
            and it is neither a hit nor a neighbour already added
        add it; budget = budget − 1
```

- A neighbour is the continuation of the same section in the same file revision. Spec file 49 gives two chunks the same section title only when a section was too long for one chunk, or when no new heading came between them.
- A neighbour has no score of its own. It carries the **rank and score of its hit** and `neighbor = true`. Its audience is the audience of its document, like any passage.
- Neighbours do not count for `topK` or `max-per-document`. They are limited by `max-neighbor-passages` only.
- The returned list keeps the reading order: the neighbour before a hit comes directly before it, the neighbour after a hit directly after it.

### 8.2 Reading the neighbours

Add to `KnowledgeVectorStore`:

```java
/** The chunks at these positions that are eligible (spec file 52, section 8), ordered by file and chunk index.
 *  Runs in its own read-only transaction. An empty list runs no statement. */
List<KnowledgeChunkMatch> findChunks(String model, List<KnowledgeChunkPosition> positions);

public record KnowledgeChunkPosition(long fileId, int chunkIndex) {}
```

```sql
SELECT c.id AS chunk_id, c.chunk_index, c.section_title, c.page_start, c.page_end, c.content,
       f.id AS file_id, f.revision_number, f.file_extension,
       d.id AS document_id, d.title, d.academic_year_label, d.category_id,
       d.curriculum_version_id, d.program_id, d.department_id, cv.program_id AS curriculum_program_id,
       dc.category_code, dc.category_name
FROM academic_document_chunks c
JOIN academic_document_files f      ON f.id = c.file_id
JOIN academic_document_processing p ON p.file_id = f.id
JOIN academic_documents d           ON d.id = f.document_id
LEFT JOIN curriculum_versions cv    ON cv.id = d.curriculum_version_id
LEFT JOIN document_categories dc    ON dc.id = d.category_id
WHERE c.embedding IS NOT NULL
  AND c.embedding_model = :model
  AND f.is_current
  AND p.status = 'READY'
  AND d.is_active
  AND NOT d.is_deleted
  AND (d.curriculum_version_id IS NULL OR cv.status = 'ACTIVE')
  AND (   (c.file_id = :fileId0 AND c.chunk_index = :chunkIndex0)
       OR (c.file_id = :fileId1 AND c.chunk_index = :chunkIndex1)
       …                                                            one fixed fragment per position
      )
ORDER BY c.file_id, c.chunk_index;
```

- The service collects the positions before and after every hit with a section title (at most 2 × `topK` = 20) and runs **one** statement for all of them. Then it applies the rule of section 8.1 to the rows that came back.
- The statement repeats every eligibility condition. A neighbour is never a chunk that may not be shown, even if its document was archived a moment ago.
- It has no vector comparison and needs no pgvector setting. `distance` of the returned matches is 0 and is never read.
- Positions are bind parameters. The number of `OR` terms follows from the number of positions; no value is written into the SQL text.

### 8.3 Example

Hits: `A#4` (section title "S4") with rank 1, `B#2` (section title "X") with rank 2. Eligible chunks around them: `A#3` ("S4"), `A#5` ("S5"), `B#1` ("X"), `B#3` ("X").

| `maxNeighborPassages` | Returned list |
|---|---|
| 0 | A#4, B#2 |
| 1 | A#3 (neighbour), A#4, B#2 |
| 2 | A#3 (neighbour), A#4, B#1 (neighbour), B#2 |
| 3 | A#3 (neighbour), A#4, B#1 (neighbour), B#2, B#3 (neighbour) |

`A#5` is never added: its section title differs. Turn this table into a unit test.

---

## 9. Service Contract

Add to `KnowledgeSearchService`:

```java
/** Search for the signed-in Student with the options of spec file 54. Used by the assistant tool. */
List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, StudentSearchOptions options);
```

```java
/** topK 1 to 10, ownProgramQuota 0 to 10, maxNeighborPassages 0 to 4; other values throw IllegalArgumentException. */
public record StudentSearchOptions(int topK, int ownProgramQuota, int maxNeighborPassages) {
    /** Spec file 52 behaviour: no reserve, no neighbours. */
    public static StudentSearchOptions plain(int topK) { return new StudentSearchOptions(topK, 0, 0); }
}
```

- The existing `studentSearch(query, scope, topK)` stays and delegates to `studentSearch(query, scope, StudentSearchOptions.plain(topK))`. Its guard, its validation and its result are unchanged.
- The guard of the new method is the guard of spec file 52, section 12: its first line is `currentStudentProvider.getCurrentStudentId()`.

`KnowledgeSearchHit` gets two more components:

```java
public record KnowledgeSearchHit(int rank, double score, long documentId, String title, String categoryName,
        String academicYearLabel, String sectionTitle, String unitType, int pageStart, int pageEnd, String content,
        KnowledgeAudience audience, boolean neighbor) {

    /** Spec file 52 call sites and tests: no audience, not a neighbour. */
    public KnowledgeSearchHit(int rank, double score, long documentId, String title, String categoryName,
            String academicYearLabel, String sectionTitle, String unitType, int pageStart, int pageEnd, String content) {
        this(rank, score, documentId, title, categoryName, academicYearLabel, sectionTitle, unitType, pageStart, pageEnd,
                content, null, false);
    }
}
```

The returned list holds the hits in rank order with their neighbours placed as in section 8.1. Every hit of a search with a scope has an audience, including the hits of the 3-argument method; only the reserve and the neighbours depend on the options.

---

## 10. The Assistant Tool

### 10.1 Executing the tool

Step 4 of spec file 53, section 7, becomes:

```text
4. hits = knowledgeSearch.studentSearch(query, scope,
          new StudentSearchOptions(settings.topK(), settings.ownProgramQuota(), settings.maxNeighborPassages()))
```

The passage map of spec file 53, section 7.1, gets one more key, between `academicYear` and `section`:

| Key | Value | Present |
|---|---|---|
| `appliesTo` | `"your program"` for `PROGRAM`, `"your department"` for `DEPARTMENT`, `"all students"` for `UNIVERSITY` | when the audience is not null (always, for a Student search) |

```json
{
  "documentTitle": "Sample B.Sc. Computer Science Curriculum 2026",
  "category": "Curriculum",
  "academicYear": "2026-2027",
  "appliesTo": "your program",
  "section": "Section 6 Graduation Requirements",
  "text": "Section 6 Graduation Requirements\nA student graduates from this program when all of these conditions are met: …"
}
```

The words are written for the model, which reads them as plain English. The map still contains no id, no score, no rank and no `neighbor` flag: a neighbour is a passage like any other, and its section shows where it belongs.

### 10.2 Context budget in two passes

Spec file 53, section 7.2, becomes:

```text
Pass 1: take the hits (neighbor = false) in rank order. Add a hit while the sum of the text lengths stays within
        max-context-chars. The first hit is always added.
Pass 2: take the neighbours in list order. Add a neighbour while the sum stays within max-context-chars, and only
        when its hit (the same rank) was added in pass 1.
Result: the added passages in the order of the returned list (section 8.1).
```

A neighbour never pushes out a hit. With the defaults (4 hits and 2 neighbours of at most 1,500 characters each, 8,000 characters of budget), pass 2 can drop a neighbour only when the chunks are long.

### 10.3 Log line

`ASSISTANT_KNOWLEDGE` of spec file 53 gets two values, counted over the passages that reached the model in this request:

```text
ASSISTANT_KNOWLEDGE requestId=… searchRequired=true searches=1 passages=4 documents=2 programPassages=2 neighborPassages=1 contextChars=2611 limitReached=false searchDemanded=false
```

---

## 11. Admin Search Endpoint

`POST /api/v1/admin/knowledge/search` (spec file 52, section 13) accepts two more optional fields, so an Admin can see exactly what the assistant receives:

| Field | Type | Rule |
|---|---|---|
| `ownProgramQuota` | integer | 0 to 10. Default 0. A value greater than 0 needs `studentScope.programId`; without it the request is 400 `VALIDATION_ERROR`. |
| `maxNeighborPassages` | integer | 0 to 4. Default 0. |

The response gets the two values that were used, at the top level, and two fields per result:

| Field | Meaning |
|---|---|
| `audience` | `PROGRAM`, `DEPARTMENT` or `UNIVERSITY`, seen from `studentScope`; `null` without `studentScope` |
| `neighbor` | `true` for a neighbour (section 8). A neighbour has the `rank` and `score` of its hit. |

`scopeType` stays: it describes the document itself, while `audience` describes it from the previewed Student.

Without the two request fields, a request returns the results of spec file 52 with `"neighbor": false` and the audience of each result. Tests of spec file 52 that compare a whole result object get the two new fields; nothing else changes.

---

## 12. Logging

The `KNOWLEDGE_SEARCH` line of spec file 52 gets three values:

```text
KNOWLEDGE_SEARCH audience=STUDENT queryChars=41 cache=HIT candidates=20 programCandidates=8 returned=4 programReserved=2 neighbors=1 topScore=0.6530 embedMs=0 dbMs=12 totalMs=14
```

| Value | Meaning |
|---|---|
| `programCandidates` | Rows of the second statement (section 7.2), `-` when it did not run |
| `programReserved` | Hits kept by step A of section 7.1 |
| `neighbors` | Neighbours added (section 8) |

`returned` still counts the hits only. Never logged: the question, a passage, a title, a program id.

---

## 13. Sample Curriculum Document

Create `docker/dev-fixtures/rag-sample-curriculum-cs.txt` (UTF-8, no BOM) with exactly this content. It is fictional and is used for the checks of spec files 54 to 57. Do not load it automatically and do not seed it through Flyway.

```text
SAMPLE DATA FOR DEVELOPMENT ONLY — NOT AN OFFICIAL UNIVERSITY DOCUMENT

Sample University, Bachelor of Science Program in Computer Science (fictional), Curriculum 2026

Section 1 Program Overview

The program leads to the degree Bachelor of Science (Computer Science). It is a four-year program with eight regular semesters. The maximum period of study is eight academic years.

A student must complete at least 132 credits to graduate from this program.

Section 2 Curriculum Structure

The 132 credits of the program are divided into three groups of courses.

Course group | Credits
General education courses | 30
Specialized courses | 96
Free elective courses | 6

The 96 credits of specialized courses are divided into four parts.

Specialized course part | Credits
Core courses | 30
Major required courses | 45
Major elective courses | 15
Cooperative education | 6

Section 3 Major Required Courses

A student must pass all major required courses. Together they are 45 credits.

Course code | Course title | Credits
CS201 | Programming Fundamentals | 3
CS202 | Object-Oriented Programming | 3
CS301 | Data Structures | 3
CS302 | Algorithm Design and Analysis | 3
CS311 | Database Systems | 3
CS321 | Operating Systems | 3
CS331 | Computer Networks | 3
CS341 | Software Engineering | 3
CSC411 | Information Security | 3
CSC421 | Artificial Intelligence | 3
CSC431 | Distributed Systems | 3
CSC452 | Mobile Programming | 3
CSC453 | Web Programming | 3
CSC481 | Senior Project 1 | 3
CSC482 | Senior Project 2 | 3

Section 4 Major Elective Courses

A student must complete at least 15 credits of major elective courses, chosen from the list below. Every course in the list is 3 credits. A course that is not in the list counts as a major elective only with written approval of the program committee. Not every elective course is offered in every semester.

Course code | Course title | Credits
CSC461 | Machine Learning | 3
CSC462 | Deep Learning | 3
CSC463 | Natural Language Processing | 3
CSC464 | Computer Vision | 3
CSC465 | Data Mining | 3
CSC466 | Big Data Analytics | 3
CSC467 | Data Visualization | 3
CSC471 | Internet of Things | 3
CSC472 | Embedded Systems | 3
CSC473 | Game Development | 3
CSC474 | Computer Graphics | 3
CSC475 | Human-Computer Interaction | 3
CSC476 | Software Testing | 3
CSC477 | DevOps Engineering | 3
CSC478 | Blockchain Technology | 3
CSC479 | Digital Forensics | 3
CSC483 | Ethical Hacking | 3
CSC484 | Network Security | 3
CSC485 | Parallel Computing | 3
CSC486 | Compiler Construction | 3
CSC487 | Quantum Computing | 3
CSC488 | Bioinformatics | 3
CSC489 | Robotics | 3
CSC492 | Selected Topics in Computer Science | 3

The program committee reviews the list of major elective courses every academic year and announces changes before the registration period.

Section 5 Cooperative Education and Internship

Every student must pass CSC490 Cooperative Education, 6 credits. Cooperative education is 16 weeks of full-time work at an approved organization, normally in the first semester of the fourth year.

To register for CSC490 a student must have earned at least 90 credits and must have a cumulative grade point average of at least 2.00.

A student who cannot take part in cooperative education may, with approval of the program committee, take CSC491 Internship, 3 credits, with at least 240 working hours in the summer session, together with one additional major elective course of 3 credits.

Section 6 Graduation Requirements

A student graduates from this program when all of these conditions are met:
(1) The student has completed at least 132 credits as required by the curriculum structure in Section 2.
(2) The student has a cumulative grade point average of at least 2.00.
(3) The student has a grade point average of at least 2.00 in the major required courses.
(4) The student has passed cooperative education or the internship option of Section 5.
(5) The student has no outstanding debt to the university.

หมวด 7 สหกิจศึกษา (สรุปภาษาไทย)

นักศึกษาทุกคนต้องผ่านรายวิชา CSC490 สหกิจศึกษา จำนวน 6 หน่วยกิต โดยปฏิบัติงานเต็มเวลา 16 สัปดาห์ในสถานประกอบการที่ได้รับอนุมัติ

นักศึกษาที่จะลงทะเบียนรายวิชา CSC490 ต้องมีหน่วยกิตสะสมไม่น้อยกว่า 90 หน่วยกิต และมีเกรดเฉลี่ยสะสมไม่ต่ำกว่า 2.00
```

Why it looks like this:

- **132 credits instead of 120.** The regulations sample of spec file 47 says 120 credits for all students. Two different numbers for two audiences are what spec file 55 must handle: the program's own rule applies to its students.
- **Course codes.** CS301, CSC452 and CSC453 are major required courses here, with the same titles as in `docker/dev-fixtures/step6-assistant-test-data.sql`, where they are Major Required courses of 3 credits. No elective code is used by any fixture, so a test can tell a curriculum list from the database.
- **The note after the elective list** gives the second part of the list more than 200 characters of body text, so the heading "Section 5 …" closes it (spec file 49, section 9). Without it, the end of the list would be stored under the title of Section 5.
- **No table row starts with a heading keyword followed by a number**, so no row is read as a heading (spec file 48, section 8).
- **The Thai section** repeats the cooperative education rule for cross-language checks.

### 13.1 Worked example

With the default configuration of spec file 49, the document must give exactly **eight** chunks:

| `chunkIndex` | `sectionTitle` | `charCount` | Starts with |
|---:|---|---:|---|
| 0 | `Section 1 Program Overview` | 450 | `SAMPLE DATA FOR DEVELOPMENT ONLY` |
| 1 | `Section 2 Curriculum Structure` | 409 | `Section 2 Curriculum Structure` |
| 2 | `Section 3 Major Required Courses` | 641 | `Section 3 Major Required Courses` |
| 3 | `Section 4 Major Elective Courses` | 974 | `Section 4 Major Elective Courses` |
| 4 | `Section 4 Major Elective Courses` | 340 | `Course code \| Course title \| Credits` |
| 5 | `Section 5 Cooperative Education and Internship` | 634 | `Section 5 Cooperative Education and Internship` |
| 6 | `Section 6 Graduation Requirements` | 522 | `Section 6 Graduation Requirements` |
| 7 | `หมวด 7 สหกิจศึกษา (สรุปภาษาไทย)` | 273 | `หมวด 7 สหกิจศึกษา (สรุปภาษาไทย)` |

Why:

- The two title lines have 165 characters of body text (70 + 95), less than `min-chars`, so the heading "Section 1 Program Overview" does not close a chunk. Chunk 0 holds the title lines and Section 1.
- Every later heading finds at least 200 characters of body text and closes the chunk before it.
- Chunk 3 is closed for reason `SIZE`: the next row, `CSC486 | Compiler Construction | 3`, would make it 1,009 characters, more than `target-chars`.
- Chunk 4 starts with the repeated header row of the table, not with an overlap, because the closed chunk ended inside the table (spec file 49, section 9). It keeps the section title of chunk 3. Its body text without the header row is 298 characters, so "Section 5 …" closes it.
- No overlap is produced anywhere: the only `SIZE` close is inside a table.
- `(1)` to `(5)` are list items, not headings.

Turn this table into a test next to the worked example of spec file 49. If the implementation gives other numbers, find out whether the implementation or this file is wrong before changing the test.

---

## 14. Tests

No test calls a real provider. Tests use `DeterministicEmbeddingClient` (spec file 50, section 13).

### 14.1 Unit tests (no Spring context, a fake `KnowledgeVectorStore`)

Audience, with scope `(P1, D1)`:

| Document | Audience |
|---|---|
| `program_id` P1 | `PROGRAM` |
| curriculum version of P1 (`curriculumProgramId` P1) | `PROGRAM` |
| `department_id` D1 | `DEPARTMENT` |
| `program_id` P2 and `department_id` D1 | `DEPARTMENT` |
| no link | `UNIVERSITY` |

And: scope `(null, D1)` gives `DEPARTMENT` or `UNIVERSITY`, never `PROGRAM`; the Admin view without a scope gives `null`.

Reserve:

- The three rows of the table of section 7.3.
- A program candidate that is in both lists is used once.
- Three program candidates of one document, `max-per-document` 2, quota 3: two are reserved.
- `topK` 2 and quota 3: two program hits.
- Quota 0, or a scope without `programId`: the second statement is not run (count the calls), and the result equals the result of spec file 52 for the same candidates.
- The kept hits are ranked by distance, also when a reserved hit is farther than a filled one.

Neighbours:

- The four rows of the table of section 8.3.
- A hit without a section title gets no neighbour.
- A neighbour that is also a hit is not added twice; a neighbour of two hits is added once.
- A position that `findChunks` does not return (not eligible) is skipped, and the budget is not used for it.
- A neighbour has `neighbor = true`, the rank and score of its hit, and the audience of its document.
- `maxNeighborPassages` 0: `findChunks` is not called.

Options and old calls:

- `StudentSearchOptions` with `topK` 0 or 11, quota −1 or 11, neighbours −1 or 5 throws `IllegalArgumentException`.
- `studentSearch(query, scope, topK)` makes the same store calls and returns the same passages in the same order, with the same ranks and scores, as before this file. Each hit now has an audience.

### 14.2 Vector store (PostgreSQL Testcontainer)

- `curriculum_program_id` is mapped: a document linked to a curriculum version of P1 gives `curriculumProgramId` P1; every other document gives `null`.
- `programDocumentsOnly` P1 returns chunks of documents with `program_id` P1 and of documents linked to a curriculum version of P1, and nothing else; it combines with the scope and with the three other filters.
- `findChunks` returns the eligible chunks at the given positions, ordered by file and chunk index. One test each for a position that is not returned: an archived document, a deleted document, a file that is not current, a file that is not `READY`, a document linked to an `ARCHIVED` curriculum version, a chunk with another embedding model.
- `findChunks` with 20 positions runs one statement; with an empty list it runs none.

### 14.3 Search through the Spring context (`KnowledgeCurriculumRetrievalTests`)

Replace the `EmbeddingClient` bean with `DeterministicEmbeddingClient`. Upload and process, one after the other so that the chunk ids grow in this order:

| Document | File | Links |
|---|---|---|
| R "Sample Academic Regulations 2026" | `rag-sample-regulations.txt` | none (university-wide) |
| H "Sample Student Handbook 2026" | the same file again | none (university-wide) |
| C "Sample B.Sc. Computer Science Curriculum 2026" | `rag-sample-curriculum-cs.txt` | `programId` P1 |

Check the worked example of section 13.1 on C first.

Then use the Admin search endpoint with `studentScope` and the `minScore` of each row (`topK` 4):

| Query | Request | Expected results |
|---|---|---|
| `Compiler Construction Quantum Computing Bioinformatics Robotics` | scope P1, `minScore` 0.30, `maxNeighborPassages` 2 | C chunk 3 (`neighbor` true, rank 1), C chunk 4 (rank 1); both `audience` `PROGRAM` |
| the same | scope P1, `minScore` 0.30, `maxNeighborPassages` 0 | C chunk 4 only |
| the same | scope P2, `minScore` 0.30, `maxNeighborPassages` 2 | none |
| `CSC476 Software Testing CSC477 DevOps Engineering CSC478 Blockchain Technology CSC479 Digital Forensics` | scope P1, `minScore` 0.25, `maxNeighborPassages` 2 | C chunk 3 (rank 1), C chunk 4 (`neighbor` true) |
| `tuition must be paid within 14 days after the first day of the semester` | scope P1, `minScore` 0.30, `ownProgramQuota` 0 | R chunk 2, H chunk 2, R chunk 3, H chunk 3 |
| the same | scope P1, `minScore` 0.30, `ownProgramQuota` 2 | R chunk 2, H chunk 2, C chunk 6, C chunk 5; `audience` `UNIVERSITY`, `UNIVERSITY`, `PROGRAM`, `PROGRAM` |
| the same | scope P2, `minScore` 0.30, `ownProgramQuota` 2 | R chunk 2, H chunk 2, R chunk 3, H chunk 3 |
| the same | scope `{}`, `ownProgramQuota` 2 | 400 `VALIDATION_ERROR` |

When this file was written, a simulation of the deterministic client and the chunker gave these scores: the first query C4 0.455, everything else 0.215 or less; the second query C3 0.332, C4 0.189, everything else 0.152 or less; the third query R2 and H2 0.653, R3 and H3 0.480, R4 and H4 0.398, C6 0.389, C5 0.384. R and H have the same scores; R comes first because its chunk ids are smaller. If the implementation gives another order, find out whether the client, the chunker or this file is wrong before changing a test.

Through the assistant (scripted model, `app.knowledge.search.min-score` 0.30 for the class, as in spec file 53, section 20.7):

- Student A of P1, the model searches with the first query: the tool result has two passages, both with `appliesTo` "your program", the passage of chunk 3 first; `sources` lists C once.
- Student B of P2, the same: `knowledgeNotFound()`, one provider request.
- The log has `ASSISTANT_KNOWLEDGE … programPassages=2 neighborPassages=1` for Student A.

### 14.4 Assistant tool (built by hand)

- `appliesTo` for each audience; no `neighbor` key, no id, no score in the map.
- The executor passes `StudentSearchOptions(topK, ownProgramQuota, maxNeighborPassages)` from the settings.
- Context budget, `max-context-chars` 2,000: hits of 800 and 800 characters and two neighbours of 300 characters (one per hit) give both hits and one neighbour; a neighbour whose hit did not fit is never added; a first hit of 3,000 characters is still returned alone.
- The order of the passages follows section 8.1.

### 14.5 Existing tests

Every test of spec files 52 and 53 passes. The only allowed change is a whole-object comparison of a `KnowledgeSearchHit` or of an Admin search result, which now has `audience` and `neighbor`. List every touched test file and why.

---

## 15. Postman Verification

Preparation:

- The regulations sample is `READY` and university-wide (spec file 51, section 16).
- Upload the curriculum sample for the program of the test Student of `docker/dev-fixtures/step6-assistant-test-data.sql` (program code `CS-TEST`; find its id with the Admin program list):

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/upload
Authorization: Bearer <admin-access-token>
Content-Type: multipart/form-data
```

Form Data:

```text
title = Sample B.Sc. Computer Science Curriculum 2026
categoryId = <an existing category id>
academicYearLabel = 2026-2027
programId = <id of program CS-TEST>
file = rag-sample-curriculum-cs.txt
```

Wait until its status is `READY`. `GET /api/v1/admin/knowledge/documents/{id}/processing` shows `chunkCount: 8`. The backend log shows one embedding request for the eight chunks.

**1. The chunks**

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/{id}/chunks
Authorization: Bearer <admin-access-token>
```

Expected: eight chunks with the section titles and character counts of section 13.1. Chunks 3 and 4 have the same section title.

**2. Whom a passage applies to**

```http
POST http://localhost:8080/api/v1/admin/knowledge/search
Authorization: Bearer <admin-access-token>
Content-Type: application/json
```

```json
{
  "query": "What are the graduation requirements?",
  "minScore": 0,
  "studentScope": { "programId": <id of program CS-TEST> }
}
```

Expected: HTTP 200. Results of the curriculum document have `"audience": "PROGRAM"`, results of the regulations have `"audience": "UNIVERSITY"`. Every result has `"neighbor": false`.

**3. The reserve**

Send request 2 with `"topK": 4` and `"ownProgramQuota": 0`, then with `"ownProgramQuota": 2`.

Expected: with 2, at least two results have `"audience": "PROGRAM"` (with `minScore` 0 every chunk reaches the threshold). The results are still ordered by score. Note how the list changes, if it changes.

**4. Neighbours**

```json
{
  "query": "Which major elective courses can I choose from?",
  "minScore": 0,
  "topK": 1,
  "maxNeighborPassages": 2,
  "studentScope": { "programId": <id of program CS-TEST> }
}
```

Expected: HTTP 200. When the single hit is chunk 3 or chunk 4 of the curriculum, the other of the two appears next to it with `"neighbor": true` and the same rank and score. When the hit is another chunk, note which one: that is a measurement of the embedding model, not an error of the neighbour rule.

**5. Validation**

```json
{ "query": "credits", "ownProgramQuota": 2 }
{ "query": "credits", "ownProgramQuota": 11, "studentScope": { "programId": 1 } }
{ "query": "credits", "maxNeighborPassages": 5 }
```

Expected: HTTP 400, `code: "VALIDATION_ERROR"` for every one.

**6. Through the assistant**

Log in as the test Student and ask:

```http
POST http://localhost:8080/api/v1/student/assistant/chat
Authorization: Bearer <student-access-token>
Content-Type: application/json
```

```json
{
  "message": "What are the graduation requirements?"
}
```

Expected: HTTP 200. The backend log has `ASSISTANT_KNOWLEDGE … programPassages=` with a value of at least 1. Spec file 55 checks what the answer says.

---

## 16. Acceptance Criteria

- [ ] Every passage of a search with a Student scope has an audience as in section 6, and the assistant's passage map has `appliesTo`.
- [ ] The reserve works as in section 7 and never adds a passage below `min-score`.
- [ ] Neighbours work as in section 8, are read with every eligibility condition, and never count for `topK` or `max-per-document`.
- [ ] A call without the new options returns the same passages, order, ranks and scores as spec file 52.
- [ ] The context budget adds hits before neighbours.
- [ ] The Admin endpoint accepts and returns the fields of section 11.
- [ ] The sample curriculum document exists with exactly the content of section 13 and gives the eight chunks of section 13.1.
- [ ] No migration, no new tool argument, no id in the passage map.
- [ ] The tests of section 14 pass; no new failure compared with the baseline.
- [ ] `.env.example` and `compose.yaml` updated.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 17. Agent Completion Report

Report: files created and modified; the final statements of sections 7.2 and 8.2; the chunk table of section 13.1 as the implementation produced it; the scores of the queries of section 14.3 with the deterministic client; the Postman results actually performed, including the result of request 4 with the real embedding model; test totals against the baseline; decision entries added to `00-changes-and-decision.md`.

**Next: spec file 55 — Step 15C: Curriculum Questions.**
