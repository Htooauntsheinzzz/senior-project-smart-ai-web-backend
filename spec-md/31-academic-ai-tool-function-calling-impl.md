# 32 - Academic AI Tool / Function Calling Implementation

## 1. Purpose

Implement:

# Step 6 — Academic AI Tool / Function Calling

for the:

**Smart AI University Student Assistant App**

Step 5 already provides:

```text
Student
    ↓
POST /api/v1/student/assistant/chat
    ↓
Spring Boot
    ↓
Gemini API
    ↓
General AI Response
```

Step 6 must extend the flow to:

```text
Student
    ↓
POST /api/v1/student/assistant/chat
    ↓
StudentAssistantService
    ↓
Gemini
    ↓
Academic Tool Call
    ↓
AcademicQueryService
    ↓
PostgreSQL
    ↓
Structured Academic Result
    ↓
Gemini
    ↓
Natural-Language Answer
```

The AI must never access PostgreSQL directly.

---

# 2. Main Goal

After Step 6, questions such as:

```text
"What courses am I enrolled in?"

"What is my schedule?"

"Who teaches CS301?"

"Which section am I in for CS301?"

"Where is my CS301 class?"

"How many credits is CS301?"

"Is section 15 full?"
```

must be answerable using trusted academic data from:

```text
PostgreSQL
```

through:

```text
AcademicQueryService
```

---

# 3. Important Architecture Rule

Use:

```text
Gemini
    ↓
Function / Tool Call
    ↓
Spring Boot Tool Executor
    ↓
AcademicQueryService
    ↓
PostgreSQL
```

Never:

```text
Gemini
    ↓
Generated SQL
    ↓
PostgreSQL
```

Gemini may select a tool.

Gemini must NOT:

```text
connect to PostgreSQL

execute SQL

access repositories directly

choose Student IDs

access arbitrary Spring services

call arbitrary URLs

execute arbitrary Java methods
```

---

# 4. Existing AcademicQueryService

Reuse the Step 3 service.

Do NOT rebuild the academic query logic.

Existing methods:

```java
AcademicProfileQueryResult getMyAcademicProfile();

List<AcademicEnrollmentQueryResult> getMyEnrollments();

List<AcademicCourseQueryResult> getMyCourses();

List<AcademicScheduleQueryResult> getMySchedule();

AcademicCourseSectionQueryResult getMyCourseSection(
    String courseIdentifier
);

AcademicLecturerQueryResult getMyCourseLecturer(
    String courseIdentifier
);

CourseInformationQueryResult getCourseInformation(
    String courseIdentifier
);

SectionAvailabilityQueryResult getSectionAvailability(
    Long courseSectionId
);
```

Step 6 must build the AI Tool layer around these methods.

---

# 5. Do Not Create New Public Academic Tool APIs

Do NOT create endpoints such as:

```text
POST /api/v1/student/assistant/tools

POST /api/v1/student/assistant/get-courses

POST /api/v1/student/tools/get-enrollments
```

Academic tools are:

```text
internal backend capabilities
```

The Student continues to call only:

```http
POST /api/v1/student/assistant/chat
```

---

# 6. Existing API Remains Unchanged

Request:

```http
POST /api/v1/student/assistant/chat
Authorization: Bearer <student-access-token>
Content-Type: application/json
```

Body:

```json
{
  "message": "What courses am I enrolled in?"
}
```

Do NOT add:

```text
studentId
toolName
courseId
databaseId
provider
apiKey
```

to the public request.

---

# 7. Student Identity

Student identity must continue to come from:

```text
JWT
    ↓
Spring Security
    ↓
SecurityContext
    ↓
CurrentStudentProvider
```

The AI model must never determine the current Student.

---

# 8. Critical studentId Rule

Tool input must NEVER contain:

```json
{
  "studentId": 15
}
```

Even if Gemini attempts to return:

```json
{
  "name": "get_my_courses",
  "args": {
    "studentId": 15
  }
}
```

the backend must reject the unexpected argument.

Student context is resolved internally.

---

# 9. Required Academic AI Tools

Implement exactly these initial tools:

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

---

# 10. Tool-to-Service Mapping

| AI Tool | AcademicQueryService |
|---|---|
| `get_my_academic_profile` | `getMyAcademicProfile()` |
| `get_my_enrollments` | `getMyEnrollments()` |
| `get_my_courses` | `getMyCourses()` |
| `get_my_schedule` | `getMySchedule()` |
| `get_my_course_section` | `getMyCourseSection(courseIdentifier)` |
| `get_my_course_lecturer` | `getMyCourseLecturer(courseIdentifier)` |
| `get_course_information` | `getCourseInformation(courseIdentifier)` |
| `get_section_availability` | `getSectionAvailability(courseSectionId)` |

Do not use reflection to automatically expose methods.

Use an explicit allowlist.

---

# 11. Tool 1 — get_my_academic_profile

Purpose:

```text
Retrieve the authenticated Student's academic profile.
```

Questions:

```text
"What program am I in?"

"What department am I in?"

"What faculty am I in?"

"What semester am I in?"

"What is my academic year?"
```

Arguments:

```json
{}
```

No arguments allowed.

Maps to:

```java
academicQueryService.getMyAcademicProfile();
```

---

# 12. Tool 2 — get_my_enrollments

Purpose:

```text
Retrieve the authenticated Student's current enrollment information.
```

Questions:

```text
"What courses am I enrolled in?"

"Show my enrollments."

"What sections am I currently enrolled in?"
```

Arguments:

```json
{}
```

Maps to:

