import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../../api/academic";
import { AsyncSection } from "../../components/AsyncSection";
import { Card } from "../../components/Card";
import { useCrAssignment } from "./CrAssignmentContext";

/**
 * The CR's assigned class, resolved entirely from the authenticated
 * session (PART 7/8) - no division selector exists anywhere on this page
 * or any other CR page; there is nothing to choose.
 */
export function MyClassPage() {
  const { assignment, isLoading, error } = useCrAssignment();
  const batches = useQuery({
    queryKey: ["batches", assignment?.divisionId],
    queryFn: () => academicApi.listBatches(assignment!.divisionId),
    enabled: !!assignment,
  });

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">My Class</h1>
      <AsyncSection isLoading={isLoading} error={error}>
        {assignment && (
          <Card>
            <p className="text-lg font-semibold text-slate-900">{assignment.program}</p>
            <p className="text-slate-700">{assignment.stream}</p>
            <p className="text-slate-700">
              Year {assignment.year} — Division {assignment.divisionCode}
            </p>
            <p className="mt-2 text-sm text-slate-500">Term: {assignment.academicTerm}</p>

            <div className="mt-4">
              <p className="mb-1 text-sm font-semibold text-slate-900">Batches</p>
              <AsyncSection isLoading={batches.isLoading} error={batches.error} isEmpty={(batches.data?.length ?? 0) === 0} emptyMessage="No batches defined for this division.">
                <div className="flex flex-wrap gap-2">
                  {batches.data?.map((b) => (
                    <span key={b.id} className="rounded-full border border-slate-300 bg-white px-3 py-1 text-sm text-slate-700">
                      {b.code}
                    </span>
                  ))}
                </div>
              </AsyncSection>
            </div>
          </Card>
        )}
      </AsyncSection>
    </div>
  );
}
