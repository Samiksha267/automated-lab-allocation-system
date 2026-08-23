package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.academic.CrAssignment;
import com.college.laballocation.academic.CrOwnershipService;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.user.UserRole;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * HC-11 - CR Authorization. The one hard constraint that is fundamentally an
 * authorization check, not a scheduling-resource check
 * (docs/06-CONSTRAINTS.md).
 *
 * <p><b>Applicability (PART 35/37 of the Phase 9 brief):</b>
 * <ul>
 *   <li>{@link SchedulingRequest#actor() actor} is {@code null} (automated
 *       REGULAR generation, Phase 14, or any internal scheduling context
 *       with no originating user) - {@code NOT_APPLICABLE}. This constraint
 *       never fails a request just because no CR is involved.</li>
 *   <li>{@code actor.role() == LAB_ASSISTANT} - {@code NOT_APPLICABLE}. A
 *       Lab Assistant is not division-scoped; this constraint exists
 *       specifically to bound CR-originated requests, not to gate Lab
 *       Assistant activity (see docs/09-AUTHORIZATION-RBAC.md's Role vs.
 *       Ownership distinction).</li>
 *   <li>{@code actor.role() == CR} - must own the request's division, read
 *       via {@link CrOwnershipService#getCurrentAssignment} (an
 *       {@code Optional}-returning query, never
 *       {@code requireOwnsDivision}'s throwing variant - see the note
 *       below).</li>
 *   <li>{@code actor.role() == STUDENT} - defensively {@code FAIL}; no
 *       functional requirement lets a student originate a scheduling
 *       request, so this branch is currently unreachable in practice but
 *       exists rather than falling through unauthorized.</li>
 * </ul>
 *
 * <p><b>Why the non-throwing query, not {@code requireOwnsDivision}:</b> an
 * earlier version of this class called {@link CrOwnershipService#requireOwnsDivision}
 * and caught its {@code ApiException} family to convert expected authorization
 * failures into a {@code FAIL} result. Manual Docker verification surfaced a
 * real bug: {@code requireOwnsDivision} is itself {@code @Transactional}, so
 * an exception crossing that method boundary marks the *shared* surrounding
 * transaction rollback-only before this constraint's catch block ever runs -
 * catching the exception here did not "un-poison" it, and the enclosing
 * transaction later failed with {@code UnexpectedRollbackException} even
 * though this constraint had already produced a correct, structured
 * {@code FAIL} result. The fix is architectural, not a try/catch tweak: never
 * let an expected-failure path cross a {@code @Transactional} boundary via an
 * exception at all (PART 2 of the Phase 9 brief already required this; this
 * class originally violated it). {@code getCurrentAssignment} never throws.
 */
@Component
public class CrAuthorizationConstraint implements SchedulingConstraint {

    private final CrOwnershipService crOwnershipService;

    public CrAuthorizationConstraint(CrOwnershipService crOwnershipService) {
        this.crOwnershipService = crOwnershipService;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_11_CR_AUTHORIZATION;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        SchedulingActor actor = context.request().actor();
        if (actor == null || actor.role() == UserRole.LAB_ASSISTANT) {
            return ConstraintResult.notApplicable(id());
        }

        if (actor.role() != UserRole.CR) {
            return fail(
                    "FORBIDDEN_DIVISION_ACCESS",
                    "Only a CR or Lab Assistant may originate a scheduling request.",
                    Map.of("actorRole", actor.role().toString()));
        }

        Optional<CrAssignment> assignment = crOwnershipService.getCurrentAssignment(actor.userId());
        if (assignment.isEmpty()) {
            return fail(
                    "CR_ASSIGNMENT_NOT_FOUND",
                    "No active CR assignment found for the current term.",
                    Map.of("actorUserId", actor.userId()));
        }
        if (!assignment.get().getDivision().getId().equals(context.division().id())) {
            return fail(
                    "FORBIDDEN_DIVISION_ACCESS",
                    "You are not assigned to division " + context.division().code() + ".",
                    Map.of("actorUserId", actor.userId(), "divisionId", context.division().id()));
        }
        return ConstraintResult.pass(id());
    }

    private ConstraintResult fail(String errorCode, String message, Map<String, Object> details) {
        return ConstraintResult.fail(id(), new ConstraintViolation(errorCode, message, "DIVISION", null, details));
    }
}
