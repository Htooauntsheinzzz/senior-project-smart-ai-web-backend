package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentProcessingStatus;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/**
 * The ingestion queue on academic_document_processing (spec file 46). Fixed SQL with bind parameters only. Every
 * status-changing statement is conditional on the expected status (and, for a running job, its attempt_count), so a
 * run that lost its job changes nothing. Callers check DocumentProcessingStatus.requireTransitionTo first and run each
 * method inside a short transaction.
 */
@Repository
public class IngestionQueueRepository {
    /** A processing row together with its file (and the document of the file). */
    public record QueueRow(long processingId, long documentId, long fileId) {}

    public record ClaimRow(long processingId, int attemptCount, String failedStage, long documentId, long fileId,
            int revisionNumber, String storageKey, String fileExtension, String mimeType, long sizeBytes, String sha256Checksum) {}

    public record ProcessingRow(long processingId, long documentId, long fileId, int revisionNumber, String status,
            int attemptCount, LocalDateTime startedAt, LocalDateTime completedAt, String lastErrorCode, String lastErrorMessage,
            String failedStage, LocalDateTime queuedAt, LocalDateTime nextAttemptAt) {}

    private static final String RETURNING = " RETURNING p.id, f.document_id, f.id AS file_id";
    private final NamedParameterJdbcTemplate jdbc;

    public IngestionQueueRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<QueueRow> cancelSupersededQueued(LocalDateTime now, String message) {
        return jdbc.query("""
                UPDATE academic_document_processing p
                SET status = 'FAILED', failed_stage = NULL, last_error_code = 'INGESTION_SUPERSEDED',
                    last_error_message = :message, next_attempt_at = NULL, completed_at = :now, updated_at = :now
                FROM academic_document_files f JOIN academic_documents d ON d.id = f.document_id
                WHERE p.file_id = f.id AND p.status = 'QUEUED' AND (NOT f.is_current OR d.is_deleted)""" + RETURNING,
                new MapSqlParameterSource("now", now).addValue("message", message), QUEUE_ROW);
    }

    public List<QueueRow> recoverInterrupted(LocalDateTime now, LocalDateTime staleBefore, int maxAttempts, String message) {
        return jdbc.query("""
                UPDATE academic_document_processing p
                SET status = 'FAILED', last_error_code = 'INGESTION_INTERRUPTED', last_error_message = :message,
                    failed_stage = CASE p.status WHEN 'PROCESSING' THEN 'EXTRACT' WHEN 'CHUNKING' THEN 'CHUNK' ELSE 'EMBED' END,
                    next_attempt_at = CASE WHEN p.attempt_count < :maxAttempts THEN CAST(:now AS timestamp) ELSE NULL END,
                    completed_at = :now, updated_at = :now
                FROM academic_document_files f
                WHERE p.file_id = f.id AND p.status IN ('PROCESSING', 'CHUNKING', 'EMBEDDING') AND p.updated_at < :staleBefore""" + RETURNING,
                new MapSqlParameterSource("now", now).addValue("staleBefore", staleBefore).addValue("maxAttempts", maxAttempts)
                        .addValue("message", message), QUEUE_ROW);
    }

    public List<QueueRow> queueDueRetries(LocalDateTime now) {
        return jdbc.query("""
                UPDATE academic_document_processing p
                SET status = 'QUEUED', next_attempt_at = NULL, queued_at = :now, updated_at = :now
                FROM academic_document_files f JOIN academic_documents d ON d.id = f.document_id
                WHERE p.file_id = f.id AND p.status = 'FAILED' AND p.next_attempt_at <= :now
                  AND f.is_current AND NOT d.is_deleted""" + RETURNING, new MapSqlParameterSource("now", now), QUEUE_ROW);
    }

    /** Oldest uploads first; queued_by is the uploader. Rows locked by another instance are skipped. */
    public List<QueueRow> queueUploads(LocalDateTime now, int limit) {
        return jdbc.query("""
                UPDATE academic_document_processing p
                SET status = 'QUEUED', queued_at = :now, queued_by = f.uploaded_by, attempt_count = 0, updated_at = :now
                FROM academic_document_files f
                WHERE p.file_id = f.id AND p.status = 'UPLOADED' AND p.id IN (
                    SELECT p2.id FROM academic_document_processing p2
                    JOIN academic_document_files f2 ON f2.id = p2.file_id
                    JOIN academic_documents d2 ON d2.id = f2.document_id
                    WHERE p2.status = 'UPLOADED' AND f2.is_current AND d2.is_active AND NOT d2.is_deleted
                    ORDER BY f2.uploaded_at, p2.id
                    LIMIT :limit
                    FOR UPDATE OF p2 SKIP LOCKED)""" + RETURNING,
                new MapSqlParameterSource("now", now).addValue("limit", limit), QUEUE_ROW);
    }

