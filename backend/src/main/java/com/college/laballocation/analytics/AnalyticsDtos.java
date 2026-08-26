package com.college.laballocation.analytics;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Response shapes for the Phase 23 analytics API. Every numeric scale is documented on the field
 * itself: minutes are plain integer minutes, every {@code *Percent} field is on a 0-100 scale
 * (never a 0.0-1.0 ratio - see ADR, docs/15-DESIGN-DECISIONS.md, for the Phase 21 bug this
 * deliberately avoids repeating), and every count is a plain non-negative integer.
 */
public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record DateRange(LocalDate from, LocalDate to) {}

    /**
     * One lab's utilization for the requested scope. {@code utilizationPercent} is {@code null}
     * only when {@code availableMinutes} is zero (no basis to divide by) - never a fabricated 0 or
     * 100 in that case.
     */
    public record LabUtilizationRow(
            Long labId,
            String labCode,
            String wing,
            int capacity,
            String labTypeCode,
            long bookedMinutes,
            long availableMinutes,
            Double utilizationPercent,
            long allocationCount) {}

    public record LabUtilizationResponse(
            Long academicTermId,
            DateRange range,
            boolean publishedVersionExists,
            Double overallUtilizationPercent,
            List<LabUtilizationRow> labs) {}

    public record UnusedLabsResponse(Long academicTermId, DateRange range, boolean publishedVersionExists, List<LabUtilizationRow> unusedLabs) {}

    public record ExtraLabBreakdownItem(String key, long active, long cancelled, long total) {}

    /**
     * {@code successfulBookings} is every EXTRA allocation that ever reached a persisted row
     * (APPROVED/PUBLISHED, i.e. currently active, or CANCELLED after having succeeded) - this
     * system never persists a row for a booking attempt that was rejected before commit (a 409 at
     * booking time never writes anything), so {@code failedBookingAttempts} cannot be derived from
     * persisted data; see {@code failedBookingDataUnavailableReason}.
     */
    public record ExtraLabAnalyticsResponse(
            Long academicTermId,
            DateRange range,
            boolean publishedVersionExists,
            long total,
            long active,
            long cancelled,
            Double cancellationRatePercent,
            List<ExtraLabBreakdownItem> byDivision,
            List<ExtraLabBreakdownItem> bySubject,
            List<ExtraLabBreakdownItem> byLab,
            long successfulBookings,
            boolean failedBookingDataAvailable,
            String failedBookingDataUnavailableReason) {}

    public record PeakDay(LocalDate date, long bookedMinutes, long allocationCount) {}

    public record PeakLab(Long labId, String labCode, long bookedMinutes, long allocationCount) {}

    /** One fixed hourly bucket within the configured college working window (e.g. 09:00-10:00) - see {@code PeakUsageAnalyticsService} for how an allocation spanning multiple buckets is distributed. */
    public record PeakTimeSlot(LocalTime slotStart, LocalTime slotEnd, long bookedMinutes, long allocationCount) {}

    public record PeakUsageResponse(
            Long academicTermId, DateRange range, boolean publishedVersionExists, PeakDay busiestDay, PeakLab mostUsedLab, PeakTimeSlot busiestTimeSlot) {}

    public record ConflictCategoryCount(String category, long count) {}

    /**
     * Honest-by-construction: {@code evidenceAvailable} is always {@code false} in this system
     * today, because no booking-conflict/rejection event is ever persisted (only successful state
     * transitions are audited, {@code AuditAction}) - {@code categories} is therefore always empty,
     * never a fabricated or estimated count. See ADR, docs/15-DESIGN-DECISIONS.md.
     */
    public record ConflictAnalyticsResponse(Long academicTermId, boolean evidenceAvailable, List<ConflictCategoryCount> categories, String explanation) {}

    public record AnalyticsSummaryResponse(
            Long academicTermId,
            String academicTermDisplayName,
            DateRange range,
            boolean publishedVersionExists,
            long activeAllocations,
            long extraLabsTotal,
            long extraLabsActive,
            long extraLabsCancelled,
            Double overallUtilizationPercent,
            long unusedLabCount,
            boolean conflictEvidenceAvailable) {}
}
