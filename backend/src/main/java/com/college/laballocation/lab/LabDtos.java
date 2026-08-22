package com.college.laballocation.lab;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class LabDtos {
    private LabDtos() {}

    public record LocationDto(String wing, String floor, String roomNumber) {}

    public record LabTypeSummary(Long id, String code, String name) {
        static LabTypeSummary from(LabType labType) {
            return new LabTypeSummary(labType.getId(), labType.getCode(), labType.getName());
        }
    }

    public record InstalledSoftwareItem(Long softwareId, String code, String name, String installedVersion) {}

    public record InstalledEquipmentItem(Long equipmentId, String code, String name, int quantity) {}

    /** code is immutable after creation - not present in {@link UpdateLabRequest} (docs/15-DESIGN-DECISIONS.md). */
    public record CreateLabRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Min(1) int capacity,
            @NotNull Long labTypeId,
            @NotBlank String wing,
            @NotBlank String floor,
            @NotBlank String roomNumber) {}

    public record UpdateLabRequest(
            @NotBlank String name,
            @Min(1) int capacity,
            @NotNull Long labTypeId,
            @NotBlank String wing,
            @NotBlank String floor,
            @NotBlank String roomNumber,
            boolean active) {}

    /**
     * Full response - includes installed software/equipment. Built by
     * {@code LabService}, which fetches the associations within the same
     * read transaction that loads the lab (docs/15-DESIGN-DECISIONS.md /
     * ASSUMPTIONS A-24 - lazy associations must be resolved before the
     * session closes, not after).
     */
    public record LabResponse(
            Long id,
            String code,
            String name,
            int capacity,
            LocationDto location,
            LabTypeSummary labType,
            List<InstalledSoftwareItem> software,
            List<InstalledEquipmentItem> equipment,
            boolean active) {
        static LabResponse from(Lab lab, List<InstalledSoftwareItem> software, List<InstalledEquipmentItem> equipment) {
            return new LabResponse(
                    lab.getId(),
                    lab.getCode(),
                    lab.getName(),
                    lab.getCapacity(),
                    new LocationDto(lab.getWing(), lab.getFloor(), lab.getRoomNumber()),
                    LabTypeSummary.from(lab.getLabType()),
                    software,
                    equipment,
                    lab.isActive());
        }
    }

    /**
     * Lighter list-view shape (no software/equipment) - used by
     * {@code GET /api/labs} static-filtering results, where fetching every
     * lab's full capability lists would be wasteful for a simple browse/filter
     * view (PART 34: "do not load every lab... filter client-side" implies
     * the server-side list endpoint should itself stay lean).
     */
    public record LabSummaryResponse(
            Long id, String code, String name, int capacity, LocationDto location, LabTypeSummary labType, boolean active) {
        static LabSummaryResponse from(Lab lab) {
            return new LabSummaryResponse(
                    lab.getId(),
                    lab.getCode(),
                    lab.getName(),
                    lab.getCapacity(),
                    new LocationDto(lab.getWing(), lab.getFloor(), lab.getRoomNumber()),
                    LabTypeSummary.from(lab.getLabType()),
                    lab.isActive());
        }
    }
}
