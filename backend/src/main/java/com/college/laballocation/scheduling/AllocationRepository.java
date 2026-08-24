package com.college.laballocation.scheduling;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** A CR's own division's EXTRA history (Phase 15, {@code GET /api/allocations/extra/mine}) - active and cancelled alike, newest first. */
    List<Allocation> findByDivisionIdAndAllocationTypeOrderByCreatedAtDesc(Long divisionId, AllocationType allocationType);

    /**
     * Lab-Assistant EXTRA activity visibility (Phase 15,
     * {@code GET /api/allocations/extra/activity}), scoped to one required
     * term - traverses {@code allocation.scheduleVersion.academicTerm.id}
     * since {@link Allocation} deliberately carries no redundant
     * {@code academicTermId} column of its own (see {@link Allocation}'s
     * class javadoc).
     */
    List<Allocation> findByAllocationTypeAndScheduleVersion_AcademicTerm_IdOrderByCreatedAtDesc(
            AllocationType allocationType, Long academicTermId);

    /**
     * Loads one allocation under a {@code SELECT ... FOR UPDATE} row lock -
     * Phase 16's double-cancel guard ({@code ExtraLabService.cancel}). Two
     * simultaneous cancellation requests for the same allocation would
     * otherwise both observe {@code PUBLISHED} (each in its own transaction,
     * under default READ COMMITTED) and both successfully apply
     * {@code Allocation.cancel(...)} in memory, with the second UPDATE
     * silently overwriting the first's audit fields once the first commits -
     * this lock instead makes the second request block until the first
     * commits, then re-read the now-{@code CANCELLED} row and correctly fail
     * with {@code INVALID_ALLOCATION_TRANSITION}, per {@link Allocation#cancel}'s
     * own existing idempotency rule (never reinvented here).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Allocation a where a.id = :id")
    Optional<Allocation> findByIdForUpdate(@Param("id") Long id);

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
