package com.college.laballocation.academic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class AcademicYearDtos {
    private AcademicYearDtos() {}

    public record CreateAcademicYearRequest(@NotNull Long streamId, @Min(1) int yearNumber) {}

    public record UpdateAcademicYearRequest(boolean active) {}

    public record AcademicYearResponse(
            Long id, Long streamId, String streamCode, Long programId, String programCode, int yearNumber, boolean active) {
        public static AcademicYearResponse from(AcademicYear year) {
            return new AcademicYearResponse(
                    year.getId(),
                    year.getStream().getId(),
                    year.getStream().getCode(),
                    year.getStream().getProgram().getId(),
                    year.getStream().getProgram().getCode(),
                    year.getYearNumber(),
                    year.isActive());
        }
    }
}
