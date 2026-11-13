# 57 — Step 16B: Build Hybrid Database + RAG Assistant — Hybrid Answers

**Project:** Smart AI University Student Assistant
**Module:** Student AI Assistant → personalized answers from the Student's records and the university documents together
**Scope:** Recognizing a question about the Student's own standing against a requirement, fetching the Student's verified progress before the model answers, the rules for combining it with document passages, the answer check for the few values that may be derived, the deterministic replies, and an evaluation.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec files 53 to 56 are implemented.
**Status:** Specification only, written 2026-11-10. Nothing in this file is implemented yet.

Roadmap: Step 15: 53 knowledge search tool → 54 curriculum-aware retrieval → 55 curriculum questions. Step 16: 56 academic progress tool → **57 hybrid answers (this file)**.

---

## 1. Objective

```text
How many credits do I still need to graduate?
        │
        ├── Student Academic Data   → PostgreSQL   get_my_academic_progress, run by the backend before the model
        ├── Graduation Requirements → RAG          search_university_knowledge, run by the model
        │
        └── Both Results → LLM → Personalized Answer → deterministic check → Student
```

Goal of Step 16: the strongest version of the Academic AI Assistant. A Student asks about their own standing and gets one answer that states the requirement from the document, the Student's own verified value, and at most one simple difference, without a guess and without a promise.

Example for Student A of the test data (18 credits earned, 9 credits this semester, cumulative GPA 2.57, program with a curriculum of 132 credits):

```text
Student: How many credits do I still need to graduate?

Assistant: Your program's curriculum requires at least 132 credits (Sample B.Sc. Computer Science Curriculum 2026,
Section 1). You have earned 18 credits from recorded course results, so at least 114 more credits remain. If you pass
the 9 credits you are taking now, at least 105 more credits will remain. The university regulations require at least
120 credits for all students, but the value for your program is 132. The registrar's office can confirm your
remaining requirements with an official degree audit.

sources: Sample Academic Regulations 2026 (2026-2027), Sample B.Sc. Computer Science Curriculum 2026 (2026-2027)
```

What the backend guarantees:

- the Student's progress comes from PostgreSQL for this request, for the token's Student, before the model writes anything;
- the requirement comes from a search of this request (the search is required, spec file 53, section 10.4);
- every number of the answer is a value of the passages, a value of the progress, or one of the derived values of section 9.2;
- the answer never says that the Student is eligible, has met all requirements or will graduate (spec file 56, section 10.3);
- when something is missing, the Student gets a fixed reply built from verified data only.

---

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `Agent.md`, spec files 53 to 56 and the decision entries of their implementations, and spec file 36 sections 64 to 68 (the Step 11 evaluation).
2. Inspect `StudentAssistantServiceImpl` (the loop of spec file 53, section 10.4, the schedule path that already runs a tool without the model), `UniversityKnowledgeQuestionDetector`, `PersonalAcademicQuestionDetector`, `AcademicGroundingPolicyImpl`, `GroundedAnswerValidatorImpl`, `SafeAcademicResponseFactoryImpl`, `AssistantProgressSettings`, `AcademicProgressFixture`, and the evaluation classes named in spec file 55, section 2.
3. Record the test baseline before changing anything. Compare failing test ids with it.

Do not:

- Change `SYSTEM_PROMPT`, `KNOWLEDGE_PROMPT`, `PROGRESS_PROMPT`, the tools, their schemas or descriptions, or the intent catalogue.
- Add a tool, an endpoint or a field of the chat request or response. `sources` stays as in spec file 53.
- Put a made-up tool call into the rounds sent to the provider. The backend's progress result reaches the model only through the system prompt block of section 8 (numbers and a status, no text of a person). A tool call that the model did not make has not been verified with the provider (D48).
- Let the backend state a derived value itself. Only the model writes the comparison; the backend only checks it.
- Change the Step 15 evaluation (spec file 55). It runs with the progress tool not active and stays as it is.
- Let a test call a real provider. The live check of section 17 is opt-in and is not run by the implementing agent.

---

## 3. What Stays and What Changes

| Stays | Changes |
|---|---|
| The ten tools and their results | Nothing |
| The flow of spec files 53 and 56 for every other question | A question about the Student's own standing is a **hybrid question** (section 6) with its own start (section 7) |
| The required search of spec file 53 | A hybrid question always requires the search |
| The answer check of spec files 53 and 56 | One more credit pattern, and three kinds of derived credit values when progress and passages are both verified (section 9) |
| The replies of spec files 53 and 56 | One new reply, `degreeAuditReferral()` (section 10) |
| `sources` | Nothing |

**With hybrid answers switched off (section 5), the assistant behaves exactly as after spec file 56.**

---

## 4. Package Structure

```text
config/
└── AiAssistantProgressProperties.java              (+ hybridEnabled)

feature/assistant/
├── grounding/
│   ├── AcademicGroundingPolicy.java                (+ messageAsksAboutOwnStanding)
│   ├── SafeAcademicResponseFactory.java            (+ degreeAuditReferral)
│   └── impl/
│       ├── AcademicGroundingPolicyImpl.java        (+ the new method)
│       ├── GroundedAnswerValidatorImpl.java        (CREDITS_MORE, derived values, section 9)
│       └── SafeAcademicResponseFactoryImpl.java    (+ degreeAuditReferral)
└── service/
    ├── AssistantProgressSettings.java              (+ hybridEnabled, of(active, hybridEnabled))
    └── impl/
        ├── HybridAcademicQuestionDetector.java     (new, section 6)
        └── StudentAssistantServiceImpl.java        (hybrid start, HYBRID_PROMPT, progress block, section 7)

src/test/java/…/feature/assistant/evaluation/
├── HybridEvaluationCase.java                       (new)
├── HybridEvaluationDataset.java                    (new)
├── HybridEvaluationDatasetTests.java               (new)
├── HybridEvaluator.java                            (new)
├── HybridEvaluationTests.java                      (new, deterministic, every test run)
└── GeminiLiveHybridEvaluationTests.java            (new, opt-in only)

src/test/resources/assistant/step16/
├── hybrid-cases.jsonl                              (new)
└── hybrid-expectations.json                        (new)
```

---

## 5. Configuration

`application.properties`:

```properties
# Step 16B: answers that combine the Student's progress with the university documents.
app.ai.progress.hybrid-enabled=${AI_HYBRID_ANSWERS_ENABLED:true}
```

`AiAssistantProgressProperties` gets `boolean hybridEnabled`. Add `AI_HYBRID_ANSWERS_ENABLED` to `.env.example` and to the `backend` service in `compose.yaml`.

`AssistantProgressSettings`:

```java
public boolean hybridEnabled();                                                    // active() && properties.hybridEnabled()
public static AssistantProgressSettings of(boolean active);                        // unchanged meaning: hybrid answers off
public static AssistantProgressSettings of(boolean active, boolean hybridEnabled);
```

