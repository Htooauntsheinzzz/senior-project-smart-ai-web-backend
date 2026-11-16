# 58 — Step 17: Persistent AI Assistant Conversation History

**Project:** Smart AI University Student Assistant
**Module:** Student AI Assistant → conversation history
**Scope:** Storing every completed chat turn in PostgreSQL, restoring the short-term Redis context from it, and Student APIs to list, read, continue and delete their own conversations.
**Stack:** Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17 + pgvector, Redis 7.4
**Prerequisites:** Spec file 35 (Step 10, Redis conversation context) and spec files 53 to 57 are implemented. Latest Flyway migration is V51.
**Status:** Written 2026-11-11 and implemented the same day (decision D82).

---

## 1. Objective

Step 10 gave the assistant a short-term memory: one Redis value per conversation that expires 30 minutes after its last turn. After that the conversation is gone, and the mobile app has nothing to show in a "previous chats" screen.

Step 17 adds the long-term side.

```text
POST /chat ──► StudentAssistantService ──► ConversationContextService
                                                 │
                         ┌───────────────────────┴───────────────────────┐
                         ▼                                               ▼
        Redis  student:assistant:conversation:<id>          PostgreSQL  assistant_conversations
        short-term context sent to the model                            assistant_messages
        last AI_CONTEXT_MAX_TURNS turns, TTL 30 min         every completed turn, kept until the Student deletes it
```

| Store | Role | Lifetime |
| --- | --- | --- |
| Redis | The working context of an active conversation: the recent turns that are sent to the model and the academic reference ("it", "the first one"). | `AI_CONTEXT_TTL_MINUTES` after the last completed turn (unchanged). |
| PostgreSQL | The record of the conversation: every question and final answer, with the sources of the answer. | Until the Student deletes the conversation. |

Goals:

1. A Student can list their conversations, open one and read all its messages.
2. A Student can continue a conversation after its Redis context expired: the backend restores the context from PostgreSQL.
3. A Student can delete a conversation; it is then gone from both stores.
4. A Student can never see, continue or delete another Student's conversation.
5. The chat endpoint, its request and its response do not change.

---

## 2. Mandatory Agent Instructions

Before implementation: read `Agent.md`, spec file 35 and decision D51, and inspect `StudentAssistantServiceImpl`, `ConversationContextServiceImpl`, `AssistantConversationRepository` (Redis) and the test classes `ConversationContextServiceTests` and `AssistantConversationIntegrationTests`. Record the test baseline first.

Do not:

- change the Redis key, the Redis value schema or its trimming rules;
- send more history to the model than Step 10 does (the model still receives at most `AI_CONTEXT_MAX_TURNS` turns);
- treat stored messages as academic facts (spec file 35, section 36: facts are always re-queried through the tools);
- store tool arguments, tool results, document passages, system prompts or provider payloads;
- log message text, titles or previews;
- edit an applied migration (V1 to V51).

---

## 3. Data Model

### 3.1 `assistant_conversations` (V52)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` PK | The `conversationId` the chat endpoint already returns. Same value as in the Redis key. |
| `student_id` | `BIGINT NOT NULL` | Owner. FK to `students(id)`. |
| `title` | `VARCHAR(120) NOT NULL` | From the first question of the conversation (section 6.2). |
| `last_message_preview` | `VARCHAR(200)` | Start of the latest answer, for the list screen. |
| `message_count` | `INTEGER NOT NULL DEFAULT 0` | Stored messages (two per turn). |
| `context_version` | `BIGINT NOT NULL DEFAULT 0` | The Redis `version` after the latest stored turn; restored into Redis (section 7). |
| `academic_reference` | `TEXT` | The Step 10 `AcademicConversationReference` as JSON, so "it" still works after a restore. Never returned by an API. |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | `updated_at` is the time of the latest completed turn. |

Index `idx_assistant_conversations_student_updated (student_id, updated_at DESC, id DESC)` for the list.

