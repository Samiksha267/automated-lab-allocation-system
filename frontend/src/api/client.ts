/**
 * Centralized API client. All frontend HTTP calls go through this module so
 * base URL resolution, JSON handling, the Authorization header, and baseline
 * error normalization live in one place rather than being repeated (and
 * drifting) across components/hooks.
 */
import { tokenStorage } from "./tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp?: string;
}

export class ApiError extends Error {
  code: string;
  details?: Record<string, unknown>;
  status: number;

  constructor(body: ApiErrorBody, status: number) {
    super(body.message);
    this.name = "ApiError";
    this.code = body.code;
    this.details = body.details;
    this.status = status;
  }
}

/**
 * Set by AuthProvider so a 401 from *any* authenticated call (not just /me)
 * can clear stale auth state consistently, without every call site needing
 * to know about auth. A no-op until AuthProvider registers itself, so this
 * module has no hard dependency on the auth feature existing.
 */
let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = tokenStorage.get();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let body: ApiErrorBody;
    try {
      body = await response.json();
    } catch {
      body = { code: "UNKNOWN_ERROR", message: `Request failed with status ${response.status}` };
    }

    // Only /api/auth/login is ever called without a token; every other 401
    // means a previously-valid session has gone stale (expired, or the
    // account was deactivated - see JwtAuthenticationFilter) and should be
    // cleared client-side. Avoids infinite redirect/request loops by only
    // reacting here, once, rather than in every caller.
    if (response.status === 401 && path !== "/auth/login") {
      unauthorizedHandler?.();
    }

    throw new ApiError(body, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = tokenStorage.get();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    // No Content-Type header here deliberately - the browser sets the
    // multipart boundary itself; setting it manually breaks the upload.
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });

  if (!response.ok) {
    let body: ApiErrorBody;
    try {
      body = await response.json();
    } catch {
      body = { code: "UNKNOWN_ERROR", message: `Request failed with status ${response.status}` };
    }
    if (response.status === 401) {
      unauthorizedHandler?.();
    }
    throw new ApiError(body, response.status);
  }

  return response.json() as Promise<T>;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  /** File uploads (Phase 19 PDF import) - multipart/form-data, never JSON-serialized. */
  postMultipart: <T>(path: string, formData: FormData) => requestMultipart<T>(path, formData),
};
