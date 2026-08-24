package com.college.laballocation.scheduling.extra;

import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.CrAssignment;
import com.college.laballocation.academic.CrOwnershipService;
import com.college.laballocation.academic.Division;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.faculty.FacultyAssignmentResolutionService;
import com.college.laballocation.faculty.FacultyService;
import com.college.laballocation.faculty.SubjectFacultyAssignment;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabService;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.CandidateAllocationFactory;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.ScheduleVersionStatus;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingContextFactory;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.alternative.AlternativeSearchResult;
import com.college.laballocation.scheduling.alternative.AlternativeSuggestionService;
import com.college.laballocation.scheduling.constraint.ConstraintEngine;
import com.college.laballocation.scheduling.constraint.ConstraintEvaluation;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabAllocationResponse;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabBookingRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabCancelRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabSearchRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabSearchResponse;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectService;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The production CR-facing EXTRA-lab workflow (Phase 15): search available
 * labs, book one, view your division's EXTRA history, cancel one you booked.
 * Orchestration only - every actual scheduling decision (who may schedule
 * what, which lab is valid, which lab is best) is delegated to the
 * already-existing Phase 4/9/10/11/12/13 services; this class contains no
 * capacity/software/conflict logic of its own (PART 8 of the phase brief).
 *
 * <p><b>Ownership is never client-supplied</b> (PART 2): every request here
 * resolves {@code divisionId}/{@code academicTermId} from the authenticated
 * user's current {@link CrAssignment} via {@link CrOwnershipService}, and
 * {@code facultyId} from {@link FacultyAssignmentResolutionService} - neither
 * ever comes from the request body. This is the single most important
 * property of this class; every method below depends on it.
 *
 * <p><b>Search vs. book</b> (PART 24): {@link #search} is an advisory
 * snapshot - it persists nothing and reuses {@link AlternativeSuggestionService}
 * end-to-end, including its own reuse of {@link com.college.laballocation.scheduling.explanation.ExplainableAllocationService}.
 * {@link #book} is the one authoritative, transactional decision point - it
 * never trusts a prior search result and always re-runs the real
 * {@link ConstraintEngine} against the selected lab, current data, right
 * before persisting (PART 11/12).
 */
@Service
@Transactional(readOnly = true)
public class ExtraLabService {

    private final CrOwnershipService crOwnershipService;
    private final FacultyAssignmentResolutionService facultyAssignmentResolutionService;
    private final AlternativeSuggestionService alternativeSuggestionService;
    private final SchedulingContextFactory schedulingContextFactory;
    private final CandidateAllocationFactory candidateAllocationFactory;
    private final ConstraintEngine constraintEngine;
    private final ScheduleVersionRepository scheduleVersionRepository;
    private final AllocationRepository allocationRepository;
    private final BatchService batchService;
    private final SubjectService subjectService;
    private final FacultyService facultyService;
    private final LabService labService;

    public ExtraLabService(
            CrOwnershipService crOwnershipService,
            FacultyAssignmentResolutionService facultyAssignmentResolutionService,
            AlternativeSuggestionService alternativeSuggestionService,
            SchedulingContextFactory schedulingContextFactory,
            CandidateAllocationFactory candidateAllocationFactory,
            ConstraintEngine constraintEngine,
            ScheduleVersionRepository scheduleVersionRepository,
            AllocationRepository allocationRepository,
            BatchService batchService,
            SubjectService subjectService,
            FacultyService facultyService,
            LabService labService) {
        this.crOwnershipService = crOwnershipService;
        this.facultyAssignmentResolutionService = facultyAssignmentResolutionService;
        this.alternativeSuggestionService = alternativeSuggestionService;
        this.schedulingContextFactory = schedulingContextFactory;
        this.candidateAllocationFactory = candidateAllocationFactory;
        this.constraintEngine = constraintEngine;
        this.scheduleVersionRepository = scheduleVersionRepository;
        this.allocationRepository = allocationRepository;
        this.batchService = batchService;
        this.subjectService = subjectService;
        this.facultyService = facultyService;
        this.labService = labService;
    }

    /** Advisory only - reuses the Phase 12/13 pipeline end-to-end; persists nothing (PART 9). */
    public ExtraLabSearchResponse search(Long userId, ExtraLabSearchRequest request) {
        CrAssignment assignment = requireCrAssignment(userId);
        SchedulingRequest schedulingRequest = buildSchedulingRequest(
                userId, assignment, request.subjectId(), request.targetType(), request.batchId(),
                request.allocationDate(), request.startTime(), request.endTime());
        AlternativeSearchResult result = alternativeSuggestionService.findAlternatives(schedulingRequest);
        return ExtraLabSearchResponse.from(result);
    }

    /**
     * The one authoritative decision point. Never trusts a prior search
     * result (PART 11/57): rebuilds the request server-side, re-resolves the
     * currently PUBLISHED schedule version, and re-evaluates the selected
     * lab through the real {@link ConstraintEngine} against current data
     * before ever writing a row.
     *
     * <p>Only the selected candidate is (re)validated, not all ~15 labs
     * (PART 13) - {@link ConstraintEngine} evaluates one {@link CandidateAllocation}
     * at a time regardless of caller, so this is not a reduced-correctness
     * path, only a narrower one: correctness comes from evaluating the exact
     * candidate about to be persisted, not from how many other candidates
     * happen to also be evaluated alongside it.
     */
    @Transactional
    public ExtraLabAllocationResponse book(Long userId, ExtraLabBookingRequest request) {
        CrAssignment assignment = requireCrAssignment(userId);
        SchedulingRequest schedulingRequest = buildSchedulingRequest(
                userId, assignment, request.subjectId(), request.targetType(), request.batchId(),
                request.allocationDate(), request.startTime(), request.endTime());

        ScheduleVersion publishedVersion = scheduleVersionRepository
                .findByAcademicTermIdAndStatus(assignment.getAcademicTerm().getId(), ScheduleVersionStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(
                        "NO_PUBLISHED_SCHEDULE", HttpStatus.CONFLICT,
                        "No published schedule exists yet for this academic term; EXTRA sessions cannot be booked."));

        SchedulingContext context = schedulingContextFactory.build(schedulingRequest);
        CandidateAllocation candidate = candidateAllocationFactory.build(context, request.labId());
        ConstraintEvaluation evaluation = constraintEngine.evaluate(context, candidate);
        if (!evaluation.valid()) {
            throw allocationConflict(evaluation);
        }

        AppUser createdBy = assignment.getUser();
        Division division = assignment.getDivision();
        Subject subject = subjectService.getEntity(request.subjectId());
        Faculty faculty = facultyService.getEntity(schedulingRequest.facultyId());
        Lab lab = labService.getEntity(request.labId());

        Allocation allocation = request.targetType() == TargetType.BATCH
                ? Allocation.forBatch(
                        AllocationType.EXTRA, division, batchService.getEntity(request.batchId()), subject, faculty, lab,
                        request.allocationDate(), request.startTime(), request.endTime(),
                        AllocationStatus.PUBLISHED, publishedVersion, createdBy)
                : Allocation.forDivision(
                        AllocationType.EXTRA, division, subject, faculty, lab,
                        request.allocationDate(), request.startTime(), request.endTime(),
                        AllocationStatus.PUBLISHED, publishedVersion, createdBy);

        Allocation saved = allocationRepository.save(allocation);
        return ExtraLabAllocationResponse.from(saved);
    }

    /**
     * Ownership is re-checked here independently of {@link #book} (PART 14/28)
     * - a CR cancelling allocation X must currently own X's division, not the
     * division they owned when X was created (a CR could in principle be
     * reassigned between booking and cancelling). Only {@code EXTRA}
     * allocations are cancellable through this workflow (PART 29); a second
     * cancel attempt is rejected by {@link Allocation#cancel} itself
     * (PART 30 - idempotency is the existing Phase 8 lifecycle's decision,
     * not reinvented here).
     */
    @Transactional
    public ExtraLabAllocationResponse cancel(Long userId, Long allocationId, ExtraLabCancelRequest request) {
        Allocation allocation = allocationRepository
                .findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EXTRA_ALLOCATION_NOT_FOUND", "Extra allocation not found: " + allocationId));
        if (allocation.getAllocationType() != AllocationType.EXTRA) {
            throw new ApiException(
                    "EXTRA_ALLOCATION_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "Only EXTRA allocations can be cancelled through this workflow.");
        }
        CrAssignment assignment = crOwnershipService.requireOwnsDivision(userId, allocation.getDivision().getId());

        allocation.cancel(assignment.getUser(), normalizeReason(request));
        return ExtraLabAllocationResponse.from(allocation);
    }

    /** The CR's own division's EXTRA allocations, active and cancelled alike (PART 33) - division derived server-side, never from a request parameter. */
    public List<ExtraLabAllocationResponse> mine(Long userId) {
        CrAssignment assignment = requireCrAssignment(userId);
        return allocationRepository
                .findByDivisionIdAndAllocationTypeOrderByCreatedAtDesc(assignment.getDivision().getId(), AllocationType.EXTRA)
                .stream()
                .map(ExtraLabAllocationResponse::from)
                .toList();
    }

    /**
     * Lab-Assistant-only visibility into CR EXTRA activity (PART 34/69),
     * scoped to one required {@code academicTermId} plus optional
     * {@code divisionId}/{@code status} filters - deliberately no analytics
     * beyond what {@link Allocation}'s own audit fields
     * ({@code createdBy}/{@code createdAt}/{@code cancelledBy}/{@code cancelledAt})
     * already carry (PART 34: a full {@code audit_log} subsystem is a later
     * phase, not pulled forward here).
     */
    public List<ExtraLabAllocationResponse> activity(Long academicTermId, Long divisionId, AllocationStatus status) {
        return allocationRepository
                .findByAllocationTypeAndScheduleVersion_AcademicTerm_IdOrderByCreatedAtDesc(AllocationType.EXTRA, academicTermId)
                .stream()
                .filter(a -> divisionId == null || a.getDivision().getId().equals(divisionId))
                .filter(a -> status == null || a.getStatus() == status)
                .map(ExtraLabAllocationResponse::from)
                .toList();
    }

    private CrAssignment requireCrAssignment(Long userId) {
        return crOwnershipService
                .getCurrentAssignment(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CR_ASSIGNMENT_NOT_FOUND", "No active CR assignment found for the current term."));
    }

    /**
     * The one place {@code divisionId}/{@code academicTermId}/{@code facultyId}
     * are resolved for this whole workflow (PART 2/5/6) - {@code divisionId}
     * and {@code academicTermId} come from {@code assignment} (never the
     * request), {@code facultyId} from {@link FacultyAssignmentResolutionService}'s
     * exact-batch-then-division-fallback resolution (Phase 4 semantics,
     * unchanged). A mismatched/foreign {@code batchId} is not rejected here -
     * it is naturally caught by HC-12 ({@code AcademicRelationshipConstraint})
     * once the full pipeline runs, since that constraint already checks
     * "does this batch belong to this division" as one of its three
     * sub-checks (PART 16/58: reuse, never reproduce, academic-relationship
     * validation).
     */
    private SchedulingRequest buildSchedulingRequest(
            Long userId,
            CrAssignment assignment,
            Long subjectId,
            TargetType targetType,
            Long batchId,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime) {
        Long divisionId = assignment.getDivision().getId();
        Long academicTermId = assignment.getAcademicTerm().getId();
        Long facultyId = resolveFaculty(subjectId, divisionId, batchId, academicTermId, targetType);
        SchedulingActor actor = new SchedulingActor(userId, UserRole.CR);
        return new SchedulingRequest(
                AllocationType.EXTRA, targetType, divisionId, batchId, subjectId, facultyId, academicTermId,
                allocationDate, startTime, endTime, actor);
    }

    private Long resolveFaculty(Long subjectId, Long divisionId, Long batchId, Long academicTermId, TargetType targetType) {
        SubjectFacultyAssignment resolved = targetType == TargetType.BATCH
                ? facultyAssignmentResolutionService.resolveForBatch(subjectId, divisionId, batchId, academicTermId)
                : facultyAssignmentResolutionService.resolveForDivision(subjectId, divisionId, academicTermId);
        return resolved.getFaculty().getId();
    }

    private ApiException allocationConflict(ConstraintEvaluation evaluation) {
        List<ViolationExplanation> violations = evaluation.violations().stream().map(ViolationExplanation::from).toList();
        return new ApiException(
                "ALLOCATION_CONFLICT", HttpStatus.CONFLICT,
                "The selected lab is no longer valid for this request.",
                Map.of("violations", violations));
    }

    private String normalizeReason(ExtraLabCancelRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return null;
        }
        String trimmed = request.reason().trim();
        if (trimmed.length() > 500) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "reason must be at most 500 characters.");
        }
        return trimmed;
    }
}
