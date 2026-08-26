package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.DateRange;
import com.college.laballocation.analytics.AnalyticsDtos.PeakDay;
import com.college.laballocation.analytics.AnalyticsDtos.PeakLab;
import com.college.laballocation.analytics.AnalyticsDtos.PeakTimeSlot;
import com.college.laballocation.analytics.AnalyticsDtos.PeakUsageResponse;
import com.college.laballocation.lab.LabRepository;
import com.college.laballocation.scheduling.AllocationStatus;
import com.college.laballocation.scheduling.alternative.SchedulingSlotPolicy;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real peak-usage metrics (PART 28-31), each with an explicit, reproducible definition rather than
 * an eyeballed "10 AM is peak":
 *
 * <ul>
 *   <li><b>Busiest day</b> - the {@code allocation_date} with the highest summed booked minutes in
 *   scope (PART 29).</li>
 *   <li><b>Most-used lab</b> - the lab with the highest summed booked minutes, not merely the
 *   highest allocation count (PART 30) - a 3-hour booking counts for more load than a 1-hour one.</li>
 *   <li><b>Busiest time slot</b> - fixed hourly buckets aligned to the college's own configured
 *   working window ({@link SchedulingSlotPolicy}, e.g. 09:00-10:00, 10:00-11:00, ...); an
 *   allocation spanning multiple buckets contributes its exact overlapping minutes to each one it
 *   touches, not an equal split or a single "start hour" bucket (PART 31).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PeakUsageAnalyticsService {

    private static final Set<AllocationStatus> ACTIVE_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ACTIVE_STATUS_NAMES =
            ACTIVE_STATUSES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AnalyticsRepository analyticsRepository;
    private final LabRepository labRepository;
    private final SchedulingSlotPolicy slotPolicy;

    public PeakUsageAnalyticsService(AnalyticsRepository analyticsRepository, LabRepository labRepository, SchedulingSlotPolicy slotPolicy) {
        this.analyticsRepository = analyticsRepository;
        this.labRepository = labRepository;
        this.slotPolicy = slotPolicy;
    }

    public PeakUsageResponse peakUsage(AnalyticsScope scope) {
        DateRange range = new DateRange(scope.from(), scope.to());
        if (scope.publishedVersionId().isEmpty()) {
            return new PeakUsageResponse(scope.term().getId(), range, false, null, null, null);
        }
        Long versionId = scope.publishedVersionId().get();

        PeakDay busiestDay = analyticsRepository.sumBookedMinutesByDate(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()).stream()
                .findFirst()
                .map(r -> new PeakDay(r.getDate(), Math.round(r.getMinutes()), r.getAllocationCount()))
                .orElse(null);

        PeakLab mostUsedLab = analyticsRepository.sumBookedMinutesByLab(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()).stream()
                .max(Comparator.comparingDouble(AnalyticsRepository.LabMinutesRow::getMinutes))
                .map(r -> new PeakLab(
                        r.getLabId(),
                        labRepository.findById(r.getLabId()).map(l -> l.getCode()).orElse("(unknown lab)"),
                        Math.round(r.getMinutes()),
                        r.getAllocationCount()))
                .orElse(null);

        PeakTimeSlot busiestTimeSlot =
                busiestTimeSlot(analyticsRepository.findBookedTimeRanges(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()));

        return new PeakUsageResponse(scope.term().getId(), range, true, busiestDay, mostUsedLab, busiestTimeSlot);
    }

    private PeakTimeSlot busiestTimeSlot(List<AnalyticsRepository.TimeRangeRow> ranges) {
        if (ranges.isEmpty()) {
            return null;
        }
        List<LocalTime[]> buckets = hourlyBuckets();
        long[] bucketMinutes = new long[buckets.size()];
        long[] bucketAllocationCounts = new long[buckets.size()];

        for (AnalyticsRepository.TimeRangeRow rangeRow : ranges) {
            for (int i = 0; i < buckets.size(); i++) {
                LocalTime bucketStart = buckets.get(i)[0];
                LocalTime bucketEnd = buckets.get(i)[1];
                LocalTime overlapStart = maxTime(rangeRow.getStartTime(), bucketStart);
                LocalTime overlapEnd = minTime(rangeRow.getEndTime(), bucketEnd);
                if (overlapStart.isBefore(overlapEnd)) {
                    bucketMinutes[i] += Duration.between(overlapStart, overlapEnd).toMinutes();
                    bucketAllocationCounts[i]++;
                }
            }
        }

        int peakIndex = 0;
        for (int i = 1; i < bucketMinutes.length; i++) {
            if (bucketMinutes[i] > bucketMinutes[peakIndex]) {
                peakIndex = i;
            }
        }
        if (bucketMinutes[peakIndex] == 0) {
            return null;
        }
        return new PeakTimeSlot(buckets.get(peakIndex)[0], buckets.get(peakIndex)[1], bucketMinutes[peakIndex], bucketAllocationCounts[peakIndex]);
    }

    private List<LocalTime[]> hourlyBuckets() {
        List<LocalTime[]> buckets = new ArrayList<>();
        LocalTime cursor = slotPolicy.dayStartTime();
        while (cursor.isBefore(slotPolicy.dayEndTime())) {
            LocalTime next = cursor.plusHours(1);
            LocalTime bucketEnd = next.isAfter(slotPolicy.dayEndTime()) || next.equals(LocalTime.MIDNIGHT) ? slotPolicy.dayEndTime() : next;
            buckets.add(new LocalTime[] {cursor, bucketEnd});
            cursor = next;
        }
        return buckets;
    }

    private static LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime minTime(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
