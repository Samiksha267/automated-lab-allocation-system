import { useQuery } from "@tanstack/react-query";
import { labsApi } from "../../api/labs";
import type { ExtraLabSearchResult, RankedCandidate, RejectedCandidate } from "../../api/extraLabs";
import { formatTimeRange } from "../../lib/formatting";

/**
 * Renders the real backend explainability payload (Phase 12/13, reused
 * unmodified) - ranking, scores, and rejection reasons are never computed
 * or invented in React (PART 20/22/77/78 of the Phase 21 brief). Lab
 * capacity/wing/type come from the existing `/api/labs` list, joined
 * client-side by `labId` - no per-candidate lab lookup (avoids N+1).
 */
export function ExtraLabResults({
  result,
  onBook,
  isBooking,
}: {
  result: ExtraLabSearchResult;
  onBook?: (candidate: RankedCandidate) => void;
  isBooking?: boolean;
}) {
  const labs = useQuery({ queryKey: ["labs", "for-search-results"], queryFn: () => labsApi.list() });
  const labsById = new Map((labs.data ?? []).map((l) => [l.id, l]));

  const candidates = result.rankedValidLabs.length > 0 ? result.rankedValidLabs : result.recommendedLab ? [result.recommendedLab] : [];

  return (
    <div className="space-y-4">
      {result.summary.length > 0 && (
        <ul className="list-disc space-y-1 rounded border border-slate-200 bg-slate-50 p-3 pl-8 text-sm text-slate-700">
          {result.summary.map((line, i) => (
            <li key={i}>{line}</li>
          ))}
        </ul>
      )}

      {candidates.length === 0 && result.rejectedLabs.length === 0 && (
        <p className="text-sm text-slate-500">No valid labs are available for the selected time.</p>
      )}

      {candidates.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-slate-900">Available Labs</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {candidates.map((c) => (
              <CandidateCard key={c.labId} candidate={c} lab={labsById.get(c.labId)} onBook={onBook} isBooking={isBooking} />
            ))}
          </div>
        </div>
      )}

      {result.rejectedLabs.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-slate-900">Unavailable Labs</h3>
          <div className="space-y-2">
            {result.rejectedLabs.map((r) => (
              <RejectedCard key={r.labId} rejected={r} />
            ))}
          </div>
        </div>
      )}

      {candidates.length === 0 && result.alternatives.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-slate-900">Alternative Times</h3>
          <div className="space-y-2">
            {result.alternatives.map((alt, i) => (
              <div key={i} className="rounded border border-slate-200 bg-white p-3 text-sm">
                <p className="font-medium text-slate-900">
                  {alt.date} — {formatTimeRange(alt.startTime, alt.endTime)} — Lab {alt.labCode}
                </p>
                <p className="text-slate-600">{alt.explanation}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function CandidateCard({
  candidate,
  lab,
  onBook,
  isBooking,
}: {
  candidate: RankedCandidate;
  lab?: { code: string; capacity: number; location: { wing: string; floor: string; roomNumber: string }; labType: { name: string } };
  onBook?: (candidate: RankedCandidate) => void;
  isBooking?: boolean;
}) {
  return (
    <div className="rounded border border-emerald-300 bg-emerald-50 p-4">
      <p className="text-sm font-semibold text-slate-900">
        #{candidate.rank} {candidate.labCode}
      </p>
      {lab && (
        <p className="text-sm text-slate-700">
          Capacity {lab.capacity} · Wing {lab.location.wing} · {lab.labType.name}
        </p>
      )}
      {/* normalizedScore is a 0-1 ratio (candidate.score / candidate.maxScore) - a real bug found live
          (Docker verification) rendered it unscaled, showing "Recommendation score: 0" for a genuinely
          decent ~41% match. Scaled to the 0-100 display the brief's own example uses. */}
      <p className="mt-1 text-xs text-slate-500">Recommendation score: {Math.round(candidate.normalizedScore * 100)}</p>
      {candidate.scoreFactors.length > 0 && (
        <ul className="mt-2 space-y-0.5 text-xs text-slate-600">
          {candidate.scoreFactors
            .filter((f) => f.applicability !== "NOT_APPLICABLE")
            .map((f, i) => (
              <li key={i}>• {f.explanation}</li>
            ))}
        </ul>
      )}
      {onBook && (
        <button
          type="button"
          disabled={isBooking}
          onClick={() => onBook(candidate)}
          className="mt-3 w-full rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          Book This Lab
        </button>
      )}
    </div>
  );
}

function RejectedCard({ rejected }: { rejected: RejectedCandidate }) {
  return (
    <div className="rounded border border-red-200 bg-red-50 p-3">
      <p className="text-sm font-semibold text-slate-900">{rejected.labCode} unavailable</p>
      <ul className="mt-1 space-y-1 text-sm text-red-800">
        {rejected.violations.map((v, i) => (
          <li key={i}>{v.message}</li>
        ))}
      </ul>
    </div>
  );
}
