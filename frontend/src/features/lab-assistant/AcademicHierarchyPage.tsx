import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { academicApi } from "../../api/academic";
import { AsyncSection } from "../../components/AsyncSection";

/** A visual Program -> Stream -> Year -> Division -> Batch tree (PART 20) - never five unrelated tables. */
export function AcademicHierarchyPage() {
  const programs = useQuery({ queryKey: ["programs"], queryFn: academicApi.listPrograms });

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Academic Setup</h1>
      <AsyncSection
        isLoading={programs.isLoading}
        error={programs.error}
        isEmpty={(programs.data?.length ?? 0) === 0}
        emptyMessage="No academic programs defined yet."
      >
        <div className="space-y-3">
          {programs.data?.map((program) => (
            <ProgramNode key={program.id} programId={program.id} programName={`${program.name} (${program.code})`} />
          ))}
        </div>
      </AsyncSection>
    </div>
  );
}

function ProgramNode({ programId, programName }: { programId: number; programName: string }) {
  const [open, setOpen] = useState(false);
  const streams = useQuery({ queryKey: ["streams", programId], queryFn: () => academicApi.listStreams(programId), enabled: open });
  return (
    <div className="rounded border border-slate-200 bg-white">
      <TreeToggle label={programName} open={open} onToggle={() => setOpen((o) => !o)} />
      {open && (
        <div className="border-t border-slate-100 pl-5">
          <AsyncSection isLoading={streams.isLoading} error={streams.error} isEmpty={(streams.data?.length ?? 0) === 0} emptyMessage="No streams.">
            {streams.data?.map((stream) => <StreamNode key={stream.id} streamId={stream.id} streamName={`${stream.name} (${stream.code})`} />)}
          </AsyncSection>
        </div>
      )}
    </div>
  );
}

function StreamNode({ streamId, streamName }: { streamId: number; streamName: string }) {
  const [open, setOpen] = useState(false);
  const years = useQuery({ queryKey: ["academic-years", streamId], queryFn: () => academicApi.listAcademicYears(streamId), enabled: open });
  return (
    <div className="border-b border-slate-50 py-1">
      <TreeToggle label={streamName} open={open} onToggle={() => setOpen((o) => !o)} />
      {open && (
        <div className="pl-5">
          <AsyncSection isLoading={years.isLoading} error={years.error} isEmpty={(years.data?.length ?? 0) === 0} emptyMessage="No years.">
            {years.data?.map((year) => <YearNode key={year.id} academicYearId={year.id} label={`Year ${year.yearNumber}`} />)}
          </AsyncSection>
        </div>
      )}
    </div>
  );
}

function YearNode({ academicYearId, label }: { academicYearId: number; label: string }) {
  const [open, setOpen] = useState(false);
  const divisions = useQuery({
    queryKey: ["divisions", academicYearId],
    queryFn: () => academicApi.listDivisions(academicYearId),
    enabled: open,
  });
  return (
    <div className="py-1">
      <TreeToggle label={label} open={open} onToggle={() => setOpen((o) => !o)} />
      {open && (
        <div className="pl-5">
          <AsyncSection isLoading={divisions.isLoading} error={divisions.error} isEmpty={(divisions.data?.length ?? 0) === 0} emptyMessage="No divisions.">
            {divisions.data?.map((division) => (
              <DivisionNode key={division.id} divisionId={division.id} label={`Division ${division.code} (strength ${division.strength})`} />
            ))}
          </AsyncSection>
        </div>
      )}
    </div>
  );
}

function DivisionNode({ divisionId, label }: { divisionId: number; label: string }) {
  const [open, setOpen] = useState(false);
  const batches = useQuery({ queryKey: ["batches", divisionId], queryFn: () => academicApi.listBatches(divisionId), enabled: open });
  return (
    <div className="py-1">
      <TreeToggle label={label} open={open} onToggle={() => setOpen((o) => !o)} />
      {open && (
        <div className="pl-5">
          <AsyncSection isLoading={batches.isLoading} error={batches.error} isEmpty={(batches.data?.length ?? 0) === 0} emptyMessage="No batches.">
            <ul className="list-disc pl-4 text-sm text-slate-700">
              {batches.data?.map((batch) => (
                <li key={batch.id}>
                  {batch.code} (strength {batch.strength})
                </li>
              ))}
            </ul>
          </AsyncSection>
        </div>
      )}
    </div>
  );
}

function TreeToggle({ label, open, onToggle }: { label: string; open: boolean; onToggle: () => void }) {
  return (
    <button type="button" onClick={onToggle} className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-slate-800 hover:bg-slate-50">
      <span className="text-slate-400">{open ? "▾" : "▸"}</span>
      {label}
    </button>
  );
}
