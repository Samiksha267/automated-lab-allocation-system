package com.college.laballocation.scheduling.automatic;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 25 opt-in performance benchmark for {@link AutomaticSchedulingEngine} (Phase 14's real,
 * unmocked backtracking search/MRV/undo-retry algorithm). Named {@code *Benchmark}, not
 * {@code *Test}, so Maven Surefire's default include pattern (<code>**&#47;*Test.java</code>)
 * never picks it up - an ordinary {@code mvn test} run stays fast; this class runs only via
 * {@code mvn test -Dtest=SchedulerBenchmark}, mirroring how this project already keeps
 * Testcontainers-backed {@code *ApiIT} classes out of the default run.
 *
 * <p><b>What is real vs. what stands in</b> (an intentional, documented benchmark-design choice,
 * not a shortcut): {@code AutomaticSchedulingEngine} itself - the search state, MRV ordering,
 * undo/retry backtracking, node/backtrack/depth counters - is the real, unmodified production
 * class under test. {@link ExplainableAllocationService} is mocked, exactly as
 * {@code AutomaticSchedulingEngineTest} already mocks it, because the real implementation behind
 * it ({@code CandidateGenerator}/{@code ConstraintEngine}) is JPA-repository-backed and requires a
 * live PostgreSQL connection - benchmarked separately, live, against the real Dockerized backend
 * (see docs/16-PERFORMANCE-BENCHMARKS.md, "Candidate Generation" / "Constraint Engine" / "BDA
 * Cloudera" sections). This split cleanly isolates the search algorithm's own overhead (attempts,
 * backtracks, node count) from constraint-evaluation cost, which is a materially different
 * question with a materially different (DB-bound) cost profile.
 */
@ExtendWith(MockitoExtension.class)
class SchedulerBenchmark {

    @Mock
    private ExplainableAllocationService explainableAllocationService;

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 20;

    private AutomaticSchedulingEngine engine(SchedulingSlotPolicy policy, int maxNodes, int maxRequirements, int maxDateRangeDays) {
        ConflictAnalyzer conflictAnalyzer = new ConflictAnalyzer();
        AutomaticSchedulingConfiguration configuration = new AutomaticSchedulingConfiguration(maxNodes, maxRequirements, maxDateRangeDays);
        return new AutomaticSchedulingEngine(explainableAllocationService, new SchedulingSlotProvider(policy), policy, conflictAnalyzer, configuration);
    }

    private static SchedulingSlotPolicy standardPolicy() {
        return new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
    }

    private SessionRequirement requirement(String key, long divisionId, long batchId, long subjectId, long facultyId) {
        return new SessionRequirement(key, AllocationType.EXTRA, TargetType.BATCH, divisionId, batchId, subjectId, facultyId, 5L, null);
    }

    private ExplainedValidCandidate candidate(String labCode, double normalizedScore) {
        long id = labCode.chars().asLongStream().sum();
        return new ExplainedValidCandidate(id, labCode, 1, normalizedScore * 60, 60, normalizedScore, List.of(), List.of());
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

    private AllocationRecommendation noValid(SchedulingRequest req) {
        RejectedCandidateExplanation rejected = new RejectedCandidateExplanation(
                1L, "X", List.of(new ViolationExplanation("FACULTY_UNAVAILABLE", "Faculty unavailable", "x", "LAB", "X", Map.of())));
        return new AllocationRecommendation(
                req, RecommendationStatus.NO_VALID_CANDIDATE, null, List.of(), List.of(rejected), RejectionSummary.from(List.of(rejected)),
                List.of("No valid laboratory satisfies all hard constraints."), 1, 0, 1);
    }

    private boolean isLabOccupied(SchedulingSearchState state, String labCode, SchedulingRequest req) {
        return state.assignments().stream().anyMatch(a -> a.chosenCandidate().labCode().equals(labCode)
                && a.request().allocationDate().equals(req.allocationDate())
                && a.request().startTime().isBefore(req.endTime())
                && req.startTime().isBefore(a.request().endTime()));
    }

    private record Timing(long medianNanos, long p95Nanos, long minNanos, long maxNanos) {
        static Timing of(List<Long> samplesNanos) {
            List<Long> sorted = new ArrayList<>(samplesNanos);
            sorted.sort(Long::compareTo);
            long median = sorted.get(sorted.size() / 2);
            long p95 = sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(sorted.size() * 0.95) - 1));
            return new Timing(median, p95, sorted.get(0), sorted.get(sorted.size() - 1));
        }

