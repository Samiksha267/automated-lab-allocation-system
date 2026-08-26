package com.college.laballocation.scheduling.scoring;

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
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.generation.CandidateGenerationResult;
import com.college.laballocation.scheduling.generation.CandidateGenerator;
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
 * Full-stack scoring tests against a real PostgreSQL instance - environment-
 * blocked on this development machine (same documented Docker/Testcontainers
 * limitation as every other IT class), but written correctly for CI/future
 * environments. Manual Docker verification (docs/11-TESTING-STRATEGY.md)
 * covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ScoringEngineIT {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CandidateGenerator candidateGenerator;

    @Autowired
    private ScoringEngine scoringEngine;

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-SC-PROG-" + suffix, "Scoring Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-SC-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private LabType seedLabType(String suffix) {
        return labTypeRepository.save(new LabType("IT-SC-TYPE-" + suffix, "Test Lab Type " + suffix, null));
    }

    private Lab seedLab(String suffix, int capacity, LabType type) {
        return labRepository.save(new Lab("IT-SC-LAB-" + suffix, "Test Lab", capacity, type, "C", "3", "301"));
    }

    /** PART 12/13/42 - a closer capacity fit outranks a much larger, equally-valid lab. */
    @Test
    void capacityFitDifferentiatesOtherwiseEqualCandidates() {
        AcademicYear year = seedYear("CAPFIT");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 68));
        Subject subject = subjectRepository.save(new Subject(year, "IT-SC-SUB-CAPFIT", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-CAPFIT", "Faculty CAPFIT", null, null));
        AcademicTerm term = seedTerm("CAPFIT");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("CAPFIT");
        Lab tightFit = seedLab("CAPFIT-TIGHT", 70, type);
        Lab looseFit = seedLab("CAPFIT-LOOSE", 150, type);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        List<String> rankedCodes = scoringResult.rankedCandidates().stream().map(ScoredCandidate::labCode).toList();
        assertThat(rankedCodes.indexOf(tightFit.getCode())).isLessThan(rankedCodes.indexOf(looseFit.getCode()));
    }

    /** PART 17/18/50 - a subject's preferred (not required) lab type differentiates two otherwise-valid candidates. */
    @Test
    void preferredLabTypeDifferentiatesBothValidCandidates() {
        AcademicYear year = seedYear("PREFTYPE");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 40));
        LabType preferredType = seedLabType("PREFTYPE-PREFERRED");
        LabType otherType = seedLabType("PREFTYPE-OTHER");
        Subject subject = subjectRepository.save(new Subject(year, "IT-SC-SUB-PREFTYPE", "Test Subject"));
        subject.setLabTypeRequirement(null, preferredType);
        subjectRepository.saveAndFlush(subject);
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-PREFTYPE", "Faculty PREFTYPE", null, null));
        AcademicTerm term = seedTerm("PREFTYP");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        Lab matchingLab = seedLab("PREFTYPE-MATCH", 45, preferredType);
        Lab mismatchedLab = seedLab("PREFTYPE-MISMATCH", 45, otherType);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        var matched = scoringResult.rankedCandidates().stream().filter(c -> c.labCode().equals(matchingLab.getCode())).findFirst().orElseThrow();
        var mismatched = scoringResult.rankedCandidates().stream().filter(c -> c.labCode().equals(mismatchedLab.getCode())).findFirst().orElseThrow();
        assertThat(matched.totalScore()).isGreaterThan(mismatched.totalScore());
    }

    /** PART 34/51/70 - hard-vs-soft: an invalid candidate never appears in the ranking, no matter how favorable its soft factors would be. */
    @Test
    void invalidCandidateIsNeverRankedRegardlessOfSoftFactors() {
        AcademicYear year = seedYear("HARDSOFT");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 68));
        LabType preferredType = seedLabType("HARDSOFT-PREFERRED");
        Subject subject = subjectRepository.save(new Subject(year, "IT-SC-SUB-HARDSOFT", "Test Subject"));
        subject.setLabTypeRequirement(null, preferredType);
        subjectRepository.saveAndFlush(subject);
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-HARDSOFT", "Faculty HARDSOFT", null, null));
        AcademicTerm term = seedTerm("HARDSFT");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        // Undersized (40 < required 68) but matches the preferred type - a perfect soft-score candidate that must still be rejected.
        Lab undersizedButPreferred = seedLab("HARDSOFT-SMALL", 40, preferredType);
        Lab validCandidate = seedLab("HARDSOFT-VALID", 70, preferredType);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        assertThat(scoringResult.rankedCandidates()).extracting(ScoredCandidate::labCode)
                .doesNotContain(undersizedButPreferred.getCode())
                .contains(validCandidate.getCode());
    }

    /** PART 35 - zero valid candidates produces an empty ranking, never an exception. */
    @Test
    void zeroValidCandidatesProducesEmptyRankingNotAnException() {
        AcademicYear year = seedYear("ZEROVALID");
        Division division = divisionRepository.save(new Division(year, "A", 500));
        Batch batch = batchRepository.save(new Batch(division, "A1", 500));
        Subject subject = subjectRepository.save(new Subject(year, "IT-SC-SUB-ZEROVALID", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-ZEROVALID", "Faculty ZEROVALID", null, null));
        AcademicTerm term = seedTerm("ZEROVAL");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        seedLab("ZEROVALID", 70, seedLabType("ZEROVALID"));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        assertThat(scoringResult.rankedCandidates()).isEmpty();
        assertThat(scoringResult.validCandidateCount()).isEqualTo(0);
    }

    /** PART 20-27/68 - a less-loaded lab outranks a heavily-loaded one within the term's PUBLISHED schedule version. */
    @Test
    void balancedUtilizationPrefersTheLessLoadedLab() {
        AcademicYear year = seedYear("UTIL");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 40));
        Subject subject = subjectRepository.save(new Subject(year, "IT-SC-SUB-UTIL", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-UTIL", "Faculty UTIL", null, null));
        AcademicTerm term = seedTerm("UTIL");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("UTIL");
        Lab busyLab = seedLab("UTIL-BUSY", 45, type);
        Lab idleLab = seedLab("UTIL-IDLE", 45, type);

        AppUser user = seedUser("it-sc-util@example.edu");
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        scheduleVersionRepository.saveAndFlush(version);
        // Load the busy lab with several other days' sessions (different date, so it never conflicts with the request itself).
        for (int day = 1; day <= 5; day++) {
            Allocation load = Allocation.forBatch(
                    AllocationType.EXTRA, division, batch, subject, faculty, busyLab,
                    LocalDate.of(2026, 3, day), LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.PUBLISHED, version, user);
            allocationRepository.save(load);
        }
        allocationRepository.flush();

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        var busy = scoringResult.rankedCandidates().stream().filter(c -> c.labCode().equals(busyLab.getCode())).findFirst().orElseThrow();
        var idle = scoringResult.rankedCandidates().stream().filter(c -> c.labCode().equals(idleLab.getCode())).findFirst().orElseThrow();
        assertThat(idle.totalScore()).isGreaterThan(busy.totalScore());
    }

    /** PART 49/69 - real seeded-shape BDA ranking: Cloudera-capable labs are scored and ranked by capacity fit. */
    @Test
    void bdaRankingScenarioOrdersCloudateraCapableLabsByCapacityFit() {
        AcademicYear year = seedYear("BDA");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 68));
        Subject bda = subjectRepository.save(new Subject(year, "IT-SC-BDA", "Big Data Analytics"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-SC-FAC-BDA", "Faculty BDA", null, null));
        AcademicTerm term = seedTerm("BDA");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("BDA");
        Software cloudera = softwareRepository.save(new Software("IT-SC-CLOUDERA", "Cloudera"));
        subjectSoftwareRequirementRepository.save(new SubjectSoftwareRequirement(bda, cloudera));

        Lab b301 = seedLab("BDA-B301", 70, type);
        Lab c202 = seedLab("BDA-C202", 72, type);
        labSoftwareRepository.save(new LabSoftware(b301, cloudera, null));
        labSoftwareRepository.save(new LabSoftware(c202, cloudera, null));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), bda.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        CandidateGenerationResult generationResult = candidateGenerator.generate(request);
        ScoringResult scoringResult = scoringEngine.score(generationResult);

        List<String> rankedCodes = scoringResult.rankedCandidates().stream().map(ScoredCandidate::labCode).toList();
        // 70 is a tighter fit against required 68 than 72 is - B-301 must outrank C-202 on capacity fit alone.
        assertThat(rankedCodes.indexOf(b301.getCode())).isLessThan(rankedCodes.indexOf(c202.getCode()));
    }
}
