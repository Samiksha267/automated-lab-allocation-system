import { apiClient } from "./client";

export type CrAssignmentStatus = "ACTIVE" | "ENDED";
export interface CrAssignment {
  id: number;
  userId: number;
  userEmail: string;
  divisionId: number;
  divisionCode: string;
  academicTermId: number;
  academicTermDisplayName: string;
  status: CrAssignmentStatus;
  validFrom: string;
  validTo: string | null;
}

export interface CurrentCrAssignment {
  divisionId: number;
  divisionCode: string;
  program: string;
  stream: string;
  year: number;
  academicTermId: number;
  academicTerm: string;
}

export const crAssignmentsApi = {
  listByDivision: (divisionId: number) => apiClient.get<CrAssignment[]>(`/cr-assignments?divisionId=${divisionId}`),
  listByUser: (userId: number) => apiClient.get<CrAssignment[]>(`/cr-assignments?userId=${userId}`),
  create: (body: { userId: number; divisionId: number; academicTermId: number }) =>
    apiClient.post<CrAssignment>("/cr-assignments", body),
  end: (id: number) => apiClient.delete<void>(`/cr-assignments/${id}`),
  /** Phase 21 - resolves the authenticated CR's own current assignment; never an arbitrary userId lookup (server-derived, CR-only). */
  me: () => apiClient.get<CurrentCrAssignment>("/cr-assignments/me"),
};
