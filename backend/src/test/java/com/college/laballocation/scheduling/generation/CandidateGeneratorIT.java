package com.college.laballocation.scheduling.generation;

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
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.faculty.SubjectFacultyAssignmentRepository;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabSoftware;
import com.college.laballocation.lab.LabSoftwareRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeRepository;
import com.college.laballocation.lab.LabUnavailability;
import com.college.laballocation.lab.LabUnavailabilityRepository;
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareRepository;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.subject.SubjectSoftwareRequirement;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * Full-stack candidate-generation tests against a real PostgreSQL instance -
 * environment-blocked on this development machine (same documented
 * Docker/Testcontainers limitation as every other IT class), but written
 * correctly for CI/future environments (PART 64 of the Phase 10 brief).
 * Manual Docker verification (docs/11-TESTING-STRATEGY.md) covers what this
 * cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class CandidateGeneratorIT {

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
    private LabUnavailabilityRepository labUnavailabilityRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleVersionRepository scheduleVersionRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CandidateGenerator candidateGenerator;

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-CG-PROG-" + suffix, "Candidate Gen Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-CG-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private Lab seedLab(String suffix, int capacity) {
        LabType labType = labTypeRepository.save(new LabType("IT-CG-TYPE-" + suffix, "Test Lab Type", null));
        return labRepository.save(new Lab("IT-CG-LAB-" + suffix, "Test Lab", capacity, labType, "C", "3", "301"));
    }

    /** PART 66/52 - BDA requires Cloudera: labs without it are evaluated and specifically fail SOFTWARE_MISMATCH, never silently absent. */
    @Test
    void bdaCandidateGenerationEvaluatesEveryLabAndRejectsMissingCloudera() {
        AcademicYear year = seedYear("CLOUDERA");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-CG-BDA", "Big Data Analytics"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CG-FAC-CL", "Faculty CL", null, null));
        AcademicTerm term = seedTerm("CLOUDER");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, faculty, division, batch, term));

        Lab labWithCloudera = seedLab("CLOUDERA-YES", 70);
        Lab labWithoutCloudera = seedLab("CLOUDERA-NO", 70);
        Software cloudera = softwareRepository.save(new Software("IT-CG-CLOUDERA", "Cloudera"));
        labSoftwareRepository.save(new LabSoftware(labWithCloudera, cloudera, null));
        subjectSoftwareRequirementRepository.save(new SubjectSoftwareRequirement(bda, cloudera));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), bda.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult result = candidateGenerator.generate(request);

        assertThat(result.evaluatedCandidates()).extracting(c -> c.candidate().lab().code())
                .contains(labWithCloudera.getCode(), labWithoutCloudera.getCode());
        var withoutCloudera = result.evaluatedCandidates().stream()
                .filter(c -> c.candidate().lab().code().equals(labWithoutCloudera.getCode())).findFirst().orElseThrow();
        assertThat(withoutCloudera.violations()).extracting(ConstraintViolation::errorCode).contains("SOFTWARE_MISMATCH");
    }

    /** PART 67 - a lab with sufficient software but insufficient capacity is still generated, then rejected by HC-07. */
    @Test
    void underCapacityLabIsGeneratedThenRejectedByCapacityConstraint() {
        AcademicYear year = seedYear("CAP");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 68));
        Subject subject = subjectRepository.save(new Subject(year, "IT-CG-SUB-CAP", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CG-FAC-CAP", "Faculty CAP", null, null));
        AcademicTerm term = seedTerm("CAP");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        Lab smallLab = seedLab("SMALL", 50);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult result = candidateGenerator.generate(request);

        var smallLabCandidate = result.evaluatedCandidates().stream()
                .filter(c -> c.candidate().lab().code().equals(smallLab.getCode())).findFirst().orElseThrow();
        assertThat(smallLabCandidate.isValid()).isFalse();
        assertThat(smallLabCandidate.violations()).extracting(ConstraintViolation::errorCode).contains("CAPACITY_VIOLATION");
    }

    /** PART 69 - an existing Allocation on an otherwise-valid lab is still generated as a candidate, then rejected by HC-01. */
    @Test
    void existingAllocationOnALabIsGeneratedThenRejectedByLabConflict() {
        AcademicYear year = seedYear("CONFLICT");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-CG-SUB-CONF", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CG-FAC-CONF", "Faculty CONF", null, null));
        AcademicTerm term = seedTerm("CONFLIC");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        AppUser user = seedUser("it-cg-conflict@example.edu");
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        scheduleVersionRepository.saveAndFlush(version);
        Lab lab = seedLab("CONFLICT", 70);

        Allocation existing = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(existing);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(10, 0), LocalTime.of(12, 0), null);

        CandidateGenerationResult result = candidateGenerator.generate(request);

        var candidate = result.evaluatedCandidates().stream()
                .filter(c -> c.candidate().lab().code().equals(lab.getCode())).findFirst().orElseThrow();
        assertThat(candidate.isValid()).isFalse();
        assertThat(candidate.violations()).extracting(ConstraintViolation::errorCode).contains("LAB_CONFLICT");
    }

    /** PART 70 - a temporarily unavailable lab is still generated as a candidate, then rejected by HC-06. */
    @Test
    void temporarilyUnavailableLabIsGeneratedThenRejectedByLabAvailability() {
        AcademicYear year = seedYear("UNAVAIL");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-CG-SUB-UNAV", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CG-FAC-UNAV", "Faculty UNAV", null, null));
        AcademicTerm term = seedTerm("UNAVAIL");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        AppUser user = seedUser("it-cg-unavail@example.edu");
        Lab lab = seedLab("UNAVAIL", 70);

        Instant start = LocalDate.of(2026, 8, 24).atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = LocalDate.of(2026, 8, 24).atTime(12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        labUnavailabilityRepository.save(new LabUnavailability(lab, start, end, "maintenance", user));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(11, 0), LocalTime.of(12, 30), null);

        CandidateGenerationResult result = candidateGenerator.generate(request);

        var candidate = result.evaluatedCandidates().stream()
                .filter(c -> c.candidate().lab().code().equals(lab.getCode())).findFirst().orElseThrow();
        assertThat(candidate.isValid()).isFalse();
        assertThat(candidate.violations()).extracting(ConstraintViolation::errorCode).contains("LAB_UNAVAILABLE");
    }

    /** PART 71 - every candidate invalid still completes normally, with an empty valid list, never an exception. */
    @Test
    void allLabsInvalidStillCompletesNormally() {
        AcademicYear year = seedYear("ZERO");
        Division division = divisionRepository.save(new Division(year, "A", 500));
        Batch batch = batchRepository.save(new Batch(division, "A1", 500));
        Subject subject = subjectRepository.save(new Subject(year, "IT-CG-SUB-ZERO", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-CG-FAC-ZERO", "Faculty ZERO", null, null));
        AcademicTerm term = seedTerm("ZERO");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        seedLab("ZERO", 70); // capacity 70 < batch strength 500 - every lab in the whole table now fails HC-07 for this candidate

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult result = candidateGenerator.generate(request);

        assertThat(result.validCandidates()).isEmpty();
        assertThat(result.totalCount()).isGreaterThan(0);
    }

    /** PART 72 - the A1/A2 scenario at the generation layer: A1 already exists, A2 (different batch, same division) generation must still offer valid labs. */
    @Test
    void a1ExistingDoesNotEliminateEveryLabForA2CandidateGeneration() {
        AcademicYear year = seedYear("A1A2");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batchA1 = batchRepository.save(new Batch(division, "A1", 30));
        Batch batchA2 = batchRepository.save(new Batch(division, "A2", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-CG-BDA2", "Big Data Analytics"));
        Subject cns = subjectRepository.save(new Subject(year, "IT-CG-CNS2", "Computer Networks & Security"));
        Faculty facultyBda = facultyRepository.save(new Faculty("IT-CG-FAC-BDA2", "Faculty BDA", null, null));
        Faculty facultyCns = facultyRepository.save(new Faculty("IT-CG-FAC-CNS2", "Faculty CNS", null, null));
        AcademicTerm term = seedTerm("A1A2");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, facultyBda, division, batchA1, term));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(cns, facultyCns, division, batchA2, term));
        AppUser user = seedUser("it-cg-a1a2@example.edu");
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        scheduleVersionRepository.saveAndFlush(version);
        Lab labA1 = seedLab("A1A2-A1LAB", 70);
        Lab labA2 = seedLab("A1A2-A2LAB", 70);

        Allocation a1 = Allocation.forBatch(
                AllocationType.EXTRA, division, batchA1, bda, facultyBda, labA1,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(a1);

        SchedulingRequest a2Request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA2.getId(), cns.getId(), facultyCns.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult result = candidateGenerator.generate(a2Request);

        var a2LabCandidate = result.evaluatedCandidates().stream()
                .filter(c -> c.candidate().lab().code().equals(labA2.getCode())).findFirst().orElseThrow();
        // labA2 was never occupied by A1 (different lab) - it must remain valid for A2's own request,
        // proving same-division different-batch occupancy alone never eliminates a candidate.
        assertThat(a2LabCandidate.violations()).extracting(ConstraintViolation::errorCode).doesNotContain("LAB_CONFLICT", "DIVISION_CONFLICT", "BATCH_CONFLICT");
    }
}
