package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectRepository;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository/persistence-level Phase 8 tests - environment-blocked on this
 * development machine (same documented Docker/Testcontainers limitation as
 * every other IT class in this project, see docs/13-DEVELOPER-SETUP.md).
 * No controller exists for Allocation/ScheduleVersion yet (PART 31 of the
 * Phase 8 brief), so this is a repository-level test, not an *ApiIT.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class AllocationPersistenceIT {

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

    private Division seedDivision(String suffix) {
        Program program = programRepository.save(new Program("IT-ALLOC-PROG-" + suffix, "Allocation Test Program", 4));
        Stream stream = streamRepository.save(new Stream(program, "CS", "CS"));
        AcademicYear year = academicYearRepository.save(new AcademicYear(stream, 3));
        return divisionRepository.save(new Division(year, "A", 68));
    }

    private AcademicTerm seedTerm(String suffix) {
        AcademicTerm term = academicTermRepository.save(
                new AcademicTerm("IT-ALLOC-YR-" + suffix, 1, "Test Term " + suffix, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
        term.updateStatus(TermStatus.ACTIVE);
        return term;
    }

    private AppUser seedUser(String email) {
        return userRepository.save(new AppUser(email, passwordEncoder.encode("irrelevant-pw1"), UserRole.LAB_ASSISTANT, "Test LA"));
    }

    private Subject seedSubject(Division division, String suffix) {
        return subjectRepository.save(new Subject(division.getAcademicYear(), "IT-ALLOC-SUB-" + suffix, "Allocation Test Subject"));
    }

    private Faculty seedFaculty(String suffix) {
        return facultyRepository.save(new Faculty("IT-ALLOC-FAC-" + suffix, "Test Faculty " + suffix, null, null));
    }

    private Lab seedLab(String suffix) {
        LabType labType = labTypeRepository.save(new LabType("IT-ALLOC-TYPE-" + suffix, "Test Lab Type", null));
        return labRepository.save(new Lab("IT-ALLOC-LAB-" + suffix, "Test Lab", 70, labType, "C", "3", "301"));
    }

    private ScheduleVersion seedPublishedVersion(AcademicTerm term, AppUser user) {
        ScheduleVersion version = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        version.publish(user);
        return version;
    }

    @Test
    void scheduleVersionUniquenessAndCheckConstraintsAreEnforced() {
        AcademicTerm term = seedTerm("SV1");
        AppUser user = seedUser("it-alloc-sv1@example.edu");
        scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));

        assertThatThrownBy(() -> {
                    scheduleVersionRepository.saveAndFlush(new ScheduleVersion(term, 1, "duplicate version number", user));
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void atMostOnePublishedScheduleVersionPerTermIsEnforced() {
        AcademicTerm term = seedTerm("SV2");
        AppUser user = seedUser("it-alloc-sv2@example.edu");
        ScheduleVersion first = scheduleVersionRepository.save(new ScheduleVersion(term, 1, null, user));
        first.publish(user);
        scheduleVersionRepository.flush();

        ScheduleVersion second = scheduleVersionRepository.save(new ScheduleVersion(term, 2, "revision", user));
        second.publish(user);

        assertThatThrownBy(() -> scheduleVersionRepository.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allocationTargetInvariantCheckConstraintRejectsBatchTypeWithNullBatch() {
        Division division = seedDivision("T1");
        AcademicTerm term = seedTerm("T1");
        AppUser user = seedUser("it-alloc-t1@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        Subject subject = seedSubject(division, "T1");
        Faculty faculty = seedFaculty("T1");
        Lab lab = seedLab("T1");

        // Bypasses the application-level factory deliberately, to prove the
        // database CHECK constraint itself rejects the invalid state even if
        // application code had a bug - defense in depth, not redundant testing.
        Allocation invalid = Allocation.forDivision(
                AllocationType.EXTRA, division, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, version, user);
        forceTargetType(invalid, TargetType.BATCH);

        assertThatThrownBy(() -> allocationRepository.saveAndFlush(invalid)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allocationIntervalCheckConstraintRejectsEndBeforeStart() {
        // Application-level validation already rejects this in Allocation's
        // constructor - this test proves the DB CHECK is a real, independent
        // guarantee by writing directly past the entity via a native update.
        Division division = seedDivision("T2");
        AcademicTerm term = seedTerm("T2");
        AppUser user = seedUser("it-alloc-t2@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        Subject subject = seedSubject(division, "T2");
        Faculty faculty = seedFaculty("T2");
        Lab lab = seedLab("T2");

        Allocation allocation = Allocation.forDivision(
                AllocationType.EXTRA, division, subject, faculty, lab,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0),
                AllocationStatus.APPROVED, version, user);
        allocationRepository.saveAndFlush(allocation);

        assertThatThrownBy(() -> forceInvalidInterval(allocation)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeAllocationQueriesReturnExpectedRowsPerResourceAndExcludeCancelled() {
        Division division = seedDivision("T3");
        AcademicTerm term = seedTerm("T3");
        AppUser user = seedUser("it-alloc-t3@example.edu");
        ScheduleVersion version = seedPublishedVersion(term, user);
        Subject subject = seedSubject(division, "T3");
        Faculty faculty = seedFaculty("T3");
        Lab lab = seedLab("T3");
        Lab otherLab = seedLab("T3-OTHER");
        Batch batch = batchRepository.save(new Batch(division, "A1", 23));
        LocalDate date = LocalDate.of(2026, 8, 24);

        Allocation active = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                date, LocalTime.of(9, 0), LocalTime.of(11, 0), AllocationStatus.APPROVED, version, user);
        allocationRepository.save(active);

        Allocation cancelled = Allocation.forBatch(
                AllocationType.EXTRA, division, batch, subject, faculty, lab,
                date, LocalTime.of(14, 0), LocalTime.of(16, 0), AllocationStatus.APPROVED, version, user);
        cancelled.cancel(user, "test cleanup");
        allocationRepository.saveAndFlush(cancelled);

        assertThat(allocationQueryService.findActiveForLab(lab.getId(), date)).hasSize(1);
        assertThat(allocationQueryService.findActiveForLab(otherLab.getId(), date)).isEmpty();
        assertThat(allocationQueryService.findActiveForFaculty(faculty.getId(), date)).hasSize(1);
        assertThat(allocationQueryService.findActiveForBatch(batch.getId(), date)).hasSize(1);
        assertThat(allocationQueryService.findActiveForDivision(division.getId(), date)).hasSize(1);
    }

    private void forceTargetType(Allocation allocation, TargetType targetType) {
        try {
            var field = Allocation.class.getDeclaredField("targetType");
            field.setAccessible(true);
            field.set(allocation, targetType);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void forceInvalidInterval(Allocation allocation) {
        try {
            var startField = Allocation.class.getDeclaredField("startTime");
            var endField = Allocation.class.getDeclaredField("endTime");
            startField.setAccessible(true);
            endField.setAccessible(true);
            startField.set(allocation, LocalTime.of(12, 0));
            endField.set(allocation, LocalTime.of(9, 0));
            allocationRepository.saveAndFlush(allocation);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