`of(boolean)` keeps hybrid answers off, so every test of spec file 56 keeps its behaviour.

Hybrid answers need both features: `knowledge.active() && progress.hybridEnabled()`. With the knowledge base not active there is no requirement to compare with, and the question is handled as in spec file 56.

---

## 6. When a Question Is Hybrid

### 6.1 The detector

`HybridAcademicQuestionDetector` is a final class with one static method, in the style of `PersonalAcademicQuestionDetector`. The message is lower-cased (`Locale.ROOT`), whitespace runs become one space, and it is stripped. `null`, empty and blank messages return false.

```java
/** Whether the student asks how they themselves stand against a requirement: a credit total, a GPA, eligibility, graduation. */
public static boolean asksAboutOwnStanding(String message)
```

True when any rule holds:

| Rule | Condition |
|---|---|
| 1. English, direct | the text contains `(?<![a-z])(can i|will i|am i able to|am i going to|when will i|when can i)\s+graduate(?![a-z])` |
| 2. English, three parts | all three: a **first-person word** `(?<![a-z])(i|me|my|mine|myself|i'm|i’m|am i|do i|did i|have i|can i|could i|should i|will i)(?![a-z])`; a **topic** `(?<![a-z])(credit|credits|gpa|gpax|cgpa|grade point average|graduate|graduates|graduating|graduation|degree|requirement|requirements|cooperative education|co-op|coop|internship|internships)(?![a-z])`; a **standing word** `(?<![a-z])(need|needs|needed|left|remaining|remain|remains|still|enough|eligible|eligibility|qualify|qualified|meet|meets|met|on track|how far|how close|missing)(?![a-z])` |
| 3. Thai, phrase alone | the text contains one of: อีกกี่หน่วยกิต, ขาดกี่หน่วยกิต, ขาดอีกกี่หน่วยกิต, จบได้ไหม, จบได้หรือยัง, จะจบไหม, ผ่านเกณฑ์ไหม, ผ่านเกณฑ์หรือยัง |
| 4. Thai, phrase with a topic | one of พอไหม, พอหรือยัง, มีสิทธิ์ไหม, ครบไหม, ครบหรือยัง, and one of หน่วยกิต, เกรด, สหกิจ, ฝึกงาน, จบ |
| 5. Myanmar | the text contains ဘွဲ့ရနိုင်; or it contains လိုသေး or ကျန်သေး together with ခရက်ဒစ် or ဘွဲ့ |

The English patterns are the ones of the first-person list of `PersonalAcademicQuestionDetector` and two new lists. Keep them as constants of the new class; do not change the existing detector. A native speaker should review the Thai and Myanmar lists (section 15).

| Message | Hybrid |
|---|---|
| How many credits do I still need to graduate? | yes (rule 2) |
| How many credits do I need to graduate? | yes |
| How many credits are left in my program? | yes |
| Do I have enough credits for cooperative education? | yes |
| Am I eligible for co-op? | yes |
| Is my GPA high enough to graduate? | yes |
| Do I meet the GPA requirement? | yes |
| What GPA do I need to graduate? | yes |
| Have I met the graduation requirements? | yes |
| Can I graduate this year? / When will I graduate? | yes (rule 1) |
| ต้องเรียนอีกกี่หน่วยกิตถึงจะจบ | yes (rule 3) |
| เกรดเฉลี่ยของฉันผ่านเกณฑ์ไหม | yes (rule 3) |
| ဘွဲ့ရဖို့ ခရက်ဒစ် ဘယ်လောက် လိုသေးလဲ | yes (rule 5) |
| What are the requirements for my program? | no: no standing word (spec file 55 answers it) |
| What are the graduation requirements? | no: no first-person word |
| How many credits have I earned? / What is my GPA? | no: spec file 56 answers it |
| How many credits does the program require? | no |
| Graduates of this program need 132 credits | no |
| Which courses do I need for my major? | no: no topic (course-level planning is out of scope) |
| Which elective courses am I taking? | no |
| I need help | no |
| What does GPA mean? | no |

### 6.2 The policy

```java
/** Whether the student asks how they themselves stand against a requirement (spec file 57). */
boolean messageAsksAboutOwnStanding(String message);      // HybridAcademicQuestionDetector.asksAboutOwnStanding
```

`AcademicGroundingPolicyImpl` implements it and keeps its no-argument constructor.

### 6.3 The values in the chat service

```java
boolean hybrid           = knowledge.active() && progress.hybridEnabled()
                           && groundingPolicy.messageAsksAboutOwnStanding(message);
boolean personalEvidence = …unchanged…;
boolean documentEvidence = knowledge.active()
                           && (groundingPolicy.messageRequiresKnowledgeEvidence(message) || hybrid);
boolean searchRequired   = documentEvidence
                           && (groundingPolicy.messageRequiresKnowledgeSearch(message) || !personalEvidence || hybrid);
boolean needsEvidence    = personalEvidence || documentEvidence;                       // unchanged formula
```

A hybrid question always needs document evidence and always requires the search. "Can I graduate this year?" has no term of the knowledge detector, so without `|| hybrid` it would not search.

| Message (both features active, hybrid answers on) | `hybrid` | `searchRequired` |
|---|---|---|
| How many credits do I still need to graduate? | true | true |
| Can I graduate this year? | true | true |
| What are the requirements for my program? | false | true (spec file 53) |
| What is my GPA? | false | false |
| What are the graduation requirements? | false | true (spec file 53) |
| any message with hybrid answers off or the knowledge base not active | false | as in spec file 56 |

A wrong `true` costs one database query (no provider request) and the required search. A wrong `false` leaves the question to spec files 53 and 56: the model may still call both tools, and the answer check still applies, only without the derived values of section 9.2, so a correct "114 more credits" is replaced by the fixed reply.

---

## 7. The Hybrid Flow

In `StudentAssistantServiceImpl.chat`, after the unchanged schedule path (an unsupported schedule question is still answered first) and before the first provider request:

```text
1. if hybrid:
       toolCalls++
       execute(requestId, 0, new LlmToolCall("backend-progress", GET_MY_ACADEMIC_PROGRESS, Map.of()), evidence)
       progress = the evidence entry just added

2.     if progress.status() != VERIFIED:
           reply = safeResponses.academicServiceUnavailable()
           save, logGrounding and log exactly like a not-allowed decision of the existing tool branch
           (reason ACADEMIC_VERIFICATION_FAILED), sources [], no provider request, return

3.     grounding = GROUNDED
       the system prompt gets HYBRID_PROMPT and the progress block (section 8)

4. The loop of spec file 53, section 10.4, runs unchanged, with one addition in C (the model asked for tools):
       after executeAll and after groundingService.evaluate allowed the answer:
       if hybrid and searchRan(evidence) and no knowledge evidence is VERIFIED:
           reply = safeResponses.groundedAnswer(evidence)
                   (the progress part, then knowledgeNotFound() or knowledgeUnavailable(), spec file 53, section 13.2)
           handle like a notAvailable decision: save, log, return; sources []

5. The model answered with text (B of spec file 53, section 10.4):
       steps 1 to 3 do not apply (grounding is GROUNDED);
       step 4 demands the search once and falls back to groundedAnswer(evidence) + knowledgeMissingEvidence();
       step 5, the answer check: when it fails in a hybrid request,
           answer = safeResponses.groundedAnswer(evidence) + "\n\n" + safeResponses.degreeAuditReferral()
```

