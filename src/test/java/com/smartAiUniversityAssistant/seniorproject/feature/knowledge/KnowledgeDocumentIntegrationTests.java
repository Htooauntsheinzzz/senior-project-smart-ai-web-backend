package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeDocumentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeDocumentIntegrationTests extends KnowledgeIntegrationSupport {
    private static final String BASE = "/api/v1/admin/knowledge/documents";
    @Autowired KnowledgeDocumentService documents;

    @Test
    void listReturnsResolvedScopeCurrentFileUploaderAndStatus() throws Exception {
        long handbook = category("STUDENT_HANDBOOK", "Student Handbook");
        long curriculumCategory = category("CURRICULUM", "Curriculum");
        long cs = curriculum(programA, "CS-2024", "ACTIVE");
        long university = document("Student Handbook 2024-2025", handbook, "2024-2025", null, null, "UPLOADED");
        long curriculumDoc = document("CS curriculum", curriculumCategory, "2024-2025", "curriculum_version_id", cs, "READY");
        perform(get(BASE + "/" + university), null).andExpect(status().isOk())
                .andExpect(jsonPath("title").value("Student Handbook 2024-2025"))
                .andExpect(jsonPath("category.code").value("STUDENT_HANDBOOK"))
                .andExpect(jsonPath("scope.type").value("UNIVERSITY"))
                .andExpect(jsonPath("scope.programId").doesNotExist())
                .andExpect(jsonPath("file.extension").value("pdf"))
                .andExpect(jsonPath("file.revisionNumber").value(1))
                .andExpect(jsonPath("file.storageKey").doesNotExist())
                .andExpect(jsonPath("uploadedBy.id").value(actorId))
                .andExpect(jsonPath("uploadedBy.displayName").value("Knowledge Admin"))
                .andExpect(jsonPath("processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("isActive").value(true));
        perform(get(BASE + "/" + curriculumDoc), null).andExpect(status().isOk())
                .andExpect(jsonPath("scope.type").value("CURRICULUM"))
                .andExpect(jsonPath("scope.curriculumVersionCode").value("CS-2024"))
                .andExpect(jsonPath("scope.programId").value(programA))
                .andExpect(jsonPath("scope.programName").value("Bachelor of Computer Science"))
                .andExpect(jsonPath("scope.departmentId").value(departmentA))
                .andExpect(jsonPath("processingStatus").value("READY"));
        perform(get(BASE).param("page", "0").param("size", "10"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("content[*].id", containsInAnyOrder((int) university, (int) curriculumDoc)));
    }

    @Test
    void searchAndCombinedFiltersUseCurrentRevisionStatusAndResolvedScope() throws Exception {
        long handbook = category("STUDENT_HANDBOOK", "Student Handbook");
        long syllabus = category("COURSE_SYLLABUS", "Course Syllabus");
        long cs = curriculum(programA, "CS-2024", "ACTIVE");
        long a = document("Handbook 2024", handbook, "2024-2025", null, null, "READY");
        long b = document("Handbook 2023", handbook, "2023-2024", null, null, "READY");
        long c = document("CS syllabus", syllabus, "2024-2025", "curriculum_version_id", cs, "FAILED");
        long d = document("IT syllabus", syllabus, "2024-2025", "program_id", programB, "UPLOADED");
        long e = document("CS guidelines", handbook, "2024-2025", "department_id", departmentA, "QUEUED");
        // Replaced file: the old revision was READY, the current one is UPLOADED.
        db.update("UPDATE academic_document_files SET is_current=FALSE WHERE document_id=?", e);
        revision(e, 2, true, "UPLOADED");
        db.update("UPDATE academic_document_processing p SET status='READY' FROM academic_document_files f "
                + "WHERE f.id=p.file_id AND f.document_id=? AND f.revision_number=1", e);
        expectIds(Map.of("categoryId", handbook, "academicYear", "2024-2025", "status", "READY"), a);
        expectIds(Map.of("status", "READY"), a, b);
        expectIds(Map.of("status", "UPLOADED"), d, e);
        expectIds(Map.of("academicYear", "2023–2024"), b);
        expectIds(Map.of("search", "SYLLABUS"), c, d);
        expectIds(Map.of("search", "Handbook 2023 description"), b);
        expectIds(Map.of("programId", programA), c);
        expectIds(Map.of("departmentId", departmentA), c, e);
        expectIds(Map.of("departmentId", departmentB), d);
        expectIds(Map.of("curriculumVersionId", cs), c);
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", b);
        expectIds(Map.of("isActive", false), b);
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", a);
        expectIds(Map.of("categoryId", handbook), b, e);
        perform(get(BASE).param("size", "2").param("sort", "title,asc"), null)
                .andExpect(jsonPath("totalElements").value(4)).andExpect(jsonPath("totalPages").value(2))
                .andExpect(jsonPath("content[0].title").value("CS guidelines"));
        for (var bad : List.of(Map.of("status", "DONE"), Map.of("isActive", "yes"), Map.of("categoryId", "0"),
                Map.of("academicYear", "2024"), Map.of("sort", "category,asc"), Map.of("other", "1")))
            perform(get(BASE).param(bad.keySet().iterator().next(), bad.values().iterator().next()), null)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
    }

    @Test
    void statisticsCountDocumentsByCurrentRevisionWithOneFilter() throws Exception {
        long category = category("UNIVERSITY_POLICIES", "University Policies");
        document("Ready", category, "2024-2025", null, null, "READY");
        document("Ready 2", category, "2024-2025", null, null, "READY");
        long replaced = document("Replaced", category, "2024-2025", null, null, "READY");
        db.update("UPDATE academic_document_files SET is_current=FALSE WHERE document_id=?", replaced);
        revision(replaced, 2, true, "UPLOADED");
        document("Embedding", category, "2024-2025", null, null, "EMBEDDING");
        document("Failed", category, "2024-2025", null, null, "FAILED");
        long archived = document("Archived", category, "2024-2025", null, null, "READY");
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archived);
        long deleted = document("Deleted", category, "2024-2025", null, null, "FAILED");
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deleted);
        perform(get(BASE + "/statistics"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalDocuments").value(5))
                .andExpect(jsonPath("readyDocuments").value(2))
                .andExpect(jsonPath("processingDocuments").value(2))
                .andExpect(jsonPath("failedDocuments").value(1));
    }

    @Test
    void metadataUpdateValidatesCategoryAndScopeAndAttributesTheAdmin() throws Exception {
        long handbook = category("PROGRAM_HANDBOOK", "Program Handbook");
        long inactive = category("FINANCIAL_AID", "Financial Aid");
        db.update("UPDATE document_categories SET is_active=FALSE WHERE id=?", inactive);
        long cs = curriculum(programA, "CS-2024", "ARCHIVED");
        long id = document("Handbook", handbook, "2024-2025", null, null, "UPLOADED");
        clock.advance(Duration.ofSeconds(7));
        perform(put(BASE + "/" + id), update("CS Program Handbook", handbook, null, programA, null)).andExpect(status().isOk())
                .andExpect(jsonPath("title").value("CS Program Handbook"))
                .andExpect(jsonPath("academicYearLabel").value("2025-2026"))
                .andExpect(jsonPath("scope.type").value("PROGRAM"))
                .andExpect(jsonPath("scope.departmentName").value("Computer Science"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT program_id,department_id,curriculum_version_id,updated_by FROM academic_documents WHERE id=?", id))
                .containsEntry("program_id", programA).containsEntry("updated_by", actorId)
                .containsEntry("department_id", null).containsEntry("curriculum_version_id", null);
        perform(put(BASE + "/" + id), update("X", handbook, cs, null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("scope.type").value("CURRICULUM")).andExpect(jsonPath("scope.curriculumStatus").value("ARCHIVED"));
        perform(put(BASE + "/" + id), update("X", handbook, null, programA, departmentA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        perform(put(BASE + "/" + id), update("X", handbook, null, 999_999L, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        db.update("UPDATE programs SET is_active=FALSE WHERE id=?", programB);
        perform(put(BASE + "/" + id), update("X", handbook, null, programB, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        perform(put(BASE + "/" + id), update("X", inactive, null, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_INVALID"));
        perform(put(BASE + "/" + id), update("X", handbook, null, null, departmentB)).andExpect(status().isOk())
                .andExpect(jsonPath("scope.type").value("DEPARTMENT"));
        var withAudit = update("X", handbook, null, null, null);
        withAudit.put("updatedBy", 1);
        perform(put(BASE + "/" + id), withAudit).andExpect(status().isBadRequest());
        var badYear = update("X", handbook, null, null, null);
        badYear.put("academicYearLabel", "2025");
        perform(put(BASE + "/" + id), badYear).andExpect(status().isBadRequest());
        perform(patch(BASE + "/" + id + "/archive"), null).andExpect(status().isOk());
        perform(put(BASE + "/" + id), update("X", handbook, null, null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DOCUMENT_NOT_EDITABLE"));
    }

    @Test
    void archiveActivateAndSoftDeleteKeepFileRevisions() throws Exception {
        long category = category("COURSE_CATALOG", "Course Catalog");
        long id = document("Catalog", category, "2024-2025", "program_id", programA, "READY");
        perform(patch(BASE + "/" + id + "/archive"), null).andExpect(status().isOk()).andExpect(jsonPath("isActive").value(false))
                .andExpect(jsonPath("processingStatus").value("READY")).andExpect(jsonPath("updatedBy").value(actorId));
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files WHERE document_id=?", Integer.class, id)).isOne();
        perform(get(BASE + "/statistics"), null).andExpect(jsonPath("totalDocuments").value(0));
        db.update("UPDATE programs SET is_active=FALSE WHERE id=?", programA);
        perform(patch(BASE + "/" + id + "/activate"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        db.update("UPDATE programs SET is_active=TRUE WHERE id=?", programA);
        perform(patch(BASE + "/" + id + "/activate"), null).andExpect(status().isOk()).andExpect(jsonPath("isActive").value(true));
        perform(patch(BASE + "/" + id + "/activate"), null).andExpect(status().isOk());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
        perform(patch(BASE + "/" + id + "/activate"), null).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT is_deleted,updated_by FROM academic_documents WHERE id=?", id))
                .containsEntry("is_deleted", true).containsEntry("updated_by", actorId);
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files WHERE document_id=?", Integer.class, id)).isOne();
    }

    @Test
    void requiresAdministrativeRoleAndAuthentication() throws Exception {
        long category = category("CAMPUS_SAFETY", "Campus Safety");
        long id = document("Safety", category, "2024-2025", null, null, "UPLOADED");
        for (String role : List.of("ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            perform(patch(BASE + "/" + id + "/archive"), null).andExpect(status().isOk());
            perform(patch(BASE + "/" + id + "/activate"), null).andExpect(status().isOk());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('KNW_TEST_READER','Knowledge reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("KNW_TEST_READER");
        for (var route : List.of(get(BASE), get(BASE + "/statistics"), get(BASE + "/" + id), patch(BASE + "/" + id + "/archive"), delete(BASE + "/" + id)))
            perform(route, null).andExpect(status().isForbidden());
        for (var route : List.of(get(BASE), get(BASE + "/statistics"), get(BASE + "/" + id), put(BASE + "/" + id), delete(BASE + "/" + id)))
            mvc.perform(route).andExpect(status().isUnauthorized());
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> documents.statistics(principal)).isInstanceOf(AccessDeniedException.class);
        assertThat(db.queryForObject("SELECT is_active FROM academic_documents WHERE id=?", Boolean.class, id)).isTrue();
    }

    private void expectIds(Map<String, Object> parameters, long... ids) throws Exception {
        var builder = get(BASE).param("size", "100");
        parameters.forEach((key, value) -> builder.param(key, String.valueOf(value)));
        var body = tree(perform(builder, null).andExpect(status().isOk()).andReturn());
        var found = new ArrayList<Long>();
        body.get("content").forEach(node -> found.add(node.get("id").asLong()));
        assertThat(found).as(parameters.toString()).containsExactlyInAnyOrder(Arrays.stream(ids).boxed().toArray(Long[]::new));
    }

    private Map<String, Object> update(String title, long categoryId, Long curriculumId, Long programId, Long departmentId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("title", title);
        body.put("description", "Updated description");
        body.put("categoryId", categoryId);
        body.put("academicYearLabel", "2025–2026");
        body.put("curriculumVersionId", curriculumId);
        body.put("programId", programId);
        body.put("departmentId", departmentId);
        return body;
    }
}
