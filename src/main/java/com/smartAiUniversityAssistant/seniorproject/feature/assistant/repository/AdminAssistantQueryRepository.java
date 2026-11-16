package com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Spec file 59: the read side of the Admin Chat History and AI Feedback screens. Fixed SQL fragments with bind
 * parameters only; a filter adds its fragment only when it has a value. Callers validate paging and filter values and
 * run each method inside a read-only transaction. Nothing is written here.
 */
@Repository
public class AdminAssistantQueryRepository {
    /** search is a trimmed, non-empty text or null; from is inclusive and to exclusive, each may be null. */
    public record ConversationFilter(String search, Instant from, Instant to) {}

    public record FeedbackFilter(String search, String rating, String reviewStatus, Instant from, Instant to) {}

    public record ConversationRow(UUID id, String title, String lastMessagePreview, int messageCount, Instant createdAt,
            Instant updatedAt, long studentId, String studentCode, String studentName) {}

    /** question and answer are complete or cut to previewChars, depending on the query. */
    public record FeedbackRow(long id, String rating, String comment, String reviewStatus, String reviewNote, Long reviewerId,
            String reviewerName, Instant reviewedAt, Instant submittedAt, long studentId, String studentCode, String studentName,
            UUID conversationId, String conversationTitle, long messageId, String question, String answer, String model,
            String sources) {}

    public record FeedbackTotals(long total, long positive, long negative, long awaitingReview) {}

    private static final String CONVERSATION_COLUMNS = """
            SELECT c.id, c.title, c.last_message_preview, c.message_count, c.created_at, c.updated_at,
                   s.id AS student_id, s.student_code, s.first_name || ' ' || s.last_name AS student_name
            """;
    private static final String CONVERSATION_FROM = """
            FROM assistant_conversations c JOIN students s ON s.id = c.student_id
            WHERE TRUE
            """;
    // The question of an answer is the Student's message stored just before it in the same conversation.
    private static final String FEEDBACK_FROM = """
            FROM assistant_message_feedback f
            JOIN assistant_messages a ON a.id = f.message_id
            JOIN assistant_conversations c ON c.id = f.conversation_id
            JOIN students s ON s.id = f.student_id
            LEFT JOIN app_users u ON u.id = f.reviewed_by
            LEFT JOIN LATERAL (
                SELECT m.content FROM assistant_messages m
                WHERE m.conversation_id = a.conversation_id AND m.role = 'USER' AND m.id < a.id
                ORDER BY m.id DESC LIMIT 1) q ON TRUE
            WHERE TRUE
            """;
    private static final RowMapper<ConversationRow> CONVERSATION = (rs, i) -> new ConversationRow(
            rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("last_message_preview"), rs.getInt("message_count"),
            instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("student_id"), rs.getString("student_code"),
            rs.getString("student_name"));
    private static final RowMapper<FeedbackRow> FEEDBACK = (rs, i) -> new FeedbackRow(
            rs.getLong("id"), rs.getString("rating"), rs.getString("comment"), rs.getString("review_status"),
            rs.getString("review_note"), rs.getObject("reviewer_id", Long.class), rs.getString("reviewer_name"),
            instant(rs, "reviewed_at"), instant(rs, "submitted_at"), rs.getLong("student_id"), rs.getString("student_code"),
            rs.getString("student_name"), rs.getObject("conversation_id", UUID.class), rs.getString("conversation_title"),
            rs.getLong("message_id"), rs.getString("question"), rs.getString("answer"), rs.getString("model"),
            rs.getString("sources"));

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAssistantQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---- conversations -------------------------------------------------------------------------------------------

    public long countConversations(ConversationFilter filter) {
        var params = new MapSqlParameterSource();
        Long count = jdbc.queryForObject("SELECT count(*) " + CONVERSATION_FROM + conversationWhere(filter, params), params, Long.class);
        return count == null ? 0 : count;
    }

    /** Newest activity first. */
    public List<ConversationRow> findConversations(ConversationFilter filter, int limit, long offset) {
        var params = new MapSqlParameterSource("limit", limit).addValue("offset", offset);
        return jdbc.query(CONVERSATION_COLUMNS + CONVERSATION_FROM + conversationWhere(filter, params)
                + " ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit OFFSET :offset", params, CONVERSATION);
    }

    public Optional<ConversationRow> findConversation(UUID id) {
        return jdbc.query(CONVERSATION_COLUMNS + CONVERSATION_FROM + " AND c.id = :id", new MapSqlParameterSource("id", id),
                CONVERSATION).stream().findFirst();
    }