Notes:

- **Why the backend fetches the progress itself.** The values are then always there, for the token's Student, before the model writes anything; the model cannot forget the tool or call it with something else; and one tool round is saved. The schedule path of Step 11 already runs an approved tool without the model in the same way.
- **Why a failed progress query fails closed.** A personalized answer without the Student's data would look complete but is not. This is the Step 11 rule for a failed database tool.
- **Rule 4 replaces a second model request.** With the progress verified, `evaluate` would allow the answer (spec file 53, section 11.2, rule 3) and the model would be asked to compare with nothing. The backend answers instead: one provider request in total.
- **The model may still call `get_my_academic_progress`.** It is executed like any tool call; the evidence then holds two progress entries with the same values, and `groundedAnswer` renders one part.
- **Limits.** The backend call counts as one tool call for `AI_MAX_TOOL_CALLS` (default 8). It is no tool round. Step 4a of spec file 53, section 10.4 still checks that one more round is allowed.
- **Conversation.** Only the USER and ASSISTANT messages are saved (D51). The progress block is built again for every hybrid request and never stored. A follow-up that is not itself hybrid ("And if I pass this semester?") is handled as in spec file 56 (section 15).

---

## 8. Prompt

Add a constant `HYBRID_PROMPT` in `StudentAssistantServiceImpl`, a text block in the style of `KNOWLEDGE_PROMPT`, starting with two line breaks:

```text
PERSONAL STANDING RULES
This question asks how the student stands against a university or curriculum requirement. The backend has already
retrieved the student's progress for this request; the values are listed at the end of this block. For this
question, rules H1 to H7 allow one thing that rules 22 and 26 do not: comparing one verified value below with one
requirement of a passage.
H1. Call search_university_knowledge for the requirement, for example "credits required to graduate" or "cooperative
education requirements". Answer only from the passages and the values below. You do not need to call
get_my_academic_progress again.
H2. Name the requirement with its document, then the student's value. When both are credit numbers, you may give the
difference in words in one sentence, for example "140 credits are required and you have earned 45, so at least 95 more
credits remain". Write "at least", because some earned credits may not count toward this curriculum. Never write a
calculation with a hyphen or a minus sign between two numbers.
H3. Credits the student is taking now (currentEnrolledCredits) are not earned credits. Mention them only as "if you
pass the courses you are taking now", followed by the number that would then remain.
H4. Use only the values below. If a value needed for a comparison is null, say that it is not currently available and
give the requirement without a comparison. Do not calculate a grade point average, a percentage or a date, and do not
predict grades or a graduation date.
H5. Compare one value with one requirement at a time. Never say or suggest that the student is eligible, has met all
requirements, can graduate or will graduate, not even in a conditional sentence: required courses, grades in major
courses, cooperative education and outstanding debt are not in these values.
H6. When passages for "your program" and for "all students" give different values for the same requirement, compare
with the value for "your program" and mention the other value separately (rule 24). If no passage applies to the
student's program, say so and compare with the university-wide value, naming its document.
H7. End with one sentence saying that the registrar's office can confirm the student's remaining requirements with an
official degree audit.
```

The progress block follows directly after `HYBRID_PROMPT`. It is built by the backend from the map of the `VERIFIED` progress evidence (spec file 56, section 7.1), one line per key in this order, `toPlainString()` for numbers, `null` for a missing value:

```text

VERIFIED STUDENT PROGRESS (retrieved by the backend for this request; null means not currently available)
cgpa: 2.57
creditsEarned: 18
currentCourses: 3
currentEnrolledCredits: 9
studyYear: 3
academicYear: 2026
dataStatus: AVAILABLE
```

The block holds only these seven values: no name, no student code, no free text. It is never logged.

Why the example of rule H2 uses 140, 45 and 95: no sample document and no test Student has these numbers, so a copied example is rejected by the check instead of passing by chance.

Why rule H2 forbids a hyphen: "132-18" is read as a room number (spec file 53, section 12.4).

**The final order of the system prompt** (this replaces the snippets of spec files 53 and 56):

```java
String systemPrompt = SYSTEM_PROMPT
        + (knowledge.active() ? KNOWLEDGE_PROMPT : "")
        + (progress.active() ? PROGRESS_PROMPT : "")
        + (hybrid ? HYBRID_PROMPT + progressBlock(progressEvidence) : "")
        + references.promptContext(resolved, conversation);
// For the one request that demands the search (spec file 53, section 10.4): + KNOWLEDGE_RETRY_PROMPT
```

The prompt is completed after step 1 of section 7, because the block needs the result. Build it in two steps if the existing code builds the prompt earlier.

---

## 9. Checking a Hybrid Answer

### 9.1 One more credit pattern

"at least 114 more credits" has a word between the number and "credits", so `CREDITS_WIDE` does not read it. Add to the wide patterns (used under the condition `wideClaims` of spec file 56, section 10.2):

```text
CREDITS_MORE   (?i)(?<![\w.])(\d{1,3})\s+(?:more|additional|remaining|further|outstanding)\s+(?:credit|หน่วยกิต|ခရက်ဒစ်)
```

Every value it finds must be in `credits`, kind `CREDIT_HOURS`, like the other credit patterns. Thai and Myanmar ("อีก 114 หน่วยกิต", "114 ခရက်ဒစ်") are already read by `CREDITS_WIDE`.

### 9.2 Derived credit values

When the evidence holds a `VERIFIED` entry of `get_my_academic_progress` **and** a `VERIFIED` entry of `search_university_knowledge`, add to `credits`:

```text
E = creditsEarned of the last VERIFIED progress entry, when it is a whole number
C = currentEnrolledCredits of the same entry, when not null
P = the whole numbers of the VERIFIED passages (spec file 53, section 12.2, line 1, digits only, not number words)
    that also occur in the answer as whole numbers, found with (?<![\d.])(\d{1,6})(?!\d|\.\d)

derived:
    E + C                         when E and C exist
    |p − E|        for p in P     when E exists
    |p − (E + C)|  for p in P     when E and C exist
```

