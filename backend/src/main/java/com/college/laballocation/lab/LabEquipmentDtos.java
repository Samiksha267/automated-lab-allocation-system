package com.college.laballocation.lab;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class LabEquipmentDtos {
    private LabEquipmentDtos() {}

    public record AssignLabEquipmentRequest(@NotNull Long equipmentId, @Min(1) int quantity) {}

    public record UpdateLabEquipmentQuantityRequest(@Min(1) int quantity) {}
}