### 3.2 `assistant_messages` (V53)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `BIGINT` identity PK | Message order within a conversation is the order of `id`. |
| `conversation_id` | `UUID NOT NULL` | FK to `assistant_conversations(id)`. |
| `role` | `VARCHAR(20) NOT NULL` | `USER` or `ASSISTANT` (check constraint). |
| `content` | `TEXT NOT NULL` | The Student's message (at most 4000 characters) or the final answer the Student received. Not trimmed. |
| `request_id` | `VARCHAR(36)` | The chat `requestId`, on both messages of a turn. |
| `model` | `VARCHAR(100)` | ASSISTANT only. |
| `sources` | `TEXT` | ASSISTANT only: the `sources` array of the chat response as JSON, null when empty. |
| `created_at` | `TIMESTAMPTZ NOT NULL` | |

Index `idx_assistant_messages_conversation (conversation_id, id)`.

Notes:

- JSON is stored as `TEXT` written and read with the application's `ObjectMapper`, the same way the Redis value is. The project has no `jsonb` mapping yet and nothing queries inside these values.
- The foreign keys do not cascade, like every foreign key of this schema (`CampusSchemaMigrationTests` checks it). The application deletes a conversation's messages and then the conversation. Students are soft-deleted (`is_deleted`), so their rows stay and so does their history.
- Deleting is a real `DELETE`, not a soft delete: when a Student removes a chat, its text must not stay in the database.

---

## 4. Package Structure

Everything stays in `feature/assistant`, in its existing layers.

```text
feature/assistant/
├── controller/StudentAssistantConversationController        new
├── dto/AssistantConversationSummaryResponse                 new
├── dto/AssistantConversationPageResponse                    new
├── dto/AssistantConversationDetailResponse                  new
├── dto/AssistantMessageResponse                             new
├── dto/AssistantTurnDetails                                 new (requestId, model, sources of one turn)
├── entity/AssistantConversation, AssistantMessage           new
├── mapper/AssistantConversationMapper                       new
├── repository/AssistantConversationHistoryRepository        new (JPA; AssistantConversationRepository stays the Redis one)
├── repository/AssistantMessageRepository                    new (JPA)
├── service/AssistantConversationHistoryService              new
├── service/impl/AssistantConversationHistoryServiceImpl     new
├── service/ConversationContextService                       changed: saveCompletedTurn with AssistantTurnDetails
├── service/impl/ConversationContextServiceImpl              changed: stores the turn, restores an expired context
├── service/impl/StudentAssistantServiceImpl                 changed: passes requestId, model and sources when saving
└── exception/AiAssistantFailure                             changed: ASSISTANT_HISTORY_UNAVAILABLE
config/AiAssistantHistoryProperties                          new (app.ai.history.*)
```

---

## 5. Configuration

| Variable | Property | Default | Meaning |
| --- | --- | --- | --- |
| `AI_HISTORY_ENABLED` | `app.ai.history.enabled` | `true` | `false`: nothing is written to PostgreSQL, an expired conversation is not restored, the list is empty and get/delete answer 404. The assistant then behaves exactly as in Step 10. |

History needs the conversation context: with `AI_CONTEXT_ENABLED=false` there is no `conversationId`, so nothing is stored.

---

## 6. Storing a Turn

### 6.1 When

A turn is stored when the chat request ends with an answer for the Student: an answer of the model, a deterministic backend reply or a safe fallback. A request that ends with an error (provider failure, timeout, tool limit, conflict) stores nothing, as in Step 10.

### 6.2 What

For a new conversation one `assistant_conversations` row is inserted, with

- `title`: the Student's first message with whitespace collapsed to single spaces, cut to 80 characters (then ending with `…`);
- then, for every turn, two `assistant_messages` rows (USER, ASSISTANT) and on the conversation: `message_count + 2`, `last_message_preview` (the answer with whitespace collapsed, cut to 160 characters), `updated_at`, `context_version` and `academic_reference` of the saved Redis state.

