# 56 — Step 16A: Build Hybrid Database + RAG Assistant — Academic Progress Tool

**Project:** Smart AI University Student Assistant
**Module:** Student AI Assistant → the Student's own academic progress from PostgreSQL
**Scope:** One new read-only assistant tool that returns the signed-in Student's progress totals (cumulative GPA, earned credits, current load), the prompt rules for it, and the Step 11 answer check extended to these values.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec files 53 to 55 are implemented. Spec file 40 (D56): `AcademicProgressService` and the grading tables V27 to V29.
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Roadmap: Step 15: 53 knowledge search tool → 54 curriculum-aware retrieval → 55 curriculum questions. Step 16: **56 academic progress tool (this file)** → 57 hybrid answers.

---

## 1. Objective

Step 16 combines both sources:

```text
Student Academic Data   → PostgreSQL       this file: get_my_academic_progress
Graduation Requirements → RAG              spec files 53 to 55: search_university_knowledge
Both Results → LLM → Personalized Answer   spec file 57
```

None of the eight database tools returns the Student's grade point average or earned credits. The values already exist: `AcademicProgressService.getProgress(studentId)` computes them for the mobile app (spec file 40, D56). This file gives them to the assistant through one more tool.

| Question | Value | Comes from |
|---|---|---|
| What is my GPA? | `cgpa` | finalized course results and the grading scheme |
| How many credits have I earned? | `creditsEarned` | finalized course results with a grade that earns credit |
| How many credits am I taking this semester? | `currentEnrolledCredits`, `currentCourses` | ACTIVE enrollments in the Student's current semester |

After this file:

- these questions are answered from PostgreSQL, and the answer is checked against the values;
- a value that is not available is said to be not available, never 0;
- an answer never tells the Student that they are eligible, have met all requirements or will graduate (section 10.3);
- spec file 57 uses the same tool for the hybrid question "How many credits do I still need to graduate?".

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec file 40 with D56 (progress and grading), spec file 53 sections 5, 6, 7, 11, 12 and 13, spec file 31 with D46, spec file 36 with D52, and the decision entries of the spec file 53 to 55 implementations.
2. Inspect `AcademicProgressService`, `AcademicProgressServiceImpl`, `AcademicProgressCalculator`, `StudentAcademicProgressResponse`, `AcademicQueryServiceImpl` (its private `query(...)` helper: logging and exception translation), `CurrentStudentProvider`, the assistant classes listed in spec file 53 section 2, `AssistantAcademicFixture`, `IntegrationSupport`, `GroundingEvaluationTests` (`FailingAcademicQueryService`), the migrations V27 to V29, and `docker/dev-fixtures/step6-assistant-test-data.sql` and `step13b-student-mobile-test-data.sql`.
3. Record the test baseline before changing anything. Compare failures by test id: 151 test ids already fail before Step 15 (D65 to D72); a new failing id is a regression.

Do not:

- Change `AcademicQueryService`. Hand-written test doubles implement it, and a new method would break them. The tool uses a new interface (section 6).
- Change `AcademicProgressService`, `AcademicProgressServiceImpl` or `AcademicProgressCalculator`. The tool returns exactly the values of spec file 40.
- Change the name, schema, description or result of the eight database tools or of `search_university_knowledge`.
- Change `SYSTEM_PROMPT` or `KNOWLEDGE_PROMPT`, or the Step 8 intent catalogue. Progress questions are not an eleventh intent.
- Give the tool any argument.
- Return course-level data: no course list, no grade per course, no grade point per course, no transcript line. Only the totals of section 7.1.
- Compute a new value in the tool or in the backend reply (remaining credits, a projected grade point average, a graduation date).
- Add grading data to a Flyway migration, or decide the official grade points. They are still an open question for the product owner (D56). The data of section 15 is test and development data.
- Apply the development fixture of section 15.2 to the running database. Write it; the product owner applies it.
- Let a test call a real provider.

---

## 3. What Stays and What Changes

| Stays | Changes |
|---|---|
| The eight database tools and `search_university_knowledge` | A new tool `get_my_academic_progress` (section 7) |
| `SYSTEM_PROMPT` and `KNOWLEDGE_PROMPT` | A block `PROGRESS_PROMPT` is added after them when the tool is active (section 8) |
| `AcademicQueryService` and `AcademicProgressService` | A new `AcademicProgressQueryService` that calls the second for the token's Student (section 6) |
| Step 11 evidence, decisions and replies | The tool is a database tool: same statuses, same fail-closed rule; `groundedAnswer` renders its values (sections 9 and 11) |
| The answer check of Step 11 and spec file 53 | Progress values are facts; the wide credit and GPA patterns also apply after this tool; a new claim kind `STANDING` (section 10) |
| The chat request and response | Nothing |