    // A conversation matches the search by its title, its latest answer, its Student or the text of any of its messages.
    private static String conversationWhere(ConversationFilter filter, MapSqlParameterSource params) {
        var where = new StringBuilder();
        if (filter.search() != null) {
            params.addValue("pattern", pattern(filter.search()));
            where.append("""
                     AND (LOWER(c.title) LIKE :pattern ESCAPE '\\'
                          OR LOWER(COALESCE(c.last_message_preview, '')) LIKE :pattern ESCAPE '\\'
                          OR LOWER(s.student_code) LIKE :pattern ESCAPE '\\'
                          OR LOWER(s.first_name || ' ' || s.last_name) LIKE :pattern ESCAPE '\\'
                          OR EXISTS (SELECT 1 FROM assistant_messages m
                                     WHERE m.conversation_id = c.id AND LOWER(m.content) LIKE :pattern ESCAPE '\\'))
                    """);
        }
        if (filter.from() != null) {
            params.addValue("from", time(filter.from()));
            where.append(" AND c.updated_at >= :from");
        }
        if (filter.to() != null) {
            params.addValue("to", time(filter.to()));
            where.append(" AND c.updated_at < :to");
        }
        return where.toString();
    }

    // ---- feedback ------------------------------------------------------------------------------------------------

    public long countFeedback(FeedbackFilter filter) {
        var params = new MapSqlParameterSource();
        Long count = jdbc.queryForObject("SELECT count(*) " + FEEDBACK_FROM + feedbackWhere(filter, params), params, Long.class);
        return count == null ? 0 : count;
    }

    /** Newest submission first; question and answer are cut to previewChars characters. */
    public List<FeedbackRow> findFeedback(FeedbackFilter filter, int limit, long offset, int previewChars) {
        var params = new MapSqlParameterSource("limit", limit).addValue("offset", offset).addValue("previewChars", previewChars);
        return jdbc.query(feedbackColumns("LEFT(q.content, :previewChars)", "LEFT(a.content, :previewChars)") + FEEDBACK_FROM
                + feedbackWhere(filter, params) + " ORDER BY f.submitted_at DESC, f.id DESC LIMIT :limit OFFSET :offset", params, FEEDBACK);
    }

    /** One record with the complete question and answer. */
    public Optional<FeedbackRow> findFeedback(long id) {
        return jdbc.query(feedbackColumns("q.content", "a.content") + FEEDBACK_FROM + " AND f.id = :id",
                new MapSqlParameterSource("id", id), FEEDBACK).stream().findFirst();
    }

    public FeedbackTotals feedbackTotals() {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE rating = 'POSITIVE') AS positive,
                       count(*) FILTER (WHERE rating = 'NEGATIVE') AS negative,
                       count(*) FILTER (WHERE review_status = 'PENDING') AS awaiting
                FROM assistant_message_feedback""", new MapSqlParameterSource(),
                (rs, i) -> new FeedbackTotals(rs.getLong("total"), rs.getLong("positive"), rs.getLong("negative"), rs.getLong("awaiting")));
    }

    private static String feedbackColumns(String question, String answer) {
        return "SELECT f.id, f.rating, f.comment, f.review_status, f.review_note, u.id AS reviewer_id, "
                + "u.first_name || ' ' || u.last_name AS reviewer_name, f.reviewed_at, f.submitted_at, "
                + "s.id AS student_id, s.student_code, s.first_name || ' ' || s.last_name AS student_name, "
                + "f.conversation_id, c.title AS conversation_title, f.message_id, " + question + " AS question, "
                + answer + " AS answer, a.model, a.sources ";
    }

    // Feedback matches the search by its comment, the question, the answer, the conversation title or the Student.
    private static String feedbackWhere(FeedbackFilter filter, MapSqlParameterSource params) {
        var where = new StringBuilder();
        if (filter.search() != null) {
            params.addValue("pattern", pattern(filter.search()));
            where.append("""
                     AND (LOWER(COALESCE(f.comment, '')) LIKE :pattern ESCAPE '\\'
                          OR LOWER(COALESCE(q.content, '')) LIKE :pattern ESCAPE '\\'
                          OR LOWER(a.content) LIKE :pattern ESCAPE '\\'
                          OR LOWER(c.title) LIKE :pattern ESCAPE '\\'
                          OR LOWER(s.student_code) LIKE :pattern ESCAPE '\\'
                          OR LOWER(s.first_name || ' ' || s.last_name) LIKE :pattern ESCAPE '\\')
                    """);
        }
        if (filter.rating() != null) {
            params.addValue("rating", filter.rating());
            where.append(" AND f.rating = :rating");
        }
        if (filter.reviewStatus() != null) {
            params.addValue("reviewStatus", filter.reviewStatus());
            where.append(" AND f.review_status = :reviewStatus");
        }
        if (filter.from() != null) {
            params.addValue("from", time(filter.from()));
            where.append(" AND f.submitted_at >= :from");
        }
        if (filter.to() != null) {
            params.addValue("to", time(filter.to()));
            where.append(" AND f.submitted_at < :to");
        }
        return where.toString();
    }

    // ---- shared --------------------------------------------------------------------------------------------------

    /** A case-insensitive "contains" pattern; %, _ and \ of the search text match themselves. */
    static String pattern(String search) {
        return "%" + search.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static OffsetDateTime time(Instant instant) { return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC); }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
