package com.college.laballocation.academic;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class CrAssignmentDtos {
    private CrAssignmentDtos() {}

    public record CreateCrAssignmentRequest(@NotNull Long userId, @NotNull Long divisionId, @NotNull Long academicTermId) {}

    public record CrAssignmentResponse(
            Long id,
            Long userId,
            String userEmail,
            Long divisionId,
            String divisionCode,
            Long academicTermId,
            String academicTermDisplayName,
            CrAssignmentStatus status,
            Instant validFrom,
            Instant validTo) {
        public static CrAssignmentResponse from(CrAssignment assignment) {
            return new CrAssignmentResponse(
                    assignment.getId(),
                    assignment.getUser().getId(),
                    assignment.getUser().getEmail(),
                    assignment.getDivision().getId(),
                    assignment.getDivision().getCode(),
                    assignment.getAcademicTerm().getId(),
                    assignment.getAcademicTerm().getDisplayName(),
                    assignment.getStatus(),
                    assignment.getValidFrom(),
                    assignment.getValidTo());
        }
    }

    /** Shape for GET /api/cr-assignments/me (PART 44 of the phase brief) - no internal ids beyond what the CR dashboard will need. */
    public record CurrentCrAssignmentResponse(
            Long divisionId,
            String divisionCode,
            String program,
            String stream,
            int year,
            Long academicTermId,
            String academicTerm) {
        public static CurrentCrAssignmentResponse from(CrAssignment assignment) {
            Division division = assignment.getDivision();
            AcademicYear year = division.getAcademicYear();
            return new CurrentCrAssignmentResponse(
                    division.getId(),
                    division.getCode(),
                    year.getStream().getProgram().getName(),
                    year.getStream().getName(),
                    year.getYearNumber(),
                    assignment.getAcademicTerm().getId(),
                    assignment.getAcademicTerm().getDisplayName());
        }
    }
}
