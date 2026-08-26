import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { auditLogsApi } from "../../api/auditLogs";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { Pagination } from "../../components/Pagination";
import { formatInstant, titleCase } from "../../lib/formatting";
import type { AuditLogEntry } from "../../api/auditLogs";

const ACTIONS = [
  "EXTRA_LAB_BOOKED",
  "EXTRA_LAB_CANCELLED",
  "CR_ASSIGNED",
  "CR_ASSIGNMENT_ENDED",
  "LAB_CREATED",
  "LAB_UPDATED",
  "LAB_SOFTWARE_CHANGED",
  "LAB_EQUIPMENT_CHANGED",
  "LAB_UNAVAILABILITY_CHANGED",
  "FACULTY_AVAILABILITY_CHANGED",
  "SUBJECT_REQUIREMENTS_CHANGED",
  "SCHEDULE_VERSION_CREATED",
  "SCHEDULE_PUBLISHED",
  "SCHEDULE_SUPERSEDED",
  "TIMETABLE_IMPORT_UPLOADED",
  "TIMETABLE_IMPORT_APPROVED",
  "TIMETABLE_IMPORT_REJECTED",
];
const RESOURCE_TYPES = ["ALLOCATION", "CR_ASSIGNMENT", "LAB", "FACULTY", "SUBJECT", "SCHEDULE_VERSION", "TIMETABLE_IMPORT"];

/** Read-only by design (PART 44) - no edit/delete action exists anywhere on this screen, matching the backend's immutable audit log. */
export function AuditLogsPage() {
  const [action, setAction] = useState("");
  const [resourceType, setResourceType] = useState("");
  const [page, setPage] = useState(0);
  const [detailRow, setDetailRow] = useState<AuditLogEntry | null>(null);

  const logs = useQuery({
    queryKey: ["audit-logs", action, resourceType, page],
    queryFn: () => auditLogsApi.search({ action: action || undefined, resourceType: resourceType || undefined, page, size: 20 }),
  });

  const columns: Column<AuditLogEntry>[] = [
    { header: "Time", cell: (e) => formatInstant(e.createdAt) },
    { header: "Actor", cell: (e) => `${e.actorDisplayName ?? e.actorEmail} (${titleCase(e.actorRole)})` },
    { header: "Action", cell: (e) => titleCase(e.action) },
    { header: "Resource", cell: (e) => `${titleCase(e.resourceType)}${e.resourceDisplay ? `: ${e.resourceDisplay}` : ` #${e.resourceId}`}` },
    {
      header: "",
      cell: (e) => (
        <button type="button" onClick={() => setDetailRow(e)} className="text-xs font-medium text-indigo-600 hover:underline">
          Details
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Audit Logs</h1>
      <div className="flex flex-wrap gap-3">
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Action</span>
          <select className="input" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {titleCase(a)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Resource Type</span>
          <select className="input" value={resourceType} onChange={(e) => setResourceType(e.target.value)}>
            <option value="">All</option>
            {RESOURCE_TYPES.map((r) => (
              <option key={r} value={r}>
                {titleCase(r)}
              </option>
            ))}
          </select>
        </label>
      </div>

      <AsyncSection isLoading={logs.isLoading} error={logs.error} isEmpty={(logs.data?.content.length ?? 0) === 0} emptyMessage="No matching audit events.">
        <DataTable columns={columns} rows={logs.data?.content ?? []} rowKey={(e) => e.id} />
        {logs.data && <Pagination page={page} totalPages={logs.data.totalPages} totalElements={logs.data.totalElements} onPageChange={setPage} />}
      </AsyncSection>

      {detailRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => setDetailRow(null)}>
          <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-semibold text-slate-900">Audit Event #{detailRow.id}</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <Row label="When" value={formatInstant(detailRow.createdAt)} />
              <Row label="Actor" value={`${detailRow.actorDisplayName ?? detailRow.actorEmail} (${titleCase(detailRow.actorRole)})`} />
              <Row label="Action" value={titleCase(detailRow.action)} />
              <Row label="Resource" value={`${titleCase(detailRow.resourceType)} #${detailRow.resourceId}`} />
              {detailRow.academicTermId && <Row label="Term" value={String(detailRow.academicTermId)} />}
              {detailRow.divisionId && <Row label="Division" value={String(detailRow.divisionId)} />}
            </dl>
            {Object.keys(detailRow.metadata).length > 0 && (
              <div className="mt-3">
                <p className="mb-1 text-sm font-medium text-slate-700">Details</p>
                <ul className="space-y-1 rounded bg-slate-50 p-3 text-sm">
                  {Object.entries(detailRow.metadata).map(([key, value]) => (
                    <li key={key}>
                      <span className="font-medium">{titleCase(key)}:</span> {String(value)}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <button type="button" onClick={() => setDetailRow(null)} className="mt-4 rounded border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="font-medium text-slate-700">{label}</dt>
      <dd className="text-slate-900">{value}</dd>
    </div>
  );
}
