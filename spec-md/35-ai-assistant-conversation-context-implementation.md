# 36 — Step 10: AI Assistant Conversation Context Implementation

**Project:** Smart AI University Student Assistant  
**Module:** Student AI Assistant — Backend  
**Specification:** `spec-md/36-step-10-ai-assistant-conversation-context.md`  
**Technology:** Java 21, Spring Boot 4.x, Maven, PostgreSQL, Redis 8, JWT RS256, Gemini API  
**Implementation Stage:** Step 10 — Multi-Turn Conversation Context

---

## 1. Purpose

Implement short-lived conversation context for the existing Student AI Assistant.

Currently, the AI Assistant supports individual academic questions using:

- Gemini API
- Academic AI Intents
- Academic Tool Calling
- AcademicQueryService
- PostgreSQL

Step 10 must enable students to ask follow-up questions without repeating information already mentioned in the same conversation.

Example:

```text
Student:
"What courses do I have?"

AI:
"You are enrolled in:
1. CS301 — Data Structures
2. CSC452 — Mobile Programming"

Student:
"Who teaches the first one?"

AI:
"Your CS301 lecturer is Dr. Smith."

Student:
"Where is it?"

AI:
"Your CS301 class is in Room 11-402."
```

The AI must understand that:

```text
"the first one" → CS301

"it" → CS301
```

These references are derived from conversation context, not guesses.

### Primary Goal

Make the Student Assistant conversational while preserving academic accuracy, authentication, privacy, and controlled tool calling.

---

## 2. Existing Project Architecture

The existing implementation includes:

```text
Flutter Student Mobile App
        ↓
Student Authentication
        ↓
Spring Security / JWT RS256
        ↓
Student Assistant Chat API
        ↓
Gemini LLM
        ↓
Academic Tool Calling
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
Structured Academic Result
        ↓
Gemini Final Response
```

Step 10 introduces:

```text
Redis Conversation Context
```

Redis must be used only for temporary conversational state.

PostgreSQL remains the single source of truth for academic records.

---

## 3. Mandatory AI Coding Agent Instructions

Before implementation:

1. Read `AGENTS.md`.
2. Inspect the `spec-md/` directory.
3. Read Step 3 — AcademicQueryService.
4. Read Step 5 — Student AI Assistant Chat API.
5. Read Step 6 — Academic AI Tools.
6. Read Step 7 — LLM Tool Calling.
7. Read Step 8 — Academic AI Intents.
8. Read Step 9 — Natural Language Variation Testing.
9. Inspect the existing Student JWT and Spring Security implementation.
10. Inspect current Redis 8 configuration and authentication caching.
11. Inspect existing Gemini client abstractions.
12. Inspect actual request/response DTOs before making changes.
13. Reuse the existing backend structure and naming conventions.
14. Implement Step 10 without breaking Steps 5–9.
15. Run automated tests and verify the implementation.

### Critical Restrictions

Do NOT:

- Create a separate AI database.
- Create conversation tables in PostgreSQL.
- Store JWTs or API keys in conversation history.
- Allow students to choose a different Student ID.
- Replace Gemini tool calling with keyword matching.
- Reimplement existing academic repositories.
- Generate or execute AI-created SQL.
- Store permanent conversation history.
- Allow Redis context to override current PostgreSQL academic facts.
- Introduce Spring Session Redis solely for assistant conversation storage.
- Change Admin Academic Management functionality.

---

# Part A — Conversation Architecture

## 4. Required Architecture

Implement:

```text
Student Mobile App
        |
        v
POST /api/v1/student/assistant/chat
        |
        v
StudentAssistantController
        |
        v
StudentAssistantService
        |
        +------ CurrentStudentProvider
        |                  |
        |                  v
        |             Student JWT
        |
        +------ ConversationContextService
        |                  |
        |                  v
        |                Redis
        |
        +------ Gemini LLM
                           |
                           v
                    Academic Tool
                           |
                           v
                  AcademicQueryService
                           |
                           v
                       PostgreSQL
                           |
                           v
                  Structured Result
                           |
                           v
                       Gemini
                           |
                           v
                 Final AI Response
                           |
                           v
                Save Updated Context
                           |
                           v
                         Redis
                           |
                           v
                      Student App
```

The conversation context must support:

```text
Previous Student messages

Previous AI responses

Previously referenced courses

Previously selected course

Previously displayed course order

Previously referenced section

Short-lived conversation metadata
```

---

## 5. Redis Storage Key

Use the following exact key structure:

```text
student:assistant:conversation:<conversationId>
```

Example:

```text
student:assistant:conversation:9fa36c31-f92b-4d1e-9f4e-217d25362010
```

Each conversation must have a unique server-generated identifier.

Use UUID v4 or an equivalently unpredictable identifier.

Do not use predictable sequential IDs.

---

## 6. Conversation Ownership

Every Redis conversation must belong to one authenticated Student.

Example:

```text
conversationId:
9fa36c31-f92b-4d1e-9f4e-217d25362010

ownerStudentId:
15
```

The owner is stored internally in Redis.

It must never be supplied by Gemini or accepted from the request body.

When a Student submits an existing `conversationId`, the backend must:

```text
1. Authenticate Student using JWT

2. Resolve currentStudentId

3. Load Redis conversation

4. Verify ownerStudentId == currentStudentId

5. Continue only if ownership matches
```

If ownership does not match, reject access.

