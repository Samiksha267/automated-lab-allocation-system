import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { academicApi } from "../../api/academic";
import { crAssignmentsApi } from "../../api/crAssignments";
import { usersApi } from "../../api/users";
import { AcademicYearSelector } from "../../components/AcademicYearSelector";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { TermSelect } from "../../components/TermSelect";
import { describeError } from "../../lib/errorMessages";
import { formatInstant } from "../../lib/formatting";
import type { CrAssignment } from "../../api/crAssignments";

export function CrManagementPage() {
  const [academicYearId, setAcademicYearId] = useState<number | null>(null);
  const [divisionId, setDivisionId] = useState<number | "">("");
  const [termId, setTermId] = useState<number | null>(null);
  const [userId, setUserId] = useState("");
  const [endingId, setEndingId] = useState<number | null>(null);

  const queryClient = useQueryClient();
  const divisions = useQuery({
    queryKey: ["divisions", academicYearId],
    queryFn: () => academicApi.listDivisions(academicYearId!),
    enabled: academicYearId !== null,
  });
  const crUsers = useQuery({ queryKey: ["users", "CR"], queryFn: () => usersApi.listByRole("CR") });
  const assignments = useQuery({
    queryKey: ["cr-assignments", divisionId],
    queryFn: () => crAssignmentsApi.listByDivision(divisionId as number),
    enabled: divisionId !== "",
  });

  const assign = useMutation({
    mutationFn: () => crAssignmentsApi.create({ userId: Number(userId), divisionId: divisionId as number, academicTermId: termId! }),
    onSuccess: () => {
      setUserId("");
      queryClient.invalidateQueries({ queryKey: ["cr-assignments", divisionId] });
    },
  });
  const endAssignment = useMutation({
    mutationFn: (id: number) => crAssignmentsApi.end(id),
    onSuccess: () => {
      setEndingId(null);
      queryClient.invalidateQueries({ queryKey: ["cr-assignments", divisionId] });
    },
  });

  const columns: Column<CrAssignment>[] = [
    { header: "CR", cell: (a) => a.userEmail },
    { header: "Term", cell: (a) => a.academicTermDisplayName },
    { header: "Status", cell: (a) => <StatusBadge status={a.status === "ACTIVE" ? "ACTIVE" : "INACTIVE"} /> },
    { header: "Since", cell: (a) => formatInstant(a.validFrom) },
    {
      header: "",
      cell: (a) =>
        a.status === "ACTIVE" && (
          <button type="button" onClick={() => setEndingId(a.id)} className="text-xs font-medium text-red-600 hover:underline">
            Deactivate
          </button>
        ),
    },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">CR Management</h1>

      <AcademicYearSelector onSelect={setAcademicYearId} />

      {academicYearId !== null && (
        <label className="flex items-center gap-2 text-sm">
          <span className="font-medium text-slate-700">Division</span>
          <select className="input max-w-xs" value={divisionId} onChange={(e) => setDivisionId(e.target.value ? Number(e.target.value) : "")}>
            <option value="">Select…</option>
            {divisions.data?.map((d) => (
              <option key={d.id} value={d.id}>
                Division {d.code}
              </option>
            ))}
          </select>
        </label>
      )}

      {divisionId !== "" && (
        <>
          <AsyncSection
            isLoading={assignments.isLoading}
            error={assignments.error}
            isEmpty={(assignments.data?.length ?? 0) === 0}
            emptyMessage="No CR is assigned to this division for any term yet."
          >
            <DataTable columns={columns} rows={assignments.data ?? []} rowKey={(a) => a.id} />
          </AsyncSection>

          <form
            className="flex flex-wrap items-end gap-3 rounded border border-slate-200 bg-white p-4"
            onSubmit={(e) => {
              e.preventDefault();
              assign.mutate();
            }}
          >
            <TermSelect value={termId} onChange={setTermId} />
            <label className="text-sm">
              <span className="mb-1 block font-medium text-slate-700">CR User</span>
              <select required className="input" value={userId} onChange={(e) => setUserId(e.target.value)}>
                <option value="">Select…</option>
                {crUsers.data?.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.displayName ?? u.email}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              disabled={!userId || termId === null || assign.isPending}
              className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              Assign CR
            </button>
            {assign.isError && <span role="alert" className="text-sm text-red-700">{describeError(assign.error)}</span>}
          </form>
        </>
      )}

      <ConfirmDialog
        open={endingId !== null}
        title="Deactivate this CR assignment?"
        body="The CR will lose access to manage this division's extra-lab bookings. This does not affect their account, only this assignment."
        confirmLabel="Deactivate"
        danger
        isPending={endAssignment.isPending}
        onCancel={() => setEndingId(null)}
        onConfirm={() => endAssignment.mutate(endingId!)}
      />
    </div>
  );
}
