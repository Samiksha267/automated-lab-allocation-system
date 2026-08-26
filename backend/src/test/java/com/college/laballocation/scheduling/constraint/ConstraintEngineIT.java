package com.college.laballocation.scheduling.constraint;

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
import com.college.laballocation.lab.LabSoftware;
import com.college.laballocation.lab.LabSoftwareRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareRepository;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationQueryService;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.subject.SubjectSoftwareRequirement;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Full-stack constraint engine tests against a real PostgreSQL instance -
 * environment-blocked on this development machine (same documented
 * Docker/Testcontainers limitation as every other IT class), but written
 * correctly for CI/future environments (PART 62 of the Phase 9 brief). Real
 * repository-backed lab/faculty/batch/division conflict queries, real
 * {@code SchedulingContextFactory}/{@code CandidateAllocationFactory}, and
 * the real Spring-assembled {@link ConstraintEngine} (all twelve
 * {@code @Component} constraints auto-discovered) - manual Docker
 * verification (docs/11-TESTING-STRATEGY.md) covers what this cannot run
 * here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ConstraintEngineIT {

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
    private SoftwareRepository softwareRepository;

    @Autowired
    private LabSoftwareRepository labSoftwareRepository;

    @Autowired
    private SubjectSoftwareRequirementRepository subjectSoftwareRequirementRepository;

    @Autowired
    private SubjectFacultyAssignmentRepository subjectFacultyAssignmentRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private AllocationQueryService allocationQueryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SchedulingContextFactory schedulingContextFactory;

    @Autowired
    private CandidateAllocationFactory candidateAllocationFactory;

    @Autowired
    private ConstraintEngine constraintEngine;

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-CE-PROG-" + suffix, "Constraint Engine Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-CE-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private ScheduleVersion seedPublishedVersion(AcademicTerm term, AppUser user) {
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        return version;
    }

    private Lab seedLab(String suffix, int capacity) {
        LabType labType = labTypeRepository.save(new LabType("IT-CE-TYPE-" + suffix, "Test Lab Type", null));
        return labRepository.save(new Lab("IT-CE-LAB-" + suffix, "Test Lab", capacity, labType, "C", "3", "301"));
    }

    /** PART 87 - A1 (BDA, Faculty BDA, Lab X) exists; A2 (CNS, Faculty CNS, Lab Y) must be independently VALID. */
    @Test
    void a1AndA2DifferentBatchesOfSameDivisionAreBothIndependentlyValid() {
        AcademicYear year = seedYear("A1A2");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batchA1 = batchRepository.save(new Batch(division, "A1", 30));
        Batch batchA2 = batchRepository.save(new Batch(division, "A2", 30));
        Subject bda = subjectRepository.save(new Subject(year, "BDA", "Big Data Analytics"));
        Subject cns = subjectRepository.save(new Subject(year, "CNS", "Computer Networks & Security"));
        Faculty facultyBda = facultyRepository.save(new Faculty("IT-CE-FAC-BDA", "Faculty BDA", null, null));
        Faculty facultyCns = facultyRepository.save(new Faculty("IT-CE-FAC-CNS", "Faculty CNS", null, null));
        AcademicTerm term = seedTerm("A1A2");
        AppUser user = seedUser("it-ce-a1a2@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        Lab labB301 = seedLab("A1A2-B301", 70);
        Lab labC202 = seedLab("A1A2-C202", 70);

        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, facultyBda, division, batchA1, term));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(cns, facultyCns, division, batchA2, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyBda, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyCns, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));

        Allocation a1 = Allocation.forBatch(
                AllocationType.EXTRA, division, batchA1, bda, facultyBda, labB301,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(a1);

        SchedulingRequest a2Request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA2.getId(), cns.getId(), facultyCns.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        SchedulingContext context = schedulingContextFactory.build(a2Request);
        CandidateAllocation candidate = candidateAllocationFactory.build(context, labC202.getId());

        ConstraintEvaluation evaluation = constraintEngine.evaluate(context, candidate);

        assertThat(evaluation.valid()).isTrue();
        assertThat(evaluation.violations()).isEmpty();
    }

    /** PART 52 - same lab, overlapping time -> HC-01 fails end-to-end against real persisted data. */
    @Test
    void sameLabOverlappingCandidateFailsLabConflictEndToEnd() {
        AcademicYear year = seedYear("LABCONF");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-CE-SUB", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CE-FAC-LC", "Faculty LC", null, null));
        AcademicTerm term = seedTerm("LABCONF");
        AppUser user = seedUser("it-ce-labconf@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        Lab lab = seedLab("LABCONF", 70);

        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));

        Allocation existing = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(existing);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(10, 0), LocalTime.of(12, 0), null);
        SchedulingContext context = schedulingContextFactory.build(request);
        CandidateAllocation candidate = candidateAllocationFactory.build(context, lab.getId());

        ConstraintEvaluation evaluation = constraintEngine.evaluate(context, candidate);

        assertThat(evaluation.valid()).isFalse();
        assertThat(evaluation.violations()).anyMatch(v -> v.errorCode().equals("LAB_CONFLICT"));
    }

    /** PART 27 - BDA/Cloudera demo, end-to-end against real persisted requirement + capability data. */
    @Test
    void bdaRequiringCloueraFailsAgainstALabWithoutItAndPassesAgainstOneWithIt() {
        AcademicYear year = seedYear("CLOUDERA");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-CE-BDA", "Big Data Analytics"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CE-FAC-CL", "Faculty CL", null, null));
        AcademicTerm term = seedTerm("CLOUDER");
        AppUser user = seedUser("it-ce-cloudera@example.edu");
        seedPublishedVersion(term, user);
        Lab labWithCloudera = seedLab("CLOUDERA-YES", 70);
        Lab labWithoutCloudera = seedLab("CLOUDERA-NO", 70);
        Software cloudera = softwareRepository.save(new Software("IT-CE-CLOUDERA", "Cloudera"));
        labSoftwareRepository.save(new LabSoftware(labWithCloudera, cloudera, null));
        subjectSoftwareRequirementRepository.save(new SubjectSoftwareRequirement(bda, cloudera));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, faculty, division, batch, term));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), bda.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        SchedulingContext context = schedulingContextFactory.build(request);

        ConstraintEvaluation withCloudera = constraintEngine.evaluate(context, candidateAllocationFactory.build(context, labWithCloudera.getId()));
        ConstraintEvaluation withoutCloudera =
                constraintEngine.evaluate(context, candidateAllocationFactory.build(context, labWithoutCloudera.getId()));

        assertThat(withoutCloudera.violations()).anyMatch(v -> v.errorCode().equals("SOFTWARE_MISMATCH"));
        assertThat(withCloudera.violations()).noneMatch(v -> v.errorCode().equals("SOFTWARE_MISMATCH"));
    }
}
