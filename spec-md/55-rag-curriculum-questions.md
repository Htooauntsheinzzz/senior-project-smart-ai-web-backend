# 55 — Step 15C: Connect Curriculum RAG to the AI Assistant — Curriculum Questions

**Project:** Smart AI University Student Assistant
**Module:** Student AI Assistant → answers about the Student's curriculum
**Scope:** The rules the model follows for curriculum questions, the tool description, the backend's passage block for curriculum passages, the behaviour for Students without a curriculum passage, and an evaluation of the four example questions of Step 15.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec files 53 and 54 are implemented.
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Roadmap: Step 15: 53 knowledge search tool → 54 curriculum-aware retrieval → **55 curriculum questions (this file)**. Step 16: 56 academic progress tool → 57 hybrid answers.

---

## 1. Objective

Step 15 must answer document-based academic questions such as:

| Question | Where the answer is |
|---|---|
| What are the graduation requirements? | The graduation section of the Student's curriculum, and the university regulations |
| What electives are available? | The elective list of the Student's curriculum |
| What does the curriculum say about internships? | The internship and cooperative education section of the curriculum |
| What are the requirements for my program? | The curriculum of the Student's own program |

```text
Question → AI → RAG Search → Relevant Curriculum Chunks → LLM → Answer
  spec 53:  the search tool, the required search, the answer check, sources
  spec 54:  passages that say whom they apply to, room for the Student's program, whole lists
  spec 55:  how the model uses curriculum passages, and how this is checked   ← this file
```

After this file:

- the model knows that the curriculum of the Student's own program wins over a university-wide value for the same requirement, and says both;
- the model lists courses only from passages and never claims that a course is offered this semester;
- a Student without a curriculum passage is told so, and is answered from the university-wide documents only;
- the four questions above, and variations of them, have a deterministic evaluation through the real endpoint.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 53 and 54, spec file 36 sections 64 to 68 (the Step 11 evaluation) and the decision entries of the spec file 53 and 54 implementations.
2. Inspect `StudentAssistantServiceImpl` (`KNOWLEDGE_PROMPT`), `AcademicAiToolRegistryImpl` (the knowledge tool definition), `SafeAcademicResponseFactoryImpl` (the passage block), and in `src/test/java/…/feature/assistant/evaluation`: `IntentEvaluationSupport`, `ScriptedLlmClient`, `RecordingLlmClient`, `RecordingAcademicAiToolExecutor`, `EvaluationTrace`, `IntentEvaluator.containsToken`, `GroundingEvaluationTests` and `GroundingEvaluationDatasetTests`.
3. Record the test baseline before changing anything.

Do not:

- Change `SYSTEM_PROMPT`, the eight database tools or the Step 8 intent catalogue.
- Add a curriculum endpoint, a curriculum tool or a second search tool. Curriculum questions use `search_university_knowledge`.
- Extend the enums of the Step 11 dataset (`GroundingEvaluationCase.Scenario`, `Outcome`). `GroundingEvaluationDatasetTests` asserts that every scenario has its minimum count, so a new scenario breaks it. Step 15 gets its own record types (section 11).
- Let a test call a real provider. The live check of section 13 is opt-in and is not run by the implementing agent.

---

## 3. What Stays and What Changes

| Stays | Changes |
|---|---|
| Rules 17 to 22 of `KNOWLEDGE_PROMPT` | Rules 23 to 27 are added after rule 22 (section 5) |
| The schema of `search_university_knowledge` | Its description and the example in the `query` description (section 6) |
| The passage block of `groundedAnswer` (spec file 53, section 13.2) | Which two passages it shows, and the heading line names the audience (section 9) |
| The required search, the answer check and `sources` of spec file 53 | Nothing |
| The Step 9 and Step 11 datasets | Nothing; Step 15 has its own dataset (section 11) |

---

## 4. Package Structure

```text
src/main/java/…/feature/assistant/
├── grounding/impl/
│   └── SafeAcademicResponseFactoryImpl.java        (passage selection and heading line, section 9)
└── service/impl/
    ├── AcademicAiToolRegistryImpl.java             (tool description, section 6)
    └── StudentAssistantServiceImpl.java            (rules 23 to 27 in KNOWLEDGE_PROMPT)

src/test/java/…/feature/assistant/evaluation/
├── CurriculumEvaluationCase.java                   (new)
├── CurriculumEvaluationDataset.java                (new)
├── CurriculumEvaluationDatasetTests.java           (new)
├── CurriculumEvaluator.java                        (new)
├── CurriculumEvaluationTests.java                  (new, deterministic, every test run)
└── GeminiLiveCurriculumEvaluationTests.java        (new, opt-in only)

src/test/resources/assistant/step15/
├── curriculum-cases.jsonl                          (new)
└── curriculum-expectations.json                    (new)
```

