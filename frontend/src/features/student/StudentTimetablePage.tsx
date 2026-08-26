import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { academicApi } from "../../api/academic";
import { timetableApi, type AllocationSummary } from "../../api/scheduleVersions";
import { AsyncSection } from "../../components/AsyncSection";
import { Pagination } from "../../components/Pagination";
import { dayOfWeekName, formatTimeRange } from "../../lib/formatting";

const DAY_ORDER = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const DAY_OPTIONS = ["All Days", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

function toId(value: string | null): number | undefined {
  if (!value) return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

function labLocation(row: AllocationSummary): string {
  const parts = [row.labWing && `Wing ${row.labWing}`, row.labFloor && `Floor ${row.labFloor}`, row.labRoomNumber && `Room ${row.labRoomNumber}`].filter(
    Boolean,
  );
  return parts.length > 0 ? parts.join(", ") : "Location not recorded";
}

/**
 * The Student's read-only timetable (Phase 22). Deliberately reuses
 * `timetableApi.current` unchanged from Phase 18/21 - it always resolves the
 * term's currently PUBLISHED schedule version (never the highest version
 * number, never a DRAFT/SUPERSEDED row), exactly the same guarantee the CR
 * timetable already relies on (PART 5/6/14).
 *
 * <p>This project has no Student-enrollment entity linking an account to a
 * division/batch (PART 22) - unlike the CR flow, which resolves its own
 * division from `/api/cr-assignments/me`, a Student must pick their own
 * program/stream/year/division/batch through the same dependent academic
 * hierarchy filters a Lab Assistant uses elsewhere. This is a real, documented
 * limitation (docs/15-DESIGN-DECISIONS.md), not a silently invented identity
 * model.
 */
export function StudentTimetablePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const programId = toId(searchParams.get("programId"));
  const streamId = toId(searchParams.get("streamId"));
  const yearId = toId(searchParams.get("yearId"));
  const divisionId = toId(searchParams.get("divisionId"));
  const batchId = toId(searchParams.get("batchId"));
  const academicTermId = toId(searchParams.get("academicTermId"));
  const day = searchParams.get("day") ?? "All Days";
  const [page, setPage] = useState(0);

  function setFilters(patch: Record<string, string | undefined>) {
    const next = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined) next.delete(key);
      else next.set(key, value);
    }
    setSearchParams(next, { replace: true });
    setPage(0);
  }

  const terms = useQuery({ queryKey: ["academic-terms"], queryFn: academicApi.listAcademicTerms });
  useEffect(() => {
    if (!academicTermId && terms.data && terms.data.length > 0) {
      const active = terms.data.find((t) => t.status === "ACTIVE") ?? terms.data[0];
      setFilters({ academicTermId: String(active.id) });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [terms.data]);

  const programs = useQuery({ queryKey: ["programs"], queryFn: academicApi.listPrograms });
  const streams = useQuery({
    queryKey: ["streams", programId],
    queryFn: () => academicApi.listStreams(programId!),
    enabled: !!programId,
  });
  const years = useQuery({
    queryKey: ["academic-years", streamId],
    queryFn: () => academicApi.listAcademicYears(streamId!),
    enabled: !!streamId,
  });
  const divisions = useQuery({
    queryKey: ["divisions", yearId],
    queryFn: () => academicApi.listDivisions(yearId!),
    enabled: !!yearId,
  });
  const batches = useQuery({
    queryKey: ["batches", divisionId],
    queryFn: () => academicApi.listBatches(divisionId!),
    enabled: !!divisionId,
  });

  const canFetchTimetable = !!academicTermId && !!divisionId;
  const timetable = useQuery({
    queryKey: ["student", "timetable", academicTermId, divisionId, batchId, page],
    queryFn: () => timetableApi.current({ academicTermId: academicTermId!, divisionId: divisionId!, batchId, page }),
    enabled: canFetchTimetable,
  });

  const rows = timetable.data?.content ?? [];
  // Day filtering happens over the current fetched page, matching the existing CR-timetable subject-filter
  // pattern (MyTimetablePage.tsx) - a term's single division/batch timetable is small enough in practice that
  // this is a documented, deliberate simplification rather than a full server-side day filter (PART 17/22).
  const filteredRows = day === "All Days" ? rows : rows.filter((r) => dayOfWeekName(r.allocationDate) === day);

  const grouped = useMemo(() => {
    const byDay = new Map<string, AllocationSummary[]>();
    for (const row of filteredRows) {
      const d = dayOfWeekName(row.allocationDate);
      if (!byDay.has(d)) byDay.set(d, []);
      byDay.get(d)!.push(row);
    }
    for (const list of byDay.values()) {
      list.sort((a, b) => a.startTime.localeCompare(b.startTime));
    }
    return DAY_ORDER.filter((d) => byDay.has(d)).map((d) => ({ day: d, rows: byDay.get(d)! }));
  }, [filteredRows]);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">My Timetable</h1>

      <div className="flex flex-wrap gap-3">
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Academic Term</span>
          <select
            className="input"
            aria-label="Academic Term"
            value={academicTermId ?? ""}
            onChange={(e) => setFilters({ academicTermId: e.target.value || undefined })}
          >
            {terms.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Program</span>
          <select
            className="input"
            aria-label="Program"
            value={programId ?? ""}
            onChange={(e) => setFilters({ programId: e.target.value || undefined, streamId: undefined, yearId: undefined, divisionId: undefined, batchId: undefined })}
          >
            <option value="">Select program</option>
            {programs.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Stream</span>
          <select
            className="input"
            aria-label="Stream"
            value={streamId ?? ""}
            disabled={!programId}
            onChange={(e) => setFilters({ streamId: e.target.value || undefined, yearId: undefined, divisionId: undefined, batchId: undefined })}
          >
            <option value="">Select stream</option>
            {streams.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.code}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Year</span>
          <select
            className="input"
            aria-label="Year"
            value={yearId ?? ""}
            disabled={!streamId}
            onChange={(e) => setFilters({ yearId: e.target.value || undefined, divisionId: undefined, batchId: undefined })}
          >
            <option value="">Select year</option>
            {years.data?.map((y) => (
              <option key={y.id} value={y.id}>
                Year {y.yearNumber}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Division</span>
          <select
            className="input"
            aria-label="Division"
            value={divisionId ?? ""}
            disabled={!yearId}
            onChange={(e) => setFilters({ divisionId: e.target.value || undefined, batchId: undefined })}
          >
            <option value="">Select division</option>
            {divisions.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.code}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Batch</span>
          <select
            className="input"
            aria-label="Batch"
            value={batchId ?? ""}
            disabled={!divisionId}
            onChange={(e) => setFilters({ batchId: e.target.value || undefined })}
          >
            <option value="">All batches (division-wide + every batch)</option>
            {batches.data?.map((b) => (
              <option key={b.id} value={b.id}>
                {b.code}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block font-medium text-slate-700">Day</span>
          <select className="input" aria-label="Day" value={day} onChange={(e) => setFilters({ day: e.target.value === "All Days" ? undefined : e.target.value })}>
            {DAY_OPTIONS.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!canFetchTimetable ? (
        <p className="py-8 text-center text-sm text-slate-500">
          Choose your program, stream, year, and division to view the timetable.
        </p>
      ) : (
        <AsyncSection
          isLoading={timetable.isLoading}
          error={timetable.error}
          isEmpty={rows.length === 0}
          emptyMessage="No published timetable is currently available."
        >
          {filteredRows.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">No practicals scheduled on {day}.</p>
          ) : (
            <div className="space-y-4">
              {grouped.map((group) => (
                <section key={group.day} className="rounded border border-slate-200 bg-white">
                  <h2 className="border-b border-slate-200 bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-800">{group.day}</h2>
                  <ul className="divide-y divide-slate-100">
                    {group.rows.map((row) => (
                      <li key={row.allocationId} className="flex flex-col gap-1 px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="font-medium text-slate-900">
                            {row.subjectCode} — {row.subjectName}
                          </p>
                          <p className="text-sm text-slate-600">
                            {row.facultyName} · {row.labCode} ({labLocation(row)})
                          </p>
                          <p className="text-xs text-slate-500">{row.batchCode ? `${row.divisionCode} / ${row.batchCode}` : row.divisionCode}</p>
                        </div>
                        <div className="text-sm font-medium text-slate-700">{formatTimeRange(row.startTime, row.endTime)}</div>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )}
          {timetable.data && (
            <Pagination page={page} totalPages={timetable.data.totalPages} totalElements={timetable.data.totalElements} onPageChange={setPage} />
          )}
        </AsyncSection>
      )}
    </div>
  );
}
