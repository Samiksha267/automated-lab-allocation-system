package com.college.laballocation.academic;

import com.college.laballocation.academic.CrAssignmentDtos.CurrentCrAssignmentResponse;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ForbiddenDivisionAccessException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The source of truth for "which division does this CR currently own" -
 * HC-11 / docs/09-AUTHORIZATION-RBAC.md. Never trust a client-supplied
 * {@code divisionId} as authorization; every future CR-scoped operation
 * (extra-lab booking, Phase 15+) must resolve ownership through this service
 * from the authenticated user's id alone.
 *
 * <p>"Current" assignment resolution: a CR could in principle hold active
 * assignments in more than one simultaneously-{@code ACTIVE} term (the
 * project deliberately does not force a single global "current term" - see
 * {@link AcademicTerm}). This service resolves to the most recently created
 * active assignment whose term is currently {@code ACTIVE}; if a CR's real
 * usage ever needs to disambiguate between two active terms explicitly, a
 * term parameter can be threaded through at that point - not needed yet.
 */
@Service
@Transactional(readOnly = true)
public class CrOwnershipService {

    private final CrAssignmentRepository crAssignmentRepository;
    private final UserRepository userRepository;

    public CrOwnershipService(CrAssignmentRepository crAssignmentRepository, UserRepository userRepository) {
        this.crAssignmentRepository = crAssignmentRepository;
        this.userRepository = userRepository;
    }

    public Optional<CrAssignment> getCurrentAssignment(Long userId) {
        return crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> a.getStatus() == CrAssignmentStatus.ACTIVE)
                .filter(a -> a.getAcademicTerm().getStatus() == TermStatus.ACTIVE)
                .findFirst();
    }

    /**
     * {@code GET /api/cr-assignments/me}'s DTO mapping happens here, inside
     * this transactional method, rather than in the controller after
     * {@link #getCurrentAssignment} returns - {@code CurrentCrAssignmentResponse.from}
     * dereferences several lazy associations (division -&gt; academicYear -&gt;
     * stream -&gt; program), and with {@code spring.jpa.open-in-view: false}
     * those must be touched before the Hibernate session closes, not after
     * control returns to the controller (a real {@code LazyInitializationException}
     * caught during this phase's Docker verification, not a hypothetical).
     */
    public Optional<CurrentCrAssignmentResponse> getCurrentAssignmentResponse(Long userId) {
        return getCurrentAssignment(userId).map(CurrentCrAssignmentResponse::from);
    }

    /**
     * @throws ApiException FORBIDDEN if the user isn't a CR at all
     * @throws ResourceNotFoundException CR_ASSIGNMENT_NOT_FOUND if the CR has no current active assignment
     * @throws ForbiddenDivisionAccessException if the CR's current assignment is a different division
     */
    public CrAssignment requireOwnsDivision(Long userId, Long divisionId) {
        AppUser user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        if (user.getRole() != UserRole.CR) {
            throw new ApiException("FORBIDDEN", HttpStatus.FORBIDDEN, "Only a CR can own a division assignment.");
        }

        CrAssignment assignment = getCurrentAssignment(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CR_ASSIGNMENT_NOT_FOUND", "No active CR assignment found for the current term."));

        if (!assignment.getDivision().getId().equals(divisionId)) {
            throw new ForbiddenDivisionAccessException(
                    "You are not assigned to division " + divisionId + ".");
        }
        return assignment;
    }

    List<CrAssignment> allActiveAssignments(Long userId) {
        return crAssignmentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(a -> a.getStatus() == CrAssignmentStatus.ACTIVE)
                .toList();
    }
}
