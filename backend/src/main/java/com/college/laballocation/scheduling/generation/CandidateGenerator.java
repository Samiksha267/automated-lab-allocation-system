package com.college.laballocation.scheduling.generation;

import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.constraint.ConstraintEngine;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Given one {@link SchedulingRequest}, produces every evaluated candidate
 * lab - answering "which labs should be considered?" (this class) as a
 * deliberately separate question from "is a considered lab valid?" (Phase 9's
 * {@link ConstraintEngine}) or "which valid lab is preferable?" (Phase 11).
 *
 * <p><b>Pipeline:</b> {@code SchedulingContextFactory.build} runs exactly
 * once per request (never per lab); every administratively-known lab is then
 * turned into a {@link CandidateAllocation} via {@link CandidateAllocationFactory}
 * and evaluated through the real, unmodified Phase 9 {@link ConstraintEngine}
 * - no second, parallel validation path exists (PART 4 of the Phase 10 brief).
 *
 * <p><b>No first-fit short-circuit</b> (PART 19): every lab is evaluated,
 * even after an earlier one is found valid - the naive "return the first
 * available lab" behavior this whole project exists to avoid.
 *
 * <p><b>Candidate universe - Design B, PART 9:</b> generation does not
 * exclude {@code active = false} labs up front. It queries every lab and
 * lets {@link com.college.laballocation.scheduling.constraint.LabAvailabilityConstraint}
 * (HC-06) reject inactive ones structurally, so a rejected candidate still
 * carries a real, explainable violation ("this lab is permanently
 * inactive") rather than silently vanishing from the considered set. At
 * ~15 labs this costs nothing meaningful; it is not a general performance
 * decision.
 *
 * <p><b>No constraint duplication</b> (PART 10/24): this class contains no
 * capacity/software/availability conditionals of its own. The one exception
 * is deterministic ordering (lab code ascending, PART 18) - a presentation
 * concern, never a validity or ranking decision.
 */
@Service
@Transactional(readOnly = true)
public class CandidateGenerator {

    private static final Logger log = LoggerFactory.getLogger(CandidateGenerator.class);

    private final SchedulingContextFactory schedulingContextFactory;
    private final CandidateAllocationFactory candidateAllocationFactory;
    private final ConstraintEngine constraintEngine;
    private final LabRepository labRepository;

    public CandidateGenerator(
            SchedulingContextFactory schedulingContextFactory,
            CandidateAllocationFactory candidateAllocationFactory,
            ConstraintEngine constraintEngine,
            LabRepository labRepository) {
        this.schedulingContextFactory = schedulingContextFactory;
        this.candidateAllocationFactory = candidateAllocationFactory;
        this.constraintEngine = constraintEngine;
        this.labRepository = labRepository;
    }

    public CandidateGenerationResult generate(SchedulingRequest request) {
        long startNanos = System.nanoTime();

        // Built exactly once per request - reused for every candidate below,
        // never rebuilt per lab (PART 5/15/45 of the Phase 10 brief).
        SchedulingContext context = schedulingContextFactory.build(request);

        List<Lab> labs = labRepository.findAll(Sort.by(Sort.Direction.ASC, "code"));

        List<EvaluatedCandidate> evaluated = new ArrayList<>(labs.size());
        for (Lab lab : labs) {
            CandidateAllocation candidate = candidateAllocationFactory.build(context, lab.getId());
            ConstraintEvaluation evaluation = constraintEngine.evaluate(context, candidate);
            evaluated.add(new EvaluatedCandidate(candidate, evaluation));
        }

        CandidateGenerationResult result = new CandidateGenerationResult(request, evaluated);

        if (log.isDebugEnabled()) {
            log.debug(
                    "Candidate generation: subjectId={} divisionId={} batchId={} date={} labsConsidered={} valid={} invalid={} durationMs={}",
                    request.subjectId(), request.divisionId(), request.batchId(), request.allocationDate(),
                    result.totalCount(), result.validCount(), result.invalidCount(),
                    (System.nanoTime() - startNanos) / 1_000_000);
        }

        return result;
    }
}
