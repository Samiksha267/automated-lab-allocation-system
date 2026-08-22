package com.college.laballocation.academic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class AcademicTermDtos {
    private AcademicTermDtos() {}

    public record CreateAcademicTermRequest(
            @NotBlank String academicYearLabel,
            @Min(1) int termNumber,
            @NotBlank String displayName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}

    public record UpdateAcademicTermStatusRequest(@NotNull TermStatus status) {}

    public record AcademicTermResponse(
            Long id,
            String academicYearLabel,
            int termNumber,
            String displayName,
            LocalDate startDate,
            LocalDate endDate,
            TermStatus status) {
        public static AcademicTermResponse from(AcademicTerm term) {
            return new AcademicTermResponse(
                    term.getId(),
                    term.getAcademicYearLabel(),
                    term.getTermNumber(),
                    term.getDisplayName(),
                    term.getStartDate(),
                    term.getEndDate(),
                    term.getStatus());
        }
    }
}
