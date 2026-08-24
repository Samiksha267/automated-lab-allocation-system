package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.alternative.SchedulingSlotPolicy;
import com.college.laballocation.scheduling.alternative.SchedulingSlotProvider;
import com.college.laballocation.scheduling.alternative.TimeSlot;
import com.college.laballocation.scheduling.conflict.ConflictAnalysis;
import com.college.laballocation.scheduling.conflict.ConflictAnalyzer;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.ExplainableAllocationService;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedules multiple {@link SessionRequirement}s together via bounded
 * depth-first backtracking with Minimum-Remaining-Values (MRV) requirement
 * ordering (PART 1/7/17/18 of the Phase 14 brief) - the central problem
 * this phase exists to solve: a locally-best candidate for one requirement
 * can prevent another from being scheduled at all, so a plain greedy
 * "choose the best candidate for each request in turn" is not sufficient.
 *
 * <p><b>No duplicated constraint/scoring logic</b> (PART 2/4/6/21): every
 * candidate slot is validated and scored by one more real call to
 * {@code ExplainableAllocationService.recommend(request, searchState)} -
 * the exact unmodified Phase 10/11/12 pipeline, extended (Phase 14,
 * additively) to also see this search's own provisional decisions via
 * {@link SchedulingSearchState}. This class contains no
 * capacity/software/lab-conflict/faculty-conflict logic of its own.
 *
 * <p><b>Advisory, no persistence</b> (PART 42/45): runs entirely inside a
 * read-only transaction; no {@code Allocation} row is created and no
 * {@code ScheduleVersion} is published. A returned schedule is a proposal
 * against a snapshot, exactly like Phase 12/13's results.
 *
 * <p>See docs/05-SCHEDULING-ENGINE.md "Automatic Scheduling" for the full
 * algorithm, complexity, and a worked greedy-failure/backtracking-success
 * example.
 */
@Service
@Transactional(readOnly = true)
public class AutomaticSchedulingEngine {

    private static final Logger log = LoggerFactory.getLogger(AutomaticSchedulingEngine.class);

    private static final Comparator<SchedulingChoice> CHOICE_ORDER = Comparator
            .comparingDouble(SchedulingChoice::normalizedScore)
            .reversed()
            .thenComparing(c -> c.request().allocationDate())
            .thenComparing(c -> c.request().startTime())
            .thenComparing(SchedulingChoice::labCode);

    private final ExplainableAllocationService explainableAllocationService;
    private final SchedulingSlotProvider schedulingSlotProvider;
    private final SchedulingSlotPolicy schedulingSlotPolicy;
    private final ConflictAnalyzer conflictAnalyzer;
    private final AutomaticSchedulingConfiguration configuration;

    public AutomaticSchedulingEngine(
            ExplainableAllocationService explainableAllocationService,
            SchedulingSlotProvider schedulingSlotProvider,
            SchedulingSlotPolicy schedulingSlotPolicy,
            ConflictAnalyzer conflictAnalyzer,
            AutomaticSchedulingConfiguration configuration) {
        this.explainableAllocationService = explainableAllocationService;
        this.schedulingSlotProvider = schedulingSlotProvider;
        this.schedulingSlotPolicy = schedulingSlotPolicy;
        this.conflictAnalyzer = conflictAnalyzer;
        this.configuration = configuration;
    }

    public AutomaticScheduleResult schedule(AutomaticSchedulingRequest request) {
        return schedule(request, true);
    }

    /**
     * @param useMrv whether to pick the most-constrained unassigned
     *     requirement first at every search node (the default and only
     *     mode the public single-argument overload uses). Exposed
     *     package-visibly so {@code AutomaticSchedulingEngineTest} can
     *     verify the underlying backtracking/undo mechanism in isolation
     *     using a fixed (input-order) requirement sequence
     *     ({@code useMrv=false}) - a correctly-implemented adaptive MRV
     *     structurally avoids needing to backtrack on the simple
     *     two-requirement "R1: X or Y, R2: X only" shape, since MRV
     *     schedules R2 (the more constrained one) first by construction.
     *     Production code always uses the public overload.
     */
    AutomaticScheduleResult schedule(AutomaticSchedulingRequest request, boolean useMrv) {
        validateBounds(request);

        if (request.requirements().isEmpty()) {
            return new AutomaticScheduleResult(
                    AutomaticScheduleStatus.COMPLETE, List.of(), List.of(), 0, 0, 0, SearchStatistics.empty());
        }

        List<TimeSlot> slots = schedulingSlotProvider.generateSlotsInRange(
                request.startDate(), request.endDate(), schedulingSlotPolicy.sessionDuration());

        SearchBookkeeping bookkeeping = new SearchBookkeeping(configuration.maxNodes());
        SearchOutcome outcome = solve(request.requirements(), SchedulingSearchState.empty(), slots, bookkeeping, useMrv);

        AutomaticScheduleResult result = buildResult(request, slots, outcome, bookkeeping);
        if (log.isDebugEnabled()) {
            log.debug(
                    "Automatic scheduling: requirements={} status={} nodesExplored={} backtracks={} slotsInRange={}",
                    request.requirements().size(), result.status(), bookkeeping.nodesExplored, bookkeeping.backtracks, slots.size());
        }
        return result;
    }