Use the same externally visible `404 Not Found` behavior for nonexistent and unauthorized conversation IDs to avoid revealing which conversations exist.

---

## 7. Conversation TTL

Recommended default:

```text
30 minutes
```

Use:

```text
AI_CONTEXT_TTL_MINUTES=30
```

A conversation expires automatically after 30 minutes without a successfully completed conversation turn.

### Sliding Expiration

Example:

```text
10:00 — Student begins conversation
         TTL 30 minutes

10:10 — Student sends another message
         TTL refreshed to 30 minutes

10:25 — Student sends another message
         TTL refreshed to 30 minutes

10:55 — Conversation expires if no further activity
```

Only a successful saved conversation turn should refresh the TTL.

Do not allow invalid or unauthorized requests to extend it.

Redis must automatically delete expired conversation keys.

No scheduled database cleanup job is required.

---

## 8. Conversation Limitations

Recommended defaults:

```text
Maximum recent conversation turns:
6

Maximum stored message pairs:
6

Maximum stored history messages:
12

Maximum referenced course entries:
20

Maximum serialized conversation size:
32 KB
```

Make the limits configurable.

The backend must trim old conversational messages before exceeding limits.

Prefer retaining recent conversational references over unrelated older messages.

Do not delete authentication records or other application Redis keys when trimming conversation context.

---

# Part B — Chat API Changes

## 9. Existing Endpoint

Keep:

```http
POST /api/v1/student/assistant/chat
```

No new public tool-calling endpoint is required.

Step 10 extends the existing request and response contracts.

---

## 10. Revised Request DTO

Current Step 5 request:

```json
{
  "message": "What courses do I have?"
}
```

Step 10 must support an optional `conversationId`.

Recommended:

```java
public record StudentAssistantChatRequest(

    @NotBlank
    @Size(max = 4000)
    String message,

    String conversationId

) {}
```

Validate the `conversationId` format in the service or with a project-compatible validation constraint.

Use a UUID-formatted value.

### Important

`conversationId` is optional.

If absent, start a new conversation.

If present, continue an existing conversation only after ownership validation.

The Student must never send:

```text
studentId

studentCode

role

systemPrompt

toolName

AI_API_KEY
```

---

## 11. First Chat Request

Request:

```http
POST /api/v1/student/assistant/chat
Authorization: Bearer <student-jwt>
Content-Type: application/json
```

Body:

```json
{
  "message": "What courses do I have?"
}
```

Expected behavior:

```text
No conversationId
        ↓
Generate new UUID
        ↓
Process question
        ↓
Execute academic tools
        ↓
Generate final response
        ↓
Save conversation in Redis
        ↓
Return conversationId
```

---

## 12. Revised Response DTO

Extend the existing Step 5 response.

Recommended:

```java
public record StudentAssistantChatResponse(

    String message,

    String requestId,

    String conversationId,

    String model,

    LocalDateTime createdAt

) {}
```

Keep all existing response fields and the project's standard API wrapper.

Do not expose the internal conversation owner, Redis key, or full stored context.

---

## 13. First Response Example

```json
{
  "success": true,
  "message": "Assistant response generated successfully",
  "data": {
    "message": "You are enrolled in CS301 and CSC452.",
    "requestId": "a41ac4e7-49da-4398-983f-4ff0feaf2077",
    "conversationId": "9fa36c31-f92b-4d1e-9f4e-217d25362010",
    "model": "configured-gemini-model",
    "createdAt": "2026-11-02T22:00:00"
  }
}
```

The example is illustrative; use the existing API wrapper and timestamp convention.

---

## 14. Follow-Up Request

The Mobile App must send the returned conversation ID.

```json
{
  "conversationId": "9fa36c31-f92b-4d1e-9f4e-217d25362010",
  "message": "Who teaches the first one?"
}
```

Expected:

```text
Resolve Student
        ↓
Load existing conversation
        ↓
Verify Student ownership
        ↓
Identify previous course list
        ↓
Resolve "first one"
        ↓
Use academic tool
        ↓
Retrieve current lecturer
        ↓
Generate grounded answer
        ↓
Update conversation
```

---

## 15. Third Message

Request:

```json
{
  "conversationId": "9fa36c31-f92b-4d1e-9f4e-217d25362010",
  "message": "Where is it?"
}
```

Expected:

```text
"it" refers to CS301
```

The backend must execute the appropriate academic query and return the current room information.

Do not reuse an old Redis room value as authoritative.

---

## 16. New Conversation

To start a new conversation, the Student sends:

```json
{
  "message": "What is my program?"
}
```

without `conversationId`.

The backend generates a new conversation.

Do not automatically reuse the previous context merely because the Student ID matches.

---

# Part C — Redis Data Model

## 17. Conversation Context Structure

Recommended internal record:

```java
public record AssistantConversationContext(

    int schemaVersion,

    String conversationId,

    Long studentId,

    Instant createdAt,

    Instant updatedAt,

    long version,

    List<ConversationMessage> messages,

    AcademicConversationReference academicReference

) {}
```

Use immutable DTOs/records where compatible with existing project conventions.

`studentId` is internal only.

Never return it in the public Chat response.

---

## 18. ConversationMessage

Recommended:

```java
public record ConversationMessage(

    String role,

    String content,

    Instant createdAt

) {}
```

Allowed persisted roles:

```text
USER

ASSISTANT
```

