import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { CrAssignmentProvider } from "./CrAssignmentContext";

/**
 * The CR application shell (PART 4) - intentionally simpler than the Lab
 * Assistant shell (Phase 20): five flat nav items, no grouping needed, and
 * deliberately excludes every Lab Assistant administration screen (PART 43-46:
 * no lab/faculty/subject/CR/audit administration, no timetable-version
 * publication, no PDF import). `CrAssignmentProvider` wraps the whole shell
 * so every nested page shares the same one `/cr-assignments/me` fetch.
 */
const NAV_ITEMS = [
  { to: "/cr", label: "My Class" },
  { to: "/cr/timetable", label: "My Timetable" },
  { to: "/cr/available-labs", label: "Available Labs" },
  { to: "/cr/extra-labs/new", label: "Schedule Extra Lab" },
  { to: "/cr/extra-labs", label: "My Extra Labs" },
];

export function CrLayout() {
  const { user, logout } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <CrAssignmentProvider>
      <div className="flex min-h-screen bg-slate-50">
        <aside
          className={`fixed inset-y-0 left-0 z-40 w-60 transform overflow-y-auto border-r border-slate-200 bg-white p-4 transition-transform md:static md:translate-x-0 ${
            sidebarOpen ? "translate-x-0" : "-translate-x-full"
          }`}
        >
          <div className="mb-6 px-2 text-lg font-bold text-slate-900">Lab Allocation</div>
          <nav>
            <ul className="space-y-0.5">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.to === "/cr" || item.to === "/cr/extra-labs"}
                    onClick={() => setSidebarOpen(false)}
                    className={({ isActive }) =>
                      `block rounded px-2 py-1.5 text-sm font-medium ${
                        isActive ? "bg-indigo-50 text-indigo-700" : "text-slate-700 hover:bg-slate-100"
                      }`
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </aside>

        {sidebarOpen && (
          <div className="fixed inset-0 z-30 bg-black/30 md:hidden" onClick={() => setSidebarOpen(false)} aria-hidden="true" />
        )}

        <div className="flex min-h-screen flex-1 flex-col">
          <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
            <button
              type="button"
              className="rounded p-1.5 text-slate-600 hover:bg-slate-100 md:hidden"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open navigation"
            >
              ☰
            </button>
            <div />
            <div className="flex items-center gap-3">
              <span className="text-sm text-slate-600">
                {user?.displayName ?? user?.email} <span className="text-slate-400">· Class Representative</span>
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
      </div>
    </CrAssignmentProvider>
  );
}
