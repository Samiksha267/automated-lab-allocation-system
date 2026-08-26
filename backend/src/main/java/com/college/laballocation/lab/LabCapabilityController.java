package com.college.laballocation.lab;

import com.college.laballocation.lab.LabDtos.InstalledEquipmentItem;
import com.college.laballocation.lab.LabDtos.InstalledSoftwareItem;
import com.college.laballocation.lab.LabEquipmentDtos.AssignLabEquipmentRequest;
import com.college.laballocation.lab.LabEquipmentDtos.UpdateLabEquipmentQuantityRequest;
import com.college.laballocation.lab.LabSoftwareDtos.AddLabSoftwareRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/{labId}")
public class LabCapabilityController {

    private final LabCapabilityService capabilityService;

    public LabCapabilityController(LabCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @GetMapping("/software")
    public List<InstalledSoftwareItem> listSoftware(@PathVariable Long labId) {
        return capabilityService.listSoftware(labId);
    }

    @PostMapping("/software")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public InstalledSoftwareItem addSoftware(
            @PathVariable Long labId, @Valid @RequestBody AddLabSoftwareRequest request, @AuthenticationPrincipal Long userId) {
        return capabilityService.addSoftware(labId, request, userId);
    }

    @DeleteMapping("/software/{softwareId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void removeSoftware(@PathVariable Long labId, @PathVariable Long softwareId, @AuthenticationPrincipal Long userId) {
        capabilityService.removeSoftware(labId, softwareId, userId);
    }

    @GetMapping("/equipment")
    public List<InstalledEquipmentItem> listEquipment(@PathVariable Long labId) {
        return capabilityService.listEquipment(labId);
    }

    @PostMapping("/equipment")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public InstalledEquipmentItem assignEquipment(
            @PathVariable Long labId, @Valid @RequestBody AssignLabEquipmentRequest request, @AuthenticationPrincipal Long userId) {
        return capabilityService.assignEquipment(labId, request, userId);
    }

    @PatchMapping("/equipment/{equipmentId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public InstalledEquipmentItem updateEquipmentQuantity(
            @PathVariable Long labId,
            @PathVariable Long equipmentId,
            @Valid @RequestBody UpdateLabEquipmentQuantityRequest request,
            @AuthenticationPrincipal Long userId) {
        return capabilityService.updateEquipmentQuantity(labId, equipmentId, request.quantity(), userId);
    }

    @DeleteMapping("/equipment/{equipmentId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void removeEquipment(@PathVariable Long labId, @PathVariable Long equipmentId, @AuthenticationPrincipal Long userId) {
        capabilityService.removeEquipment(labId, equipmentId, userId);
    }
}
