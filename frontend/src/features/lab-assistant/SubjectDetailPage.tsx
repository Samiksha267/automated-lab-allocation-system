import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { subjectsApi } from "../../api/subjects";
import { labsApi } from "../../api/labs";
import { AsyncSection } from "../../components/AsyncSection";
import { describeError } from "../../lib/errorMessages";

/** Presents SubjectRequirements as scheduling requirements in plain language (PART 18), never raw join-table rows. */
export function SubjectDetailPage() {
  const { subjectId } = useParams();
  const id = Number(subjectId);
  const queryClient = useQueryClient();
  const requirements = useQuery({ queryKey: ["subjects", id, "requirements"], queryFn: () => subjectsApi.getRequirements(id) });
  const labTypes = useQuery({ queryKey: ["lab-types"], queryFn: labsApi.listLabTypes });
  const softwareCatalog = useQuery({ queryKey: ["software-catalog"], queryFn: labsApi.listSoftwareCatalog });
  const equipmentCatalog = useQuery({ queryKey: ["equipment-catalog"], queryFn: labsApi.listEquipmentCatalog });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["subjects", id, "requirements"] });

  const [softwareId, setSoftwareId] = useState("");
  const addSoftware = useMutation({
    mutationFn: () => subjectsApi.addSoftwareRequirement(id, Number(softwareId)),
    onSuccess: () => {
      setSoftwareId("");
      invalidate();
    },
  });
  const removeSoftware = useMutation({ mutationFn: (softwareId2: number) => subjectsApi.removeSoftwareRequirement(id, softwareId2), onSuccess: invalidate });

  const [equipmentId, setEquipmentId] = useState("");
  const [equipmentQty, setEquipmentQty] = useState("1");
  const addEquipment = useMutation({
    mutationFn: () => subjectsApi.addEquipmentRequirement(id, Number(equipmentId), Number(equipmentQty)),
    onSuccess: () => {
      setEquipmentId("");
      setEquipmentQty("1");
      invalidate();
    },
  });
  const removeEquipment = useMutation({ mutationFn: (equipmentId2: number) => subjectsApi.removeEquipmentRequirement(id, equipmentId2), onSuccess: invalidate });

  const [labTypeMode, setLabTypeMode] = useState<"required" | "preferred">("required");
  const [labTypeId, setLabTypeId] = useState("");
  const setLabType = useMutation({
    mutationFn: () =>
      subjectsApi.setLabTypeRequirement(
        id,
        labTypeMode === "required" ? Number(labTypeId) : null,
        labTypeMode === "preferred" ? Number(labTypeId) : null,
      ),
    onSuccess: invalidate,
  });

  return (
    <div className="space-y-4">
      <Link to="/lab-assistant/subjects" className="text-sm text-indigo-600 hover:underline">
        ← Back to Subjects
      </Link>

      <AsyncSection isLoading={requirements.isLoading} error={requirements.error}>
        {requirements.data && (
          <>
            <h1 className="text-xl font-semibold text-slate-900">
              {requirements.data.subject.code} — {requirements.data.subject.name}
            </h1>

            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="rounded border border-slate-200 bg-white p-4">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Lab Requirement</h2>
                {!requirements.data.requiredLabType && !requirements.data.preferredLabType && (
                  <p className="text-sm text-slate-500">No special lab requirements.</p>
                )}
                {requirements.data.requiredLabType && <p className="text-sm">Required: {requirements.data.requiredLabType.name}</p>}
                {requirements.data.preferredLabType && <p className="text-sm">Preferred: {requirements.data.preferredLabType.name}</p>}
                <div className="mt-3 flex flex-wrap gap-2">
                  <select className="input" value={labTypeMode} onChange={(e) => setLabTypeMode(e.target.value as "required" | "preferred")}>
                    <option value="required">Required</option>
                    <option value="preferred">Preferred</option>
                  </select>
                  <select className="input" value={labTypeId} onChange={(e) => setLabTypeId(e.target.value)}>
                    <option value="">Select lab type…</option>
                    {labTypes.data?.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={!labTypeId || setLabType.isPending}
                    onClick={() => setLabType.mutate()}
                    className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    Set
                  </button>
                </div>
                {setLabType.isError && <p className="mt-1 text-xs text-red-700">{describeError(setLabType.error)}</p>}
              </div>

              <div className="rounded border border-slate-200 bg-white p-4">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Required Software</h2>
                {requirements.data.software.length === 0 ? (
                  <p className="text-sm text-slate-500">No special software requirements.</p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {requirements.data.software.map((s) => (
                      <li key={s.id} className="flex items-center justify-between">
                        <span>{s.name}</span>
                        <button type="button" onClick={() => removeSoftware.mutate(s.id)} className="text-xs text-red-600 hover:underline">
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="mt-3 flex gap-2">
                  <select className="input" value={softwareId} onChange={(e) => setSoftwareId(e.target.value)}>
                    <option value="">Add software…</option>
                    {softwareCatalog.data?.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={!softwareId || addSoftware.isPending}
                    onClick={() => addSoftware.mutate()}
                    className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    Add
                  </button>
                </div>
              </div>

              <div className="rounded border border-slate-200 bg-white p-4">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Required Equipment</h2>
                {requirements.data.equipment.length === 0 ? (
                  <p className="text-sm text-slate-500">No special equipment requirements.</p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {requirements.data.equipment.map((eq) => (
                      <li key={eq.id} className="flex items-center justify-between">
                        <span>
                          {eq.name} × {eq.requiredQuantity}
                        </span>
                        <button type="button" onClick={() => removeEquipment.mutate(eq.id)} className="text-xs text-red-600 hover:underline">
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="mt-3 flex gap-2">
                  <select className="input" value={equipmentId} onChange={(e) => setEquipmentId(e.target.value)}>
                    <option value="">Add equipment…</option>
                    {equipmentCatalog.data?.map((eq) => (
                      <option key={eq.id} value={eq.id}>
                        {eq.name}
                      </option>
                    ))}
                  </select>
                  <input type="number" min={1} className="input w-16" value={equipmentQty} onChange={(e) => setEquipmentQty(e.target.value)} />
                  <button
                    type="button"
                    disabled={!equipmentId || addEquipment.isPending}
                    onClick={() => addEquipment.mutate()}
                    className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    Add
                  </button>
                </div>
              </div>
            </div>
          </>
        )}
      </AsyncSection>
    </div>
  );
}
