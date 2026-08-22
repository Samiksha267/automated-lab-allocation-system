import { apiClient } from "./client";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserSummary {
  id: number;
  email: string;
  displayName: string | null;
  role: "LAB_ASSISTANT" | "CR" | "STUDENT";
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiClient.post<LoginResponse>("/auth/login", request);
}

export function fetchCurrentUser(): Promise<UserSummary> {
  return apiClient.get<UserSummary>("/auth/me");
}
