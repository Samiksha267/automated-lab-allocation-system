import { apiClient } from "./client";

export interface Faculty {
  id: number;
  employeeCode: string;
  name: string;
  email: string | null;
  department: string | null;
  active: boolean;
}
export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
export interface FacultyAvailability {
  id: number;
  facultyId: number;
  facultyName: string;
  academicTermId: number;
  academicTermDisplayName: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  active: boolean;
}

export const facultyApi = {
  list: () => apiClient.get<Faculty[]>("/faculty"),
  get: (id: number) => apiClient.get<Faculty>(`/faculty/${id}`),
  create: (body: { employeeCode: string; name: string; email?: string; department?: string }) =>
    apiClient.post<Faculty>("/faculty", body),
  update: (id: number, body: { name: string; email?: string; department?: string; active: boolean }) =>
    apiClient.patch<Faculty>(`/faculty/${id}`, body),

  listAvailability: (facultyId: number, academicTermId: number) =>
    apiClient.get<FacultyAvailability[]>(`/faculty/${facultyId}/availability?academicTermId=${academicTermId}`),
  createAvailability: (facultyId: number, body: { academicTermId: number; dayOfWeek: DayOfWeek; startTime: string; endTime: string }) =>
    apiClient.post<FacultyAvailability>(`/faculty/${facultyId}/availability`, body),
  updateAvailability: (facultyId: number, availabilityId: number, body: { dayOfWeek: DayOfWeek; startTime: string; endTime: string }) =>
    apiClient.patch<FacultyAvailability>(`/faculty/${facultyId}/availability/${availabilityId}`, body),
  removeAvailability: (facultyId: number, availabilityId: number) =>
    apiClient.delete<void>(`/faculty/${facultyId}/availability/${availabilityId}`),
};
