package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query infrastructure for the future Constraint Engine (Phase 9) -
 * no creation/mutation methods live here deliberately (PART 31/32 of the
 * Phase 8 brief: no production allocation-creation path exists until
 * constraints exist to validate against). "Active" is defined once, centrally,
 * from {@link AllocationStatus#blocksScheduling()} - callers never invent
 * their own active-status list.
 */
@Service
@Transactional(readOnly = true)
public class AllocationQueryService {

    private static final Set<AllocationStatus> BLOCKING_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(AllocationStatus.class)));

    private final AllocationRepository allocationRepository;

    public AllocationQueryService(AllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    /** Candidate-specific (varies per candidate lab) - used per-candidate during evaluation, HC-01. */
    public List<Allocation> findActiveForLab(Long labId, LocalDate date) {
        return allocationRepository.findByLabIdAndAllocationDateAndStatusIn(labId, date, BLOCKING_STATUSES);
    }

    /** Candidate-independent (same for every candidate lab of one request) - HC-02. */
    public List<Allocation> findActiveForFaculty(Long facultyId, LocalDate date) {
        return allocationRepository.findByFacultyIdAndAllocationDateAndStatusIn(facultyId, date, BLOCKING_STATUSES);
    }

    /** Candidate-independent - HC-04. */
    public List<Allocation> findActiveForBatch(Long batchId, LocalDate date) {
        return allocationRepository.findByBatchIdAndAllocationDateAndStatusIn(batchId, date, BLOCKING_STATUSES);
    }

    /** Candidate-independent - HC-05 (returns both DIVISION-wide and BATCH rows for this division; see {@link AllocationRepository}). */
    public List<Allocation> findActiveForDivision(Long divisionId, LocalDate date) {
        return allocationRepository.findByDivisionIdAndAllocationDateAndStatusIn(divisionId, date, BLOCKING_STATUSES);
    }
}
