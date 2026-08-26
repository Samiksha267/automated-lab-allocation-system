import type { ReactNode } from "react";
import { describeError } from "../lib/errorMessages";

/**
 * One consistent loading/error/empty/success wrapper (PART 8/58 of the
 * Phase 20 brief) - "no pending imports" (a real, empty result) must never
 * look the same as "could not load imports" (a failed request), and a
 * failure must never silently render as `0`/blank.
 */
export function AsyncSection({
  isLoading,
  error,
  isEmpty,
  emptyMessage,
  children,
}: {
  isLoading: boolean;
  error: unknown;
  isEmpty?: boolean;
  emptyMessage?: ReactNode;
  children: ReactNode;
}) {
  if (isLoading) {
    return <div className="py-8 text-center text-sm text-slate-500">Loading…</div>;
  }
  if (error) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4 text-sm text-red-800">
        Could not load this data: {describeError(error)}
      </div>
    );
  }
  if (isEmpty) {
    return <div className="py-8 text-center text-sm text-slate-500">{emptyMessage ?? "Nothing to show."}</div>;
  }
  return <>{children}</>;
}
