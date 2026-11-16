package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.MigrationVersions;
import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class KnowledgeSchemaMigrationTests extends IntegrationSupport {
    private static final List<String> TABLES = List.of("curriculum_versions", "document_categories",
            "academic_documents", "academic_document_files", "academic_document_processing");
    private String schema;
    private Flyway flyway;
    private JdbcTemplate db;

    @BeforeEach
    void migrate() {
        schema = "knowledge_" + UUID.randomUUID().toString().replace("-", "");
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).cleanDisabled(false).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(MigrationVersions.count());
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        db = new JdbcTemplate(dataSource);
    }

    @AfterEach void clean() { flyway.clean(); }

    @Test
    void historyTablesAndNoSeededDocuments() {
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                .contains("21", "22", "23", "24", "25");
        for (String table : TABLES) {
            assertThat(db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?",
                    Integer.class, schema, table)).as(table).isOne();
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
    }

    @Test
    void foreignKeysExistAndNeverCascade() {
        var keys = db.queryForList("""
                SELECT c.conname, c.confdeltype FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
                WHERE n.nspname=? AND c.contype='f' AND c.conrelid::regclass::text = ANY(?)
                """, schema, TABLES.toArray(String[]::new));
        assertThat(keys).hasSize(15);
        assertThat(keys).allSatisfy(key -> assertThat(key.get("confdeltype").toString()).isEqualTo("a"));
        assertThat(keys).extracting(key -> key.get("conname")).contains("fk_curriculum_versions_program",
                "fk_academic_documents_category", "fk_academic_documents_curriculum", "fk_academic_documents_program",
                "fk_academic_documents_department", "fk_academic_document_files_document",
                "fk_academic_document_files_uploaded_by", "fk_academic_document_processing_file",
                "fk_academic_document_processing_queued_by");
    }

    @Test
    void constraintsEnforceCurriculumScopeRevisionAndProcessingRules() {
        long user = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('SCHEMA-1','Schema','Tester','schema@example.test','ACTIVE') RETURNING id""", Long.class);
        long faculty = db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('F','F',?) RETURNING id", Long.class, user);
        long department = db.queryForObject("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('D','D',?,?) RETURNING id",
                Long.class, faculty, user);
        long program = db.queryForObject("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('P','P','B',?,?) RETURNING id",
                Long.class, department, user);
        // The en dash label from the specification fits VARCHAR(20).
        long active = db.queryForObject("""
                INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,created_by)
                VALUES (?,'CS-2024','Curriculum 2024','2024–2025',?) RETURNING id""", Long.class, program, user);
        assertThat(db.queryForObject("SELECT status FROM curriculum_versions WHERE id=?", String.class, active)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> db.update("INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label) VALUES (?,'CS-2025','C','2025-2026')", program))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_curriculum_versions_one_active");
        db.update("INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,status) VALUES (?,'CS-2025','C','2025-2026','ARCHIVED')", program);
        assertThatThrownBy(() -> db.update("INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,status) VALUES (?,'CS-2025','C','2025-2026','ARCHIVED')", program))
                .hasMessageContaining("uk_curriculum_versions_program_code");
        assertThatThrownBy(() -> db.update("INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,status) VALUES (?,'X','C','y','DRAFT')", program))
                .hasMessageContaining("chk_curriculum_versions_status");
        assertThatThrownBy(() -> db.update("INSERT INTO curriculum_versions(program_id,version_code,version_name,academic_year_label,status,effective_from,effective_to) VALUES (?,'Y','C','y','ARCHIVED','2025-01-02','2025-01-01')", program))
                .hasMessageContaining("chk_curriculum_versions_dates");

        long category = db.queryForObject("INSERT INTO document_categories(category_code,category_name) VALUES ('CURRICULUM','Curriculum') RETURNING id", Long.class);
        assertThatThrownBy(() -> db.update("INSERT INTO document_categories(category_code,category_name) VALUES ('CURRICULUM','Again')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO academic_documents(title,category_id,academic_year_label,program_id,department_id) VALUES ('T',?,'2024-2025',?,?)",
                category, program, department)).hasMessageContaining("chk_academic_documents_scope");
        long document = db.queryForObject("INSERT INTO academic_documents(title,category_id,academic_year_label,curriculum_version_id) VALUES ('T',?,'2024-2025',?) RETURNING id",
                Long.class, category, active);
        db.update("INSERT INTO academic_documents(title,category_id,academic_year_label) VALUES ('T',?,'2024-2025')", category);

        long file = file(document, 1, "k1", true, user);
        assertThatThrownBy(() -> file(document, 2, "k2", true, user)).hasMessageContaining("uk_academic_document_files_one_current");
        file(document, 2, "k2", false, user);
        assertThatThrownBy(() -> file(document, 3, "k2", false, user)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> file(document, 2, "k3", false, user)).hasMessageContaining("uk_academic_document_files_revision");
        assertThatThrownBy(() -> file(document, 0, "k4", false, user)).hasMessageContaining("chk_academic_document_files_revision");

        db.update("INSERT INTO academic_document_processing(file_id) VALUES (?)", file);
        assertThat(db.queryForMap("SELECT status,attempt_count FROM academic_document_processing WHERE file_id=?", file))
                .containsEntry("status", "UPLOADED").containsEntry("attempt_count", 0);
        assertThatThrownBy(() -> db.update("INSERT INTO academic_document_processing(file_id) VALUES (?)", file))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE academic_document_processing SET status='DONE' WHERE file_id=?", file))
                .hasMessageContaining("chk_academic_document_processing_status");
        assertThatThrownBy(() -> db.update("DELETE FROM academic_documents WHERE id=?", document))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long file(long document, int revision, String key, boolean current, long user) {
        return db.queryForObject("""
                INSERT INTO academic_document_files(document_id,revision_number,original_filename,storage_key,mime_type,
                    file_extension,size_bytes,sha256_checksum,is_current,uploaded_by)
                VALUES (?,?,'a.pdf',?,'application/pdf','pdf',10,repeat('a',64),?,?) RETURNING id""",
                Long.class, document, revision, key, current, user);
    }
}
