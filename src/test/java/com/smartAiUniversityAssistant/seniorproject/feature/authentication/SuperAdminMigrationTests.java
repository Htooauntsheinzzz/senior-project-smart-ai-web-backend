package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class SuperAdminMigrationTests extends IntegrationSupport {
    @Test
    void migrationCreatesRequiredAccountAndDoesNotDuplicateIt() {
        String schema = "super_admin_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema)
                .schemas(schema)
                .cleanDisabled(false)
                .load();
        try {
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
            var dataSource = new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var db = new JdbcTemplate(dataSource);
            Map<String, Object> account = db.queryForMap("""
                    SELECT u.employee_id, u.first_name, u.last_name, u.email,
                           u.department_id, u.account_status, u.is_deleted,
                           c.password_hash, c.force_password_change,
                           c.failed_login_attempts, r.role_code
                    FROM %1$s.app_users u
                    JOIN %1$s.appuser_credentials c ON c.user_id=u.id
                    JOIN %1$s.app_user_roles ur ON ur.user_id=u.id
                    JOIN %1$s.app_roles r ON r.id=ur.role_id
                    WHERE u.email='smartairsu@rsu.ac.th'
                    """.formatted(schema));

            assertThat(account).containsEntry("employee_id", "RSU-SUPER-ADMIN")
                    .containsEntry("first_name", "Smart AI")
                    .containsEntry("last_name", "Super Admin")
                    .containsEntry("email", "smartairsu@rsu.ac.th")
                    .containsEntry("account_status", "ACTIVE")
                    .containsEntry("is_deleted", false)
                    .containsEntry("force_password_change", true)
                    .containsEntry("failed_login_attempts", 0)
                    .containsEntry("role_code", "SUPER_ADMIN");
            assertThat(account.get("department_id")).isNull();
            assertThat(new BCryptPasswordEncoder().matches(
                    "67smartaiP@ssw0rd", (String) account.get("password_hash"))).isTrue();

            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM " + schema + ".app_users", Integer.class)).isOne();
            assertThat(db.queryForObject("SELECT count(*) FROM " + schema + ".appuser_credentials", Integer.class)).isOne();
            assertThat(db.queryForObject("SELECT count(*) FROM " + schema + ".app_user_roles", Integer.class)).isOne();
        } finally {
            flyway.clean();
        }
    }
}
