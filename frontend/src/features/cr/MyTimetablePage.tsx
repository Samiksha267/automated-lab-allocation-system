import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../../api/academic";
import { timetableApi } from "../../api/scheduleVersions";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { Pagination } from "../../components/Pagination";
import { StatusBadge } from "../../components/StatusBadge";
import { formatDate, formatTimeRange } from "../../lib/formatting";
import { useCrAssignment } from "./CrAssignmentContext";
import type { AllocationSummary } from "../../api/scheduleVersions";

/**
 * The CR's own division's CURRENT PUBLISHED timetable only (PART 10/14) -
 * `timetableApi.current` (Phase 18, unchanged) always selects by
 * `status = PUBLISHED`, never the highest version number; this page never
 * lets the division vary from the CR's own assignment.
 */
export function MyTimetablePage() {
  const { assignment, isLoading: assignmentLoading, error: assignmentError } = useCrAssignment();
  const [page, setPage] = useState(0);
  const [batchId, setBatchId] = useState<number | "">("");
  const [subjectFilter, setSubjectFilter] = useState("");

  const batches = useQuery({
    queryKey: ["batches", assignment?.divisionId],
    queryFn: () => academicApi.listBatches(assignment!.divisionId),
    enabled: !!assignment,
  });

  const timetable = useQuery({
    queryKey: ["cr", "timetable", assignment?.academicTermId, assignment?.divisionId, batchId, page],
    queryFn: () =>
      timetableApi.current({
        academicTermId: assignment!.academicTermId,
        divisionId: assignment!.divisionId,
        batchId: batchId === "" ? undefined : batchId,
        page,
      }),
    enabled: !!assignment,
  });

  const filteredRows = useMemo(() => {
    const rows = timetable.data?.content ?? [];
    if (!subjectFilter) return rows;
    return rows.filter((r) => r.subjectCode.toLowerCase().includes(subjectFilter.toLowerCase()));
  }, [timetable.data, subjectFilter]);

  const columns: Column<AllocationSummary>[] = [
    { header: "Date", cell: (a) => formatDate(a.allocationDate) },
    { header: "Time", cell: (a) => formatTimeRange(a.startTime, a.endTime) },
    { header: "Subject", cell: (a) => a.subjectCode },
    { header: "Faculty", cell: (a) => a.facultyName },
    { header: "Lab", cell: (a) => a.labCode },
    { header: "Batch/Division", cell: (a) => (a.batchCode ? `${a.divisionCode} / ${a.batchCode}` : a.divisionCode) },
    { header: "Type", cell: (a) => a.allocationType },
    { header: "Status", cell: (a) => <StatusBadge status={a.status} /> },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">My Timetable</h1>
      <AsyncSection isLoading={assignmentLoading} error={assignmentError}>
        {assignment && (
          <>
            <p className="text-sm text-slate-500">
              {assignment.program} — {assignment.stream}, Year {assignment.year}, Division {assignment.divisionCode} · {assignment.academicTerm}
            </p>
            <div className="flex flex-wrap gap-3">
              <label className="text-sm">
                <span className="mb-1 block font-medium text-slate-700">Batch</span>
                <select className="input" value={batchId} onChange={(e) => setBatchId(e.target.value ? Number(e.target.value) : "")}>
                  <option value="">All (division-wide + every batch)</option>
                  {batches.data?.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.code}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                <span className="mb-1 block font-medium text-slate-700">Subject contains</span>
                <input className="input" value={subjectFilter} onChange={(e) => setSubjectFilter(e.target.value)} placeholder="e.g. BDA" />
              </label>
            </div>

            <AsyncSection
              isLoading={timetable.isLoading}
              error={timetable.error}
              isEmpty={filteredRows.length === 0}
              emptyMessage="No published timetable is available for this term yet."
            >
              <DataTable columns={columns} rows={filteredRows} rowKey={(a) => a.allocationId} />
              {timetable.data && (
                <Pagination page={page} totalPages={timetable.data.totalPages} totalElements={timetable.data.totalElements} onPageChange={setPage} />
              )}
            </AsyncSection>
          </>
        )}
      </AsyncSection>
    </div>
  );
}
