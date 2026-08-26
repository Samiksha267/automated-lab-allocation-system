import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { extraLabsApi, type ExtraLabAllocation } from "../../api/extraLabs";
import { AsyncSection } from "../../components/AsyncSection";
import { DataTable, type Column } from "../../components/DataTable";
import { StatusBadge } from "../../components/StatusBadge";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { describeError } from "../../lib/errorMessages";
import { formatDate, formatInstant, formatTimeRange } from "../../lib/formatting";

/** Only the CR's own extra practicals (server-scoped, PART 35) - cancel is offered only while the allocation is still active (PART 37). */
export function MyExtraLabsPage() {
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState<ExtraLabAllocation | null>(null);
  const [reason, setReason] = useState("");

  const extraLabs = useQuery({ queryKey: ["cr", "extra-labs"], queryFn: extraLabsApi.mine });

  const cancel = useMutation({
    mutationFn: (allocationId: number) => extraLabsApi.cancel(allocationId, reason || undefined),
    onSuccess: () => {
      setCancelling(null);
      setReason("");
      queryClient.invalidateQueries({ queryKey: ["cr", "extra-labs"] });
      queryClient.invalidateQueries({ queryKey: ["cr", "timetable"] });
    },
  });

  const columns: Column<ExtraLabAllocation>[] = [
    { header: "Subject", cell: (a) => a.subjectCode },
    { header: "Batch/Division", cell: (a) => (a.batchCode ? `${a.divisionCode} / ${a.batchCode}` : a.divisionCode) },
    { header: "Faculty", cell: (a) => a.facultyName },
    { header: "Lab", cell: (a) => a.labCode },
    { header: "Date", cell: (a) => formatDate(a.allocationDate) },
    { header: "Time", cell: (a) => formatTimeRange(a.startTime, a.endTime) },
    { header: "Status", cell: (a) => <StatusBadge status={a.status} /> },
    { header: "Booked", cell: (a) => formatInstant(a.createdAt) },
    {
      header: "",
      cell: (a) =>
        a.status !== "CANCELLED" && (
          <button type="button" onClick={() => setCancelling(a)} className="text-xs font-medium text-red-600 hover:underline">
            Cancel
          </button>
        ),
    },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">My Extra Labs</h1>
      <AsyncSection
        isLoading={extraLabs.isLoading}
        error={extraLabs.error}
        isEmpty={(extraLabs.data?.length ?? 0) === 0}
        emptyMessage="No extra practicals have been scheduled yet."
      >
        <DataTable columns={columns} rows={extraLabs.data ?? []} rowKey={(a) => a.allocationId} />
      </AsyncSection>

      <ConfirmDialog
        open={cancelling !== null}
        title={`Cancel ${cancelling?.subjectCode} extra practical?`}
        body={
          cancelling && (
            <div className="space-y-2">
              <p>
                {formatDate(cancelling.allocationDate)}, {formatTimeRange(cancelling.startTime, cancelling.endTime)} — Lab {cancelling.labCode}
              </p>
              <p>This will release the lab and faculty slot.</p>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-slate-700">Reason for cancellation (optional)</span>
                <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              {cancel.isError && <p role="alert" className="text-sm text-red-700">{describeError(cancel.error)}</p>}
            </div>
          )
        }
        confirmLabel="Cancel Booking"
        danger
        isPending={cancel.isPending}
        onCancel={() => {
          setCancelling(null);
          setReason("");
        }}
        onConfirm={() => cancelling && cancel.mutate(cancelling.allocationId)}
      />
    </div>
  );
}
