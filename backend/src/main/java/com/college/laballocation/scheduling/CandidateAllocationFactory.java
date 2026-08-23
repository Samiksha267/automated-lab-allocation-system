package com.college.laballocation.scheduling;

import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabEquipment;
import com.college.laballocation.lab.LabEquipmentRepository;
import com.college.laballocation.lab.LabService;
import com.college.laballocation.lab.LabSoftware;
import com.college.laballocation.lab.LabSoftwareRepository;
import com.college.laballocation.lab.LabUnavailabilityRepository;
import com.college.laballocation.lab.Software;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a {@link CandidateAllocation} for one already-known {@code labId} -
 * loading exactly the candidate-specific data HC-01/06/07/08/09/10 need
 * (capacity, lab type, installed software/equipment, existing lab
 * allocations, unavailability windows) once, into a single {@link LabRef}
 * snapshot, rather than each constraint independently re-querying the same
 * lab (PART 41/42 of the Phase 9 brief).
 *
 * <p>This is not candidate generation (Phase 10, which enumerates many
 * labs) - it plays the same "resolve exactly what was asked for" role
 * {@link SchedulingContextFactory} already plays for {@code SchedulingRequest},
 * scoped to a single candidate lab instead.
 */
@Service
@Transactional(readOnly = true)
public class CandidateAllocationFactory {

    private final LabService labService;
    private final LabSoftwareRepository labSoftwareRepository;
    private final LabEquipmentRepository labEquipmentRepository;
    private final LabUnavailabilityRepository labUnavailabilityRepository;
    private final AllocationQueryService allocationQueryService;

    public CandidateAllocationFactory(
            LabService labService,
            LabSoftwareRepository labSoftwareRepository,
            LabEquipmentRepository labEquipmentRepository,
            LabUnavailabilityRepository labUnavailabilityRepository,
            AllocationQueryService allocationQueryService) {
        this.labService = labService;
        this.labSoftwareRepository = labSoftwareRepository;
        this.labEquipmentRepository = labEquipmentRepository;
        this.labUnavailabilityRepository = labUnavailabilityRepository;
        this.allocationQueryService = allocationQueryService;
    }

    public CandidateAllocation build(SchedulingContext context, Long labId) {
        Lab lab = labService.getEntity(labId);

        Set<String> softwareCodes = labSoftwareRepository.findByLabId(labId).stream()
                .map(LabSoftware::getSoftware)
                .map(Software::getCode)
                .collect(Collectors.toUnmodifiableSet());

        Map<String, Integer> equipmentQuantities = labEquipmentRepository.findByLabId(labId).stream()
                .collect(Collectors.toUnmodifiableMap(
                        le -> le.getEquipment().getCode(), LabEquipment::getQuantity));

        List<ExistingAllocationSnapshot> existingAllocations = allocationQueryService
                .findActiveForLab(labId, context.request().allocationDate()).stream()
                .map(ExistingAllocationSnapshot::from)
                .toList();

        List<InstantRange> unavailabilityWindows = labUnavailabilityRepository.findByLabIdOrderByStartDateTime(labId).stream()
                .map(u -> new InstantRange(u.getStartDateTime(), u.getEndDateTime()))
                .toList();

        LabRef labRef = new LabRef(
                lab.getId(),
                lab.getCode(),
                lab.isActive(),
                lab.getCapacity(),
                lab.getLabType().getId(),
                lab.getLabType().getCode(),
                softwareCodes,
                equipmentQuantities,
                existingAllocations,
                unavailabilityWindows);

        return new CandidateAllocation(context, labRef);
    }
}
