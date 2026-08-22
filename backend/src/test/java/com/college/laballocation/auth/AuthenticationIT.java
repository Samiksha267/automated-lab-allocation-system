package com.college.laballocation.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end authentication tests (Phase 3, docs/11-TESTING-STRATEGY.md
 * §Security Integration Tests) against a real Testcontainers PostgreSQL - see
 * docs/13-DEVELOPER-SETUP.md Known Limitations for why this class is
 * environment-blocked on this particular development machine (Failsafe
 * `mvn verify`, not the default `mvn test`), and is expected to run
 * successfully in CI/other environments.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthenticationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private AppUser persistUser(String email, String rawPassword, UserRole role) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode(rawPassword), role, "Test " + role));
    }

    @Test
    void loginWithValidCredentialsSucceedsAndReturnsJwtPlusSafeUserSummary() {
        persistUser("it-cr@example.edu", "correct-password", UserRole.CR);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("it-cr@example.edu", "correct-password"), LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().user().role()).isEqualTo("CR");
        assertThat(response.getBody().user().email()).isEqualTo("it-cr@example.edu");
    }

    @Test
    void loginWithWrongPasswordReturnsGenericInvalidCredentials401() {
        persistUser("it-wrongpass@example.edu", "correct-password", UserRole.STUDENT);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("it-wrongpass@example.edu", "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void loginWithUnknownEmailReturnsTheSameGenericFailureAsWrongPassword() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("nobody-at-all@example.edu", "whatever1"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void deactivatedUserCannotLoginEvenWithCorrectPassword() {
        AppUser user = persistUser("it-inactive@example.edu", "correct-password", UserRole.STUDENT);
        user.deactivate();
        userRepository.save(user);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("it-inactive@example.edu", "correct-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meWithoutTokenReturns401Json() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/auth/me"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void meWithValidTokenReturnsSafeProfile() {
        persistUser("it-me@example.edu", "correct-password", UserRole.LAB_ASSISTANT);
        String token = login("it-me@example.edu", "correct-password");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<UserSummary> response = restTemplate.exchange(
                url("/api/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), UserSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("it-me@example.edu");
        assertThat(response.getBody().role()).isEqualTo("LAB_ASSISTANT");
    }

    @Test
    void meWithInvalidTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this.is.not-a-valid-jwt");
        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emailUniquenessIsEnforcedByTheDatabaseNotJustApplicationCode() {
        persistUser("it-dup@example.edu", "correct-password", UserRole.STUDENT);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(
                    new AppUser("it-dup@example.edu", passwordEncoder.encode("another-password"), UserRole.CR, "Dup"));
        });
    }

    private String login(String email, String password) {
        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity(url("/api/auth/login"), new LoginRequest(email, password), LoginResponse.class);
        return response.getBody().accessToken();
    }
}
