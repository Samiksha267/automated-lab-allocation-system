package com.college.laballocation.scheduling;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ScheduleVersionDtos {
    private ScheduleVersionDtos() {}

    public record CreateScheduleVersionRequest(@NotNull Long academicTermId, String reason) {}

    /** Never the raw JPA entity - actor names resolved eagerly here (small, single-row responses; no N+1 concern at this scale, unlike the Phase 17 audit listing). */
    public record ScheduleVersionResponse(
            Long id,
            Long academicTermId,
            String academicTermDisplayName,
            int versionNumber,
            String status,
            String reason,
            Long createdByUserId,
            String createdByEmail,
            Instant createdAt,
            Long publishedByUserId,
            String publishedByEmail,
            Instant publishedAt,
            long allocationCount) {

        static ScheduleVersionResponse from(ScheduleVersion version, long allocationCount) {
            return new ScheduleVersionResponse(
                    version.getId(),
                    version.getAcademicTerm().getId(),
                    version.getAcademicTerm().getDisplayName(),
                    version.getVersionNumber(),
                    version.getStatus().name(),
                    version.getReason(),
                    version.getCreatedBy().getId(),
                    version.getCreatedBy().getEmail(),
                    version.getCreatedAt(),
                    version.getPublishedBy() != null ? version.getPublishedBy().getId() : null,
                    version.getPublishedBy() != null ? version.getPublishedBy().getEmail() : null,
                    version.getPublishedAt(),
                    allocationCount);
        }
    }

    /** {@code versions} sorted newest-first by version number (PART 18 of the phase brief). */
    public record ScheduleVersionHistoryResponse(Long academicTermId, String academicTermDisplayName, List<ScheduleVersionResponse> versions) {}

    /**
     * A compact, generic allocation row - shared by the version-allocations listing and the student/CR timetable API
     * (PART 20/21). Never the raw JPA entity.
     *
     * <p>{@code subjectName}/{@code labWing}/{@code labFloor}/{@code labRoomNumber} added in Phase 22: the Student
     * timetable UI must show a subject's full name and a lab's actual location ("C-202, Wing C"), not just its
     * stable code, and no existing endpoint let the frontend resolve those without an N+1 per-row lookup - see
     * docs/15-DESIGN-DECISIONS.md ADR-120.
     */
    public record AllocationSummaryResponse(
            Long allocationId,
            String allocationType,
            String status,
            String targetType,
            Long subjectId,
            String subjectCode,
            String subjectName,
            Long facultyId,
            String facultyName,
            Long labId,
            String labCode,
            String labWing,
            String labFloor,
            String labRoomNumber,
            Long divisionId,
            String divisionCode,
            Long batchId,
            String batchCode,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime,
            Long scheduleVersionId,
            int scheduleVersionNumber) {

        static AllocationSummaryResponse from(Allocation allocation) {
            return new AllocationSummaryResponse(
                    allocation.getId(),
                    allocation.getAllocationType().name(),
                    allocation.getStatus().name(),
                    allocation.getTargetType().name(),
                    allocation.getSubject().getId(),
                    allocation.getSubject().getCode(),
                    allocation.getSubject().getName(),
                    allocation.getFaculty().getId(),
                    allocation.getFaculty().getName(),
                    allocation.getLab().getId(),
                    allocation.getLab().getCode(),
                    allocation.getLab().getWing(),
                    allocation.getLab().getFloor(),
                    allocation.getLab().getRoomNumber(),
                    allocation.getDivision().getId(),
                    allocation.getDivision().getCode(),
                    allocation.getBatch() != null ? allocation.getBatch().getId() : null,
                    allocation.getBatch() != null ? allocation.getBatch().getCode() : null,
                    allocation.getAllocationDate(),
                    allocation.getStartTime(),
                    allocation.getEndTime(),
                    allocation.getScheduleVersion().getId(),
                    allocation.getScheduleVersion().getVersionNumber());
        }
    }
}
