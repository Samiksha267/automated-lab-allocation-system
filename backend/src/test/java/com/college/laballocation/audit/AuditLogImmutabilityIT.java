package com.college.laballocation.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PART 26 (mandatory) - proves {@code audit_log} is append-only at the
 * database level, not merely because this codebase happens not to expose an
 * update/delete code path. Issues raw SQL directly (bypassing every JPA/service
 * layer entirely) to prove the V12 trigger itself rejects mutation, regardless
 * of which client attempts it.
 *
 * <p>Environment-blocked on this development machine (same documented
 * Docker/Testcontainers-on-Windows-npipe limitation as every other IT class
 * in this project, see docs/13-DEVELOPER-SETUP.md) - written correctly for
 * CI/future environments, independently confirmed via manual {@code psql}
 * verification against the live Dockerized stack (docs/11-TESTING-STRATEGY.md).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class AuditLogImmutabilityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.college.laballocation.user.UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Long seedOneAuditRow() {
        var user = userRepository.save(new com.college.laballocation.user.AppUser(
                "immutability-" + System.nanoTime() + "@example.edu", passwordEncoder.encode("irrelevant-pw1"),
                com.college.laballocation.user.UserRole.CR, "Immutability Test CR"));
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO audit_log (actor_user_id, actor_role, action, resource_type, resource_id, resource_display, metadata) "
                        + "VALUES (?, 'CR', 'EXTRA_LAB_BOOKED', 'ALLOCATION', 1, 'test row', '{}'::jsonb) RETURNING id",
                Long.class, user.getId());
        return id;
    }

    @Test
    void directUpdateIsRejectedByTheDatabaseTrigger() {
        Long id = seedOneAuditRow();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_log SET resource_display = 'tampered' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        String display = jdbcTemplate.queryForObject("SELECT resource_display FROM audit_log WHERE id = ?", String.class, id);
        assertThat(display).isEqualTo("test row");
    }

    @Test
    void directDeleteIsRejectedByTheDatabaseTrigger() {
        Long id = seedOneAuditRow();

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void insertIsUnaffectedByTheTrigger() {
        Long id = seedOneAuditRow();
        assertThat(id).isNotNull();
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }
}
