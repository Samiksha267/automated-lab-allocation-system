import { apiClient } from "./client";

export interface Program {
  id: number;
  code: string;
  name: string;
  durationYears: number;
  active: boolean;
}
export interface Stream {
  id: number;
  programId: number;
  programCode: string;
  code: string;
  name: string;
  active: boolean;
}
export interface AcademicYear {
  id: number;
  streamId: number;
  streamCode: string;
  programId: number;
  programCode: string;
  yearNumber: number;
  active: boolean;
}
export interface Division {
  id: number;
  academicYearId: number;
  yearNumber: number;
  streamCode: string;
  code: string;
  strength: number;
  active: boolean;
}
export interface Batch {
  id: number;
  divisionId: number;
  divisionCode: string;
  code: string;
  strength: number;
  active: boolean;
}
export type TermStatus = "UPCOMING" | "ACTIVE" | "CLOSED";
export interface AcademicTerm {
  id: number;
  academicYearLabel: string;
  termNumber: number;
  displayName: string;
  startDate: string;
  endDate: string;
  status: TermStatus;
}

export const academicApi = {
  listPrograms: () => apiClient.get<Program[]>("/programs"),
  listStreams: (programId: number) => apiClient.get<Stream[]>(`/streams?programId=${programId}`),
  listAcademicYears: (streamId: number) => apiClient.get<AcademicYear[]>(`/academic-years?streamId=${streamId}`),
  listDivisions: (academicYearId: number) => apiClient.get<Division[]>(`/divisions?academicYearId=${academicYearId}`),
  /** Phase 21 - the CR's `/cr-assignments/me` response gives a divisionId but not the division's academicYearId; this resolves it (open to any authenticated user, no role restriction). */
  getDivision: (divisionId: number) => apiClient.get<Division>(`/divisions/${divisionId}`),
  listBatches: (divisionId: number) => apiClient.get<Batch[]>(`/batches?divisionId=${divisionId}`),
  listAcademicTerms: () => apiClient.get<AcademicTerm[]>("/academic-terms"),
};
