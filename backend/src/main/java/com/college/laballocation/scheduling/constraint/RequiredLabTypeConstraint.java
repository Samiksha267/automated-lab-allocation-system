package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-10 - Required Lab Type. {@code subject.requiredLabType == null OR
 * lab.labType == requiredLabType} (docs/06-CONSTRAINTS.md). Reads
 * {@code context.subject().requiredLabTypeId()} only - {@code preferredLabTypeId}
 * is never consulted here; it is a Phase 11 scoring signal, never a hard
 * rejection (PART 33/34 of the Phase 9 brief).
 */
@Component
public class RequiredLabTypeConstraint implements SchedulingConstraint {

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_10_REQUIRED_LAB_TYPE;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        Long requiredLabTypeId = context.subject().requiredLabTypeId();
        if (requiredLabTypeId == null) {
            return ConstraintResult.pass(id());
        }
        if (!requiredLabTypeId.equals(candidate.lab().labTypeId())) {
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "LAB_TYPE_MISMATCH",
                            "Lab " + candidate.lab().code() + " type (" + candidate.lab().labTypeCode()
                                    + ") does not match the subject's required lab type.",
                            "LAB",
                            candidate.lab().code(),
                            Map.of(
                                    "requiredLabTypeId", requiredLabTypeId,
                                    "candidateLabTypeId", candidate.lab().labTypeId(),
                                    "candidateLabTypeCode", candidate.lab().labTypeCode())));
        }
        return ConstraintResult.pass(id());
    }
}