- Derived values are added to `credits` only, not to seats, sections or semesters.
- `p` must occur in the answer, so a remaining number is accepted only when the answer also states the number it was derived from. A good answer does that anyway (rule H2).
- No grade point average is derived. A GPA in the answer must be the Student's `cgpa` or a value of a passage.
- The derivation does not depend on the `hybrid` flag. It applies whenever both kinds of verified evidence are present, so an answer of the flow of spec file 56 in which the model called both tools itself is checked in the same way.

### 9.3 Examples

Evidence: Student A (`creditsEarned` 18, `currentEnrolledCredits` 9, `cgpa` 2.57) and the passages of query HQ1 (section 16.4: regulations Chapter 5 with 120 credits and 2.00; curriculum Sections 1, 5 and 6 with 132 credits, 90 credits and 2.00).

| Answer | Result |
|---|---|
| "…requires at least 132 credits. You have earned 18 credits, so at least 114 more credits remain." | grounded (132 − 18) |
| "…at least 132 credits… If you pass the 9 credits you are taking now, at least 105 more credits will remain." | grounded (132 − 27) |
| "…you will then have 27 credits." | grounded (18 + 9) |
| "…requires at least 132 credits… so at least 104 more credits remain." | rejected, `CREDIT_HOURS` |
| "You need 114 more credits." (132 is not in the answer) | rejected, `CREDIT_HOURS` |
| "You have earned 21 credits." | rejected, `CREDIT_HOURS` |
| "Your cumulative GPA is 2.75." | rejected, `GPA` |
| "You need 132-18 credits." | rejected, `ROOM` |
| "You have met all graduation requirements." | rejected, `STANDING` |
| "The university requires at least 120 credits; you have earned 18, so at least 102 more credits remain." | grounded: the wrong precedence is not caught (section 15) |

### 9.4 What is still not checked

- Whether the compared requirement is the right one: 102 (120 − 18) passes for a Student whose program requires 132. Rule H6 is the protection; the evaluation keeps this case as a known limit (HYB-029).
- Whether the sentence around a number says the right thing ("at least 114 more credits" against "114 credits too many").
- Statements without values, beyond the `STANDING` phrases.

---

## 10. Deterministic Replies

Add to `SafeAcademicResponseFactory` and its implementation:

| Method | Text |
|---|---|
| `degreeAuditReferral()` | "I could not reliably compare your records with these requirements. Please compare the values above yourself, or ask the registrar's office for an official degree audit." |

What a hybrid request returns when the backend answers:

| Situation | Reply | `sources` |
|---|---|---|
| The progress query did not return `VERIFIED` | `academicServiceUnavailable()` | `[]` |
| The search ran and found nothing | the progress part, then `knowledgeNotFound()` | `[]` |
| The search failed | the progress part, then `knowledgeUnavailable()` | `[]` |
| No search even after the demand | the progress part, then `knowledgeMissingEvidence()` (spec file 53, step 4b) | `[]` |
| The answer failed the check | the progress part, the passage block (spec files 53 and 55), then `degreeAuditReferral()` | the documents of the passages |

The last row counts as "the answer is `groundedAnswer(evidence)`" for the `sources` rule of spec file 53, section 14.

---

## 11. Sources

No change. A hybrid answer lists the documents of its passages exactly as in spec file 53. The progress values are not a source; the answer names them as the Student's records.

---

## 12. What Happens in Each Situation

| Situation | The Student gets | Chat requests | Embedding requests |
|---|---|---:|---:|
| Hybrid question, passages found, answer passes the check | the model's answer and `sources` | 2 | 1 |
| Answer fails the check | progress part, passages, `degreeAuditReferral()` | 2 | 1 |
| Nothing found | progress part and `knowledgeNotFound()` | 1 | 1 |
| Search failed | progress part and `knowledgeUnavailable()` | 1 | 0 or 1 |
| The model answers without searching | one request that demands the search, then one of the rows above | +1 | |
| Still no search | progress part and `knowledgeMissingEvidence()` | 2 | 0 |
| Progress query failed | `academicServiceUnavailable()` | 0 | 0 |
| Hybrid answers off, or the knowledge base not active | as after spec file 56 | | |
| Not a hybrid question | as after spec file 56 | | |

---

## 13. Logging

One new line for every hybrid request that ends with a reply:

```text
ASSISTANT_HYBRID requestId=… progress=VERIFIED dataStatus=AVAILABLE searches=1 knowledge=VERIFIED outcome=ANSWER
```

| Field | Values |
|---|---|
| `progress` | the status of the backend's progress evidence |
| `dataStatus` | `AVAILABLE`, `PARTIAL`, or `-` when not verified |
| `searches` | searches that ran (status not `ERROR`) |
| `knowledge` | `VERIFIED` when any search found passages, otherwise `VERIFIED_EMPTY`, `UNAVAILABLE` or `NONE` |
| `outcome` | `ANSWER`, `CHECK_FAILED`, `DOCUMENT_NOT_FOUND`, `DOCUMENT_UNAVAILABLE`, `SEARCH_MISSING`, `PROGRESS_UNAVAILABLE`, or `OTHER` for any other backend reply (for example another database tool failed) |

The backend's progress call also writes the existing `ASSISTANT_TOOL … round=0 tool=get_my_academic_progress …` line. Never logged: a progress value, the progress block, a query, a passage.

---

## 14. Privacy, Security and Cost

- **Who.** The progress is fetched for the token's Student only, by the backend, without any argument. The search scope comes from the Student's own profile (spec file 52). Nothing the Student, the model or a passage writes can change either.
- **What the chat provider receives.** The seven progress values in the system prompt, and the passages as tool results. No name, student code, email or course result. A hybrid request sends no more personal data than a request of spec file 56 in which the model calls the tool.
- **Instructions inside documents.** Unchanged (spec file 53, section 17). The progress block contains no text that a person wrote.
- **Cost.** A hybrid answer costs two chat requests and one embedding request, the same as a document answer. The progress query costs no provider request.

---

## 15. Known Limits and Out of Scope

Known limits:

- **Precedence is not checked** (section 9.4). Rules 24 and H6 are the protection.
- **"At least" is an estimate.** Earned credits that do not count toward the curriculum are not known (spec file 56, section 14).
- **Follow-up questions** that do not contain the hybrid words are not hybrid ("And if I pass this semester?" after a hybrid answer). The model may still call both tools; a correct derived number is then accepted only if both kinds of evidence are in that request (section 9.2).
- **The detector is a word list.** A wrong `true` costs one database query and one search; a wrong `false` falls back to spec files 53 and 56. The Thai and Myanmar lists need a native speaker's review.
- **`STANDING` is a phrase list** (spec file 56, section 14).

Out of scope (not part of Steps 15 and 16): a degree audit (which courses count toward which requirement), course recommendations or study plans, prerequisite checks against completed courses, what-if grade calculations, a predicted graduation date.

