package com.college.laballocation.subject;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.AcademicYearRepository;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.ProgramRepository;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.StreamRepository;
import com.college.laballocation.lab.Equipment;
import com.college.laballocation.lab.EquipmentRepository;
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareRepository;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
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
 * End-to-end Phase 6 subject-requirement API tests - environment-blocked on
 * this development machine (same documented Docker/Testcontainers limitation
 * as every other IT class in this project, see docs/13-DEVELOPER-SETUP.md).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SubjectRequirementApiIT {

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
    private ProgramRepository programRepository;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SoftwareRepository softwareRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

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

    private Subject seedSubject(String code) {
        Program program = programRepository.save(new Program("IT-REQ-PROG-" + code, "Requirement Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        return subjectRepository.save(new Subject(year, code, "Requirement Test Subject " + code));
    }

    @Test
    void labAssistantCanAddRequirementCrAndStudentCannotUnauthenticatedIs401() {
        Subject subject = seedSubject("IT-RBAC");
        Software software = softwareRepository.save(new Software("IT-RBAC-SOFT", "RBAC Software"));

        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-req1@example.edu");
        String crToken = tokenFor(UserRole.CR, "it-cr-req1@example.edu");
        String studentToken = tokenFor(UserRole.STUDENT, "it-student-req1@example.edu");

        String body = "{\"softwareId\":" + software.getId() + "}";

        ResponseEntity<String> laResponse = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/software-requirements"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(laToken)), String.class);
        assertThat(laResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Software software2 = softwareRepository.save(new Software("IT-RBAC-SOFT2", "RBAC Software 2"));
        ResponseEntity<String> crResponse = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/software-requirements"), HttpMethod.POST,
                new HttpEntity<>("{\"softwareId\":" + software2.getId() + "}", jsonAuth(crToken)), String.class);
        assertThat(crResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentResponse = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/software-requirements"), HttpMethod.POST,
                new HttpEntity<>("{\"softwareId\":" + software2.getId() + "}", jsonAuth(studentToken)), String.class);
        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> unauthenticatedRead =
                restTemplate.getForEntity(url("/api/subjects/" + subject.getId() + "/requirements"), String.class);
        assertThat(unauthenticatedRead.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> authenticatedRead = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/requirements"), HttpMethod.GET, new HttpEntity<>(jsonAuth(crToken)), String.class);
        assertThat(authenticatedRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authenticatedRead.getBody()).contains("IT-RBAC-SOFT");
    }

    @Test
    void duplicateSoftwareRequirementIsRejectedCleanly() {
        Subject subject = seedSubject("IT-DUP");
        Software software = softwareRepository.save(new Software("IT-DUP-SOFT", "Dup Software"));
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-dup@example.edu");
        String body = "{\"softwareId\":" + software.getId() + "}";

        restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/software-requirements"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(laToken)), String.class);
        ResponseEntity<String> second = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/software-requirements"), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuth(laToken)), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("SOFTWARE_REQUIREMENT_ALREADY_EXISTS");
    }

    @Test
    void equipmentRequirementQuantityValidation() {
        Subject subject = seedSubject("IT-QTY");
        Equipment equipment = equipmentRepository.save(new Equipment("IT-QTY-EQUIP", "Qty Equipment", null));
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-qty@example.edu");

        ResponseEntity<String> zeroQuantity = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/equipment-requirements"), HttpMethod.POST,
                new HttpEntity<>("{\"equipmentId\":" + equipment.getId() + ",\"requiredQuantity\":0}", jsonAuth(laToken)), String.class);
        assertThat(zeroQuantity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> validQuantity = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/equipment-requirements"), HttpMethod.POST,
                new HttpEntity<>("{\"equipmentId\":" + equipment.getId() + ",\"requiredQuantity\":5}", jsonAuth(laToken)), String.class);
        assertThat(validQuantity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validQuantity.getBody()).contains("\"requiredQuantity\":5");
    }

    @Test
    void subjectWithNoRequirementsReturnsEmptyListsAndNullLabTypes() {
        Subject subject = seedSubject("IT-EMPTY");
        String laToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-empty@example.edu");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/subjects/" + subject.getId() + "/requirements"), HttpMethod.GET, new HttpEntity<>(jsonAuth(laToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"software\":[]").contains("\"equipment\":[]")
                .contains("\"requiredLabType\":null").contains("\"preferredLabType\":null");
    }
}