```java
academicQueryService.getMyEnrollments();
```

---

# 13. Tool 3 — get_my_courses

Purpose:

```text
Retrieve courses belonging to the authenticated Student.
```

Questions:

```text
"What courses do I have?"

"List my courses."
```

Arguments:

```json
{}
```

Maps to:

```java
academicQueryService.getMyCourses();
```

---

# 14. Tool 4 — get_my_schedule

Purpose:

```text
Retrieve the authenticated Student's current academic schedule.
```

Questions:

```text
"What is my schedule?"

"Show my class schedule."
```

Arguments:

```json
{}
```

Maps to:

```java
academicQueryService.getMySchedule();
```

Important:

The current `course_sections.schedule` is still free-text.

Therefore do NOT claim support for advanced reasoning such as:

```text
"What is my next class?"

"What classes do I have tomorrow?"

"Do I have conflicting classes?"
```

unless schedule normalization has already been implemented.

---

# 15. Tool 5 — get_my_course_section

Purpose:

```text
Retrieve the authenticated Student's section
for a specific course.
```

Questions:

```text
"Which section am I in for CS301?"

"Where is my CS301 class?"

"What room is CS301 in?"
```

Arguments:

```json
{
  "courseIdentifier": "CS301"
}
```

Required:

```text
courseIdentifier
```

Maps to:

```java
academicQueryService.getMyCourseSection(
    courseIdentifier
);
```

---

# 16. Tool 6 — get_my_course_lecturer

Purpose:

```text
Retrieve the lecturer for the authenticated
Student's section of a course.
```

Questions:

```text
"Who teaches CS301?"

"Who is my CS301 lecturer?"
```

Arguments:

```json
{
  "courseIdentifier": "CS301"
}
```

Maps to:

```java
academicQueryService.getMyCourseLecturer(
    courseIdentifier
);
```

---

# 17. Tool 7 — get_course_information

Purpose:

```text
Retrieve general information about a course.
```

Questions:

```text
"How many credits is CS301?"

"What is CS301?"

"What are the prerequisites for CS301?"

"What type of course is CS301?"
```

Arguments:

```json
{
  "courseIdentifier": "CS301"
}
```

Maps to:

```java
academicQueryService.getCourseInformation(
    courseIdentifier
);
```

This tool may return non-private academic course metadata.

---

# 18. Course Eligibility Limitation

Existing prerequisite data may answer:

```text
"What prerequisites does CS301 have?"
```

It must NOT automatically answer:

```text
"Am I eligible to take CS301?"
```

unless the project later contains reliable:

```text
completed courses

grades

curriculum requirements

academic history
```

Do not infer eligibility from incomplete information.

---

# 19. Tool 8 — get_section_availability

Purpose:

```text
Return capacity and current occupied seats
for an active Course Section.
```

Arguments:

```json
{
  "courseSectionId": 15
}
```

Maps to:

```java
academicQueryService.getSectionAvailability(
    courseSectionId
);
```

Expected values include:

```text
capacity

occupiedSeats

availableSeats

full
```

Occupied seats remain derived from:

```text
PENDING + ACTIVE enrollments
```

and not from:

```text
WITHDRAWN
DROPPED
```

---

# 20. Tool Argument Schema

Tools must use strict, minimal argument definitions.

No-argument tools:

```text
get_my_academic_profile
get_my_enrollments
get_my_courses
get_my_schedule
```

Expected:

```json
{}
```

Course tools:

```text
get_my_course_section
get_my_course_lecturer
get_course_information
```

Expected:

```json
{
  "courseIdentifier": "CS301"
}
```

Availability:

```json
{
  "courseSectionId": 15
}
```

---

# 21. Reject Unknown Arguments

If Gemini generates:

```json
{
  "courseIdentifier": "CS301",
  "studentId": 100,
  "role": "ADMIN"
}
```

reject the tool arguments.

Do not silently use:

```text
studentId
role
```

Unknown fields must not affect execution.

---

# 22. courseIdentifier Validation

Validate:

```text
not null

not blank

trimmed

reasonable maximum length
```

Recommended:

```text
100 characters maximum
```

Do not accept enormous tool arguments.

---

# 23. courseSectionId Validation

Validate:

```text
not null

> 0
```

Example valid:

```text
15
```

Invalid:

```text
0

-1

null
```

---

# 24. Recommended Package Structure

Extend Step 5:

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
    ├── llm/
    │   ├── LlmClient.java
    │   ├── GeminiLlmClient.java
    │   ├── LlmRequest.java
    │   ├── LlmResponse.java
    │   ├── LlmToolCall.java
    │   ├── LlmToolResult.java
    │   └── LlmTurnResult.java
    │
    ├── tool/
    │   ├── AcademicAiToolNames.java
    │   ├── AcademicAiToolDefinition.java
    │   ├── AcademicAiToolRegistry.java
    │   ├── AcademicAiToolExecutor.java
    │   ├── AcademicAiToolResult.java
    │   └── impl/
    │       └── AcademicAiToolExecutorImpl.java
    │
    ├── config/
    │   └── AiAssistantProperties.java
    │
    └── exception/
        ├── UnsupportedAiToolException.java
        ├── InvalidAiToolArgumentsException.java
        ├── AiToolExecutionException.java
        └── AiToolCallLimitExceededException.java
```

Follow the project's actual package style.

---

# 25. Do Not Duplicate Academic Services

Do NOT create:

```text
AiEnrollmentRepository

AiCourseRepository

AiStudentRepository

