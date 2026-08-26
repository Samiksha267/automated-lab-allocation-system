import { apiClient } from "./client";
import type { LabTypeSummary } from "./labs";

export interface Subject {
  id: number;
  academicYearId: number;
  yearNumber: number;
  code: string;
  name: string;
  active: boolean;
}
export interface SoftwareSummary {
  id: number;
  code: string;
  name: string;
}
export interface EquipmentRequirementSummary {
  id: number;
  code: string;
  name: string;
  requiredQuantity: number;
}
export interface SubjectRequirements {
  subject: { id: number; code: string; name: string };
  software: SoftwareSummary[];
  equipment: EquipmentRequirementSummary[];
  requiredLabType: LabTypeSummary | null;
  preferredLabType: LabTypeSummary | null;
}

export const subjectsApi = {
  list: (academicYearId: number) => apiClient.get<Subject[]>(`/subjects?academicYearId=${academicYearId}`),
  get: (id: number) => apiClient.get<Subject>(`/subjects/${id}`),
  create: (body: { academicYearId: number; code: string; name: string }) => apiClient.post<Subject>("/subjects", body),
  update: (id: number, body: { name: string; active: boolean }) => apiClient.patch<Subject>(`/subjects/${id}`, body),

  getRequirements: (subjectId: number) => apiClient.get<SubjectRequirements>(`/subjects/${subjectId}/requirements`),
  addSoftwareRequirement: (subjectId: number, softwareId: number) =>
    apiClient.post<SoftwareSummary>(`/subjects/${subjectId}/software-requirements`, { softwareId }),
  removeSoftwareRequirement: (subjectId: number, softwareId: number) =>
    apiClient.delete<void>(`/subjects/${subjectId}/software-requirements/${softwareId}`),
  addEquipmentRequirement: (subjectId: number, equipmentId: number, requiredQuantity: number) =>
    apiClient.post<EquipmentRequirementSummary>(`/subjects/${subjectId}/equipment-requirements`, { equipmentId, requiredQuantity }),
  updateEquipmentRequirement: (subjectId: number, equipmentId: number, requiredQuantity: number) =>
    apiClient.patch<EquipmentRequirementSummary>(`/subjects/${subjectId}/equipment-requirements/${equipmentId}`, { requiredQuantity }),
  removeEquipmentRequirement: (subjectId: number, equipmentId: number) =>
    apiClient.delete<void>(`/subjects/${subjectId}/equipment-requirements/${equipmentId}`),
  setLabTypeRequirement: (subjectId: number, requiredLabTypeId: number | null, preferredLabTypeId: number | null) =>
    apiClient.put<LabTypeSummary>(`/subjects/${subjectId}/lab-type-requirement`, { requiredLabTypeId, preferredLabTypeId }),
};
