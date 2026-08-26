import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { labsApi } from "../../api/labs";
import { timetableImportsApi, type ImportRow, type RowCorrection } from "../../api/timetableImports";
import { AsyncSection } from "../../components/AsyncSection";
import { StatCard } from "../../components/Card";
import { StatusBadge } from "../../components/StatusBadge";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { describeError } from "../../lib/errorMessages";
import { formatTimeRange } from "../../lib/formatting";

/**
 * The Phase 19 staged-import review workflow (PART 33 of the Phase 20 brief)
 * - the most consequential screen in this phase. Corrections only ever
 * update local state after the backend confirms revalidation (PART 37 -
 * never optimistically marked VALID before the server responds).
 */
export function ImportReviewPage() {
  const { importId } = useParams();
  const id = Number(importId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const detail = useQuery({ queryKey: ["timetable-imports", id], queryFn: () => timetableImportsApi.detail(id) });
  const [correctingRow, setCorrectingRow] = useState<ImportRow | null>(null);
  const [showApprove, setShowApprove] = useState(false);
  const [showReject, setShowReject] = useState(false);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["timetable-imports", id] });

  const approve = useMutation({
    mutationFn: () => timetableImportsApi.approve(id),
    onSuccess: () => {
      setShowApprove(false);
      invalidate();
    },
  });
  const reject = useMutation({
    mutationFn: () => timetableImportsApi.reject(id),
    onSuccess: () => {
      setShowReject(false);
      invalidate();
    },
  });

  const imp = detail.data?.importResponse;
  const editable = imp ? imp.status === "NEEDS_REVIEW" || imp.status === "VALIDATED" || imp.status === "UPLOADED" : false;

  return (
    <div className="space-y-4">
      <Link to="/lab-assistant/imports" className="text-sm text-indigo-600 hover:underline">
        ← Back to Imports
      </Link>

      <AsyncSection isLoading={detail.isLoading} error={detail.error}>
        {imp && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-xl font-semibold text-slate-900">{imp.originalFilename}</h1>
              <StatusBadge status={imp.status} />
            </div>
            {imp.failureReason && (
              <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
                {imp.failureReason}
              </p>
            )}

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
              <StatCard label="Total Rows" value={imp.summary.totalRows} />
              <StatCard label="Valid" value={imp.summary.validRows} />
              <StatCard label="Warnings" value={imp.summary.warningRows} />
              <StatCard label="Errors" value={imp.summary.errorRows} />
              <StatCard label="Corrected" value={imp.summary.correctedRows} />
            </div>

            {editable && (
              <div className="flex flex-wrap gap-3">
                <button
                  type="button"
                  disabled={imp.status !== "VALIDATED"}
                  onClick={() => setShowApprove(true)}
                  className="rounded bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-40"
                >
                  Approve Import
                </button>
                <button type="button" onClick={() => setShowReject(true)} className="rounded border border-red-300 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50">
                  Reject Import
                </button>
                {imp.status === "NEEDS_REVIEW" && (
                  <span className="self-center text-sm text-amber-700">Resolve every ERROR row below before this import can be approved.</span>
                )}
              </div>
            )}

            {imp.status === "APPROVED" && (
              <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900">
                Approved — allocations were created inside Draft Version. This does <strong>not</strong> publish the timetable. Go to{" "}
                <Link to="/lab-assistant/timetable-versions" className="underline">
                  Timetable Versions
                </Link>{" "}
                to review and publish when ready.
              </div>
            )}

            <div className="overflow-x-auto rounded border border-slate-200">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50">
                  <tr>
                    {["Row", "Day", "Time", "Subject", "Faculty", "Lab", "Division", "Batch", "Status", "Issues", "Action"].map((h) => (
                      <th key={h} scope="col" className="px-3 py-2 text-left font-medium text-slate-600 whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {detail.data?.rows.map((row) => (
                    <tr key={row.id} className="align-top hover:bg-slate-50">
                      <td className="px-3 py-2">{row.rowNumber}</td>
                      <td className="px-3 py-2">{row.normalizedDay ?? <RawValue value={row.rawDay} />}</td>
                      <td className="px-3 py-2">{row.normalizedStartTime ? formatTimeRange(row.normalizedStartTime, row.normalizedEndTime) : <RawValue value={`${row.rawStartTime}-${row.rawEndTime}`} />}</td>
                      <td className="px-3 py-2">{row.subjectCode ?? <RawValue value={row.rawSubject} />}</td>
                      <td className="px-3 py-2">{row.facultyName ?? <RawValue value={row.rawFaculty} />}</td>
                      <td className="px-3 py-2">{row.labCode ?? <RawValue value={row.rawLab} />}</td>
                      <td className="px-3 py-2">{row.divisionCode ?? <RawValue value={row.rawDivision} />}</td>
                      <td className="px-3 py-2">{row.batchCode ?? <RawValue value={row.rawBatch} />}</td>
                      <td className="px-3 py-2">
                        <StatusBadge status={row.validationStatus} />
                        {row.corrected && <span className="ml-1 text-xs text-slate-400">(corrected)</span>}
                      </td>
                      <td className="max-w-xs px-3 py-2">
                        {row.validationMessages.length === 0 ? (
                          <span className="text-slate-400">-</span>
                        ) : (
                          <ul className="space-y-1">
                            {row.validationMessages.map((m, i) => (
                              <li key={i} className={m.severity === "ERROR" ? "text-red-700" : "text-amber-700"}>
                                {m.message}
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        {editable && (
                          <button type="button" onClick={() => setCorrectingRow(row)} className="text-xs font-medium text-indigo-600 hover:underline">
                            Correct
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </AsyncSection>

      {correctingRow && <CorrectionDialog importId={id} row={correctingRow} onClose={() => setCorrectingRow(null)} onCorrected={invalidate} />}

      <ConfirmDialog
        open={showApprove}
        title="Approve this import?"
        body={
          <>
            <p>
              Approving this import will create confirmed allocations in the target draft schedule version ({imp?.summary.validRows} row
              {imp?.summary.validRows === 1 ? "" : "s"}).
            </p>
            <p className="mt-2 font-medium">This will NOT publish the timetable to students.</p>
            <p className="mt-1 text-slate-500">You will still need to review and publish the timetable version separately.</p>
          </>
        }
        confirmLabel="Approve"
        isPending={approve.isPending}
        onCancel={() => setShowApprove(false)}
        onConfirm={() => approve.mutate()}
      />
      {approve.isError && <p role="alert" className="text-sm text-red-700">{describeError(approve.error)}</p>}
      {approve.isSuccess && (
        <div className="rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900">
          {approve.data.allocationsCreated} allocation{approve.data.allocationsCreated === 1 ? "" : "s"} created in Draft Version{" "}
          {approve.data.importResponse.scheduleVersionId}. Next: review and publish that version when ready.
          <button type="button" className="ml-2 underline" onClick={() => navigate("/lab-assistant/timetable-versions")}>
            Go to Timetable Versions
          </button>
        </div>
      )}

      <ConfirmDialog
        open={showReject}
        title="Reject this import?"
        body="Rejected imports remain in history and can never create allocations. This cannot be undone."
        confirmLabel="Reject"
        danger
        isPending={reject.isPending}
        onCancel={() => setShowReject(false)}
        onConfirm={() => reject.mutate()}
      />
    </div>
  );
}

function RawValue({ value }: { value: string | null }) {
  return <span className="text-slate-400" title="From the uploaded PDF, not yet resolved">{value ?? "-"}</span>;
}

function CorrectionDialog({
  importId,
  row,
  onClose,
  onCorrected,
}: {
  importId: number;
  row: ImportRow;
  onClose: () => void;
  onCorrected: () => void;
}) {
  const labs = useQuery({ queryKey: ["labs", "for-correction"], queryFn: () => labsApi.list() });
  const [labId, setLabId] = useState(row.labId ? String(row.labId) : "");
  const [startTime, setStartTime] = useState(row.normalizedStartTime ?? "");
  const [endTime, setEndTime] = useState(row.normalizedEndTime ?? "");

  const correct = useMutation({
    mutationFn: () => {
      const body: RowCorrection = {};
      if (labId) body.labId = Number(labId);
      if (startTime) body.startTime = startTime;
      if (endTime) body.endTime = endTime;
      return timetableImportsApi.correctRow(importId, row.id, body);
    },
    onSuccess: () => {
      onCorrected();
      onClose();
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
        <h2 className="text-lg font-semibold text-slate-900">Correct Row {row.rowNumber}</h2>
        <div className="mt-3 space-y-2 rounded bg-slate-50 p-3 text-sm">
          <p>
            <span className="font-medium">PDF value (lab):</span> {row.rawLab}
          </p>
          <p>
            <span className="font-medium">Currently mapped:</span> {row.labCode ?? "unresolved"}
          </p>
        </div>

        <div className="mt-4 space-y-3">
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Lab</span>
            <select className="input" value={labId} onChange={(e) => setLabId(e.target.value)}>
              <option value="">Keep current</option>
              {labs.data?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.code} — {l.name}
                </option>
              ))}
            </select>
          </label>
          <div className="flex gap-3">
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">Start Time</span>
              <input type="time" className="input" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">End Time</span>
              <input type="time" className="input" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
            </label>
          </div>
        </div>

        {correct.isError && <p role="alert" className="mt-2 text-sm text-red-700">{describeError(correct.error)}</p>}

        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
            Cancel
          </button>
          <button
            type="button"
            disabled={correct.isPending}
            onClick={() => correct.mutate()}
            className="rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {correct.isPending ? "Saving…" : "Save & Revalidate"}
          </button>
        </div>
      </div>
    </div>
  );
}
