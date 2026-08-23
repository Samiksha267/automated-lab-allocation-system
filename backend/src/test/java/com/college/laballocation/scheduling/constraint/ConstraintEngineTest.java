package com.college.laballocation.scheduling.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyAssignmentResolutionService;
import com.college.laballocation.faculty.FacultyAvailabilityService;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.SchedulingTimeMapper;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.academic.CrOwnershipService;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import com.college.laballocation.subject.SubjectEquipmentRequirementRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Engine-level tests - deterministic ordering, all-constraints-evaluated
 * behavior, and the two headline demo scenarios (PART 50/59/87/88 of the
 * Phase 9 brief). Individual constraints' own logic is unit-tested in their
 * dedicated test classes; this class proves the engine wires and reports
 * them correctly together.
 */
@ExtendWith(MockitoExtension.class)
class ConstraintEngineTest {

    @Mock
    private FacultyAvailabilityService facultyAvailabilityService;

    @Mock
    private SubjectSoftwareRequirementRepository softwareRequirementRepository;

    @Mock
    private SubjectEquipmentRequirementRepository equipmentRequirementRepository;

    @Mock
    private CrOwnershipService crOwnershipService;

    @Mock
    private FacultyAssignmentResolutionService facultyAssignmentResolutionService;

    private final SchedulingTimeMapper timeMapper = new SchedulingTimeMapper("Asia/Kolkata");

    private List<SchedulingConstraint> allConstraints;

    @BeforeEach
    void setUp() {
        allConstraints = new ArrayList<>(List.of(
                new LabConflictConstraint(),
                new FacultyConflictConstraint(),
                new FacultyAvailabilityConstraint(facultyAvailabilityService),
                new BatchConflictConstraint(),
                new DivisionWideConflictConstraint(),
                new LabAvailabilityConstraint(timeMapper),
                new CapacityConstraint(),
                new RequiredSoftwareConstraint(softwareRequirementRepository),
                new RequiredEquipmentConstraint(equipmentRequirementRepository),
                new RequiredLabTypeConstraint(),
                new CrAuthorizationConstraint(crOwnershipService),
                new AcademicRelationshipConstraint(facultyAssignmentResolutionService)));
        // Shuffle registration order deterministically to prove the engine's own
        // sort is what produces the stable evaluation order, not injection order.
        Collections.shuffle(allConstraints, new java.util.Random(42));
    }

    private SubjectFacultyAssignment assignment(String subjectCode, Faculty faculty, Division division, Batch batch, AcademicTerm term) {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Subject subject = new Subject(year, subjectCode, subjectCode);
        return new SubjectFacultyAssignment(subject, faculty, division, batch, term);
    }

