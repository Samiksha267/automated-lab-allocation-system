package com.college.laballocation.analytics;

import com.college.laballocation.scheduling.Allocation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate/projection queries for Phase 23 analytics - a second repository interface over the
 * {@link Allocation} entity (Spring Data allows this; {@code AllocationRepository} keeps its existing
 * scheduling-focused queries untouched), following the exact native-{@code @Query}-with-projection
 * pattern {@code AllocationRepository.sumScheduledMinutesByLab} already established (Phase 11).
 *
 * <p>Every query here is explicitly scoped to one {@code schedule_version_id} (always the term's
 * current PUBLISHED version, resolved by the caller - never {@code MAX(version_number)}) and an
 * explicit status collection (always {@code AllocationStatus.blocksScheduling()}'s APPROVED/PUBLISHED
 * pair for "active" queries, so CANCELLED rows and any other schedule version's rows - DRAFT or
 * SUPERSEDED - can never be counted), matching this project's centralized "what counts as active
 * scheduling load" definition (PART 5/6/7 of the phase brief).
 */
public interface AnalyticsRepository extends JpaRepository<Allocation, Long> {

    @Query(
            value = "SELECT COUNT(*) "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND status IN (:statuses) "
                    + "AND allocation_date BETWEEN :fromDate AND :toDate",
            nativeQuery = true)
    long countActiveAllocations(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("statuses") Collection<String> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT lab_id AS labId, "
                    + "SUM(EXTRACT(EPOCH FROM (end_time - start_time)) / 60) AS minutes, "
                    + "COUNT(*) AS allocationCount "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND status IN (:statuses) "
                    + "AND allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY lab_id",
            nativeQuery = true)
    List<LabMinutesRow> sumBookedMinutesByLab(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("statuses") Collection<String> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT allocation_date AS date, "
                    + "SUM(EXTRACT(EPOCH FROM (end_time - start_time)) / 60) AS minutes, "
                    + "COUNT(*) AS allocationCount "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND status IN (:statuses) "
                    + "AND allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY allocation_date "
                    + "ORDER BY minutes DESC",
            nativeQuery = true)
    List<DateMinutesRow> sumBookedMinutesByDate(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("statuses") Collection<String> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /** Raw time ranges for peak-time-slot bucketing (PART 31) - deliberately done in Java (see {@code PeakUsageAnalyticsService}), not SQL, since distributing one row's minutes across several overlapping hourly buckets is clearer as ordinary code than as a single aggregate query. */
    @Query(
            value = "SELECT start_time AS startTime, end_time AS endTime "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND status IN (:statuses) "
                    + "AND allocation_date BETWEEN :fromDate AND :toDate",
            nativeQuery = true)
    List<TimeRangeRow> findBookedTimeRanges(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("statuses") Collection<String> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT status AS status, COUNT(*) AS count "
                    + "FROM allocation "
                    + "WHERE schedule_version_id = :scheduleVersionId "
                    + "AND allocation_type = 'EXTRA' "
                    + "AND allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY status",
            nativeQuery = true)
    List<StatusCountRow> countExtraLabsByStatus(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT d.code AS groupKey, "
                    + "COUNT(*) FILTER (WHERE a.status IN (:activeStatuses)) AS active, "
                    + "COUNT(*) FILTER (WHERE a.status = 'CANCELLED') AS cancelled, "
                    + "COUNT(*) AS total "
                    + "FROM allocation a JOIN division d ON d.id = a.division_id "
                    + "WHERE a.schedule_version_id = :scheduleVersionId "
                    + "AND a.allocation_type = 'EXTRA' "
                    + "AND a.allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY d.code ORDER BY total DESC",
            nativeQuery = true)
    List<BreakdownRow> extraLabsByDivision(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("activeStatuses") Collection<String> activeStatuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT s.code AS groupKey, "
                    + "COUNT(*) FILTER (WHERE a.status IN (:activeStatuses)) AS active, "
                    + "COUNT(*) FILTER (WHERE a.status = 'CANCELLED') AS cancelled, "
                    + "COUNT(*) AS total "
                    + "FROM allocation a JOIN subject s ON s.id = a.subject_id "
                    + "WHERE a.schedule_version_id = :scheduleVersionId "
                    + "AND a.allocation_type = 'EXTRA' "
                    + "AND a.allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY s.code ORDER BY total DESC",
            nativeQuery = true)
    List<BreakdownRow> extraLabsBySubject(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("activeStatuses") Collection<String> activeStatuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query(
            value = "SELECT l.code AS groupKey, "
                    + "COUNT(*) FILTER (WHERE a.status IN (:activeStatuses)) AS active, "
                    + "COUNT(*) FILTER (WHERE a.status = 'CANCELLED') AS cancelled, "
                    + "COUNT(*) AS total "
                    + "FROM allocation a JOIN lab l ON l.id = a.lab_id "
                    + "WHERE a.schedule_version_id = :scheduleVersionId "
                    + "AND a.allocation_type = 'EXTRA' "
                    + "AND a.allocation_date BETWEEN :fromDate AND :toDate "
                    + "GROUP BY l.code ORDER BY total DESC",
            nativeQuery = true)
    List<BreakdownRow> extraLabsByLab(
            @Param("scheduleVersionId") Long scheduleVersionId,
            @Param("activeStatuses") Collection<String> activeStatuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    interface LabMinutesRow {
        Long getLabId();

        Double getMinutes();

        Long getAllocationCount();
    }

    interface DateMinutesRow {
        LocalDate getDate();

        Double getMinutes();

        Long getAllocationCount();
    }

    interface TimeRangeRow {
        LocalTime getStartTime();

        LocalTime getEndTime();
    }

    interface StatusCountRow {
        String getStatus();

        Long getCount();
    }

    interface BreakdownRow {
        String getGroupKey();

        Long getActive();

        Long getCancelled();

        Long getTotal();
    }
}
