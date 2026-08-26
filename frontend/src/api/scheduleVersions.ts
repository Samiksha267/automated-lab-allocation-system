import { apiClient } from "./client";
import type { PageResponse } from "./types";

export type ScheduleVersionStatus = "DRAFT" | "PUBLISHED" | "SUPERSEDED";

export interface ScheduleVersion {
  id: number;
  academicTermId: number;
  academicTermDisplayName: string;
  versionNumber: number;
  status: ScheduleVersionStatus;
  reason: string | null;
  createdByUserId: number;
  createdByEmail: string;
  createdAt: string;
  publishedByUserId: number | null;
  publishedByEmail: string | null;
  publishedAt: string | null;
  allocationCount: number;
}

export interface ScheduleVersionHistory {
  academicTermId: number;
  academicTermDisplayName: string;
  versions: ScheduleVersion[];
}

export interface AllocationSummary {
  allocationId: number;
  allocationType: string;
  status: string;
  targetType: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  facultyId: number;
  facultyName: string;
  labId: number;
  labCode: string;
  labWing: string;
  labFloor: string;
  labRoomNumber: string;
  divisionId: number;
  divisionCode: string;
  batchId: number | null;
  batchCode: string | null;
  allocationDate: string;
  startTime: string;
  endTime: string;
  scheduleVersionId: number;
  scheduleVersionNumber: number;
}

export const scheduleVersionsApi = {
  history: (academicTermId: number) => apiClient.get<ScheduleVersionHistory>(`/schedule-versions?academicTermId=${academicTermId}`),
  get: (id: number) => apiClient.get<ScheduleVersion>(`/schedule-versions/${id}`),
  createDraft: (body: { academicTermId: number; reason?: string }) => apiClient.post<ScheduleVersion>("/schedule-versions", body),
  publish: (id: number) => apiClient.post<ScheduleVersion>(`/schedule-versions/${id}/publish`),
  allocations: (id: number, page: number, size = 20) =>
    apiClient.get<PageResponse<AllocationSummary>>(`/schedule-versions/${id}/allocations?page=${page}&size=${size}`),
};

export const timetableApi = {
  current: (params: { academicTermId: number; divisionId?: number; batchId?: number; page?: number; size?: number }) => {
    const search = new URLSearchParams();
    search.set("academicTermId", String(params.academicTermId));
    if (params.divisionId) search.set("divisionId", String(params.divisionId));
    if (params.batchId) search.set("batchId", String(params.batchId));
    search.set("page", String(params.page ?? 0));
    search.set("size", String(params.size ?? 20));
    return apiClient.get<PageResponse<AllocationSummary>>(`/timetable?${search.toString()}`);
  },
};
