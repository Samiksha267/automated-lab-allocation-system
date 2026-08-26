package com.college.laballocation.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.AcademicYearRepository;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchRepository;
import com.college.laballocation.academic.CrAssignment;
import com.college.laballocation.academic.CrAssignmentRepository;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionRepository;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.ProgramRepository;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.StreamRepository;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyAvailability;
import com.college.laballocation.faculty.FacultyAvailabilityRepository;
import com.college.laballocation.faculty.FacultyRepository;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.faculty.SubjectFacultyAssignmentRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Full-stack tests for {@code GET /api/audit-logs} (PART 16/24/27/32) and the
 * same-transaction guarantee that a failed booking never leaves a misleading
 * "successful" audit row (PART 12/28). Environment-blocked on this
 * development machine (same documented Docker/Testcontainers-on-Windows
 * limitation as every other IT class, see docs/13-DEVELOPER-SETUP.md), but
 * written correctly for CI/future environments; manual Docker verification
 * (docs/11-TESTING-STRATEGY.md) covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuditLogApiIT {

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
    private DivisionRepository divisionRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private FacultyAvailabilityRepository facultyAvailabilityRepository;

    @Autowired
    private LabTypeRepository labTypeRepository;

    @Autowired
    private LabRepository labRepository;

    @Autowired
    private SubjectFacultyAssignmentRepository subjectFacultyAssignmentRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

    @Autowired
    private CrAssignmentRepository crAssignmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private AppUser seedUser(UserRole role, String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), role, "Test " + role));
    }

    private String tokenFor(AppUser user) {
        return jwtService.generateToken(user.getId(), user.getRole().name());
    }

    private HttpHeaders jsonAuth(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private record Fixture(
            Division division, Batch batch, Subject subject, Faculty faculty, AcademicTerm term, Lab lab, ScheduleVersion version) {}

    private Fixture seedFixture(String suffix) {
        Program program = programRepository.save(new Program("AUDIT-PROG-" + suffix, "Audit Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "A", 60));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "AUDIT-SUB-" + suffix, "Audit Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("AUDIT-FAC-" + suffix, "Faculty " + suffix, null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("AUDIT-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType labType = labTypeRepository.save(new LabType("AUDIT-TYPE-" + suffix, "Test Lab Type " + suffix, null));
        Lab lab = labRepository.save(new Lab("AUDIT-LAB-" + suffix, "Test Lab", 30, labType, "C", "2", "1"));
        AppUser publisher = seedUser(UserRole.LAB_ASSISTANT, "audit-publisher-" + suffix + "@example.edu");
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, publisher));
        version.publish(publisher);
        return new Fixture(division, batch, subject, faculty, term, lab, version);
    }

    private String bookingBody(Fixture fixture, Long labId, LocalTime start, LocalTime end) {
        return "{\"subjectId\":" + fixture.subject().getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + fixture.batch().getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"labId\":" + labId + "}";
    }

    /** PART 14/25 - a successful booking produces a matching, correctly-scoped audit row visible to the Lab Assistant. */
    @Test
    void successfulBookingProducesAVisibleAuditEvent() {
        Fixture fixture = seedFixture("BOOK");
        AppUser crUser = seedUser(UserRole.CR, "audit-cr-book@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        ResponseEntity<String> bookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);
        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "audit-la-book@example.edu");
        ResponseEntity<String> activity = restTemplate.exchange(
                url("/api/audit-logs?action=EXTRA_LAB_BOOKED&actorUserId=" + crUser.getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(activity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activity.getBody())
                .contains("\"action\":\"EXTRA_LAB_BOOKED\"")
                .contains("\"actorUserId\":" + crUser.getId())
                .contains(fixture.lab().getCode());
    }

    /** PART 12/22/28 - a booking rejected by a real database conflict must never leave a "successful" audit row behind. */
    @Test
    void rejectedBookingProducesNoSuccessfulAuditEvent() {
        Fixture fixture = seedFixture("REJECT");
        AppUser crUser = seedUser(UserRole.CR, "audit-cr-reject@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        // Occupy the slot first with a REGULAR allocation so the CR's booking attempt is rejected before ever reaching the audit write.
        AppUser importer = seedUser(UserRole.LAB_ASSISTANT, "audit-importer-reject@example.edu");
        Allocation blocking = Allocation.forBatch(
                AllocationType.REGULAR, fixture.division(), fixture.batch(), fixture.subject(), fixture.faculty(), fixture.lab(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.PUBLISHED, fixture.version(), importer);
        allocationRepository.saveAndFlush(blocking);

        ResponseEntity<String> bookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);
        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long auditCount = auditLogRepository.findAll().stream()
                .filter(a -> a.getActorUserId().equals(crUser.getId()) && a.getAction() == AuditAction.EXTRA_LAB_BOOKED)
                .count();
        assertThat(auditCount).isZero();
    }

    /** PART 24/27 - RBAC on the activity endpoint: LAB_ASSISTANT 200, CR/STUDENT 403, anonymous 401. */
    @Test
    void onlyLabAssistantCanReadAuditHistory() {
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "audit-rbac-la@example.edu");
        AppUser cr = seedUser(UserRole.CR, "audit-rbac-cr@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "audit-rbac-student@example.edu");

        assertThat(restTemplate.exchange(
                        url("/api/audit-logs"), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(
                        url("/api/audit-logs"), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(cr))), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(
                        url("/api/audit-logs"), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(
                        url("/api/audit-logs"), HttpMethod.GET, new HttpEntity<>(jsonAuth(null)), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** PART 17/27 - filtering by actorUserId isolates one CR's events from another's. */
    @Test
    void actorFilterIsolatesOneCrsActivityFromAnother() {
        Fixture fixture = seedFixture("FILTER");
        AppUser crA = seedUser(UserRole.CR, "audit-filter-cra@example.edu");
        AppUser crB = seedUser(UserRole.CR, "audit-filter-crb@example.edu");
        crAssignmentRepository.save(new CrAssignment(crA, fixture.division(), fixture.term(), crA));
        Division divisionB = divisionRepository.save(new Division(fixture.division().getAcademicYear(), "B", 60));
        crAssignmentRepository.save(new CrAssignment(crB, divisionB, fixture.term(), crB));

        restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(tokenFor(crA))),
                String.class);

        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "audit-filter-la@example.edu");
        ResponseEntity<String> filteredForA = restTemplate.exchange(
                url("/api/audit-logs?actorUserId=" + crA.getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);
        ResponseEntity<String> filteredForB = restTemplate.exchange(
                url("/api/audit-logs?actorUserId=" + crB.getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(filteredForA.getBody()).contains("\"actorUserId\":" + crA.getId());
        assertThat(filteredForA.getBody()).doesNotContain("\"actorUserId\":" + crB.getId());
        assertThat(filteredForB.getBody()).contains("\"totalElements\":0");
    }

    /** PART 18 - a page-size request above the cap is silently clamped, never returning unbounded history. */
    @Test
    void pageSizeIsCappedAtTheConfiguredMaximum() {
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "audit-page-la@example.edu");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/audit-logs?size=500"), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"size\":100");
    }
}
