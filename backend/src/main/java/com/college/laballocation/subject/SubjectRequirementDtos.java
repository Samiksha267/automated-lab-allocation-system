package com.college.laballocation.subject;

import com.college.laballocation.lab.Equipment;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.Software;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class SubjectRequirementDtos {
    private SubjectRequirementDtos() {}

    public record AddSoftwareRequirementRequest(@NotNull Long softwareId) {}

    public record AddEquipmentRequirementRequest(@NotNull Long equipmentId, @Min(1) int requiredQuantity) {}

    public record UpdateEquipmentRequirementQuantityRequest(@Min(1) int requiredQuantity) {}

    /** Both nullable - {@code null}/{@code null} clears any lab-type requirement; exactly one may be non-null, never both. */
    public record SetLabTypeRequirementRequest(Long requiredLabTypeId, Long preferredLabTypeId) {}

    public record SubjectSummary(Long id, String code, String name) {
        static SubjectSummary from(Subject subject) {
            return new SubjectSummary(subject.getId(), subject.getCode(), subject.getName());
        }
    }

    public record SoftwareSummary(Long id, String code, String name) {
        static SoftwareSummary from(Software software) {
            return new SoftwareSummary(software.getId(), software.getCode(), software.getName());
        }
    }

    public record EquipmentSummary(Long id, String code, String name, int requiredQuantity) {
        static EquipmentSummary from(Equipment equipment, int requiredQuantity) {
            return new EquipmentSummary(equipment.getId(), equipment.getCode(), equipment.getName(), requiredQuantity);
        }
    }

    public record LabTypeSummary(Long id, String code, String name) {
        static LabTypeSummary from(LabType labType) {
            return labType == null ? null : new LabTypeSummary(labType.getId(), labType.getCode(), labType.getName());
        }
    }

    /**
     * The consolidated view the future Constraint Engine (and any management
     * UI) will read - "what does this subject require," entirely separate
     * from "what does a lab provide" (docs/03-SYSTEM-ARCHITECTURE.md).
     * Software/equipment lists are sorted by code for deterministic
     * responses (PART 37 of the phase brief).
     */
    public record SubjectRequirementsResponse(
            SubjectSummary subject,
            List<SoftwareSummary> software,
            List<EquipmentSummary> equipment,
            LabTypeSummary requiredLabType,
            LabTypeSummary preferredLabType) {}
}
