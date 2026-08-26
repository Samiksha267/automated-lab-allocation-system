import { apiClient } from "./client";

export interface LabLocation {
  wing: string;
  floor: string;
  roomNumber: string;
}
export interface LabTypeSummary {
  id: number;
  code: string;
  name: string;
}
export interface InstalledSoftwareItem {
  softwareId: number;
  code: string;
  name: string;
  installedVersion: string | null;
}
export interface InstalledEquipmentItem {
  equipmentId: number;
  code: string;
  name: string;
  quantity: number;
}
export interface LabSummary {
  id: number;
  code: string;
  name: string;
  capacity: number;
  location: LabLocation;
  labType: LabTypeSummary;
  active: boolean;
}
export interface Lab extends LabSummary {
  software: InstalledSoftwareItem[];
  equipment: InstalledEquipmentItem[];
}
export interface LabUnavailability {
  id: number;
  labId: number;
  labCode: string;
  startDateTime: string;
  endDateTime: string;
  reason: string;
  createdByEmail: string;
}
export interface LabType {
  id: number;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
}
export interface Software {
  id: number;
  code: string;
  name: string;
  active: boolean;
}
export interface Equipment {
  id: number;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
}

export interface LabFilters {
  wing?: string;
  labType?: string;
  minCapacity?: number;
  active?: boolean;
}

function buildQuery(params: LabFilters): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export const labsApi = {
  list: (filters: LabFilters = {}) => apiClient.get<LabSummary[]>(`/labs${buildQuery(filters)}`),
  get: (id: number) => apiClient.get<Lab>(`/labs/${id}`),
  create: (body: { code: string; name: string; capacity: number; labTypeId: number; wing: string; floor: string; roomNumber: string }) =>
    apiClient.post<Lab>("/labs", body),
  update: (
    id: number,
    body: { name: string; capacity: number; labTypeId: number; wing: string; floor: string; roomNumber: string; active: boolean },
  ) => apiClient.patch<Lab>(`/labs/${id}`, body),

  addSoftware: (labId: number, body: { softwareId: number; installedVersion?: string }) =>
    apiClient.post<InstalledSoftwareItem>(`/labs/${labId}/software`, body),
  removeSoftware: (labId: number, softwareId: number) => apiClient.delete<void>(`/labs/${labId}/software/${softwareId}`),

  addEquipment: (labId: number, body: { equipmentId: number; quantity: number }) =>
    apiClient.post<InstalledEquipmentItem>(`/labs/${labId}/equipment`, body),
  updateEquipmentQuantity: (labId: number, equipmentId: number, quantity: number) =>
    apiClient.patch<InstalledEquipmentItem>(`/labs/${labId}/equipment/${equipmentId}`, { quantity }),
  removeEquipment: (labId: number, equipmentId: number) => apiClient.delete<void>(`/labs/${labId}/equipment/${equipmentId}`),

  listUnavailability: (labId: number) => apiClient.get<LabUnavailability[]>(`/labs/${labId}/unavailability`),
  createUnavailability: (labId: number, body: { startDateTime: string; endDateTime: string; reason: string }) =>
    apiClient.post<LabUnavailability>(`/labs/${labId}/unavailability`, body),
  removeUnavailability: (labId: number, unavailabilityId: number) =>
    apiClient.delete<void>(`/labs/${labId}/unavailability/${unavailabilityId}`),

  listLabTypes: () => apiClient.get<LabType[]>("/lab-types"),
  listSoftwareCatalog: () => apiClient.get<Software[]>("/software"),
  listEquipmentCatalog: () => apiClient.get<Equipment[]>("/equipment"),
};