---

## 5. Prompt Rules for Curriculum Questions

Append to `KNOWLEDGE_PROMPT` (spec file 53, section 9), after rule 22, in the same text block style:

```text
CURRICULUM RULES
23. For questions about the student's curriculum (graduation requirements, total credits, the structure of the
program, required courses, elective courses, internships, cooperative education or study plans), call
search_university_knowledge with a short query about the topic, for example "graduation requirements" or "major
elective courses". Do not put the student's name or student code into the query.
24. Each passage says whom it applies to in appliesTo: "your program", "your department" or "all students". When a
passage for "your program" and a passage for "all students" give different values for the same requirement, the
value for the student's program is the one that applies to this student: say so, and mention the university-wide
value separately with its document. Never merge or average two values, and never choose a value in between. If you
are not sure that two passages describe the same requirement, give both, each with its document.
25. Passages with the same document and section are consecutive parts of one section: read them together. List
courses, credits and conditions only as the passages state them, with course code and title. Do not add a course that
is not in a passage. A curriculum list says what may be taken; it does not say which courses are offered in the
current semester, so never claim that a course is offered now.
26. A curriculum says what is required. It does not say what this student has completed, so do not conclude from a
passage that the student has met or has not met a requirement.
27. For a curriculum question, if no passage applies to "your program", say that you found no curriculum passage for
the student's program, then answer only from the passages for "all students" or "your department" and name them.
```

Why each rule:

| Rule | Reason |
|---|---|
| 23 | A short topic query finds the curriculum section better than the whole question. The Student's name and code are personal data and help no search. |
| 24 | The sample curriculum says 132 credits, the sample regulations 120. Both are true, for different readers. The answer check compares values as a set and cannot see which one was given to whom (section 10). |
| 25 | Spec file 54 delivers both parts of a split list as neighbouring passages with the same section. The database knows which sections are open this semester; a curriculum does not. |
| 26 | Rule 22 says this for regulations. Step 16 (spec file 57) allows one controlled comparison with verified progress numbers; nothing else may conclude it. |
| 27 | Without it, a Student whose program has no curriculum document would get the university-wide numbers as if they were the program's. |

---

## 6. Tool Description

The definition of `search_university_knowledge` (spec file 53, section 6) keeps its name and its closed schema. Its description becomes:

```text
Search the university's official documents that this student is allowed to read (regulations, announcements,
handbooks, the curriculum documents of the student's program, fee and calendar information) and return the most
relevant passages with their document title, academic year, whom each passage applies to (appliesTo), section and
pages. Use it for questions about university rules, requirements, fees, deadlines and procedures, and about the
student's curriculum: graduation requirements, total credits, program structure, required and elective courses,
internships and cooperative education. It does not return the student's own records or results: use the other tools
for those. An empty list means no relevant passage was found.
```

The description of the `query` property gets a second example:

```text
What to look for, as one short question or phrase in the language of the student's question, for example "minimum
credits to graduate" or "major elective courses". Do not include the student's name or other personal data.
```

---

## 7. How the Four Questions Are Answered

With the sample documents of spec files 47 and 54, both `READY`: the regulations university-wide, the curriculum for program P1. The Student belongs to P1.

| Question | `personalEvidence` | `searchRequired` | A good query | The answer states |
|---|---|---|---|---|
| What are the graduation requirements? | false | true | graduation requirements | for the program: at least 132 credits, a cumulative grade point average of at least 2.00, at least 2.00 in the major required courses, cooperative education or the internship option, no outstanding debt. When the regulations passage was returned, the university-wide 120 credits separately, with its document. |
| What electives are available? | false | true | major elective courses | at least 15 credits of major electives chosen from the list, the courses of the list with code and title, that a course outside the list needs approval, and that not every elective is offered every semester. No claim about the current semester. |
| What does the curriculum say about internships? | false | true | internship cooperative education | CSC490 Cooperative Education, 6 credits, 16 weeks of full-time work, at least 90 earned credits and a cumulative grade point average of at least 2.00 to register; the alternative CSC491 Internship, 3 credits, at least 240 working hours in the summer session, with one additional major elective of 3 credits and the approval of the program committee. |
| What are the requirements for my program? | true | true | program requirements | the 132 credits and the structure (30 general education, 96 specialized, 6 free elective credits) or the graduation conditions, or both. Nothing about whether the Student has met them (rule 26). |

