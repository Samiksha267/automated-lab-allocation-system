import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { analyticsApi } from "../../api/analytics";
import { AsyncSection } from "../../components/AsyncSection";
import { Card, StatCard } from "../../components/Card";
import { DataTable, type Column } from "../../components/DataTable";
import { TermSelect } from "../../components/TermSelect";
import { formatDate, formatTime } from "../../lib/formatting";
import type { ExtraLabBreakdownItem, LabUtilizationRow } from "../../api/analytics";

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${value}%`;
}

function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remaining = minutes % 60;
  if (hours === 0) return `${remaining} min`;
  return remaining === 0 ? `${hours}h` : `${hours}h ${remaining}m`;
}

/**
 * Real Phase 23 analytics, replacing the honest Phase 20 placeholder. Every card/table renders
 * only what the backend actually computed from persisted data - no hard-coded percentages, no
 * fake trends, no estimated conflict counts (see `ConflictAnalyticsService`'s explicit
 * "no evidence available" contract, rendered here verbatim rather than a fabricated number).
 */
export function AnalyticsPage() {
  const [academicTermId, setAcademicTermId] = useState<number | null>(null);

  const summary = useQuery({
    queryKey: ["analytics", "summary", academicTermId],
    queryFn: () => analyticsApi.summary({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });
  const utilization = useQuery({
    queryKey: ["analytics", "lab-utilization", academicTermId],
    queryFn: () => analyticsApi.labUtilization({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });
  const unusedLabs = useQuery({
    queryKey: ["analytics", "unused-labs", academicTermId],
    queryFn: () => analyticsApi.unusedLabs({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });
  const extraLabs = useQuery({
    queryKey: ["analytics", "extra-labs", academicTermId],
    queryFn: () => analyticsApi.extraLabs({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });
  const peakUsage = useQuery({
    queryKey: ["analytics", "peak-usage", academicTermId],
    queryFn: () => analyticsApi.peakUsage({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });
  const conflicts = useQuery({
    queryKey: ["analytics", "conflicts", academicTermId],
    queryFn: () => analyticsApi.conflicts({ academicTermId: academicTermId! }),
    enabled: !!academicTermId,
  });

  const utilizationColumns: Column<LabUtilizationRow>[] = [
    { header: "Lab", cell: (r) => r.labCode },
    { header: "Wing", cell: (r) => r.wing },
    { header: "Booked", cell: (r) => formatMinutes(r.bookedMinutes) },
    { header: "Available", cell: (r) => formatMinutes(r.availableMinutes) },
    { header: "Utilization", cell: (r) => formatPercent(r.utilizationPercent) },
    { header: "Allocations", cell: (r) => r.allocationCount },
  ];

  const breakdownColumns: Column<ExtraLabBreakdownItem>[] = [
    { header: "", cell: (r) => r.key },
    { header: "Active", cell: (r) => r.active },
    { header: "Cancelled", cell: (r) => r.cancelled },
    { header: "Total", cell: (r) => r.total },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Analytics</h1>
        <TermSelect value={academicTermId} onChange={setAcademicTermId} />
      </div>

      {!academicTermId ? (
        <p className="py-8 text-center text-sm text-slate-500">Select an academic term to view analytics.</p>
      ) : (
        <div className="space-y-4">
          <AsyncSection isLoading={summary.isLoading} error={summary.error}>
            {summary.data && (
              <>
                {!summary.data.publishedVersionExists && (
                  <div className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
                    No published timetable exists for this term yet - the figures below reflect zero operational
                    activity, not missing data.
                  </div>
                )}
                <p className="text-xs text-slate-500">
                  Scope: {summary.data.range.from} to {summary.data.range.to}
                </p>
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  <StatCard
                    label="Overall Lab Utilization"
                    value={formatPercent(summary.data.overallUtilizationPercent)}
                    hint="Booked / available minutes, selected term"
                  />
                  <StatCard label="Active Allocations" value={summary.data.activeAllocations} hint="Published, not cancelled" />
                  <StatCard label="Extra Labs" value={summary.data.extraLabsTotal} hint={`${summary.data.extraLabsActive} active, ${summary.data.extraLabsCancelled} cancelled`} />
                  <StatCard label="Unused Labs" value={summary.data.unusedLabCount} hint="Zero allocations in scope" />
                </div>
              </>
            )}
          </AsyncSection>

          <Card title="Lab Utilization">
            <AsyncSection
              isLoading={utilization.isLoading}
              error={utilization.error}
              isEmpty={(utilization.data?.labs.length ?? 0) === 0}
              emptyMessage="No active labs to report on."
            >
              <p className="mb-2 text-xs text-slate-500">
                Overall: {formatPercent(utilization.data?.overallUtilizationPercent ?? null)} (weighted by available minutes, not an average of
                per-lab percentages)
              </p>
              <DataTable columns={utilizationColumns} rows={utilization.data?.labs ?? []} rowKey={(r) => r.labId} />
            </AsyncSection>
          </Card>

          <Card title="Extra Labs">
            <AsyncSection isLoading={extraLabs.isLoading} error={extraLabs.error}>
              {extraLabs.data && (
                <div className="space-y-4">
                  <div className="grid grid-cols-3 gap-3">
                    <StatCard label="Total" value={extraLabs.data.total} />
                    <StatCard label="Active" value={extraLabs.data.active} />
                    <StatCard label="Cancelled" value={extraLabs.data.cancelled} hint={extraLabs.data.cancellationRatePercent !== null ? `${extraLabs.data.cancellationRatePercent}% cancellation rate` : undefined} />
                  </div>
                  <p className="text-xs text-slate-500">{extraLabs.data.failedBookingDataUnavailableReason}</p>
                  {extraLabs.data.byDivision.length > 0 && (
                    <div>
                      <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">By Division</h4>
                      <DataTable columns={breakdownColumns} rows={extraLabs.data.byDivision} rowKey={(r) => r.key} />
                    </div>
                  )}
                  {extraLabs.data.bySubject.length > 0 && (
                    <div>
                      <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">By Subject</h4>
                      <DataTable columns={breakdownColumns} rows={extraLabs.data.bySubject} rowKey={(r) => r.key} />
                    </div>
                  )}
                  {extraLabs.data.byLab.length > 0 && (
                    <div>
                      <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">By Lab</h4>
                      <DataTable columns={breakdownColumns} rows={extraLabs.data.byLab} rowKey={(r) => r.key} />
                    </div>
                  )}
                </div>
              )}
            </AsyncSection>
          </Card>

          <Card title="Peak Usage">
            <AsyncSection isLoading={peakUsage.isLoading} error={peakUsage.error}>
              {peakUsage.data && (
                <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                  <StatCard
                    label="Busiest Day"
                    value={peakUsage.data.busiestDay ? formatDate(peakUsage.data.busiestDay.date) : "—"}
                    hint={peakUsage.data.busiestDay ? `${formatMinutes(peakUsage.data.busiestDay.bookedMinutes)} booked` : "No activity in scope"}
                  />
                  <StatCard
                    label="Most-Used Lab"
                    value={peakUsage.data.mostUsedLab?.labCode ?? "—"}
                    hint={peakUsage.data.mostUsedLab ? `${formatMinutes(peakUsage.data.mostUsedLab.bookedMinutes)} booked` : "No activity in scope"}
                  />
                  <StatCard
                    label="Busiest Time Slot"
                    value={peakUsage.data.busiestTimeSlot ? `${formatTime(peakUsage.data.busiestTimeSlot.slotStart)}-${formatTime(peakUsage.data.busiestTimeSlot.slotEnd)}` : "—"}
                    hint={peakUsage.data.busiestTimeSlot ? `${formatMinutes(peakUsage.data.busiestTimeSlot.bookedMinutes)} booked` : "No activity in scope"}
                  />
                </div>
              )}
            </AsyncSection>
          </Card>

          <Card title="Unused Labs">
            <AsyncSection
              isLoading={unusedLabs.isLoading}
              error={unusedLabs.error}
              isEmpty={(unusedLabs.data?.unusedLabs.length ?? 0) === 0}
              emptyMessage="All labs were used during the selected period."
            >
              <DataTable
                columns={[
                  { header: "Lab", cell: (r: LabUtilizationRow) => r.labCode },
                  { header: "Wing", cell: (r: LabUtilizationRow) => r.wing },
                  { header: "Capacity", cell: (r: LabUtilizationRow) => r.capacity },
                  { header: "Lab Type", cell: (r: LabUtilizationRow) => r.labTypeCode },
                ]}
                rows={unusedLabs.data?.unusedLabs ?? []}
                rowKey={(r) => r.labId}
              />
            </AsyncSection>
          </Card>

          <Card title="Conflicts / Allocation Attempts">
            <AsyncSection isLoading={conflicts.isLoading} error={conflicts.error}>
              {conflicts.data && (
                <div>
                  {conflicts.data.evidenceAvailable ? (
                    <DataTable
                      columns={[
                        { header: "Category", cell: (r) => r.category },
                        { header: "Count", cell: (r) => r.count },
                      ]}
                      rows={conflicts.data.categories}
                      rowKey={(r) => r.category}
                    />
                  ) : (
                    <p className="text-sm text-slate-600">{conflicts.data.explanation}</p>
                  )}
                </div>
              )}
            </AsyncSection>
          </Card>
        </div>
      )}
    </div>
  );
}
