-- Step 14A: queue columns for the ingestion worker (spec file 46). No new status: the V25 status CHECK is unchanged.
-- failed_stage is the stage of the last failure and the resume hint; queued_by is never taken from a request body.
ALTER TABLE academic_document_processing
    ADD COLUMN failed_stage VARCHAR(20),
    ADD COLUMN queued_at TIMESTAMP,
    ADD COLUMN queued_by BIGINT,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD CONSTRAINT fk_academic_document_processing_queued_by
        FOREIGN KEY (queued_by) REFERENCES app_users(id),
    ADD CONSTRAINT chk_academic_document_processing_failed_stage
        CHECK (
            failed_stage IS NULL
            OR failed_stage IN ('EXTRACT', 'CLEAN', 'CHUNK', 'EMBED', 'INDEX')
        );