Do not persist provider-specific system messages or hidden reasoning as ordinary conversation history.

Do not store raw internal function-call payloads by default.

---

## 19. AcademicConversationReference

Create structured context for academic referents.

Recommended:

```java
public record AcademicConversationReference(

    String activeCourseCode,

    Long activeCourseSectionId,

    String lastAcademicIntent,

    List<PresentedCourseReference> lastPresentedCourses

) {}
```

This is a contextual pointer, not a copy of the academic database.

---

## 20. PresentedCourseReference

Recommended:

```java
public record PresentedCourseReference(

    int displayIndex,

    String courseCode,

    Long courseSectionId

) {}
```

The list must reflect the course order actually shown to the Student.

Example:

```json
[
  {
    "displayIndex": 1,
    "courseCode": "CS301",
    "courseSectionId": 15
  },
  {
    "displayIndex": 2,
    "courseCode": "CSC452",
    "courseSectionId": 18
  }
]
```

Do not assume that database query order always equals the order Gemini displayed.

For numbered academic lists, the backend must establish and preserve a deterministic display order. Instruct the LLM to retain that order and verify it where necessary. If the displayed order cannot be established reliably, request clarification instead of guessing what "the first one" means.

---

## 21. Example Redis Value

```json
{
  "schemaVersion": 1,
  "conversationId": "9fa36c31-f92b-4d1e-9f4e-217d25362010",
  "studentId": 15,
  "createdAt": "2026-11-02T15:00:00Z",
  "updatedAt": "2026-11-02T15:05:00Z",
  "version": 2,
  "messages": [
    {
      "role": "USER",
      "content": "What courses do I have?",
      "createdAt": "2026-11-02T15:00:00Z"
    },
    {
      "role": "ASSISTANT",
      "content": "1. CS301 — Data Structures\n2. CSC452 — Mobile Programming",
      "createdAt": "2026-11-02T15:00:03Z"
    },
    {
      "role": "USER",
      "content": "Who teaches the first one?",
      "createdAt": "2026-11-02T15:05:00Z"
    },
    {
      "role": "ASSISTANT",
      "content": "CS301 is taught by Dr. Smith.",
      "createdAt": "2026-11-02T15:05:02Z"
    }
  ],
  "academicReference": {
    "activeCourseCode": "CS301",
    "activeCourseSectionId": 15,
    "lastAcademicIntent": "GET_MY_COURSE_LECTURER",
    "lastPresentedCourses": [
      {
        "displayIndex": 1,
        "courseCode": "CS301",
        "courseSectionId": 15
      },
      {
        "displayIndex": 2,
        "courseCode": "CSC452",
        "courseSectionId": 18
      }
    ]
  }
}
```

All values above are examples only.

Do not persist Student passwords, JWTs, or private credentials.

---

# Part D — Backend Package Structure

## 22. Recommended Structure

Extend the completed assistant feature:

```text
feature/
└── assistant/
    ├── controller/
    │   └── StudentAssistantController.java
    │
    ├── dto/
    │   ├── StudentAssistantChatRequest.java
    │   └── StudentAssistantChatResponse.java
    │
    ├── service/
    │   ├── StudentAssistantService.java
    │   └── impl/
    │       └── StudentAssistantServiceImpl.java
    │
    ├── conversation/
    │   ├── dto/
    │   │   ├── AssistantConversationContext.java
    │   │   ├── ConversationMessage.java
    │   │   ├── AcademicConversationReference.java
    │   │   └── PresentedCourseReference.java
    │   │
    │   ├── repository/
    │   │   ├── AssistantConversationRepository.java
    │   │   └── RedisAssistantConversationRepository.java
    │   │
    │   ├── service/
    │   │   ├── ConversationContextService.java
    │   │   ├── AcademicReferenceResolver.java
    │   │   └── impl/
    │   │       ├── ConversationContextServiceImpl.java
    │   │       └── AcademicReferenceResolverImpl.java
    │   │
    │   └── exception/
    │       ├── ConversationNotFoundException.java
    │       ├── InvalidConversationIdException.java
    │       ├── ConversationConflictException.java
    │       └── ConversationContextUnavailableException.java
    │
    ├── llm/
    │   ├── LlmClient.java
    │   └── GeminiLlmClient.java
    │
    ├── tool/
    │   ├── AcademicAiToolRegistry.java
    │   └── AcademicAiToolExecutor.java
    │
    └── config/
        └── AssistantContextProperties.java
```

Follow the existing Java package root and feature organization.

Do not duplicate the already implemented LLM or Academic Tool classes.

---

# Part E — Conversation Services

## 23. ConversationContextService

Recommended interface:

```java
public interface ConversationContextService {

    AssistantConversationContext createNew(
        Long studentId
    );

    AssistantConversationContext getOwnedConversation(
        String conversationId,
        Long studentId
    );

    void saveCompletedTurn(
        AssistantConversationContext context,
        ConversationMessage userMessage,
        ConversationMessage assistantMessage,
        AcademicConversationReference references
    );

}
```

The exact methods may be adjusted to support versioned/atomic writes.

The service must not accept `studentId` directly from the public request body.

---

## 24. Conversation Repository

Recommended abstraction:

```java
public interface AssistantConversationRepository {

    Optional<AssistantConversationContext> findById(
        String conversationId
    );

    boolean create(
        AssistantConversationContext context
    );

    boolean saveIfVersionMatches(
        AssistantConversationContext context,
        long expectedVersion
    );

}
```

