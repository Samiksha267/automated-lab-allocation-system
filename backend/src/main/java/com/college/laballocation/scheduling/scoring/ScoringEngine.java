package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.LabUtilizationService;
import com.college.laballocation.scheduling.generation.CandidateGenerationResult;
import com.college.laballocation.scheduling.generation.EvaluatedCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranks only the already-{@link EvaluatedCandidate#isValid() valid}
 * candidates from a {@link CandidateGenerationResult} (PART 3/4/34 of the
 * Phase 11 brief) - an invalid candidate is never scored, regardless of how
 * favorably it might otherwise rank; hard constraints always override soft
 * scoring. Deliberately reads {@code generationResult.validCandidates()}
 * only, so a caller cannot accidentally pass invalid candidates in.
 *
 * <p>Read-only, stateless between calls, never persists anything (PART 5,
 * PART 38/39) - this only ranks; a future orchestration layer (Phase 12+)
 * decides whether/what to do with the ranking.
 */
@Service
@Transactional(readOnly = true)
public class ScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(ScoringEngine.class);

    private final List<AllocationScorer> scorers;
    private final LabUtilizationService labUtilizationService;

    public ScoringEngine(List<AllocationScorer> scorers, LabUtilizationService labUtilizationService) {
        this.scorers = List.copyOf(scorers);
        this.labUtilizationService = labUtilizationService;
    }

    public ScoringResult score(CandidateGenerationResult generationResult) {
        long startNanos = System.nanoTime();
        List<EvaluatedCandidate> validCandidates = generationResult.validCandidates();

        if (validCandidates.isEmpty()) {
            return new ScoringResult(generationResult.request(), List.of(), 0, enabledFactorIds());
        }

        ScoringContext scoringContext = buildScoringContext(validCandidates);

        List<ScoredCandidate> scored = new ArrayList<>(validCandidates.size());
        for (EvaluatedCandidate evaluatedCandidate : validCandidates) {
            scored.add(scoreOne(scoringContext, evaluatedCandidate));
        }

        List<ScoredCandidate> ranked = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::normalizedScore)
                        .reversed()
                        .thenComparing(ScoredCandidate::labCode))
                .toList();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Scored {} valid candidate(s) for subjectId={} divisionId={} batchId={} date={} in {} us",
                    ranked.size(),
                    generationResult.request().subjectId(),
                    generationResult.request().divisionId(),
                    generationResult.request().batchId(),
                    generationResult.request().allocationDate(),
                    (System.nanoTime() - startNanos) / 1000);
        }

        return new ScoringResult(generationResult.request(), ranked, validCandidates.size(), enabledFactorIds());
    }

    private ScoredCandidate scoreOne(ScoringContext scoringContext, EvaluatedCandidate evaluatedCandidate) {
        CandidateAllocation candidate = evaluatedCandidate.candidate();
        List<ScoreContribution> contributions = new ArrayList<>(scorers.size());
        double total = 0;
        double applicableMax = 0;
        for (AllocationScorer scorer : scorers) {
            ScoreContribution contribution = scorer.score(scoringContext, candidate);
            contributions.add(contribution);
            if (contribution.applicability() == ScoreApplicability.APPLIED) {
                total += contribution.pointsAwarded();
                applicableMax += contribution.maxPoints();
            }
        }
        return new ScoredCandidate(evaluatedCandidate, contributions, total, applicableMax);
    }

    /**
     * Loads Balanced Utilization's per-lab scheduled-minutes data exactly
     * once for the whole candidate set (PART 55 - one grouped query, not one
     * per lab), then derives the min/max load across that same set so every
     * scorer sees an identical, already-computed relative baseline.
     */
    private ScoringContext buildScoringContext(List<EvaluatedCandidate> validCandidates) {
        var schedulingContext = validCandidates.get(0).candidate().context();
        List<Long> labIds =
                validCandidates.stream().map(c -> c.candidate().lab().id()).toList();

        Optional<Map<Long, Long>> loadByLab =
                labUtilizationService.scheduledMinutesByLab(schedulingContext.request().academicTermId(), labIds);
        if (loadByLab.isEmpty()) {
            return new ScoringContext(schedulingContext, false, Map.of(), null, null);
        }

        Map<Long, Long> load = loadByLab.get();
        long minLoad = labIds.stream().mapToLong(id -> load.getOrDefault(id, 0L)).min().orElse(0L);
        long maxLoad = labIds.stream().mapToLong(id -> load.getOrDefault(id, 0L)).max().orElse(0L);

        return new ScoringContext(schedulingContext, true, load, minLoad, maxLoad);
    }

    private List<ScoringFactorId> enabledFactorIds() {
        return scorers.stream().map(AllocationScorer::id).toList();
    }
}
