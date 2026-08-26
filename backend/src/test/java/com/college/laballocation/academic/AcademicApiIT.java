package com.college.laballocation.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyRepository;
import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.CreateSubjectFacultyAssignmentRequest;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end Phase 4 academic API tests (real endpoints, real RBAC, real
 * database) - see docs/13-DEVELOPER-SETUP.md Known Limitations for why this
 * class is environment-blocked on this particular development machine
 * (Failsafe `mvn verify`, not the default `mvn test`).
 *
 * <p>This is what supersedes Phase 3's test-only {@code RoleAuthorizationTest}
 * fixture (removed this phase, per PART 41/48 of the brief): role
 * authorization is now proven against real, permanent controllers instead of
 * a throwaway probe.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AcademicApiIT {

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
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private DivisionRepository divisionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private CrAssignmentRepository crAssignmentRepository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String tokenFor(UserRole role, String email) {
        AppUser user = userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), role, "Test " + role));
        return jwtService.generateToken(user.getId(), role.name());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void labAssistantCanCreateAProgramCrAndStudentCannotAndUnauthenticatedIsRejected() {
        String labAssistantToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-program@example.edu");
        String crToken = tokenFor(UserRole.CR, "it-cr-program@example.edu");
        String studentToken = tokenFor(UserRole.STUDENT, "it-student-program@example.edu");

        String body = "{\"code\":\"IT-PROG-1\",\"name\":\"Test Program\",\"durationYears\":4}";

        ResponseEntity<String> labAssistantResponse = restTemplate.exchange(
                url("/api/programs"), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(labAssistantToken)), String.class);
        assertThat(labAssistantResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> crResponse = restTemplate.exchange(
                url("/api/programs"), HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"IT-PROG-2\",\"name\":\"X\",\"durationYears\":4}", jsonHeaders(crToken)), String.class);
        assertThat(crResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crResponse.getBody()).contains("FORBIDDEN");

        ResponseEntity<String> studentResponse = restTemplate.exchange(
                url("/api/programs"), HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"IT-PROG-3\",\"name\":\"X\",\"durationYears\":4}", jsonHeaders(studentToken)), String.class);
        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(url("/api/programs"), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Authenticated CR/STUDENT reads are allowed.
        ResponseEntity<String> crRead = restTemplate.exchange(
                url("/api/programs"), HttpMethod.GET, new HttpEntity<>(authHeaders(crToken)), String.class);
        assertThat(crRead.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void batchFromWrongDivisionIsRejectedWhenCreatingAFacultyAssignment() {
        Program program = programRepository.save(new Program("IT-PROG-BATCH", "Batch Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division divisionA = divisionRepository.save(new Division(year, "BA1", 60));
        Division divisionB = divisionRepository.save(new Division(year, "BB1", 60));
        Batch batchFromDivisionB = batchRepository.save(new Batch(divisionB, "B1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "BDA-IT", "Big Data Analytics"));
        Faculty faculty = facultyRepository.save(new Faculty("FAC-IT-1", "Faculty One", null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-2026-27", 5, "Semester 5", LocalDate.now(), LocalDate.now().plusMonths(4)));

        String labAssistantToken = tokenFor(UserRole.LAB_ASSISTANT, "it-la-batchcheck@example.edu");

        var request = new CreateSubjectFacultyAssignmentRequest(
                subject.getId(), faculty.getId(), divisionA.getId(), batchFromDivisionB.getId(), term.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/subject-faculty-assignments"), new HttpEntity<>(request, authHeaders(labAssistantToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_ACADEMIC_RELATIONSHIP");
    }

    @Test
    void crCanRetrieveTheirOwnCurrentAssignmentButNotWithoutOne() {
        Program program = programRepository.save(new Program("IT-PROG-CRME", "CR Me Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "CRME1", 60));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-CRME-2026-27", 5, "Semester 5", LocalDate.now(), LocalDate.now().plusMonths(4)));
        term.updateStatus(TermStatus.ACTIVE);
        academicTermRepository.save(term);

        AppUser labAssistant = userRepository.save(
                new AppUser("it-la-crme@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "LA"));
        AppUser crWithAssignment = userRepository.save(
                new AppUser("it-cr-withassignment@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.CR, "CR With"));
        AppUser crWithoutAssignment = userRepository.save(
                new AppUser("it-cr-noassignment@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.CR, "CR Without"));
        crAssignmentRepository.save(new CrAssignment(crWithAssignment, division, term, labAssistant));

        String tokenWithAssignment = jwtService.generateToken(crWithAssignment.getId(), "CR");
        String tokenWithoutAssignment = jwtService.generateToken(crWithoutAssignment.getId(), "CR");

        ResponseEntity<String> withAssignment = restTemplate.exchange(
                url("/api/cr-assignments/me"), HttpMethod.GET, new HttpEntity<>(authHeaders(tokenWithAssignment)), String.class);
        assertThat(withAssignment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withAssignment.getBody()).contains("\"divisionCode\":\"CRME1\"");

        ResponseEntity<String> withoutAssignment = restTemplate.exchange(
                url("/api/cr-assignments/me"), HttpMethod.GET, new HttpEntity<>(authHeaders(tokenWithoutAssignment)), String.class);
        assertThat(withoutAssignment.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(withoutAssignment.getBody()).contains("CR_ASSIGNMENT_NOT_FOUND");
    }

    @Test
    void databaseConstraintsAreReal() {
        // program.code UNIQUE
        programRepository.save(new Program("IT-DB-DUP", "Dup Program", 4));
        assertThat(catchDataIntegrityViolation(
                        () -> programRepository.saveAndFlush(new Program("IT-DB-DUP", "Dup Program 2", 4))))
                .isTrue();

        // faculty.employee_code UNIQUE
        facultyRepository.save(new Faculty("IT-DB-DUP-FAC", "Faculty A", null, null));
        assertThat(catchDataIntegrityViolation(
                        () -> facultyRepository.saveAndFlush(new Faculty("IT-DB-DUP-FAC", "Faculty B", null, null))))
                .isTrue();

        // stream UNIQUE(program_id, code)
        Program program = programRepository.save(new Program("IT-DB-STREAM", "Stream Program", 4));
        streamRepository.save(new Stream(program, "CS", "CS"));
        assertThat(catchDataIntegrityViolation(
                        () -> streamRepository.saveAndFlush(new Stream(program, "CS", "CS Duplicate"))))
                .isTrue();

        // batch UNIQUE(division_id, code)
        Stream stream = streamRepository.save(new Stream(program, "IT", "IT"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 1));
        Division division = divisionRepository.save(new Division(year, "DBX", 60));
        batchRepository.save(new Batch(division, "X1", 30));
        assertThat(catchDataIntegrityViolation(() -> batchRepository.saveAndFlush(new Batch(division, "X1", 30))))
                .isTrue();
    }

    private boolean catchDataIntegrityViolation(Runnable action) {
        try {
            action.run();
            return false;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return true;
        }
    }
}