**When the tool is not active (section 5), the assistant behaves exactly as before this step.**

---

## 4. Package Structure

```text
feature/academicquery/
├── dto/
│   └── AcademicProgressQueryResult.java               (new)
└── service/
    ├── AcademicProgressQueryService.java              (new)
    └── impl/
        └── AcademicProgressQueryServiceImpl.java      (new)

feature/assistant/
├── grounding/
│   ├── SafeAcademicResponseFactory.java               (comment: groundedAnswer also renders progress)
│   └── impl/
│       ├── AcademicGroundingPolicyImpl.java           (+ the tool in ACADEMIC_TOOLS)
│       ├── GroundedAnswerValidatorImpl.java           (progress facts, wide condition, STANDING, section 10)
│       └── SafeAcademicResponseFactoryImpl.java       (progress part of groundedAnswer, section 11)
├── mapper/
│   └── AcademicAiToolResultMapper.java                (+ progress)
└── service/
    ├── AcademicAiToolNames.java                       (+ GET_MY_ACADEMIC_PROGRESS)
    ├── AssistantProgressSettings.java                 (new)
    └── impl/
        ├── AcademicAiToolRegistryImpl.java            (+ the definition, only when active)
        ├── AcademicAiToolExecutorImpl.java            (+ the case)
        └── StudentAssistantServiceImpl.java           (PROGRESS_PROMPT)

config/
└── AiAssistantProgressProperties.java                 (new, app.ai.progress)

src/test/java/…/feature/assistant/
└── AcademicProgressFixture.java                       (new, section 15.1)

docker/dev-fixtures/
└── step16-academic-progress-test-data.sql             (new, section 15.2)
```

`feature/academicquery` already depends on `feature/studentacademic` (`StudentAcademicService`); the new service adds `AcademicProgressService` from the same feature.

---

## 5. Configuration and Activation

`application.properties`:

```properties
# Step 16A: the assistant's academic progress tool.
app.ai.progress.enabled=${AI_ACADEMIC_PROGRESS_ENABLED:true}
```

`AiAssistantProgressProperties` is a `@Validated` record with `boolean enabled`, like `AiAssistantContextProperties`. Add `AI_ACADEMIC_PROGRESS_ENABLED` to `.env.example` and to the `backend` service in `compose.yaml`. Spec file 57 adds a second property to the same record.

### 5.1 Active or not active

```java
/** Step 16A: whether the assistant may read the Student's academic progress totals. */
@Component
public class AssistantProgressSettings {
    public boolean active();                                     // properties.enabled()
    public static AssistantProgressSettings inactive();
    public static AssistantProgressSettings of(boolean active);  // for classes built by hand in tests
}
```

`active()` is asked in this one class. The tool does not depend on the knowledge base: it can be active without an embedding model.

| | Active | Not active |
|---|---|---|
| Tools declared to the model | the eight, then `get_my_academic_progress`, then `search_university_knowledge` when that is active | as before this step |
| System prompt | `PROGRESS_PROMPT` is added (section 8) | as before this step |
| A call of `get_my_academic_progress` by the model | executed | `UNSUPPORTED_AI_TOOL`, as for any unknown tool |

With both tools active the model sees ten tools.

### 5.2 Existing tests

Unlike the knowledge tool, this tool needs no provider, so it would be active in the Spring test context by default and change the tool list of every existing chat test. Therefore:

- `IntegrationSupport.infrastructure` adds `r.add("app.ai.progress.enabled", () -> "false")`. Every existing test runs without the tool and needs no change.
- Test classes of this file and of spec file 57 activate the tool with a nested `@TestConfiguration` that declares `@Bean @Primary AssistantProgressSettings progressSettings()` returning `AssistantProgressSettings.of(true)`. A `@TestPropertySource` cannot do it, because the dynamic properties of `IntegrationSupport` take precedence.
- The constructors that existing tests use by hand (`new AcademicAiToolRegistryImpl()`, the registry and executor constructors of spec file 53, `AssistantTestServices.service(…)`) are kept and mean "progress not active". Add the constructors that Spring uses and mark them `@Autowired`. `AssistantTestServices` gets an overload that also takes `AssistantKnowledgeSettings` and `AssistantProgressSettings`; the existing method stays.

Existing pins that this protects: `AcademicIntentCatalogTests` (`hasSize(8)` on the no-argument registry), `StudentAssistantServiceTests` and `AcademicAiToolIntegrationTests` (`hasSize(8)` on the declared tools).

### 5.3 Before turning it on

