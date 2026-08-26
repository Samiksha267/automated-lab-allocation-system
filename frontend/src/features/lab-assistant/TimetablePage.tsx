import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { timetableApi } from "../../api/scheduleVersions";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { Pagination } from "../../components/Pagination";
import { StatusBadge } from "../../components/StatusBadge";
import { TermSelect } from "../../components/TermSelect";
import { formatDate, formatTimeRange } from "../../lib/formatting";
import type { AllocationSummary } from "../../api/scheduleVersions";

/**
 * The CURRENT PUBLISHED timetable only (`GET /api/timetable`) - deliberately
 * the same API students/CRs use, never `MAX(versionNumber)` and never a
 * version picker (PART 72 - historical/draft inspection belongs to the
 * separate Timetable Versions screen, which reads a specific version's
 * allocations from a different endpoint).
 */
export function TimetablePage() {
  const [termId, setTermId] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [labFilter, setLabFilter] = useState("");
  const [facultyFilter, setFacultyFilter] = useState("");

  const timetable = useQuery({
    queryKey: ["timetable", termId, page],
    queryFn: () => timetableApi.current({ academicTermId: termId!, page }),
    enabled: termId !== null,
  });

  const filteredRows = useMemo(() => {
    const rows = timetable.data?.content ?? [];
    return rows.filter(
      (r) =>
        (labFilter === "" || r.labCode.toLowerCase().includes(labFilter.toLowerCase())) &&
        (facultyFilter === "" || r.facultyName.toLowerCase().includes(facultyFilter.toLowerCase())),
    );
  }, [timetable.data, labFilter, facultyFilter]);

  const columns: Column<AllocationSummary>[] = [
    { header: "Date", cell: (a) => formatDate(a.allocationDate) },
    { header: "Time", cell: (a) => formatTimeRange(a.startTime, a.endTime) },
    { header: "Subject", cell: (a) => a.subjectCode },
    { header: "Faculty", cell: (a) => a.facultyName },
    { header: "Lab", cell: (a) => a.labCode },
    { header: "Division/Batch", cell: (a) => `${a.divisionCode}${a.batchCode ? ` / ${a.batchCode}` : ""}` },
    { header: "Type", cell: (a) => a.allocationType },
    { header: "Status", cell: (a) => <StatusBadge status={a.status} /> },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Current Published Timetable</h1>
      <div className="flex flex-wrap items-end gap-3">
        <TermSelect value={termId} onChange={setTermId} />
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Lab contains</span>
          <input className="input" value={labFilter} onChange={(e) => setLabFilter(e.target.value)} placeholder="e.g. C-202" />
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Faculty contains</span>
          <input className="input" value={facultyFilter} onChange={(e) => setFacultyFilter(e.target.value)} placeholder="e.g. Sharma" />
        </label>
      </div>
      <p className="text-xs text-slate-500">Lab/faculty filters apply to the currently loaded page only.</p>

      {termId === null ? (
        <p className="text-sm text-slate-500">Select an academic term to view its published timetable.</p>
      ) : (
        <AsyncSection
          isLoading={timetable.isLoading}
          error={timetable.error}
          isEmpty={filteredRows.length === 0}
          emptyMessage="No published timetable is currently available for this term/filter."
        >
          <DataTable columns={columns} rows={filteredRows} rowKey={(a) => a.allocationId} />
          {timetable.data && (
            <Pagination page={page} totalPages={timetable.data.totalPages} totalElements={timetable.data.totalElements} onPageChange={setPage} />
          )}
        </AsyncSection>
      )}
    </div>
  );
}
