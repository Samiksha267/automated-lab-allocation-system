package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.subjectRef;
import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequiredLabTypeConstraintTest {

    private final RequiredLabTypeConstraint constraint = new RequiredLabTypeConstraint();

    private SchedulingContext contextWithRequiredLabType(Long requiredLabTypeId) {
        var request = batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0));
        return new SchedulingContext(
                request,
                subjectRef(requiredLabTypeId),
                SchedulingFixtures.facultyRef(),
                SchedulingFixtures.divisionRef(),
                SchedulingFixtures.batchRef(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void idIsHc10() {
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_10_REQUIRED_LAB_TYPE);
    }

    @Test
    void passesWhenSubjectHasNoRequiredLabType() {
        SchedulingContext context = contextWithRequiredLabType(null);
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab(70, 999L, "OTHER", Set.of(), Map.of(), List.of(), List.of())));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void passesWhenCandidateLabTypeMatchesRequired() {
        SchedulingContext context = contextWithRequiredLabType(20L);
        LabRef lab = lab(70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenCandidateLabTypeDoesNotMatchRequired() {
        SchedulingContext context = contextWithRequiredLabType(20L);
        LabRef lab = lab(70, 999L, "OTHER", Set.of(), Map.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context, lab));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("LAB_TYPE_MISMATCH");
    }

    /** PART 34 - mandatory test: preferred-only lab type must never gate HC-10. */
    @Test
    void preferredOnlyLabTypeNeverFailsHc10() {
        // requiredLabTypeId is null here (subjectRef(requiredLabTypeId) only ever
        // sets the *required* field in this fixture) - preferredLabType has no
        // representation in SubjectRef at all, precisely because HC-10 must never
        // read it (PART 33). A candidate lab type mismatching some hypothetical
        // "preferred" type still passes.
        SchedulingContext context = contextWithRequiredLabType(null);
        LabRef mismatchedType = lab(70, 999L, "OTHER", Set.of(), Map.of(), List.of(), List.of());
        ConstraintResult result = constraint.evaluate(context, candidate(context, mismatchedType));
        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }
}
