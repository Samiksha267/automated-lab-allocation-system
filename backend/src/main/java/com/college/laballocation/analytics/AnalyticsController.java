package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.college.laballocation.analytics.AnalyticsDtos.ConflictAnalyticsResponse;
import com.college.laballocation.analytics.AnalyticsDtos.ExtraLabAnalyticsResponse;
import com.college.laballocation.analytics.AnalyticsDtos.LabUtilizationResponse;
import com.college.laballocation.analytics.AnalyticsDtos.PeakUsageResponse;
import com.college.laballocation.analytics.AnalyticsDtos.UnusedLabsResponse;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab Assistant-only operational analytics (PART 4, mandatory) - CR and STUDENT never had a
 * documented need for institution-wide scheduling statistics, unlike the timetable/extra-lab APIs
 * which are deliberately shared. Every endpoint shares the same {@code academicTermId} (required)
 * + optional {@code from}/{@code to} scope, resolved once by {@link AnalyticsScopeService} so a
 * bad date range (`to` before `from`) or an unknown term fails identically everywhere.
 */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasRole('LAB_ASSISTANT')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsScopeService scopeService;
    private final LabUtilizationAnalyticsService labUtilizationAnalyticsService;
    private final ExtraLabAnalyticsService extraLabAnalyticsService;
    private final PeakUsageAnalyticsService peakUsageAnalyticsService;
    private final ConflictAnalyticsService conflictAnalyticsService;

    public AnalyticsController(
            AnalyticsService analyticsService,
            AnalyticsScopeService scopeService,
            LabUtilizationAnalyticsService labUtilizationAnalyticsService,
            ExtraLabAnalyticsService extraLabAnalyticsService,
            PeakUsageAnalyticsService peakUsageAnalyticsService,
            ConflictAnalyticsService conflictAnalyticsService) {
        this.analyticsService = analyticsService;
        this.scopeService = scopeService;
        this.labUtilizationAnalyticsService = labUtilizationAnalyticsService;
        this.extraLabAnalyticsService = extraLabAnalyticsService;
        this.peakUsageAnalyticsService = peakUsageAnalyticsService;
        this.conflictAnalyticsService = conflictAnalyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
            @RequestParam Long academicTermId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return analyticsService.summary(academicTermId, from, to);
    }

    @GetMapping("/lab-utilization")
    public LabUtilizationResponse labUtilization(
            @RequestParam Long academicTermId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String wing) {
        return labUtilizationAnalyticsService.utilization(scopeService.resolve(academicTermId, from, to), wing);
    }

    @GetMapping("/unused-labs")
    public UnusedLabsResponse unusedLabs(
            @RequestParam Long academicTermId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String wing) {
        return labUtilizationAnalyticsService.unusedLabs(scopeService.resolve(academicTermId, from, to), wing);
    }

    @GetMapping("/extra-labs")
    public ExtraLabAnalyticsResponse extraLabs(
            @RequestParam Long academicTermId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return extraLabAnalyticsService.extraLabAnalytics(scopeService.resolve(academicTermId, from, to));
    }

    @GetMapping("/peak-usage")
    public PeakUsageResponse peakUsage(
            @RequestParam Long academicTermId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return peakUsageAnalyticsService.peakUsage(scopeService.resolve(academicTermId, from, to));
    }

    @GetMapping("/conflicts")
    public ConflictAnalyticsResponse conflicts(
            @RequestParam Long academicTermId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return conflictAnalyticsService.conflicts(scopeService.resolve(academicTermId, from, to));
    }
}
