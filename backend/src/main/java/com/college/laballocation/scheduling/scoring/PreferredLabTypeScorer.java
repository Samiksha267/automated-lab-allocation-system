package com.college.laballocation.scheduling.scoring;

import com.college.laballocation.scheduling.CandidateAllocation;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rewards a lab matching the subject's <b>soft</b> {@code preferredLabTypeId}
 * - deliberately distinct from HC-10's hard {@code requiredLabTypeId}, which
 * this scorer never reads (PART 18 of the Phase 11 brief; a subject enforces
 * the two are mutually exclusive, so a candidate never faces both at once).
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>No preference recorded ({@code preferredLabTypeId == null}) -
 *       {@link ScoreApplicability#NOT_APPLICABLE}; this factor's weight is
 *       excluded from the candidate's applicable maximum entirely (PART 17),
 *       never a fabricated full or zero score.</li>
 *   <li>Preference recorded and the candidate's lab type matches - full
 *       weight.</li>
 *   <li>Preference recorded and the candidate's lab type does not match -
 *       zero points, but the candidate remains valid; only its score is
 *       affected (PART 18).</li>
 * </ul>
 */
@Component
public class PreferredLabTypeScorer implements AllocationScorer {

    private final ScoringConfiguration configuration;

    public PreferredLabTypeScorer(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ScoringFactorId id() {
        return ScoringFactorId.PREFERRED_LAB_TYPE;
    }

    @Override
    public ScoreContribution score(ScoringContext scoringContext, CandidateAllocation candidate) {
        Long preferredLabTypeId = scoringContext.schedulingContext().subject().preferredLabTypeId();
        if (preferredLabTypeId == null) {
            return ScoreContribution.notApplicable(
                    id(), "Subject has no preferred lab type recorded; this factor does not apply.");
        }

        double weight = configuration.preferredLabTypeWeight();
        boolean matches = preferredLabTypeId.equals(candidate.lab().labTypeId());
        double points = matches ? weight : 0;

        return new ScoreContribution(
                id(),
                ScoreApplicability.APPLIED,
                points,
                weight,
                matches
                        ? "Lab type (" + candidate.lab().labTypeCode() + ") matches the subject's preferred lab type."
                        : "Lab type (" + candidate.lab().labTypeCode() + ") does not match the subject's preferred lab type.",
                Map.of(
                        "preferredLabTypeId", preferredLabTypeId,
                        "candidateLabTypeId", candidate.lab().labTypeId(),
                        "candidateLabTypeCode", candidate.lab().labTypeCode(),
                        "matched", matches));
    }
}