### 6.3 Atomicity

`ConversationContextService.saveCompletedTurn` does both saves in this order, inside one database transaction:

```text
BEGIN
  lock or insert the conversation row, insert the two messages
  save the turn in Redis (Step 10: create-if-absent or compare-and-set on the version)
  update the conversation row from the saved Redis state
COMMIT
```

- Redis reports a conflict, a missing key or is unavailable → the transaction rolls back and the Step 10 error is returned (409, 404, 503). PostgreSQL holds nothing of that turn.
- PostgreSQL fails before the Redis save → Redis is untouched; the response is `503 ASSISTANT_HISTORY_UNAVAILABLE`.
- Only a failed `COMMIT` after a successful Redis save leaves the stores different (Redis has one turn that the history lacks). The response is 503; the next turn stores normally and sets `context_version` again.

A conversation that exists in Redis but not in PostgreSQL (started before Step 17 was deployed, or while history was disabled) gets its row with the first turn that is stored; its title comes from that turn's question and earlier turns are not in the history.

---

## 7. Continuing a Conversation

`POST /api/v1/student/assistant/chat` with `conversationId` works as before while the Redis value exists. New in Step 17, when it does not exist:

1. The backend looks for the Student's own conversation in PostgreSQL. Not found, or owned by another Student → `404 CONVERSATION_NOT_FOUND` (unchanged, no model request).
2. Found → a Redis context is rebuilt: the last `AI_CONTEXT_MAX_TURNS` turns (each message capped to 4000 characters, the whole value fitted to `AI_CONTEXT_MAX_BYTES` by the Step 10 rules), the stored `academic_reference` (empty when it cannot be read), `version = context_version`, and written with create-if-absent and a fresh TTL. If another request restored it at the same moment, that value is read instead.
3. The request continues as a normal follow-up.

So a `conversationId` now stays valid until the Student deletes the conversation. The model still receives only the recent turns, never the full history.

---

## 8. Student APIs

All under `/api/v1/student/assistant`, protected by the existing `/api/v1/student/**` rule (Student token, `ROLE_STUDENT`, no pending password change) and the same eligibility check as the chat endpoint. The Student is always the token's Student; no API takes a student id. Responses carry `Cache-Control: no-store`.

### 8.1 List — `GET /conversations?page=0&size=20`

`page` ≥ 0 (default 0), `size` 1 to 50 (default 20); anything else is `400 VALIDATION_ERROR`. Newest activity first (`updated_at` descending).

