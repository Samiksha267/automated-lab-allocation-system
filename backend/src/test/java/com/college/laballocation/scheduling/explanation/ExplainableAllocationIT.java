package com.college.laballocation.scheduling.explanation;

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
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareRepository;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.subject.SubjectSoftwareRequirement;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack explainable-allocation tests against a real PostgreSQL instance
 * - environment-blocked on this development machine (same documented
 * Docker/Testcontainers limitation as every other IT class), but written
 * correctly for CI/future environments. Manual Docker verification
 * (docs/11-TESTING-STRATEGY.md) covers what this cannot run here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ExplainableAllocationIT {

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
    private AcademicTermRepository academicTermRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private ExplainableAllocationService explainableAllocationService;

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-EX-PROG-" + suffix, "Explanation Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-EX-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private LabType seedLabType(String suffix) {
        return labTypeRepository.save(new LabType("IT-EX-TYPE-" + suffix, "Test Lab Type " + suffix, null));
    }

    private Lab seedLab(String suffix, int capacity, LabType type) {
        return labRepository.save(new Lab("IT-EX-LAB-" + suffix, "Test Lab", capacity, type, "C", "3", "301"));
    }

    /** PART 30/31 - BDA-shaped scenario: a Cloudera-capable, preferred-type lab is recommended; the non-Cloudera lab is rejected with its real reason. */
    @Test
    void bdaRecommendationSelectsTopRankedValidLabAndExplainsTheRejectedOne() {
        AcademicYear year = seedYear("BDA");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 40));
        LabType preferredType = seedLabType("BDA-PREFERRED");
        Subject bda = subjectRepository.save(new Subject(year, "IT-EX-BDA", "Big Data Analytics"));
        bda.setLabTypeRequirement(null, preferredType);
        subjectRepository.saveAndFlush(bda);
        Faculty faculty = facultyRepository.save(new Faculty("IT-EX-FAC-BDA", "Faculty BDA", null, null));
        AcademicTerm term = seedTerm("BDA");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, faculty, division, batch, term));
        Software cloudera = softwareRepository.save(new Software("IT-EX-CLOUDERA", "Cloudera"));
        subjectSoftwareRequirementRepository.save(new SubjectSoftwareRequirement(bda, cloudera));

        Lab preferredWithCloudera = seedLab("BDA-MATCH", 45, preferredType);
        Lab otherTypeWithCloudera = seedLab("BDA-OTHERTYPE", 45, seedLabType("BDA-OTHER"));
        Lab preferredWithoutCloudera = seedLab("BDA-NOCLOUD", 45, preferredType);
        labSoftwareRepository.save(new LabSoftware(preferredWithCloudera, cloudera, null));
        labSoftwareRepository.save(new LabSoftware(otherTypeWithCloudera, cloudera, null));
        // preferredWithoutCloudera deliberately has no Cloudera - matches preferred type but must still be rejected.

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), bda.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AllocationRecommendation recommendation = explainableAllocationService.recommend(request);

        assertThat(recommendation.status()).isEqualTo(RecommendationStatus.RECOMMENDED);
        assertThat(recommendation.recommendedCandidate().labCode()).isEqualTo(preferredWithCloudera.getCode());
        var rejected = recommendation.rejectedCandidates().stream()
                .filter(r -> r.labCode().equals(preferredWithoutCloudera.getCode())).findFirst().orElseThrow();
        assertThat(rejected.violations()).extracting(ViolationExplanation::errorCode).contains("SOFTWARE_MISMATCH");
    }

    /** PART 32 - hard-vs-soft: an invalid-but-preferred-type-matching candidate never outranks a valid one. */
    @Test
    void invalidPreferredTypeCandidateNeverOutranksAValidOne() {
        AcademicYear year = seedYear("HARDSOFT");
        Division division = divisionRepository.save(new Division(year, "A", 68));
        Batch batch = batchRepository.save(new Batch(division, "A1", 68));
        LabType preferredType = seedLabType("HARDSOFT-PREFERRED");
        Subject subject = subjectRepository.save(new Subject(year, "IT-EX-SUB-HS", "Test Subject"));
        subject.setLabTypeRequirement(null, preferredType);
        subjectRepository.saveAndFlush(subject);
        Faculty faculty = facultyRepository.save(new Faculty("IT-EX-FAC-HS", "Faculty HS", null, null));
        AcademicTerm term = seedTerm("HARDSFT");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        Lab undersizedButPreferred = seedLab("HS-SMALL", 40, preferredType);
        Lab validCandidate = seedLab("HS-VALID", 70, preferredType);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AllocationRecommendation recommendation = explainableAllocationService.recommend(request);

        assertThat(recommendation.recommendedCandidate().labCode()).isEqualTo(validCandidate.getCode());
        assertThat(recommendation.rejectedCandidates()).extracting(RejectedCandidateExplanation::labCode)
                .contains(undersizedButPreferred.getCode());
    }

    /** PART 20 - zero valid candidates is a normal result, never an exception. */
    @Test
    void zeroValidCandidatesProducesNoValidCandidateStatus() {
        AcademicYear year = seedYear("ZERO");
        Division division = divisionRepository.save(new Division(year, "A", 500));
        Batch batch = batchRepository.save(new Batch(division, "A1", 500));
        Subject subject = subjectRepository.save(new Subject(year, "IT-EX-SUB-ZERO", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-EX-FAC-ZERO", "Faculty ZERO", null, null));
        AcademicTerm term = seedTerm("ZERO");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        seedLab("ZERO", 70, seedLabType("ZERO"));

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AllocationRecommendation recommendation = explainableAllocationService.recommend(request);

        assertThat(recommendation.status()).isEqualTo(RecommendationStatus.NO_VALID_CANDIDATE);
        assertThat(recommendation.recommendedCandidate()).isNull();
        assertThat(recommendation.rejectionSummary().rejectedCount()).isGreaterThan(0);
    }

    /** PART 57 - a recommendation must never persist an Allocation row. */
    @Test
    void recommendationDoesNotChangeAllocationRowCount() {
        AcademicYear year = seedYear("PERSIST");
        Division division = divisionRepository.save(new Division(year, "A", 40));
        Batch batch = batchRepository.save(new Batch(division, "A1", 40));
        Subject subject = subjectRepository.save(new Subject(year, "IT-EX-SUB-PERSIST", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-EX-FAC-PERSIST", "Faculty PERSIST", null, null));
        AcademicTerm term = seedTerm("PERSIST");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        seedLab("PERSIST", 45, seedLabType("PERSIST"));

        long before = allocationRepository.count();

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        explainableAllocationService.recommend(request);

        long after = allocationRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
