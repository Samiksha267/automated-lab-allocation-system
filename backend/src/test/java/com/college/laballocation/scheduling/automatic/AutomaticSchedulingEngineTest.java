package com.college.laballocation.scheduling.automatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.alternative.SchedulingSlotPolicy;
import com.college.laballocation.scheduling.alternative.SchedulingSlotProvider;
import com.college.laballocation.scheduling.conflict.ConflictAnalyzer;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.ExplainableAllocationService;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import com.college.laballocation.scheduling.explanation.RejectedCandidateExplanation;
import com.college.laballocation.scheduling.explanation.RejectionSummary;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomaticSchedulingEngineTest {

    @Mock
    private ExplainableAllocationService explainableAllocationService;

    private ConflictAnalyzer conflictAnalyzer;
    private AutomaticSchedulingEngine engine;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    @BeforeEach
    void setUp() {
        conflictAnalyzer = new ConflictAnalyzer();
        SchedulingSlotPolicy policy =
                new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
        AutomaticSchedulingConfiguration configuration = new AutomaticSchedulingConfiguration(2000, 20, 31);
        engine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(policy), policy, conflictAnalyzer, configuration);
    }

    private SessionRequirement requirement(String key, Long divisionId, Long batchId, Long subjectId, Long facultyId) {
        return new SessionRequirement(key, AllocationType.EXTRA, TargetType.BATCH, divisionId, batchId, subjectId, facultyId, 5L, null);
    }

    private ExplainedValidCandidate candidate(String labCode, double normalizedScore) {
        return new ExplainedValidCandidate(labCodeToId(labCode), labCode, 1, normalizedScore * 60, 60, normalizedScore, List.of(), List.of());
    }

    private long labCodeToId(String labCode) {
        return labCode.chars().asLongStream().sum();
    }

    /** Simulates HC-01 (lab conflict) against this mock's own provisional search state - the same overlap check the real constraint performs. */
    private boolean isLabOccupied(SchedulingSearchState state, String labCode, SchedulingRequest req) {
        return state.assignments().stream().anyMatch(a -> a.chosenCandidate().labCode().equals(labCode)
                && a.request().allocationDate().equals(req.allocationDate())
                && a.request().startTime().isBefore(req.endTime())
                && req.startTime().isBefore(a.request().endTime()));
    }

    private AllocationRecommendation recommended(SchedulingRequest req, ExplainedValidCandidate candidate) {
        return new AllocationRecommendation(
                req, RecommendationStatus.RECOMMENDED, candidate, List.of(candidate), List.of(),
                new RejectionSummary(0, Map.of()), List.of("Satisfies all applicable hard constraints."), 1, 1, 0);
    }

    private AllocationRecommendation recommendedMulti(SchedulingRequest req, List<ExplainedValidCandidate> candidates) {
        return new AllocationRecommendation(
                req, RecommendationStatus.RECOMMENDED, candidates.get(0), candidates, List.of(),
                new RejectionSummary(0, Map.of()), List.of("Satisfies all applicable hard constraints."), candidates.size(), candidates.size(), 0);
    }

    private AllocationRecommendation noValid(SchedulingRequest req, String errorCode) {
        RejectedCandidateExplanation rejected = new RejectedCandidateExplanation(
                1L, "X", List.of(new ViolationExplanation(errorCode, errorCode, "x", "LAB", "X", Map.of())));
        return new AllocationRecommendation(
                req, RecommendationStatus.NO_VALID_CANDIDATE, null, List.of(), List.of(rejected), RejectionSummary.from(List.of(rejected)),
                List.of("No valid laboratory satisfies all hard constraints."), 1, 0, 1);
    }

    @Test
    void zeroRequirementsIsCompleteWithEmptyAssignments() {
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(), MONDAY, MONDAY);

        AutomaticScheduleResult result = engine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.assignments()).isEmpty();
        assertThat(result.totalRequirements()).isEqualTo(0);
    }

    @Test
    void oneRequirementBehavesConsistentlyWithSingleRequestPipeline() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        // Only the exact 09:00 Monday slot succeeds.
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            if (req.allocationDate().equals(MONDAY) && req.startTime().equals(java.time.LocalTime.of(9, 0))) {
                return recommended(req, candidate("X", 0.9));
            }
            return noValid(req, "FACULTY_UNAVAILABLE");
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1), MONDAY, MONDAY);
        AutomaticScheduleResult result = engine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.assignments()).hasSize(1);
        assertThat(result.assignments().get(0).chosenCandidate().labCode()).isEqualTo("X");
    }

    @Test
    void greedySuccessProducesNoUnnecessaryBacktracking() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        // Every slot for every requirement succeeds immediately with a distinct lab per requirement key.
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            String lab = req.batchId() == 2L ? "LAB-A" : "LAB-B";
            return recommended(req, candidate(lab, 0.9));
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);
        AutomaticScheduleResult result = engine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.statistics().backtracks()).isEqualTo(0);
    }

    /**
     * The flagship Phase 14 test (PART 56). R1 can use lab X or Y at the shared time; R2 can use
     * only X; scoring prefers X for R1. This test deliberately calls the package-visible
     * {@code useMrv=false} overload: a correctly-implemented adaptive MRV (the production default,
     * separately proven not to backtrack on this exact shape by {@link #mrvSchedulesTheMoreConstrainedRequirementFirstAvoidingBacktracking()})
     * schedules R2 (the more constrained requirement) first, which structurally avoids ever needing
     * to backtrack here. Running with a fixed order (R1 first, as literally described in the brief)
     * isolates and proves the underlying undo/retry mechanism itself.
     */
    @Test
    void greedyFailsWithFixedOrderButBacktrackingRecoversAndSucceeds() {
        // A narrow single-slot policy (09:00-11:00 only) removes any time-based escape route, isolating the
        // scenario to a pure "which lab" decision - exactly the brief's classic R1(X-or-Y)/R2(X-only) shape.
        SchedulingSlotPolicy singleSlotPolicy = new SchedulingSlotPolicy("09:00", "11:00", 60, "MONDAY", 0, 6, 3, 120);
        AutomaticSchedulingConfiguration configuration = new AutomaticSchedulingConfiguration(2000, 20, 31);
        AutomaticSchedulingEngine singleSlotEngine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(singleSlotPolicy), singleSlotPolicy, conflictAnalyzer, configuration);

        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            SchedulingSearchState state = inv.getArgument(1);
            if (req.batchId() == 2L) {
                // R1: X and Y both structurally valid, X scores higher - but X only remains AVAILABLE if not
                // already provisionally taken (real HC-01 lab-conflict semantics, simulated here since this
                // mock stands in for the real constraint engine).
                List<ExplainedValidCandidate> valid = new java.util.ArrayList<>();
                valid.add(candidate("Y", 0.5));
                if (!isLabOccupied(state, "X", req)) {
                    valid.add(0, candidate("X", 0.9));
                }
                return recommendedMulti(req, valid);
            }
            // R2: only lab X satisfies its software requirement - no fallback lab exists.
            if (isLabOccupied(state, "X", req)) {
                return noValid(req, "LAB_CONFLICT");
            }
            return recommended(req, candidate("X", 0.9));
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);
        AutomaticScheduleResult result = singleSlotEngine.schedule(request, false);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.statistics().backtracks()).isGreaterThan(0);
        var r1Assignment = result.assignments().stream().filter(a -> a.requirementKey().equals("R1")).findFirst().orElseThrow();
        var r2Assignment = result.assignments().stream().filter(a -> a.requirementKey().equals("R2")).findFirst().orElseThrow();
        assertThat(r1Assignment.chosenCandidate().labCode()).isEqualTo("Y");
        assertThat(r2Assignment.chosenCandidate().labCode()).isEqualTo("X");
    }

    /** Same shape as the flagship test, but with MRV (the real, production default) enabled - demonstrates MRV avoids the backtrack entirely. */
    @Test
    void mrvSchedulesTheMoreConstrainedRequirementFirstAvoidingBacktracking() {
        SchedulingSlotPolicy singleSlotPolicy = new SchedulingSlotPolicy("09:00", "11:00", 60, "MONDAY", 0, 6, 3, 120);
        AutomaticSchedulingConfiguration configuration = new AutomaticSchedulingConfiguration(2000, 20, 31);
        AutomaticSchedulingEngine singleSlotEngine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(singleSlotPolicy), singleSlotPolicy, conflictAnalyzer, configuration);

        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            SchedulingSearchState state = inv.getArgument(1);
            if (req.batchId() == 2L) {
                List<ExplainedValidCandidate> valid = new java.util.ArrayList<>();
                valid.add(candidate("Y", 0.5));
                if (!isLabOccupied(state, "X", req)) {
                    valid.add(0, candidate("X", 0.9));
                }
                return recommendedMulti(req, valid);
            }
            if (isLabOccupied(state, "X", req)) {
                return noValid(req, "LAB_CONFLICT");
            }
            return recommended(req, candidate("X", 0.9));
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);
        AutomaticScheduleResult result = singleSlotEngine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.statistics().backtracks()).isEqualTo(0);
    }

    @Test
    void searchLimitReachedDiffersFromNoSolution() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        // Every slot is valid for both, with many equally-scored choices to keep the search busy across labs.
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            return recommended(req, candidate("X", 0.9));
        });

        SchedulingSlotPolicy policy =
                new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
        AutomaticSchedulingConfiguration tinyLimit = new AutomaticSchedulingConfiguration(1, 20, 31);
        AutomaticSchedulingEngine limitedEngine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(policy), policy, conflictAnalyzer, tinyLimit);

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);
        AutomaticScheduleResult result = limitedEngine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.SEARCH_LIMIT_REACHED);
        assertThat(result.statistics().searchLimitReached()).isTrue();
    }

    @Test
    void genuinelyInfeasibleRequestReturnsNoSolutionNotSearchLimit() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        // No slot is ever valid for this requirement - genuinely, provably infeasible.
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any()))
                .thenAnswer(inv -> noValid(inv.getArgument(0), "CAPACITY_VIOLATION"));

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1), MONDAY, MONDAY);
        AutomaticScheduleResult result = engine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.NO_SOLUTION);
        assertThat(result.statistics().searchLimitReached()).isFalse();
        assertThat(result.unscheduledRequirements()).hasSize(1);
    }

    @Test
    void deterministicAcrossRepeatedRuns() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            if (req.batchId() == 2L) {
                return recommendedMulti(req, List.of(candidate("X", 0.9), candidate("Y", 0.5)));
            }
            return recommended(req, candidate("X", 0.9));
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);
        AutomaticScheduleResult first = engine.schedule(request);
        AutomaticScheduleResult second = engine.schedule(request);

        assertThat(first.status()).isEqualTo(second.status());
        assertThat(first.assignments()).isEqualTo(second.assignments());
        assertThat(first.statistics()).isEqualTo(second.statistics());
    }

    @Test
    void duplicateRequirementKeysAreRejectedAtConstruction() {
        SessionRequirement r1 = requirement("SAME", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("SAME", 1L, 5L, 6L, 7L);

        assertThatThrownBy(() -> new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void startDateAfterEndDateIsRejected() {
        assertThatThrownBy(() -> new AutomaticSchedulingRequest(List.of(), MONDAY, MONDAY.minusDays(1)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tooManyRequirementsIsRejected() {
        SchedulingSlotPolicy policy =
                new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
        AutomaticSchedulingConfiguration smallLimit = new AutomaticSchedulingConfiguration(2000, 1, 31);
        AutomaticSchedulingEngine limitedEngine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(policy), policy, conflictAnalyzer, smallLimit);
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        SessionRequirement r2 = requirement("R2", 1L, 5L, 6L, 7L);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1, r2), MONDAY, MONDAY);

        assertThatThrownBy(() -> limitedEngine.schedule(request)).isInstanceOf(ApiException.class);
    }

    @Test
    void dateRangeTooLargeIsRejected() {
        SchedulingSlotPolicy policy =
                new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
        AutomaticSchedulingConfiguration smallRange = new AutomaticSchedulingConfiguration(2000, 20, 2);
        AutomaticSchedulingEngine limitedEngine = new AutomaticSchedulingEngine(
                explainableAllocationService, new SchedulingSlotProvider(policy), policy, conflictAnalyzer, smallRange);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(), MONDAY, MONDAY.plusDays(10));

        assertThatThrownBy(() -> limitedEngine.schedule(request)).isInstanceOf(ApiException.class);
    }

    @Test
    void everyGeneratedRequestPreservesTheConfiguredTwoHourDuration() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            assertThat(java.time.Duration.between(req.startTime(), req.endTime())).isEqualTo(java.time.Duration.ofHours(2));
            return noValid(req, "FACULTY_UNAVAILABLE");
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1), MONDAY, MONDAY);
        engine.schedule(request);
    }

    @Test
    void noAssignmentFallsOutsideTheSuppliedDateRange() {
        SessionRequirement r1 = requirement("R1", 1L, 2L, 3L, 4L);
        LocalDate rangeEnd = MONDAY.plusDays(2);
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            assertThat(req.allocationDate()).isBetween(MONDAY, rangeEnd);
            return req.allocationDate().equals(rangeEnd) ? recommended(req, candidate("X", 0.9)) : noValid(req, "FACULTY_UNAVAILABLE");
        });

        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(List.of(r1), MONDAY, rangeEnd);
        AutomaticScheduleResult result = engine.schedule(request);

        assertThat(result.status()).isEqualTo(AutomaticScheduleStatus.COMPLETE);
        assertThat(result.assignments().get(0).request().allocationDate()).isBetween(MONDAY, rangeEnd);
    }
}