AiAcademicQueryService
```

when the existing:

```text
AcademicQueryService
```

already owns academic query behavior.

The Tool layer must delegate to it.

---

# 26. AcademicAiToolNames

Recommended:

```java
public final class AcademicAiToolNames {

    public static final String GET_MY_ACADEMIC_PROFILE =
        "get_my_academic_profile";

    public static final String GET_MY_ENROLLMENTS =
        "get_my_enrollments";

    public static final String GET_MY_COURSES =
        "get_my_courses";

    public static final String GET_MY_SCHEDULE =
        "get_my_schedule";

    public static final String GET_MY_COURSE_SECTION =
        "get_my_course_section";

    public static final String GET_MY_COURSE_LECTURER =
        "get_my_course_lecturer";

    public static final String GET_COURSE_INFORMATION =
        "get_course_information";

    public static final String GET_SECTION_AVAILABILITY =
        "get_section_availability";

    private AcademicAiToolNames() {
    }
}
```

Avoid duplicated string literals throughout the project.

---

# 27. Academic Tool Registry

Create an explicit registry containing only approved tools.

Concept:

```java
public interface AcademicAiToolRegistry {

    List<AcademicAiToolDefinition>
        getAvailableTools();

    boolean isSupported(
        String toolName
    );

}
```

The registry must not dynamically scan:

```text
all Spring beans

all service methods

all repository methods
```

---

# 28. Academic Tool Definition

Recommended conceptual DTO:

```java
public record AcademicAiToolDefinition(

    String name,

    String description,

    Map<String, Object> parameterSchema

) {}
```

The provider adapter can convert this internal definition into Gemini's function declaration structure.

---

# 29. Provider Independence

Keep the Academic Tool layer independent from Gemini classes.

Correct:

```text
AcademicAiToolDefinition
        ↓
GeminiLlmClient
        ↓
Gemini function declaration
```

Avoid:

```text
Academic Tool Service
        ↓
Gemini SDK-specific model classes
```

throughout the business layer.

---

# 30. Gemini Integration

Step 5 already established:

```text
LlmClient
```

Step 6 extends that contract to support:

```text
tool declarations

tool calls

tool results

final response
```

If Step 5 currently uses:

```text
ExternalLlmClient
```

it may be refactored into:

```text
GeminiLlmClient
```

because Gemini is now the selected provider.

Do not break the provider-neutral service layer.

---

# 31. Recommended LLM Interaction Model

The internal LLM abstraction should distinguish between:

```text
Final text response

Tool call response
```

Concept:

```java
public record LlmToolCall(

    String callId,

    String name,

    Map<String, Object> arguments

) {}
```

and:

```java
public record LlmTurnResult(

    String text,

    List<LlmToolCall> toolCalls,

    String providerRequestId,

    String model

) {}
```

Exact implementation may differ depending on the current Step 5 code.

---

# 32. Tool Result Model

Recommended:

```java
public record LlmToolResult(

    String callId,

    String toolName,

    Object result

) {}
```

The result should contain only safe structured academic data.

---

# 33. Tool Execution Interface

Create:

```java
public interface AcademicAiToolExecutor {

    AcademicAiToolResult execute(
        LlmToolCall toolCall
    );

}
```

The executor must perform:

```text
tool allowlist validation

argument validation

domain call

result mapping

safe error mapping
```

---

# 34. Explicit Tool Execution

Conceptual implementation:

```java
switch (toolCall.name()) {

    case GET_MY_ACADEMIC_PROFILE ->
        academicQueryService
            .getMyAcademicProfile();

    case GET_MY_ENROLLMENTS ->
        academicQueryService
            .getMyEnrollments();

    case GET_MY_COURSES ->
        academicQueryService
            .getMyCourses();

    case GET_MY_SCHEDULE ->
        academicQueryService
            .getMySchedule();

    case GET_MY_COURSE_SECTION ->
        academicQueryService
            .getMyCourseSection(
                courseIdentifier
            );

    case GET_MY_COURSE_LECTURER ->
        academicQueryService
            .getMyCourseLecturer(
                courseIdentifier
            );

    case GET_COURSE_INFORMATION ->
        academicQueryService
            .getCourseInformation(
                courseIdentifier
            );

    case GET_SECTION_AVAILABILITY ->
        academicQueryService
            .getSectionAvailability(
                courseSectionId
            );

    default ->
        throw new UnsupportedAiToolException();
}
```

Do NOT execute arbitrary class/method names returned by Gemini.

---

# 35. No Reflection-Based Tool Execution

Do NOT do:

```java
Class.forName(toolName);

method.invoke(...);
```

Do NOT map model-controlled strings directly to:

```text
Spring bean names

repository methods

database queries
```

Use explicit mapping only.

---

# 36. Student Authentication Before Tool Calling

Before initial Gemini request:

```text
Resolve Student
        ↓
Validate account
        ↓
Build LLM request
```

Existing Step 5 rules still apply:

```text
ACTIVE
is_deleted = FALSE
ROLE_STUDENT
```

and the existing:

```text
forcePasswordChange
```

restriction.

---

# 37. Student Isolation

Critical requirement:

```text
Student A
```

must never receive:

```text
Student B academic information
```

All:

```text
get_my_...
```

tools obtain Student identity through:

```text
CurrentStudentProvider
```

not model arguments.

---

# 38. AI Tool Execution Flow

Required flow:

```text
1. Student sends message

2. Backend authenticates Student

3. Backend sends message + approved tool declarations to Gemini

4. Gemini either:
   A. returns final text
   OR
   B. requests one or more academic tools

