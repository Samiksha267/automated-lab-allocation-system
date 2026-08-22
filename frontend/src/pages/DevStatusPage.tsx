import { useBackendHealth } from "../hooks/useBackendHealth";

/**
 * Minimal development status page: verifies the frontend can reach the
 * backend. Not a real dashboard - role-specific dashboards (Lab Assistant,
 * CR, Student) are built in later phases once the domain/auth exist.
 */
export function DevStatusPage() {
  const { data, isLoading, isError, error } = useBackendHealth();

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-8">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-6 space-y-4">
        <h1 className="text-xl font-semibold text-slate-900">
          Lab Allocation &mdash; Development Status
        </h1>
        <p className="text-sm text-slate-500">
          This page only verifies the frontend can reach the backend health
          endpoint. Role-based dashboards have not been built yet.
        </p>

        <div className="rounded border border-slate-200 p-4">
          <span className="text-sm font-medium text-slate-600">Backend: </span>
          {isLoading && <span className="text-slate-500">Checking...</span>}
          {isError && (
            <span className="text-red-600 font-semibold">
              Unavailable ({error instanceof Error ? error.message : "unknown error"})
            </span>
          )}
          {data && (
            <span
              className={
                data.status === "UP"
                  ? "text-green-600 font-semibold"
                  : "text-amber-600 font-semibold"
              }
            >
              {data.status === "UP" ? "Connected" : data.status}
            </span>
          )}
        </div>
      </div>
    </main>
  );
}
