import { apiClient } from "./client";

export interface UserSummary {
  id: number;
  email: string;
  displayName: string | null;
  role: "LAB_ASSISTANT" | "CR" | "STUDENT";
  active: boolean;
}

export const usersApi = {
  listByRole: (role: UserSummary["role"]) => apiClient.get<UserSummary[]>(`/users?role=${role}`),
};