5. Backend validates requested tool

6. Backend validates arguments

7. Backend executes AcademicQueryService

8. Backend serializes safe result

9. Backend sends tool result back to Gemini

10. Gemini generates final response
```

---

# 39. Example — Enrollment Question

Student:

```text
"What courses am I enrolled in?"
```

Expected:

```text
Gemini
    ↓
get_my_enrollments
    ↓
AcademicAiToolExecutor
    ↓
AcademicQueryService.getMyEnrollments()
    ↓
PostgreSQL
```

Example structured tool result:

```json
[
  {
    "courseCode": "CS301",
    "courseName": "Data Structures",
    "sectionNumber": "SEC-01",
    "status": "ACTIVE"
  },
  {
    "courseCode": "CSC452",
    "courseName": "Mobile Programming",
    "sectionNumber": "SEC-02",
    "status": "ACTIVE"
  }
]
```

Gemini may then answer:

```text
You are currently enrolled in:

1. CS301 — Data Structures, Section SEC-01
2. CSC452 — Mobile Programming, Section SEC-02
```

---

# 40. Example — Lecturer Question

Student:

```text
"Who teaches CS301?"
```

Gemini:

```json
{
  "name": "get_my_course_lecturer",
  "args": {
    "courseIdentifier": "CS301"
  }
}
```

Backend:

```java
academicQueryService
    .getMyCourseLecturer("CS301");
```

Example tool result:

```json
{
  "courseCode": "CS301",
  "courseName": "Data Structures",
  "sectionNumber": "SEC-01",
  "lecturerName": "Dr. John Smith"
}
```

---

# 41. Example — Class Location

Student:

```text
"Where is my CS301 class?"
```

Expected tool:

```text
get_my_course_section
```

Example result:

```json
{
  "courseCode": "CS301",
  "sectionNumber": "SEC-01",
  "room": "11-402",
  "schedule": "Tuesday 09:00-11:45"
}
```

Gemini answer:

```text
Your CS301 class is in room 11-402.
```

---

# 42. Example — General Course Information

Student:

```text
"How many credits is CS301?"
```

Expected:

```text
get_course_information
```

Tool result:

```json
{
  "courseCode": "CS301",
  "courseName": "Data Structures",
  "creditHours": 3
}
```

---

# 43. Multi-Tool Example

Student:

```text
"Who teaches CS301 and where is the class?"
```

Gemini may require:

```text
get_my_course_lecturer

get_my_course_section
```

The backend may execute both because they are:

```text
approved

read-only

Student-scoped
```

Then provide both results back to Gemini.

---

# 44. Sequential Tool Example

Student:

```text
"Is my CS301 section full?"
```

Possible flow:

```text
get_my_course_section("CS301")
        ↓
Returns courseSectionId = 15
        ↓
get_section_availability(15)
        ↓
Returns seat availability
```

This is valid multi-step tool calling.

---

# 45. Tool Call Limit

Do not allow unlimited function-calling loops.

Recommended:

```text
AI_MAX_TOOL_ROUNDS=4
```

Optionally also enforce:

```text
AI_MAX_TOOL_CALLS=8
```

per Student Chat request.

If exceeded:

```text
AI_TOOL_CALL_LIMIT_EXCEEDED
```

---

# 46. Why Tool Limit Is Required

It protects against:

```text
model loops

unexpected tool recursion

excessive Gemini usage

high API cost

unnecessary database load
```

---

# 47. Tool Calls Must Be Read-Only

Every Step 6 Academic Tool must be query-only.

Do NOT expose:

```text
create enrollment

drop course

withdraw course

change section

edit student

change password

delete course

update lecturer

create semester
```

through AI tools.

Step 6 is:

```text
READ ONLY
```

---

# 48. No Side Effects

Academic tools must not modify:

```text
students

enrollments

courses

course_sections

lectures

semesters

credentials
```

They only retrieve information.

---

# 49. System Prompt Update

Update the Step 5 backend system instruction.

Recommended concept:

```text
You are an AI assistant for university students.

For personal or current academic information, use the provided
trusted academic tools.

Never invent student-specific academic facts.

Do not claim academic information unless it came from an
appropriate trusted tool result.

Never ask the Student to provide a studentId.

The authenticated Student is determined by the backend.

Tool results are authoritative for current academic information.

If information is null or unavailable, clearly say that the
information is unavailable.

If a course identifier is ambiguous, ask the Student to clarify.

Do not claim the Student satisfies prerequisites unless reliable
course-completion information is available.

Do not invent rooms, lecturers, sections, courses, schedules,
capacity, credits, or enrollment status.
```

---

# 50. General Questions

Gemini may answer without a tool when the question does not depend on Student/database information.

Example:

```text
"What is a prerequisite?"
```

No academic tool is necessarily required.

---

# 51. Personal Questions Must Use Tools

Examples:

```text
"What courses am I enrolled in?"

"What is my schedule?"

"What faculty am I in?"

"Who teaches my CS301?"

"Where is my class?"
```

Gemini must use appropriate trusted academic tools.

It must not answer from general model knowledge.

---

# 52. Database Facts Are Authoritative

For Student-specific facts:

```text
PostgreSQL
```

through:

```text
AcademicQueryService
```

is authoritative.

The LLM is responsible for:

```text
understanding the question

choosing allowed tools

