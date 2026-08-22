import { apiClient } from "./client";

export interface HealthStatus {
  status: "UP" | "DOWN" | string;
}

/**
 * Calls the backend's actuator health endpoint directly (it is not under
 * /api, so this bypasses the shared apiClient's base path assumption) purely
 * to verify frontend-backend connectivity during development (Phase 2).
 * Domain queries are added once real endpoints exist - see
 * docs/10-API-DOCUMENTATION.md.
 */
export async function fetchBackendHealth(): Promise<HealthStatus> {
  const apiBase = import.meta.env.VITE_API_BASE_URL;
  const backendOrigin = apiBase.replace(/\/api\/?$/, "");
  const response = await fetch(`${backendOrigin}/actuator/health`);
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`);
  }
  return response.json();
}

// Re-exported so callers can use the shared apiClient error-handling path for
// any future /api-scoped health-adjacent endpoint without importing apiClient directly.
export { apiClient };
