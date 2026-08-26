import { createContext, useContext, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { crAssignmentsApi, type CurrentCrAssignment } from "../../api/crAssignments";

interface CrAssignmentContextValue {
  assignment: CurrentCrAssignment | undefined;
  isLoading: boolean;
  error: unknown;
}

const CrAssignmentContext = createContext<CrAssignmentContextValue | null>(null);

/**
 * The single source of the CR's scope (division/term) for every CR page
 * (PART 8/48 of the Phase 21 brief) - fetched once here via
 * `GET /api/cr-assignments/me`, which the backend resolves entirely from
 * the authenticated principal. No CR page ever lets the user choose a
 * division, and no CR page ever needs a term selector: `/me` already
 * resolves the CR's one current (active) assignment, term included - a
 * deliberate simplification (see docs/15-DESIGN-DECISIONS.md) rather than
 * building a multi-term selector for a capability the backend doesn't
 * expose to a CR at all (only LAB_ASSISTANT can list a user's assignments
 * across terms).
 */
export function CrAssignmentProvider({ children }: { children: ReactNode }) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["cr", "my-assignment"],
    queryFn: crAssignmentsApi.me,
    retry: false,
  });

  return <CrAssignmentContext.Provider value={{ assignment: data, isLoading, error }}>{children}</CrAssignmentContext.Provider>;
}

export function useCrAssignment(): CrAssignmentContextValue {
  const context = useContext(CrAssignmentContext);
  if (!context) {
    throw new Error("useCrAssignment must be used within a CrAssignmentProvider");
  }
  return context;
}
