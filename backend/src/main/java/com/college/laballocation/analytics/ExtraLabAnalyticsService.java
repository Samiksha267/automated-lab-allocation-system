package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.DateRange;
import com.college.laballocation.analytics.AnalyticsDtos.ExtraLabAnalyticsResponse;
import com.college.laballocation.analytics.AnalyticsDtos.ExtraLabBreakdownItem;
import com.college.laballocation.scheduling.AllocationStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extra-lab (FCFS makeup/additional practical, {@code AllocationType.EXTRA}) statistics, scoped to
 * the term's current PUBLISHED schedule version - an EXTRA allocation whose version has since been
 * superseded is stale historical state, not current operational activity, and is excluded exactly
 * the way regular timetable allocations are (PART 5/7).
 *
 * <p><b>What "successful booking" means here</b> (PART 36/37, honestly scoped): every EXTRA
 * allocation row that exists at all - active or later cancelled - represents an authoritative
 * booking attempt (a real {@code POST /api/allocations/extra}) that passed every hard constraint
 * and committed. This system never persists a row, or any other trace, for a booking attempt that
 * was rejected before commit (a {@code 409 ALLOCATION_CONFLICT} writes nothing) - so
 * "successful bookings" can be counted exactly, but a success *rate* (successes over all attempts,
 * including failed ones) cannot be honestly computed. {@code failedBookingDataAvailable} is always
 * {@code false}, with an explicit reason string, rather than a fabricated or silently-omitted
 * denominator.
 */
@Service
@Transactional(readOnly = true)
public class ExtraLabAnalyticsService {

    private static final String FAILED_BOOKING_UNAVAILABLE_REASON =
            "Failed authoritative extra-lab booking attempts (409 ALLOCATION_CONFLICT) are never persisted - "
                    + "only a successful commit creates an allocation row. A success rate over all attempts cannot be "
                    + "honestly computed; only the count of bookings that actually succeeded is shown.";

    private static final Set<AllocationStatus> ACTIVE_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ACTIVE_STATUS_NAMES =
            ACTIVE_STATUSES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AnalyticsRepository analyticsRepository;

    public ExtraLabAnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public ExtraLabAnalyticsResponse extraLabAnalytics(AnalyticsScope scope) {
        DateRange range = new DateRange(scope.from(), scope.to());
        if (scope.publishedVersionId().isEmpty()) {
            return new ExtraLabAnalyticsResponse(
                    scope.term().getId(), range, false, 0, 0, 0, null, List.of(), List.of(), List.of(), 0, false, FAILED_BOOKING_UNAVAILABLE_REASON);
        }
        Long versionId = scope.publishedVersionId().get();

        List<AnalyticsRepository.StatusCountRow> statusCounts =
                analyticsRepository.countExtraLabsByStatus(versionId, scope.from(), scope.to());
        long active = statusCounts.stream()
                .filter(r -> ACTIVE_STATUS_NAMES.contains(r.getStatus()))
                .mapToLong(AnalyticsRepository.StatusCountRow::getCount)
                .sum();
        long cancelled = statusCounts.stream()
                .filter(r -> r.getStatus().equals(AllocationStatus.CANCELLED.name()))
                .mapToLong(AnalyticsRepository.StatusCountRow::getCount)
                .sum();
        long total = active + cancelled;
        Double cancellationRate = total > 0 ? LabUtilizationAnalyticsService.round1(cancelled * 100.0 / total) : null;

        List<ExtraLabBreakdownItem> byDivision = toBreakdownItems(analyticsRepository.extraLabsByDivision(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()));
        List<ExtraLabBreakdownItem> bySubject = toBreakdownItems(analyticsRepository.extraLabsBySubject(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()));
        List<ExtraLabBreakdownItem> byLab = toBreakdownItems(analyticsRepository.extraLabsByLab(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()));

        return new ExtraLabAnalyticsResponse(
                scope.term().getId(), range, true, total, active, cancelled, cancellationRate,
                byDivision, bySubject, byLab, total, false, FAILED_BOOKING_UNAVAILABLE_REASON);
    }

    private static List<ExtraLabBreakdownItem> toBreakdownItems(List<AnalyticsRepository.BreakdownRow> rows) {
        return rows.stream()
                .map(r -> new ExtraLabBreakdownItem(r.getGroupKey(), r.getActive(), r.getCancelled(), r.getTotal()))
                .toList();
    }
}
