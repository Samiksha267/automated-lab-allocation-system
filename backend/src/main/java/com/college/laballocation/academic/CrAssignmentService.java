package com.college.laballocation.academic;

import com.college.laballocation.academic.CrAssignmentDtos.CrAssignmentResponse;
import com.college.laballocation.academic.CrAssignmentDtos.CreateCrAssignmentRequest;
import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab-Assistant-only CR-assignment management (create, end, list) - see
 * {@link CrOwnershipService} for the read-side "does this CR own this
 * division" authorization check used elsewhere.
 */
@Service
@Transactional(readOnly = true)
public class CrAssignmentService {

    private final CrAssignmentRepository crAssignmentRepository;
    private final UserRepository userRepository;
    private final DivisionService divisionService;
    private final AcademicTermService academicTermService;
    private final AuditLogService auditLogService;

    public CrAssignmentService(
            CrAssignmentRepository crAssignmentRepository,
            UserRepository userRepository,
            DivisionService divisionService,
            AcademicTermService academicTermService,
            AuditLogService auditLogService) {
        this.crAssignmentRepository = crAssignmentRepository;
        this.userRepository = userRepository;
        this.divisionService = divisionService;
        this.academicTermService = academicTermService;
        this.auditLogService = auditLogService;
    }

    public List<CrAssignmentResponse> listByDivision(Long divisionId) {
        return crAssignmentRepository.findByDivisionIdOrderByCreatedAtDesc(divisionId).stream()
                .map(CrAssignmentResponse::from)
                .toList();
    }

    public List<CrAssignmentResponse> listByUser(Long userId) {
        return crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(CrAssignmentResponse::from)
                .toList();
    }

    /**
     * Ends any existing active assignment for the target division/term (a
     * reassignment), then creates the new one - both preserved as history,
     * per docs/04-DATABASE-DESIGN.md (never overwritten in place).
     *
     * <p><b>Audit (Phase 17):</b> a reassignment - where creating the new
     * assignment also ends an existing one for the same division or user -
     * emits both {@code CR_ASSIGNMENT_ENDED} (for each row actually ended)
     * and {@code CR_ASSIGNED} (for the new row), rather than one collapsed
     * "reassigned" event: the two are genuinely distinct facts (a division
     * losing its CR; a user gaining/changing one), and the ended and created
     * rows can differ in which dimension changed (same user moving division,
     * or same division getting a new user) - one event per real state
     * transition keeps each record unambiguous. A plain first-time
     * assignment (nothing ended) emits only {@code CR_ASSIGNED}.
     */
    @Transactional
    public CrAssignmentResponse create(CreateCrAssignmentRequest request, Long actingLabAssistantUserId) {
        AppUser user = userRepository
                .findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + request.userId()));
        if (user.getRole() != UserRole.CR) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "User " + user.getId() + " does not have role CR");
        }
        Division division = divisionService.getEntity(request.divisionId());
        AcademicTerm term = academicTermService.getEntity(request.academicTermId());
        AppUser actingUser = userRepository
                .findById(actingLabAssistantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Acting user not found"));

        Optional<CrAssignment> endedForDivision = crAssignmentRepository
                .findByDivisionIdAndAcademicTermIdAndStatus(division.getId(), term.getId(), CrAssignmentStatus.ACTIVE);
        endedForDivision.ifPresent(CrAssignment::end);
        Optional<CrAssignment> endedForUser = crAssignmentRepository
                .findByUserIdAndAcademicTermIdAndStatus(user.getId(), term.getId(), CrAssignmentStatus.ACTIVE);
        endedForUser.ifPresent(CrAssignment::end);

        try {
            CrAssignment saved = crAssignmentRepository.save(new CrAssignment(user, division, term, actingUser));
            crAssignmentRepository.flush();

            endedForDivision.ifPresent(ended -> recordEnded(actingLabAssistantUserId, ended));
            endedForUser
                    .filter(ended -> endedForDivision.isEmpty() || !ended.getId().equals(endedForDivision.get().getId()))
                    .ifPresent(ended -> recordEnded(actingLabAssistantUserId, ended));
            auditLogService.record(new AuditEvent(
                    actingLabAssistantUserId, UserRole.LAB_ASSISTANT, AuditAction.CR_ASSIGNED,
                    AuditResourceType.CR_ASSIGNMENT, saved.getId(),
                    user.getEmail() + " -> Division " + division.getCode(),
                    term.getId(), division.getId(),
                    Map.of("crUserId", user.getId(), "crEmail", user.getEmail(), "divisionCode", division.getCode())));

            return CrAssignmentResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Guards the race window between the ifPresent(...).end() checks above and
            // this insert - the partial unique indexes (V5 migration) are the real
            // guarantee; this turns a raw constraint violation into a clean API error.
            throw new ApiException(
                    "DUPLICATE_ASSIGNMENT", HttpStatus.CONFLICT,
                    "An active CR assignment already exists for this division or user in this term");
        }
    }

    @Transactional
    public void end(Long assignmentId, Long actingLabAssistantUserId) {
        CrAssignment assignment = crAssignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CR_ASSIGNMENT_NOT_FOUND", "CR assignment not found: " + assignmentId));
        assignment.end();
        recordEnded(actingLabAssistantUserId, assignment);
    }

    private void recordEnded(Long actingLabAssistantUserId, CrAssignment ended) {
        auditLogService.record(new AuditEvent(
                actingLabAssistantUserId, UserRole.LAB_ASSISTANT, AuditAction.CR_ASSIGNMENT_ENDED,
                AuditResourceType.CR_ASSIGNMENT, ended.getId(),
                ended.getUser().getEmail() + " ended for Division " + ended.getDivision().getCode(),
                ended.getAcademicTerm().getId(), ended.getDivision().getId(),
                Map.of(
                        "crUserId", ended.getUser().getId(), "crEmail", ended.getUser().getEmail(),
                        "divisionCode", ended.getDivision().getCode())));
    }
}
