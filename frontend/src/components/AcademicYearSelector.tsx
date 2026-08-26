import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../api/academic";

/** Program -> Stream -> Academic Year cascading selector, reused by Subjects and Academic Hierarchy screens. */
export function AcademicYearSelector({ onSelect }: { onSelect: (academicYearId: number | null) => void }) {
  const [programId, setProgramId] = useState<number | "">("");
  const [streamId, setStreamId] = useState<number | "">("");
  const [yearId, setYearId] = useState<number | "">("");

  const programs = useQuery({ queryKey: ["programs"], queryFn: academicApi.listPrograms });
  const streams = useQuery({
    queryKey: ["streams", programId],
    queryFn: () => academicApi.listStreams(programId as number),
    enabled: programId !== "",
  });
  const years = useQuery({
    queryKey: ["academic-years", streamId],
    queryFn: () => academicApi.listAcademicYears(streamId as number),
    enabled: streamId !== "",
  });

  return (
    <div className="flex flex-wrap gap-3">
      <label className="text-sm">
        <span className="mb-1 block font-medium text-slate-700">Program</span>
        <select
          className="input"
          value={programId}
          onChange={(e) => {
            const value = e.target.value ? Number(e.target.value) : "";
            setProgramId(value);
            setStreamId("");
            setYearId("");
            onSelect(null);
          }}
        >
          <option value="">Select…</option>
          {programs.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <label className="text-sm">
        <span className="mb-1 block font-medium text-slate-700">Stream</span>
        <select
          className="input"
          value={streamId}
          disabled={programId === ""}
          onChange={(e) => {
            const value = e.target.value ? Number(e.target.value) : "";
            setStreamId(value);
            setYearId("");
            onSelect(null);
          }}
        >
          <option value="">Select…</option>
          {streams.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      <label className="text-sm">
        <span className="mb-1 block font-medium text-slate-700">Year</span>
        <select
          className="input"
          value={yearId}
          disabled={streamId === ""}
          onChange={(e) => {
            const value = e.target.value ? Number(e.target.value) : "";
            setYearId(value);
            onSelect(value === "" ? null : value);
          }}
        >
          <option value="">Select…</option>
          {years.data?.map((y) => (
            <option key={y.id} value={y.id}>
              Year {y.yearNumber}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
