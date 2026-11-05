package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared fixture for the curriculum and knowledge tests: one admin, two faculties' worth of academic structure. */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
public abstract class KnowledgeIntegrationSupport extends IntegrationSupport {
    protected static final String PASSWORD = "knowledge actor password";
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate db;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected MutableClock clock;
    protected long actorId, facultyId, departmentA, departmentB, programA, programB;
    protected String access;

    @BeforeEach
    protected void knowledgeFixture() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clearKnowledgeTables();
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM course_sections");
        db.update("DELETE FROM courses");
        db.update("DELETE FROM lectures");
        db.update("DELETE FROM semesters");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('KNW-ACTOR','Knowledge','Admin','knowledge.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyId = db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('KF','Knowledge Faculty',?) RETURNING id",
                Long.class, actorId);
        departmentA = department("KD-A", "Computer Science");
        departmentB = department("KD-B", "Information Technology");
        programA = program("KP-CS", "Bachelor of Computer Science", departmentA);
        programB = program("KP-IT", "Bachelor of Information Technology", departmentB);
    }

    /** Other test classes delete programs, departments and users; leave no rows that reference them. */
    @AfterEach
    protected void clearKnowledgeTables() {
        SecurityContextHolder.clearContext();
        db.update("DELETE FROM academic_document_processing");
        db.update("DELETE FROM academic_document_files");
        db.update("DELETE FROM academic_documents");
        db.update("DELETE FROM document_categories");
        db.update("DELETE FROM curriculum_versions");
    }

    protected void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    protected long department(String code, String name) {
        return db.queryForObject("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES (?,?,?,?) RETURNING id",
                Long.class, code, name, facultyId, actorId);
    }
    protected long program(String code, String name, long departmentId) {
        return db.queryForObject("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES (?,?,'Bachelor',?,?) RETURNING id",
                Long.class, code, name, departmentId, actorId);
    }
    protected long curriculum(long programId, String code, String status) {
        return db.queryForObject("""
                INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,status,created_by)
                VALUES (?,?,?,'2024-2025',?,?) RETURNING id""", Long.class, programId, code, code + " curriculum", status, actorId);
    }
    protected long category(String code, String name) {
        return db.queryForObject("INSERT INTO document_categories(category_code,category_name,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, code, name, actorId);
    }
    /** Inserts a document directly (bypassing upload) with an optional scope column and its current revision. */
    protected long document(String title, long categoryId, String year, String scopeColumn, Long scopeId, String status) {
        long id = db.queryForObject("INSERT INTO academic_documents(title,description,category_id,academic_year_label,"
                + (scopeColumn == null ? "created_by" : scopeColumn + ",created_by") + ") VALUES (?,?,?,?,"
                + (scopeColumn == null ? "?" : "?,?") + ") RETURNING id", Long.class,
                scopeColumn == null ? new Object[] {title, title + " description", categoryId, year, actorId}
                        : new Object[] {title, title + " description", categoryId, year, scopeId, actorId});
        if (status != null) revision(id, 1, true, status);
        return id;
    }
    protected long revision(long documentId, int number, boolean current, String status) {
        long file = db.queryForObject("""
                INSERT INTO academic_document_files(document_id,revision_number,original_filename,storage_key,mime_type,
                    file_extension,size_bytes,sha256_checksum,is_current,uploaded_by)
                VALUES (?,?,?,?,'application/pdf','pdf',1234,repeat('a',64),?,?) RETURNING id""", Long.class,
                documentId, number, "doc-" + documentId + "-r" + number + ".pdf",
                "knowledge/test/" + documentId + "-" + number + ".pdf", current, actorId);
        db.update("INSERT INTO academic_document_processing(file_id,status) VALUES (?,?)", file, status);
        return file;
    }
    protected String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "knowledge.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    protected ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    protected JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