`personalEvidence` and `searchRequired` are the values of spec file 53, section 10.3. In all four cases the backend makes sure that the search runs (spec file 53, section 10.4), answers an empty search itself, and checks every course code, credit value, grade point average, year and section number of the answer against the passages (spec file 53, section 12).

The fourth question is personal ("my program"). The model may also call `get_my_academic_profile` to name the program. That is allowed, but it does not replace the search.

---

## 8. Students Without a Curriculum Passage

| Situation | What the Student gets |
|---|---|
| The Student's program has no `READY` curriculum document | the passages for all students and for the Student's department; the answer says that no curriculum passage for the program was found (rule 27) |
| The Student belongs to another program | the same; a program document of another program is never returned (spec file 52, section 9) |
| The Student has no program | the same; no reserve is used (spec file 54, section 7) |
| No passage reaches the threshold | `knowledgeNotFound()` from the backend, one provider request (spec file 53, section 11) |
| The curriculum document is archived | the same as "no `READY` curriculum document"; after activation the passages are back without reprocessing |

---

## 9. The Passage Block of the Backend

`groundedAnswer(evidence)` quotes passages when the model's answer failed the check (spec file 53, section 13.2). Two changes:

**Which passages.** It still shows at most two:

```text
passages = the collected passage maps (spec file 53, section 13.2, point 2), in order
first    = the first passage whose appliesTo is "your program"; when there is none, passages[0]
second   = the passage directly after first, when it has the same documentTitle and section as first
           (the rest of a split section); otherwise the first passage of the list that is not first
```

**Heading line.** After `(academicYear)` it names the audience when the passage has `appliesTo`:

```text
1. Sample B.Sc. Computer Science Curriculum 2026 (2026-2027), for your program, Section 6 Graduation Requirements
2. Sample Academic Regulations 2026 (2026-2027), for all students, Chapter 5 Graduation
```

Everything else of spec file 53, section 13.2 stays: the intro line, the 700-character cut, one empty line between passages, the database part before the block. Passages without `appliesTo` (built by hand in tests of spec file 53) keep the selection and the heading line of spec file 53.

Example: the model answers "What are the graduation requirements?" with "You need at least 140 credits." The search returned the curriculum chunks 6 and 0 and the regulations chunks 4 and 3. 140 is in no passage, so the answer is replaced by:

```text
Here is what the university documents say:

1. Sample B.Sc. Computer Science Curriculum 2026 (2026-2027), for your program, Section 6 Graduation Requirements
Section 6 Graduation Requirements
A student graduates from this program when all of these conditions are met:
(1) The student has completed at least 132 credits as required by the curriculum structure in Section 2.
(2) The student has a cumulative grade point average of at least 2.00.
(3) The student has a grade point average of at least 2.00 in the major required courses.
(4) The student has passed cooperative education or the internship option of Section 5.
(5) The student has no outstanding debt to the university.

2. Sample B.Sc. Computer Science Curriculum 2026 (2026-2027), for your program, Section 1 Program Overview
SAMPLE DATA FOR DEVELOPMENT ONLY — NOT AN OFFICIAL UNIVERSITY DOCUMENT
Sample University, Bachelor of Science Program in Computer Science (fictional), Curriculum 2026
Section 1 Program Overview
…
```

---

## 10. Known Limits

- **Precedence is not checked.** The answer check compares values as a set (spec file 53, section 12.4). An answer that gives a P1 Student the university-wide 120 credits passes, because 120 is in a passage. Rule 24 is the protection. The evaluation contains this case as a known limit (section 11.4, CUR-015) so that it stays visible.
- **Completeness is not checked.** An answer that names 5 of the 24 electives passes.
- **Course titles are not checked**, only course codes.
- **A list split into more than two chunks** reaches the model only partly when `max-neighbor-passages` or the context budget is reached. The sample document does not have such a list.

---

## 11. Evaluation Dataset (Deterministic)

### 11.1 Files and types

```text
src/test/resources/assistant/step15/curriculum-cases.jsonl          one case per line
src/test/resources/assistant/step15/curriculum-expectations.json    document titles, minimum counts, version "1.0"
```

