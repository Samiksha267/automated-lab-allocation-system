package com.college.laballocation.academic;

import com.college.laballocation.academic.CrAssignmentDtos.CrAssignmentResponse;
import com.college.laballocation.academic.CrAssignmentDtos.CreateCrAssignmentRequest;
import com.college.laballocation.academic.CrAssignmentDtos.CurrentCrAssignmentResponse;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-assignment management is LAB_ASSISTANT-only, except {@code /me}, which
 * every CR uses to resolve their own division - never an arbitrary
 * {@code userId} lookup (PART 43 of the phase brief).
 */
@RestController
@RequestMapping("/api/cr-assignments")
public class CrAssignmentController {

    private final CrAssignmentService crAssignmentService;
    private final CrOwnershipService crOwnershipService;

    public CrAssignmentController(CrAssignmentService crAssignmentService, CrOwnershipService crOwnershipService) {
        this.crAssignmentService = crAssignmentService;
        this.crOwnershipService = crOwnershipService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CR')")
    public CurrentCrAssignmentResponse me(@AuthenticationPrincipal Long userId) {
        return crOwnershipService
                .getCurrentAssignmentResponse(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CR_ASSIGNMENT_NOT_FOUND", "No active CR assignment found for the current term."));
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public List<CrAssignmentResponse> list(
            @RequestParam(required = false) Long divisionId, @RequestParam(required = false) Long userId) {
        if (divisionId != null) {
            return crAssignmentService.listByDivision(divisionId);
        }
        if (userId != null) {
            return crAssignmentService.listByUser(userId);
        }
        throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Either divisionId or userId must be supplied.");
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public CrAssignmentResponse create(
            @Valid @RequestBody CreateCrAssignmentRequest request, @AuthenticationPrincipal Long actingUserId) {
        return crAssignmentService.create(request, actingUserId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void end(@PathVariable Long id) {
        crAssignmentService.end(id);
    }
}
