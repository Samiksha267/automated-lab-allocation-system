import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../api/academic";

/**
 * Every screen that depends on an academic term shows/selects it explicitly
 * (PART 21 of the Phase 20 brief) - this project supports multiple
 * simultaneously-ACTIVE terms server-side, so no screen may silently assume
 * "the" current term.
 */
export function TermSelect({ value, onChange }: { value: number | null; onChange: (termId: number) => void }) {
  const { data: terms, isLoading } = useQuery({ queryKey: ["academic-terms"], queryFn: academicApi.listAcademicTerms });

  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="font-medium text-slate-700">Academic Term</span>
      <select
        className="rounded border border-slate-300 px-2 py-1.5 text-sm"
        value={value ?? ""}
        disabled={isLoading}
        onChange={(e) => onChange(Number(e.target.value))}
      >
        <option value="" disabled>
          {isLoading ? "Loading…" : "Select a term"}
        </option>
        {terms?.map((term) => (
          <option key={term.id} value={term.id}>
            {term.displayName} ({term.status})
          </option>
        ))}
      </select>
    </label>
  );
}
