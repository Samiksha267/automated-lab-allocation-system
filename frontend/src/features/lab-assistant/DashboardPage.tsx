import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { academicApi } from "../../api/academic";
import { labsApi } from "../../api/labs";
import { facultyApi } from "../../api/faculty";
import { scheduleVersionsApi, type ScheduleVersion } from "../../api/scheduleVersions";
import { timetableImportsApi } from "../../api/timetableImports";
import { auditLogsApi } from "../../api/auditLogs";
import { StatCard, Card } from "../../components/Card";
import { AsyncSection } from "../../components/AsyncSection";
import { StatusBadge } from "../../components/StatusBadge";
import { TermSelect } from "../../components/TermSelect";
import { formatInstant } from "../../lib/formatting";

/**
 * Only real backend data (PART 6/82) - no invented metrics. Each card is its
 * own query so one failing endpoint doesn't blank the whole dashboard.
 */
export function DashboardPage() {
  const [termId, setTermId] = useState<number | null>(null);
  const { data: terms } = useQuery({ queryKey: ["academic-terms"], queryFn: academicApi.listAcademicTerms });

  useEffect(() => {
    if (termId === null && terms && terms.length > 0) {
      setTermId((terms.find((t) => t.status === "ACTIVE") ?? terms[0]).id);
    }
  }, [terms, termId]);

  const versionHistory = useQuery({
    queryKey: ["schedule-version-history", termId],
    queryFn: () => scheduleVersionsApi.history(termId!),
    enabled: termId !== null,
  });
  const labs = useQuery({ queryKey: ["labs", "dashboard"], queryFn: () => labsApi.list() });
  const faculty = useQuery({ queryKey: ["faculty", "dashboard"], queryFn: facultyApi.list });
  const pendingImports = useQuery({
    queryKey: ["timetable-imports", "needs-review", termId],
    queryFn: () => timetableImportsApi.list({ academicTermId: termId!, status: "NEEDS_REVIEW", size: 100 }),
    enabled: termId !== null,
  });
  const recentActivity = useQuery({
    queryKey: ["audit-logs", "recent"],
    queryFn: () => auditLogsApi.search({ size: 6 }),
  });

  const published = versionHistory.data?.versions.find((v) => v.status === "PUBLISHED");
  const draft = versionHistory.data?.versions.find((v) => v.status === "DRAFT");
  const errorRowTotal = pendingImports.data?.content.reduce((sum, imp) => sum + imp.summary.errorRows, 0) ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Dashboard</h1>
        <TermSelect value={termId} onChange={setTermId} />
      </div>

      {termId === null ? (
        <p className="text-sm text-slate-500">Select an academic term to see term-scoped status.</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <AsyncSection isLoading={versionHistory.isLoading} error={versionHistory.error}>
            <StatCard
              label="Published Timetable"
              value={published ? `v${published.versionNumber}` : "None yet"}
              hint={published ? `${published.allocationCount} sessions` : "No version published for this term"}
            />
          </AsyncSection>
          <AsyncSection isLoading={versionHistory.isLoading} error={versionHistory.error}>
            <StatCard
              label="Draft Timetable"
              value={draft ? `v${draft.versionNumber}` : "None"}
              hint={draft ? `${draft.allocationCount} sessions staged` : "No draft in progress"}
            />
          </AsyncSection>
          <AsyncSection isLoading={labs.isLoading} error={labs.error}>
            <StatCard label="Labs" value={labs.data?.length ?? 0} hint={`${labs.data?.filter((l) => l.active).length ?? 0} active`} />
          </AsyncSection>
          <AsyncSection isLoading={faculty.isLoading} error={faculty.error}>
            <StatCard label="Faculty" value={faculty.data?.length ?? 0} />
          </AsyncSection>
          <AsyncSection isLoading={pendingImports.isLoading} error={pendingImports.error}>
            <StatCard label="Imports Needing Review" value={pendingImports.data?.content.length ?? 0} />
          </AsyncSection>
          <AsyncSection isLoading={pendingImports.isLoading} error={pendingImports.error}>
            <StatCard label="Unresolved Row Errors" value={errorRowTotal} />
          </AsyncSection>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card title="Quick Actions">
          <div className="flex flex-wrap gap-2">
            <Link to="/lab-assistant/timetable-versions" className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700">
              Create Timetable Draft
            </Link>
            <Link to="/lab-assistant/imports" className="rounded border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              Upload / Review Imports
            </Link>
            <Link to="/lab-assistant/conflicts" className="rounded border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              View Conflicts
            </Link>
            <Link to="/lab-assistant/labs" className="rounded border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              Manage Labs
            </Link>
            <Link to="/lab-assistant/cr-management" className="rounded border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              Manage CRs
            </Link>
          </div>
        </Card>

        <Card title="Recent Activity">
          <AsyncSection
            isLoading={recentActivity.isLoading}
            error={recentActivity.error}
            isEmpty={(recentActivity.data?.content.length ?? 0) === 0}
            emptyMessage="No recent activity."
          >
            <ul className="divide-y divide-slate-100 text-sm">
              {recentActivity.data?.content.map((entry) => (
                <li key={entry.id} className="flex items-center justify-between py-2">
                  <div>
                    <span className="font-medium text-slate-800">{entry.action.replaceAll("_", " ")}</span>
                    <span className="ml-2 text-slate-500">by {entry.actorDisplayName ?? entry.actorEmail}</span>
                  </div>
                  <span className="text-xs text-slate-400">{formatInstant(entry.createdAt)}</span>
                </li>
              ))}
            </ul>
          </AsyncSection>
          <Link to="/lab-assistant/audit-logs" className="mt-2 inline-block text-xs font-medium text-indigo-600 hover:underline">
            View all audit logs →
          </Link>
        </Card>
      </div>

      {termId !== null && (
        <AsyncSection isLoading={versionHistory.isLoading} error={versionHistory.error}>
          {published && <VersionRow version={published} />}
        </AsyncSection>
      )}
    </div>
  );
}

function VersionRow({ version }: { version: ScheduleVersion }) {
  return (
    <Card title="Current Published Version">
      <div className="flex items-center gap-3 text-sm">
        <StatusBadge status={version.status} />
        <span>Version {version.versionNumber}</span>
        <span className="text-slate-500">published {formatInstant(version.publishedAt)}</span>
      </div>
    </Card>
  );
}
