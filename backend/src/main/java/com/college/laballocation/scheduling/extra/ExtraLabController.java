package com.college.laballocation.scheduling.extra;

import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabAllocationResponse;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabBookingRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabCancelRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabSearchRequest;
import com.college.laballocation.scheduling.extra.ExtraLabDtos.ExtraLabSearchResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CR-facing EXTRA-lab workflow (Phase 15) - see {@link ExtraLabService}
 * for the actual orchestration; this class only wires HTTP verbs/paths/roles
 * to it and never touches a JPA entity directly (PART 42).
 *
 * <p>Every endpoint requires authentication (deny-by-default,
 * {@code SecurityConfig}); an unauthenticated request receives {@code 401}
 * before ever reaching this class. {@code search}/book/cancel/{@code mine}
 * are {@code CR}-only (PART 38: no functional requirement authorizes
 * {@code LAB_ASSISTANT} to create EXTRA bookings through this workflow -
 * administrative scheduling, if ever needed, is a separate concern);
 * {@code activity} is {@code LAB_ASSISTANT}-only (FR-29/FR-33). Every other
 * role/anonymous combination on any of these receives {@code 403}/{@code 401}
 * via Spring Security method security - never silently ignored.
 */
@RestController
@RequestMapping("/api/allocations/extra")
public class ExtraLabController {

    private final ExtraLabService extraLabService;

    public ExtraLabController(ExtraLabService extraLabService) {
        this.extraLabService = extraLabService;
    }

    /** Advisory search - "what are my options?" Persists nothing (PART 7/9). */
    @PostMapping("/search")
    @PreAuthorize("hasRole('CR')")
    public ExtraLabSearchResponse search(@Valid @RequestBody ExtraLabSearchRequest request, @AuthenticationPrincipal Long userId) {
        return extraLabService.search(userId, request);
    }

    /** Authoritative booking - "I choose this concrete lab." Fresh, transactional revalidation (PART 10-13). */
    @PostMapping
    @PreAuthorize("hasRole('CR')")
    public ExtraLabAllocationResponse book(@Valid @RequestBody ExtraLabBookingRequest request, @AuthenticationPrincipal Long userId) {
        return extraLabService.book(userId, request);
    }

    /**
     * {@code POST .../cancel} rather than {@code DELETE} (PART 27): the
     * operation is a soft lifecycle transition that accepts an optional
     * request body ({@code reason}), which is unconventional for
     * {@code DELETE} in this project's existing client/tooling stack -
     * {@code Allocation.cancel(...)} never removes the row either way
     * (PART 27/72 - NFR-06).
     */
    @PostMapping("/{allocationId}/cancel")
    @PreAuthorize("hasRole('CR')")
    public ExtraLabAllocationResponse cancel(
            @PathVariable Long allocationId,
            @RequestBody(required = false) ExtraLabCancelRequest request,
            @AuthenticationPrincipal Long userId) {
        return extraLabService.cancel(userId, allocationId, request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CR')")
    public List<ExtraLabAllocationResponse> mine(@AuthenticationPrincipal Long userId) {
        return extraLabService.mine(userId);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public List<ExtraLabAllocationResponse> activity(
            @RequestParam Long academicTermId,
            @RequestParam(required = false) Long divisionId,
            @RequestParam(required = false) AllocationStatus status) {
        return extraLabService.activity(academicTermId, divisionId, status);
    }
}