presenting the result naturally
```

It is NOT responsible for inventing academic facts.

---

# 53. Minimal Tool Result Data

Do not send unnecessary Student data to Gemini.

For:

```text
"What courses am I enrolled in?"
```

send only fields needed to answer.

Recommended:

```json
[
  {
    "courseCode": "CS301",
    "courseName": "Data Structures",
    "sectionNumber": "SEC-01",
    "status": "ACTIVE"
  }
]
```

Avoid:

```text
dateOfBirth

phoneNumber

passwordHash

JWT

refreshToken

failedLoginAttempts

lockedUntil

createdBy

updatedBy
```

---

# 54. Student ID in Tool Results

Prefer not to send:

```text
studentId
```

to Gemini when the answer does not require it.

The model already knows it is answering for:

```text
the authenticated Student
```

without needing the internal database ID.

---

# 55. Course Section ID

`courseSectionId` may be included where it is needed for chaining:

```text
get_my_course_section
        ↓
get_section_availability
```

Example:

```json
{
  "courseSectionId": 15,
  "courseCode": "CS301",
  "sectionNumber": "SEC-01"
}
```

Do not expose unrelated internal IDs.

---

# 56. Null Values

If:

```text
room = null
```

Gemini must say:

```text
The room information is not currently available.
```

Do not invent a room.

If:

```text
lecturer = null
```

do not generate a lecturer.

If:

```text
schedule = null
```

do not generate a schedule.

---

# 57. Empty Results

If Student has no active enrollments:

```json
[]
```

Gemini should answer semantically:

```text
You do not currently have any active enrollments.
```

It must not create fake courses.

---

# 58. Course Ambiguity

Existing AcademicQueryService may return:

```text
ACADEMIC_COURSE_AMBIGUOUS
```

Example:

```text
"Tell me my Programming course section."
```

when multiple Programming courses match.

The AI should ask a clarification question such as:

```text
I found more than one matching course.
Which course do you mean?
```

Do not guess.

---

# 59. Student Not Enrolled

If:

```text
getMyCourseSection("CS999")
```

results in:

```text
STUDENT_COURSE_ENROLLMENT_NOT_FOUND
```

Gemini should state that the Student does not have a matching enrollment.

Do not reveal another Student's enrollment.

---

# 60. Course Not Found

If:

```text
ACADEMIC_COURSE_NOT_FOUND
```

the AI should tell the Student that no matching course was found.

Do not invent a similar course.

---

# 61. Tool Errors

Recommended Step 6 error codes:

```text
UNSUPPORTED_AI_TOOL

INVALID_AI_TOOL_ARGUMENTS

AI_TOOL_EXECUTION_FAILED

AI_TOOL_CALL_LIMIT_EXCEEDED
```

Keep existing Step 5 errors:

```text
AI_ASSISTANT_UNAVAILABLE

AI_REQUEST_TIMEOUT

AI_PROVIDER_RATE_LIMITED

INVALID_AI_PROVIDER_RESPONSE
```

---

# 62. Unsupported Tool

If Gemini requests:

```text
delete_student

run_sql

get_all_students

change_grade
```

reject it.

Recommended:

```text
UNSUPPORTED_AI_TOOL
```

Do not attempt fallback execution.

---

# 63. Invalid Tool Arguments

Examples:

```json
{
  "courseSectionId": -10
}
```

or:

```json
{
  "studentId": 15
}
```

or:

```json
{
  "courseIdentifier": ""
}
```

Return internal:

```text
INVALID_AI_TOOL_ARGUMENTS
```

Do not execute the tool.

---

# 64. Tool Error Sent Back to Gemini

A safe structured tool error may be supplied to Gemini so it can produce a user-friendly response.

Example:

```json
{
  "success": false,
  "code": "ACADEMIC_COURSE_AMBIGUOUS",
  "message": "More than one course matches the supplied identifier."
}
```

Do NOT send:

```text
Java stack trace

SQL

database table details

repository class name

internal exception stack
```

---

# 65. Do Not Let Gemini Override Errors

If a tool returns:

```text
NOT_FOUND
```

Gemini must not proceed by inventing data.

System instructions must emphasize:

```text
Tool result is authoritative.
```

---

# 66. LLM Tool Loop

Recommended service logic:

```text
MAX_TOOL_ROUNDS = 4

Send initial message to Gemini

for each round:

    if final text:
        return final response

    if tool call:
        validate tool

        validate arguments

        execute tool

        create tool result

        send result back to Gemini

if max rounds exceeded:
    throw AI_TOOL_CALL_LIMIT_EXCEEDED
```

---

# 67. Multiple Tool Calls

If Gemini returns several supported read-only tool calls in one turn:

```text
validate every tool

validate every argument

execute approved calls

collect results

return results to Gemini
```

For the first implementation, sequential execution is acceptable.

Do not add unnecessary parallel execution complexity.

---

# 68. Long Database Transactions

Do not hold a database transaction open across:

```text
Gemini API request
```

Recommended:

```text
Gemini asks for tool
        ↓
Execute short AcademicQueryService operation
        ↓
DB transaction ends
        ↓
Send result to Gemini
```

---

# 69. Logging

Recommended logs:

```text
requestId

Student request started

model

tool round

tool name

tool execution duration

tool success/failure

Gemini duration

final status
```

---

# 70. Do Not Log Tool Output by Default

Tool results may contain Student academic information.

Avoid logging full:

```text
enrollments

schedule

profile

courses
```

Prefer:

```text
requestId=...
tool=get_my_courses
success=true
durationMs=...
resultCount=5
```

---

# 71. Never Log

Never log:

```text
AI_API_KEY

Student JWT

Refresh Token

Password

Password Hash