Use optimistic concurrency or equivalent atomic synchronization.

Do not implement a simple read-modify-write operation that can silently overwrite concurrent conversation turns.

---

## 25. Redis Implementation

Reuse the current project's Redis connection configuration.

Preferred options:

```text
StringRedisTemplate

Jackson JSON serialization

Typed DTO mapping
```

Avoid fragile Java native serialization and untyped cached object casting.

Use:

```text
student:assistant:conversation:
```

as the conversation key namespace.

Do not interfere with existing Redis keys for:

```text
Student refresh token sessions

Admin refresh sessions

Application caching

Authentication rate limits
```

---

## 26. Conversation Persistence

Persist only after the backend has successfully:

```text
Authenticated Student

Validated conversation ownership

Completed Gemini tool calling

Generated a valid final response

Constructed safe context references
```

On successful save:

```text
Append USER message

Append ASSISTANT message

Update academic references

Increment version

Refresh Redis TTL
```

Do not append incomplete tool-calling messages as completed conversation turns.

If Gemini fails, do not advance the conversation history.

If Redis cannot save a conversation safely, return a controlled error rather than claiming the conversation was saved.

---

## 27. Concurrent Requests

Two requests may arrive with the same `conversationId`.

Example:

```text
Request A:
"Who teaches the first course?"

Request B:
"Where is the second course?"
```

Both must not overwrite each other's context.

Implement one of:

- Optimistic locking/version validation using atomic Redis operations.
- Per-conversation synchronization with bounded locking and safe lock ownership.
- Another existing project-compatible atomic update mechanism.

Recommended initial approach:

```text
Conversation version check
        ↓
Atomic write if version matches
        ↓
Otherwise CONVERSATION_CONFLICT
```

Use Redis transactions or a correctly implemented atomic script if required.

Do not retry by blindly appending stale conversation state.

---

# Part F — Follow-Up Understanding

## 28. AcademicReferenceResolver

Implement a reusable internal component:

```java
public interface AcademicReferenceResolver {

    AcademicConversationReference resolve(
        String currentMessage,
        AssistantConversationContext context
    );

}
```

The implementation may use the LLM's interpreted reference together with deterministic context validation.

Do not replace Gemini's natural-language understanding with a large keyword-based intent router.

The resolver is responsible for validating referents, not deciding every academic intent using hard-coded phrases.

---

## 29. "The First One" Resolution

Previous answer:

```text
1. CS301 — Data Structures

2. CSC452 — Mobile Programming
```

Student:

```text
"Who teaches the first one?"
```

Expected resolution:

```text
first one
    ↓
lastPresentedCourses[0]
    ↓
CS301
```

The resulting tool call must be equivalent to:

```json
{
  "name": "get_my_course_lecturer",
  "args": {
    "courseIdentifier": "CS301"
  }
}
```

The backend must verify that the course reference comes from the current Student's conversation or is otherwise a valid explicit request.

---

## 30. "The Second One" Resolution

Student:

```text
"Where is the second one?"
```

Expected:

```text
second one
    ↓
CSC452
    ↓
get_my_course_section("CSC452")
```

The final room must come from the current academic service result.

---

## 31. Pronoun Resolution

Previous answer:

```text
CS301 is taught by Dr. Smith.
```

Student:

```text
"Where is it?"
```

Expected:

```text
it
    ↓
activeCourseCode = CS301
    ↓
get_my_course_section("CS301")
```

The AI must not guess if the active reference is unclear.

---

## 32. Explicit Course Overrides Context

Previous active course:

```text
CS301
```

New Student question:

```text
"Who teaches CSC452?"
```

Expected:

```text
CSC452
```

The current explicit course reference overrides the previous active course.

After a successful response, the active reference may be updated to CSC452.

---

## 33. Ambiguous Follow-Up Questions

Student:

```text
"Where is it?"
```

without any usable conversation reference.

Expected:

```text
"Which course are you asking about?"
```

Do not select an arbitrary course.

If the previous response discussed multiple possible entities without a clearly established active one, ask for clarification.

---

## 34. Context Expiration

When Redis conversation state has expired:

```text
Student:
"Where is it?"
```

Expected:

```text
CONVERSATION_NOT_FOUND
```

or a safe user-facing message requesting the Student to start a new conversation.

Do not reconstruct an expired conversation from guesses or unrelated Student history.

---

# Part G — Academic Tools and Data Freshness

## 35. Existing Eight Academic Tools

Reuse exactly:

```text
get_my_academic_profile

get_my_enrollments

get_my_courses

get_my_schedule

get_my_course_section

get_my_course_lecturer

get_course_information

get_section_availability
```

Step 10 must not create duplicate tools solely to support pronouns.

---

## 36. Redis Is Not an Academic Source of Truth

Critical rule:

```text
Redis
→ Conversational references

PostgreSQL
→ Current academic facts
```

Example:

```text
Previous conversation:
CS301 Room 11-402

Academic Admin updates room:
11-405

Student:
"Where is it?"
```

Expected answer:

```text
CS301 is currently in Room 11-405.
```

Do not return the obsolete room from Redis.

Resolve the course from context and re-query `AcademicQueryService`.

---

## 37. Student Enrollment Changes

If a Student previously discussed CS301 but is no longer actively enrolled:

