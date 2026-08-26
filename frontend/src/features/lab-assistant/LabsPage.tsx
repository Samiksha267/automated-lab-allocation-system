import { useMemo, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { labsApi } from "../../api/labs";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import { describeError } from "../../lib/errorMessages";
import type { LabSummary } from "../../api/labs";

export function LabsPage() {
  const [wing, setWing] = useState("");
  const [labTypeId, setLabTypeId] = useState("");
  const [showCreate, setShowCreate] = useState(false);

  const labTypes = useQuery({ queryKey: ["lab-types"], queryFn: labsApi.listLabTypes });
  const labs = useQuery({ queryKey: ["labs", wing], queryFn: () => labsApi.list({ wing: wing || undefined }) });

  // Lab type filtering is done client-side - the backend supports it by code, but the
  // dataset here is small enough that filtering the already-fetched list is not misleading.
  const filtered = useMemo(() => {
    if (!labs.data) return [];
    if (!labTypeId) return labs.data;
    return labs.data.filter((l) => String(l.labType.id) === labTypeId);
  }, [labs.data, labTypeId]);

  const wings = useMemo(() => Array.from(new Set((labs.data ?? []).map((l) => l.location.wing))).sort(), [labs.data]);

  const columns: Column<LabSummary>[] = [
    { header: "Code", cell: (l) => <Link to={`/lab-assistant/labs/${l.id}`} className="font-medium text-indigo-600 hover:underline">{l.code}</Link> },
    { header: "Name", cell: (l) => l.name },
    { header: "Location", cell: (l) => `Wing ${l.location.wing}, Floor ${l.location.floor}, Room ${l.location.roomNumber}` },
    { header: "Capacity", cell: (l) => l.capacity },
    { header: "Type", cell: (l) => l.labType.name },
    { header: "Status", cell: (l) => <StatusBadge status={l.active ? "ACTIVE" : "INACTIVE"} /> },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Labs</h1>
        <button type="button" onClick={() => setShowCreate((s) => !s)} className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700">
          {showCreate ? "Cancel" : "Add Lab"}
        </button>
      </div>

      {showCreate && <CreateLabForm onDone={() => setShowCreate(false)} />}

      <div className="flex flex-wrap gap-3">
        <label className="flex items-center gap-2 text-sm">
          <span className="font-medium text-slate-700">Wing</span>
          <select className="rounded border border-slate-300 px-2 py-1.5" value={wing} onChange={(e) => setWing(e.target.value)}>
            <option value="">All</option>
            {wings.map((w) => (
              <option key={w} value={w}>
                {w}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm">
          <span className="font-medium text-slate-700">Lab Type</span>
          <select className="rounded border border-slate-300 px-2 py-1.5" value={labTypeId} onChange={(e) => setLabTypeId(e.target.value)}>
            <option value="">All</option>
            {labTypes.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <AsyncSection isLoading={labs.isLoading} error={labs.error} isEmpty={filtered.length === 0} emptyMessage="No labs match these filters.">
        <DataTable columns={columns} rows={filtered} rowKey={(l) => l.id} />
      </AsyncSection>
    </div>
  );
}

function CreateLabForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient();
  const labTypes = useQuery({ queryKey: ["lab-types"], queryFn: labsApi.listLabTypes });
  const [form, setForm] = useState({ code: "", name: "", capacity: "", labTypeId: "", wing: "", floor: "", roomNumber: "" });

  const mutation = useMutation({
    mutationFn: () =>
      labsApi.create({
        code: form.code,
        name: form.name,
        capacity: Number(form.capacity),
        labTypeId: Number(form.labTypeId),
        wing: form.wing,
        floor: form.floor,
        roomNumber: form.roomNumber,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["labs"] });
      onDone();
    },
  });

  return (
    <form
      className="grid grid-cols-2 gap-3 rounded border border-slate-200 bg-white p-4 md:grid-cols-4"
      onSubmit={(e) => {
        e.preventDefault();
        mutation.mutate();
      }}
    >
      <Field label="Code"><input required className="input" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
      <Field label="Name"><input required className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      <Field label="Capacity"><input required type="number" min={1} className="input" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} /></Field>
      <Field label="Lab Type">
        <select required className="input" value={form.labTypeId} onChange={(e) => setForm({ ...form, labTypeId: e.target.value })}>
          <option value="" disabled>
            Select
          </option>
          {labTypes.data?.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Wing"><input required className="input" value={form.wing} onChange={(e) => setForm({ ...form, wing: e.target.value })} /></Field>
      <Field label="Floor"><input required className="input" value={form.floor} onChange={(e) => setForm({ ...form, floor: e.target.value })} /></Field>
      <Field label="Room"><input required className="input" value={form.roomNumber} onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} /></Field>
      <div className="col-span-full flex items-center gap-3">
        <button type="submit" disabled={mutation.isPending} className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
          {mutation.isPending ? "Saving…" : "Create Lab"}
        </button>
        {mutation.isError && <span role="alert" className="text-sm text-red-700">{describeError(mutation.error)}</span>}
      </div>
    </form>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="text-sm">
      <span className="mb-1 block font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}