---

## 16. Evaluation Dataset (Deterministic)

### 16.1 Files and types

```text
src/test/resources/assistant/step16/hybrid-cases.jsonl          one case per line
src/test/resources/assistant/step16/hybrid-expectations.json    document titles, Student values, minimum counts, version "1.0"
```

```java
/** One Step 16 case: one question on a new conversation, the stand-in model's script, and what the Student must get. */
public record HybridEvaluationCase(String caseId, Category category, String language, String testStudent,
        String input, String modelBehavior, List<Map<String, Object>> script, Outcome expectedOutcome,
        List<String> mustContain, List<String> forbiddenFacts, List<String> expectedSources, List<String> expectedTools,
        Integer expectedProviderRequests, boolean knownLimit, String note) {

    public enum Category { CREDITS_REMAINING, GPA_STANDING, COOP_ELIGIBILITY, GRADUATION_STANDING, MISSING_PROGRESS,
        NO_PROGRAM_DOCUMENT, DOCUMENT_NOT_FOUND, SEARCH_MISSING, FAULTY_CALCULATION, FAULTY_CONCLUSION,
        PROGRESS_UNAVAILABLE, NOT_HYBRID, LANGUAGE }

    /** ANSWER: the last scripted text unchanged. CHECKED_REPLY: progress part, passages, degreeAuditReferral().
     *  DOCUMENT_NOT_FOUND: progress part and knowledgeNotFound(). MISSING_EVIDENCE: ends with knowledgeMissingEvidence().
     *  SERVICE_UNAVAILABLE: academicServiceUnavailable(). */
    public enum Outcome { ANSWER, CHECKED_REPLY, DOCUMENT_NOT_FOUND, MISSING_EVIDENCE, SERVICE_UNAVAILABLE }
}
```

The script format, `modelBehavior`, `mustContain`, `forbiddenFacts`, `expectedSources` and `expectedTools` are as in spec file 55, section 11.1. `expectedTools` lists the distinct tool names in call order, including the backend's progress call; `note` names the limit of a `knownLimit` case.

### 16.2 Fixture

`HybridEvaluationTests` extends `IntentEvaluationSupport` and adds:

- the `EmbeddingClient` bean replaced by `DeterministicEmbeddingClient`, and `app.knowledge.search.min-score=0.30`, as in spec file 55, section 11.2;
- a `@Primary AssistantProgressSettings` with `of(true, true)`;
- a `@Primary` wrapper around `AcademicProgressQueryServiceImpl` that can be told to fail for one case (like `FailingAcademicQueryService` in `GroundingEvaluationTests`), for the `PROGRESS_UNAVAILABLE` cases;
- `AcademicProgressFixture` (spec file 56, section 15.1), cleaned before the base fixture is created and after the class;
- the two sample documents as in spec file 55, section 11.2: "Sample Academic Regulations 2026" university-wide, "Sample B.Sc. Computer Science Curriculum 2026" for Student A's program.

Student values (checked by `AcademicProgressFixture.create()`):

| Student | Program | `cgpa` | `creditsEarned` | `currentEnrolledCredits` | Sees the curriculum |
|---|---|---|---|---:|---|
| `STD-TEST-001` (A) | Computer Science | 2.57 | 18 | 9 | yes |
| `STD-TEST-002` (B) | Information Systems | null | null | 9 | no |
| `STD-TEST-003` (C) | none | null | null | null | no |

### 16.3 Categories and minimum counts

| Category | Meaning | Minimum |
|---|---|---:|
| `CREDITS_REMAINING` | credits still needed | 5 |
| `GPA_STANDING` | the Student's GPA against a minimum | 3 |
| `COOP_ELIGIBILITY` | credits and GPA against the cooperative education condition | 3 |
| `GRADUATION_STANDING` | "Can I graduate …?" | 3 |
| `MISSING_PROGRESS` | the Student's values are null | 3 |
| `NO_PROGRAM_DOCUMENT` | no curriculum passage for the Student's program | 2 |
| `DOCUMENT_NOT_FOUND` | the search finds nothing | 2 |
| `SEARCH_MISSING` | the model does not search | 3 |
| `FAULTY_CALCULATION` | a wrong number | 4 |
| `FAULTY_CONCLUSION` | an eligibility or graduation claim | 3 |
| `PROGRESS_UNAVAILABLE` | the progress query fails | 2 |
| `NOT_HYBRID` | progress-only or document-only questions stay on their paths | 3 |
| `LANGUAGE` | Thai and Myanmar | 3 |

At least 42 cases in total. Use the variations of Step 9 for the wording.

### 16.4 Cases that must exist

Queries, with the results of a simulation of the deterministic client when this file was written (minimum score 0.30, own-program reserve 2):

| Query | Student A | Students B and C |
|---|---|---|
| HQ1 `A student must complete at least 132 credits to graduate from this program` | R4, C0, C6, C5 | R4, R3 |
| HQ2 `To register for CSC490 a student must have earned at least 90 credits` | C5, R1, R4, C6 | R1, R4 |
| HQ3 `cumulative grade point average of at least 2.00` | R4, C6, C5 | R4 |
| HQ4 `A student graduates from this program when all of these conditions are met` | C6, C0, R4, R3 | R4, R3 |
| HQ5 `นักศึกษาที่จะลงทะเบียนรายวิชา CSC490 ต้องมีหน่วยกิตสะสมไม่น้อยกว่า 90 หน่วยกิต` | C7 | none |
| HQ6 `parking permit for motorcycles` | none | none |

(R = regulations chunk, C = curriculum chunk, as in spec file 55, section 11.4.) R3 for Students B and C scores 0.315, close to the threshold; assert `mustContain` and `forbiddenFacts`, not exact passage lists.

