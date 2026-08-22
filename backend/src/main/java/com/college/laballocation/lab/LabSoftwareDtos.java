package com.college.laballocation.lab;

import jakarta.validation.constraints.NotNull;

public final class LabSoftwareDtos {
    private LabSoftwareDtos() {}

    public record AddLabSoftwareRequest(@NotNull Long softwareId, String installedVersion) {}
}
