import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { extraLabsApi, type ExtraLabAllocation, type ExtraLabSearchRequest, type ExtraLabSearchResult, type RankedCandidate } from "../../api/extraLabs";
import { AsyncSection } from "../../components/AsyncSection";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { describeError, describeErrorCode } from "../../lib/errorMessages";
import { formatDate, formatTimeRange } from "../../lib/formatting";
import { ExtraLabSearchForm, EMPTY_FORM, type ExtraLabFormState } from "./ExtraLabSearchForm";
import { ExtraLabResults } from "./ExtraLabResults";

/**
 * The full booking wizard (PART 26): search -> ranked recommendations ->
 * choose -> summary/confirm -> FCFS-safe book -> success. Search never
 * reserves anything (PART 29/78) - only the final `book()` call is
 * authoritative, and its own fresh backend revalidation is what can still
 * reject a candidate that looked available moments earlier (Phase 16).
 */
export function ScheduleExtraLabPage() {
  const [form, setForm] = useState<ExtraLabFormState>(EMPTY_FORM);
  const [lastRequest, setLastRequest] = useState<ExtraLabSearchRequest | null>(null);
  const [result, setResult] = useState<ExtraLabSearchResult | null>(null);
  const [confirming, setConfirming] = useState<RankedCandidate | null>(null);
  const [booked, setBooked] = useState<ExtraLabAllocation | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const search = useMutation({
    mutationFn: extraLabsApi.search,
    onSuccess: (data, variables) => {
      setResult(data);
      setLastRequest(variables);
    },
  });

  const book = useMutation({
    mutationFn: (candidate: RankedCandidate) => extraLabsApi.book({ ...lastRequest!, labId: candidate.labId }),
    onSuccess: (allocation) => {
      setConfirming(null);
      setBooked(allocation);
      queryClient.invalidateQueries({ queryKey: ["cr", "extra-labs"] });
    },
    onError: () => {
      // FCFS conflict or any other booking failure (PART 30/31) - never leave stale
      // "available" results on screen; the candidate that just failed may no
      // longer be valid, so the CR is guided back to search again.
      setConfirming(null);
    },
  });

  function searchAgain() {
    book.reset();
    if (lastRequest) search.mutate(lastRequest);
  }

  if (booked) {
    return (
      <div className="space-y-4">
        <h1 className="text-xl font-semibold text-slate-900">Schedule Extra Lab</h1>
        <div className="rounded border border-emerald-300 bg-emerald-50 p-4">
          <p className="font-semibold text-emerald-900">Extra practical scheduled successfully.</p>
          <dl className="mt-3 space-y-1 text-sm text-slate-700">
            <Row label="Subject" value={booked.subjectCode} />
            <Row label="Class" value={booked.batchCode ? `${booked.divisionCode} / ${booked.batchCode}` : booked.divisionCode} />
            <Row label="Faculty" value={booked.facultyName} />
            <Row label="Date" value={formatDate(booked.allocationDate)} />
            <Row label="Time" value={formatTimeRange(booked.startTime, booked.endTime)} />
            <Row label="Lab" value={booked.labCode} />
          </dl>
          <button
            type="button"
            onClick={() => navigate("/cr/extra-labs")}
            className="mt-4 rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            View My Extra Labs
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Schedule Extra Lab</h1>

      <ExtraLabSearchForm
        value={form}
        onChange={setForm}
        onSubmit={(req) => search.mutate(req)}
        submitLabel="Search Available Labs"
        isSubmitting={search.isPending}
      />

      {search.isError && (
        <p role="alert" className="text-sm text-red-700">
          {describeError(search.error)}
        </p>
      )}

      {book.isError && (
        <div role="alert" className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          <p className="font-medium">{describeErrorCode(book.error) === "ALLOCATION_CONFLICT" ? "This slot is no longer available." : "Booking failed."}</p>
          <p className="mt-1">{describeError(book.error)}</p>
          <button type="button" onClick={searchAgain} disabled={search.isPending} className="mt-2 rounded border border-amber-400 px-3 py-1.5 text-sm font-medium text-amber-900 hover:bg-amber-100">
            Search Again
          </button>
        </div>
      )}

      {result && (
        <AsyncSection isLoading={false} error={null}>
          <ExtraLabResults result={result} onBook={(candidate) => setConfirming(candidate)} isBooking={book.isPending} />
        </AsyncSection>
      )}

      <ConfirmDialog
        open={confirming !== null}
        title="Schedule this extra practical?"
        body={
          confirming &&
          lastRequest && (
            <div className="space-y-1">
              <p>The lab and faculty slot will be reserved if this request succeeds.</p>
              <dl className="mt-2 space-y-1">
                <Row label="Date" value={formatDate(lastRequest.allocationDate)} />
                <Row label="Time" value={formatTimeRange(lastRequest.startTime, lastRequest.endTime)} />
                <Row label="Lab" value={confirming.labCode} />
                <Row label="Faculty" value="Assigned automatically based on your subject/batch" />
              </dl>
            </div>
          )
        }
        confirmLabel="Confirm Booking"
        isPending={book.isPending}
        onCancel={() => setConfirming(null)}
        onConfirm={() => confirming && book.mutate(confirming)}
      />
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="font-medium text-slate-700">{label}:</dt>
      <dd className="text-slate-900">{value}</dd>
    </div>
  );
}