| caseId | Category | Student | Input | Script after the backend's progress call | Outcome | Must contain / forbidden |
|---|---|---|---|---|---|---|
| HYB-001 | CREDITS_REMAINING | A | How many credits do I still need to graduate? | HQ1; then the example answer of section 1 | ANSWER, 2 requests, tools `get_my_academic_progress`, `search_university_knowledge`; sources regulations, curriculum | 114, 132 |
| HYB-002 | CREDITS_REMAINING | A | How many credits do I need to graduate if I pass this semester? | HQ1; then "…at least 132 credits. You have earned 18 credits and are taking 9 credits now. If you pass the courses you are taking now, you will have 27 credits, and at least 105 more credits will remain. …registrar…" | ANSWER | 105 |
| HYB-003 | COOP_ELIGIBILITY | A | Do I have enough credits to register for cooperative education? | HQ2; then "To register for CSC490 Cooperative Education, the curriculum requires at least 90 earned credits and a cumulative grade point average of at least 2.00 (Section 5). You have earned 18 credits, which is below 90, so you need at least 72 more credits first. Your cumulative GPA of 2.57 is above the 2.00 minimum. …registrar…" | ANSWER | 72 |
| HYB-004 | GPA_STANDING | A | Is my GPA high enough to graduate? | HQ3; then "Your program's curriculum (Section 6) and the university regulations both require a cumulative grade point average of at least 2.00. Your cumulative GPA is 2.57, which is above this minimum. The GPA is only one of the graduation conditions; …registrar…" | ANSWER | 2.57 |
| HYB-005 | GRADUATION_STANDING | A | Can I graduate this year? | HQ4; then "Your program's curriculum lists five graduation conditions (Section 6), including at least 132 credits and a cumulative grade point average of at least 2.00. You have earned 18 credits, so at least 114 more credits remain, and your cumulative GPA is 2.57. I can't tell from your records whether the other conditions are met. …registrar…" | ANSWER | 114 |
| HYB-006 | FAULTY_CALCULATION | A | How many credits do I still need to graduate? | HQ1; then "…at least 132 credits. You have earned 18 credits, so at least 104 more credits remain." | CHECKED_REPLY, sources filled | "Your academic progress:", "Here is what the university documents say:"; not 104 |
| HYB-007 | FAULTY_CALCULATION | A | How many credits have I got left to graduate? | HQ1; then "You have earned 21 credits, so at least 111 more credits remain." | CHECKED_REPLY | not 21, not 111 |
| HYB-008 | FAULTY_CALCULATION | A | Is my GPA high enough to graduate? | HQ3; then "Your cumulative GPA is 2.75, above the minimum of 2.00." | CHECKED_REPLY | not 2.75 |
| HYB-009 | FAULTY_CALCULATION | A | Do I have enough credits to register for cooperative education? | HQ2; then "You need 90 credits and have 18, so 62 more credits are needed." | CHECKED_REPLY | not 62 |
| HYB-010 | FAULTY_CONCLUSION | A | Can I graduate this year? | HQ4; then "Yes, you have met all graduation requirements." | CHECKED_REPLY | not "met all" |
| HYB-011 | FAULTY_CONCLUSION | A | Am I eligible for cooperative education? | HQ2; then "Your GPA of 2.57 is above 2.00, so you are eligible to register for CSC490." | CHECKED_REPLY | not "you are eligible" |
| HYB-012 | FAULTY_CONCLUSION | A | Can I graduate this year? | HQ4; then "With a cumulative GPA of 2.57, graduation this year looks likely." | ANSWER, `knownLimit`, note: a paraphrase is not a `STANDING` phrase | — |
| HYB-013 | MISSING_PROGRESS | B | How many credits do I still need to graduate? | HQ1; then "I found no curriculum passage for your program. The university regulations (Sample Academic Regulations 2026, Chapter 5) require at least 120 credits and a cumulative grade point average of at least 2.00 for all students. Your earned credits are not currently available, so I can't work out how many credits remain. …registrar…" | ANSWER; sources regulations | 120; not 132 |
| HYB-014 | MISSING_PROGRESS | B | How many credits do I still need to graduate? | HQ1; then "You have earned 45 credits, so at least 75 more credits remain." | CHECKED_REPLY | "not currently available"; not 45 |
| HYB-015 | MISSING_PROGRESS | B | Is my GPA high enough to graduate? | HQ3; then "The university regulations require a cumulative grade point average of at least 2.00. Your cumulative GPA is not currently available, so I can't compare it. …registrar…" | ANSWER | 2.00 |
| HYB-016 | NO_PROGRAM_DOCUMENT | C | Am I eligible for cooperative education? | HQ2; then "I found no curriculum passage for your program, and the university documents I found do not mention cooperative education. The registrar's office can tell you whether it applies to you." | ANSWER | not 90 |
| HYB-017 | NO_PROGRAM_DOCUMENT | C | How many credits do I still need to graduate? | HQ1; then the text of HYB-013 | ANSWER | 120; not 132 |
| HYB-018 | DOCUMENT_NOT_FOUND | A | Do I have enough credits for the parking permit? | HQ6 | DOCUMENT_NOT_FOUND, 1 request, sources `[]` | "Credits earned from recorded course results: 18" |
| HYB-019 | SEARCH_MISSING | A | How many credits do I still need to graduate? | text "You need about 114 more credits."; then the same text again | MISSING_EVIDENCE, 2 requests; the second request has `requireTool` and ends with `KNOWLEDGE_RETRY_PROMPT` | — |
| HYB-020 | SEARCH_MISSING | A | How many credits do I still need to graduate? | text; then HQ1; then the answer of HYB-001 | ANSWER, 3 requests | 114 |
| HYB-021 | SEARCH_MISSING | A | How many credits do I still need to graduate? | `get_my_academic_progress`; then text; then HQ1; then the answer of HYB-001 | ANSWER, 4 requests | 114 |
| HYB-022 | PROGRESS_UNAVAILABLE | A | How many credits do I still need to graduate? | the wrapper fails; script `[{"text": "unused"}]` | SERVICE_UNAVAILABLE, 0 requests, sources `[]` | — |
| HYB-023 | NOT_HYBRID | A | What is my GPA? | `get_my_academic_progress`; then "Your cumulative GPA is 2.57." | ANSWER, 2 requests; the progress tool ran once (no backend call); the system prompt has no `PERSONAL STANDING RULES` | 2.57 |
| HYB-024 | NOT_HYBRID | A | How many credits have I earned? | `get_my_academic_progress`; then "You have earned 18 credits from recorded course results, and you are taking 9 credits this semester." | ANSWER | 18 |
| HYB-025 | NOT_HYBRID | A | What are the requirements for my program? | HQ1; then an honest Step 15 answer with 132 | ANSWER; tools `search_university_knowledge` only | 132 |
| HYB-026 | LANGUAGE | A | ต้องเรียนอีกกี่หน่วยกิตถึงจะจบ | HQ1; then "หลักสูตรของคุณกำหนดให้เรียนไม่น้อยกว่า 132 หน่วยกิต คุณได้หน่วยกิตสะสมแล้ว 18 หน่วยกิต จึงยังต้องเรียนอีกอย่างน้อย 114 หน่วยกิต กรุณาตรวจสอบกับสำนักทะเบียน" | ANSWER | 114 |
| HYB-027 | LANGUAGE | A | เกรดเฉลี่ยของฉันผ่านเกณฑ์ไหม | HQ3; then "หลักสูตรและระเบียบของมหาวิทยาลัยกำหนดเกรดเฉลี่ยสะสมไม่ต่ำกว่า 2.00 เกรดเฉลี่ยสะสมของคุณคือ 2.57 ซึ่งสูงกว่าเกณฑ์นี้ กรุณาตรวจสอบเงื่อนไขอื่นกับสำนักทะเบียน" | ANSWER | 2.57 |
| HYB-028 | LANGUAGE | A | ဘွဲ့ရဖို့ ခရက်ဒစ် ဘယ်လောက် လိုသေးလဲ | HQ1; then "သင့်ပရိုဂရမ်အတွက် အနည်းဆုံး 132 ခရက်ဒစ် လိုအပ်ပါသည်။ သင် 18 ခရက်ဒစ် ရရှိပြီးဖြစ်သောကြောင့် အနည်းဆုံး 114 ခရက်ဒစ် ကျန်ပါသေးသည်။" | ANSWER | 114 |
| HYB-029 | CREDITS_REMAINING | A | How many credits do I still need to graduate? | HQ1; then "The university requires at least 120 credits. You have earned 18 credits, so at least 102 more credits remain." | ANSWER, `knownLimit`, note: precedence is not checked (section 9.4) | — |

