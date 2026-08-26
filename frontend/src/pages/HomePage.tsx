import { Link } from "react-router-dom";
import { useAuth } from "../features/auth/AuthContext";

/**
 * Authenticated shared landing page - all three roles land here after
 * login, each with a link into their own application (Lab Assistant: Phase
 * 20, CR: Phase 21, Student: Phase 22). This is also where
 * `RequireRouteRole` sends a wrong-role user redirected away from a
 * Lab Assistant route, so it must never itself require a specific role.
 */
export function HomePage() {
  const { user, logout } = useAuth();

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-8">
      <div className="max-w-md w-full bg-white rounded-lg shadow p-6 space-y-4 text-center">
        <h1 className="text-xl font-semibold text-slate-900">Welcome</h1>
        <p className="text-sm text-slate-600">
          Signed in as <span className="font-medium">{user?.displayName ?? user?.email}</span>
        </p>
        <p className="text-sm text-slate-500">
          Role: <span className="font-semibold">{user?.role}</span>
        </p>
        {user?.role === "LAB_ASSISTANT" && (
          <Link
            to="/lab-assistant"
            className="inline-block rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Go to Lab Assistant Dashboard
          </Link>
        )}
        {user?.role === "CR" && (
          <Link to="/cr" className="inline-block rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700">
            Go to My Class
          </Link>
        )}
        {user?.role === "STUDENT" && (
          <Link to="/student" className="inline-block rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700">
            Go to My Timetable
          </Link>
        )}
        <button
          type="button"
          onClick={logout}
          className="block w-full rounded bg-slate-200 text-slate-900 px-4 py-2 text-sm font-medium hover:bg-slate-300"
        >
          Log out
        </button>
      </div>
    </main>
  );
}
