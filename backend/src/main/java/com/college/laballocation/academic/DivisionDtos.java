package com.college.laballocation.academic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class DivisionDtos {
    private DivisionDtos() {}

    public record CreateDivisionRequest(@NotNull Long academicYearId, @NotBlank String code, @Min(1) int strength) {}

    public record UpdateDivisionRequest(@Min(1) int strength, boolean active) {}

    public record DivisionResponse(
            Long id, Long academicYearId, int yearNumber, String streamCode, String code, int strength, boolean active) {
        public static DivisionResponse from(Division division) {
            return new DivisionResponse(
                    division.getId(),
                    division.getAcademicYear().getId(),
                    division.getAcademicYear().getYearNumber(),
                    division.getAcademicYear().getStream().getCode(),
                    division.getCode(),
                    division.getStrength(),
                    division.isActive());
        }
    }
}
