package com.college.laballocation.lab;

import com.college.laballocation.lab.LabUnavailabilityDtos.CreateLabUnavailabilityRequest;
import com.college.laballocation.lab.LabUnavailabilityDtos.LabUnavailabilityResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/{labId}/unavailability")
public class LabUnavailabilityController {

    private final LabUnavailabilityService unavailabilityService;

    public LabUnavailabilityController(LabUnavailabilityService unavailabilityService) {
        this.unavailabilityService = unavailabilityService;
    }

    @GetMapping
    public List<LabUnavailabilityResponse> list(@PathVariable Long labId) {
        return unavailabilityService.listByLab(labId);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabUnavailabilityResponse create(
            @PathVariable Long labId,
            @Valid @RequestBody CreateLabUnavailabilityRequest request,
            @AuthenticationPrincipal Long userId) {
        return unavailabilityService.create(labId, request, userId);
    }

    @DeleteMapping("/{unavailabilityId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void remove(@PathVariable Long labId, @PathVariable Long unavailabilityId, @AuthenticationPrincipal Long userId) {
        unavailabilityService.remove(unavailabilityId, userId);
    }
}
