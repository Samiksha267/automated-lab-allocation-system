package com.college.laballocation.lab;

import jakarta.validation.constraints.NotBlank;

public final class EquipmentDtos {
    private EquipmentDtos() {}

    public record CreateEquipmentRequest(@NotBlank String code, @NotBlank String name, String description) {}

    public record UpdateEquipmentRequest(@NotBlank String name, String description, boolean active) {}

    public record EquipmentResponse(Long id, String code, String name, String description, boolean active) {
        public static EquipmentResponse from(Equipment equipment) {
            return new EquipmentResponse(
                    equipment.getId(), equipment.getCode(), equipment.getName(), equipment.getDescription(), equipment.isActive());
        }
    }
}
