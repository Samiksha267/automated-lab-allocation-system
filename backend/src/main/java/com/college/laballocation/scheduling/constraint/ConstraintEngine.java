package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates one {@link CandidateAllocation} against every registered
 * {@link SchedulingConstraint} and returns a {@link ConstraintEvaluation}.
 *
 * <p><b>Evaluates all constraints, never fails fast</b> (PART 3/46 of the
 * Phase 9 brief) - a candidate that fails capacity, software, and faculty
 * availability simultaneously should report all three, not just whichever
 * ran first. This supports explainability (Phase 12) and alternative
 * ranking (Phase 13) without requiring re-evaluation later.
 *
 * <p>Constraints are Spring-discovered ({@code List<SchedulingConstraint>}
 * injected) and then sorted into {@link #EVALUATION_ORDER} for deterministic
 * test/log/explanation output - this order is a presentation concern only;
 * no constraint's correctness depends on when another constraint runs
 * (PART 5). {@link HardConstraintId}'s own declared enum order is left
 * untouched (HC-01..HC-12) - {@code EVALUATION_ORDER} is a separate,
 * explicit list so adding this doesn't require renumbering established IDs.
 */
@Component
public class ConstraintEngine {

    private static final Logger log = LoggerFactory.getLogger(ConstraintEngine.class);

    /** PART 5's recommended order: cheap/foundational checks first, resource-conflict checks last. */
    private static final List<HardConstraintId> EVALUATION_ORDER = List.of(
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

    private final List<SchedulingConstraint> constraints;

    public ConstraintEngine(List<SchedulingConstraint> constraints) {
        this.constraints = constraints.stream()
                .sorted(Comparator.comparingInt(c -> EVALUATION_ORDER.indexOf(c.id())))
                .toList();
    }

    // At least two constraints (HC-08, HC-09) independently query a repository and then
    // read a lazy association off the result (e.g. SubjectSoftwareRequirement.getSoftware())
    // - safe only while one session spans the whole evaluate() call. Every production caller
    // happens to already run inside its own @Transactional service method, which is why this
    // was never observed there; a raw, non-transactional caller (as several *IT tests
    // legitimately are, exercising this engine directly) would otherwise hit
    // LazyInitializationException the moment such a constraint runs.
    @Transactional(readOnly = true)
    public ConstraintEvaluation evaluate(SchedulingContext context, CandidateAllocation candidate) {
        List<ConstraintResult> results = new ArrayList<>(constraints.size());
        for (SchedulingConstraint constraint : constraints) {
            long startNanos = System.nanoTime();
            ConstraintResult result = constraint.evaluate(context, candidate);
            results.add(result);
            if (log.isDebugEnabled()) {
                log.debug(
                        "{} lab={} -> {} ({} us)",
                        constraint.id(), candidate.lab().code(), result.outcome(), (System.nanoTime() - startNanos) / 1000);
            }
        }
        return ConstraintEvaluation.of(results);
    }
}
