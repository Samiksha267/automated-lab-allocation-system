import { useState, type ChangeEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { scheduleVersionsApi } from "../../api/scheduleVersions";
import { timetableImportsApi, type TimetableImportStatus } from "../../api/timetableImports";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { Pagination } from "../../components/Pagination";
import { StatusBadge } from "../../components/StatusBadge";
import { TermSelect } from "../../components/TermSelect";
import { describeError } from "../../lib/errorMessages";
import { formatInstant } from "../../lib/formatting";
import type { TimetableImportRecord } from "../../api/timetableImports";

const STATUSES: TimetableImportStatus[] = ["UPLOADED", "NEEDS_REVIEW", "VALIDATED", "APPROVED", "REJECTED", "FAILED"];

export function ImportsPage() {
  const [searchParams] = useSearchParams();
  const [termId, setTermId] = useState<number | null>(searchParams.get("academicTermId") ? Number(searchParams.get("academicTermId")) : null);
  const [status, setStatus] = useState<TimetableImportStatus | "">("");
  const [page, setPage] = useState(0);
  const [showUpload, setShowUpload] = useState(searchParams.get("upload") === "1");

  const imports = useQuery({
    queryKey: ["timetable-imports", termId, status, page],
    queryFn: () => timetableImportsApi.list({ academicTermId: termId ?? undefined, status: status || undefined, page }),
    enabled: termId !== null,
  });

  const columns: Column<TimetableImportRecord>[] = [
    { header: "File", cell: (i) => <Link to={`/lab-assistant/imports/${i.id}`} className="font-medium text-indigo-600 hover:underline">{i.originalFilename}</Link> },
    { header: "Status", cell: (i) => <StatusBadge status={i.status} /> },
    { header: "Rows", cell: (i) => i.summary.totalRows },
    { header: "Valid", cell: (i) => i.summary.validRows },
    { header: "Warnings", cell: (i) => i.summary.warningRows },
    { header: "Errors", cell: (i) => i.summary.errorRows },
    { header: "Uploaded", cell: (i) => formatInstant(i.uploadedAt) },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Timetable Imports</h1>
        <button type="button" onClick={() => setShowUpload((s) => !s)} className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700">
          {showUpload ? "Cancel" : "Upload PDF"}
        </button>
      </div>

      {showUpload && (
        <UploadForm
          defaultTermId={termId}
          defaultVersionId={searchParams.get("scheduleVersionId") ? Number(searchParams.get("scheduleVersionId")) : null}
          onDone={() => setShowUpload(false)}
        />
      )}

      <div className="flex flex-wrap items-end gap-3">
        <TermSelect value={termId} onChange={setTermId} />
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Status</span>
          <select className="input" value={status} onChange={(e) => setStatus(e.target.value as TimetableImportStatus | "")}>
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replaceAll("_", " ")}
              </option>
            ))}
          </select>
        </label>
      </div>

      {termId === null ? (
        <p className="text-sm text-slate-500">Select an academic term to see its timetable imports.</p>
      ) : (
        <AsyncSection
          isLoading={imports.isLoading}
          error={imports.error}
          isEmpty={(imports.data?.content.length ?? 0) === 0}
          emptyMessage="No timetable imports yet. Upload a PDF to create the first one."
        >
          <DataTable columns={columns} rows={imports.data?.content ?? []} rowKey={(i) => i.id} />
          {imports.data && (
            <Pagination page={page} totalPages={imports.data.totalPages} totalElements={imports.data.totalElements} onPageChange={setPage} />
          )}
        </AsyncSection>
      )}
    </div>
  );
}

function UploadForm({ defaultTermId, defaultVersionId, onDone }: { defaultTermId: number | null; defaultVersionId: number | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [termId, setTermId] = useState<number | null>(defaultTermId);
  const [versionId, setVersionId] = useState<number | "">(defaultVersionId ?? "");
  const [file, setFile] = useState<File | null>(null);
  const [clientError, setClientError] = useState<string | null>(null);

  const history = useQuery({
    queryKey: ["schedule-version-history", termId],
    queryFn: () => scheduleVersionsApi.history(termId!),
    enabled: termId !== null,
  });
  const draftVersions = history.data?.versions.filter((v) => v.status === "DRAFT") ?? [];

  const upload = useMutation({
    mutationFn: () => timetableImportsApi.upload(termId!, versionId as number, file!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["timetable-imports"] });
      onDone();
    },
  });

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0] ?? null;
    setClientError(null);
    if (selected) {
      if (!selected.name.toLowerCase().endsWith(".pdf")) {
        setClientError("Only .pdf files are supported.");
        setFile(null);
        return;
      }
      if (selected.size > 10 * 1024 * 1024) {
        setClientError("This file exceeds the 10 MB upload limit.");
        setFile(null);
        return;
      }
    }
    setFile(selected);
  }

  return (
    <form
      className="space-y-3 rounded border border-slate-200 bg-white p-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (file && termId !== null && versionId !== "") upload.mutate();
      }}
    >
      <p className="text-sm text-slate-600">
        Supported format: a text-based PDF with one session per line, pipe-delimited (Day | Start | End | Subject | Faculty | Lab | Division | Batch). See
        the PDF import guide for details.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <TermSelect
          value={termId}
          onChange={(id) => {
            setTermId(id);
            setVersionId("");
          }}
        />
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Target Draft Version</span>
          <select required className="input" value={versionId} onChange={(e) => setVersionId(e.target.value ? Number(e.target.value) : "")} disabled={termId === null}>
            <option value="">Select…</option>
            {draftVersions.map((v) => (
              <option key={v.id} value={v.id}>
                v{v.versionNumber} {v.reason ? `— ${v.reason}` : ""}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">PDF File</span>
          {/* No `accept` attribute - the OS file picker's own type filtering is inconsistent across
              browsers and can silently hide a mislabeled PDF; this project's own clear client-side
              message (extension/size) plus backend signature validation are the real defenses. */}
          <input required type="file" onChange={handleFileChange} className="text-sm" />
        </label>
        <button
          type="submit"
          disabled={!file || termId === null || versionId === "" || upload.isPending}
          className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {upload.isPending ? "Uploading and processing timetable…" : "Upload"}
        </button>
      </div>
      {termId !== null && draftVersions.length === 0 && (
        <p className="text-sm text-amber-700">This term has no DRAFT version yet — create one on the Timetable Versions page first.</p>
      )}
      {clientError && <p role="alert" className="text-sm text-red-700">{clientError}</p>}
      {upload.isError && <p role="alert" className="text-sm text-red-700">{describeError(upload.error)}</p>}
    </form>
  );
}
