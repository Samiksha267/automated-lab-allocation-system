import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

interface NavItem {
  to: string;
  label: string;
}
interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Domain-oriented grouping (PART 5 of the Phase 20 brief) - avoids one long flat menu. */
const NAV_GROUPS: NavGroup[] = [
  { label: "Overview", items: [{ to: "/lab-assistant", label: "Dashboard" }] },
  {
    label: "Scheduling",
    items: [
      { to: "/lab-assistant/timetable", label: "Timetable" },
      { to: "/lab-assistant/timetable-versions", label: "Timetable Versions" },
      { to: "/lab-assistant/imports", label: "Imports" },
    ],
  },
  {
    label: "Resources",
    items: [
      { to: "/lab-assistant/labs", label: "Labs" },
      { to: "/lab-assistant/faculty", label: "Faculty" },
      { to: "/lab-assistant/subjects", label: "Subjects" },
    ],
  },
  {
    label: "Academics",
    items: [
      { to: "/lab-assistant/academic-hierarchy", label: "Academic Setup" },
      { to: "/lab-assistant/cr-management", label: "CR Management" },
    ],
  },
  {
    label: "Administration",
    items: [
      { to: "/lab-assistant/audit-logs", label: "Audit Logs" },
      { to: "/lab-assistant/analytics", label: "Analytics" },
    ],
  },
];

/**
 * The Lab Assistant application shell (PART 4) - sidebar, top bar, content
 * area. `<Outlet />` renders whichever nested route matched, so every
 * Lab Assistant page shares this chrome without repeating it.
 */
export function LabAssistantLayout() {
  const { user, logout } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* Sidebar - collapses to an off-canvas panel on narrow screens (PART 60) */}
      <aside
        className={`fixed inset-y-0 left-0 z-40 w-64 transform overflow-y-auto border-r border-slate-200 bg-white p-4 transition-transform md:static md:translate-x-0 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="mb-6 px-2 text-lg font-bold text-slate-900">Lab Allocation</div>
        <nav className="space-y-6">
          {NAV_GROUPS.map((group) => (
            <div key={group.label}>
              <p className="px-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{group.label}</p>
              <ul className="mt-1 space-y-0.5">
                {group.items.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      end={item.to === "/lab-assistant"}
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
            </div>
          ))}
        </nav>
      </aside>

      {sidebarOpen && (
        <div className="fixed inset-0 z-30 bg-black/30 md:hidden" onClick={() => setSidebarOpen(false)} aria-hidden="true" />
      )}

      <div className="flex min-h-screen flex-1 flex-col md:pl-0">
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
              {user?.displayName ?? user?.email} <span className="text-slate-400">· Lab Assistant</span>
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
  );
}
