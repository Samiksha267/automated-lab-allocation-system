package com.college.laballocation.analytics;

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
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.lab.LabUnavailability;
import com.college.laballocation.lab.LabUnavailabilityRepository;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.ScheduleVersionStatus;
import com.college.laballocation.scheduling.SchedulingTimeMapper;
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
 * Full-stack tests for the Phase 23 analytics API - published-only/draft/superseded/cancelled
 * exclusion, weighted overall utilization (mandatory, PART 20), peak day/lab, unused labs, extra-lab
 * breakdowns, security, and date-range validation. Environment-blocked on this development machine
 * (same documented Docker/Testcontainers-on-Windows limitation as every other IT class, see
 * docs/13-DEVELOPER-SETUP.md), written correctly for CI/future environments; manual Docker
 * verification (docs/11-TESTING-STRATEGY.md) covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AnalyticsApiIT {

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
    private LabUnavailabilityRepository labUnavailabilityRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

    @Autowired
    private SchedulingTimeMapper timeMapper;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 8, 25);

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

    private record Fixture(AcademicTerm term, Division division, Batch batch, Subject subject, Faculty faculty) {}

    private Fixture seedFixture(String suffix, LocalDate termStart, LocalDate termEnd) {
        Program program = programRepository.save(new Program("AN-PROG-" + suffix, "Analytics Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "A", 60));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "AN-SUB-" + suffix, "Analytics Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("AN-FAC-" + suffix, "Faculty " + suffix, null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("AN-YR-" + suffix, 1, "Test Term " + suffix, termStart, termEnd));
        term.updateStatus(TermStatus.ACTIVE);
        academicTermRepository.saveAndFlush(term);
        return new Fixture(term, division, batch, subject, faculty);
    }

    private Lab seedLab(String suffix) {
        LabType labType = labTypeRepository.save(new LabType("AN-TYPE-" + suffix, "Test Lab Type " + suffix, null));
        return labRepository.save(new Lab("AN-LAB-" + suffix, "Test Lab " + suffix, 30, labType, "C", "2", "1"));
    }

    private ScheduleVersion publishedVersion(Fixture fixture, AppUser labAssistant) {
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(fixture.term(), 1, null, labAssistant));
        version.publish(labAssistant);
        return scheduleVersionRepository.saveAndFlush(version);
    }

    private Allocation seedAllocation(
            Fixture fixture, Lab lab, ScheduleVersion version, AppUser createdBy, AllocationType type, AllocationStatus status,
            LocalDate date, LocalTime start, LocalTime end) {
        return seedAllocation(fixture, lab, fixture.faculty(), fixture.batch(), version, createdBy, type, status, date, start, end);
    }

    // A second, simultaneous allocation in a different lab needs its own faculty AND batch - the
    // same faculty can't teach two overlapping sessions at once, and the same batch of students
    // can't be in two labs at once either; the real ex_allocation_faculty_overlap/
    // ex_allocation_batch_overlap exclusion constraints correctly reject either regardless of
    // which lab is involved.
    private Allocation seedAllocation(
            Fixture fixture, Lab lab, Faculty faculty, Batch batch, ScheduleVersion version, AppUser createdBy,
            AllocationType type, AllocationStatus status, LocalDate date, LocalTime start, LocalTime end) {
        Allocation allocation = Allocation.forBatch(
                type, fixture.division(), batch, fixture.subject(), faculty, lab, date, start, end, status, version, createdBy);
        return allocationRepository.saveAndFlush(allocation);
    }

    @Test
    void utilizationCountsRealAllocationDurationAndCancelledExclusion() {
        Fixture fixture = seedFixture("DURATION", MONDAY, MONDAY.plusDays(1));
        Lab lab = seedLab("DURATION");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-duration@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        // Active: 09:00-10:00 (60) + 11:00-13:00 (120) = 180 booked minutes.
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0));
        // Cancelled: must NOT contribute to booked minutes.
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.CANCELLED, MONDAY, LocalTime.of(14, 0), LocalTime.of(17, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/lab-utilization?academicTermId=" + fixture.term().getId() + "&from=" + MONDAY + "&to=" + MONDAY), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"labCode\":\"" + lab.getCode() + "\"")
                .contains("\"bookedMinutes\":180")
                .contains("\"availableMinutes\":600")
                .doesNotContain("\"bookedMinutes\":420");
    }

    @Test
    void operationalAnalyticsExcludeDraftVersionAllocations() {
        Fixture fixture = seedFixture("DRAFT", MONDAY, MONDAY.plusDays(1));
        Lab lab = seedLab("DRAFT");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-draft@example.edu");
        ScheduleVersion published = publishedVersion(fixture, labAssistant);
        seedAllocation(fixture, lab, published, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        ScheduleVersion draft = scheduleVersionRepository.saveAndFlush(new ScheduleVersion(fixture.term(), 2, "revision", labAssistant));
        seedAllocation(fixture, lab, draft, labAssistant, AllocationType.REGULAR, AllocationStatus.APPROVED, MONDAY, LocalTime.of(11, 0), LocalTime.of(17, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/lab-utilization?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody()).contains("\"bookedMinutes\":60").doesNotContain("\"bookedMinutes\":420");
    }

    @Test
    void operationalAnalyticsExcludeSupersededVersionAllocationsAndDoNotDoubleCount() {
        Fixture fixture = seedFixture("SUPERSEDE", MONDAY, MONDAY.plusDays(1));
        Lab lab = seedLab("SUPERSEDE");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-supersede@example.edu");
        ScheduleVersion v1 = publishedVersion(fixture, labAssistant);
        seedAllocation(fixture, lab, v1, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));

        ScheduleVersion v2 = scheduleVersionRepository.save(new ScheduleVersion(fixture.term(), 2, "revision", labAssistant));
        v1.supersede();
        v2.publish(labAssistant);
        scheduleVersionRepository.saveAndFlush(v1);
        scheduleVersionRepository.saveAndFlush(v2);
        // A different time slot than v1's allocation - the exclusion constraint scopes to any
        // active-status allocation regardless of schedule version, so a genuinely overlapping
        // slot here would correctly be rejected as a real double-booking, not a version-scoping
        // question this test is about.
        seedAllocation(fixture, lab, v2, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/lab-utilization?academicTermId=" + fixture.term().getId() + "&from=" + MONDAY + "&to=" + MONDAY), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        // Only V2's 60 minutes count - V1's 120 minutes (SUPERSEDED) must not be included or double-counted.
        assertThat(response.getBody()).contains("\"bookedMinutes\":60").doesNotContain("\"bookedMinutes\":180");
    }

    @Test
    void weightedOverallUtilizationIsNotANaiveAverageOfPerLabPercentages() {
        Fixture fixture = seedFixture("WEIGHTED", MONDAY, MONDAY.plusDays(1));
        Lab labA = seedLab("WEIGHTED-A"); // available 600min, booked 300min -> 50%
        Lab labB = seedLab("WEIGHTED-B"); // available 120min (after unavailability), booked 120min -> 100%
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-weighted@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        Faculty secondFaculty = facultyRepository.save(new Faculty("AN-FAC-WEIGHTED2", "Faculty WEIGHTED2", null, null));
        Batch secondBatch = batchRepository.save(new Batch(fixture.division(), "B", 30));
        seedAllocation(fixture, labA, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(14, 0));
        seedAllocation(fixture, labB, secondFaculty, secondBatch, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));
        labUnavailabilityRepository.save(new LabUnavailability(
                labB, timeMapper.toInstant(MONDAY, LocalTime.of(11, 0)), timeMapper.toInstant(MONDAY, LocalTime.of(19, 0)), "maintenance", labAssistant));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/lab-utilization?academicTermId=" + fixture.term().getId() + "&from=" + MONDAY + "&to=" + MONDAY), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody())
                .contains("\"labCode\":\"" + labA.getCode() + "\"")
                .contains("\"utilizationPercent\":50.0")
                .contains("\"labCode\":\"" + labB.getCode() + "\"")
                .contains("\"availableMinutes\":120")
                .contains("\"utilizationPercent\":100.0")
                // (300+120)/(600+120) = 420/720 = 58.33%, never the naive average (50+100)/2 = 75%.
                .contains("\"overallUtilizationPercent\":58.3")
                .doesNotContain("\"overallUtilizationPercent\":75.0");
    }

    @Test
    void unusedLabsListsOnlyLabsWithZeroQualifyingAllocations() {
        Fixture fixture = seedFixture("UNUSED", MONDAY, MONDAY.plusDays(1));
        Lab usedLab = seedLab("UNUSED-USED");
        Lab unusedLab = seedLab("UNUSED-EMPTY");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-unused@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        seedAllocation(fixture, usedLab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/unused-labs?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody())
                .contains("\"labCode\":\"" + unusedLab.getCode() + "\"")
                .doesNotContain("\"labCode\":\"" + usedLab.getCode() + "\"");
    }

    @Test
    void mostUsedLabIsRankedByBookedMinutesNotAllocationCount() {
        Fixture fixture = seedFixture("PEAKLAB", MONDAY, MONDAY.plusDays(1));
        Lab busyShortLab = seedLab("PEAKLAB-A"); // 2 allocations, 240 minutes total
        Lab busyLongLab = seedLab("PEAKLAB-B"); // 1 allocation, 480 minutes total - more load, fewer bookings
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-peaklab@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        Faculty secondFaculty = facultyRepository.save(new Faculty("AN-FAC-PEAKLAB2", "Faculty PEAKLAB2", null, null));
        Batch secondBatch = batchRepository.save(new Batch(fixture.division(), "B", 30));
        seedAllocation(fixture, busyShortLab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));
        seedAllocation(fixture, busyShortLab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0));
        seedAllocation(fixture, busyLongLab, secondFaculty, secondBatch, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/peak-usage?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody()).contains("\"labCode\":\"" + busyLongLab.getCode() + "\"").contains("\"bookedMinutes\":480");
    }

    @Test
    void peakDayIsTheDateWithTheHighestBookedMinutes() {
        Fixture fixture = seedFixture("PEAKDAY", MONDAY, TUESDAY);
        Lab lab = seedLab("PEAKDAY");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-peakday@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)); // 60
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)); // 240

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/peak-usage?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody()).contains("\"date\":\"" + TUESDAY + "\"").contains("\"bookedMinutes\":240");
    }

    @Test
    void extraLabAnalyticsCountsTotalActiveCancelledAndBreaksDownByDivision() {
        Fixture fixture = seedFixture("EXTRA", MONDAY, MONDAY.plusDays(1));
        Lab lab = seedLab("EXTRA");
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-extra@example.edu");
        ScheduleVersion version = publishedVersion(fixture, labAssistant);
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.EXTRA, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.EXTRA, AllocationStatus.APPROVED, MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0));
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.EXTRA, AllocationStatus.CANCELLED, MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        // A REGULAR allocation must never be counted as an extra lab.
        seedAllocation(fixture, lab, version, labAssistant, AllocationType.REGULAR, AllocationStatus.PUBLISHED, MONDAY, LocalTime.of(15, 0), LocalTime.of(16, 0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/extra-labs?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody())
                .contains("\"total\":3")
                .contains("\"active\":2")
                .contains("\"cancelled\":1")
                .contains("\"failedBookingDataAvailable\":false")
                .contains("\"key\":\"" + fixture.division().getCode() + "\"");
    }

    @Test
    void conflictAnalyticsHonestlyReportsNoPersistedEvidenceExists() {
        Fixture fixture = seedFixture("CONFLICT", MONDAY, MONDAY.plusDays(1));
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-conflict@example.edu");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/conflicts?academicTermId=" + fixture.term().getId()), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getBody()).contains("\"evidenceAvailable\":false").contains("\"categories\":[]");
    }

    @Test
    void invalidDateRangeIsRejectedWithAValidationError() {
        Fixture fixture = seedFixture("BADRANGE", MONDAY, MONDAY.plusDays(1));
        AppUser labAssistant = seedUser(UserRole.LAB_ASSISTANT, "an-la-badrange@example.edu");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/analytics/summary?academicTermId=" + fixture.term().getId() + "&from=2026-08-25&to=2026-08-24"), HttpMethod.GET,
                new HttpEntity<>(jsonAuth(tokenFor(labAssistant))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void analyticsIsForbiddenToCrAndStudentAndUnauthorizedForAnonymous() {
        Fixture fixture = seedFixture("RBAC", MONDAY, MONDAY.plusDays(1));
        AppUser cr = seedUser(UserRole.CR, "an-rbac-cr@example.edu");
        AppUser student = seedUser(UserRole.STUDENT, "an-rbac-student@example.edu");
        String path = "/api/analytics/summary?academicTermId=" + fixture.term().getId();

        assertThat(restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(cr))), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(jsonAuth(tokenFor(student))), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(jsonAuth(null)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
