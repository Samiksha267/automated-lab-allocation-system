package com.college.laballocation.lab;

import com.college.laballocation.lab.LabDtos.CreateLabRequest;
import com.college.laballocation.lab.LabDtos.LabResponse;
import com.college.laballocation.lab.LabDtos.LabSummaryResponse;
import com.college.laballocation.lab.LabDtos.UpdateLabRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/labs} is a <b>static capability filter</b>, not a
 * schedule-aware availability search (PART 33 of the phase brief) - it never
 * considers time, faculty, or existing bookings, none of which exist until
 * Phase 9+. See {@link LabSpecifications} for the exact filtering semantics.
 */
@RestController
@RequestMapping("/api/labs")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @GetMapping
    public List<LabSummaryResponse> search(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String wing,
            @RequestParam(required = false) String labType,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) List<String> software,
            @RequestParam(required = false) List<String> equipment) {
        return labService.search(active, wing, labType, minCapacity, software, equipment);
    }

    @GetMapping("/{id}")
    public LabResponse get(@PathVariable Long id) {
        return labService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabResponse create(@Valid @RequestBody CreateLabRequest request, @AuthenticationPrincipal Long userId) {
        return labService.create(request, userId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLabRequest request, @AuthenticationPrincipal Long userId) {
        return labService.update(id, request, userId);
    }
}
