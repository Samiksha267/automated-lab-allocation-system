package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small, decoupled identity/display snapshots consumed by {@link SchedulingContext}
 * and {@link CandidateAllocation} - deliberately just identity fields plus the
 * handful of scalar fields a Phase 9 constraint directly needs (e.g.
 * {@code academicYearId} for HC-12, {@code requiredLabTypeId} for HC-10), not
 * full requirement or availability data. Subject software/equipment
 * requirements (Phase 6) and faculty availability (Phase 7) already have
 * their own dedicated, tested services (`SubjectSoftwareRequirementRepository`,
 * `SubjectEquipmentRequirementRepository`, `FacultyAvailabilityService`) -
 * Phase 9's constraint classes call those directly rather than this context
 * duplicating their data into a second, potentially-stale copy.
 */
public final class SchedulingRefs {
    private SchedulingRefs() {}

    /**
     * {@code academicYearId} backs HC-12's "subject belongs to the request's
     * academic hierarchy" check; {@code requiredLabTypeId} backs HC-10.
     * {@code preferredLabTypeId} (Phase 11) is the soft counterpart read only
     * by {@code PreferredLabTypeScorer} - {@code Subject} already enforces
     * the two are mutually exclusive, so a candidate never sees both set.
     */
    public record SubjectRef(
            Long id, String code, String name, Long academicYearId, Long requiredLabTypeId, Long preferredLabTypeId) {}

    public record FacultyRef(Long id, String employeeCode, String name, boolean active) {}

    /** {@code academicYearId} backs HC-12 - compared against {@link SubjectRef#academicYearId()}. */
    public record DivisionRef(Long id, String code, int strength, Long academicYearId) {}

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

    /**
     * A candidate-specific snapshot of one lab - built once per candidate by
     * {@code CandidateAllocationFactory} (Phase 9), not re-queried by every
     * constraint that needs lab data (HC-01, HC-06, HC-07, HC-08, HC-09,
     * HC-10 all read from this single object rather than each independently
     * loading {@code Lab}/{@code LabSoftware}/{@code LabEquipment}/
     * {@code LabUnavailability}).
     */
    public record LabRef(
            Long id,
            String code,
            boolean active,
            int capacity,
            Long labTypeId,
            String labTypeCode,
            Set<String> softwareCodes,
            Map<String, Integer> equipmentQuantities,
            List<ExistingAllocationSnapshot> existingAllocations,
            List<InstantRange> unavailabilityWindows) {

        public LabRef {
            softwareCodes = Set.copyOf(softwareCodes);
            equipmentQuantities = Map.copyOf(equipmentQuantities);
            existingAllocations = List.copyOf(existingAllocations);
            unavailabilityWindows = List.copyOf(unavailabilityWindows);
        }
    }
}
