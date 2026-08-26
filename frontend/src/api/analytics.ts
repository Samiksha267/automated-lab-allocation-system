import { apiClient } from "./client";

/**
 * Types mirror `AnalyticsDtos` (backend, Phase 23) field-for-field. Every `*Percent` field is on a
 * 0-100 scale, never a 0.0-1.0 ratio (see the Phase 21 `normalizedScore` display bug this
 * deliberately avoids repeating, docs/15-DESIGN-DECISIONS.md) - render with a trailing `%`, never
 * multiplied again. Every `*Minutes` field is a plain integer minute count.
 */

export interface DateRange {
  from: string;
  to: string;
}

export interface AnalyticsQuery {
  academicTermId: number;
  from?: string;
  to?: string;
}

export interface LabUtilizationRow {
  labId: number;
  labCode: string;
  wing: string;
  capacity: number;
  labTypeCode: string;
  bookedMinutes: number;
  availableMinutes: number;
  utilizationPercent: number | null;
  allocationCount: number;
}

export interface LabUtilizationResponse {
  academicTermId: number;
  range: DateRange;
  publishedVersionExists: boolean;
  overallUtilizationPercent: number | null;
  labs: LabUtilizationRow[];
}

export interface UnusedLabsResponse {
  academicTermId: number;
  range: DateRange;
  publishedVersionExists: boolean;
  unusedLabs: LabUtilizationRow[];
}

export interface ExtraLabBreakdownItem {
  key: string;
  active: number;
  cancelled: number;
  total: number;
}

export interface ExtraLabAnalyticsResponse {
  academicTermId: number;
  range: DateRange;
  publishedVersionExists: boolean;
  total: number;
  active: number;
  cancelled: number;
  cancellationRatePercent: number | null;
  byDivision: ExtraLabBreakdownItem[];
  bySubject: ExtraLabBreakdownItem[];
  byLab: ExtraLabBreakdownItem[];
  successfulBookings: number;
  failedBookingDataAvailable: boolean;
  failedBookingDataUnavailableReason: string;
}

export interface PeakDay {
  date: string;
  bookedMinutes: number;
  allocationCount: number;
}

export interface PeakLab {
  labId: number;
  labCode: string;
  bookedMinutes: number;
  allocationCount: number;
}

export interface PeakTimeSlot {
  slotStart: string;
  slotEnd: string;
  bookedMinutes: number;
  allocationCount: number;
}

export interface PeakUsageResponse {
  academicTermId: number;
  range: DateRange;
  publishedVersionExists: boolean;
  busiestDay: PeakDay | null;
  mostUsedLab: PeakLab | null;
  busiestTimeSlot: PeakTimeSlot | null;
}

export interface ConflictCategoryCount {
  category: string;
  count: number;
}

export interface ConflictAnalyticsResponse {
  academicTermId: number;
  evidenceAvailable: boolean;
  categories: ConflictCategoryCount[];
  explanation: string;
}

export interface AnalyticsSummaryResponse {
  academicTermId: number;
  academicTermDisplayName: string;
  range: DateRange;
  publishedVersionExists: boolean;
  activeAllocations: number;
  extraLabsTotal: number;
  extraLabsActive: number;
  extraLabsCancelled: number;
  overallUtilizationPercent: number | null;
  unusedLabCount: number;
  conflictEvidenceAvailable: boolean;
}

function buildQuery(params: AnalyticsQuery & { wing?: string }): string {
  const search = new URLSearchParams();
  search.set("academicTermId", String(params.academicTermId));
  if (params.from) search.set("from", params.from);
  if (params.to) search.set("to", params.to);
  if (params.wing) search.set("wing", params.wing);
  return search.toString();
}

export const analyticsApi = {
  summary: (params: AnalyticsQuery) => apiClient.get<AnalyticsSummaryResponse>(`/analytics/summary?${buildQuery(params)}`),
  labUtilization: (params: AnalyticsQuery & { wing?: string }) =>
    apiClient.get<LabUtilizationResponse>(`/analytics/lab-utilization?${buildQuery(params)}`),
  unusedLabs: (params: AnalyticsQuery & { wing?: string }) => apiClient.get<UnusedLabsResponse>(`/analytics/unused-labs?${buildQuery(params)}`),
  extraLabs: (params: AnalyticsQuery) => apiClient.get<ExtraLabAnalyticsResponse>(`/analytics/extra-labs?${buildQuery(params)}`),
  peakUsage: (params: AnalyticsQuery) => apiClient.get<PeakUsageResponse>(`/analytics/peak-usage?${buildQuery(params)}`),
  conflicts: (params: AnalyticsQuery) => apiClient.get<ConflictAnalyticsResponse>(`/analytics/conflicts?${buildQuery(params)}`),
};
