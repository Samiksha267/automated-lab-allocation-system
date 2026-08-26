import { useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { timetableImportsApi } from "../../api/timetableImports";
import { AsyncSection } from "../../components/AsyncSection";
import { TermSelect } from "../../components/TermSelect";
import { formatTimeRange } from "../../lib/formatting";

/**
 * Conflicts are surfaced through the Phase 19 import-validation pipeline
 * (PART 41 - "reuse existing conflict APIs") rather than a separate,
 * newly-invented conflicts endpoint: every ERROR/WARNING message on a
 * NEEDS_REVIEW import's rows already IS this project's conflict-detection
 * output (the same constraint engine every other scheduling decision uses).
 * This screen aggregates that real data across every import awaiting
 * review for the selected term - no fabricated conflict list.
 */
export function ConflictsPage() {
  const [termId, setTermId] = useState<number | null>(null);

  const needsReview = useQuery({
    queryKey: ["timetable-imports", "needs-review-full", termId],
    queryFn: () => timetableImportsApi.list({ academicTermId: termId!, status: "NEEDS_REVIEW", size: 50 }),
    enabled: termId !== null,
  });

  const details = useQueries({
    queries: (needsReview.data?.content ?? []).map((imp) => ({
      queryKey: ["timetable-imports", imp.id],
      queryFn: () => timetableImportsApi.detail(imp.id),
      enabled: needsReview.isSuccess,
    })),
  });

  const anyDetailLoading = details.some((d) => d.isLoading);
  const anyDetailError = details.find((d) => d.error)?.error;

  const conflictRows = details.flatMap((d, index) => {
    const imp = needsReview.data?.content[index];
    if (!d.data || !imp) return [];
    return d.data.rows
      .filter((row) => row.validationMessages.length > 0)
      .map((row) => ({ importId: imp.id, filename: imp.originalFilename, row }));
  });

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Conflicts</h1>
      <p className="text-sm text-slate-500">Conflicts detected while reviewing timetable imports awaiting correction for the selected term.</p>
      <TermSelect value={termId} onChange={setTermId} />

      {termId === null ? (
        <p className="text-sm text-slate-500">Select an academic term.</p>
      ) : (
        <AsyncSection
          isLoading={needsReview.isLoading || anyDetailLoading}
          error={needsReview.error ?? anyDetailError}
          isEmpty={conflictRows.length === 0}
          emptyMessage="No conflicts detected — every import for this term is clean or fully approved/rejected."
        >
          <div className="space-y-3">
            {conflictRows.map(({ importId, filename, row }) => (
              <div key={`${importId}-${row.id}`} className="rounded border border-slate-200 bg-white p-4">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold text-slate-900">
                    {row.subjectCode ?? row.rawSubject} — {row.divisionCode ?? row.rawDivision}
                    {row.batchCode ? ` / ${row.batchCode}` : ""} —{" "}
                    {row.normalizedStartTime ? formatTimeRange(row.normalizedStartTime, row.normalizedEndTime) : `${row.rawStartTime}-${row.rawEndTime}`}
                  </p>
                  <Link to={`/lab-assistant/imports/${importId}`} className="text-xs font-medium text-indigo-600 hover:underline">
                    Review in {filename} →
                  </Link>
                </div>
                <ul className="mt-2 space-y-1 text-sm">
                  {row.validationMessages.map((m, i) => (
                    <li key={i} className={m.severity === "ERROR" ? "text-red-700" : "text-amber-700"}>
                      {m.message}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </AsyncSection>
      )}
    </div>
  );
}