```text
Redis:
activeCourseCode = CS301
```

The backend must not treat this as proof of current enrollment.

Academic service authorization and current enrollment checks must still apply.

The AI should accurately explain that current enrollment details are unavailable or no longer match the Student's active records.

---

## 38. Structured Schedule Limitation

Current academic design stores:

```text
course_sections.schedule
```

as a free-text string.

Example:

```text
Tuesday 09:00-11:45
```

This supports displaying the recorded timetable.

It does not reliably support:

```text
"What classes do I have tomorrow?"

"What is my next class?"

"Which class starts first tomorrow?"
```

without date-aware schedule data.

Therefore:

**Do not pretend that Step 10 automatically implements tomorrow/next-class reasoning.**

Those questions require a reliable schedule representation, such as:

```text
course_section_schedules
```

with:

```text
course_section_id

day_of_week

start_time

end_time

room
```

and any required semester date range, exceptions, and calendar rules.

Schedule normalization must be implemented as a separately reviewed academic feature using Flyway before claiming those capabilities.

Do not parse arbitrary free-text schedules with fragile assumptions and present the results as verified.

---

## 39. Timezone for Future Date-Aware Queries

The project uses:

```text
Asia/Bangkok
```

For future supported date queries, resolve the requested academic date using a server-controlled `Clock` and the configured academic timezone.

Example concept:

```java
ZoneId zone = ZoneId.of("Asia/Bangkok");

LocalDate tomorrow = LocalDate
    .now(clock.withZone(zone))
    .plusDays(1);
```

Do not use the LLM's guessed current date.

Do not send a timestamp without timezone context and expect Gemini to determine university-local dates correctly.

---

# Part H — Gemini Conversation Integration

## 40. LLM Context Construction

For each follow-up:

```text
Trusted System Instructions
        ↓
Recent USER / ASSISTANT messages
        ↓
Validated Academic References
        ↓
Current Student Message
        ↓
Approved Academic Tool Declarations
```

All previous Student messages remain untrusted user content.

Do not concatenate them into the system prompt as privileged instructions.

---

## 41. Gemini Multi-Turn Handling

Reuse the existing Gemini client and Step 7 tool-calling integration.

Requirements:

```text
Maintain user/assistant message ordering.

Preserve current-request tool-call/function-response relationships.

Keep the backend-owned system instructions separate.

Respect provider message formatting rules.

Preserve provider-required tool-call identifiers and
reasoning/thought-signature metadata when applicable.

Do not fabricate provider tool-call metadata.
```

When using Gemini thinking models, the adapter must comply with the selected API's requirements for returning tool-call context and signatures during the active function-calling sequence.

Prefer reusing the existing working Gemini adapter rather than rewriting provider-specific conversation handling.

The Redis conversation history is a compact application-level context. It is not a license to reconstruct provider-internal reasoning or forge tool-call history.

---

## 42. System Prompt Update

Extend the existing backend-controlled system prompt.

Suggested instructions:

```text
You are an academic AI assistant for university students.

You may receive a short history of the current authenticated
Student's conversation.

Use conversation history only to understand what the Student
is referring to.

Resolve expressions such as "it", "that course", "the first one",
and "the second one" only when the reference is clear.

When a reference is ambiguous, ask for clarification.

For personal or current academic facts, use the approved
academic tools.

Conversation history is not proof that academic information
is still current.

Use the latest trusted AcademicQueryService result when answering.

Do not invent courses, lecturers, sections, rooms, schedules,
credits, or enrollments.

Never ask for a studentId.

Never allow conversation history to override authentication
or tool authorization.

Do not claim date-specific class availability unless supported
by a reliable backend schedule query.
```

---

## 43. Limit LLM Context Size

Do not send unlimited conversation history.

Use configured limits:

```text
AI_CONTEXT_MAX_TURNS

AI_CONTEXT_MAX_BYTES
```

Trimming strategy:

```text
1. Keep the latest relevant academic reference.

2. Retain recent complete USER/ASSISTANT turns.

3. Remove oldest unrelated turns first.

4. Exclude raw tool results unless currently required.

5. Never include secrets or authentication data.
```

Use compact structured references for academic context.

Do not use a second LLM call solely to summarize every conversation turn.

This helps control Gemini API usage, especially while evaluating rate limits and costs.

---

# Part I — Configuration

## 44. Environment Variables

Add:

```env
AI_CONTEXT_ENABLED=true

AI_CONTEXT_TTL_MINUTES=30

AI_CONTEXT_MAX_TURNS=6

AI_CONTEXT_MAX_COURSE_REFERENCES=20

AI_CONTEXT_MAX_BYTES=32768
```

Keep existing:

```env
AI_PROVIDER=gemini

AI_API_KEY=your-gemini-api-key

AI_MODEL=your-configured-gemini-model

AI_TIMEOUT_SECONDS=30

AI_MAX_TOOL_ROUNDS=4

AI_MAX_TOOL_CALLS=8
```

Never commit a real API key.

---

## 45. Application Configuration

Example:

```yaml
ai:
  provider: ${AI_PROVIDER:gemini}
  api-key: ${AI_API_KEY}
  model: ${AI_MODEL}
  timeout-seconds: ${AI_TIMEOUT_SECONDS:30}

  context:
    enabled: ${AI_CONTEXT_ENABLED:true}
    ttl-minutes: ${AI_CONTEXT_TTL_MINUTES:30}
    max-turns: ${AI_CONTEXT_MAX_TURNS:6}
    max-course-references: ${AI_CONTEXT_MAX_COURSE_REFERENCES:20}
    max-bytes: ${AI_CONTEXT_MAX_BYTES:32768}
```