Provider Authorization Header
```

---

# 72. No Separate AI Database

Continue using:

```text
smart_university_db
```

Development:

```text
test academic data
```

Later UAT:

```text
approved real academic data
```

No:

```text
ai_academic_db
```

is required.

---

# 73. Test Data for Step 6

Continue using controlled development data.

Example Student:

```text
student_code:
STD-TEST-001

status:
ACTIVE

faculty:
Information Technology

department:
Computer Science

program:
Computer Science

semester:
Semester 1
```

---

# 74. Example Courses

Create/use test data:

```text
CS301
Data Structures
3 credits

CSC452
Mobile Programming
3 credits
```

---

# 75. Example Sections

```text
CS301
Section:
SEC-01

Room:
11-402

Schedule:
Tuesday 09:00-11:45

Lecturer:
Dr. John Smith
```

and:

```text
CSC452
Section:
SEC-02

Room:
5-201

Schedule:
Friday 09:00-11:45

Lecturer:
Dr. Jane Lee
```

---

# 76. Example Enrollments

```text
STD-TEST-001
→ CS301
→ SEC-01
→ ACTIVE
```

```text
STD-TEST-001
→ CSC452
→ SEC-02
→ ACTIVE
```

Use the normal application/API/test-fixture mechanism.

Do NOT create permanent production Flyway seed data merely for AI testing.

---

# 77. Automated Testing Strategy

Step 6 tests should NOT normally call the real Gemini API.

Mock/stub:

```text
LlmClient
```

Simulate Gemini tool calls.

This gives deterministic tests without:

```text
API cost

rate limits

network dependency
```

---

# 78. Tool Registry Tests

Verify exactly:

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

are registered.

Verify:

```text
delete_student
run_sql
get_all_students
```

are not supported.

---

# 79. Tool Executor Tests

Test:

```text
[ ] get_my_academic_profile mapping

[ ] get_my_enrollments mapping

[ ] get_my_courses mapping

[ ] get_my_schedule mapping

[ ] get_my_course_section mapping

[ ] get_my_course_lecturer mapping

[ ] get_course_information mapping

[ ] get_section_availability mapping
```

---

# 80. Argument Validation Tests

Test:

```text
[ ] courseIdentifier accepted

[ ] courseIdentifier trimmed

[ ] blank courseIdentifier rejected

[ ] null courseIdentifier rejected

[ ] unknown argument rejected

[ ] studentId argument rejected

[ ] positive courseSectionId accepted

[ ] zero courseSectionId rejected

[ ] negative courseSectionId rejected
```

---

# 81. Student Isolation Tests

Create:

```text
Student A
Student B
```

with different enrollments.

Verify:

```text
Student A JWT
        ↓
get_my_enrollments
        ↓
only Student A data
```

and:

```text
Student B JWT
        ↓
get_my_enrollments
        ↓
only Student B data
```

This is mandatory.

---

# 82. Hallucination Protection Tests

Simulate:

```text
Student:
"Who teaches CS301?"
```

Tool returns:

```json
{
  "lecturerName": null
}
```

Final assistant response must not invent:

```text
Dr. Someone
```

---

# 83. Ambiguity Tests

Simulate:

```text
ACADEMIC_COURSE_AMBIGUOUS
```

Verify final assistant asks for clarification.

It must not select a random matching course.

---

# 84. Tool Loop Tests

Verify:

```text
Gemini requests valid tool
        ↓
tool executes
        ↓
result returned
        ↓
Gemini final answer
```

Also verify:

```text
tool call
→ tool call
→ tool call
→ tool call
→ additional tool call
```

is stopped when:

```text
AI_MAX_TOOL_ROUNDS
```

is exceeded.

---

# 85. Security Tests

Verify:

```text
[ ] Missing JWT → 401

[ ] Invalid JWT → 401

[ ] Admin JWT → rejected

[ ] ACTIVE Student → allowed

[ ] PENDING Student → rejected

[ ] INACTIVE Student → rejected

[ ] SUSPENDED Student → rejected

[ ] Deleted Student → rejected

[ ] Student cannot choose studentId

[ ] Gemini cannot choose studentId
```

---

# 86. Gemini Provider Tests

Stub Gemini responses for:

```text
normal final text

single tool call

multiple tool calls

sequential tool calls

malformed tool arguments

unknown tool

empty response

429

500

timeout
```

No real API key required for normal automated tests.

---

# 87. Live Gemini Test

After automated tests pass, perform a small manual Gemini Free Tier connectivity test.

This test is:

```text
optional for automated suite

required for manual Step 6 verification
```

Do not make:

```bash
mvn test
```

depend on a real Gemini API key.

---

# 88. Postman Test — Enrollments

Login with:

```text
STD-TEST-001
```

Get Student JWT.

Request:

```http
POST /api/v1/student/assistant/chat
Authorization: Bearer <student-jwt>
Content-Type: application/json
```

Body:

```json
{
  "message": "What courses am I enrolled in?"
}
```

Expected data should reflect:

```text
CS301
CSC452
```

from PostgreSQL.

---

# 89. Postman Test — Section

```json
{
  "message": "Which section am I in for CS301?"
}
```

Expected:

```text
SEC-01
```

---

# 90. Postman Test — Lecturer

```json
{
  "message": "Who teaches CS301?"
}
```

Expected:

```text
Dr. John Smith
```

based on the test database.

---

# 91. Postman Test — Room

```json
{
  "message": "Where is my CS301 class?"
}
```

Expected:

```text
Room 11-402
```

---

# 92. Postman Test — Schedule

```json
{
  "message": "What is my schedule?"
}
```

Expected to reflect database values such as:

```text
CS301
Tuesday 09:00-11:45
Room 11-402

