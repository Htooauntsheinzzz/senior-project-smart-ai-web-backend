package com.smartAiUniversityAssistant.seniorproject.feature.curriculum;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.service.CurriculumVersionService;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeIntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CurriculumIntegrationTests extends KnowledgeIntegrationSupport {
    private static final String BASE = "/api/v1/admin/curriculums";
    @Autowired CurriculumVersionService curriculums;

    @Test
    void createsActiveVersionForValidProgramWithAuditAndNormalizedYear() throws Exception {
        var created = perform(post(BASE), request(programA, " CS-2024 ", "2024–2025")).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("programId").value(programA))
                .andExpect(jsonPath("programName").value("Bachelor of Computer Science"))
                .andExpect(jsonPath("departmentId").value(departmentA))
                .andExpect(jsonPath("versionCode").value("CS-2024"))
                .andExpect(jsonPath("academicYearLabel").value("2024-2025"))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("documentCount").value(0))
                .andExpect(jsonPath("effectiveFrom").value("2024-08-01"))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT created_by,status,is_deleted FROM curriculum_versions WHERE id=?", id))
                .containsEntry("created_by", actorId).containsEntry("status", "ACTIVE").containsEntry("is_deleted", false);
        // A second version never silently replaces the active one: it starts ARCHIVED until activated explicitly.
        perform(post(BASE), request(programA, "CS-2025", "2025-2026")).andExpect(status().isCreated())
                .andExpect(jsonPath("status").value("ARCHIVED"));
        assertThat(activeCount(programA)).isOne();
        clock.advance(Duration.ofSeconds(5));
        var update = request(programA, "CS-2024A", "2024-2025");
        update.remove("programId");
        update.put("versionName", "Updated name");
        perform(put(BASE + "/" + id), update).andExpect(status().isOk())
                .andExpect(jsonPath("versionCode").value("CS-2024A"))
                .andExpect(jsonPath("versionName").value("Updated name"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        update.put("programId", programB);
        perform(put(BASE + "/" + id), update).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsDuplicatesInvalidProgramsAndInvalidInput() throws Exception {
        perform(post(BASE), request(programA, "CS-2024", "2024-2025")).andExpect(status().isCreated());
        perform(post(BASE), request(programA, "CS-2024", "2025-2026")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("CURRICULUM_VERSION_DUPLICATE"));
        perform(post(BASE), request(programB, "CS-2024", "2024-2025")).andExpect(status().isCreated());
        perform(post(BASE), request(999_999L, "X-1", "2024-2025")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("CURRICULUM_INVALID_PROGRAM"));
        long inactive = program("KP-OFF", "Inactive", departmentA);
        db.update("UPDATE programs SET is_active=FALSE WHERE id=?", inactive);
        perform(post(BASE), request(inactive, "X-1", "2024-2025")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("CURRICULUM_INVALID_PROGRAM"));
        long deleted = program("KP-DEL", "Deleted", departmentA);
        db.update("UPDATE programs SET is_deleted=TRUE WHERE id=?", deleted);
        perform(post(BASE), request(deleted, "X-1", "2024-2025")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("CURRICULUM_INVALID_PROGRAM"));
        var dates = request(programA, "X-2", "2024-2025");
        dates.put("effectiveTo", "2024-07-01");
        perform(post(BASE), dates).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        for (String year : List.of("2024", "2024-2026", "24-25", "", "2024/2025"))
            perform(post(BASE), request(programA, "X-3", year)).andExpect(status().isBadRequest());
        perform(post(BASE), request(programA, "bad code", "2024-2025")).andExpect(status().isBadRequest());
        var unknown = request(programA, "X-4", "2024-2025");
        unknown.put("status", "ACTIVE");
        perform(post(BASE), unknown).andExpect(status().isBadRequest());
        var createdBy = request(programA, "X-5", "2024-2025");
        createdBy.put("createdBy", 1);
        perform(post(BASE), createdBy).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT count(*) FROM curriculum_versions", Integer.class)).isEqualTo(2);
    }

    @Test
    void activationArchivesPreviousVersionAtomicallyAndTransitionsAreChecked() throws Exception {
        long first = create(programA, "CS-2020", "2020-2021");
        long second = create(programA, "CS-2024", "2024-2025");
        perform(patch(BASE + "/" + first + "/activate"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("CURRICULUM_ALREADY_ACTIVE"));
        perform(patch(BASE + "/" + second + "/activate"), null).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("ACTIVE")).andExpect(jsonPath("updatedBy").value(actorId));
        assertThat(statusOf(first)).isEqualTo("ARCHIVED");
        assertThat(statusOf(second)).isEqualTo("ACTIVE");
        perform(patch(BASE + "/" + second + "/archive"), null).andExpect(status().isOk()).andExpect(jsonPath("status").value("ARCHIVED"));
        perform(patch(BASE + "/" + second + "/archive"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("CURRICULUM_INVALID_STATUS_TRANSITION"));
        assertThat(activeCount(programA)).isZero();
        perform(patch(BASE + "/" + first + "/activate"), null).andExpect(status().isOk());
        db.update("UPDATE programs SET is_active=FALSE WHERE id=?", programA);
        perform(patch(BASE + "/" + second + "/activate"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("CURRICULUM_INVALID_PROGRAM"));
        perform(patch(BASE + "/999999/activate"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("CURRICULUM_NOT_FOUND"));
    }

    @Test
    void concurrentActivationsLeaveExactlyOneActiveVersion() throws Exception {
        create(programA, "CS-A", "2020-2021");
        long b = create(programA, "CS-B", "2021-2022");
        long c = create(programA, "CS-C", "2022-2023");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = pool.submit(() -> { barrier.await(); return perform(patch(BASE + "/" + b + "/activate"), null).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { barrier.await(); return perform(patch(BASE + "/" + c + "/activate"), null).andReturn().getResponse().getStatus(); });
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).allMatch(code -> code == 200 || code == 409);
        }
        assertThat(activeCount(programA)).isOne();
    }

    @Test
    void listFiltersAndDerivesDocumentCountsAndDeleteIsGuarded() throws Exception {
        long cs = create(programA, "CS-2024", "2024-2025");
        long csOld = create(programA, "CS-2019", "2019-2020");
        long it = create(programB, "IT-2024", "2024-2025");
        long category = category("CURRICULUM", "Curriculum");
        document("Active", category, "2024-2025", "curriculum_version_id", cs, "UPLOADED");
        long archived = document("Archived", category, "2024-2025", "curriculum_version_id", cs, "READY");
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archived);
        long deleted = document("Deleted", category, "2024-2025", "curriculum_version_id", cs, "READY");
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deleted);
        perform(get(BASE + "/" + cs), null).andExpect(status().isOk()).andExpect(jsonPath("documentCount").value(1));
        perform(get(BASE).param("programId", String.valueOf(programA)).param("sort", "versionCode,asc"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("content[0].versionCode").value("CS-2019"))
                .andExpect(jsonPath("content[1].documentCount").value(1));
        perform(get(BASE).param("academicYear", "2024–2025"), null).andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("status", "ARCHIVED"), null).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(csOld));
        perform(get(BASE).param("search", "it-20"), null).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(it));
        perform(get(BASE).param("size", "1").param("page", "1").param("sort", "versionCode,asc"), null)
                .andExpect(jsonPath("content[0].versionCode").value("CS-2024")).andExpect(jsonPath("totalPages").value(3));
        for (var bad : List.of(Map.of("status", "DRAFT"), Map.of("size", "101"), Map.of("academicYear", "2024"),
                Map.of("sort", "programId,asc"), Map.of("unknown", "1")))
            perform(get(BASE).param(bad.keySet().iterator().next(), bad.values().iterator().next()), null)
                    .andExpect(status().isBadRequest());
        perform(delete(BASE + "/" + cs), null).andExpect(status().isConflict()).andExpect(jsonPath("code").value("CURRICULUM_HAS_DOCUMENTS"));
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE curriculum_version_id=?", cs);
        perform(delete(BASE + "/" + cs), null).andExpect(status().isNoContent());
        perform(get(BASE + "/" + cs), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("CURRICULUM_NOT_FOUND"));
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files f JOIN academic_documents d ON d.id=f.document_id WHERE d.curriculum_version_id=?",
                Integer.class, cs)).isEqualTo(3);
        // The deleted version frees the active slot for the program.
        perform(patch(BASE + "/" + csOld + "/activate"), null).andExpect(status().isOk());
    }

    @Test
    void adminRolesAuthorityAndAuthenticationAreEnforced() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create(programA, "R-" + role.replace('_', '-'), "2024-2025");
            perform(patch(BASE + "/" + id + "/archive"), null).andExpect(status().isOk());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('KNW_TEST_READER','Knowledge reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("KNW_TEST_READER");
        perform(get(BASE), null).andExpect(status().isForbidden());
        perform(post(BASE), request(programA, "NO", "2024-2025")).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?", actorId);
        for (var route : List.of(get(BASE), get(BASE + "/1"), post(BASE), put(BASE + "/1"), patch(BASE + "/1/activate"), delete(BASE + "/1")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> curriculums.activate(principal, 1)).isInstanceOf(AccessDeniedException.class);
    }

    private long create(long programId, String code, String year) throws Exception {
        return tree(perform(post(BASE), request(programId, code, year)).andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(long programId, String code, String year) {
        var body = new LinkedHashMap<String, Object>();
        body.put("programId", programId);
        body.put("versionCode", code);
        body.put("versionName", "Computer Science Curriculum " + code.trim());
        body.put("academicYearLabel", year);
        body.put("description", "Official curriculum");
        body.put("effectiveFrom", "2024-08-01");
        body.put("effectiveTo", null);
        return body;
    }
    private int activeCount(long programId) {
        return db.queryForObject("SELECT count(*) FROM curriculum_versions WHERE program_id=? AND status='ACTIVE' AND NOT is_deleted",
                Integer.class, programId);
    }
    private String statusOf(long id) {
        return db.queryForObject("SELECT status FROM curriculum_versions WHERE id=?", String.class, id);
    }
}