The production default is `true`. Until the university's grading data exists (open question of D56), every Student has `cgpa` and `creditsEarned` null, and the assistant says that these values are not currently available. That is correct behaviour, not an error.

---

## 6. The Query Service

```java
/** Step 16A: the token Student's academic progress totals, for the assistant. Read-only. */
public interface AcademicProgressQueryService {
    AcademicProgressQueryResult getMyAcademicProgress();
}
```

```java
/** The totals of spec file 40 for the token's Student. Unknown values are null, never zero. */
public record AcademicProgressQueryResult(BigDecimal cgpa, BigDecimal creditsEarned, Integer currentCourses,
        Integer currentEnrolledCredits, Integer studyYear, Integer academicYear, String dataStatus) {}
```

`AcademicProgressQueryServiceImpl`:

```text
@Service, method @Transactional(readOnly = true, timeout = 15)
1. studentId = currentStudent.getCurrentStudentId()           (from the SecurityContext, never an argument)
2. p = academicProgressService.getProgress(studentId)          (applies the Student eligibility rules through getProfile)
3. return new AcademicProgressQueryResult(p.cgpa(), p.creditsEarned(), p.currentCourses(),
          p.currentEnrolledCredits(), p.studyYear(), p.academicYear(), p.dataStatus())
```

- Logging and exception translation are exactly those of `AcademicQueryServiceImpl.query(...)` for `get_my_academic_profile`, with the operation name `get_my_academic_progress`. Copy that behaviour; do not change `AcademicQueryServiceImpl` to share code. A test proves that both services translate the same failure in the same way (section 17.1).
- Nothing is written. No value is rounded, defaulted or recomputed.

---

## 7. The Tool

Add to `AcademicAiToolNames`:

```java
public static final String GET_MY_ACADEMIC_PROGRESS = "get_my_academic_progress";
```

Definition (built with the existing `noArguments` helper: closed schema without properties):

```text
name:        get_my_academic_progress

description: Get the authenticated student's academic progress totals: cumulative grade point average (cgpa),
             credits earned from finalized course results (creditsEarned), number of courses and credits in the
             current semester (currentCourses, currentEnrolledCredits), study year and academic year. Use it for
             "What is my GPA?", "How many credits have I earned?" and "How many credits am I taking this semester?",
             and together with search_university_knowledge when the student asks how they stand against a
             requirement. A null value means not currently available. It does not list courses or grades and does
             not say whether a requirement is met.
```

```json
{ "type": "object", "properties": {}, "additionalProperties": false }
```

New case in `AcademicAiToolExecutorImpl.execute`:

```text
case GET_MY_ACADEMIC_PROGRESS -> {
    noArguments(args);                                                     (existing: null or non-empty → INVALID_AI_TOOL_ARGUMENTS)
    yield one(name, mapper.progress(progressQuery.getMyAcademicProgress()));
}
```

- Failures use the existing `catch` list: `AcademicQueryFailure` with its code, `DataAccessException` as `AI_TOOL_EXECUTION_FAILED`.
- An executor built with a constructor that has no `AcademicProgressQueryService` returns `UNSUPPORTED_AI_TOOL` for this name.
- The executor still has no transaction; the query service opens its own short read-only transaction.

### 7.1 Result map

`AcademicAiToolResultMapper.progress(AcademicProgressQueryResult)`, built with `fields(…)` so null values are kept. This is exactly what the model receives:

```json
{
  "cgpa": 2.57,
  "creditsEarned": 18,
  "currentCourses": 3,
  "currentEnrolledCredits": 9,
  "studyYear": 3,
  "academicYear": 2026,
  "dataStatus": "AVAILABLE"
}
```

| Key | Meaning |
|---|---|
| `cgpa` | cumulative grade point average of the finalized results, 2 decimal places, or null |
| `creditsEarned` | credits of finalized results whose grade earns credit, each course once; transfer credits are not included; or null |
| `currentCourses` | number of courses with an ACTIVE enrollment in the Student's current semester, or null without a current semester |
| `currentEnrolledCredits` | the credit hours of those courses, or null without a current semester |
| `studyYear` | the Student's study year from the profile, or null |
| `academicYear` | the Student's academic year from the profile, or null |
| `dataStatus` | `AVAILABLE` when the four values of spec file 40 are known, otherwise `PARTIAL` |

The map has no student id, name, code, course or grade. A Student without finalized results gets `cgpa: null`, `creditsEarned: null`, `dataStatus: "PARTIAL"`.

---

## 8. System Prompt

Add a constant `PROGRESS_PROMPT` in `StudentAssistantServiceImpl`, a text block in the style of `KNOWLEDGE_PROMPT`, starting with two line breaks:

