package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.AcademicYearRepository;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.BatchRepository;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.DivisionRepository;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.ProgramRepository;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.StreamRepository;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.audit.AuditLogRepository;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.security.JwtService;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
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
 * Full-stack tests for the Phase 18 timetable-version lifecycle
 * ({@code /api/schedule-versions}) and student/CR timetable visibility
 * ({@code /api/timetable}) - draft creation, publication with automatic
 * superseding, historical preservation, RBAC, and the "highest version
 * number is not necessarily published" invariant (PART 21/47, a real,
 * explicitly-tested class of bug). Environment-blocked on this development
 * machine (same documented Docker/Testcontainers-on-Windows limitation as
 * every other IT class, see docs/13-DEVELOPER-SETUP.md), written correctly
 * for CI/future environments; manual Docker verification
 * (docs/11-TESTING-STRATEGY.md) covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ScheduleVersionApiIT {

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
    private LabTypeRepository labTypeRepository;

    @Autowired
    private LabRepository labRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

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

    private record Fixture(AcademicTerm term, Division division, Batch batch, Subject subject, Faculty faculty, Lab lab) {}

    private Fixture seedFixture(String suffix) {
        Program program = programRepository.save(new Program("VER-PROG-" + suffix, "Version Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "A", 60));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "VER-SUB-" + suffix, "Version Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("VER-FAC-" + suffix, "Faculty " + suffix, null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("VER-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        LabType labType = labTypeRepository.save(new LabType("VER-TYPE-" + suffix, "Test Lab Type " + suffix, null));
        Lab lab = labRepository.save(new Lab("VER-LAB-" + suffix, "Test Lab", 30, labType, "C", "2", "1"));
        return new Fixture(term, division, batch, subject, faculty, lab);
    }

    private Allocation seedAllocation(Fixture fixture, ScheduleVersion version, AppUser createdBy, LocalTime start, LocalTime end) {
        Allocation allocation = Allocation.forBatch(
                AllocationType.REGULAR, fixture.division(), fixture.batch(), fixture.subject(), fixture.faculty(), fixture.lab(),
                MONDAY, start, end, AllocationStatus.PUBLISHED, version, createdBy);
        return allocationRepository.saveAndFlush(allocation);
    }

    /** PART 41 - draft creation: version number, status, term, createdBy correct; publishedBy/publishedAt null. */
    @Test
    void labAssistantCreatesFirstDraftWithNoReasonRequired() {
        Fixture fixture = seedFixture("CREATE");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-create@example.edu");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"versionNumber\":1")
                .contains("\"status\":\"DRAFT\"")
                .contains("\"publishedByUserId\":null")
                .contains("\"publishedAt\":null")
                .contains("\"createdByUserId\":" + labAssistant.getId());
    }

    /** PART 41 - CR/STUDENT forbidden, anonymous unauthenticated. */
    @Test
    void draftCreationIsForbiddenToCrAndStudentAndRejectedForAnonymous() {
        Fixture fixture = seedFixture("RBAC");
        AppUser cr = seedUser(UserRole.CR, "ver-rbac-cr@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "ver-rbac-student@example.edu");
        String body = "{\"academicTermId\":" + fixture.term().getId() + "}";

        assertThat(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(tokenFor(cr))), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(tokenFor(student))), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST, new HttpEntity<>(body, jsonAuth(null)), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** PART 42 - version numbers are term-scoped: Term A gets V1/V2, Term B independently gets V1. */
    @Test
    void versionNumbersAreScopedIndependentlyPerTerm() {
        Fixture termA = seedFixture("SCOPEA");
        Fixture termB = seedFixture("SCOPEB");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-scope@example.edu");
        String token = tokenFor(labAssistant);

        restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + termA.term().getId() + "}", jsonAuth(token)), String.class);
        ResponseEntity<String> aV2 = restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + termA.term().getId() + ",\"reason\":\"revision\"}", jsonAuth(token)), String.class);
        ResponseEntity<String> bV1 = restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + termB.term().getId() + "}", jsonAuth(token)), String.class);

        assertThat(aV2.getBody()).contains("\"versionNumber\":2");
        assertThat(bV1.getBody()).contains("\"versionNumber\":1");
    }

    /** PART 43/52 - publishing the first version for a term: status, publishedBy/publishedAt, and an audit event. */
    @Test
    void publishingTheFirstVersionSetsPublishedFieldsAndWritesAnAuditEvent() {
        Fixture fixture = seedFixture("FIRSTPUB");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-firstpub@example.edu");
        String token = tokenFor(labAssistant);
        ResponseEntity<String> created = restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(token)), String.class);
        Long versionId = extractId(created.getBody());

        ResponseEntity<String> published = restTemplate.exchange(
                url("/api/schedule-versions/" + versionId + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(token)), String.class);

        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody())
                .contains("\"status\":\"PUBLISHED\"")
                .contains("\"publishedByUserId\":" + labAssistant.getId());
        assertThat(auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getResourceId().equals(versionId) && a.getAction().name().equals("SCHEDULE_PUBLISHED")))
                .isTrue();
    }

    /** PART 44/45/46/47/64 - the central end-to-end scenario: publish V1, create+publish V2, verify V1 -> SUPERSEDED with its allocations preserved, and that the student timetable always reflects the current PUBLISHED version, never the highest version number. */
    @Test
    void publishingASecondVersionSupersedesTheFirstAndStudentSeesOnlyTheCurrentPublishedVersion() {
        Fixture fixture = seedFixture("SUPERSEDE");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-supersede@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "ver-student-supersede@example.edu");
        String laToken = tokenFor(labAssistant);

        ResponseEntity<String> v1 = restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(laToken)), String.class);
        Long v1Id = extractId(v1.getBody());
        restTemplate.exchange(url("/api/schedule-versions/" + v1Id + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(laToken)), String.class);
        ScheduleVersion v1Entity = scheduleVersionRepository.findById(v1Id).orElseThrow();
        Allocation v1Allocation = seedAllocation(fixture, v1Entity, labAssistant, LocalTime.of(9, 0), LocalTime.of(10, 0));

        // Student sees V1's allocation while V1 is current.
        ResponseEntity<String> beforeRepublish = restTemplate.exchange(
                url("/api/timetable?academicTermId=" + fixture.term().getId()), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class);
        assertThat(beforeRepublish.getBody()).contains("\"allocationId\":" + v1Allocation.getId());

        // Create + publish V2 (deliberately the HIGHER version number) with a different allocation.
        ResponseEntity<String> v2 = restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + ",\"reason\":\"revision\"}", jsonAuth(laToken)), String.class);
        Long v2Id = extractId(v2.getBody());
        // While V2 is still DRAFT, student must still see V1 (not the higher-numbered draft).
        ResponseEntity<String> whileV2IsDraft = restTemplate.exchange(
                url("/api/timetable?academicTermId=" + fixture.term().getId()), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class);
        assertThat(whileV2IsDraft.getBody()).contains("\"allocationId\":" + v1Allocation.getId());

        ScheduleVersion v2Entity = scheduleVersionRepository.findById(v2Id).orElseThrow();
        Allocation v2Allocation = seedAllocation(fixture, v2Entity, labAssistant, LocalTime.of(11, 0), LocalTime.of(12, 0));
        restTemplate.exchange(url("/api/schedule-versions/" + v2Id + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(laToken)), String.class);

        // V1 is now SUPERSEDED, V2 is PUBLISHED - both rows and both versions still exist (historical preservation, PART 24/45).
        ScheduleVersion v1AfterSupersede = scheduleVersionRepository.findById(v1Id).orElseThrow();
        assertThat(v1AfterSupersede.getStatus()).isEqualTo(ScheduleVersionStatus.SUPERSEDED);
        assertThat(allocationRepository.findById(v1Allocation.getId())).isPresent();
        assertThat(allocationRepository.findById(v2Allocation.getId())).isPresent();

        // Student now sees ONLY V2's allocation - never V1 (superseded) or any draft.
        ResponseEntity<String> afterRepublish = restTemplate.exchange(
                url("/api/timetable?academicTermId=" + fixture.term().getId()), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class);
        assertThat(afterRepublish.getBody())
                .contains("\"allocationId\":" + v2Allocation.getId())
                .doesNotContain("\"allocationId\":" + v1Allocation.getId());

        // Lab Assistant history shows both versions, correctly labeled.
        ResponseEntity<String> history = restTemplate.exchange(
                url("/api/schedule-versions?academicTermId=" + fixture.term().getId()), HttpMethod.GET, new HttpEntity<>(jsonAuth(laToken)), String.class);
        assertThat(history.getBody()).contains("\"status\":\"SUPERSEDED\"").contains("\"status\":\"PUBLISHED\"");
    }

    /** PART 48 - publishing a SUPERSEDED version is rejected with a clean 409, never a 500. */
    @Test
    void publishingASupersededVersionIsRejectedWithConflict() {
        Fixture fixture = seedFixture("REJECTSUP");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-rejectsup@example.edu");
        String token = tokenFor(labAssistant);
        Long v1Id = extractId(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(token)), String.class).getBody());
        restTemplate.exchange(url("/api/schedule-versions/" + v1Id + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(token)), String.class);
        Long v2Id = extractId(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + ",\"reason\":\"r\"}", jsonAuth(token)), String.class).getBody());
        restTemplate.exchange(url("/api/schedule-versions/" + v2Id + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(token)), String.class);
        // v1 is now SUPERSEDED.

        ResponseEntity<String> reattempt = restTemplate.exchange(
                url("/api/schedule-versions/" + v1Id + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(token)), String.class);

        assertThat(reattempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reattempt.getBody()).contains("INVALID_SCHEDULE_VERSION_TRANSITION");
    }

    /** PART 66 - a term with no published version yet returns an empty timetable, never a DRAFT's rows. */
    @Test
    void studentSeesEmptyTimetableWhenTermHasNoPublishedVersionYet() {
        Fixture fixture = seedFixture("NOPUB");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-nopub@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "ver-student-nopub@example.edu");
        Long draftId = extractId(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(tokenFor(labAssistant))), String.class).getBody());
        ScheduleVersion draft = scheduleVersionRepository.findById(draftId).orElseThrow();
        seedAllocation(fixture, draft, labAssistant, LocalTime.of(9, 0), LocalTime.of(10, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/timetable?academicTermId=" + fixture.term().getId()), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalElements\":0");
    }

    /** Phase 22 PART 9/27 - a batch-scoped timetable request must include both that batch's own rows and the division-wide rows every batch attends; a strict batch-id equality would silently hide division-wide practicals. */
    @Test
    void batchScopedTimetableIncludesDivisionWideAllocationsToo() {
        Fixture fixture = seedFixture("BATCHVIS");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "ver-la-batchvis@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "ver-student-batchvis@example.edu");
        String token = tokenFor(labAssistant);
        Long versionId = extractId(restTemplate.exchange(url("/api/schedule-versions"), HttpMethod.POST,
                new HttpEntity<>("{\"academicTermId\":" + fixture.term().getId() + "}", jsonAuth(token)), String.class).getBody());
        ScheduleVersion version = scheduleVersionRepository.findById(versionId).orElseThrow();
        Allocation batchAllocation = seedAllocation(fixture, version, labAssistant, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Allocation divisionWideAllocation = allocationRepository.saveAndFlush(Allocation.forDivision(
                AllocationType.REGULAR, fixture.division(), fixture.subject(), fixture.faculty(), fixture.lab(),
                MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0), AllocationStatus.PUBLISHED, version, labAssistant));
        restTemplate.exchange(url("/api/schedule-versions/" + versionId + "/publish"), HttpMethod.POST, new HttpEntity<>(jsonAuth(token)), String.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/timetable?academicTermId=" + fixture.term().getId() + "&divisionId=" + fixture.division().getId()
                        + "&batchId=" + fixture.batch().getId()),
                HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class);

        assertThat(response.getBody())
                .contains("\"allocationId\":" + batchAllocation.getId())
                .contains("\"allocationId\":" + divisionWideAllocation.getId());
    }

    /** PART 27/33 - version management RBAC on the history/detail endpoints: LAB_ASSISTANT only. */
    @Test
    void onlyLabAssistantCanViewVersionHistory() {
        Fixture fixture = seedFixture("HISTRBAC");
        AppUser cr = seedUser(UserRole.CR, "ver-histrbac-cr@example.edu");

        assertThat(restTemplate.exchange(url("/api/schedule-versions?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                        new HttpEntity<>(jsonAuth(tokenFor(cr))), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url("/api/schedule-versions?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                        new HttpEntity<>(jsonAuth(null)), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static Long extractId(String responseBody) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(responseBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No id field in response: " + responseBody);
        }
        return Long.valueOf(matcher.group(1));
    }
}
