import { Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * The Student application shell (Phase 22, PART 3) - one nav item because
 * there is exactly one thing a Student can do here. Deliberately excludes
 * every scheduling/administration screen (Available Labs, Schedule/Cancel
 * Extra Lab, CR/Lab Management, PDF Import, Timetable Versions, Audit Logs) -
 * this frontend is read-only by construction, not just by omission of links;
 * no Student route ever mounts a mutation form.
 */
export function StudentLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
        <div className="text-lg font-bold text-slate-900">Lab Allocation</div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">
            {user?.displayName ?? user?.email} <span className="text-slate-400">· Student</span>
          </span>
          <button
            type="button"
            onClick={logout}
            className="rounded border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Log out
          </button>
        </div>
      </header>
      <main className="flex-1 p-4 md:p-6">
        <Outlet />
      </main>
    </div>
  );
}
