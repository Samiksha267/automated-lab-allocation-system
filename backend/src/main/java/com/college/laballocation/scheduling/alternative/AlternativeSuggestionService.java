package com.college.laballocation.scheduling.alternative;

import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.conflict.ConflictAnalysis;
import com.college.laballocation.scheduling.conflict.ConflictAnalyzer;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.ExplainableAllocationService;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the originally requested date/time cannot produce a valid
 * recommendation, searches for a bounded, deterministic set of alternative
 * time slots that can (PART 1/24 of the Phase 13 brief). Depends only on
 * {@link ExplainableAllocationService} (Phase 12) - never the other
 * direction, so the dependency graph stays acyclic
 * ({@code ExplainableAllocationService} has no knowledge this class exists,
 * PART 31/32).
 *
 * <p><b>No duplicate validation logic</b> (PART 2): every alternative slot
 * is validated by re-running the exact same {@code CandidateGenerator}/
 * {@code ConstraintEngine}/{@code ScoringEngine} pipeline (via a fresh call
 * to {@code recommend(...)}) - this class never itself decides "is this lab
 * free," only "which slots are worth asking that question about" and "in
 * what order to present the answers."
 *
 * <p>Read-only and advisory (PART 2, PART 79): nothing is persisted,
 * reserved, or guaranteed to remain available - a returned suggestion is a
 * snapshot, exactly like a Phase 12 recommendation. Phase 16 owns
 * commit-time revalidation.
 */
@Service
@Transactional(readOnly = true)
public class AlternativeSuggestionService {

    private final ExplainableAllocationService explainableAllocationService;
    private final ConflictAnalyzer conflictAnalyzer;
    private final SchedulingSlotProvider schedulingSlotProvider;
    private final SchedulingSlotPolicy schedulingSlotPolicy;

    public AlternativeSuggestionService(
            ExplainableAllocationService explainableAllocationService,
            ConflictAnalyzer conflictAnalyzer,
            SchedulingSlotProvider schedulingSlotProvider,
            SchedulingSlotPolicy schedulingSlotPolicy) {
        this.explainableAllocationService = explainableAllocationService;
        this.conflictAnalyzer = conflictAnalyzer;
        this.schedulingSlotProvider = schedulingSlotProvider;
        this.schedulingSlotPolicy = schedulingSlotPolicy;
    }

    public AlternativeSearchResult findAlternatives(SchedulingRequest request) {
        AllocationRecommendation original = explainableAllocationService.recommend(request);
        ConflictAnalysis conflictAnalysis = conflictAnalyzer.analyze(original);

        if (original.status() == RecommendationStatus.RECOMMENDED) {
            return new AlternativeSearchResult(
                    request, original, conflictAnalysis, List.of(), AlternativeSearchStatus.ALTERNATIVES_NOT_NEEDED, 0);
        }

        if (!conflictAnalysis.alternativeTimeSearchWorthwhile()) {
            // Every rejected candidate has at least one STRUCTURAL failure - changing the time cannot help any of them (PART 19/20).
            return new AlternativeSearchResult(
                    request, original, conflictAnalysis, List.of(), AlternativeSearchStatus.NO_ALTERNATIVE_FOUND, 0);
        }

        List<CandidateSlot> candidateSlots = schedulingSlotProvider.generateCandidateSlots(request);
        List<AlternativeSuggestion> found = new ArrayList<>();
        int searched = 0;
        for (CandidateSlot slot : candidateSlots) {
            searched++;
            SchedulingRequest alternativeRequest = withNewSlot(request, slot);
            AllocationRecommendation alternativeRecommendation = explainableAllocationService.recommend(alternativeRequest);
            if (alternativeRecommendation.status() == RecommendationStatus.RECOMMENDED) {
                found.add(AlternativeSuggestion.of(slot, alternativeRequest, alternativeRecommendation.recommendedCandidate()));
            }
        }

        List<AlternativeSuggestion> ranked = found.stream()
                .sorted(RANKING_ORDER)
                .limit(schedulingSlotPolicy.maxAlternativeSuggestions())
                .toList();

        AlternativeSearchStatus status =
                ranked.isEmpty() ? AlternativeSearchStatus.NO_ALTERNATIVE_FOUND : AlternativeSearchStatus.ALTERNATIVES_FOUND;
        return new AlternativeSearchResult(request, original, conflictAnalysis, ranked, status, searched);
    }

    /**
     * Deterministic, lexicographic ranking (PART 13/29 - never one merged
     * "magic" score): (1) minimum day displacement, (2) minimum time-of-day
     * displacement from the original request, (3) Phase 11's own normalized
     * score descending, (4) lab code ascending as the final tie-break.
     */
    private static final Comparator<AlternativeSuggestion> RANKING_ORDER = Comparator.<AlternativeSuggestion>comparingInt(
                    AlternativeSuggestion::dayOffset)
            .thenComparingInt(AlternativeSuggestion::minutesFromOriginalStart)
            .thenComparing(Comparator.comparingDouble((AlternativeSuggestion s) -> s.recommendedCandidate().normalizedScore())
                    .reversed())
            .thenComparing(s -> s.recommendedCandidate().labCode());

    /** Preserves subject/faculty/division/batch/actor/term/duration exactly - only the date/start/end change (PART 40-43). */
    private SchedulingRequest withNewSlot(SchedulingRequest original, CandidateSlot slot) {
        return new SchedulingRequest(
                original.allocationType(),
                original.targetType(),
                original.divisionId(),
                original.batchId(),
                original.subjectId(),
                original.facultyId(),
                original.academicTermId(),
                slot.date(),
                slot.startTime(),
                slot.endTime(),
                original.actor());
    }
}