"…registrar…" stands for the closing sentence of rule H7, for example "The registrar's office can confirm your remaining requirements with an official degree audit." Write the full texts into the dataset.

Each `FAULTY_CALCULATION` and `MISSING_PROGRESS` case with a wrong number names that number in `forbiddenFacts`. `HybridEvaluator` checks before grading that a forbidden number is not among the facts of the request (a self-check of the dataset): when it is, the case fails with "broken case: the wrong value is a fact", not with a grading result.

### 16.5 Grading

`HybridEvaluator` grades with deterministic checks only:

1. HTTP 200 and a message.
2. The outcome: ANSWER means the message equals the last scripted text after `strip()`; CHECKED_REPLY means it contains "Your academic progress:" and "Here is what the university documents say:" and ends with `degreeAuditReferral()`; DOCUMENT_NOT_FOUND means it starts with "Your academic progress:" and ends with `knowledgeNotFound()`; MISSING_EVIDENCE means it ends with `knowledgeMissingEvidence()`; SERVICE_UNAVAILABLE means it equals `academicServiceUnavailable()`.
3. Every `mustContain` is in the message; no `forbiddenFacts` is (whole-token matching of `IntentEvaluator.containsToken`).
4. `sources`, tools and provider requests as in spec file 55, section 11.5.
5. **The backend's progress call:** for every hybrid case the first tool call is `get_my_academic_progress` with the call id `backend-progress`, before the first provider request; for `NOT_HYBRID` cases there is none.
6. **No other program's data:** for Students B and C, neither the message nor any tool result contains the curriculum title, `132` or `CSC486`.
7. **No other Student's values:** for Students B and C, neither the message, any tool result nor any system prompt sent to the provider contains `2.57`.
8. **No identity sent to the provider:** no request to the provider contains the Student's code or email.

`HybridEvaluationTests` fails when a case fails. A `knownLimit` case passes when the backend behaves as the case describes and is listed under `knownLimits`.

Report in `target/step16-hybrid-evaluation/`: `summary.json` (cases, passed, pass rate per category, `faultyAnswersReplaced`, `crossProgramFindings`, `crossStudentFindings`, `identityFindings` (all three must be 0), `knownLimits`) and `case-results.jsonl`. These are deterministic results of a scripted model, not a measurement of Gemini; say so in `summary.json`.

### 16.6 Dataset test

`HybridEvaluationDatasetTests` (no Spring context): unique ids; every category meets its minimum and the total is at least 42; every script is non-empty and every tool name exists; `testStudent` is one of the three; every `knownLimit` case has a `note`; every case of section 16.4 exists; every input of a case that is not `NOT_HYBRID` is hybrid by `HybridAcademicQuestionDetector`, and every `NOT_HYBRID` input is not.

---

## 17. Live Check (opt-in)

`GeminiLiveHybridEvaluationTests` sends HYB-001, HYB-003, HYB-004 and HYB-026 for Student A through the real endpoint with the real chat model and the real embedding model.

```bash
AI_EVAL_ENABLED=true KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=GeminiLiveHybridEvaluationTests
```

