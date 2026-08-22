package com.college.laballocation.lab;

import jakarta.validation.constraints.NotBlank;

public final class SoftwareDtos {
    private SoftwareDtos() {}

    public record CreateSoftwareRequest(@NotBlank String code, @NotBlank String name) {}

    public record UpdateSoftwareRequest(@NotBlank String name, boolean active) {}

    public record SoftwareResponse(Long id, String code, String name, boolean active) {
        public static SoftwareResponse from(Software software) {
            return new SoftwareResponse(software.getId(), software.getCode(), software.getName(), software.isActive());
        }
    }
}
