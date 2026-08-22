package com.college.laballocation.faculty;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public final class FacultyAvailabilityDtos {
    private FacultyAvailabilityDtos() {}

    public record CreateFacultyAvailabilityRequest(
            @NotNull Long academicTermId, @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    public record UpdateFacultyAvailabilityRequest(
            @NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    public record FacultyAvailabilityResponse(
            Long id,
            Long facultyId,
            String facultyName,
            Long academicTermId,
            String academicTermDisplayName,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean active) {
        static FacultyAvailabilityResponse from(FacultyAvailability availability) {
            return new FacultyAvailabilityResponse(
                    availability.getId(),
                    availability.getFaculty().getId(),
                    availability.getFaculty().getName(),
                    availability.getAcademicTerm().getId(),
                    availability.getAcademicTerm().getDisplayName(),
                    availability.getDayOfWeek(),
                    availability.getStartTime(),
                    availability.getEndTime(),
                    availability.isActive());
        }
    }

    /** Administrative preview only - never scheduling/conflict validation (that is Phase 9's constraint engine). */
    public record AvailabilityCheckResponse(
            Long facultyId,
            Long academicTermId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean available) {}
}
