package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Small, decoupled identity/display snapshots consumed by {@link SchedulingContext} -
 * deliberately just identity fields (id/code/name), not full requirement or
 * availability data. Subject requirements (Phase 6), lab capabilities
 * (Phase 5), and faculty availability (Phase 7) already have their own
 * dedicated, tested services (`SubjectRequirementService`,
 * `LabCapabilityService`, `FacultyAvailabilityService`) - Phase 9's
 * constraint classes call those directly rather than this context
 * duplicating their data into a second, potentially-stale copy.
 */
public final class SchedulingRefs {
    private SchedulingRefs() {}

    public record SubjectRef(Long id, String code, String name) {}

    public record FacultyRef(Long id, String employeeCode, String name, boolean active) {}

    public record DivisionRef(Long id, String code, int strength) {}

    public record BatchRef(Long id, String code, int strength, Long divisionId) {}

    /** A candidate-independent snapshot of one existing allocation - just enough for a future constraint to explain a rejection. */
    public record ExistingAllocationSnapshot(
            Long allocationId,
            Long labId,
            String labCode,
            Long facultyId,
            Long divisionId,
            Long batchId,
            TargetType targetType,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime) {

        public static ExistingAllocationSnapshot from(Allocation allocation) {
            return new ExistingAllocationSnapshot(
                    allocation.getId(),
                    allocation.getLab().getId(),
                    allocation.getLab().getCode(),
                    allocation.getFaculty().getId(),
                    allocation.getDivision().getId(),
                    allocation.getBatch() != null ? allocation.getBatch().getId() : null,
                    allocation.getTargetType(),
                    allocation.getAllocationDate(),
                    allocation.getStartTime(),
                    allocation.getEndTime());
        }
    }
}