```java
/** One Step 15 case: one question on a new conversation, the stand-in model's script, and what the Student must get. */
public record CurriculumEvaluationCase(String caseId, Category category, String language, String testStudent,
        String input, String modelBehavior, List<Map<String, Object>> script, Outcome expectedOutcome,
        List<String> mustContain, List<String> forbiddenFacts, List<String> expectedSources, List<String> expectedTools,
        Integer expectedProviderRequests, boolean knownLimit, String note) {

    public enum Category { GRADUATION, ELECTIVES, INTERNSHIP, PROGRAM_REQUIREMENTS, PRECEDENCE, OTHER_AUDIENCE,
        NOT_FOUND, SEARCH_MISSING, FAULTY_ANSWER, PERSONAL_MIX, LANGUAGE }

    /** ANSWER: the last scripted text unchanged. PASSAGES: the backend's passage block. NOT_FOUND: knowledgeNotFound().
     *  MISSING_EVIDENCE: the reply ends with knowledgeMissingEvidence(). */
    public enum Outcome { ANSWER, PASSAGES, NOT_FOUND, MISSING_EVIDENCE }
}
```

- `script` has the format of Step 9's `ScriptedLlmClient`: `{"tools": [{"name": …, "arguments": {…}}]}` requests tool calls, `{"text": "…"}` answers.
- `modelBehavior` is `HONEST` or `FAULTY`, as in Step 11.
- `testStudent` is `STD-TEST-001` (program of the curriculum), `STD-TEST-002` (another program) or `STD-TEST-003` (no program) of `AssistantAcademicFixture`.
- `mustContain` and `forbiddenFacts` use the whole-token matching of `IntentEvaluator.containsToken`.
- `expectedSources` are the titles of `sources`, in order. `expectedTools` are the distinct tool names in call order, as `EvaluationTrace.toolNames()` reports them.
- `note` names the limit of a `knownLimit` case; it is null otherwise.

### 11.2 Fixture

`CurriculumEvaluationTests` extends `IntentEvaluationSupport` and adds:

- the `EmbeddingClient` bean replaced by `DeterministicEmbeddingClient` (the knowledge tool is then active, spec file 53, section 5.2);
- `app.knowledge.search.min-score=0.30`;
- before all cases: upload and process the regulations sample as university-wide document "Sample Academic Regulations 2026", then the curriculum sample as document "Sample B.Sc. Computer Science Curriculum 2026" with `programId` of Student A's program;
- after all cases: delete both documents, then the existing fixture clean-up.

The scripted queries reuse words of the documents, because the test client compares characters, not meaning (spec file 52, section 17.4).

### 11.3 Categories and minimum counts

| Category | Meaning | Minimum |
|---|---|---:|
| `GRADUATION` | graduation requirements | 4 |
| `ELECTIVES` | elective courses | 4 |
| `INTERNSHIP` | internship and cooperative education | 3 |
| `PROGRAM_REQUIREMENTS` | requirements or structure of the Student's program | 4 |
| `PRECEDENCE` | the program and the university give different values | 2 |
| `OTHER_AUDIENCE` | a Student of another program or without a program | 4 |
| `NOT_FOUND` | nothing reaches the threshold | 3 |
| `SEARCH_MISSING` | the model does not search: retry, demand, fallback | 4 |
| `FAULTY_ANSWER` | the model invents or changes a value | 4 |
| `PERSONAL_MIX` | curriculum words in a question about the Student's own records | 2 |
| `LANGUAGE` | Thai or Myanmar questions | 3 |

At least 40 cases in total. Use the variations of Step 9 for the wording: questions with and without "my", short forms ("electives?"), typos, Thai.

### 11.4 Cases that must exist

Queries, with the results of a simulation of the deterministic client when this file was written (Student A sees both documents with the reserve, Students B and C see the regulations only):

| Query | Student A | Students B and C |
|---|---|---|
| Q1 `A student graduates from this program when all of these conditions are met` | C6, C0, R4, R3 | R4, R3 |
| Q2 `major elective courses` | C1, C4, C3 | none |
| Q3 `cooperative education and internship` | C5, C6 | none |
| Q4 `A student must complete at least 132 credits to graduate from this program` | R4, C0, C6, C5 | R4, R3 |
| Q5 `นักศึกษาที่จะลงทะเบียนรายวิชา CSC490 ต้องมีหน่วยกิตสะสมไม่น้อยกว่า 90 หน่วยกิต` | C7 | none |
| Q6 `parking permit for motorcycles` | none | none |

