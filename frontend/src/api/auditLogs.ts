import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface AuditLogEntry {
  id: number;
  actorUserId: number;
  actorDisplayName: string | null;
  actorEmail: string | null;
  actorRole: string;
  action: string;
  resourceType: string;
  resourceId: number;
  resourceDisplay: string | null;
  academicTermId: number | null;
  divisionId: number | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}

export interface AuditLogFilters {
  actorUserId?: number;
  action?: string;
  resourceType?: string;
  academicTermId?: number;
  divisionId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const auditLogsApi = {
  search: (filters: AuditLogFilters) => {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== "") search.set(key, String(value));
    }
    if (!search.has("page")) search.set("page", "0");
    if (!search.has("size")) search.set("size", "20");
    return apiClient.get<PageResponse<AuditLogEntry>>(`/audit-logs?${search.toString()}`);
  },
};
