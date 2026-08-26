import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { facultyApi, type DayOfWeek, type FacultyAvailability } from "../../api/faculty";
import { AsyncSection } from "../../components/AsyncSection";
import { TermSelect } from "../../components/TermSelect";
import { describeError } from "../../lib/errorMessages";
import { formatTimeRange, titleCase } from "../../lib/formatting";

const DAYS: DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

/** Presents FacultyAvailability rows as a weekly availability view (PART 15), not a raw record list. */
export function FacultyDetailPage() {
  const { facultyId } = useParams();
  const id = Number(facultyId);
  const queryClient = useQueryClient();
  const faculty = useQuery({ queryKey: ["faculty", id], queryFn: () => facultyApi.get(id) });
  const [termId, setTermId] = useState<number | null>(null);
  const availability = useQuery({
    queryKey: ["faculty", id, "availability", termId],
    queryFn: () => facultyApi.listAvailability(id, termId!),
    enabled: termId !== null,
  });

  const byDay = new Map<DayOfWeek, FacultyAvailability[]>();
  for (const day of DAYS) byDay.set(day, []);
  for (const window of availability.data ?? []) {
    byDay.get(window.dayOfWeek)?.push(window);
  }

  const [form, setForm] = useState({ dayOfWeek: "MONDAY" as DayOfWeek, startTime: "", endTime: "" });
  const addWindow = useMutation({
    mutationFn: () => facultyApi.createAvailability(id, { academicTermId: termId!, ...form }),
    onSuccess: () => {
      setForm({ dayOfWeek: "MONDAY", startTime: "", endTime: "" });
      queryClient.invalidateQueries({ queryKey: ["faculty", id, "availability", termId] });
    },
  });
  const removeWindow = useMutation({
    mutationFn: (availabilityId: number) => facultyApi.removeAvailability(id, availabilityId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["faculty", id, "availability", termId] }),
  });

  const timeInvalid = form.startTime !== "" && form.endTime !== "" && form.startTime >= form.endTime;

  return (
    <div className="space-y-4">
      <Link to="/lab-assistant/faculty" className="text-sm text-indigo-600 hover:underline">
        ← Back to Faculty
      </Link>
      <AsyncSection isLoading={faculty.isLoading} error={faculty.error}>
        {faculty.data && <h1 className="text-xl font-semibold text-slate-900">{faculty.data.name}</h1>}
      </AsyncSection>

      <TermSelect value={termId} onChange={setTermId} />

      {termId !== null && (
        <AsyncSection isLoading={availability.isLoading} error={availability.error}>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {DAYS.map((day) => {
              const windows = byDay.get(day) ?? [];
              return (
                <div key={day} className="rounded border border-slate-200 bg-white p-3">
                  <p className="text-sm font-semibold text-slate-900">{titleCase(day)}</p>
                  {windows.length === 0 ? (
                    <p className="mt-1 text-sm text-slate-400">Unavailable</p>
                  ) : (
                    <ul className="mt-1 space-y-1 text-sm">
                      {windows.map((w) => (
                        <li key={w.id} className="flex items-center justify-between">
                          <span>{formatTimeRange(w.startTime, w.endTime)}</span>
                          <button type="button" onClick={() => removeWindow.mutate(w.id)} className="text-xs text-red-600 hover:underline">
                            Remove
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>

          <form
            className="mt-4 flex flex-wrap items-end gap-3 rounded border border-slate-200 bg-white p-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (!timeInvalid) addWindow.mutate();
            }}
          >
            <label className="text-sm">
              <span className="mb-1 block font-medium text-slate-700">Day</span>
              <select className="input" value={form.dayOfWeek} onChange={(e) => setForm({ ...form, dayOfWeek: e.target.value as DayOfWeek })}>
                {DAYS.map((d) => (
                  <option key={d} value={d}>
                    {titleCase(d)}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-slate-700">Start</span>
              <input required type="time" className="input" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-slate-700">End</span>
              <input required type="time" className="input" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
            </label>
            <button type="submit" disabled={addWindow.isPending} className="rounded bg-indigo-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
              Add Window
            </button>
            {timeInvalid && <span className="text-sm text-red-700">End time must be after start time.</span>}
            {addWindow.isError && <span role="alert" className="text-sm text-red-700">{describeError(addWindow.error)}</span>}
          </form>
        </AsyncSection>
      )}
    </div>
  );
}
