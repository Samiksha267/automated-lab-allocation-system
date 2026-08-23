package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.faculty.FacultyAssignmentResolutionService;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * HC-12 - Academic Relationship Validity. Checked first in the engine's
 * evaluation order (PART 5) since the checks below are foundational - a
 * candidate whose academic relationships are already incoherent is not
 * usefully evaluated against resource-conflict rules.
 *
 * <p>Three sub-checks, evaluated in order, returning on the first failure
 * (an internal-only fail-fast within this one constraint - unrelated to the
 * engine's own all-constraints-evaluated behavior, which is unaffected: every
 * other HC still runs regardless of this one's outcome):
 * <ol>
 *   <li><b>Batch belongs to division</b> ({@code targetType == BATCH} only) -
 *       {@code context.batch().divisionId()} must equal
 *       {@code context.division().id()}. {@code SchedulingContextFactory}
 *       deliberately does not enforce this at context-build time (a
 *       constraint engine needs a structured result, not a thrown
 *       exception, to explain an invalid candidate) - this is the one place
 *       it is actually checked before evaluation.</li>
 *   <li><b>Subject belongs to the request's academic hierarchy</b> -
 *       {@code context.subject().academicYearId()} must equal
 *       {@code context.division().academicYearId()}, ruling out impossible
 *       combinations like a Year-3 CS subject requested for a Year-2 IT
 *       division (PART 39 of the Phase 9 brief) using data the Phase 4
 *       model already carries - no redundant field invented.</li>
 *   <li><b>Faculty matches the authoritative assignment</b> -
 *       {@link FacultyAssignmentResolutionService} (Phase 4) is re-resolved
 *       for the request's subject/division/batch/term; if no assignment
 *       resolves at all, or the resolved faculty differs from
 *       {@link SchedulingRequest#facultyId()}, this fails. The constraint
 *       never silently substitutes the resolved faculty - normalization
 *       happens upstream, before a {@code SchedulingRequest} exists (PART 40).</li>
 * </ol>
 */
@Component
public class AcademicRelationshipConstraint implements SchedulingConstraint {

    private final FacultyAssignmentResolutionService facultyAssignmentResolutionService;

    public AcademicRelationshipConstraint(FacultyAssignmentResolutionService facultyAssignmentResolutionService) {
        this.facultyAssignmentResolutionService = facultyAssignmentResolutionService;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_12_ACADEMIC_RELATIONSHIP;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        SchedulingRequest request = context.request();

        if (request.targetType() == TargetType.BATCH && !context.batch().divisionId().equals(context.division().id())) {
            return fail(
                    "Batch " + context.batch().code() + " does not belong to division " + context.division().code() + ".",
                    Map.of("batchDivisionId", context.batch().divisionId(), "requestedDivisionId", context.division().id()));
        }

        if (!context.subject().academicYearId().equals(context.division().academicYearId())) {
            return fail(
                    "Subject " + context.subject().code() + " does not belong to the same academic year as division "
                            + context.division().code() + ".",
                    Map.of(
                            "subjectAcademicYearId", context.subject().academicYearId(),
                            "divisionAcademicYearId", context.division().academicYearId()));
        }

        Optional<SubjectFacultyAssignment> resolved = request.targetType() == TargetType.BATCH
                ? facultyAssignmentResolutionService.resolveForBatchIfPresent(
                        request.subjectId(), request.divisionId(), request.batchId(), request.academicTermId())
                : facultyAssignmentResolutionService.resolveForDivisionIfPresent(
                        request.subjectId(), request.divisionId(), request.academicTermId());

        if (resolved.isEmpty()) {
            return fail(
                    "No authoritative faculty assignment exists for subject " + context.subject().code() + " / division "
                            + context.division().code() + " / term " + request.academicTermId() + ".",
                    Map.of("subjectId", request.subjectId(), "divisionId", request.divisionId()));
        }

        Long assignedFacultyId = resolved.get().getFaculty().getId();
        if (!assignedFacultyId.equals(request.facultyId())) {
            return fail(
                    "Requested faculty " + context.faculty().employeeCode()
                            + " does not match the authoritative assignment for this subject/division/term.",
                    Map.of("requestedFacultyId", request.facultyId(), "assignedFacultyId", assignedFacultyId));
        }

        return ConstraintResult.pass(id());
    }

    private ConstraintResult fail(String message, Map<String, Object> details) {
        return ConstraintResult.fail(id(), new ConstraintViolation("INVALID_ACADEMIC_RELATIONSHIP", message, "ACADEMIC_RELATIONSHIP", null, details));
    }
}