```json
{
  "content": [
    {
      "conversationId": "0b6f4c0e-6f0e-4a55-9f39-0f1f4f0a2c11",
      "title": "What courses do I have?",
      "lastMessagePreview": "1. CS301 — Data Structures 2. CSC452 — Mobile Programming",
      "messageCount": 4,
      "createdAt": "2026-11-11T09:00:00Z",
      "updatedAt": "2026-11-11T09:02:10Z"
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

### 8.2 Get — `GET /conversations/{conversationId}?limit=50&beforeMessageId=`

Returns the conversation and one window of its messages, oldest first inside the window. `limit` 1 to 100 (default 50). Without `beforeMessageId` the window is the newest `limit` messages; with it, the `limit` messages just before that message id (for "load older"). `hasMoreBefore` tells whether older messages exist.

```json
{
  "conversationId": "0b6f4c0e-6f0e-4a55-9f39-0f1f4f0a2c11",
  "title": "What courses do I have?",
  "messageCount": 4,
  "createdAt": "2026-11-11T09:00:00Z",
  "updatedAt": "2026-11-11T09:02:10Z",
  "hasMoreBefore": false,
  "messages": [
    { "messageId": 101, "role": "USER", "content": "What courses do I have?", "createdAt": "2026-11-11T09:00:00Z", "sources": [] },
    { "messageId": 102, "role": "ASSISTANT", "content": "1. CS301 — Data Structures\n2. CSC452 — Mobile Programming", "createdAt": "2026-11-11T09:00:03Z", "sources": [] }
  ]
}
```

`sources` has the shape of the chat response (`title`, `academicYear`, `category`) and is `[]` when the answer used no document.

### 8.3 Continue — `POST /chat`

Unchanged request and response (spec file 35). Send the `conversationId` of a listed conversation (section 7).

### 8.4 Delete — `DELETE /conversations/{conversationId}`

`204 No Content`. Inside one transaction: the row is locked, the Redis key is removed, the messages and then the conversation are deleted. If Redis is unavailable nothing is deleted and the response is `503 ASSISTANT_CONTEXT_UNAVAILABLE`, so a deleted conversation can never be continued from a left-over Redis value. Deleting again answers 404.

### 8.5 Errors

| Case | Status | Code |
| --- | --- | --- |
| `conversationId` is not a lowercase UUID v4 | 400 | `INVALID_CONVERSATION_ID` |
| `page`, `size`, `limit` or `beforeMessageId` invalid | 400 | `VALIDATION_ERROR` |
| No token / Admin token | 401 / 403 | existing |
| Unknown conversation, another Student's conversation, history disabled | 404 | `CONVERSATION_NOT_FOUND` |
| Redis unavailable on delete or chat | 503 | `ASSISTANT_CONTEXT_UNAVAILABLE` |
| PostgreSQL unavailable while storing or restoring a turn | 503 | `ASSISTANT_HISTORY_UNAVAILABLE` |

Another Student's conversation is always reported exactly like a missing one.

---

## 9. Privacy and Limits

- Message text is now stored in PostgreSQL (Step 10 stored it only in Redis for 30 minutes). It is the Student's question and the answer they received; nothing else of the request is stored.
- Only ids, counts and versions are logged (`ASSISTANT_HISTORY_SAVED`, `ASSISTANT_CONVERSATION_RESTORED`, `ASSISTANT_CONVERSATION_DELETED`).
- There is no Admin API for Student conversations in this step.
- Not in this step: renaming a conversation, deleting all conversations at once, search, automatic retention or a cap on the number of conversations, summaries of old turns for the model.

---

## 10. Tests

1. `ConversationContextServiceTests` (unit, existing): unchanged behaviour without history.
2. `AssistantConversationHistoryIntegrationTests` (real endpoint, PostgreSQL and Redis, scripted model):
   - a first message stores one conversation and two messages; a follow-up adds two more; title, preview, count;
   - list: only the Student's own conversations, newest first, paging, validation;
   - get: messages in order, `limit` / `beforeMessageId` / `hasMoreBefore`, sources of a document answer;
   - continue after the Redis key is removed: history is sent to the model again, the reference still resolves, version continues;
   - Student B gets 404 for Student A's conversation on get, delete and chat, and nothing is restored into Redis;
   - delete removes rows and the Redis key; chat with the deleted id is 404 with no model request;
   - a failed chat request stores nothing;
   - invalid ids and parameters; 401 without a token.
3. The existing assistant tests pass with the same baseline failures.

---

## 11. Postman

Base URL `http://localhost:8080`, Student of `docker/dev-fixtures/step6-assistant-test-data.sql`.

1. `POST /api/v1/student/auth/login` → copy `accessToken`.
2. `POST /api/v1/student/assistant/chat` `{ "message": "What courses do I have?" }` → copy `conversationId`.
3. `POST /api/v1/student/assistant/chat` `{ "message": "Who teaches the first one?", "conversationId": "…" }`.
4. `GET /api/v1/student/assistant/conversations` → one item, `messageCount` 4.
5. `GET /api/v1/student/assistant/conversations/{conversationId}` → four messages.
6. `docker exec <redis> redis-cli -a <password> DEL student:assistant:conversation:<id>` (or wait for the TTL), then repeat step 3 with another question → 200, same `conversationId`.
7. `DELETE /api/v1/student/assistant/conversations/{conversationId}` → 204; step 5 again → 404.