- It runs only when both variables are `true`, like spec file 55, section 13.
- Budget: at most 10 chat requests and at most 6 embedding requests.
- It reports per case: whether the backend's progress call and the search ran, the outcome, which `mustContain` values the answer has, whether the answer was replaced, and the provider requests used. Report: `target/step16-hybrid-live/`.
- It fails only on a security finding (another program's or another Student's data, the Student's code or email in a provider request, a secret in a report). Answer quality is a measurement, not a build gate.

The implementing agent does not run it.

---

## 18. Tests

### 18.1 Detector and policy

- The table of section 6.1, plus at least five more messages per rule.
- Whole-word matching: "credited", "metadata", "remainder" and "needle" do not count as a topic or standing word.
- `null`, an empty text and a text of spaces return false.
- `messageAsksAboutOwnStanding` delegates to the detector. The table of section 6.3, computed exactly as in the chat service, is one parameterized test.

### 18.2 Settings

- `of(true)`: `hybridEnabled()` false. `of(true, true)`: true. `of(false, true)`: false. From properties: `enabled=true`, `hybrid-enabled=false` gives false.

### 18.3 Answer check

- Every row of the table of section 9.3.
- `CREDITS_MORE` reads "114 more credits", "3 additional credits" and "6 remaining credits"; it does not read "114 credits more" twice.
- No derived value without verified progress, without verified passages, with `creditsEarned` null or not whole, or when `p` is not in the answer.
- `currentEnrolledCredits` null: only `|p − E|` is derived.
- All existing validator tests, including those of spec files 53 and 56, pass unchanged.

### 18.4 Replies

- The text of `degreeAuditReferral()`.
- Each row of the table of section 10 through `groundedAnswer` and the service.

### 18.5 Chat service (built by hand, scripted model, a fake `KnowledgeSearchService`)

1. A hybrid question: the executor's first call is `get_my_academic_progress` with id `backend-progress`, before the first provider request. The first request's system prompt ends with the progress block, after `HYBRID_PROMPT`, after `PROGRESS_PROMPT`, after `KNOWLEDGE_PROMPT`. The model searches and answers; the answer is returned with sources; two provider requests; `toolCalls` is 2.
2. The progress query fails: `academicServiceUnavailable()`, no provider request, no search.
3. The search finds nothing: the progress part and `knowledgeNotFound()`, one provider request, `sources: []`.
4. The search fails: the progress part and `knowledgeUnavailable()`.
5. The model answers without searching: the next request demands the search (spec file 53, section 10.4); then search and answer; three provider requests.
6. The model answers twice without searching: the progress part and `knowledgeMissingEvidence()`, grounding `FALLBACK`.
7. A wrong remaining number: the progress part, the passage block and `degreeAuditReferral()`; `sources` filled.
8. A `STANDING` phrase: as 7.
9. Hybrid answers off (`of(true)` or `AI_HYBRID_ANSWERS_ENABLED=false`): no backend call, no `HYBRID_PROMPT`; the question goes through spec files 53 and 56.
10. Knowledge base not active: the same as 9.
11. An unsupported schedule question that also contains hybrid words: the schedule path answers, as before.
12. The model calls `get_my_academic_progress` itself in a hybrid request: it is executed; the check and `groundedAnswer` behave as with one entry.
13. The conversation saved to Redis holds the user message and the answer only; the next request's prompt has the block only if that request is hybrid too.
14. The log has one `ASSISTANT_HYBRID` line with the outcome and none of the values.

### 18.6 Evaluation

- `HybridEvaluationDatasetTests` (section 16.6).
- `HybridEvaluationTests`: every case passes; `crossProgramFindings`, `crossStudentFindings` and `identityFindings` are 0.

### 18.7 Existing tests

Every existing test passes without being edited, including the tests of spec files 53 to 56 and the Step 9, Step 11 and Step 15 evaluations. List every touched test file and why. Compare failing test ids with the baseline.

---

## 19. Postman Verification

Preparation:

- The product owner has done the steps of Step 14 (backup, rebuilt PostgreSQL image, backend with V48 to V51, `AI_EMBEDDING_MODEL` set).
- Both samples are `READY`: the regulations university-wide (spec file 51), the curriculum for program `CS-TEST` (spec file 54, section 15).
- `docker/dev-fixtures/step16-academic-progress-test-data.sql` is applied (spec file 56, section 15.2).
- `AI_KNOWLEDGE_ENABLED`, `AI_ACADEMIC_PROGRESS_ENABLED` and `AI_HYBRID_ANSWERS_ENABLED` are `true` or not set.
- Log in as `std-test-016@rsu.ac.th` (password of `step6-assistant-test-data.sql`). Values of this Student: `cgpa` 2.57, `creditsEarned` 18, current credits as written in the fixture header (6 when CS301 and CSC452 have 3 credits each).
- Each question costs two chat requests and one embedding request, more when the model has to be asked to search. The six checks use about 12 to 16 chat requests; run the first three first.

All requests:

```http
POST http://localhost:8080/api/v1/student/assistant/chat
Authorization: Bearer <student-access-token>
Content-Type: application/json
```

**1. Credits still needed (the example of Step 16)**

```json
{ "message": "How many credits do I still need to graduate?" }
```

Expected: HTTP 200. The answer states at least 132 credits from the curriculum, 18 earned credits, and at least 114 more credits; it may add the current credits (at least 108 more after 6 current credits). It may mention the university-wide 120 separately. It ends with the registrar sentence and does not say that the Student is eligible or will graduate. `sources` contains "Sample B.Sc. Computer Science Curriculum 2026". Log: `ASSISTANT_TOOL … round=0 tool=get_my_academic_progress … evidence=VERIFIED`, `ASSISTANT_TOOL … tool=search_university_knowledge …`, `ASSISTANT_HYBRID … progress=VERIFIED … outcome=ANSWER`.

**2. Grade point average against the minimum**

```json
{ "message": "Is my GPA high enough to graduate?" }
```

Expected: HTTP 200. 2.57 against the minimum of 2.00, that this is one of several conditions, the registrar sentence; no eligibility claim.

**3. Cooperative education**

```json
{ "message": "Do I have enough credits to register for cooperative education?" }
```

Expected: HTTP 200. At least 90 earned credits and a GPA of at least 2.00 for CSC490; 18 credits earned, so at least 72 more; 2.57 above 2.00.

**4. Thai**

```json
{ "message": "ต้องเรียนอีกกี่หน่วยกิตถึงจะจบ" }
```

Expected: HTTP 200. A Thai answer with 132, 18 and 114 หน่วยกิต.

**5. A Student without results**

Log in as `std-test-001@rsu.ac.th` and send request 1.

Expected: HTTP 200. The requirement of 132 credits, and that the earned credits are not currently available (unless finalized results were entered for this Student). No remaining number.

**6. Not hybrid**

As `std-test-016@rsu.ac.th`:

```json
{ "message": "What are the requirements for my program?" }
```

Expected: HTTP 200. The Step 15 answer about the curriculum, without the Student's values. The log has no `ASSISTANT_HYBRID` line and no `round=0` progress call.

**7. The switch (optional)**

Set `AI_HYBRID_ANSWERS_ENABLED=false`, restart the backend and send request 1.

Expected: HTTP 200. No `round=0` progress call and no `ASSISTANT_HYBRID` line. The model may call both tools itself; if it writes a remaining number, the answer check still applies. Set the variable back afterwards.

---

## 20. Acceptance Criteria

- [ ] `HybridAcademicQuestionDetector` and `messageAsksAboutOwnStanding` follow section 6; the existing detectors are unchanged.
- [ ] A hybrid question fetches the Student's progress in the backend before the first provider request, fails closed when it is not verified, and always requires the search.
- [ ] The progress reaches the model only through the progress block of section 8; no made-up tool call is sent to the provider.
- [ ] `HYBRID_PROMPT` and the final prompt order are as in section 8; `SYSTEM_PROMPT`, `KNOWLEDGE_PROMPT` and `PROGRESS_PROMPT` are unchanged.
- [ ] A hybrid search without passages is answered by the backend with one provider request in total.
- [ ] The answer check accepts exactly the derived values of section 9.2 and rejects the faulty answers of section 9.3.
- [ ] A rejected hybrid answer is replaced by the progress part, the passages and `degreeAuditReferral()`.
- [ ] The dataset of section 16 exists with every case of section 16.4 and the minimum counts of section 16.3, and every case passes; the three finding counts are 0.
- [ ] With hybrid answers off, the assistant behaves exactly as after spec file 56.
- [ ] No progress value, progress block, query or passage is logged or stored in Redis.
- [ ] Every existing test passes without being edited; no new failing test id compared with the baseline.
- [ ] `.env.example` and `compose.yaml` updated; the live check was not run by the implementing agent.
- [ ] `./mvnw clean package` result reported truthfully.

---

## 21. Agent Completion Report

Report: files created and modified; the final `HYBRID_PROMPT`, the progress block of one test request (with test values only) and the final prompt order; the result of the tables of sections 6.1, 6.3 and 9.3 and of flows 1 to 14 of section 18.5; the evaluation summary (cases per category, pass rates, the three finding counts, `knownLimits`); any case whose passages differed from the simulation of section 16.4; existing test files that were touched and why; the Postman results actually performed with the provider requests used; failing test ids against the baseline; decision entries added to `00-changes-and-decision.md`.

Report honestly what the check cannot see (sections 9.4 and 15). Do not claim that a hybrid answer replaces an official degree audit.

**Step 16 is complete with this file.** Possible later steps, not part of Steps 15 and 16: a degree audit from completed courses and the curriculum structure, course recommendations, and a Student screen that shows the progress next to the curriculum.
