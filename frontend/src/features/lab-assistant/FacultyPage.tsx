import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { facultyApi } from "../../api/faculty";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import type { Faculty } from "../../api/faculty";

export function FacultyPage() {
  const faculty = useQuery({ queryKey: ["faculty"], queryFn: facultyApi.list });

  const columns: Column<Faculty>[] = [
    { header: "Name", cell: (f) => <Link to={`/lab-assistant/faculty/${f.id}`} className="font-medium text-indigo-600 hover:underline">{f.name}</Link> },
    { header: "Employee Code", cell: (f) => f.employeeCode },
    { header: "Department", cell: (f) => f.department ?? "-" },
    { header: "Email", cell: (f) => f.email ?? "-" },
    { header: "Status", cell: (f) => <StatusBadge status={f.active ? "ACTIVE" : "INACTIVE"} /> },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Faculty</h1>
      <AsyncSection isLoading={faculty.isLoading} error={faculty.error} isEmpty={(faculty.data?.length ?? 0) === 0} emptyMessage="No faculty on record.">
        <DataTable columns={columns} rows={faculty.data ?? []} rowKey={(f) => f.id} />
      </AsyncSection>
    </div>
  );
}
