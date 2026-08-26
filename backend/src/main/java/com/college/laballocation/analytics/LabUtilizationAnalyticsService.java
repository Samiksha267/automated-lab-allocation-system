package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.DateRange;
import com.college.laballocation.analytics.AnalyticsDtos.LabUtilizationResponse;
import com.college.laballocation.analytics.AnalyticsDtos.LabUtilizationRow;
import com.college.laballocation.analytics.AnalyticsDtos.UnusedLabsResponse;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.lab.LabUnavailability;
import com.college.laballocation.lab.LabUnavailabilityRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.SchedulingTimeMapper;
import com.college.laballocation.scheduling.alternative.SchedulingSlotPolicy;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab utilization = booked schedulable minutes / available schedulable minutes, per PART 12 of the
 * phase brief. Available minutes are computed against the college's own real, already-configured
 * scheduling window ({@link SchedulingSlotPolicy}: {@code app.scheduling.day-start-time}/
 * {@code day-end-time}/{@code working-days}, first introduced for Phase 13's alternative-time
 * search) - reused deliberately rather than inventing a second, parallel "analytics working hours"
 * configuration block (PART 13/14: prefer existing real configuration; ADR, docs/15-DESIGN-DECISIONS.md),
 * minus any overlapping {@link LabUnavailability} windows within the requested date range.
 *
 * <p><b>Unavailability overlap handling</b> (PART 15): {@code lab_unavailability} rows are not
 * guaranteed non-overlapping by any database constraint, so overlapping rows are merged (classic
 * sorted-interval-union) before their minutes are summed - a naive per-row subtraction would
 * double-subtract genuinely overlapping windows and understate available time.
 *
 * <p><b>Documented simplification</b> (also PART 15): unavailability minutes are subtracted against
 * the whole requested date range, not clipped to the daily working-hour window per calendar day -
 * clipping a multi-day unavailability span to a repeating daily window is a materially harder
 * calendar computation this phase does not attempt. In practice every unavailability window this
 * project's UI lets a Lab Assistant create is itself declared within a single day (maintenance,
 * an event), so this rarely diverges from a fully-clipped calculation; a hypothetical multi-day,
 * round-the-clock unavailability window would over-subtract slightly against the strict
 * within-working-hours definition. Documented, not silently assumed away.
 */
