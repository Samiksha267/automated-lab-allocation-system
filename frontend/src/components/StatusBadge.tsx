import { titleCase } from "../lib/formatting";

/**
 * One consistent status-badge component for every status enum in this
 * project (PART 61) - color AND text together, never color alone (PART 59:
 * "color is not the only status indicator").
 */
const STATUS_STYLES: Record<string, string> = {
  // ScheduleVersion
  DRAFT: "bg-amber-100 text-amber-800 border-amber-300",
  PUBLISHED: "bg-emerald-100 text-emerald-800 border-emerald-300",
  SUPERSEDED: "bg-slate-100 text-slate-600 border-slate-300",
  // TimetableImport
  UPLOADED: "bg-sky-100 text-sky-800 border-sky-300",
  VALIDATED: "bg-emerald-100 text-emerald-800 border-emerald-300",
  NEEDS_REVIEW: "bg-amber-100 text-amber-800 border-amber-300",
  APPROVED: "bg-emerald-100 text-emerald-800 border-emerald-300",
  REJECTED: "bg-slate-100 text-slate-600 border-slate-300",
  FAILED: "bg-red-100 text-red-800 border-red-300",
  // Import row / allocation
  VALID: "bg-emerald-100 text-emerald-800 border-emerald-300",
  WARNING: "bg-amber-100 text-amber-800 border-amber-300",
  ERROR: "bg-red-100 text-red-800 border-red-300",
  CANCELLED: "bg-slate-100 text-slate-600 border-slate-300",
  ACTIVE: "bg-emerald-100 text-emerald-800 border-emerald-300",
  INACTIVE: "bg-slate-100 text-slate-600 border-slate-300",
};

const DEFAULT_STYLE = "bg-slate-100 text-slate-700 border-slate-300";

export function StatusBadge({ status }: { status: string | null | undefined }) {
  if (!status) return null;
  const style = STATUS_STYLES[status] ?? DEFAULT_STYLE;
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${style}`}>
      {titleCase(status)}
    </span>
  );
}