```text
ACADEMIC PROGRESS RULES
These rules continue the academic fact rules above.
P1. For the student's cumulative GPA, earned credits, number of courses or credits in the current semester, or study
year, call get_my_academic_progress. Never estimate these values or take them from an earlier answer.
P2. Report the values exactly as the tool returns them. cgpa is the cumulative grade point average of the student's
finalized course results. creditsEarned counts the credits of recorded course results only; transfer credits are not
included. currentEnrolledCredits are credits of courses the student is taking now, not earned credits. If a value is
null, say that it is not currently available; never write 0 or another value instead.
P3. The tool returns totals only. It does not say which courses the student passed or failed, which curriculum
requirements the credits count toward, or whether the student is eligible for anything. Never tell the student that
they are eligible, that they have met all requirements or that they will graduate.
P4. Do not calculate a grade point average, predict grades or a graduation date, or say which grades the student needs.
```

The prompt of a request becomes:

```java
String systemPrompt = SYSTEM_PROMPT
        + (knowledge.active() ? KNOWLEDGE_PROMPT : "")
        + (progress.active() ? PROGRESS_PROMPT : "")
        + references.promptContext(resolved, conversation);
```

`KNOWLEDGE_RETRY_PROMPT` (spec file 53, section 10.4) is still appended last when it is sent. Spec file 57 adds one more block and shows the final order.

The rules are numbered P1 to P4, not 28 to 31, because the block is also used without `KNOWLEDGE_PROMPT`.

Rule 11 of `SYSTEM_PROMPT` ("course-completion records are not available") stays true: the tool gives totals, not course results.

---

## 9. Evidence and Decisions

- `AcademicGroundingPolicyImpl.ACADEMIC_TOOLS` gets `GET_MY_ACADEMIC_PROGRESS`, so `toolRequiresGrounding` is true.
- `capture` needs no change. Success with a map is `VERIFIED`, also when single values are null (they mean "not currently available", as for the profile). `AI_TOOL_EXECUTION_FAILED` is `UNAVAILABLE`. Other codes are `ERROR`.
- `evaluate` needs no change. The tool is a database tool: `UNAVAILABLE` fails closed with `academicServiceUnavailable()` (spec file 53, section 11.2, rule 2).
- `PersonalAcademicQuestionDetector` already treats "What is my GPA?" and "How many credits have I earned?" as personal (a first-person word and `gpa` or `credit…`). No change.
- `AcademicIntentCatalog.primaryIntentForTool("get_my_academic_progress")` returns null and `academicIntent(evidence)` skips it. `AcademicReferenceResolverImpl.nextReference` stores the tool name like it does for the search (spec file 53, section 15); a test proves that the course references of the earlier turn are kept.

---

## 10. Checking the Answer

### 10.1 Facts from progress evidence

In `Facts.of`, after the existing `facts.collect(null, e.verifiedData())`, add for evidence of `get_my_academic_progress` with status `VERIFIED` and a map as data:

```text
creditsEarned            → credits, only when it is a whole number (stripTrailingZeros().scale() <= 0)
currentEnrolledCredits   → credits
cgpa                     → gpas, as stripTrailingZeros()
```

`academicYear` already reaches `years` through `collect`. `currentCourses` and `studyYear` are no claim kind.

### 10.2 When the wide patterns apply

Spec file 53, section 12.3 uses `CREDITS_WIDE`, `CREDITS_AFTER_WIDE` and the kind `GPA` only while verified document evidence is present. Change that condition to:

```text
wideClaims(evidence) = the evidence holds a VERIFIED entry of search_university_knowledge
                       or a VERIFIED entry of get_my_academic_progress
```

Without either, the check is exactly the Step 11 check.

| Answer (evidence: `cgpa` 2.57, `creditsEarned` 18, `currentEnrolledCredits` 9, no passage) | Result |
|---|---|
| "Your cumulative GPA is 2.57." | grounded |
| "Your GPA is 2.6." | rejected, `GPA` (inconsistent) |
| "You have earned 18 credits." | grounded |
| "You have earned 21 credits." | rejected, `CREDIT_HOURS` |
| "You are taking 9 credits this semester." | grounded |
| "You need 120 credits to graduate." | rejected, `CREDIT_HOURS`: no passage of this request contains 120 |
| (evidence with `cgpa` null) "Your GPA is 3.00." | rejected, `GPA` (unsupported) |
| "Your GPA is 2.57, so you are eligible to graduate." | rejected, `STANDING` (section 10.3) |

### 10.3 Claims about the Student's standing

