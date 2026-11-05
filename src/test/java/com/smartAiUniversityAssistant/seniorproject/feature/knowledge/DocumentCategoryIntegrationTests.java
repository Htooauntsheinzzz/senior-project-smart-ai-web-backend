package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import java.util.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentCategoryIntegrationTests extends KnowledgeIntegrationSupport {
    private static final String BASE = "/api/v1/admin/knowledge/categories";

    @Test
    void crudWithUniqueCodesAndAuditAttribution() throws Exception {
        var created = perform(post(BASE), body(" curriculum ", "Curriculum")).andExpect(status().isCreated())
                .andExpect(jsonPath("categoryCode").value("CURRICULUM"))
                .andExpect(jsonPath("categoryName").value("Curriculum"))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("documentCount").value(0))
                .andExpect(jsonPath("createdBy").value(actorId)).andReturn();
        long id = tree(created).get("id").asLong();
        perform(post(BASE), body("CURRICULUM", "Again")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_DUPLICATE"));
        long handbook = tree(perform(post(BASE), body("STUDENT_HANDBOOK", "Student Handbook")).andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        perform(put(BASE + "/" + handbook), body("CURRICULUM", "Clash")).andExpect(status().isConflict());
        perform(put(BASE + "/" + id), body("CURRICULUM", "Official Curriculum")).andExpect(status().isOk())
                .andExpect(jsonPath("categoryName").value("Official Curriculum")).andExpect(jsonPath("updatedBy").value(actorId));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("categoryCode").value("CURRICULUM"));
        perform(get(BASE).param("search", "hand"), null).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(handbook));
        perform(patch(BASE + "/" + handbook + "/deactivate"), null).andExpect(status().isOk()).andExpect(jsonPath("isActive").value(false));
        perform(get(BASE).param("isActive", "true"), null).andExpect(jsonPath("totalElements").value(1));
        perform(patch(BASE + "/" + handbook + "/activate"), null).andExpect(status().isOk()).andExpect(jsonPath("isActive").value(true));
        perform(delete(BASE + "/" + handbook), null).andExpect(status().isNoContent());
        perform(get(BASE + "/" + handbook), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_NOT_FOUND"));
        // Soft-deleted codes stay reserved by the UNIQUE constraint.
        perform(post(BASE), body("STUDENT_HANDBOOK", "Again")).andExpect(status().isConflict());
        for (var invalid : List.of(body("bad code", "X"), body("", "X"), body("OK", " "), body("1ABC", "X")))
            perform(post(BASE), invalid).andExpect(status().isBadRequest());
        var unknown = body("OTHER", "Other");
        unknown.put("isActive", false);
        perform(post(BASE), unknown).andExpect(status().isBadRequest());
    }

    @Test
    void deactivationKeepsDocumentsReadableAndDeletionIsGuarded() throws Exception {
        long category = category("SCHOLARSHIP_POLICY", "Scholarship Policies");
        long document = document("Scholarship rules", category, "2024-2025", null, null, "UPLOADED");
        perform(get(BASE + "/" + category), null).andExpect(jsonPath("documentCount").value(1));
        perform(delete(BASE + "/" + category), null).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_IN_USE"));
        perform(patch(BASE + "/" + category + "/deactivate"), null).andExpect(status().isOk());
        perform(get("/api/v1/admin/knowledge/documents/" + document), null).andExpect(status().isOk())
                .andExpect(jsonPath("category.isActive").value(false)).andExpect(jsonPath("isActive").value(true));
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", document);
        perform(delete(BASE + "/" + category), null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT is_deleted FROM document_categories WHERE id=?", Boolean.class, category)).isTrue();
    }

    @Test
    void requiresAdministrativeRole() throws Exception {
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('KNW_TEST_READER','Knowledge reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("KNW_TEST_READER");
        perform(get(BASE), null).andExpect(status().isForbidden());
        perform(post(BASE), body("X", "X")).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    private Map<String, Object> body(String code, String name) {
        var body = new LinkedHashMap<String, Object>();
        body.put("categoryCode", code);
        body.put("categoryName", name);
        body.put("description", "Official documents");
        return body;
    }
}
