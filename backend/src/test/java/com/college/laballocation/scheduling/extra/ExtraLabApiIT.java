package com.college.laballocation.scheduling.extra;

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
 * Full-stack, real-HTTP tests for the Phase 15 CR extra-lab workflow -
 * environment-blocked on this development machine (same documented
 * Docker/Testcontainers limitation as every other IT class in this project,
 * see docs/13-DEVELOPER-SETUP.md), but written correctly for CI/future
 * environments. Manual Docker verification (docs/11-TESTING-STRATEGY.md)
 * covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ExtraLabApiIT {

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

    /** One division/batch/subject/faculty/lab/term/published-version scenario, suffix-namespaced to avoid cross-test collisions. */
    private Fixture seedFixture(String suffix, int labCapacity) {
        Program program = programRepository.save(new Program("EXTRA-PROG-" + suffix, "Extra Lab Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "A", 60));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "EXTRA-SUB-" + suffix, "Extra Lab Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("EXTRA-FAC-" + suffix, "Faculty " + suffix, null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("EXTRA-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType labType = labTypeRepository.save(new LabType("EXTRA-TYPE-" + suffix, "Test Lab Type " + suffix, null));
        Lab lab = labRepository.save(new Lab("EXTRA-LAB-" + suffix, "Test Lab", labCapacity, labType, "C", "2", "1"));
        AppUser publisher = seedUser(UserRole.LAB_ASSISTANT, "extra-publisher-" + suffix + "@example.edu");
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, publisher));
        version.publish(publisher);
        return new Fixture(division, batch, subject, faculty, term, lab, version);
    }

    private String bookingBody(Fixture fixture, Long labId, LocalTime start, LocalTime end) {
        return "{\"subjectId\":" + fixture.subject().getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + fixture.batch().getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"labId\":" + labId + "}";
    }

    private String searchBody(Fixture fixture, LocalTime start, LocalTime end) {
        return "{\"subjectId\":" + fixture.subject().getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + fixture.batch().getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    /** PART 80 - CR search returns a recommendation, resolving division/faculty entirely server-side. */
    @Test
    void crSearchReturnsRecommendationWithDivisionAndFacultyResolvedServerSide() {
        Fixture fixture = seedFixture("SEARCH", 30);
        AppUser crUser = seedUser(UserRole.CR, "extra-cr-search@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/allocations/extra/search"), HttpMethod.POST,
                new HttpEntity<>(searchBody(fixture, LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("RECOMMENDED").contains(fixture.lab().getCode());
    }

    /** PART 81/82 - a valid booking persists a correctly-shaped EXTRA row; re-booking the same slot for another target then conflicts. */
    @Test
    void crBookPersistsExtraAllocationAndSecondConflictingBookAttemptFails() {
        Fixture fixture = seedFixture("BOOK", 30);
        Batch secondBatch = batchRepository.save(new Batch(fixture.division(), "A2", 30));
        Subject secondSubject = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "EXTRA-SUB-BOOK2", "Second Subject"));
        Faculty secondFaculty = facultyRepository.save(new Faculty("EXTRA-FAC-BOOK2", "Faculty BOOK2", null, null));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(secondSubject, secondFaculty, fixture.division(), secondBatch, fixture.term()));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(secondFaculty, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));

        AppUser crUser = seedUser(UserRole.CR, "extra-cr-book@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        ResponseEntity<String> bookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);
        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookResponse.getBody()).contains("\"allocationType\":\"EXTRA\"").contains("\"status\":\"PUBLISHED\"");

        Allocation persisted = allocationRepository.findAll().stream()
                .filter(a -> a.getLab().getId().equals(fixture.lab().getId()) && a.getAllocationType() == AllocationType.EXTRA)
                .findFirst()
                .orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AllocationStatus.PUBLISHED);
        assertThat(persisted.getTargetType()).isEqualTo(com.college.laballocation.scheduling.TargetType.BATCH);
        assertThat(persisted.getDivision().getId()).isEqualTo(fixture.division().getId());
        assertThat(persisted.getBatch().getId()).isEqualTo(fixture.batch().getId());
        assertThat(persisted.getCreatedBy().getId()).isEqualTo(crUser.getId());
        assertThat(persisted.getScheduleVersion().getId()).isEqualTo(fixture.version().getId());

        // Same lab, same time, different (but otherwise valid) target -> LAB_CONFLICT.
        String secondBody = "{\"subjectId\":" + secondSubject.getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + secondBatch.getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"09:00\",\"endTime\":\"11:00\",\"labId\":" + fixture.lab().getId() + "}";
        ResponseEntity<String> conflictResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST, new HttpEntity<>(secondBody, jsonAuth(crToken)), String.class);
        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResponse.getBody()).contains("ALLOCATION_CONFLICT").contains("LAB_CONFLICT");
    }

    /** PART 57 - a lab valid at search time can become invalid before booking; the server must reject the stale choice, never trust the earlier search. */
    @Test
    void staleSearchResultIsRejectedAtBookTime() {
        Fixture fixture = seedFixture("STALE", 30);
        AppUser crUser = seedUser(UserRole.CR, "extra-cr-stale@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        ResponseEntity<String> searchResponse = restTemplate.exchange(
                url("/api/allocations/extra/search"), HttpMethod.POST,
                new HttpEntity<>(searchBody(fixture, LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)), String.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).contains("RECOMMENDED");

        // Something else takes the lab between search and book (a REGULAR row, simulating an official-timetable clash).
        AppUser importer = seedUser(UserRole.LAB_ASSISTANT, "extra-importer-stale@example.edu");
        Allocation intervening = Allocation.forBatch(
                AllocationType.REGULAR, fixture.division(), fixture.batch(), fixture.subject(), fixture.faculty(), fixture.lab(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.PUBLISHED, fixture.version(), importer);
        allocationRepository.saveAndFlush(intervening);

        ResponseEntity<String> bookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);

        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bookResponse.getBody()).contains("ALLOCATION_CONFLICT");

        allocationRepository.delete(intervening);
    }

    /** PART 85 - a CR cannot book against another division's batch, even by directly supplying that batch's real id. */
    @Test
    void crCannotBookAnotherDivisionsBatchOwnershipAttack() {
        Fixture ownFixture = seedFixture("ATTACKOWN", 30);
        Fixture otherFixture = seedFixture("ATTACKOTHER", 30);
        AppUser crUser = seedUser(UserRole.CR, "extra-cr-attack@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, ownFixture.division(), ownFixture.term(), crUser));
        String crToken = tokenFor(crUser);

        // CR (assigned to ownFixture's division) submits otherFixture's subject+batch - a foreign division's data.
        String attackBody = "{\"subjectId\":" + otherFixture.subject().getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + otherFixture.batch().getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"09:00\",\"endTime\":\"11:00\",\"labId\":" + otherFixture.lab().getId() + "}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST, new HttpEntity<>(attackBody, jsonAuth(crToken)), String.class);

        // Rejected either at faculty-resolution (404, no assignment exists for otherFixture's subject in ownFixture's division)
        // or at HC-12 academic-relationship revalidation (409) - never a 200.
        assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
        assertThat(allocationRepository.findAll().stream()
                        .noneMatch(a -> a.getLab().getId().equals(otherFixture.lab().getId())
                                && a.getSubject().getId().equals(otherFixture.subject().getId())))
                .isTrue();
    }

    /** PART 86/67/68 - student cannot search/book/cancel through the CR API; unauthenticated requests get 401. */
    @Test
    void studentIsForbiddenAndUnauthenticatedIsRejected() {
        Fixture fixture = seedFixture("RBAC", 30);
        AppUser student = seedUser(UserRole.STUDENT, "extra-student-rbac@example.edu");
        String studentToken = tokenFor(student);

        ResponseEntity<String> studentSearch = restTemplate.exchange(
                url("/api/allocations/extra/search"), HttpMethod.POST,
                new HttpEntity<>(searchBody(fixture, LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(studentToken)), String.class);
        assertThat(studentSearch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentBook = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(studentToken)),
                String.class);
        assertThat(studentBook.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> studentCancel = restTemplate.exchange(
                url("/api/allocations/extra/1/cancel"), HttpMethod.POST, new HttpEntity<>(jsonAuth(studentToken)), String.class);
        assertThat(studentCancel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> unauthenticated = restTemplate.exchange(
                url("/api/allocations/extra/search"), HttpMethod.POST,
                new HttpEntity<>(searchBody(fixture, LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(null)), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** PART 87 - cancelling a real EXTRA booking sets the correct audit fields and immediately frees the slot for a later request. */
    @Test
    void cancelSetsAuditFieldsAndFreedSlotNoLongerBlocksScheduling() {
        Fixture fixture = seedFixture("CANCEL", 30);
        AppUser crUser = seedUser(UserRole.CR, "extra-cr-cancel@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        ResponseEntity<String> bookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);
        assertThat(bookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Allocation booked = allocationRepository.findAll().stream()
                .filter(a -> a.getLab().getId().equals(fixture.lab().getId()))
                .findFirst()
                .orElseThrow();

        ResponseEntity<String> cancelResponse = restTemplate.exchange(
                url("/api/allocations/extra/" + booked.getId() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"Faculty unavailable\"}", jsonAuth(crToken)), String.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody()).contains("\"status\":\"CANCELLED\"").contains("Faculty unavailable");

        Allocation cancelled = allocationRepository.findById(booked.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(AllocationStatus.CANCELLED);
        assertThat(cancelled.getCancelledBy().getId()).isEqualTo(crUser.getId());
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancellationReason()).isEqualTo("Faculty unavailable");

        // The exact same slot must now be bookable again for another target - the cancelled row no longer blocks HC-01.
        Batch secondBatch = batchRepository.save(new Batch(fixture.division(), "A2", 30));
        Subject secondSubject = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "EXTRA-SUB-CANCEL2", "Second Subject"));
        Faculty secondFaculty = facultyRepository.save(new Faculty("EXTRA-FAC-CANCEL2", "Faculty CANCEL2", null, null));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(secondSubject, secondFaculty, fixture.division(), secondBatch, fixture.term()));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(secondFaculty, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        String reBookBody = "{\"subjectId\":" + secondSubject.getId() + ",\"targetType\":\"BATCH\",\"batchId\":" + secondBatch.getId()
                + ",\"allocationDate\":\"" + MONDAY + "\",\"startTime\":\"09:00\",\"endTime\":\"11:00\",\"labId\":" + fixture.lab().getId() + "}";
        ResponseEntity<String> reBookResponse = restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST, new HttpEntity<>(reBookBody, jsonAuth(crToken)), String.class);
        assertThat(reBookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Cross-CR cancel: a different CR (different division) cannot cancel this allocation.
        Fixture otherFixture = seedFixture("CANCELOTHER", 30);
        AppUser otherCr = seedUser(UserRole.CR, "extra-cr-cancel-other@example.edu");
        crAssignmentRepository.save(new CrAssignment(otherCr, otherFixture.division(), otherFixture.term(), otherCr));
        ResponseEntity<String> crossCrCancel = restTemplate.exchange(
                url("/api/allocations/extra/" + booked.getId() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(jsonAuth(tokenFor(otherCr))), String.class);
        assertThat(crossCrCancel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** PART 88 - Lab Assistant can see CR EXTRA activity for a term; CR/student cannot reach the administrative activity endpoint. */
    @Test
    void labAssistantSeesExtraActivityCrAndStudentCannot() {
        Fixture fixture = seedFixture("ACTIVITY", 30);
        AppUser crUser = seedUser(UserRole.CR, "extra-cr-activity@example.edu");
        crAssignmentRepository.save(new CrAssignment(crUser, fixture.division(), fixture.term(), crUser));
        String crToken = tokenFor(crUser);

        restTemplate.exchange(
                url("/api/allocations/extra"), HttpMethod.POST,
                new HttpEntity<>(bookingBody(fixture, fixture.lab().getId(), LocalTime.of(9, 0), LocalTime.of(11, 0)), jsonAuth(crToken)),
                String.class);

        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "extra-la-activity@example.edu");
        ResponseEntity<String> activityResponse = restTemplate.exchange(
                url("/api/allocations/extra/activity?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);
        assertThat(activityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activityResponse.getBody()).contains(fixture.lab().getCode());

        ResponseEntity<String> crActivity = restTemplate.exchange(
                url("/api/allocations/extra/activity?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(crToken)), String.class);
        assertThat(crActivity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
