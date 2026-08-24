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
import com.college.laballocation.common.ApiException;
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
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabBookingRequest;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real, true-concurrency tests for Phase 16 - proves PostgreSQL's exclusion
 * constraints (V11 migration) and the per-division pessimistic lock actually
 * prevent double-booking under genuinely simultaneous requests, not merely
 * sequential ones. Environment-blocked on this development machine (same
 * documented Docker/Testcontainers limitation as every other IT class, see
 * docs/13-DEVELOPER-SETUP.md), but written correctly for CI/future
 * environments, and independently proven live against the real Dockerized
 * stack via true parallel HTTP requests (docs/11-TESTING-STRATEGY.md).
 *
 * <p><b>No test method here is itself {@code @Transactional}</b> (PART 35 of
 * the phase brief): each competing booking call goes directly through the
 * real, Spring-proxied {@link ExtraLabService} bean from its own worker
 * thread, so each call gets its own independent transaction/connection via
 * Spring's thread-bound transaction synchronization - never sharing one
 * outer test transaction, which would make the whole exercise meaningless
 * (both "bookings" would just be two statements inside the same not-yet-committed
 * transaction, never actually racing).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class AllocationConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ExtraLabService extraLabService;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    private record Fixture(
            Division division, Subject subject, Faculty faculty, AcademicTerm term, Lab lab, AppUser crUser) {}

    /** One division/subject/faculty/lab/term/CR-user/published-version scenario, suffix-namespaced. */
    private Fixture seedFixture(String suffix, Long batchDivisionOverride) {
        Program program = programRepository.save(new Program("CONC-PROG-" + suffix, "Concurrency Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        Division division = divisionRepository.save(new Division(year, "A", 60));
        Subject subject = subjectRepository.save(new Subject(year, "CONC-SUB-" + suffix, "Concurrency Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("CONC-FAC-" + suffix, "Faculty " + suffix, null, null));
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("CONC-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType labType = labTypeRepository.save(new LabType("CONC-TYPE-" + suffix, "Test Lab Type " + suffix, null));
        Lab lab = labRepository.save(new Lab("CONC-LAB-" + suffix, "Test Lab", 30, labType, "C", "2", "1"));
        AppUser publisher = userRepository.save(
                new AppUser("conc-publisher-" + suffix + "@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, publisher));
        version.publish(publisher);
        AppUser crUser = userRepository.save(
                new AppUser("conc-cr-" + suffix + "@example.edu", passwordEncoder.encode("irrelevant-pw1"), UserRole.CR, "Test CR"));
        crAssignmentRepository.save(new CrAssignment(crUser, division, term, crUser));
        return new Fixture(division, subject, faculty, term, lab, crUser);
    }

    private Batch batchFor(Division division, String code) {
        return batchRepository.save(new Batch(division, code, 30));
    }

    private void assignBatch(Subject subject, Faculty faculty, Division division, Batch batch, AcademicTerm term) {
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
    }

    private ExtraLabBookingRequest bookingRequest(Long subjectId, TargetType targetType, Long batchId, Long labId) {
        return new ExtraLabBookingRequest(subjectId, targetType, batchId, MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), labId);
    }

    /** Runs two booking calls, released simultaneously via a {@link CountDownLatch} barrier, each on its own thread/transaction. */
    private record RaceOutcome(int successCount, int conflictCount) {}

    private RaceOutcome race(Long userId1, ExtraLabBookingRequest r1, Long userId2, ExtraLabBookingRequest r2) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Future<?> f1 = executor.submit(() -> attempt(userId1, r1, ready, go, successes, conflicts));
        Future<?> f2 = executor.submit(() -> attempt(userId2, r2, ready, go, successes, conflicts));

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        return new RaceOutcome(successes.get(), conflicts.get());
    }

    private void attempt(
            Long userId, ExtraLabBookingRequest request, CountDownLatch ready, CountDownLatch go,
            AtomicInteger successes, AtomicInteger conflicts) {
        try {
            ready.countDown();
            go.await();
            extraLabService.book(userId, request);
            successes.incrementAndGet();
        } catch (ApiException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** PART 27/82 - two independent, otherwise-valid requests contending for the same lab must never both commit. */
    @Test
    void concurrentSameLabRequestsProduceExactlyOneSuccess() throws Exception {
        Fixture fixture = seedFixture("LAB", null);
        Batch batchA = batchFor(fixture.division(), "A1");
        Batch batchB = batchFor(fixture.division(), "A2");
        Subject subjectB = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "CONC-SUB-LAB2", "Second"));
        Faculty facultyB = facultyRepository.save(new Faculty("CONC-FAC-LAB2", "Faculty LAB2", null, null));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(facultyB, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batchA, fixture.term());
        assignBatch(subjectB, facultyB, fixture.division(), batchB, fixture.term());

        RaceOutcome outcome = race(
                fixture.crUser().getId(), bookingRequest(fixture.subject().getId(), TargetType.BATCH, batchA.getId(), fixture.lab().getId()),
                fixture.crUser().getId(), bookingRequest(subjectB.getId(), TargetType.BATCH, batchB.getId(), fixture.lab().getId()));

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.conflictCount()).isEqualTo(1);
        assertThat(allocationRepository.findByLabIdAndAllocationDateAndStatusIn(
                        fixture.lab().getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(1);
    }

    /** PART 28/83 - same faculty, different labs, same time - exactly one commits. */
    @Test
    void concurrentSameFacultyRequestsProduceExactlyOneSuccess() throws Exception {
        Fixture fixture = seedFixture("FAC", null);
        Batch batchA = batchFor(fixture.division(), "A1");
        Batch batchB = batchFor(fixture.division(), "A2");
        Subject subjectB = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "CONC-SUB-FAC2", "Second"));
        Lab labB = labRepository.save(new Lab("CONC-LAB-FAC2", "Test Lab", 30, fixture.lab().getLabType(), "C", "2", "2"));
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batchA, fixture.term());
        assignBatch(subjectB, fixture.faculty(), fixture.division(), batchB, fixture.term());

        RaceOutcome outcome = race(
                fixture.crUser().getId(), bookingRequest(fixture.subject().getId(), TargetType.BATCH, batchA.getId(), fixture.lab().getId()),
                fixture.crUser().getId(), bookingRequest(subjectB.getId(), TargetType.BATCH, batchB.getId(), labB.getId()));

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.conflictCount()).isEqualTo(1);
        assertThat(allocationRepository.findByFacultyIdAndAllocationDateAndStatusIn(
                        fixture.faculty().getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(1);
    }

    /** PART 29/84 - same batch, different labs/faculty, same time - exactly one commits. */
    @Test
    void concurrentSameBatchRequestsProduceExactlyOneSuccess() throws Exception {
        Fixture fixture = seedFixture("BATCH", null);
        Batch batch = batchFor(fixture.division(), "A1");
        Subject subjectB = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "CONC-SUB-BATCH2", "Second"));
        Faculty facultyB = facultyRepository.save(new Faculty("CONC-FAC-BATCH2", "Faculty BATCH2", null, null));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(facultyB, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        Lab labB = labRepository.save(new Lab("CONC-LAB-BATCH2", "Test Lab", 30, fixture.lab().getLabType(), "C", "2", "2"));
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batch, fixture.term());
        assignBatch(subjectB, facultyB, fixture.division(), batch, fixture.term());

        RaceOutcome outcome = race(
                fixture.crUser().getId(), bookingRequest(fixture.subject().getId(), TargetType.BATCH, batch.getId(), fixture.lab().getId()),
                fixture.crUser().getId(), bookingRequest(subjectB.getId(), TargetType.BATCH, batch.getId(), labB.getId()));

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.conflictCount()).isEqualTo(1);
        assertThat(allocationRepository.findByBatchIdAndAllocationDateAndStatusIn(
                        batch.getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(1);
    }

    /** PART 31/86 - DIVISION vs BATCH cross-type race, same division, same time - exactly one commits. */
    @Test
    void concurrentDivisionVsBatchRequestsProduceExactlyOneSuccess() throws Exception {
        Fixture fixture = seedFixture("DVB", null);
        Batch batch = batchFor(fixture.division(), "A1");
        Subject subjectB = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "CONC-SUB-DVB2", "Second"));
        Faculty facultyB = facultyRepository.save(new Faculty("CONC-FAC-DVB2", "Faculty DVB2", null, null));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(facultyB, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        Lab labB = labRepository.save(new Lab("CONC-LAB-DVB2", "Test Lab", 30, fixture.lab().getLabType(), "C", "2", "2"));
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batch, fixture.term());
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subjectB, facultyB, fixture.division(), null, fixture.term()));

        RaceOutcome outcome = race(
                fixture.crUser().getId(), bookingRequest(fixture.subject().getId(), TargetType.BATCH, batch.getId(), fixture.lab().getId()),
                fixture.crUser().getId(), bookingRequest(subjectB.getId(), TargetType.DIVISION, null, labB.getId()));

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(outcome.conflictCount()).isEqualTo(1);
        assertThat(allocationRepository.findByDivisionIdAndAllocationDateAndStatusIn(
                        fixture.division().getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(1);
    }

    /** PART 30/85 - A1 and A2, different labs/faculty, same division/time - BOTH must succeed (serialization, never rejection). */
    @Test
    void concurrentDifferentBatchesInSameDivisionBothSucceed() throws Exception {
        Fixture fixture = seedFixture("A1A2", null);
        Batch batchA1 = batchFor(fixture.division(), "A1");
        Batch batchA2 = batchFor(fixture.division(), "A2");
        Subject subjectB = subjectRepository.save(new Subject(fixture.division().getAcademicYear(), "CONC-SUB-A1A2-2", "Second"));
        Faculty facultyB = facultyRepository.save(new Faculty("CONC-FAC-A1A2-2", "Faculty A1A2-2", null, null));
        facultyAvailabilityRepository.save(
                new FacultyAvailability(facultyB, fixture.term(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        Lab labB = labRepository.save(new Lab("CONC-LAB-A1A2-2", "Test Lab", 30, fixture.lab().getLabType(), "C", "2", "2"));
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batchA1, fixture.term());
        assignBatch(subjectB, facultyB, fixture.division(), batchA2, fixture.term());

        RaceOutcome outcome = race(
                fixture.crUser().getId(), bookingRequest(fixture.subject().getId(), TargetType.BATCH, batchA1.getId(), fixture.lab().getId()),
                fixture.crUser().getId(), bookingRequest(subjectB.getId(), TargetType.BATCH, batchA2.getId(), labB.getId()));

        assertThat(outcome.successCount()).isEqualTo(2);
        assertThat(outcome.conflictCount()).isEqualTo(0);
    }

    /** PART 25/87 - adjacent intervals on the same lab must both succeed, sequentially (not a race - a straightforward DB-level proof). */
    @Test
    void adjacentIntervalsOnSameLabBothSucceed() {
        Fixture fixture = seedFixture("ADJ", null);
        Batch batch = batchFor(fixture.division(), "A1");
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batch, fixture.term());

        extraLabService.book(
                fixture.crUser().getId(),
                new ExtraLabBookingRequest(
                        fixture.subject().getId(), TargetType.BATCH, batch.getId(), MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(11, 0), fixture.lab().getId()));
        extraLabService.book(
                fixture.crUser().getId(),
                new ExtraLabBookingRequest(
                        fixture.subject().getId(), TargetType.BATCH, batch.getId(), MONDAY,
                        LocalTime.of(11, 0), LocalTime.of(13, 0), fixture.lab().getId()));

        assertThat(allocationRepository.findByLabIdAndAllocationDateAndStatusIn(
                        fixture.lab().getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(2);
    }

    /** PART 24/88 - a cancelled allocation must not block a new booking for the identical resource/time. */
    @Test
    void cancelledAllocationDoesNotBlockNewBooking() {
        Fixture fixture = seedFixture("CANCEL", null);
        Batch batch = batchFor(fixture.division(), "A1");
        assignBatch(fixture.subject(), fixture.faculty(), fixture.division(), batch, fixture.term());

        Allocation cancelled = Allocation.forBatch(
                AllocationType.EXTRA, fixture.division(), batch, fixture.subject(), fixture.faculty(), fixture.lab(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.PUBLISHED,
                scheduleVersionRepository.findByAcademicTermIdAndStatus(fixture.term().getId(), com.college.laballocation.scheduling.ScheduleVersionStatus.PUBLISHED)
                        .orElseThrow(),
                fixture.crUser());
        cancelled = allocationRepository.saveAndFlush(cancelled);
        cancelled.cancel(fixture.crUser(), "test");
        allocationRepository.saveAndFlush(cancelled);

        var response = extraLabService.book(
                fixture.crUser().getId(),
                new ExtraLabBookingRequest(
                        fixture.subject().getId(), TargetType.BATCH, batch.getId(), MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(11, 0), fixture.lab().getId()));

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(allocationRepository.findByLabIdAndAllocationDateAndStatusIn(
                        fixture.lab().getId(), MONDAY, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED)))
                .hasSize(1);
    }
}