Preserve the existing configuration layout if it differs.

---

## 46. AssistantContextProperties

Recommended:

```java
@ConfigurationProperties(prefix = "ai.context")
public record AssistantContextProperties(

    boolean enabled,

    int ttlMinutes,

    int maxTurns,

    int maxCourseReferences,

    int maxBytes

) {}
```

Validate configuration values at startup.

Reject negative TTLs, zero limits, and unreasonable context-size values.

---

# Part J — Error Handling

## 47. Required Error Codes

Implement or reuse:

```text
INVALID_CONVERSATION_ID

CONVERSATION_NOT_FOUND

CONVERSATION_CONFLICT

ASSISTANT_CONTEXT_UNAVAILABLE

AI_ASSISTANT_UNAVAILABLE

AI_REQUEST_TIMEOUT

AI_TOOL_EXECUTION_FAILED
```

Keep all existing authentication and academic errors.

---

## 48. HTTP Behavior

| Situation | HTTP |
|---|---:|
| New conversation success | 200 |
| Follow-up success | 200 |
| Invalid conversation ID format | 400 |
| Missing/invalid Student JWT | 401 |
| Inactive or forbidden Student | 403 |
| Conversation not found/expired | 404 |
| Conversation belongs to another Student | 404 |
| Concurrent version conflict | 409 |
| Redis context service unavailable | 503 |
| Gemini unavailable | 503 |

Do not expose internal Redis implementation details in API responses.

---

## 49. Redis Failure Behavior

If Redis is unavailable:

```text
Conversation load/save fails
        ↓
ASSISTANT_CONTEXT_UNAVAILABLE
```

Recommended behavior is a controlled `503` response.

Do not silently invent a missing conversation context.

Avoid returning a successful response that falsely promises a reusable conversation ID when its state could not be persisted.

---

# Part K — Testing

## 50. Test Strategy

Use:

```text
JUnit

Spring Boot Test

PostgreSQL Testcontainers

Redis Testcontainers

Mock Gemini LlmClient
```

or compatible existing project testing components.

Normal tests must not call the live Gemini API.

Live provider tests must remain separately enabled.

---

## 51. Conversation Creation Tests

```text
[ ] New request without conversationId creates a conversation

[ ] conversationId is a UUID

[ ] Response contains conversationId

[ ] Redis key is created

[ ] Redis value has authenticated Student owner

[ ] Initial user and assistant messages are stored

[ ] Redis TTL is configured

[ ] Existing Chat API authentication still works
```

---

## 52. Follow-Up Tests

Test this conversation:

```text
Student:
"What courses do I have?"

AI:
"1. CS301
 2. CSC452"

Student:
"Who teaches the first one?"

AI:
"CS301 is taught by Dr. Smith."

Student:
"Where is it?"

AI:
"Room 11-402."
```

Expected:

```text
[ ] Same conversationId across all three requests

[ ] First course resolves to CS301

[ ] Lecturer tool receives CS301

[ ] Pronoun resolves to CS301

[ ] Course section tool receives CS301

[ ] Responses match PostgreSQL

[ ] No duplicate or invented courses
```

This test uses the existing supported course-list API rather than unsupported "tomorrow" reasoning.

---

## 53. Context Switching Tests

Test:

```text
"What courses do I have?"

"Who teaches the second one?"

"Where is it?"

"Now who teaches CS301?"

"Where is that course?"
```

Verify that active course references update appropriately.

An explicit course identifier must override older context.

---

## 54. Expiration Tests

```text
[ ] Conversation expires after configured TTL

[ ] Successful follow-up refreshes TTL

[ ] Invalid request does not refresh TTL

[ ] Unauthorized request does not refresh TTL

[ ] Expired conversation returns 404

[ ] Expired state is not reconstructed from guesses

[ ] New request without conversationId creates fresh context
```

---

## 55. Student Isolation Tests

Create:

```text
Student A

Student B
```

Student A starts a conversation.

Student B attempts to use Student A's `conversationId`.

Expected:

```text
404 Not Found
```

Verify:

```text
[ ] Student B cannot read Student A's messages

[ ] Student B cannot continue Student A's conversation

[ ] Student B cannot change Student A's references

[ ] Student B cannot extend Student A's conversation TTL

[ ] No conversation contents leak through errors
```

---

## 56. Academic Freshness Tests

Test:

```text
Step 1:
Student asks where CS301 is located.

Database:
Room 11-402

Step 2:
Academic Admin changes room to 11-405.

Step 3:
Student asks "Where is it now?"
```

Expected:

```text
Room 11-405
```

The assistant must perform a fresh academic query.

---

## 57. Ambiguity Tests

```text
[ ] "Where is it?" without context requests clarification

[ ] "Who teaches the first one?" without displayed list requests clarification

[ ] Reference to an unavailable list position requests clarification

[ ] Explicit course code overrides ambiguous older context

[ ] Expired context is not used

[ ] Missing lecturer does not produce invented name

[ ] Missing room does not produce invented location
```

---

## 58. Concurrency Tests

