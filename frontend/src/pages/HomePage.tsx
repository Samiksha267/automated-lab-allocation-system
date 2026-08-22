import { useAuth } from "../features/auth/AuthContext";

/**
 * Authenticated placeholder home page - exists only to verify routing and
 * auth state after login, for all three roles. Real role dashboards
 * (Lab Assistant / CR / Student) replace this in Phases 20-22.
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
        <button
          type="button"
          onClick={logout}
          className="rounded bg-slate-200 text-slate-900 px-4 py-2 text-sm font-medium hover:bg-slate-300"
        >
          Log out
        </button>
      </div>
    </main>
  );
}