    /** Locks the oldest queued job of a current file of a non-deleted document; another instance skips it. */
    public Optional<ClaimRow> lockNextQueued() {
        return jdbc.query("""
                SELECT p.id, p.attempt_count, p.failed_stage, f.document_id, f.id AS file_id, f.revision_number, f.storage_key,
                       f.file_extension, f.mime_type, f.size_bytes, f.sha256_checksum
                FROM academic_document_processing p
                JOIN academic_document_files f ON f.id = p.file_id
                JOIN academic_documents d ON d.id = f.document_id
                WHERE p.status = 'QUEUED'
                  AND f.is_current
                  AND NOT d.is_deleted
                ORDER BY p.queued_at NULLS FIRST, p.id
                LIMIT 1
                FOR UPDATE OF p SKIP LOCKED""", Map.of(), (rs, i) -> new ClaimRow(rs.getLong("id"), rs.getInt("attempt_count"),
                rs.getString("failed_stage"), rs.getLong("document_id"), rs.getLong("file_id"), rs.getInt("revision_number"),
                rs.getString("storage_key"), rs.getString("file_extension"), rs.getString("mime_type"), rs.getLong("size_bytes"),
                rs.getString("sha256_checksum"))).stream().findFirst();
    }

    public boolean markClaimed(long processingId, int attemptCount, LocalDateTime now) {
        return jdbc.update("""
                UPDATE academic_document_processing
                SET status = 'PROCESSING', attempt_count = attempt_count + 1, started_at = :now, completed_at = NULL,
                    failed_stage = NULL, last_error_code = NULL, last_error_message = NULL, next_attempt_at = NULL, updated_at = :now
                WHERE id = :id AND status = 'QUEUED' AND attempt_count = :attempt""",
                new MapSqlParameterSource("id", processingId).addValue("attempt", attemptCount).addValue("now", now)) == 1;
    }

    /** Heartbeat of a running job; false when the row no longer belongs to this run. */
    public boolean heartbeat(long processingId, DocumentProcessingStatus status, int attempt, LocalDateTime now) {
        return jdbc.update("UPDATE academic_document_processing SET updated_at = :now WHERE id = :id AND status = :status AND attempt_count = :attempt",
                run(processingId, status, attempt).addValue("now", now)) == 1;
    }