(C = curriculum chunk, R = regulations chunk.) Some scores are close to the threshold. The cases therefore assert `mustContain` and `forbiddenFacts`, not exact passage lists.

| caseId | Student | Input | Script | Outcome | Must contain / forbidden |
|---|---|---|---|---|---|
| CUR-001 | 001 | What are the graduation requirements? | Q1, then an honest answer with 132, 2.00 and the university-wide 120 | ANSWER, 2 requests | 132 |
| CUR-002 | 001 | What electives are available? | Q2, then an honest answer naming CSC461 Machine Learning and CSC486 Compiler Construction and at least 15 credits | ANSWER | CSC461, CSC486 |
| CUR-003 | 001 | What does the curriculum say about internships? | Q3, then an honest answer with CSC490, CSC491 and 240 working hours | ANSWER | CSC490, CSC491 |
| CUR-004 | 001 | What are the requirements for my program? | Q4, then an honest answer with 132 | ANSWER | 132 |
| CUR-005 | 001 | What are the graduation requirements? | Q1, then "You need at least 140 credits and a GPA of 2.50." | PASSAGES | "all of these conditions are met"; not 140, not 2.50 |
| CUR-006 | 001 | What electives are available? | Q2, then "You can take CSC499 Special Topics as an elective." | PASSAGES | not CSC499 |
| CUR-007 | 001 | What does the curriculum say about internships? | Q3, then "Cooperative education is 9 credits." | PASSAGES | not "9 credits" |
| CUR-008 | 001 | What are the requirements for my program? | `get_my_academic_profile`, then a text without search, then (after the demand) Q4, then an honest answer | ANSWER, 4 requests | 132 |
| CUR-009 | 001 | What are the requirements for my program? | `get_my_academic_profile`, then text, then text again | MISSING_EVIDENCE, 3 requests, `sources: []` | the program name of Student A |
| CUR-010 | 001 | What are the graduation requirements? | text, then text again | MISSING_EVIDENCE, 2 requests | — |
| CUR-011 | 002 | What electives are available? | Q2 | NOT_FOUND, 1 request | not CSC461 |
| CUR-012 | 003 | What are the requirements for my program? | Q4, then an honest answer: no curriculum passage for the program, the regulations require at least 120 credits | ANSWER | 120; not 132 |
| CUR-013 | 001 | Which elective courses am I taking? | `get_my_courses`, then an honest answer with Student A's courses | ANSWER, 2 requests, no search | a course code of Student A |
| CUR-014 | 001 | สหกิจศึกษาต้องมีหน่วยกิตสะสมเท่าไร | Q5, then an honest Thai answer with 90 หน่วยกิต and 2.00 | ANSWER | 90 |
| CUR-015 | 001 | How many credits do I need to graduate? | Q4, then "You need at least 120 credits." | ANSWER, `knownLimit` true | — (section 10: the wrong precedence is not caught) |
| CUR-016 | 001 | What is the parking fee for students? | Q6 | NOT_FOUND, 1 request | — |

The expected `sources` follow from the passages: CUR-001 lists the curriculum first, then the regulations; CUR-011 and CUR-016 have none.

### 11.5 Grading

`CurriculumEvaluator` grades with deterministic checks only:

1. HTTP 200 and a message.
2. The outcome: ANSWER means the message equals the last scripted text after `strip()`; PASSAGES means the message contains "Here is what the university documents say:"; NOT_FOUND means it equals `knowledgeNotFound()`; MISSING_EVIDENCE means it ends with `knowledgeMissingEvidence()`.
3. Every `mustContain` is in the message; no `forbiddenFacts` is.
4. `sources` titles equal `expectedSources`, and `expectedTools` equal the observed tool names, when the case gives them.
5. The number of provider requests, when the case gives it.
6. **No other program's data:** for `STD-TEST-002` and `STD-TEST-003`, neither the message nor the tool results contain the curriculum title, `132` or `CSC486`.

`CurriculumEvaluationTests` fails when a case fails. A case with `knownLimit` passes when the backend behaves as the case describes, and the report lists it under `knownLimits`.

Report in `target/step15-curriculum-evaluation/`: `summary.json` (cases, passed, pass rate per category, `faultyAnswersReplaced`, `crossProgramFindings` (must be 0), `knownLimits`) and `case-results.jsonl`. These are deterministic results of a scripted model, not a measurement of Gemini; say so in `summary.json`.

