import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { extraLabsApi, type ExtraLabSearchResult } from "../../api/extraLabs";
import { AsyncSection } from "../../components/AsyncSection";
import { describeError } from "../../lib/errorMessages";
import { ExtraLabSearchForm, EMPTY_FORM, type ExtraLabFormState } from "./ExtraLabSearchForm";
import { ExtraLabResults } from "./ExtraLabResults";

/**
 * Read-only exploration (PART 15) - "what could host an extra practical?"
 * Search only; no booking action here. A CR who finds a lab they want
 * proceeds to Schedule Extra Lab to actually book it.
 */
export function AvailableLabsPage() {
  const [form, setForm] = useState<ExtraLabFormState>(EMPTY_FORM);
  const [result, setResult] = useState<ExtraLabSearchResult | null>(null);

  const search = useMutation({
    mutationFn: extraLabsApi.search,
    onSuccess: setResult,
  });

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Available Labs</h1>
      <p className="text-sm text-slate-500">
        Search for labs that could host an extra practical for your class. This does not reserve anything — to actually book a lab, use{" "}
        <Link to="/cr/extra-labs/new" className="text-indigo-600 hover:underline">
          Schedule Extra Lab
        </Link>
        .
      </p>

      <ExtraLabSearchForm value={form} onChange={setForm} onSubmit={(req) => search.mutate(req)} submitLabel="Search" isSubmitting={search.isPending} />

      {search.isError && (
        <p role="alert" className="text-sm text-red-700">
          {describeError(search.error)}
        </p>
      )}

      {result && (
        <AsyncSection isLoading={false} error={null}>
          <ExtraLabResults result={result} />
        </AsyncSection>
      )}
    </div>
  );
}