```text
[ ] Two requests cannot overwrite one another silently

[ ] Context version increments correctly

[ ] Concurrent version conflict is detected

[ ] History remains correctly ordered

[ ] Redis TTL remains valid after atomic update

[ ] Failed write does not claim success
```

---

## 59. Privacy Tests

Verify Redis does not store:

```text
API key

JWT access token

Refresh token

Password hash

Temporary password

Database credentials

Student authentication secrets

Provider internal reasoning
```

Verify logs do not expose full private conversation history by default.

---

## 60. Conversation Size Tests

```text
[ ] Maximum turn limit enforced

[ ] Oldest irrelevant turns trimmed

[ ] Reference list limit enforced

[ ] Serialized size limit enforced

[ ] Large inputs do not cause unbounded Redis growth

[ ] Important current course reference remains available

[ ] Context truncation does not silently create false referents
```

---

## 61. Step 9 Evaluation Compatibility

Step 9 tests individual natural-language variations.

Do not accidentally share context across independent evaluation cases.

Default Step 9 behavior:

```text
One test case
        ↓
New conversation
        ↓
Evaluate result
```

Step 10-specific evaluation:

```text
Multiple related test cases
        ↓
Same conversationId
        ↓
Evaluate follow-up resolution
```

Keep single-turn and multi-turn research metrics separate.

---

# Part L — Postman Testing

## 62. Setup

Start:

```text
PostgreSQL

Redis

Spring Boot backend

Gemini API configuration
```

Login with a development test Student and obtain the JWT.

Use:

```http
POST http://localhost:8080/api/v1/student/assistant/chat

Authorization: Bearer <student-jwt>

Content-Type: application/json
```

---

## 63. Postman Test 1 — Start Conversation

```json
{
  "message": "What courses do I have?"
}
```

Expected:

```text
HTTP 200

Academic course list

conversationId returned
```

Save the returned `conversationId` as a Postman environment variable.

Example Postman Tests script:

```javascript
const body = pm.response.json();

pm.environment.set(
    "conversationId",
    body.data.conversationId
);
```

Adapt to the actual project response wrapper.

---

## 64. Postman Test 2 — Lecturer Follow-Up

```json
{
  "conversationId": "{{conversationId}}",
  "message": "Who teaches the first one?"
}
```

Expected:

```text
Correct first course identified

Academic lecturer tool executed

Correct lecturer returned
```

---

## 65. Postman Test 3 — Room Follow-Up

```json
{
  "conversationId": "{{conversationId}}",
  "message": "Where is it?"
}
```

Expected:

```text
Same course reference maintained

AcademicQueryService queried

Current course room returned
```

---

## 66. Postman Test 4 — Explicit Course Change

```json
{
  "conversationId": "{{conversationId}}",
  "message": "What about CSC452?"
}
```

If the request does not identify what information about CSC452 is wanted, the assistant should use the immediately preceding context when sufficiently clear or ask for clarification.

Do not invent an academic question or answer unsupported by the available context.

---

## 67. Postman Test 5 — New Conversation

Omit `conversationId`:

```json
{
  "message": "What is my academic program?"
}
```

Expected:

```text
New conversationId

No accidental reference to previous course conversation
```

---

## 68. Postman Test 6 — Cross-Student Security

1. Create a conversation with Student A.
2. Copy the conversation ID.
3. Login as Student B.
4. Submit Student B's JWT with Student A's ID.

Expected:

```text
404
```

No conversation data must be exposed.

---

## 69. Postman Test 7 — Expired Conversation

Use a short TTL in a dedicated test configuration.

After expiry, reuse the conversation ID.

Expected:

```text
CONVERSATION_NOT_FOUND
```

Do not wait for production's full 30-minute TTL during automated testing.

---

# Part M — Implementation Phases

## 70. Required Implementation Order

```text
PHASE 1
Inspect existing project and Steps 5–9
        ↓
PHASE 2
Inspect Redis configuration
        ↓
PHASE 3
Add AssistantContextProperties
        ↓
PHASE 4
Extend Chat Request DTO
        ↓
PHASE 5
Extend Chat Response DTO
        ↓
PHASE 6
Create ConversationContext DTO
        ↓
PHASE 7
Create ConversationMessage DTO
        ↓
PHASE 8
Create AcademicConversationReference
        ↓
PHASE 9
Create Redis Repository
        ↓
PHASE 10
Implement TTL and typed JSON serialization
        ↓
PHASE 11
Implement ownership validation
        ↓
PHASE 12
Implement concurrency/version handling
        ↓
PHASE 13
Create ConversationContextService
        ↓
PHASE 14
Implement creation and loading
        ↓
PHASE 15
Implement recent-message trimming
        ↓
PHASE 16
Implement AcademicReferenceResolver
        ↓
PHASE 17
Update Gemini conversation request construction
        ↓
PHASE 18
Preserve existing function-calling behavior
        ↓
PHASE 19
Update StudentAssistantService
        ↓
PHASE 20
Save completed conversation turns
        ↓
PHASE 21
Add error handling
        ↓
PHASE 22
Add safe logging
        ↓
PHASE 23
Create Redis tests
        ↓
PHASE 24
Create ownership/security tests
        ↓
PHASE 25
Create multi-turn conversation tests
        ↓
PHASE 26
Create context freshness tests
        ↓
PHASE 27
Create concurrency tests
        ↓
PHASE 28
Run automated tests and build
        ↓
PHASE 29
Run Postman multi-turn tests
        ↓
PHASE 30
Review all acceptance criteria
```