CSC452
Friday 09:00-11:45
Room 5-201
```

Do not expect advanced date reasoning yet.

---

# 93. Postman Test — Course Credits

```json
{
  "message": "How many credits is CS301?"
}
```

Expected:

```text
3 credits
```

from:

```text
get_course_information
```

---

# 94. Postman Test — Academic Profile

```json
{
  "message": "What program and department am I in?"
}
```

Expected to use:

```text
get_my_academic_profile
```

and return the database values.

---

# 95. Postman Test — Availability

Example:

```json
{
  "message": "Is my CS301 section full?"
}
```

Possible flow:

```text
get_my_course_section
        ↓
courseSectionId
        ↓
get_section_availability
```

Expected answer must match:

```text
section capacity
PENDING + ACTIVE enrollment count
```

---

# 96. Postman Test — General Question

```json
{
  "message": "What is a credit hour?"
}
```

Gemini may answer directly.

An academic database tool is not required.

---

# 97. Postman Test — Invalid Course

```json
{
  "message": "Who teaches INVALID999?"
}
```

Expected:

```text
No matching course/enrollment found.
```

Do not generate fake lecturer information.

---

# 98. Postman Test — Ambiguous Course

Use test data where two courses can match the same partial phrase.

Ask:

```json
{
  "message": "Who teaches my Programming course?"
}
```

If ambiguous, expected:

```text
clarification request
```

not a random course.

---

# 99. Step 6 Does Not Include Conversation Memory

This step does NOT yet require:

```text
Redis conversation history

database chat history

conversationId

long-term AI memory
```

Each request may remain stateless except for the internal Gemini tool-calling interaction needed to complete the current request.

---

# 100. Pronoun Limitation

Without conversation context:

```text
"Who teaches it?"
```

may not be resolvable.

Prefer explicit questions during Step 6 testing:

```text
"Who teaches CS301?"
```

Conversation context belongs to a later step.

---

# 101. Step 6 Does Not Include RAG

Do NOT implement:

```text
PDF upload

curriculum documents

embeddings

vector search

pgvector

semantic retrieval

RAG
```

in this step.

Step 6 is for:

```text
structured PostgreSQL academic data
```

only.

---

# 102. No Real Production Data Yet

Continue using:

```text
Development Test Data
```

during Step 6.

Do NOT migrate the final real university dataset merely to test function calling.

Real/approved data is introduced later in:

```text
UAT
```

after the complete development workflow is stable.

---

# 103. Environment Variables

Keep Step 5 Gemini variables.

Example:

```text
AI_PROVIDER=gemini

AI_API_KEY=<secret>

AI_MODEL=<configured-gemini-model>

AI_TIMEOUT_SECONDS=30
```

Add optionally:

```text
AI_MAX_TOOL_ROUNDS=4

AI_MAX_TOOL_CALLS=8
```

---

# 104. Configuration

Conceptual:

```yaml
ai:
  provider: ${AI_PROVIDER:gemini}
  api-key: ${AI_API_KEY}
  model: ${AI_MODEL}
  timeout-seconds: ${AI_TIMEOUT_SECONDS:30}
  max-tool-rounds: ${AI_MAX_TOOL_ROUNDS:4}
  max-tool-calls: ${AI_MAX_TOOL_CALLS:8}
```

Do not commit the real API key.

---

# 105. API Key Security Remains Unchanged

The Gemini API key stays:

```text
Backend only
```

Never expose it to:

```text
Flutter App

React Admin

Browser

Student

tool result
```

---

# 106. Student JWT Security Remains Unchanged

The Student JWT stays between:

```text
Mobile App
↔
Spring Boot
```

Never send:

```text
Student JWT
```

to Gemini.

---

# 107. Implementation Order

Implement Step 6 in this order:

```text
PHASE 1
Read AGENTS.md
        ↓
PHASE 2
Read Step 3 AcademicQueryService specification
        ↓
PHASE 3
Read Step 4 verification specification
        ↓
PHASE 4
Inspect completed Step 5 implementation
        ↓
PHASE 5
Inspect Gemini LLM client implementation
        ↓
PHASE 6
Create Academic Tool name constants
        ↓
PHASE 7
Create provider-neutral Tool Definitions
        ↓
PHASE 8
Create Academic Tool Registry
        ↓
PHASE 9
Create Tool Call / Tool Result DTOs
        ↓
PHASE 10
Extend LlmClient for Function Calling
        ↓
PHASE 11
Implement Gemini Function Declarations
        ↓
PHASE 12
Create AcademicAiToolExecutor
        ↓
PHASE 13
Map each Tool to AcademicQueryService
        ↓
PHASE 14
Implement strict argument validation
        ↓
PHASE 15
Implement safe tool-result serialization
        ↓
PHASE 16
Update Assistant System Prompt
        ↓
PHASE 17
Implement Tool Calling Loop
        ↓
PHASE 18
Implement max tool-round protection
        ↓
PHASE 19
Implement tool error mapping
        ↓
PHASE 20
Implement safe logging
        ↓
PHASE 21
Create Tool Registry tests
        ↓
PHASE 22
Create Tool Executor tests
        ↓
PHASE 23
Create Student isolation tests
        ↓
PHASE 24
Create Gemini stub function-call tests
        ↓
PHASE 25
Create Assistant integration tests
        ↓
PHASE 26
Run mvn test
        ↓
PHASE 27
Run mvn clean package
        ↓