    /** True while the file is the current revision of a document that is not deleted. */
    public boolean wanted(long fileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COALESCE((SELECT f.is_current AND NOT d.is_deleted FROM academic_document_files f
                    JOIN academic_documents d ON d.id = f.document_id WHERE f.id = :fileId), FALSE)""",
                Map.of("fileId", fileId), Boolean.class));
    }

    public boolean moveRunning(long processingId, DocumentProcessingStatus from, int attempt, DocumentProcessingStatus to, LocalDateTime now) {
        return jdbc.update("UPDATE academic_document_processing SET status = :to, updated_at = :now WHERE id = :id AND status = :status AND attempt_count = :attempt",
                run(processingId, from, attempt).addValue("to", to.name()).addValue("now", now)) == 1;
    }

    public boolean markReady(long processingId, int attempt, LocalDateTime now) {
        return jdbc.update("""
                UPDATE academic_document_processing
                SET status = 'READY', completed_at = :now, failed_stage = NULL, last_error_code = NULL, last_error_message = NULL,
                    next_attempt_at = NULL, updated_at = :now
                WHERE id = :id AND status = 'EMBEDDING' AND attempt_count = :attempt""",
                run(processingId, DocumentProcessingStatus.EMBEDDING, attempt).addValue("now", now)) == 1;
    }

    public boolean markFailed(long processingId, DocumentProcessingStatus from, int attempt, String failedStage, String code,
            String message, LocalDateTime nextAttemptAt, LocalDateTime now) {
        return jdbc.update("""
                UPDATE academic_document_processing
                SET status = 'FAILED', failed_stage = :failedStage, completed_at = :now, last_error_code = :code,
                    last_error_message = :message, next_attempt_at = :nextAttemptAt, updated_at = :now
                WHERE id = :id AND status = :status AND attempt_count = :attempt""",
                run(processingId, from, attempt).addValue("failedStage", failedStage).addValue("code", code)
                        .addValue("message", message).addValue("nextAttemptAt", nextAttemptAt).addValue("now", now)) == 1;
    }

    public boolean chunksExist(long fileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM academic_document_chunks WHERE file_id = :fileId)",
                Map.of("fileId", fileId), Boolean.class));
    }

    /** Deletes at most maxRows chunk rows of one file. */
    public int deleteChunks(long fileId, int maxRows) {
        return jdbc.update("DELETE FROM academic_document_chunks WHERE id IN (SELECT id FROM academic_document_chunks WHERE file_id = :fileId LIMIT :max)",
                new MapSqlParameterSource("fileId", fileId).addValue("max", maxRows));
    }

    /** Deletes at most maxRows page rows of one file. */
    public int deletePages(long fileId, int maxRows) {
        return jdbc.update("DELETE FROM academic_document_pages WHERE id IN (SELECT id FROM academic_document_pages WHERE file_id = :fileId LIMIT :max)",
                new MapSqlParameterSource("fileId", fileId).addValue("max", maxRows));
    }

    /** The current file of a document and its processing row (no row when there is none). */
    public Optional<ProcessingRow> currentProcessing(long documentId, boolean forUpdate) {
        return jdbc.query("""
                SELECT p.id, f.document_id, f.id AS file_id, f.revision_number, p.status, p.attempt_count, p.started_at,
                       p.completed_at, p.last_error_code, p.last_error_message, p.failed_stage, p.queued_at, p.next_attempt_at
                FROM academic_document_files f
                JOIN academic_document_processing p ON p.file_id = f.id
                WHERE f.document_id = :documentId AND f.is_current""" + (forUpdate ? " FOR UPDATE OF p" : ""),
                Map.of("documentId", documentId), (rs, i) -> new ProcessingRow(rs.getLong("id"), rs.getLong("document_id"),
                        rs.getLong("file_id"), rs.getInt("revision_number"), rs.getString("status"), rs.getInt("attempt_count"),
                        time(rs, "started_at"), time(rs, "completed_at"), rs.getString("last_error_code"),
                        rs.getString("last_error_message"), rs.getString("failed_stage"), time(rs, "queued_at"),
                        time(rs, "next_attempt_at"))).stream().findFirst();
    }

    /** Reprocess: the row was locked FOR UPDATE by the caller in the same transaction. */
    public boolean requeue(long processingId, DocumentProcessingStatus from, String failedStage, long actorUserId, LocalDateTime now) {
        return jdbc.update("""
                UPDATE academic_document_processing
                SET status = 'QUEUED', failed_stage = :failedStage, attempt_count = 0, queued_at = :now, queued_by = :actor,
                    next_attempt_at = NULL, last_error_code = NULL, last_error_message = NULL, updated_at = :now
                WHERE id = :id AND status = :status""",
                new MapSqlParameterSource("id", processingId).addValue("status", from.name()).addValue("failedStage", failedStage)
                        .addValue("actor", actorUserId).addValue("now", now)) == 1;
    }

    public void lockTimeout() {
        jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '3s'");
    }

    public long pageCount(long fileId) {
        return count("SELECT COUNT(*) FROM academic_document_pages WHERE file_id = :fileId", fileId);
    }

    public long chunkCount(long fileId) {
        return count("SELECT COUNT(*) FROM academic_document_chunks WHERE file_id = :fileId", fileId);
    }

    /** Chunks with a vector of this model (none when model is null). */
    public long embeddedChunkCount(long fileId, String model) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM academic_document_chunks
                WHERE file_id = :fileId AND embedding IS NOT NULL AND embedding_model = CAST(:model AS varchar)""",
                new MapSqlParameterSource("fileId", fileId).addValue("model", model), Long.class);
        return value == null ? 0 : value;
    }

    private long count(String sql, long fileId) {
        Long value = jdbc.queryForObject(sql, Map.of("fileId", fileId), Long.class);
        return value == null ? 0 : value;
    }

    private static MapSqlParameterSource run(long processingId, DocumentProcessingStatus status, int attempt) {
        return new MapSqlParameterSource("id", processingId).addValue("status", status.name()).addValue("attempt", attempt);
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    private static final org.springframework.jdbc.core.RowMapper<QueueRow> QUEUE_ROW =
            (rs, i) -> new QueueRow(rs.getLong("id"), rs.getLong("document_id"), rs.getLong("file_id"));
}