        String describe() {
            return String.format(
                    "median=%.2fms p95=%.2fms min=%.2fms max=%.2fms",
                    medianNanos / 1_000_000.0, p95Nanos / 1_000_000.0, minNanos / 1_000_000.0, maxNanos / 1_000_000.0);
        }
    }

    private Timing time(int warmup, int measured, Runnable op) {
        for (int i = 0; i < warmup; i++) {
            op.run();
        }
        List<Long> samples = new ArrayList<>(measured);
        for (int i = 0; i < measured; i++) {
            long start = System.nanoTime();
            op.run();
            samples.add(System.nanoTime() - start);
        }
        return Timing.of(samples);
    }

    /** PART 11 - many equally-good compatible labs for every requirement; zero backtracking expected. */
    @Test
    void easyScenario_manyCompatibleLabs() {
        List<SessionRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requirements.add(requirement("R" + i, 1L, 100L + i, 10L + i, 200L + i));
        }
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            return recommended(req, candidate("LAB-" + req.batchId(), 0.9));
        });
        AutomaticSchedulingEngine engine = engine(standardPolicy(), 2000, 20, 31);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY.plusDays(2));

        AutomaticScheduleResult[] last = new AutomaticScheduleResult[1];
        Timing t = time(WARMUP_RUNS, MEASURED_RUNS, () -> last[0] = engine.schedule(request));

        System.out.println("[BENCHMARK] Scheduler EASY (10 req, many compatible labs): " + t.describe()
                + " status=" + last[0].status() + " nodes=" + last[0].statistics().nodesExplored()
                + " backtracks=" + last[0].statistics().backtracks() + " maxDepth=" + last[0].statistics().maxDepthReached());
    }

    /** PART 12 - only 2 compatible labs shared across every requirement, forcing tighter search but still solvable. */
    @Test
    void constrainedScenario_fewCompatibleLabsSharedAcrossRequirements() {
        List<SessionRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requirements.add(requirement("R" + i, 1L, 100L + i, 10L + i, 200L + i));
        }
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            SchedulingSearchState state = inv.getArgument(1);
            List<ExplainedValidCandidate> valid = new ArrayList<>();
            for (String lab : List.of("SHARED-A", "SHARED-B")) {
                if (!isLabOccupied(state, lab, req)) {
                    valid.add(candidate(lab, lab.equals("SHARED-A") ? 0.9 : 0.7));
                }
            }
            return valid.isEmpty() ? noValid(req) : recommendedMulti(req, valid);
        });
        AutomaticSchedulingEngine engine = engine(standardPolicy(), 2000, 20, 31);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY.plusDays(5));

        AutomaticScheduleResult[] last = new AutomaticScheduleResult[1];
        Timing t = time(WARMUP_RUNS, MEASURED_RUNS, () -> last[0] = engine.schedule(request));

        System.out.println("[BENCHMARK] Scheduler CONSTRAINED (10 req, 2 shared labs): " + t.describe()
                + " status=" + last[0].status() + " nodes=" + last[0].statistics().nodesExplored()
                + " backtracks=" + last[0].statistics().backtracks() + " maxDepth=" + last[0].statistics().maxDepthReached());
    }

    /** PART 13, mandatory - N (X-or-Y)/(X-only) requirement pairs, fixed order (useMrv=false), forcing a real backtrack per pair. */
    @Test
    void backtrackingScenario_forcedGreedyDeadEndRecoversViaBacktracking() {
        SchedulingSlotPolicy singleSlotPolicy = new SchedulingSlotPolicy("09:00", "11:00", 60, "MONDAY", 0, 40, 3, 120);
        int pairs = 5;
        List<SessionRequirement> requirements = new ArrayList<>();
        Map<Long, String> xLabByBatchId = new java.util.HashMap<>();
        Map<Long, Boolean> isXOrYByBatchId = new java.util.HashMap<>();
        for (int i = 0; i < pairs; i++) {
            long aBatchId = 1000L + i * 2;
            long bBatchId = 1000L + i * 2 + 1;
            requirements.add(requirement("R" + i + "a", 1L, aBatchId, 3L + i, 4L + i)); // X-or-Y
            requirements.add(requirement("R" + i + "b", 1L, bBatchId, 6L + i, 7L + i)); // X-only
            String xLab = "X-" + i;
            xLabByBatchId.put(aBatchId, xLab);
            xLabByBatchId.put(bBatchId, xLab);
            isXOrYByBatchId.put(aBatchId, true);
            isXOrYByBatchId.put(bBatchId, false);
        }
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            SchedulingSearchState state = inv.getArgument(1);
            String xLab = xLabByBatchId.get(req.batchId());
            if (Boolean.TRUE.equals(isXOrYByBatchId.get(req.batchId()))) {
                List<ExplainedValidCandidate> valid = new ArrayList<>();
                valid.add(candidate("Y-" + xLab, 0.5));
                if (!isLabOccupied(state, xLab, req)) {
                    valid.add(0, candidate(xLab, 0.9));
                }
                return recommendedMulti(req, valid);
            }
            return isLabOccupied(state, xLab, req) ? noValid(req) : recommended(req, candidate(xLab, 0.9));
        });
        AutomaticSchedulingEngine engine = engine(singleSlotPolicy, 20000, 40, 3);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY);

        AutomaticScheduleResult[] last = new AutomaticScheduleResult[1];
        Timing t = time(WARMUP_RUNS, MEASURED_RUNS, () -> last[0] = engine.schedule(request, false));

        System.out.println("[BENCHMARK] Scheduler BACKTRACKING (" + pairs + " forced-backtrack pairs, fixed order): " + t.describe()
                + " status=" + last[0].status() + " nodes=" + last[0].statistics().nodesExplored()
                + " backtracks=" + last[0].statistics().backtracks() + " maxDepth=" + last[0].statistics().maxDepthReached()
                + " choicesEvaluated=" + last[0].statistics().choicesEvaluated());
    }

    /** PART 14 - every requirement is genuinely infeasible; must report NO_SOLUTION quickly, never hang or falsely report SEARCH_LIMIT_REACHED. */
    @Test
    void unsatisfiableScenario_genuinelyInfeasibleReportsNoSolutionQuickly() {
        List<SessionRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            requirements.add(requirement("R" + i, 1L, 100L + i, 10L + i, 200L + i));
        }
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> noValid(inv.getArgument(0)));
        AutomaticSchedulingEngine engine = engine(standardPolicy(), 2000, 20, 31);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY.plusDays(3));

        AutomaticScheduleResult[] last = new AutomaticScheduleResult[1];
        Timing t = time(WARMUP_RUNS, MEASURED_RUNS, () -> last[0] = engine.schedule(request));

        System.out.println("[BENCHMARK] Scheduler UNSATISFIABLE (5 req, no valid candidate ever): " + t.describe()
                + " status=" + last[0].status() + " searchLimitReached=" + last[0].statistics().searchLimitReached()
                + " nodes=" + last[0].statistics().nodesExplored());
    }

    /** PART 15/16 - scaling workload size; also exercises maxNodes/maxRequirements protections at the top of the range. */
    @Test
    void scalingScenario_requirementCountGrowth() {
        for (int n : new int[] {10, 25, 50, 100}) {
            List<SessionRequirement> requirements = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                requirements.add(requirement("R" + i, 1L, 100L + i, 10L + i, 200L + i));
            }
            when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
                SchedulingRequest req = inv.getArgument(0);
                return recommended(req, candidate("LAB-" + req.batchId(), 0.9));
            });
            AutomaticSchedulingEngine engine = engine(standardPolicy(), 50000, 150, 31);
            AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY.plusDays(2));

            int warmup = n <= 25 ? WARMUP_RUNS : 2;
            int measured = n <= 25 ? MEASURED_RUNS : 10;
            AutomaticScheduleResult[] last = new AutomaticScheduleResult[1];
            Timing t = time(warmup, measured, () -> last[0] = engine.schedule(request));

            System.out.println("[BENCHMARK] Scheduler SCALING n=" + n + " (warmup=" + warmup + " measured=" + measured + "): " + t.describe()
                    + " status=" + last[0].status() + " nodes=" + last[0].statistics().nodesExplored()
                    + " backtracks=" + last[0].statistics().backtracks());
        }
    }

    /** PART 16 - a deliberately tiny maxNodes bound must stop the search and report SEARCH_LIMIT_REACHED, never hang. */
    @Test
    void maxNodesProtection_stopsSearchAndReportsLimitReached() {
        List<SessionRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            requirements.add(requirement("R" + i, 1L, 100L + i, 10L + i, 200L + i));
        }
        when(explainableAllocationService.recommend(any(SchedulingRequest.class), any())).thenAnswer(inv -> {
            SchedulingRequest req = inv.getArgument(0);
            return recommended(req, candidate("X", 0.9));
        });
        AutomaticSchedulingEngine engine = engine(standardPolicy(), 5, 20, 31);
        AutomaticSchedulingRequest request = new AutomaticSchedulingRequest(requirements, MONDAY, MONDAY.plusDays(2));

        long start = System.nanoTime();
        AutomaticScheduleResult result = engine.schedule(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[BENCHMARK] Scheduler maxNodes=5 protection: elapsed=" + elapsedMs + "ms status=" + result.status()
                + " searchLimitReached=" + result.statistics().searchLimitReached() + " nodesExplored=" + result.statistics().nodesExplored());
    }
}