A new claim kind `STANDING`. It is read only while the evidence holds a `VERIFIED` entry of `get_my_academic_progress`. Every match is an issue, because no evidence can support it: the tool has no course results, and spec file 57 never gives the model a list of met requirements.

```text
STANDING_EN      (?i)(?<!(?:before|until|whether|if|once|when|that)\s)(?<![a-z])you(?:'re|’re|\s+are|\s+have|'ve|’ve)?\s+(?:now\s+|already\s+)?(?:eligible|qualified|(?:met|meet|satisfied|fulfilled|completed)\s+(?:all|every)(?![a-z])|on\s+track\s+to\s+graduate)
STANDING_GRAD    (?i)(?<!(?:before|until|whether|if|once|when|that)\s)(?<![a-z])you\s+(?:will|can|are\s+able\s+to)\s+graduate(?![a-z])
STANDING_TH      the message contains one of: คุณมีสิทธิ์สำเร็จการศึกษา, คุณมีสิทธิ์จบการศึกษา, คุณจะจบการศึกษา,
                 คุณจบการศึกษาได้, คุณผ่านเกณฑ์ครบ, ครบทุกเงื่อนไขแล้ว
```

| Text | `STANDING` |
|---|---|
| "You are eligible to graduate." / "You're now eligible for cooperative education." | yes |
| "You have met all graduation requirements." / "You meet every requirement." | yes |
| "You have completed all major required courses." | yes (the tool knows no courses) |
| "You will graduate next year." / "You can graduate this semester." | yes |
| "You're on track to graduate." | yes |
| "Before you can graduate, you need at least 132 credits." | no (after "before") |
| "I can't tell whether you are eligible." | no (after "whether") |
| "You are not yet eligible." | no |
| "Eligibility is decided by the registrar." | no |

A sentence such as "you can graduate once you …" is rejected although it is conditional. Prompt rule P3 (and rule H5 of spec file 57) tells the model not to write these phrases at all, so a correct answer does not contain them. The Myanmar language has no list; prompt rule P3 is its only guard (section 14).

### 10.4 Order of the checks

`STANDING` is checked after the existing kinds and adds its issue to `unsupported`. The outcome follows the existing rule: `INCONSISTENT_ACADEMIC_VALUE` when an inconsistent value was also found, otherwise `UNSUPPORTED_ACADEMIC_CLAIM`.

Every existing `GroundedAnswerValidatorTests` case passes unchanged, because none has progress evidence.

---

## 11. The Progress Part of `groundedAnswer`

`SafeAcademicResponseFactoryImpl.render` gets a case for `GET_MY_ACADEMIC_PROGRESS` with a map:

```text
Your academic progress:
- Cumulative GPA: 2.57
- Credits earned from recorded course results: 18
- Courses this semester: 3
- Credits this semester: 9
- Study year: 3
```

- A null value is written as "not currently available" (the existing `orUnavailable`).
- Numbers are written with `BigDecimal.toPlainString()` and `String.valueOf`. Nothing is rounded or calculated.
- `dataStatus` and `academicYear` are not written.
- The part takes its place in the existing loop, in the order of the evidence. Identical evidence is rendered once (existing rule), so a tool that ran twice gives one part.

Update the comment of `SafeAcademicResponseFactory.groundedAnswer`: it renders progress totals too.

---

## 12. What Happens in Each Situation

| Situation | The Student gets | Chat requests |
|---|---|---:|
| "What is my GPA?", grading data present, answer passes the check | the model's answer | 2 |
| The model states another value | the progress part of `groundedAnswer` | 2 |
| No grading data (`cgpa` null) | the model says "not currently available"; a number would be rejected | 2 |
| The model answers without a tool | the Step 7 retry, then `missingEvidence()` if it still uses no tool | 2 or 3 |
| Database failure | `academicServiceUnavailable()` | 1 |
| Tool not active | as before this step | as before |
| "How many credits do I still need to graduate?" with both tools active, before spec file 57 | the model may call both tools; the answer is checked against both; a remaining-credit number that no evidence contains is rejected and the Student gets the progress part and the passages | 2 or 3 |

---

## 13. Privacy, Security and Cost

