package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.college.laballocation.analytics.AnalyticsDtos.DateRange;
import com.college.laballocation.analytics.AnalyticsDtos.ExtraLabAnalyticsResponse;
import com.college.laballocation.analytics.AnalyticsDtos.LabUtilizationResponse;
import com.college.laballocation.scheduling.AllocationStatus;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the one-screen summary (PART 39, 51-52) from the same per-area services the dedicated
 * endpoints use - never a second, independent computation of utilization or extra-lab counts, so
 * the summary card and the detail page can never silently disagree.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Set<AllocationStatus> ACTIVE_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ACTIVE_STATUS_NAMES =
            ACTIVE_STATUSES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AnalyticsScopeService scopeService;
    private final AnalyticsRepository analyticsRepository;
    private final LabUtilizationAnalyticsService labUtilizationAnalyticsService;
    private final ExtraLabAnalyticsService extraLabAnalyticsService;

    public AnalyticsService(
            AnalyticsScopeService scopeService,
            AnalyticsRepository analyticsRepository,
            LabUtilizationAnalyticsService labUtilizationAnalyticsService,
            ExtraLabAnalyticsService extraLabAnalyticsService) {
        this.scopeService = scopeService;
        this.analyticsRepository = analyticsRepository;
        this.labUtilizationAnalyticsService = labUtilizationAnalyticsService;
        this.extraLabAnalyticsService = extraLabAnalyticsService;
    }

    public AnalyticsSummaryResponse summary(Long academicTermId, LocalDate from, LocalDate to) {
        AnalyticsScope scope = scopeService.resolve(academicTermId, from, to);
        DateRange range = new DateRange(scope.from(), scope.to());

        long activeAllocations = scope.publishedVersionId()
                .map(versionId -> analyticsRepository.countActiveAllocations(versionId, ACTIVE_STATUS_NAMES, scope.from(), scope.to()))
                .orElse(0L);

        LabUtilizationResponse utilization = labUtilizationAnalyticsService.utilization(scope, null);
        long unusedLabCount = utilization.labs().stream().filter(l -> l.bookedMinutes() == 0).count();

        ExtraLabAnalyticsResponse extraLabs = extraLabAnalyticsService.extraLabAnalytics(scope);

        return new AnalyticsSummaryResponse(
                scope.term().getId(),
                scope.term().getDisplayName(),
                range,
                scope.publishedVersionId().isPresent(),
                activeAllocations,
                extraLabs.total(),
                extraLabs.active(),
                extraLabs.cancelled(),
                utilization.overallUtilizationPercent(),
                unusedLabCount,
                false);
    }
}
