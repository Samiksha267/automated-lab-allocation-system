package com.college.laballocation.subject;

import com.college.laballocation.subject.SubjectRequirementDtos.AddEquipmentRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.AddSoftwareRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.EquipmentSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.LabTypeSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.SetLabTypeRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.SoftwareSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.SubjectRequirementsResponse;
import com.college.laballocation.subject.SubjectRequirementDtos.UpdateEquipmentRequirementQuantityRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read: any authenticated user (the future Constraint Engine and any
 * management/CR-facing UI both need to see what a subject requires). Write:
 * LAB_ASSISTANT only (docs/09-AUTHORIZATION-RBAC.md).
 */
@RestController
@RequestMapping("/api/subjects/{subjectId}")
public class SubjectRequirementController {

    private final SubjectRequirementService requirementService;

    public SubjectRequirementController(SubjectRequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @GetMapping("/requirements")
    public SubjectRequirementsResponse get(@PathVariable Long subjectId) {
        return requirementService.get(subjectId);
    }

    @PostMapping("/software-requirements")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SoftwareSummary addSoftwareRequirement(@PathVariable Long subjectId, @Valid @RequestBody AddSoftwareRequirementRequest request) {
        return requirementService.addSoftwareRequirement(subjectId, request);
    }

    @DeleteMapping("/software-requirements/{softwareId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void removeSoftwareRequirement(@PathVariable Long subjectId, @PathVariable Long softwareId) {
        requirementService.removeSoftwareRequirement(subjectId, softwareId);
    }

    @PostMapping("/equipment-requirements")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public EquipmentSummary addEquipmentRequirement(@PathVariable Long subjectId, @Valid @RequestBody AddEquipmentRequirementRequest request) {
        return requirementService.addEquipmentRequirement(subjectId, request);
    }

    @PatchMapping("/equipment-requirements/{equipmentId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public EquipmentSummary updateEquipmentRequirementQuantity(
            @PathVariable Long subjectId,
            @PathVariable Long equipmentId,
            @Valid @RequestBody UpdateEquipmentRequirementQuantityRequest request) {
        return requirementService.updateEquipmentRequirementQuantity(subjectId, equipmentId, request.requiredQuantity());
    }

    @DeleteMapping("/equipment-requirements/{equipmentId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void removeEquipmentRequirement(@PathVariable Long subjectId, @PathVariable Long equipmentId) {
        requirementService.removeEquipmentRequirement(subjectId, equipmentId);
    }

    @PutMapping("/lab-type-requirement")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public LabTypeSummary setLabTypeRequirement(@PathVariable Long subjectId, @RequestBody SetLabTypeRequirementRequest request) {
        return requirementService.setLabTypeRequirement(subjectId, request);
    }

    @DeleteMapping("/lab-type-requirement")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void clearLabTypeRequirement(@PathVariable Long subjectId) {
        requirementService.clearLabTypeRequirement(subjectId);
    }
}
