import { apiClient } from "./client";
import type { PageResponse } from "./types";

export type TimetableImportStatus = "UPLOADED" | "NEEDS_REVIEW" | "VALIDATED" | "APPROVED" | "REJECTED" | "FAILED";
export type ImportRowStatus = "VALID" | "WARNING" | "ERROR";

export interface ImportSummary {
  totalRows: number;
  validRows: number;
  warningRows: number;
  errorRows: number;
  correctedRows: number;
}

export interface TimetableImportRecord {
  id: number;
  academicTermId: number;
  scheduleVersionId: number;
  originalFilename: string;
  fileSizeBytes: number;
  fileHash: string;
  status: TimetableImportStatus;
  failureReason: string | null;
  uploadedByUserId: number;
  uploadedAt: string;
  approvedByUserId: number | null;
  approvedAt: string | null;
  summary: ImportSummary;
}

export interface ValidationMessage {
  severity: "WARNING" | "ERROR";
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface ImportRow {
  id: number;
  rowNumber: number;
  rawDay: string | null;
  rawStartTime: string | null;
  rawEndTime: string | null;
  rawSubject: string | null;
  rawFaculty: string | null;
  rawLab: string | null;
  rawDivision: string | null;
  rawBatch: string | null;
  normalizedDay: string | null;
  normalizedStartTime: string | null;
  normalizedEndTime: string | null;
  subjectId: number | null;
  subjectCode: string | null;
  facultyId: number | null;
  facultyName: string | null;
  labId: number | null;
  labCode: string | null;
  divisionId: number | null;
  divisionCode: string | null;
  batchId: number | null;
  batchCode: string | null;
  allocationDate: string | null;
  validationStatus: ImportRowStatus;
  validationMessages: ValidationMessage[];
  corrected: boolean;
}

export interface ImportDetail {
  importResponse: TimetableImportRecord;
  rows: ImportRow[];
}

export interface ApproveResult {
  importResponse: TimetableImportRecord;
  allocationsCreated: number;
}

export interface RowCorrection {
  subjectId?: number;
  facultyId?: number;
  labId?: number;
  divisionId?: number;
  batchId?: number;
  day?: string;
  startTime?: string;
  endTime?: string;
}

export const timetableImportsApi = {
  upload: (academicTermId: number, scheduleVersionId: number, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient.postMultipart<TimetableImportRecord>(
      `/timetable-imports?academicTermId=${academicTermId}&scheduleVersionId=${scheduleVersionId}`,
      formData,
    );
  },
  list: (params: { academicTermId?: number; scheduleVersionId?: number; status?: TimetableImportStatus; page?: number; size?: number }) => {
    const search = new URLSearchParams();
    if (params.academicTermId) search.set("academicTermId", String(params.academicTermId));
    if (params.scheduleVersionId) search.set("scheduleVersionId", String(params.scheduleVersionId));
    if (params.status) search.set("status", params.status);
    search.set("page", String(params.page ?? 0));
    search.set("size", String(params.size ?? 20));
    return apiClient.get<PageResponse<TimetableImportRecord>>(`/timetable-imports?${search.toString()}`);
  },
  detail: (importId: number, page = 0, size = 50) =>
    apiClient.get<ImportDetail>(`/timetable-imports/${importId}?page=${page}&size=${size}`),
  correctRow: (importId: number, rowId: number, body: RowCorrection) =>
    apiClient.patch<ImportRow>(`/timetable-imports/${importId}/rows/${rowId}`, body),
  approve: (importId: number) => apiClient.post<ApproveResult>(`/timetable-imports/${importId}/approve`),
  reject: (importId: number) => apiClient.post<TimetableImportRecord>(`/timetable-imports/${importId}/reject`),
};
