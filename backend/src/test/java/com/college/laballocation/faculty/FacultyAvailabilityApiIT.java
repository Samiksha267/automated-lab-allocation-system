package com.college.laballocation.faculty;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
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
 * End-to-end Phase 7 faculty-availability API tests - environment-blocked on
 * this development machine (same documented Docker/Testcontainers limitation
 * as every other IT class in this project, see docs/13-DEVELOPER-SETUP.md).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FacultyAvailabilityApiIT {

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
    private FacultyRepository facultyRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

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

    private Faculty seedFaculty(String employeeCode) {
        return facultyRepository.save(new Faculty(employeeCode, "IT Faculty " + employeeCode, null, null));
    }

    private AcademicTerm seedTerm(String label, int number) {
        return academicTermRepository.save(
                new AcademicTerm(label, number, "IT Term " + label + "-" + number, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void labAssistantCanAddAvailabilityCrAndStudentCannotUnauthenticatedIs401() {
        Faculty faculty = seedFaculty("IT-AVAIL-RBAC");
        AcademicTerm term = seedTerm("IT-RBAC-YR", 1);
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-avail-rbac@example.edu");
        String crToken = tokenFor(UserRole.CR, "it-cr-avail-rbac@example.edu");
        String studentToken = tokenFor(UserRole.STUDENT, "it-student-avail-rbac@example.edu");

        String body = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"11:00\"}";

        ResponseEntity<String> laResponse = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(laToken)), String.class);
        assertThat(laResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> crResponse = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(crToken)), String.class);
        assertThat(crResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentResponse = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(studentToken)), String.class);
        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Reads are also LAB_ASSISTANT-only for Phase 7 (PART 22) - deliberately
        // stricter than Phase 5/6's open-read pattern; see docs/15-DESIGN-DECISIONS.md.
        ResponseEntity<String> crRead = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.GET, new HttpEntity<>(jsonAuth(crToken)), String.class);
        assertThat(crRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> unauthenticated =
                restTemplate.getForEntity(url("/api/faculty/" + faculty.getId() + "/availability"), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> laRead = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.GET, new HttpEntity<>(jsonAuth(laToken)), String.class);
        assertThat(laRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(laRead.getBody()).contains("MONDAY");
    }

    @Test
    void overlappingAvailabilityIsRejectedCleanly() {
        Faculty faculty = seedFaculty("IT-AVAIL-OVERLAP");
        AcademicTerm term = seedTerm("IT-OVERLAP-YR", 1);
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-avail-overlap@example.edu");

        String first = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}";
        restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(first, jsonAuth(laToken)), String.class);

        String overlapping = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"11:00\",\"endTime\":\"14:00\"}";
        ResponseEntity<String> overlapResponse = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(overlapping, jsonAuth(laToken)), String.class);
        assertThat(overlapResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlapResponse.getBody()).contains("FACULTY_AVAILABILITY_OVERLAP");
    }

    @Test
    void adjacentAvailabilityIsAllowed() {
        Faculty faculty = seedFaculty("IT-AVAIL-ADJ");
        AcademicTerm term = seedTerm("IT-ADJ-YR", 1);
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-avail-adj@example.edu");

        String first = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}";
        restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(first, jsonAuth(laToken)), String.class);

        String adjacent = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"12:00\",\"endTime\":\"15:00\"}";
        ResponseEntity<String> adjacentResponse = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(adjacent, jsonAuth(laToken)), String.class);
        assertThat(adjacentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidIntervalIsRejected() {
        Faculty faculty = seedFaculty("IT-AVAIL-INVALID");
        AcademicTerm term = seedTerm("IT-INVALID-YR", 1);
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-avail-invalid@example.edu");

        String backwards = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"12:00\",\"endTime\":\"09:00\"}";
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(backwards, jsonAuth(laToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_AVAILABILITY_INTERVAL");
    }

    @Test
    void checkEndpointReflectsSeededAvailability() {
        Faculty faculty = seedFaculty("IT-AVAIL-CHECK");
        AcademicTerm term = seedTerm("IT-CHECK-YR", 1);
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-avail-check@example.edu");

        String window = "{\"academicTermId\":" + term.getId()
                + ",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}";
        restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability"), HttpMethod.POST,
                new HttpEntity<>(window, jsonAuth(laToken)), String.class);

        ResponseEntity<String> available = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability/check?academicTermId=" + term.getId()
                        + "&dayOfWeek=MONDAY&startTime=09:00&endTime=11:00"),
                HttpMethod.GET, new HttpEntity<>(jsonAuth(laToken)), String.class);
        assertThat(available.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(available.getBody()).contains("\"available\":true");

        ResponseEntity<String> unavailable = restTemplate.exchange(
                url("/api/faculty/" + faculty.getId() + "/availability/check?academicTermId=" + term.getId()
                        + "&dayOfWeek=MONDAY&startTime=13:00&endTime=14:00"),
                HttpMethod.GET, new HttpEntity<>(jsonAuth(laToken)), String.class);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unavailable.getBody()).contains("\"available\":false");
    }
}