    @Test
    void resultsAreReturnedInTheDocumentedDeterministicOrderRegardlessOfInjectionOrder() {
        when(softwareRequirementRepository.findBySubjectIdOrderBySoftware_Code(3L)).thenReturn(List.of());
        when(equipmentRequirementRepository.findBySubjectIdOrderByEquipment_Code(3L)).thenReturn(List.of());
        when(facultyAvailabilityService.isAvailable(4L, 5L, LocalDate.of(2026, 8, 24).getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .thenReturn(true);
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(3L, 1L, 2L, 5L)).thenReturn(Optional.empty());

        ConstraintEngine engine = new ConstraintEngine(allConstraints);
        SchedulingContext context = simpleBatchContext();
        CandidateAllocation candidate = new CandidateAllocation(context, simpleLab());

        ConstraintEvaluation evaluation = engine.evaluate(context, candidate);

        List<HardConstraintId> ids = evaluation.results().stream().map(ConstraintResult::constraintId).toList();
        assertThat(ids)
                .containsExactly(
                        HardConstraintId.HC_12_ACADEMIC_RELATIONSHIP,
                        HardConstraintId.HC_07_CAPACITY,
                        HardConstraintId.HC_08_REQUIRED_SOFTWARE,
                        HardConstraintId.HC_09_REQUIRED_EQUIPMENT,
                        HardConstraintId.HC_10_REQUIRED_LAB_TYPE,
                        HardConstraintId.HC_03_FACULTY_AVAILABILITY,
                        HardConstraintId.HC_06_LAB_AVAILABILITY,
                        HardConstraintId.HC_01_LAB_CONFLICT,
                        HardConstraintId.HC_02_FACULTY_CONFLICT,
                        HardConstraintId.HC_04_BATCH_CONFLICT,
                        HardConstraintId.HC_05_DIVISION_CONFLICT,
                        HardConstraintId.HC_11_CR_AUTHORIZATION);
    }

    /** PART 50/87 - A1 (BDA, Faculty BDA, Lab B-301) already exists; A2 (CNS, Faculty CNS, different lab) must be individually valid. */
    @Test
    void a1AndA2DifferentBatchesOfTheSameDivisionSimultaneouslyIsValid() {
        // A1 exists as a BATCH allocation for a *different* faculty/batch/lab - it
        // only ever appears in A2's existingDivisionAllocations (division-scoped
        // query returns every row in the division), never in A2's
        // faculty/batch-scoped lists, matching real AllocationQueryService
        // semantics (Phase 8).
        ExistingAllocationSnapshot a1 = new ExistingAllocationSnapshot(
                900L, 700L, "B-301", 40L, 1L, 200L, TargetType.BATCH, LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        when(softwareRequirementRepository.findBySubjectIdOrderBySoftware_Code(3L)).thenReturn(List.of());
        when(equipmentRequirementRepository.findBySubjectIdOrderByEquipment_Code(3L)).thenReturn(List.of());
        when(facultyAvailabilityService.isAvailable(4L, 5L, LocalDate.of(2026, 8, 24).getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .thenReturn(true);
        Faculty facultyCns = new Faculty("FAC-CNS", "Faculty CNS", null, null);
        setId(facultyCns, 4L);
        Division division = new Division(sharedYear(), "A", 68);
        setId(division, 1L);
        Batch batchA2 = new Batch(division, "A2", 23);
        setId(batchA2, 2L);
        AcademicTerm term = new AcademicTerm("2026-27", 5, "Semester 5", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 1));
        term.updateStatus(TermStatus.ACTIVE);
        setId(term, 5L);
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(3L, 1L, 2L, 5L))
                .thenReturn(Optional.of(assignment("CNS", facultyCns, division, batchA2, term)));

        ConstraintEngine engine = new ConstraintEngine(allConstraints);
        SchedulingContext context = new SchedulingContext(
                simpleRequest(),
                new SubjectRef(3L, "CNS", "Computer Networks & Security", 10L, null, null),
                new FacultyRef(4L, "FAC-CNS", "Faculty CNS", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A2", 23, 1L),
                List.of(), // A2's faculty (CNS) has no existing allocations
                List.of(), // A2's own batch has no existing allocations
                List.of(a1)); // A1 shows up here only, as a BATCH row in the same division
        CandidateAllocation candidate = new CandidateAllocation(context, simpleLab());

        ConstraintEvaluation evaluation = engine.evaluate(context, candidate);

        assertThat(evaluation.valid()).isTrue();
        assertThat(evaluation.violations()).isEmpty();
        // Explicitly confirm the individually-named applicable constraints from PART 87.
        assertThat(outcomeOf(evaluation, HardConstraintId.HC_01_LAB_CONFLICT)).isEqualTo(ConstraintOutcome.PASS);
        assertThat(outcomeOf(evaluation, HardConstraintId.HC_02_FACULTY_CONFLICT)).isEqualTo(ConstraintOutcome.PASS);
        assertThat(outcomeOf(evaluation, HardConstraintId.HC_03_FACULTY_AVAILABILITY)).isEqualTo(ConstraintOutcome.PASS);
        assertThat(outcomeOf(evaluation, HardConstraintId.HC_04_BATCH_CONFLICT)).isEqualTo(ConstraintOutcome.PASS);
        assertThat(outcomeOf(evaluation, HardConstraintId.HC_05_DIVISION_CONFLICT)).isEqualTo(ConstraintOutcome.PASS);
    }

    /** PART 59/88 - a candidate simultaneously failing capacity, software, and faculty availability returns all three. */
    @Test
    void multipleSimultaneousFailuresAreAllReportedNotFailFast() {
        when(softwareRequirementRepository.findBySubjectIdOrderBySoftware_Code(3L))
                .thenReturn(List.of(new com.college.laballocation.subject.SubjectSoftwareRequirement(
                        new Subject(sharedYear(), "BDA", "Big Data Analytics"),
                        new com.college.laballocation.lab.Software("CLOUDERA", "Cloudera"))));
        when(equipmentRequirementRepository.findBySubjectIdOrderByEquipment_Code(3L)).thenReturn(List.of());
        when(facultyAvailabilityService.isAvailable(4L, 5L, LocalDate.of(2026, 8, 24).getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .thenReturn(false);
        when(facultyAssignmentResolutionService.resolveForBatchIfPresent(3L, 1L, 2L, 5L)).thenReturn(Optional.empty());

        ConstraintEngine engine = new ConstraintEngine(allConstraints);
        SchedulingContext context = simpleBatchContext();
        LabRef underCapacityNoSoftware = new LabRef(700L, "C-304", true, 10, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
        CandidateAllocation candidate = new CandidateAllocation(context, underCapacityNoSoftware);

        ConstraintEvaluation evaluation = engine.evaluate(context, candidate);

        assertThat(evaluation.valid()).isFalse();
        List<String> failedCodes = evaluation.violations().stream().map(ConstraintViolation::errorCode).toList();
        assertThat(failedCodes).contains("CAPACITY_VIOLATION", "SOFTWARE_MISMATCH", "FACULTY_UNAVAILABLE");
        // HC-12 fails too in this fixture (no assignment resolves) - proving the
        // engine keeps going and reports every applicable failure, not just the
        // three named in the brief's scenario.
        assertThat(evaluation.results()).hasSize(12);
    }

    private ConstraintOutcome outcomeOf(ConstraintEvaluation evaluation, HardConstraintId id) {
        return evaluation.results().stream()
                .filter(r -> r.constraintId() == id)
                .findFirst()
                .orElseThrow()
                .outcome();
    }

    private AcademicYear sharedYear() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        setId(year, 10L);
        return year;
    }

    private SchedulingRequest simpleRequest() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private SchedulingContext simpleBatchContext() {
        return new SchedulingContext(
                simpleRequest(),
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(),
                List.of(),
                List.of());
    }

    private LabRef simpleLab() {
        return new LabRef(700L, "B-301", true, 70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
