package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.subject.SubjectSoftwareRequirement;
import com.college.laballocation.subject.SubjectSoftwareRequirementRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-08 - Required Software. {@code R_s ⊆ L_ℓ} (docs/06-CONSTRAINTS.md):
 * every software row the subject requires must be present in the candidate
 * lab's installed set - ALL-required semantics, never ANY. An empty
 * requirement set always passes (PART 29 of the Phase 9 brief).
 *
 * <p>Queries {@link SubjectSoftwareRequirementRepository} directly rather
 * than reading it from {@code SchedulingContext} - subject requirements were
 * deliberately excluded from the context's preloaded data in Phase 8
 * (see {@code SchedulingRefs} javadoc), since only this one constraint
 * needs them.
 */
@Component
public class RequiredSoftwareConstraint implements SchedulingConstraint {

    private final SubjectSoftwareRequirementRepository requirementRepository;

    public RequiredSoftwareConstraint(SubjectSoftwareRequirementRepository requirementRepository) {
        this.requirementRepository = requirementRepository;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_08_REQUIRED_SOFTWARE;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        List<SubjectSoftwareRequirement> required =
                requirementRepository.findBySubjectIdOrderBySoftware_Code(context.request().subjectId());

        List<String> missing = required.stream()
                .map(r -> r.getSoftware().getCode())
                .filter(code -> !candidate.lab().softwareCodes().contains(code))
                .toList();

        if (!missing.isEmpty()) {
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "SOFTWARE_MISMATCH",
                            "Lab " + candidate.lab().code() + " does not provide required software: " + String.join(", ", missing) + ".",
                            "SOFTWARE",
                            String.join(",", missing),
                            Map.of("missingSoftware", missing)));
        }
        return ConstraintResult.pass(id());
    }
}