@Service
@Transactional(readOnly = true)
public class LabUtilizationAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(LabUtilizationAnalyticsService.class);

    private static final Set<AllocationStatus> ACTIVE_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ACTIVE_STATUS_NAMES =
            ACTIVE_STATUSES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AnalyticsRepository analyticsRepository;
    private final LabRepository labRepository;
    private final LabUnavailabilityRepository labUnavailabilityRepository;
    private final SchedulingSlotPolicy slotPolicy;
    private final SchedulingTimeMapper timeMapper;

    public LabUtilizationAnalyticsService(
            AnalyticsRepository analyticsRepository,
            LabRepository labRepository,
            LabUnavailabilityRepository labUnavailabilityRepository,
            SchedulingSlotPolicy slotPolicy,
            SchedulingTimeMapper timeMapper) {
        this.analyticsRepository = analyticsRepository;
        this.labRepository = labRepository;
        this.labUnavailabilityRepository = labUnavailabilityRepository;
        this.slotPolicy = slotPolicy;
        this.timeMapper = timeMapper;
    }

    public LabUtilizationResponse utilization(AnalyticsScope scope, String wing) {
        List<LabUtilizationRow> rows = computeRows(scope, wing);
        long totalBooked = rows.stream().mapToLong(LabUtilizationRow::bookedMinutes).sum();
        long totalAvailable = rows.stream().mapToLong(LabUtilizationRow::availableMinutes).sum();
        Double overall = totalAvailable > 0 ? round1(totalBooked * 100.0 / totalAvailable) : null;
        return new LabUtilizationResponse(
                scope.term().getId(), new DateRange(scope.from(), scope.to()), scope.publishedVersionId().isPresent(), overall, rows);
    }

    public UnusedLabsResponse unusedLabs(AnalyticsScope scope, String wing) {
        List<LabUtilizationRow> unused =
                computeRows(scope, wing).stream().filter(r -> r.bookedMinutes() == 0).toList();
        return new UnusedLabsResponse(scope.term().getId(), new DateRange(scope.from(), scope.to()), scope.publishedVersionId().isPresent(), unused);
    }

    private List<LabUtilizationRow> computeRows(AnalyticsScope scope, String wing) {
        List<Lab> labs = wing != null ? labRepository.findByActiveTrueAndWing(wing) : labRepository.findByActiveTrue();

        Map<Long, AnalyticsRepository.LabMinutesRow> bookedByLab = scope.publishedVersionId().isEmpty()
                ? Map.of()
                : analyticsRepository
                        .sumBookedMinutesByLab(scope.publishedVersionId().get(), ACTIVE_STATUS_NAMES, scope.from(), scope.to())
                        .stream()
                        .collect(Collectors.toMap(AnalyticsRepository.LabMinutesRow::getLabId, r -> r));

        List<LabUtilizationRow> rows = new ArrayList<>();
        for (Lab lab : labs) {
            AnalyticsRepository.LabMinutesRow bookedRow = bookedByLab.get(lab.getId());
            long booked = bookedRow != null ? Math.round(bookedRow.getMinutes()) : 0;
            long allocationCount = bookedRow != null ? bookedRow.getAllocationCount() : 0;
            long available = availableMinutes(lab, scope.from(), scope.to());
            if (booked > available) {
                log.warn(
                        "Lab {} shows {} booked minutes against only {} available minutes in range {}..{} - "
                                + "investigate rather than trust the resulting >100% utilization figure.",
                        lab.getCode(), booked, available, scope.from(), scope.to());
            }
            Double percent = available > 0 ? round1(booked * 100.0 / available) : null;
            rows.add(new LabUtilizationRow(
                    lab.getId(), lab.getCode(), lab.getWing(), lab.getCapacity(), lab.getLabType().getCode(),
                    booked, available, percent, allocationCount));
        }
        rows.sort(Comparator.comparing(LabUtilizationRow::labCode));
        return rows;
    }

    private long availableMinutes(Lab lab, LocalDate from, LocalDate to) {
        long workingDays = countWorkingDays(from, to);
        long dailyMinutes = Duration.between(slotPolicy.dayStartTime(), slotPolicy.dayEndTime()).toMinutes();
        long window = workingDays * dailyMinutes;
        long unavailable = unavailableMinutes(lab, from, to);
        return Math.max(0, window - unavailable);
    }

    private long countWorkingDays(LocalDate from, LocalDate to) {
        Set<DayOfWeek> workingDays = slotPolicy.workingDays();
        long count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (workingDays.contains(date.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    private long unavailableMinutes(Lab lab, LocalDate from, LocalDate to) {
        Instant rangeStart = timeMapper.toInstant(from, LocalTime.MIDNIGHT);
        Instant rangeEnd = timeMapper.toInstant(to.plusDays(1), LocalTime.MIDNIGHT);

        List<long[]> clippedEpochRanges = labUnavailabilityRepository.findByLabIdOrderByStartDateTime(lab.getId()).stream()
                .map(u -> {
                    Instant clippedStart = u.getStartDateTime().isAfter(rangeStart) ? u.getStartDateTime() : rangeStart;
                    Instant clippedEnd = u.getEndDateTime().isBefore(rangeEnd) ? u.getEndDateTime() : rangeEnd;
                    return new long[] {clippedStart.getEpochSecond(), clippedEnd.getEpochSecond()};
                })
                .filter(r -> r[0] < r[1])
                .sorted(Comparator.comparingLong(r -> r[0]))
                .toList();

        long totalSeconds = 0;
        long mergedStart = Long.MIN_VALUE;
        long mergedEnd = Long.MIN_VALUE;
        for (long[] range : clippedEpochRanges) {
            if (mergedEnd == Long.MIN_VALUE) {
                mergedStart = range[0];
                mergedEnd = range[1];
            } else if (range[0] >= mergedEnd) {
                totalSeconds += mergedEnd - mergedStart;
                mergedStart = range[0];
                mergedEnd = range[1];
            } else {
                mergedEnd = Math.max(mergedEnd, range[1]);
            }
        }
        if (mergedEnd != Long.MIN_VALUE) {
            totalSeconds += mergedEnd - mergedStart;
        }
        return totalSeconds / 60;
    }

    static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
