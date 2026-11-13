-- Step 14A: embeddings are stored in the existing PostgreSQL database with pgvector (no second AI database).
-- The extension always lives in schema public, whatever Flyway's default schema is. Migration tests migrate into
-- throwaway schemas and clean them; an extension created inside such a schema would be dropped with CASCADE.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
