package com.college.laballocation.scheduling.alternative;

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
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack conflict-analysis / alternative-suggestion tests against a real
 * PostgreSQL instance - environment-blocked on this development machine
 * (same documented Docker/Testcontainers limitation as every other IT
 * class), but written correctly for CI/future environments. Manual Docker
 * verification (docs/11-TESTING-STRATEGY.md) covers what this cannot run
 * here.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class AlternativeSuggestionIT {

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
    private AlternativeSuggestionService alternativeSuggestionService;

    private AcademicYear seedYear(String suffix) {
        Program program = programRepository.save(new Program("IT-ALT-PROG-" + suffix, "Alternative Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        return academicYearRepository.save(new AcademicYear(stream, 3));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-ALT-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private LabType seedLabType(String suffix) {
        return labTypeRepository.save(new LabType("IT-ALT-TYPE-" + suffix, "Test Lab Type " + suffix, null));
    }

    private Lab seedLab(String suffix, int capacity, LabType type) {
        return labRepository.save(new Lab("IT-ALT-LAB-" + suffix, "Test Lab", capacity, type, "C", "3", "301"));
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private ScheduleVersion seedPublishedVersion(AcademicTerm term, AppUser user) {
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        return scheduleVersionRepository.saveAndFlush(version);
    }

    // 2026-08-24 is a Monday - the fixed reference date used throughout this project's dev seed and tests.
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    /** PART 67 - an occupied lab does not require alternative-time search: another lab at the same time already resolves it via Phase 12. */
    @Test
    void labConflictIsResolvedBySameTimeDifferentLabNeverNeedingAlternativeSearch() {
        AcademicYear year = seedYear("LABCONF");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-ALT-SUB-LC", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-ALT-FAC-LC", "Faculty LC", null, null));
        AcademicTerm term = seedTerm("LABCNF");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        AppUser user = seedUser("it-alt-labconf@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        LabType type = seedLabType("LABCONF");
        Lab occupiedLab = seedLab("LABCONF-OCC", 30, type);
        Lab freeLab = seedLab("LABCONF-FREE", 30, type);

        Allocation existing = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, occupiedLab,
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(existing);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(request);

        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_NOT_NEEDED);
        assertThat(result.originalRecommendation().recommendedCandidate().labCode()).isEqualTo(freeLab.getCode());
        assertThat(result.slotsSearched()).isEqualTo(0);
    }

    /** PART 68/22 - both labs occupied by the same batch/faculty at the requested time forces a real alternative-time search. */
    @Test
    void batchConflictAcrossEveryLabTriggersAlternativeTimeSearch() {
        AcademicYear year = seedYear("BATCHCONF");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-ALT-SUB-BC", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-ALT-FAC-BC", "Faculty BC", null, null));
        AcademicTerm term = seedTerm("BATCHC");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        AppUser user = seedUser("it-alt-batchconf@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        LabType type = seedLabType("BATCHCONF");
        Lab labA = seedLab("BATCHCONF-A", 30, type);
        seedLab("BATCHCONF-B", 30, type);

        // Batch A1 already has a session 09:00-11:00 (on labA) - every candidate lab for a NEW request at the
        // same time fails BATCH_CONFLICT uniformly, since it's checked against the batch's own existing
        // allocations regardless of which lab the new candidate proposes (see BatchConflictConstraint).
        Allocation existing = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, labA,
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(existing);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(request);

        assertThat(result.originalRecommendation().status()).isEqualTo(
                com.college.laballocation.scheduling.explanation.RecommendationStatus.NO_VALID_CANDIDATE);
        assertThat(result.conflictAnalysis().alternativeTimeSearchWorthwhile()).isTrue();
        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_FOUND);
        assertThat(result.suggestions()).isNotEmpty();
        // Different batches (A1 vs a hypothetical A2) at the same time never incorrectly trigger this - see the
        // A1/A2 test below - this scenario is specifically the SAME batch double-booked, which does require a new time.
        assertThat(result.suggestions().get(0).startTime()).isNotEqualTo(LocalTime.of(9, 0));
    }

    /** PART 69 - a faculty unavailable at the requested time, but available later the same day, yields a real alternative time. */
    @Test
    void facultyUnavailableAtRequestedTimeButAvailableLaterYieldsAlternativeTime() {
        AcademicYear year = seedYear("FACAVAIL");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-ALT-SUB-FA", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-ALT-FAC-FA", "Faculty FA", null, null));
        AcademicTerm term = seedTerm("FACAVL");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        // Faculty is available only 11:00-19:00 - the requested 09:00-11:00 falls entirely outside it.
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(19, 0)));
        LabType type = seedLabType("FACAVAIL");
        seedLab("FACAVAIL-A", 30, type);

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(request);

        assertThat(result.originalRecommendation().status()).isEqualTo(
                com.college.laballocation.scheduling.explanation.RecommendationStatus.NO_VALID_CANDIDATE);
        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_FOUND);
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).startTime()).isAfterOrEqualTo(LocalTime.of(11, 0));
    }

    /** PART 72 - every lab too small for the requested strength: no lab is structurally viable, so alternative-time search is skipped entirely. */
    @Test
    void structuralImpossibilitySkipsAlternativeTimeSearchEntirely() {
        AcademicYear year = seedYear("STRUCT");
        Division division = divisionRepository.save(new Division(year, "A", 500));
        Batch batch = batchRepository.save(new Batch(division, "A1", 500));
        Subject subject = subjectRepository.save(new Subject(year, "IT-ALT-SUB-STRUCT", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-ALT-FAC-STRUCT", "Faculty STRUCT", null, null));
        AcademicTerm term = seedTerm("STRUCT");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        seedLab("STRUCT-A", 70, seedLabType("STRUCT")); // capacity 70 << required 500, every day/time

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(request);

        assertThat(result.conflictAnalysis().alternativeTimeSearchWorthwhile()).isFalse();
        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.NO_ALTERNATIVE_FOUND);
        assertThat(result.slotsSearched()).isEqualTo(0);
    }

    /** PART 71 - A1 (BDA) and A2 (CNS) at the same time, different batches of the same division: A2 must still receive a real recommendation. */
    @Test
    void a1AndA2SimultaneousSessionsBothReceiveIndependentRecommendations() {
        AcademicYear year = seedYear("A1A2");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batchA1 = batchRepository.save(new Batch(division, "A1", 30));
        Batch batchA2 = batchRepository.save(new Batch(division, "A2", 30));
        Subject bda = subjectRepository.save(new Subject(year, "IT-ALT-BDA", "Big Data Analytics"));
        Subject cns = subjectRepository.save(new Subject(year, "IT-ALT-CNS", "Computer Networks & Security"));
        Faculty facultyBda = facultyRepository.save(new Faculty("IT-ALT-FAC-BDA", "Faculty BDA", null, null));
        Faculty facultyCns = facultyRepository.save(new Faculty("IT-ALT-FAC-CNS", "Faculty CNS", null, null));
        AcademicTerm term = seedTerm("A1A2");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(bda, facultyBda, division, batchA1, term));
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(cns, facultyCns, division, batchA2, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyBda, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        facultyAvailabilityRepository.save(new FacultyAvailability(facultyCns, term, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)));
        AppUser user = seedUser("it-alt-a1a2@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        LabType type = seedLabType("A1A2");
        Lab labA1 = seedLab("A1A2-A1LAB", 30, type);
        seedLab("A1A2-A2LAB", 30, type);

        Allocation a1Allocation = Allocation.forBatch(
                AllocationType.EXTRA, division, batchA1, bda, facultyBda, labA1,
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(a1Allocation);

        SchedulingRequest a2Request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batchA2.getId(), cns.getId(), facultyCns.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);

        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(a2Request);

        // A2 must NOT be blocked by A1's existing session - no false DIVISION_CONFLICT, and a same-time
        // recommendation should already exist (a different lab, same time), needing no alternative search at all.
        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_NOT_NEEDED);
        assertThat(result.originalRecommendation().status()).isEqualTo(
                com.college.laballocation.scheduling.explanation.RecommendationStatus.RECOMMENDED);
    }

    /** PART 74 - neither a recommendation nor an alternative search may persist anything. */
    @Test
    void alternativeSearchNeverChangesAllocationRowCount() {
        AcademicYear year = seedYear("PERSIST");
        Division division = divisionRepository.save(new Division(year, "A", 30));
        Batch batch = batchRepository.save(new Batch(division, "A1", 30));
        Subject subject = subjectRepository.save(new Subject(year, "IT-ALT-SUB-PERSIST", "Test Subject"));
        Faculty faculty = facultyRepository.save(new Faculty("IT-ALT-FAC-PERSIST", "Faculty PERSIST", null, null));
        AcademicTerm term = seedTerm("PERSIS");
        subjectFacultyAssignmentRepository.save(new SubjectFacultyAssignment(subject, faculty, division, batch, term));
        facultyAvailabilityRepository.save(new FacultyAvailability(faculty, term, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(19, 0)));
        seedLab("PERSIST-A", 30, seedLabType("PERSIST"));

        long before = allocationRepository.count();

        SchedulingRequest request = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, division.getId(), batch.getId(), subject.getId(), faculty.getId(), term.getId(),
                MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        alternativeSuggestionService.findAlternatives(request);

        long after = allocationRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