- **Who.** The Student comes from the token. The tool has no argument, so no message, passage or tool argument can ask for another Student's progress.
- **What is sent to the chat provider.** The totals of section 7.1 are personal academic data. They are sent only for a request in which the model calls the tool (or spec file 57's backend call), like enrollments and courses since Step 6. No name, student code or course result is sent.
- **Logs.** The values are never logged (section 16).
- **Redis.** Only the USER and ASSISTANT messages of a completed turn are stored, as for every answer (D51). The tool result is not stored.
- **Cost.** No embedding request. One short read-only database transaction per call.

---

## 14. Known Limits

- **Totals only.** The tool cannot tell which courses count toward which part of the curriculum, which required courses are missing, or the grade point average of the major required courses. That is a degree audit, which this project does not have.
- **`creditsEarned` counts recorded course results only.** Transfer credits and credits that do not count toward the curriculum are not distinguished (spec file 40).
- **No official grading data yet** (D56). Until it exists, `cgpa` and `creditsEarned` are null.
- **The current semester** is the Student's `semester_id` (spec file 40). There is no date-based current term.
- **`STANDING` is a phrase list.** A paraphrase ("graduation this year looks likely"), most Thai wordings and every Myanmar wording are not caught. Prompt rule P3 is the guard for them.
- A grade point average written without a decimal point ("a GPA of 2") is not read (spec file 53, section 12.4).

---

## 15. Test Data

### 15.1 `AcademicProgressFixture` (tests only)

Added on top of `AssistantAcademicFixture` (Testcontainers only, never a migration). Values:

| Item | Value |
|---|---|
| Grading scheme | `scheme_code` `TEST-PROGRESS`, `repeat_attempt_policy` `LATEST_ATTEMPT` |
| Grade definitions | A 4.00, B 3.00, C 2.00: counts in GPA, earns credit, passing. F 0.00: counts in GPA, does not earn credit, not passing |
| Past semester | academic year 2025, "Semester 2" (Thai "ภาคการศึกษาที่ 2") |
| Past courses | `PGR101` to `PGR107`, "Progress Test Course 1" to "… 7", 3 credit hours each, program CS of Student A |
| Past sections | one `SEC-01` per course in the past semester, any lecturer of the base fixture, no published meeting |
| Student A | ACTIVE enrollments in the seven sections; FINALIZED results with `credits_attempted` 3 and grades A, A, B, B, C, C, F; `study_year` set to 3 |

Expected progress of Student A: `cgpa` 2.57 (54 grade points over 21 credits), `creditsEarned` 18 (six passed courses of 3 credits), `currentCourses` 3 and `currentEnrolledCredits` 9 (CS301, CSC452 and CSC460 of the base fixture, 3 credits each), `studyYear` 3, `academicYear` 2026, `dataStatus` `AVAILABLE`.

Student B has no results: `cgpa` null, `creditsEarned` null, `currentCourses` 3, `currentEnrolledCredits` 9, `dataStatus` `PARTIAL`. Student C has no semester, no study year, no academic year and no results: all six values null, `dataStatus` `PARTIAL`.

`create()` checks these values through `AcademicProgressService` and fails with a clear message when they differ, so a later change of the base fixture is noticed here and not as a confusing evaluation failure.

`clean()` deletes, in this order: the results whose grade definition belongs to `TEST-PROGRESS`, Student A's enrollments in sections of `PGR1…` courses, those sections, the `PGR1…` courses, the past semester, the grade definitions and the scheme; and sets Student A's `study_year` back to null. It must be safe when nothing exists. Call it **before** `AssistantAcademicFixture.create()` (whose `clean()` deletes all enrollments and would otherwise fail on the results' foreign key) and after the test class.

The past enrollments are ACTIVE (progress only counts ACTIVE enrollments, spec file 40), so `get_my_enrollments` and `get_my_courses` list them for Student A while the fixture exists. Tests of this file and of spec file 57 must not assert Student A's exact enrollment list; no other test class uses this fixture.

### 15.2 Development fixture

`docker/dev-fixtures/step16-academic-progress-test-data.sql`, in the style of `step13b-student-mobile-test-data.sql`: a header that says it is for the local development database only and never a migration, the `docker exec … psql` line to apply it, a cleanup block that removes only its own rows, and one transaction.

| Item | Value |
|---|---|
| Student | `STD-TEST-016`, `std-test-016@rsu.ac.th`, first name "Test", last name "Sixteen", ACTIVE, the password hash of `step6-assistant-test-data.sql` (same password), faculty/department/program `CS-TEST`, the semester "ภาคการศึกษาที่ 1 (ทดสอบ)", `academic_year` 2026, `enrollment_year` 2024, `study_year` 3 |
| Current enrollments | ACTIVE in the same two sections as `STD-TEST-001` (CS301 SEC-01 and CSC452 SEC-02 of that semester) |
| Grading scheme | `DEV-TEST-PROGRESS` "Development progress test scheme", description "Test values for Postman checks, not university policy", A 4.00, B 3.00, C 2.00, F 0.00 as in section 15.1 |
| Past semester | academic year 2025, Thai name "ภาคการศึกษาที่ 2 (ทดสอบ ความก้าวหน้า)", English "Semester 2" |
| Past courses and results | `PGR101` to `PGR107` as in section 15.1, sections in the past semester, ACTIVE enrollments of `STD-TEST-016`, FINALIZED results A, A, B, B, C, C, F, `created_by` and `finalized_by` admin user 1 |

Expected for `STD-TEST-016` after applying: `cgpa` 2.57, `creditsEarned` 18, `currentCourses` 2, `currentEnrolledCredits` the credit hours of CS301 and CSC452 in the development database (6 when both have 3), `dataStatus` `AVAILABLE`. Write the expected values into the header.

Before writing the file, check the development fixtures for `STD-TEST-016` and `PGR10…`; if either is taken, use the next free number and record it. The fixture does not touch `STD-TEST-001` or the `DEV-TEST-ONLY` scheme of `step13b`. It does not mark its scheme as a default for anyone else (the schema has no default; keep it that way).

---

## 16. Logging

The existing tool line covers the tool:

```text
ASSISTANT_TOOL requestId=… round=1 tool=get_my_academic_progress success=true code=- resultCount=1 evidence=VERIFIED durationMs=12
```

The query service logs like the other academic queries (section 6). Never logged: a progress value.

---

## 17. Tests

No test calls a real provider.

### 17.1 Query service (unit, mocks)

- Returns the seven values of `getProgress` for `currentStudent.getCurrentStudentId()`, unchanged, including null values.
- A failure of `getProgress` (`DataAccessException`, a Student eligibility failure) is translated exactly as `getMyAcademicProfile` translates the same failure.

### 17.2 Registry and executor (built by hand)

- Not active: the existing tools only, `isSupported("get_my_academic_progress")` is false, a call returns `UNSUPPORTED_AI_TOOL`.
- Active: the tool comes after the eight database tools and before `search_university_knowledge` when that is active too (ten tools). Its schema has no property and `additionalProperties` false.
- Arguments `{}` run the query; `null`, `{"studentId": 1}` and `{"x": 1}` return `INVALID_AI_TOOL_ARGUMENTS` and the query is not called.
- The map has exactly the seven keys of section 7.1 and keeps null values.
- `DataAccessException` gives `AI_TOOL_EXECUTION_FAILED`; an `AcademicQueryFailure` gives its code.
- An executor built without the progress query service returns `UNSUPPORTED_AI_TOOL` for this name.

### 17.3 Policy and grounding

- `toolRequiresGrounding("get_my_academic_progress")` is true.
- `capture`: a map with null values is `VERIFIED`; `AI_TOOL_EXECUTION_FAILED` is `UNAVAILABLE`.
- `evaluate`: progress `UNAVAILABLE` gives `academicServiceUnavailable()`, also together with `VERIFIED` knowledge evidence.

### 17.4 Answer check

- Every row of the table of section 10.2.
- Every row of the table of section 10.3, and each Thai phrase.
- `creditsEarned` 18.5 adds no credit fact; "You have earned 18.5 credits" is not read as a claim of 18 or 5.
- Progress evidence with status `UNAVAILABLE` or `ERROR` adds no fact and switches on neither the wide patterns nor `STANDING`.
- Knowledge evidence only: `STANDING` is not read (spec file 53 behaviour unchanged).
- All existing `GroundedAnswerValidatorTests` pass unchanged.

### 17.5 Deterministic replies

- The progress part with all values, with null values, and after a profile part.
- The same progress evidence twice gives one part.
- All existing `SafeAcademicResponseFactoryTests` pass unchanged.

### 17.6 Chat service (built by hand, scripted model)

1. "What is my GPA?": the model calls the tool, then answers "Your cumulative GPA is 2.57." The answer is returned; two provider requests; the first request declares nine tools (progress active, knowledge not active) and its system prompt contains `ACADEMIC PROGRESS RULES`.
2. The model answers "Your GPA is 3.10.": the progress part of `groundedAnswer` is returned.
3. `cgpa` null and the model answers "Your GPA is 2.57.": rejected, the progress part says "not currently available".
4. The model answers "…, so you are eligible to graduate.": rejected (`STANDING`).
5. The query fails with a database error: `academicServiceUnavailable()`, one provider request.
6. Progress not active: the system prompt and the tool list are as before this step; a call of the tool by the model gives `UNSUPPORTED_AI_TOOL`.
7. Both tools active: ten tools in the order of section 17.2; the system prompt has `KNOWLEDGE_PROMPT` before `PROGRESS_PROMPT`.

### 17.7 Through the Spring context (`AssistantAcademicProgressIntegrationTests`)

Extends `IntentEvaluationSupport` (or `IntegrationSupport` with the base fixture), activates the tool as in section 5.2, uses `AcademicProgressFixture` and a scripted `LlmClient`.

- Student A: the tool result the model receives has the values of section 15.1.
- Student B: `cgpa` and `creditsEarned` null, `dataStatus` `PARTIAL`; a scripted answer with a GPA number is replaced by the progress part.
- The tool result contains no student id, name, code or course.
- A database failure through a `@Primary` wrapper around `AcademicProgressQueryServiceImpl` (like `FailingAcademicQueryService` in `GroundingEvaluationTests`) gives `academicServiceUnavailable()`.
- No token: 401. An Admin token: 403 (existing rules).
- The log of a request contains `tool=get_my_academic_progress` and none of the values `2.57` or `18`.

### 17.8 Existing tests

Every existing test passes without being edited, because it runs with the tool not active (section 5.2). The only allowed change outside new files is the property line in `IntegrationSupport` and the new overload in `AssistantTestServices`. List every touched test file and why. Compare the failing test ids with the baseline.

---

## 18. Postman Verification

Preparation:

- The product owner applies `docker/dev-fixtures/step16-academic-progress-test-data.sql` (its header shows the command). `AI_ACADEMIC_PROGRESS_ENABLED` is `true` or not set. Rebuild and restart the backend after the implementation.
- Log in as `std-test-016@rsu.ac.th` (password of `step6-assistant-test-data.sql`) and use the Student access token.
- Each question costs about two chat requests of the provider.

All requests:

```http
POST http://localhost:8080/api/v1/student/assistant/chat
Authorization: Bearer <student-access-token>
Content-Type: application/json
```

**1. Grade point average**

```json
{ "message": "What is my GPA?" }
```

Expected: HTTP 200. The answer says 2.57. Log: `ASSISTANT_TOOL … tool=get_my_academic_progress success=true … evidence=VERIFIED`.

**2. Earned credits**

```json
{ "message": "How many credits have I earned?" }
```

Expected: HTTP 200. 18 credits, from recorded course results.

**3. Current load**

```json
{ "message": "How many credits am I taking this semester?" }
```

Expected: HTTP 200. The value from the fixture header (6 when CS301 and CSC452 have 3 credits each), two courses.

**4. No grading data**

Log in as `std-test-001@rsu.ac.th` and send request 1.

Expected: HTTP 200. The answer says that the GPA is not currently available, unless `step13b-student-mobile-test-data.sql` gave this Student finalized results; then it states the value that `GET` progress of the mobile API shows for this Student. Never 0.

**5. No eligibility claim**

As `std-test-016@rsu.ac.th`:

```json
{ "message": "Is my GPA good enough to graduate?" }
```

Expected before spec file 57: HTTP 200. The answer gives 2.57 and does not say that the Student is eligible or will graduate. If it does, the backend replaces it with the progress part (log `ASSISTANT_GROUNDING_REJECTED … issues=[STANDING]`). After spec file 57 this question is a hybrid question (spec file 57, section 16).

---

## 19. Acceptance Criteria

- [ ] `get_my_academic_progress` exists only when `AI_ACADEMIC_PROGRESS_ENABLED` is true, has no argument, and returns exactly the seven values of spec file 40 for the token's Student.
- [ ] `AcademicQueryService`, `AcademicProgressService`, the calculator, the eight tools, `search_university_knowledge`, `SYSTEM_PROMPT`, `KNOWLEDGE_PROMPT` and the intent catalogue are unchanged.
- [ ] `PROGRESS_PROMPT` is added after `KNOWLEDGE_PROMPT` when the tool is active.
- [ ] Null values reach the model as null and are rendered as "not currently available"; nothing is defaulted to 0.
- [ ] Progress values are facts of the answer check; the wide credit and GPA patterns apply after the tool; `STANDING` claims are rejected.
- [ ] A database failure of the tool fails closed with `academicServiceUnavailable()`.
- [ ] No progress value is logged or stored outside the answer text.
- [ ] Every existing test passes without being edited (except the two allowed lines of section 17.8); no new failing test id compared with the baseline.
- [ ] `.env.example` and `compose.yaml` updated; the development fixture is written and not applied.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 20. Agent Completion Report

Report: files created and modified; the final tool definition and `PROGRESS_PROMPT`; how activation was implemented and which constructors were kept; the result of every row of sections 10.2 and 10.3; the values that `AcademicProgressFixture` produced; the expected values written into the development fixture and any code or number that had to change; existing test files that were touched and why; the Postman results actually performed, with the provider requests used; failing test ids against the baseline; decision entries added to `00-changes-and-decision.md`.

Report honestly what the check cannot see (section 14).

**Next: spec file 57 — Step 16B: Hybrid Answers.**
