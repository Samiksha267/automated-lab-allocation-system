package com.college.laballocation.academic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class ProgramDtos {
    private ProgramDtos() {}

    public record CreateProgramRequest(@NotBlank String code, @NotBlank String name, @Min(1) int durationYears) {}

    public record UpdateProgramRequest(@NotBlank String name, @Min(1) int durationYears, boolean active) {}

    public record ProgramResponse(Long id, String code, String name, int durationYears, boolean active) {
        public static ProgramResponse from(Program program) {
            return new ProgramResponse(
                    program.getId(), program.getCode(), program.getName(), program.getDurationYears(),
                    program.isActive());
        }
    }
}