---

# Part N — Acceptance Criteria

## 71. Conversation API

```text
[ ] Existing chat endpoint remains unchanged

[ ] Optional conversationId accepted

[ ] New conversation created when ID absent

[ ] Existing conversation resumed when ID valid

[ ] Response includes conversationId

[ ] Request validation preserved

[ ] Student JWT required
```

## 72. Redis Context

```text
[ ] Redis key uses required namespace

[ ] Conversation ownership stored internally

[ ] TTL defaults to 30 minutes

[ ] TTL refreshed on successful completed turns

[ ] Expired keys automatically removed

[ ] Recent conversation history retained

[ ] Academic references retained

[ ] Old messages trimmed

[ ] Redis context size bounded

[ ] No PostgreSQL conversation table created
```

## 73. Academic Conversation

```text
[ ] Supports "the first one"

[ ] Supports "the second one"

[ ] Supports "it" when reference is clear

[ ] Supports explicit course switching

[ ] Ambiguous references request clarification

[ ] Academic facts queried from PostgreSQL

[ ] Existing eight academic tools reused

[ ] Redis never overrides current academic facts

[ ] Unsupported date-specific schedule claims prevented
```

## 74. Security

```text
[ ] Conversation owned by authenticated Student

[ ] Student cannot choose studentId

[ ] Student cannot read another Student's context

[ ] Student cannot update another Student's context

[ ] No JWT stored in conversation history

[ ] No API key stored in conversation history

[ ] No database credentials stored in Redis context

[ ] No secrets exposed through logging

[ ] No arbitrary Gemini SQL execution
```

## 75. LLM Integration

```text
[ ] Existing Gemini provider reused

[ ] Existing LlmClient abstraction preserved

[ ] Previous messages included with correct roles

[ ] Provider-specific function-call context handled correctly

[ ] Context limited to configured size

[ ] Trusted system instructions kept separate

[ ] Tool execution remains backend-controlled

[ ] Existing tool limits preserved
```

## 76. Testing

```text
[ ] Conversation creation tests pass

[ ] Follow-up tests pass

[ ] Reference resolution tests pass

[ ] Expiration tests pass

[ ] Student isolation tests pass

[ ] Redis serialization tests pass

[ ] Concurrency tests pass

[ ] Academic freshness tests pass

[ ] Ambiguity tests pass

[ ] Security tests pass

[ ] Postman end-to-end tests pass
```

## 77. Build Verification

Run:

```bash
mvn test
```

Then:

```bash
mvn clean package
```

Do not claim success unless the commands actually complete successfully.

---

# Part O — Required Agent Report

## 78. Completion Report

After implementation, provide:

```text
1. Files created

2. Files modified

3. Chat API DTO changes

4. Redis conversation key structure

5. TTL implementation

6. Student ownership validation

7. Conversation serialization strategy

8. Academic reference resolution implementation

9. Gemini multi-turn integration details

10. Existing tool-calling compatibility

11. Conversation concurrency handling

12. Redis error handling

13. Academic data freshness mechanism

14. Tests created

15. Test execution results

16. Postman multi-turn test results

17. Maven build result

18. Known limitations

19. Any deferred schedule-normalization work
```

Do not include actual API keys, JWTs, passwords, or private Student records in the report.

---

# 79. Final Step 10 Workflow

After Step 10, the backend should support:

```text
Student:
"What courses do I have?"
        ↓
Gemini
        ↓
get_my_courses
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
CS301 + CSC452
        ↓
AI Response
        ↓
Save Course References to Redis
        ↓
Return conversationId


Student:
"Who teaches the first one?"
        ↓
Send conversationId
        ↓
Load Redis Context
        ↓
Resolve first course = CS301
        ↓
get_my_course_lecturer
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
Dr. Smith
        ↓
AI Response
        ↓
Update Redis Context


Student:
"Where is it?"
        ↓
Send conversationId
        ↓
Load Redis Context
        ↓
Resolve active course = CS301
        ↓
get_my_course_section
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
Current Room 11-402
        ↓
AI Response
        ↓
Update Redis Context
```

---

# 80. Step 10 Completion Definition

Step 10 is complete when:

1. Students can start and resume short-lived conversations.
2. The backend stores conversation context in Redis using the required key format.
3. The backend resolves follow-up course references correctly.
4. Student JWT ownership is enforced.
5. Gemini continues using controlled academic tools.
6. PostgreSQL remains authoritative for academic answers.
7. Expired, missing, and ambiguous context is handled safely.
8. Context history remains limited and temporary.
9. Automated and Postman tests demonstrate multi-turn correctness.
10. The implementation does not claim unsupported tomorrow/next-class functionality.

**Final Goal:** Transform the Student AI Assistant from a single-question chatbot into a secure, context-aware, multi-turn academic assistant while preserving factual correctness and Student privacy.

---

# 81. Next Development Stage

After Step 10:

- Evaluate multi-turn conversation understanding.
- Test longer conversational sequences and context switching.
- Normalize course-section schedules if tomorrow/next-class functionality is required.
- Implement broader academic capabilities or Curriculum RAG in their respective steps.
- Continue using synthetic development data.
- After all project features are completed and tested, deploy to UAT, run Flyway migrations, import approved university data, and perform final real-data verification.

**Do not migrate real production Student data merely to implement Step 10.**