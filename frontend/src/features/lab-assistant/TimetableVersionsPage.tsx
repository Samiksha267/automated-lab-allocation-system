import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { scheduleVersionsApi } from "../../api/scheduleVersions";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import { TermSelect } from "../../components/TermSelect";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { describeError } from "../../lib/errorMessages";
import { formatInstant } from "../../lib/formatting";
import type { ScheduleVersion } from "../../api/scheduleVersions";

export function TimetableVersionsPage() {
  const [termId, setTermId] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [publishTarget, setPublishTarget] = useState<ScheduleVersion | null>(null);
  const queryClient = useQueryClient();

  const history = useQuery({
    queryKey: ["schedule-version-history", termId],
    queryFn: () => scheduleVersionsApi.history(termId!),
    enabled: termId !== null,
  });

  const currentlyPublished = history.data?.versions.find((v) => v.status === "PUBLISHED");

  const createDraft = useMutation({
    mutationFn: () => scheduleVersionsApi.createDraft({ academicTermId: termId!, reason: reason || undefined }),
    onSuccess: () => {
      setReason("");
      queryClient.invalidateQueries({ queryKey: ["schedule-version-history", termId] });
    },
  });

  const publish = useMutation({
    mutationFn: (id: number) => scheduleVersionsApi.publish(id),
    onSuccess: () => {
      setPublishTarget(null);
      queryClient.invalidateQueries({ queryKey: ["schedule-version-history", termId] });
    },
  });

  const columns: Column<ScheduleVersion>[] = [
    { header: "Version", cell: (v) => <Link to={`/lab-assistant/timetable-versions/${v.id}`} className="font-medium text-indigo-600 hover:underline">v{v.versionNumber}</Link> },
    { header: "Status", cell: (v) => <StatusBadge status={v.status} /> },
    { header: "Reason", cell: (v) => v.reason ?? "-" },
    { header: "Sessions", cell: (v) => v.allocationCount },
    { header: "Created", cell: (v) => `${formatInstant(v.createdAt)} by ${v.createdByEmail}` },
    { header: "Published", cell: (v) => (v.publishedAt ? `${formatInstant(v.publishedAt)} by ${v.publishedByEmail}` : "-") },
    {
      header: "",
      cell: (v) =>
        v.status === "DRAFT" && (
          <div className="flex gap-2">
            <button type="button" onClick={() => setPublishTarget(v)} className="text-xs font-medium text-indigo-600 hover:underline">
              Publish
            </button>
          </div>
        ),
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Timetable Versions</h1>
        <TermSelect value={termId} onChange={setTermId} />
      </div>

      {termId !== null && (
        <>
          <AsyncSection
            isLoading={history.isLoading}
            error={history.error}
            isEmpty={(history.data?.versions.length ?? 0) === 0}
            emptyMessage="No timetable versions exist yet for this term."
          >
            <DataTable columns={columns} rows={history.data?.versions ?? []} rowKey={(v) => v.id} />
          </AsyncSection>

          <form
            className="flex flex-wrap items-end gap-3 rounded border border-slate-200 bg-white p-4"
            onSubmit={(e) => {
              e.preventDefault();
              createDraft.mutate();
            }}
          >
            <label className="text-sm">
              <span className="mb-1 block font-medium text-slate-700">Reason (required for a revision)</span>
              <input className="input w-72" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Faculty availability updated" />
            </label>
            <button type="submit" disabled={createDraft.isPending} className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
              Create Draft
            </button>
            {createDraft.isError && <span role="alert" className="text-sm text-red-700">{describeError(createDraft.error)}</span>}
          </form>
        </>
      )}

      <ConfirmDialog
        open={publishTarget !== null}
        title={`Publish Version ${publishTarget?.versionNumber}?`}
        body={
          <>
            <p>
              Version {publishTarget?.versionNumber} will become <strong>PUBLISHED</strong>.
            </p>
            {currentlyPublished && (
              <p className="mt-1">
                The currently published version (v{currentlyPublished.versionNumber}) will become <strong>SUPERSEDED</strong>.
              </p>
            )}
            <p className="mt-1">Students and CRs will immediately begin seeing this version's timetable.</p>
          </>
        }
        confirmLabel="Publish"
        isPending={publish.isPending}
        onCancel={() => setPublishTarget(null)}
        onConfirm={() => publish.mutate(publishTarget!.id)}
      />
      {publish.isError && <p role="alert" className="text-sm text-red-700">{describeError(publish.error)}</p>}
    </div>
  );
}
