package com.college.laballocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Baseline foundation integration test (Phase 2): verifies the Spring context
 * actually starts against a real PostgreSQL instance (Testcontainers, not H2 -
 * see docs/13-DEVELOPER-SETUP.md on why the app is designed against real
 * Postgres behavior), that Flyway's V1 baseline migration applies without
 * error (the context would fail to start otherwise), and that the health
 * endpoint reports the application - including its database connection - as
 * up. Named *IT (not *Test) and run via the Failsafe plugin during
 * `mvn verify`, not `mvn test` - it requires a working Docker daemon, so it is
 * deliberately kept out of the fast default unit-test run (see pom.xml).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LabAllocationBackendApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Implicitly asserts: Spring context starts, datasource connects, Flyway
        // migration V1__baseline.sql applies successfully.
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
