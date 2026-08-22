import { useQuery } from "@tanstack/react-query";
import { fetchBackendHealth } from "../api/health";

/** Development-facing hook used only to verify frontend-backend connectivity (Phase 2). */
export function useBackendHealth() {
  return useQuery({
    queryKey: ["backend-health"],
    queryFn: fetchBackendHealth,
    refetchInterval: 15_000,
  });
}
