package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Bulk scheduled-minutes-per-lab aggregation for {@code LabUtilizationService}
     * (Phase 11's Balanced Utilization scorer) - one grouped query for every
     * candidate lab in a scoring run rather than one query per lab (PART 55
     * of the Phase 11 brief). Scoped to a single, explicit
     * {@code scheduleVersionId} so a superseded version's historical rows
     * never silently double-count alongside the current one (PART 57) - the
     * caller decides which version is "current" for its purposes.
     */
    @Query(
            value = "SELECT lab_id AS labId, "
                    + "SUM(EXTRACT(EPOCH FROM (end_time - start_time)) / 60) AS minutes "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND lab_id IN (:labIds) "
                    + "AND status IN (:statuses) "
                    + "GROUP BY lab_id",
            nativeQuery = true)
    List<LabLoadRow> sumScheduledMinutesByLab(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("labIds") Collection<Long> labIds,
            @Param("statuses") Collection<String> statuses);

    interface LabLoadRow {
        Long getLabId();

        Double getMinutes();
    }
}
