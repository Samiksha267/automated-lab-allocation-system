package com.college.laballocation.scheduling.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.constraint.ConstraintEngine;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CandidateGeneratorTest {

    @Mock
    private SchedulingContextFactory schedulingContextFactory;

    @Mock
    private CandidateAllocationFactory candidateAllocationFactory;

    @Mock
    private ConstraintEngine constraintEngine;

    @Mock
    private LabRepository labRepository;

    private CandidateGenerator generator;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private SchedulingRequest request() {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
    }

    private SchedulingContext context() {
        return new SchedulingContext(
                request(),
                new SubjectRef(3L, "BDA", "Big Data Analytics", 10L, null, null),
                new FacultyRef(4L, "FAC-BDA", "Faculty BDA", true),
                new DivisionRef(1L, "A", 68, 10L),
                new BatchRef(2L, "A1", 23, 1L),
                List.of(), List.of(), List.of());
    }

    private Lab lab(String code, Long id) {
        LabType type = new LabType("COMPUTER", "Computer Lab", null);
        setId(type, 20L);
        Lab lab = new Lab(code, code, 70, type, "C", "3", "301");
        setId(lab, id);
        return lab;
    }

    private LabRef labRef(Long id, String code) {
        return new LabRef(id, code, true, 70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    private ConstraintEvaluation passingEvaluation() {
        return ConstraintEvaluation.of(List.of(ConstraintResult.pass(HardConstraintId.HC_07_CAPACITY)));
    }

    private ConstraintEvaluation failingEvaluation(String errorCode) {
        ConstraintViolation violation = new ConstraintViolation(errorCode, "failed", "LAB", null, Map.of());
        return ConstraintEvaluation.of(List.of(ConstraintResult.fail(HardConstraintId.HC_07_CAPACITY, violation)));
    }

    @BeforeEach
    void setUp() {
        generator = new CandidateGenerator(schedulingContextFactory, candidateAllocationFactory, constraintEngine, labRepository);
    }

    @Test
    void contextIsBuiltExactlyOnceRegardlessOfLabCount() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L), lab("C-301", 3L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X")));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(passingEvaluation());

        generator.generate(request());

        verify(schedulingContextFactory, times(1)).build(any(), any());
    }

    @Test
    void allLabsAreEvaluatedNoFirstFitShortCircuit() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L), lab("C-301", 3L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X")));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(passingEvaluation());

        CandidateGenerationResult result = generator.generate(request());

        verify(constraintEngine, times(3)).evaluate(eq(context), any());
        assertThat(result.totalCount()).isEqualTo(3);
    }

    @Test
    void validAndInvalidCandidatesAreSeparatedAndInvalidRetainViolations() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        Lab labA = lab("A-101", 1L);
        Lab labB = lab("B-201", 2L);
        Lab labC = lab("C-301", 3L);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(labA, labB, labC));
        when(candidateAllocationFactory.build(eq(context), eq(1L), any())).thenReturn(new CandidateAllocation(context, labRef(1L, "A-101")));
        when(candidateAllocationFactory.build(eq(context), eq(2L), any())).thenReturn(new CandidateAllocation(context, labRef(2L, "B-201")));
        when(candidateAllocationFactory.build(eq(context), eq(3L), any())).thenReturn(new CandidateAllocation(context, labRef(3L, "C-301")));
        when(constraintEngine.evaluate(eq(context), any())).thenAnswer(inv -> {
            CandidateAllocation candidate = inv.getArgument(1);
            if (candidate.lab().code().equals("A-101")) {
                return passingEvaluation();
            }
            return failingEvaluation("CAPACITY_VIOLATION");
        });

        CandidateGenerationResult result = generator.generate(request());

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.validCount()).isEqualTo(1);
        assertThat(result.invalidCount()).isEqualTo(2);
        assertThat(result.invalidCandidates()).allSatisfy(c -> assertThat(c.violations()).isNotEmpty());
    }

    @Test
    void zeroValidCandidatesIsANormalResultNotAnException() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L), lab("C-301", 3L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X")));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(failingEvaluation("CAPACITY_VIOLATION"));

        CandidateGenerationResult result = generator.generate(request());

        assertThat(result.validCandidates()).isEmpty();
        assertThat(result.invalidCount()).isEqualTo(3);
    }

    @Test
    void allValidCandidatesStillProducesNoWinner() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L), lab("C-301", 3L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X")));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(passingEvaluation());

        CandidateGenerationResult result = generator.generate(request());

        assertThat(result.validCount()).isEqualTo(3);
        assertThat(result.invalidCount()).isEqualTo(0);
        // No score/ranking field exists anywhere on the result or candidates - selecting a
        // "winner" is structurally impossible here, not merely undone.
    }

    @Test
    void deterministicOrderIsRequestedFromTheRepositoryByLabCodeAscending() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X")));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(passingEvaluation());

        generator.generate(request());

        var sortCaptor = org.mockito.ArgumentCaptor.forClass(Sort.class);
        verify(labRepository).findAll(sortCaptor.capture());
        Sort.Order order = sortCaptor.getValue().getOrderFor("code");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void multipleViolationsOnOneCandidateArePreserved() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(lab("A-101", 1L)));
        when(candidateAllocationFactory.build(eq(context), eq(1L), any())).thenReturn(new CandidateAllocation(context, labRef(1L, "A-101")));
        ConstraintViolation capacityViolation = new ConstraintViolation("CAPACITY_VIOLATION", "x", "LAB", null, Map.of());
        ConstraintViolation softwareViolation = new ConstraintViolation("SOFTWARE_MISMATCH", "y", "LAB", null, Map.of());
        ConstraintEvaluation twoFailures = ConstraintEvaluation.of(List.of(
                ConstraintResult.fail(HardConstraintId.HC_07_CAPACITY, capacityViolation),
                ConstraintResult.fail(HardConstraintId.HC_08_REQUIRED_SOFTWARE, softwareViolation)));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(twoFailures);

        CandidateGenerationResult result = generator.generate(request());

        assertThat(result.evaluatedCandidates().get(0).violations()).extracting(ConstraintViolation::errorCode)
                .containsExactlyInAnyOrder("CAPACITY_VIOLATION", "SOFTWARE_MISMATCH");
    }

    @Test
    void noDuplicateLabsAppearInOneGenerationRun() {
        SchedulingContext context = context();
        when(schedulingContextFactory.build(any(), any())).thenReturn(context);
        when(labRepository.findAll(any(Sort.class))).thenReturn(List.of(lab("A-101", 1L), lab("B-201", 2L), lab("C-301", 3L)));
        when(candidateAllocationFactory.build(eq(context), any(), any())).thenAnswer(
                inv -> new CandidateAllocation(context, labRef(inv.getArgument(1), "X" + inv.getArgument(1))));
        when(constraintEngine.evaluate(eq(context), any())).thenReturn(passingEvaluation());

        CandidateGenerationResult result = generator.generate(request());

        List<Long> labIds = result.evaluatedCandidates().stream().map(c -> c.candidate().lab().id()).toList();
        assertThat(labIds).doesNotHaveDuplicates();
    }
}
