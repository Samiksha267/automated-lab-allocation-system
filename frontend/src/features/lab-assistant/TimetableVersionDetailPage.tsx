import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { scheduleVersionsApi } from "../../api/scheduleVersions";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { Pagination } from "../../components/Pagination";
import { StatusBadge } from "../../components/StatusBadge";
import { formatDate, formatTimeRange } from "../../lib/formatting";
import type { AllocationSummary } from "../../api/scheduleVersions";

/**
 * Actions shown match the version's lifecycle status exactly (PART 26) - a
 * PUBLISHED/SUPERSEDED version never shows an edit/upload action here.
 */
export function TimetableVersionDetailPage() {
  const { versionId } = useParams();
  const id = Number(versionId);
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const version = useQuery({ queryKey: ["schedule-versions", id], queryFn: () => scheduleVersionsApi.get(id) });
  const allocations = useQuery({ queryKey: ["schedule-versions", id, "allocations", page], queryFn: () => scheduleVersionsApi.allocations(id, page) });

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
      <Link to="/lab-assistant/timetable-versions" className="text-sm text-indigo-600 hover:underline">
        ← Back to Timetable Versions
      </Link>

      <AsyncSection isLoading={version.isLoading} error={version.error}>
        {version.data && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-xl font-semibold text-slate-900">
                {version.data.academicTermDisplayName} — Version {version.data.versionNumber}
              </h1>
              <StatusBadge status={version.data.status} />
            </div>
            {version.data.reason && <p className="text-sm text-slate-600">Reason: {version.data.reason}</p>}
            {version.data.status === "DRAFT" && (
              <button
                type="button"
                onClick={() => navigate(`/lab-assistant/imports?academicTermId=${version.data!.academicTermId}&scheduleVersionId=${version.data!.id}&upload=1`)}
                className="mt-2 rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700"
              >
                Upload Timetable PDF into this Draft
              </button>
            )}
          </>
        )}
      </AsyncSection>

      <AsyncSection
        isLoading={allocations.isLoading}
        error={allocations.error}
        isEmpty={(allocations.data?.content.length ?? 0) === 0}
        emptyMessage="No sessions in this version yet."
      >
        <DataTable columns={columns} rows={allocations.data?.content ?? []} rowKey={(a) => a.allocationId} />
        {allocations.data && (
          <Pagination page={page} totalPages={allocations.data.totalPages} totalElements={allocations.data.totalElements} onPageChange={setPage} />
        )}
      </AsyncSection>
    </div>
  );
}
