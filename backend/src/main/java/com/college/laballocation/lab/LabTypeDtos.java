package com.college.laballocation.lab;

import jakarta.validation.constraints.NotBlank;

public final class LabTypeDtos {
    private LabTypeDtos() {}

    public record CreateLabTypeRequest(@NotBlank String code, @NotBlank String name, String description) {}

    public record UpdateLabTypeRequest(@NotBlank String name, String description, boolean active) {}

    public record LabTypeResponse(Long id, String code, String name, String description, boolean active) {
        public static LabTypeResponse from(LabType labType) {
            return new LabTypeResponse(
                    labType.getId(), labType.getCode(), labType.getName(), labType.getDescription(), labType.isActive());
        }
    }
}
