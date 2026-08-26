import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { labsApi } from "../../api/labs";
import { AsyncSection } from "../../components/AsyncSection";
import { StatusBadge } from "../../components/StatusBadge";
import { describeError } from "../../lib/errorMessages";
import { formatInstant } from "../../lib/formatting";

export function LabDetailPage() {
  const { labId } = useParams();
  const id = Number(labId);
  const queryClient = useQueryClient();
  const lab = useQuery({ queryKey: ["labs", id], queryFn: () => labsApi.get(id) });
  const softwareCatalog = useQuery({ queryKey: ["software-catalog"], queryFn: labsApi.listSoftwareCatalog });
  const equipmentCatalog = useQuery({ queryKey: ["equipment-catalog"], queryFn: labsApi.listEquipmentCatalog });
  const unavailability = useQuery({ queryKey: ["labs", id, "unavailability"], queryFn: () => labsApi.listUnavailability(id) });

  const [selectedSoftwareId, setSelectedSoftwareId] = useState("");
  const [selectedEquipmentId, setSelectedEquipmentId] = useState("");
  const [equipmentQty, setEquipmentQty] = useState("1");

  const invalidateLab = () => queryClient.invalidateQueries({ queryKey: ["labs", id] });

  const addSoftware = useMutation({
    mutationFn: () => labsApi.addSoftware(id, { softwareId: Number(selectedSoftwareId) }),
    onSuccess: () => {
      setSelectedSoftwareId("");
      invalidateLab();
    },
  });
  const removeSoftware = useMutation({ mutationFn: (softwareId: number) => labsApi.removeSoftware(id, softwareId), onSuccess: invalidateLab });
  const addEquipment = useMutation({
    mutationFn: () => labsApi.addEquipment(id, { equipmentId: Number(selectedEquipmentId), quantity: Number(equipmentQty) }),
    onSuccess: () => {
      setSelectedEquipmentId("");
      setEquipmentQty("1");
      invalidateLab();
    },
  });
  const removeEquipment = useMutation({ mutationFn: (equipmentId: number) => labsApi.removeEquipment(id, equipmentId), onSuccess: invalidateLab });

  return (
    <div className="space-y-4">
      <Link to="/lab-assistant/labs" className="text-sm text-indigo-600 hover:underline">
        ← Back to Labs
      </Link>

      <AsyncSection isLoading={lab.isLoading} error={lab.error}>
        {lab.data && (
          <>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-semibold text-slate-900">
                {lab.data.code} — {lab.data.name}
              </h1>
              <StatusBadge status={lab.data.active ? "ACTIVE" : "INACTIVE"} />
            </div>
            <p className="text-sm text-slate-600">
              Wing {lab.data.location.wing}, Floor {lab.data.location.floor}, Room {lab.data.location.roomNumber} · Capacity {lab.data.capacity} ·{" "}
              {lab.data.labType.name}
            </p>

            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="rounded border border-slate-200 bg-white p-4">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Installed Software</h2>
                {lab.data.software.length === 0 ? (
                  <p className="text-sm text-slate-500">No software installed.</p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {lab.data.software.map((s) => (
                      <li key={s.softwareId} className="flex items-center justify-between">
                        <span>
                          {s.name}
                          {s.installedVersion ? ` (v${s.installedVersion})` : ""}
                        </span>
                        <button type="button" onClick={() => removeSoftware.mutate(s.softwareId)} className="text-xs text-red-600 hover:underline">
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="mt-3 flex gap-2">
                  <select className="input" value={selectedSoftwareId} onChange={(e) => setSelectedSoftwareId(e.target.value)}>
                    <option value="">Add software…</option>
                    {softwareCatalog.data
                      ?.filter((s) => !lab.data!.software.some((installed) => installed.softwareId === s.id))
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                  </select>
                  <button
                    type="button"
                    disabled={!selectedSoftwareId || addSoftware.isPending}
                    onClick={() => addSoftware.mutate()}
                    className="whitespace-nowrap rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    Add
                  </button>
                </div>
                {addSoftware.isError && <p className="mt-1 text-xs text-red-700">{describeError(addSoftware.error)}</p>}
              </div>

              <div className="rounded border border-slate-200 bg-white p-4">
                <h2 className="mb-2 text-sm font-semibold text-slate-900">Equipment</h2>
                {lab.data.equipment.length === 0 ? (
                  <p className="text-sm text-slate-500">No equipment assigned.</p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {lab.data.equipment.map((eq) => (
                      <li key={eq.equipmentId} className="flex items-center justify-between">
                        <span>
                          {eq.name} — {eq.quantity}
                        </span>
                        <button type="button" onClick={() => removeEquipment.mutate(eq.equipmentId)} className="text-xs text-red-600 hover:underline">
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="mt-3 flex gap-2">
                  <select className="input" value={selectedEquipmentId} onChange={(e) => setSelectedEquipmentId(e.target.value)}>
                    <option value="">Add equipment…</option>
                    {equipmentCatalog.data?.map((eq) => (
                      <option key={eq.id} value={eq.id}>
                        {eq.name}
                      </option>
                    ))}
                  </select>
                  <input type="number" min={1} className="input w-20" value={equipmentQty} onChange={(e) => setEquipmentQty(e.target.value)} />
                  <button
                    type="button"
                    disabled={!selectedEquipmentId || addEquipment.isPending}
                    onClick={() => addEquipment.mutate()}
                    className="whitespace-nowrap rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    Add
                  </button>
                </div>
                {addEquipment.isError && <p className="mt-1 text-xs text-red-700">{describeError(addEquipment.error)}</p>}
              </div>
            </div>

            <div className="mt-4 rounded border border-slate-200 bg-white p-4">
              <h2 className="mb-2 text-sm font-semibold text-slate-900">Unavailability</h2>
              <AsyncSection
                isLoading={unavailability.isLoading}
                error={unavailability.error}
                isEmpty={(unavailability.data?.length ?? 0) === 0}
                emptyMessage="No unavailability windows recorded - this lab is available whenever no session is booked."
              >
                <ul className="space-y-1 text-sm">
                  {unavailability.data?.map((u) => (
                    <li key={u.id}>
                      {formatInstant(u.startDateTime)} – {formatInstant(u.endDateTime)}: {u.reason}
                    </li>
                  ))}
                </ul>
              </AsyncSection>
            </div>
          </>
        )}
      </AsyncSection>
    </div>
  );
}
