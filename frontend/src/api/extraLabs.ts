import { apiClient } from "./client";

export type TargetType = "DIVISION" | "BATCH";

export interface ExtraLabSearchRequest {
  subjectId: number;
  targetType: TargetType;
  batchId?: number;
  allocationDate: string;
  startTime: string;
  endTime: string;
}

export interface ExtraLabBookingRequest extends ExtraLabSearchRequest {
  labId: number;
}

export interface ScoreFactor {
  factor: string;
  applicability: string;
  pointsAwarded: number;
  maxPoints: number;
  explanation: string;
}

export interface RankedCandidate {
  labId: number;
  labCode: string;
  rank: number;
  score: number;
  maxScore: number;
  normalizedScore: number;
  scoreFactors: ScoreFactor[];
}

export interface Violation {
  errorCode: string;
  label: string;
  message: string;
}

export interface RejectedCandidate {
  labId: number;
  labCode: string;
  violations: Violation[];
}

export interface AlternativeSuggestion {
  type: string;
  date: string;
  startTime: string;
  endTime: string;
  labId: number;
  labCode: string;
  normalizedScore: number;
  explanation: string;
}

export interface ExtraLabSearchResult {
  recommendationStatus: string;
  recommendedLab: RankedCandidate | null;
  rankedValidLabs: RankedCandidate[];
  rejectedLabs: RejectedCandidate[];
  summary: string[];
  alternativeStatus: string;
  alternatives: AlternativeSuggestion[];
}

export interface ExtraLabAllocation {
  allocationId: number;
  allocationType: string;
  status: "APPROVED" | "PUBLISHED" | "CANCELLED";
  targetType: TargetType;
  subjectId: number;
  subjectCode: string;
  facultyId: number;
  facultyName: string;
  labId: number;
  labCode: string;
  divisionId: number;
  divisionCode: string;
  batchId: number | null;
  batchCode: string | null;
  allocationDate: string;
  startTime: string;
  endTime: string;
  scheduleVersionId: number;
  createdByUserId: number;
  createdAt: string;
  cancelledByUserId: number | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
}

export const extraLabsApi = {
  /** Advisory only - persists nothing, never reserves a lab (PART 29/78 of the Phase 21 brief). */
  search: (request: ExtraLabSearchRequest) => apiClient.post<ExtraLabSearchResult>("/allocations/extra/search", request),
  /** The only call that can actually reserve the lab - fresh server-side revalidation happens here, not at search time. */
  book: (request: ExtraLabBookingRequest) => apiClient.post<ExtraLabAllocation>("/allocations/extra", request),
  cancel: (allocationId: number, reason?: string) =>
    apiClient.post<ExtraLabAllocation>(`/allocations/extra/${allocationId}/cancel`, reason ? { reason } : undefined),
  mine: () => apiClient.get<ExtraLabAllocation[]>("/allocations/extra/mine"),
};
