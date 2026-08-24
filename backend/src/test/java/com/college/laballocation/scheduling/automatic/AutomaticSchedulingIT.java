package com.college.laballocation.scheduling.automatic;

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
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * Full-stack automatic-scheduling tests against a real PostgreSQL instance -
 * environment-blocked on this development machine (same documented
 * Docker/Testcontainers limitation as every other IT class), but written
 * correctly for CI/future environments. Manual Docker verification
 * (docs/11-TESTING-STRATEGY.md) covers what this cannot run here. The
 * controlled greedy-fails/backtracking-succeeds proof itself is authoritative
 * at the unit level ({@code AutomaticSchedulingEngineTest}); this class
 * proves the real end-to-end integration (persisted + provisional occupancy
 * reaching the unmodified Phase 9 constraints through real
 * {@code SchedulingContextFactory}/{@code CandidateAllocationFactory} calls).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class AutomaticSchedulingIT {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AutomaticSchedulingEngine automaticSchedulingEngine;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-AUTO-PROG-" + suffix, "Automatic Scheduling Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-AUTO-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private LabType seedLabType(String suffix) {
        return labTypeRepository.save(new LabType("IT-AUTO-TYPE-" + suffix, "Test Lab Type " + suffix, null));
    }

    private Lab seedLab(String suffix, int capacity, LabType type) {
        return labRepository.save(new Lab("IT-AUTO-LAB-" + suffix, "Test Lab", capacity, type, "C", "3", "301"));
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private ScheduleVersion seedPublishedVersion(AcademicTerm term, AppUser user) {
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        return version;
    }

    /** PART 74/76 - two independent requirements (different subjects/batches/faculty/labs) both get placed. */
    @Test
    void multiRequirementSchedulePlacesBothWhenEnoughLabsExist() {
        AcademicYear year = seedYear("MULTI");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batchA1 = batchRepository.save(new Batch(division, "A1", 30));
        Batch batchA2 = batchRepository.save(new Batch(division, "A2", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-AUTO-BDA", "Big Data Analytics"));
        Subject cns = subjectRepository.save(new Subject(year, "IT-AUTO-CNS", "Computer Networks & Security"));
        Faculty facultyBda = facultyRepository.save(new Faculty("IT-AUTO-FAC-BDA", "Faculty BDA", null, null));
        Faculty facultyCns = facultyRepository.save(new Faculty("IT-AUTO-FAC-CNS", "Faculty CNS", null, null));
        AcademicTerm term = seedTerm("MULTI");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, facultyBda, division, batchA1, term));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(cns, facultyCns, division, batchA2, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyBda, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyCns, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("MULTI");
        seedLab("MULTI-A", 30, type);
        seedLab("MULTI-B", 30, type);

        SessionRequirement r1 = new SessionRequirement(
                "R1", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA1.getId(), bda.getId(), facultyBda.getId(), term.getId(), null);
        SessionRequirement r2 = new SessionRequirement(
                "R2", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA2.getId(), cns.getId(), facultyCns.getId(), term.getId(), null);

        AutomaticScheduleResult result = automaticSchedulingEngine.schedule(new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY));

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.assignments()).hasSize(2);
    }

    /** PART 43/62/80 - a real, already-persisted allocation is respected: the solver schedules around it, never through it. */
    @Test
    void persistedAllocationIsRespectedNotOverwritten() {
        AcademicYear year = seedYear("PERSIST");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-AUTO-SUB-PERSIST", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-AUTO-FAC-PERSIST", "Faculty PERSIST", null, null));
        AcademicTerm term = seedTerm("PERSIST");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("PERSIST");
        Lab onlyLab = seedLab("PERSIST-ONLY", 30, type);
        AppUser user = seedUser("it-auto-persist@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);

        Allocation existing = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, onlyLab,
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(existing);

        SessionRequirement r1 = new SessionRequirement(
                "R1", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(), null);

        AutomaticScheduleResult result = automaticSchedulingEngine.schedule(new AutomaticSchedulingRequest(List.of(r1), MONDAY, MONDAY));

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        PlannedAllocation planned = result.assignments().get(0);
        // The only lab in this scenario is occupied 09:00-11:00 - the solver must have found a different time.
        assertThat(planned.request().startTime()).isNotEqualTo(LocalTime.of(9, 0));
    }

    /** PART 61 - A1 and A2 (same division, different batch/faculty/lab) may both be scheduled at the same time. */
    @Test
    void a1AndA2CanBeScheduledSimultaneously() {
        AcademicYear year = seedYear("A1A2");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batchA1 = batchRepository.save(new Batch(division, "A1", 30));
        Batch batchA2 = batchRepository.save(new Batch(division, "A2", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-AUTO-A1A2-BDA", "Big Data Analytics"));
        Subject cns = subjectRepository.save(new Subject(year, "IT-AUTO-A1A2-CNS", "Computer Networks & Security"));
        Faculty facultyBda = facultyRepository.save(new Faculty("IT-AUTO-A1A2-FBDA", "Faculty BDA", null, null));
        Faculty facultyCns = facultyRepository.save(new Faculty("IT-AUTO-A1A2-FCNS", "Faculty CNS", null, null));
        AcademicTerm term = seedTerm("A1A2");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, facultyBda, division, batchA1, term));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(cns, facultyCns, division, batchA2, term));
        // Both faculties available ONLY 09:00-11:00 - forces the solver toward the same slot for both.
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyBda, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyCns, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        LabType type = seedLabType("A1A2");
        seedLab("A1A2-A", 30, type);
        seedLab("A1A2-B", 30, type);

        SessionRequirement r1 = new SessionRequirement(
                "R1", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA1.getId(), bda.getId(), facultyBda.getId(), term.getId(), null);
        SessionRequirement r2 = new SessionRequirement(
                "R2", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA2.getId(), cns.getId(), facultyCns.getId(), term.getId(), null);

        AutomaticScheduleResult result = automaticSchedulingEngine.schedule(new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY));

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        var r1Assignment = result.assignments().stream().filter(a -> a.requirementKey().equals("R1")).findFirst().orElseThrow();
        var r2Assignment = result.assignments().stream().filter(a -> a.requirementKey().equals("R2")).findFirst().orElseThrow();
        assertThat(r1Assignment.request().startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r2Assignment.request().startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r1Assignment.chosenCandidate().labCode()).isNotEqualTo(r2Assignment.chosenCandidate().labCode());
    }

    /** PART 42/73/82 - automatic scheduling must never persist anything. */
    @Test
    void automaticSchedulingNeverChangesAllocationRowCount() {
        AcademicYear year = seedYear("NOPERSIST");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-AUTO-SUB-NOPERSIST", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-AUTO-FAC-NOPERSIST", "Faculty NOPERSIST", null, null));
        AcademicTerm term = seedTerm("NOPERSIST");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        seedLab("NOPERSIST-A", 30, seedLabType("NOPERSIST"));

        long before = allocationRepository.count();

        SessionRequirement r1 = new SessionRequirement(
                "R1", AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(), null);
        automaticSchedulingEngine.schedule(new AutomaticSchedulingRequest(List.of(r1), MONDAY, MONDAY));

        long after = allocationRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
