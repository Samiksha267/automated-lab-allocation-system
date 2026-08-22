package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Query shapes the future Constraint Engine (Phase 9) needs for HC-01/02/04/05:
 * lab/faculty/batch/division + date, filtered by status. Every method takes
 * an explicit status collection rather than hardcoding "active" here - see
 * {@link AllocationQueryService}, which is the single place that decides
 * which statuses block scheduling ({@link AllocationStatus#blocksScheduling()}).
 *
 * <p>Note {@code division_id} is always set on every row regardless of
 * {@code targetType} (docs/04-DATABASE-DESIGN.md §7) - so
 * {@link #findByDivisionIdAndAllocationDateAndStatusIn} alone already returns
 * both DIVISION-wide rows and BATCH rows belonging to that division, exactly
 * what HC-05's bidirectional check needs, with no join required.
 */
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    List<Allocation> findByLabIdAndAllocationDateAndStatusIn(Long labId, LocalDate allocationDate, Collection<AllocationStatus> statuses);

    List<Allocation> findByFacultyIdAndAllocationDateAndStatusIn(
            Long facultyId, LocalDate allocationDate, Collection<AllocationStatus> statuses);

    List<Allocation> findByBatchIdAndAllocationDateAndStatusIn(
            Long batchId, LocalDate allocationDate, Collection<AllocationStatus> statuses);

    List<Allocation> findByDivisionIdAndAllocationDateAndStatusIn(
            Long divisionId, LocalDate allocationDate, Collection<AllocationStatus> statuses);
}