    private void validateBounds(AutomaticSchedulingRequest request) {
        if (request.requirements().size() > configuration.maxRequirements()) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Too many requirements: " + request.requirements().size() + " exceeds the configured maximum of "
                            + configuration.maxRequirements() + ".");
        }
        long rangeDays = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (rangeDays > configuration.maxDateRangeDays()) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Date range too large: " + rangeDays + " days exceeds the configured maximum of "
                            + configuration.maxDateRangeDays() + ".");
        }
    }

    /**
     * The recursive backtracking step - one call = one node (PART 39).
     * Returns success with the completed state the moment every requirement
     * is assigned; otherwise tries every choice for the chosen requirement
     * in score order, recursing into each, and undoing (a "backtrack",
     * PART 40) whenever the deeper search fails for every alternative.
     */
    private SearchOutcome solve(
            List<SessionRequirement> unassigned, SchedulingSearchState state, List<TimeSlot> slots, SearchBookkeeping bookkeeping, boolean useMrv) {
        bookkeeping.nodesExplored++;
        bookkeeping.observe(state);

        if (bookkeeping.nodesExplored > bookkeeping.maxNodes) {
            bookkeeping.searchLimitReached = true;
            return SearchOutcome.failure();
        }
        if (unassigned.isEmpty()) {
            return SearchOutcome.success(state);
        }

        SessionRequirement next;
        List<SchedulingChoice> choices;
        if (useMrv) {
            SessionRequirement chosen = null;
            List<SchedulingChoice> chosenChoices = null;
            for (SessionRequirement candidate : sortedByKey(unassigned)) {
                List<SchedulingChoice> candidateChoices = computeChoices(candidate, slots, state);
                if (chosenChoices == null || candidateChoices.size() < chosenChoices.size()) {
                    chosen = candidate;
                    chosenChoices = candidateChoices;
                }
            }
            next = chosen;
            choices = chosenChoices;
        } else {
            // Fixed, given order - deliberately bypasses MRV (test-only path, see the package-visible overload's javadoc).
            next = unassigned.get(0);
            choices = computeChoices(next, slots, state);
        }

        List<SessionRequirement> remaining = unassigned.stream().filter(r -> !r.key().equals(next.key())).toList();

        for (SchedulingChoice choice : choices) {
            bookkeeping.choicesEvaluated++;
            SchedulingSearchState nextState = state.with(new PlannedAllocation(next.key(), choice.request(), choice.candidate()));
            SearchOutcome outcome = solve(remaining, nextState, slots, bookkeeping, useMrv);
            if (outcome.success()) {
                return outcome;
            }
            if (bookkeeping.searchLimitReached) {
                return SearchOutcome.failure();
            }
            bookkeeping.backtracks++;
        }
        return SearchOutcome.failure();
    }

    /**
     * Every valid (slot, lab) choice for one requirement, across the whole
     * slot universe - reuses the full Phase 10/11/12 pipeline per slot, so
     * no candidate is ever generated, validated, or scored by this class
     * itself (PART 21/44). Ordered per PART 19: normalized score
     * descending, then earlier date, then earlier time, then lab code.
     *
     * <p>Deliberately keeps <b>every</b> valid lab per slot, not only the
     * best one (unlike Phase 13's alternative-suggestion heuristic, PART
     * 45 of the Phase 13 brief) - Phase 14's backtracking needs the full
     * branching structure (e.g. "this requirement could use lab X or lab Y
     * at the same time") to correctly demonstrate and exercise undo/retry;
     * Phase 13's "best per slot" simplification was specific to that
     * phase's concise-suggestions UX goal, not a universal rule.
     */
    private List<SchedulingChoice> computeChoices(SessionRequirement requirement, List<TimeSlot> slots, SchedulingSearchState state) {
        List<SchedulingChoice> choices = new ArrayList<>();
        for (TimeSlot slot : slots) {
            SchedulingRequest concreteRequest = requirement.toRequest(slot);
            AllocationRecommendation recommendation = explainableAllocationService.recommend(concreteRequest, state);
            if (recommendation.status() == RecommendationStatus.RECOMMENDED) {
                for (ExplainedValidCandidate candidate : recommendation.rankedValidCandidates()) {
                    choices.add(new SchedulingChoice(concreteRequest, candidate));
                }
            }
        }
        choices.sort(CHOICE_ORDER);
        return choices;
    }

    private List<SessionRequirement> sortedByKey(List<SessionRequirement> requirements) {
        return requirements.stream().sorted(Comparator.comparing(SessionRequirement::key)).toList();
    }

    private AutomaticScheduleResult buildResult(
            AutomaticSchedulingRequest request, List<TimeSlot> slots, SearchOutcome outcome, SearchBookkeeping bookkeeping) {
        int total = request.requirements().size();
        SearchStatistics statistics = bookkeeping.toStatistics();

        if (outcome.success()) {
            List<PlannedAllocation> assignments = outcome.state().assignments();
            return new AutomaticScheduleResult(
                    AutomaticScheduleStatus.COMPLETE, assignments, List.of(), total, assignments.size(), 0, statistics);
        }

        SchedulingSearchState bestState = bookkeeping.bestState;
        List<UnscheduledRequirement> unresolved = explainUnscheduled(request, slots, bestState);
        AutomaticScheduleStatus status;
        if (bookkeeping.searchLimitReached) {
            status = AutomaticScheduleStatus.SEARCH_LIMIT_REACHED;
        } else {
            status = bestState.size() == 0 ? AutomaticScheduleStatus.NO_SOLUTION : AutomaticScheduleStatus.PARTIAL;
        }
        return new AutomaticScheduleResult(
                status, bestState.assignments(), unresolved, total, bestState.size(), total - bestState.size(), statistics);
    }

    /**
     * A real, non-fabricated reason per unscheduled requirement (PART 51) -
     * derived from one representative {@code recommend(...)} call (the
     * first slot in the range) against the final search state, reusing
     * {@link ConflictAnalyzer}. Honestly scoped to that one slot's reasons,
     * not claimed to summarize every slot in the range.
     */
    private List<UnscheduledRequirement> explainUnscheduled(
            AutomaticSchedulingRequest request, List<TimeSlot> slots, SchedulingSearchState finalState) {
        Set<String> assignedKeys = new HashSet<>();
        for (PlannedAllocation assignment : finalState.assignments()) {
            assignedKeys.add(assignment.requirementKey());
        }

        List<UnscheduledRequirement> unresolved = new ArrayList<>();
        for (SessionRequirement requirement : request.requirements()) {
            if (assignedKeys.contains(requirement.key())) {
                continue;
            }
            unresolved.add(new UnscheduledRequirement(requirement.key(), explainOne(requirement, slots, finalState)));
        }
        return unresolved;
    }

    private String explainOne(SessionRequirement requirement, List<TimeSlot> slots, SchedulingSearchState finalState) {
        if (slots.isEmpty()) {
            return "No candidate slots exist in the supplied date range for this requirement's working-day/hour policy.";
        }
        TimeSlot representative = slots.get(0);
        SchedulingRequest concreteRequest = requirement.toRequest(representative);
        AllocationRecommendation recommendation = explainableAllocationService.recommend(concreteRequest, finalState);
        if (recommendation.status() == RecommendationStatus.RECOMMENDED) {
            return "A valid slot exists at " + representative.date() + " " + representative.startTime()
                    + ", but this requirement was not reached by the returned assignment.";
        }
        ConflictAnalysis analysis = conflictAnalyzer.analyze(recommendation);
        List<String> mostCommon = analysis.rejectionSummary().mostCommonReasons();
        String reasonPhrase = mostCommon.isEmpty() ? "no valid candidates" : "most common reason: " + String.join(", ", mostCommon);
        return "No valid lab found at representative slot " + representative.date() + " " + representative.startTime() + " (" + reasonPhrase + ").";
    }

    /** Success carries the completed state; failure carries nothing (the search fully unwound). */
    private record SearchOutcome(boolean success, SchedulingSearchState state) {
        static SearchOutcome success(SchedulingSearchState state) {
            return new SearchOutcome(true, state);
        }

        static SearchOutcome failure() {
            return new SearchOutcome(false, null);
        }
    }

    /** Mutable, per-{@code schedule(...)}-call search bookkeeping - never shared across calls, never a Spring bean field. */
    private static final class SearchBookkeeping {
        final int maxNodes;
        int nodesExplored;
        int backtracks;
        int choicesEvaluated;
        boolean searchLimitReached;
        SchedulingSearchState bestState = SchedulingSearchState.empty();

        SearchBookkeeping(int maxNodes) {
            this.maxNodes = maxNodes;
        }

        void observe(SchedulingSearchState state) {
            if (state.size() > bestState.size()) {
                bestState = state;
            }
        }

        SearchStatistics toStatistics() {
            return new SearchStatistics(nodesExplored, backtracks, bestState.size(), choicesEvaluated, searchLimitReached);
        }
    }
}
