-- Step 17 (spec file 58): one row per assistant conversation of a Student. id is the conversationId the chat endpoint
-- returns and the Redis key suffix (student:assistant:conversation:<id>). Redis keeps the short-term context; this table
-- and assistant_messages keep the history until the Student deletes it.
-- No cascade, as everywhere in this schema: the application deletes a conversation's messages, then the conversation.
CREATE TABLE assistant_conversations (
    id UUID PRIMARY KEY,
    student_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    last_message_preview VARCHAR(200),
    message_count INTEGER NOT NULL DEFAULT 0,
    -- The Redis context version after the latest stored turn, restored into Redis when the context expired.
    context_version BIGINT NOT NULL DEFAULT 0,
    -- AcademicConversationReference as JSON (written by the application), never returned by an API.
    academic_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_assistant_conversations_student
        FOREIGN KEY (student_id)
        REFERENCES students(id),

    CONSTRAINT chk_assistant_conversations_counts
        CHECK (message_count >= 0 AND context_version >= 0)
);

-- The Student's conversation list, newest activity first.
CREATE INDEX idx_assistant_conversations_student_updated
ON assistant_conversations (student_id, updated_at DESC, id DESC);