### 11.6 Dataset test

`CurriculumEvaluationDatasetTests` (no Spring context): unique ids; every category meets its minimum and the total is at least 40; every script is non-empty and every tool name in a script exists (the eight tools and `search_university_knowledge`); `testStudent` is one of the three; every `knownLimit` case names its limit in a `note` field; every `mustContain` that is meant to come from a passage occurs in the sample documents.

---

## 12. Retrieval Evaluation Cases

Section 11 proves the backend. Whether the real embedding model finds the curriculum passage for a real question is measured with the live retrieval evaluation of spec file 52, section 18.

Add a field `document` to the cases of `KnowledgeRetrievalEvaluationDataset`: `REGULATIONS` for the existing 24 cases, `CURRICULUM` for the cases below. The live evaluation processes both sample documents (two embedding requests) and searches each case with the `documentId` of its document.

| `caseId` | Question | `mustContain` |
|---|---|---|
| CUR-01 | What are the graduation requirements of the computer science program? | `all of these conditions are met` |
| CUR-02 | What electives can I choose? | `chosen from the list below` |
| CUR-03 | Is there an internship option instead of cooperative education? | `CSC491 Internship` |
| CUR-04 | How many credits does the computer science program require? | `at least 132 credits to graduate` |
| CUR-05 | How is the curriculum structured? | `divided into three groups` |
| CUR-06 | Do I have to pass every major required course? | `must pass all major required courses` |
| CUR-07 | What do I need before I can register for cooperative education? | `earned at least 90 credits` |
| CUR-08 | Is Compiler Construction an elective? | `CSC486 \| Compiler Construction` |
| CUR-TH-01 | ต้องมีหน่วยกิตสะสมเท่าไรจึงจะลงทะเบียนสหกิจศึกษาได้ | `ไม่น้อยกว่า 90 หน่วยกิต` |
| CUR-X-01 | วิชาเลือกเอกต้องเรียนอย่างน้อยกี่หน่วยกิต | `at least 15 credits of major elective courses` |

Every `mustContain` occurs in exactly one chunk of the curriculum sample (checked when this file was written). The dataset test of spec file 52, section 18.2 checks it for the document the case names.

Provider usage: 10 more embedding requests and 1 for the document. A native speaker should check the Thai questions.

---

## 13. Live Check (opt-in)

`GeminiLiveCurriculumEvaluationTests` sends Hsu's four questions and CUR-014 for Student A through the real endpoint with the real chat model and the real embedding model.

```bash
AI_EVAL_ENABLED=true KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=GeminiLiveCurriculumEvaluationTests
```