PHASE 28
Start Development Environment
        ↓
PHASE 29
Login with Test Student
        ↓
PHASE 30
Postman End-to-End AI Testing
```

---

# 108. Acceptance Criteria

## TOOLS

```text
[ ] get_my_academic_profile implemented

[ ] get_my_enrollments implemented

[ ] get_my_courses implemented

[ ] get_my_schedule implemented

[ ] get_my_course_section implemented

[ ] get_my_course_lecturer implemented

[ ] get_course_information implemented

[ ] get_section_availability implemented
```

## SECURITY

```text
[ ] Student identity comes from JWT

[ ] No tool accepts studentId

[ ] Unknown arguments rejected

[ ] Unknown tools rejected

[ ] No direct SQL generated by Gemini

[ ] No arbitrary method execution

[ ] No repository exposed to Gemini

[ ] Student A cannot access Student B
```

## TOOL EXECUTION

```text
[ ] Explicit allowlist used

[ ] Tool Registry exists

[ ] Tool Executor exists

[ ] Tool arguments validated

[ ] AcademicQueryService reused

[ ] Tools are read-only

[ ] Null values preserved

[ ] Empty results handled safely
```

## GEMINI

```text
[ ] Approved tool declarations sent to Gemini

[ ] Gemini function calls parsed

[ ] Tool call ID preserved where required

[ ] Tool result returned to Gemini

[ ] Final natural-language answer returned

[ ] Multiple tool calls supported safely

[ ] Sequential calls supported

[ ] Tool-round limit enforced
```

## GROUNDING

```text
[ ] Personal academic questions use tools

[ ] General questions may answer without tools

[ ] Tool result treated as authoritative

[ ] Missing information is not invented

[ ] Ambiguous courses cause clarification

[ ] Student-specific information is not fabricated
```

## PRIVACY

```text
[ ] API key not exposed

[ ] JWT not sent to Gemini

[ ] Password data not sent to Gemini

[ ] Student ID not unnecessarily sent to Gemini

[ ] Minimal academic fields sent

[ ] Full tool output not logged
```

## DATABASE

```text
[ ] Same PostgreSQL database used

[ ] No separate AI database created

[ ] AcademicQueryService is source of academic queries

[ ] No production real-data migration performed
```

## TESTING

```text
[ ] Tool Registry tests pass

[ ] Tool Executor tests pass

[ ] Argument validation tests pass

[ ] Student isolation tests pass

[ ] Gemini stub tests pass

[ ] Tool loop tests pass

[ ] Error handling tests pass

[ ] Hallucination/null tests pass

[ ] Postman tests pass
```

## BUILD

```text
[ ] mvn test passes

[ ] mvn clean package passes
```

---

# 109. Expected Coding Agent Report

After implementation, report:

```text
1. Files created

2. Files modified

3. Gemini function calling implementation

4. Academic Tool definitions created

5. Tool Registry implementation

6. Tool Executor implementation

7. AcademicQueryService mappings

8. Student identity/security handling

9. Tool argument validation

10. Tool result sanitization

11. Tool call limit implementation

12. Error handling

13. Tests created

14. mvn test result

15. mvn clean package result

16. Manual Postman results

17. Any limitations discovered
```

Do NOT include:

```text
AI_API_KEY

JWT

password

refresh token
```

in the report.

---

# 110. Step 6 Completion Architecture

After Step 6:

```text
Student Mobile App
        │
        │ Student JWT
        ↓
POST /api/v1/student/assistant/chat
        ↓
StudentAssistantController
        ↓
StudentAssistantService
        ↓
Gemini
        │
        ├──── General question
        │          ↓
        │       Direct answer
        │
        └──── Academic question
                   ↓
             Function Call
                   ↓
          AcademicAiToolRegistry
                   ↓
          AcademicAiToolExecutor
                   ↓
          AcademicQueryService
                   ↓
               PostgreSQL
                   ↓
          Structured Tool Result
                   ↓
                Gemini
                   ↓
          Natural-Language Answer
                   ↓
             Student App
```

---

# 111. Example Final Flow

Student:

```text
"What courses am I enrolled in?"
```

Flow:

```text
JWT Student
        ↓
Assistant API
        ↓
Gemini
        ↓
get_my_enrollments
        ↓
AcademicAiToolExecutor
        ↓
AcademicQueryService
        ↓
PostgreSQL
        ↓
CS301 + CSC452
        ↓
Gemini
        ↓
"You are currently enrolled in
CS301 and CSC452."
```

---

# 112. What to Do After Step 6

After implementation:

```text
mvn test
        ↓
mvn clean package
        ↓
Start PostgreSQL + Redis + Backend
        ↓
Use Test Student Account
        ↓
Postman Test
        ↓
Compare AI Answers With PostgreSQL
        ↓
Fix Any Tool / Security Issues
        ↓
Proceed to Step 7
```

---

# 113. Step 7

Next:

# Step 7 — Academic AI End-to-End Natural Language Verification

Step 7 should test many different natural-language forms against the same expected academic facts.

Examples:

```text
"What courses am I enrolled in?"

"Show me my current classes."

"Which subjects am I taking?"

"Can you list my courses?"
```

All should resolve to the appropriate academic tool and produce answers matching PostgreSQL.

Step 7 should also test:

```text
paraphrases

ambiguous questions

missing data

invalid courses

Student isolation

hallucination resistance

tool-selection accuracy

response correctness
```

Continue using:

```text
controlled test data
```

until the development workflow is complete.

Real academic data should be migrated later to the UAT environment after the full project has been validated with test data.