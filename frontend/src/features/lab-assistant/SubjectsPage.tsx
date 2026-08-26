import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { subjectsApi } from "../../api/subjects";
import { AcademicYearSelector } from "../../components/AcademicYearSelector";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import type { Subject } from "../../api/subjects";

export function SubjectsPage() {
  const [academicYearId, setAcademicYearId] = useState<number | null>(null);
  const subjects = useQuery({
    queryKey: ["subjects", academicYearId],
    queryFn: () => subjectsApi.list(academicYearId!),
    enabled: academicYearId !== null,
  });

  const columns: Column<Subject>[] = [
    { header: "Code", cell: (s) => <Link to={`/lab-assistant/subjects/${s.id}`} className="font-medium text-indigo-600 hover:underline">{s.code}</Link> },
    { header: "Name", cell: (s) => s.name },
    { header: "Status", cell: (s) => <StatusBadge status={s.active ? "ACTIVE" : "INACTIVE"} /> },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Subjects</h1>
      <AcademicYearSelector onSelect={setAcademicYearId} />
      {academicYearId === null ? (
        <p className="text-sm text-slate-500">Select a program, stream, and year to see its subjects.</p>
      ) : (
        <AsyncSection
          isLoading={subjects.isLoading}
          error={subjects.error}
          isEmpty={(subjects.data?.length ?? 0) === 0}
          emptyMessage="No subjects defined for this academic year."
        >
          <DataTable columns={columns} rows={subjects.data ?? []} rowKey={(s) => s.id} />
        </AsyncSection>
      )}
    </div>
  );
}
