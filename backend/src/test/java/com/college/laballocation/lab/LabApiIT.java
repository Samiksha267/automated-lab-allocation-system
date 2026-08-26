package com.college.laballocation.lab;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.security.JwtService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end Phase 5 laboratory API tests - see docs/13-DEVELOPER-SETUP.md
 * Known Limitations for why this class is environment-blocked on this
 * particular development machine (Failsafe {@code mvn verify}), same as
 * every other Testcontainers-backed IT class in this project.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LabApiIT {

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

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LabTypeRepository labTypeRepository;

    @Autowired
    private LabRepository labRepository;

    @Autowired
    private SoftwareRepository softwareRepository;

    @Autowired
    private LabSoftwareRepository labSoftwareRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String tokenFor(UserRole role, String email) {
        AppUser user = userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), role, "Test " + role));
        return jwtService.generateToken(user.getId(), role.name());
    }

    private HttpHeaders jsonAuth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private LabType seedLabType(String code) {
        return labTypeRepository.save(new LabType(code, code, null));
    }

    @Test
    void labAssistantCanCreateLabCrAndStudentCannotUnauthenticatedIs401() {
        LabType type = seedLabType("IT-COMPUTER-1");
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-lab1@example.edu");
        String crToken = tokenFor(UserRole.CR, "it-cr-lab1@example.edu");
        String studentToken = tokenFor(UserRole.STUDENT, "it-student-lab1@example.edu");

        String body = String.format(
                "{\"code\":\"IT-X-101\",\"name\":\"Test Lab\",\"capacity\":50,\"labTypeId\":%d,\"wing\":\"X\",\"floor\":\"1\",\"roomNumber\":\"101\"}",
                type.getId());

        ResponseEntity<String> laResponse =
                restTemplate.exchange(url("/api/labs"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(laToken)), String.class);
        assertThat(laResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> crResponse = restTemplate.exchange(
                url("/api/labs"), HttpMethod.POST,
                new HttpEntity<>(body.replace("IT-X-101", "IT-X-102"), jsonAuth(crToken)), String.class);
        assertThat(crResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentResponse = restTemplate.exchange(
                url("/api/labs"), HttpMethod.POST,
                new HttpEntity<>(body.replace("IT-X-101", "IT-X-103"), jsonAuth(studentToken)), String.class);
        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(url("/api/labs"), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidCapacityIsRejected() {
        LabType type = seedLabType("IT-COMPUTER-2");
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-lab2@example.edu");

        String body = String.format(
                "{\"code\":\"IT-Y-101\",\"name\":\"Bad Capacity Lab\",\"capacity\":0,\"labTypeId\":%d,\"wing\":\"Y\",\"floor\":\"1\",\"roomNumber\":\"101\"}",
                type.getId());

        ResponseEntity<String> response =
                restTemplate.exchange(url("/api/labs"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(laToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void duplicateLabCodeIsRejectedCleanlyNotAsRawSqlError() {
        LabType type = seedLabType("IT-COMPUTER-3");
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-lab3@example.edu");

        String body = String.format(
                "{\"code\":\"IT-Z-101\",\"name\":\"Dup Lab\",\"capacity\":40,\"labTypeId\":%d,\"wing\":\"Z\",\"floor\":\"1\",\"roomNumber\":\"101\"}",
                type.getId());

        restTemplate.exchange(url("/api/labs"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(laToken)), String.class);
        ResponseEntity<String> second =
                restTemplate.exchange(url("/api/labs"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(laToken)), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody()).contains("VALIDATION_ERROR").doesNotContain("SQLState").doesNotContain("ConstraintViolationException");
    }

    @Test
    void staticCapabilityFilteringCloudeaCapacityAndCombined() {
        LabType type = seedLabType("IT-CAPFILTER");
        Software cloudera = softwareRepository.save(new Software("IT-CLOUDERA", "Cloudera"));
        Software spark = softwareRepository.save(new Software("IT-SPARK", "Spark"));

        Lab bigWithCloudera = labRepository.save(new Lab("IT-CAP-1", "Big Cloudera Lab", 72, type, "F", "1", "1"));
        Lab bigWithoutCloudera = labRepository.save(new Lab("IT-CAP-2", "Big Non-Cloudera Lab", 72, type, "F", "1", "2"));
        Lab smallWithCloudera = labRepository.save(new Lab("IT-CAP-3", "Small Cloudera Lab", 40, type, "F", "1", "3"));

        saveLabSoftware(bigWithCloudera, cloudera);
        saveLabSoftware(bigWithCloudera, spark);
        saveLabSoftware(smallWithCloudera, cloudera);

        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-capfilter@example.edu");

        // Cloudera filter excludes non-Cloudera labs.
        List<Map> cloudLabs = getList(url("/api/labs?software=IT-CLOUDERA"), laToken);
        assertThat(codesOf(cloudLabs)).contains("IT-CAP-1", "IT-CAP-3").doesNotContain("IT-CAP-2");

        // Capacity filter excludes the smaller lab.
        List<Map> capLabs = getList(url("/api/labs?minCapacity=68"), laToken);
        assertThat(codesOf(capLabs)).contains("IT-CAP-1", "IT-CAP-2").doesNotContain("IT-CAP-3");

        // Combined: capacity >= 68 AND Cloudera -> only the one lab satisfying both.
        List<Map> combined = getList(url("/api/labs?minCapacity=68&software=IT-CLOUDERA"), laToken);
        assertThat(codesOf(combined)).containsExactly("IT-CAP-1");

        // ALL semantics: requiring Cloudera+Spark together excludes the Cloudera-only lab.
        List<Map> allSemantics = getList(url("/api/labs?software=IT-CLOUDERA&software=IT-SPARK"), laToken);
        assertThat(codesOf(allSemantics)).containsExactly("IT-CAP-1");
    }

    @Test
    void unavailabilityWithEndBeforeStartIsRejected() {
        LabType type = seedLabType("IT-UNAVAIL-TYPE");
        Lab lab = labRepository.save(new Lab("IT-UNAVAIL-1", "Unavailability Test Lab", 40, type, "G", "1", "1"));
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-unavail@example.edu");

        Instant start = Instant.now();
        Instant end = start.minus(2, ChronoUnit.HOURS);
        String body = String.format("{\"startDateTime\":\"%s\",\"endDateTime\":\"%s\",\"reason\":\"Maintenance\"}", start, end);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/labs/" + lab.getId() + "/unavailability"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(laToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_UNAVAILABILITY_INTERVAL");
    }

    @SuppressWarnings("unchecked")
    private List<Map> getList(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<String> codesOf(List<Map> labs) {
        return labs.stream().map(m -> (String) m.get("code")).toList();
    }

    private void saveLabSoftware(Lab lab, Software software) {
        labSoftwareRepository.save(new LabSoftware(lab, software, null));
    }
}
