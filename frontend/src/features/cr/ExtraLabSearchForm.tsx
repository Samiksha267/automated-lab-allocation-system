import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../../api/academic";
import { subjectsApi } from "../../api/subjects";
import type { ExtraLabSearchRequest, TargetType } from "../../api/extraLabs";
import { useCrAssignment } from "./CrAssignmentContext";

export interface ExtraLabFormState {
  subjectId: string;
  targetType: TargetType;
  batchId: string;
  allocationDate: string;
  startTime: string;
  endTime: string;
}

const EMPTY_FORM: ExtraLabFormState = { subjectId: "", targetType: "DIVISION", batchId: "", allocationDate: "", startTime: "", endTime: "" };

/**
 * Subject/batch/date/time inputs shared by Available Labs (search-only) and
 * Schedule Extra Lab (search-then-book) - the CR's division is always fixed
 * from {@link useCrAssignment}; only batch (within that division) is ever
 * user-selectable (PART 8/16/18 of the Phase 21 brief).
 */
export function ExtraLabSearchForm({
  value,
  onChange,
  onSubmit,
  submitLabel,
  isSubmitting,
}: {
  value: ExtraLabFormState;
  onChange: (next: ExtraLabFormState) => void;
  onSubmit: (request: ExtraLabSearchRequest) => void;
  submitLabel: string;
  isSubmitting: boolean;
}) {
  const { assignment } = useCrAssignment();
  const [clientError, setClientError] = useState<string | null>(null);

  const division = useQuery({
    queryKey: ["divisions", assignment?.divisionId],
    queryFn: () => academicApi.getDivision(assignment!.divisionId),
    enabled: !!assignment,
  });
  const subjects = useQuery({
    queryKey: ["subjects", division.data?.academicYearId],
    queryFn: () => subjectsApi.list(division.data!.academicYearId),
    enabled: !!division.data,
  });
  const batches = useQuery({
    queryKey: ["batches", assignment?.divisionId],
    queryFn: () => academicApi.listBatches(assignment!.divisionId),
    enabled: !!assignment,
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setClientError(null);
    if (!value.subjectId) return setClientError("Select a subject.");
    if (value.targetType === "BATCH" && !value.batchId) return setClientError("Select a batch.");
    if (!value.allocationDate) return setClientError("Select a date.");
    if (!value.startTime || !value.endTime) return setClientError("Select a start and end time.");
    if (value.startTime >= value.endTime) return setClientError("End time must be after start time.");

    onSubmit({
      subjectId: Number(value.subjectId),
      targetType: value.targetType,
      batchId: value.targetType === "BATCH" ? Number(value.batchId) : undefined,
      allocationDate: value.allocationDate,
      startTime: value.startTime,
      endTime: value.endTime,
    });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3 rounded border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap gap-3">
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Subject</span>
          <select className="input" value={value.subjectId} onChange={(e) => onChange({ ...value, subjectId: e.target.value })}>
            <option value="">Select…</option>
            {subjects.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.code} — {s.name}
              </option>
            ))}
          </select>
        </label>

        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Target</span>
          <select
            className="input"
            value={value.targetType}
            onChange={(e) => onChange({ ...value, targetType: e.target.value as TargetType, batchId: "" })}
          >
            <option value="DIVISION">Whole division</option>
            <option value="BATCH">Specific batch</option>
          </select>
        </label>

        {value.targetType === "BATCH" && (
          <label className="text-sm">
            <span className="mb-1 block font-medium text-slate-700">Batch</span>
            <select className="input" value={value.batchId} onChange={(e) => onChange({ ...value, batchId: e.target.value })}>
              <option value="">Select…</option>
              {batches.data?.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.code}
                </option>
              ))}
            </select>
          </label>
        )}

        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Date</span>
          <input required type="date" className="input" value={value.allocationDate} onChange={(e) => onChange({ ...value, allocationDate: e.target.value })} />
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Start</span>
          <input required type="time" className="input" value={value.startTime} onChange={(e) => onChange({ ...value, startTime: e.target.value })} />
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">End</span>
          <input required type="time" className="input" value={value.endTime} onChange={(e) => onChange({ ...value, endTime: e.target.value })} />
        </label>
      </div>

      <button type="submit" disabled={isSubmitting} className="rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
        {isSubmitting ? "Searching…" : submitLabel}
      </button>
      {clientError && <p role="alert" className="text-sm text-red-700">{clientError}</p>}
    </form>
  );
}

export { EMPTY_FORM };
