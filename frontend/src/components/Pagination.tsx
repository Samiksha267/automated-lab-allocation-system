/** Respects backend pagination (PART 50) - never requests an oversized page to avoid this. */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return <p className="mt-2 text-xs text-slate-500">{totalElements} result{totalElements === 1 ? "" : "s"}</p>;
  }
  return (
    <div className="mt-3 flex items-center justify-between text-sm">
      <span className="text-slate-500">
        Page {page + 1} of {totalPages} — {totalElements} result{totalElements === 1 ? "" : "s"}
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          className="rounded border border-slate-300 px-3 py-1 disabled:opacity-40"
        >
          Previous
        </button>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="rounded border border-slate-300 px-3 py-1 disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  );
}