- It runs only when both variables are `true`, takes the keys like the Step 9 live mode, processes both sample documents with the real embedding model, and sends the cases one at a time with `AI_EVAL_DELAY_SECONDS` between them.
- Budget: at most 10 chat requests (`AI_EVAL_MAX_PROVIDER_CALLS`, default 10 for this class) and at most 8 embedding requests. A free tier allows about 20 chat requests a day.
- It reports, per case: whether the search ran, the passages' audiences, `sources`, which `mustContain` values the answer has, and the provider requests used. Report: `target/step15-curriculum-live/`.
- It fails only on a security finding (another program's data, a secret in a report). Answer quality is a measurement, not a build gate.

The implementing agent does not run it. The product owner runs it when the quota allows.

---

## 14. Tests

### 14.1 Prompt and tool

- With the feature active, the system prompt contains `CURRICULUM RULES` and rules 23 to 27 after rule 22; without the feature it equals the prompt before Step 15.
- The tool description is the text of section 6; the schema is unchanged except the `query` description.

### 14.2 Passage block (`SafeAcademicResponseFactoryTests`)

- The first passage for "your program" comes first, also when it is not first in the list.
- A neighbour directly after it (same document and section) comes second; otherwise the first other passage.
- Without a passage for "your program": the first two passages.
- Heading lines with "for your program", "for your department" and "for all students"; a passage without `appliesTo` keeps the heading of spec file 53.
- Every existing test of spec file 53 passes.

### 14.3 Evaluation

- `CurriculumEvaluationDatasetTests` (section 11.6).
- `CurriculumEvaluationTests`: every case passes; `crossProgramFindings` is 0.

### 14.4 Existing tests

Every existing test passes without being edited, including the Step 9 and Step 11 datasets and their evaluations. List every touched test file and why.

---

## 15. Postman Verification

Preparation:

- The regulations sample is `READY` and university-wide; the curriculum sample is `READY` for program `CS-TEST` (spec file 54, section 15). `AI_EMBEDDING_MODEL` is set; `AI_KNOWLEDGE_ENABLED` is `true` or not set.
- Log in as `std-test-001@rsu.ac.th` (program `CS-TEST`, `docker/dev-fixtures/step6-assistant-test-data.sql`) and use the Student access token.
- Every question costs two to four chat requests. The six checks below use about 12 to 20 of the roughly 20 free requests of one day. Run the first four first.

All requests:

```http
POST http://localhost:8080/api/v1/student/assistant/chat
Authorization: Bearer <student-access-token>
Content-Type: application/json
```

**1. Graduation requirements**

```json
{ "message": "What are the graduation requirements?" }
```

Expected: HTTP 200. The answer says that the program requires at least 132 credits, a cumulative grade point average of at least 2.00, at least 2.00 in the major required courses, cooperative education or the internship option, and no outstanding debt. If it mentions 120 credits, it says that this is the university-wide value of "Sample Academic Regulations 2026". `sources` contains "Sample B.Sc. Computer Science Curriculum 2026". Log: `ASSISTANT_KNOWLEDGE … searchRequired=true searches=1 … programPassages=` 1 or more.

**2. Electives**

```json
{ "message": "What electives are available?" }
```

Expected: HTTP 200. Major elective courses from the curriculum with code and title, at least 15 credits, and no statement that a course is offered this semester. Every course code in the answer is in the elective list of the sample. `sources` contains the curriculum.

**3. Internships**

```json
{ "message": "What does the curriculum say about internships?" }
```

Expected: HTTP 200. CSC490 Cooperative Education (6 credits, 16 weeks, 90 earned credits and a grade point average of 2.00 to register) and the CSC491 Internship option (3 credits, 240 working hours in the summer session, one more major elective, approval of the program committee). `sources` contains the curriculum.

**4. Requirements of my program**

```json
{ "message": "What are the requirements for my program?" }
```

Expected: HTTP 200. 132 credits, and the structure or the graduation conditions. No statement that the Student has met or not met them. `sources` contains the curriculum. If the log shows `ASSISTANT_GROUNDING_RETRY … reason=KNOWLEDGE_SEARCH_MISSING`, the model first answered without searching and the backend demanded the search; note it.

**5. Thai**

```json
{ "message": "วิชาเลือกมีอะไรบ้าง" }
```

Expected: HTTP 200, an answer in Thai with major electives from the curriculum (course codes and titles as written in the document).

**6. No curriculum passage**

Archive the curriculum document (`PATCH /api/v1/admin/knowledge/documents/{id}/archive` with an Admin token) and repeat request 1.

Expected: HTTP 200. The answer says that no curriculum passage for the Student's program was found and gives the university-wide 120 credits from "Sample Academic Regulations 2026"; `sources` has only the regulations. Activate the curriculum document again afterwards.

---

## 16. Acceptance Criteria

- [ ] `KNOWLEDGE_PROMPT` has rules 23 to 27 after rule 22; `SYSTEM_PROMPT` is unchanged.
- [ ] The tool description is the text of section 6; the schema is unchanged.
- [ ] The passage block prefers the Student's program and names the audience (section 9).
- [ ] The dataset of section 11 exists with every case of section 11.4 and the minimum counts of section 11.3, and every case passes in the deterministic run.
- [ ] No case shows data of another program to a Student of another program or without a program.
- [ ] The retrieval cases of section 12 are in the dataset; the live checks were not run by the implementing agent (or the report says who ran them and the provider requests used).
- [ ] The Step 9 and Step 11 datasets and their tests are unchanged.
- [ ] No new failure compared with the baseline; `./mvnw clean package` result reported truthfully.

---

## 17. Agent Completion Report

Report: files created and modified; the final `KNOWLEDGE_PROMPT` and tool description; the evaluation summary (cases per category, pass rates, `knownLimits`); any case whose passages differed from the simulation of section 11.4; the Postman results actually performed with the provider requests used; test totals against the baseline; decision entries added to `00-changes-and-decision.md`.

Report honestly what the backend cannot check (section 10).

**Step 15 is complete with this file. Next: spec file 56 — Step 16A: Academic Progress Tool.**
