import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MyExtraLabsPage } from "./MyExtraLabsPage";
import { ApiError } from "../../api/client";
import * as extraLabsApi from "../../api/extraLabs";
import type { ExtraLabAllocation } from "../../api/extraLabs";

vi.mock("../../api/extraLabs");

function allocation(overrides: Partial<ExtraLabAllocation>): ExtraLabAllocation {
  return {
    allocationId: 1,
    allocationType: "EXTRA",
    status: "PUBLISHED",
    targetType: "DIVISION",
    subjectId: 1,
    subjectCode: "BDA",
    facultyId: 1,
    facultyName: "Dr. Sharma",
    labId: 9,
    labCode: "C-202",
    divisionId: 1,
    divisionCode: "A",
    batchId: null,
    batchCode: null,
    allocationDate: "2026-08-31",
    startTime: "09:00:00",
    endTime: "11:00:00",
    scheduleVersionId: 5,
    createdByUserId: 2,
    createdAt: "2026-08-25T10:00:00Z",
    cancelledByUserId: null,
    cancelledAt: null,
    cancellationReason: null,
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MyExtraLabsPage />
    </QueryClientProvider>,
  );
}

describe("MyExtraLabsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("PART 72 - renders active and cancelled bookings, cancel only offered on active ones", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockResolvedValue([
      allocation({ allocationId: 1, status: "PUBLISHED" }),
      allocation({ allocationId: 2, status: "CANCELLED", cancelledAt: "2026-08-25T11:00:00Z" }),
    ]);

    renderPage();
    await screen.findAllByText("BDA");

    const cancelButtons = screen.getAllByRole("button", { name: /^cancel$/i });
    expect(cancelButtons).toHaveLength(1);
  });

  it("PART 72 - shows the empty state when no extra practicals exist", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText(/no extra practicals have been scheduled yet/i)).toBeInTheDocument();
  });

  it("PART 72 - shows an error state, not an empty list, on API failure", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockRejectedValue(new Error("Server error"));

    renderPage();

    expect(await screen.findByText(/could not load this data/i)).toBeInTheDocument();
  });

  it("PART 73 - cancels a booking, refreshing status after backend confirmation", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockResolvedValue([allocation({ allocationId: 1, status: "PUBLISHED" })]);
    vi.mocked(extraLabsApi.extraLabsApi.cancel).mockResolvedValue(allocation({ allocationId: 1, status: "CANCELLED" }));

    renderPage();
    await screen.findByText("BDA");

    await userEvent.click(screen.getByRole("button", { name: /^cancel$/i }));
    expect(await screen.findByText(/cancel bda extra practical/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /^cancel booking$/i }));

    expect(extraLabsApi.extraLabsApi.cancel).toHaveBeenCalledWith(1, undefined);
  });

  it("PART 74 - a cancellation failure shows an error and leaves the booking visible, never optimistically removed", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockResolvedValue([allocation({ allocationId: 1, status: "PUBLISHED" })]);
    vi.mocked(extraLabsApi.extraLabsApi.cancel).mockRejectedValue(
      new ApiError({ code: "INVALID_ALLOCATION_TRANSITION", message: "This allocation is already cancelled." }, 409),
    );

    renderPage();
    await screen.findByText("BDA");
    await userEvent.click(screen.getByRole("button", { name: /^cancel$/i }));
    await userEvent.click(screen.getByRole("button", { name: /^cancel booking$/i }));

    expect(await screen.findByText(/can no longer be cancelled/i)).toBeInTheDocument();
    // The row is still present - nothing was optimistically removed.
    expect(screen.getByText("BDA")).toBeInTheDocument();
  });

  it("PART 71 (mandatory) - attempting to cancel another CR's allocation is rejected with a clear scope message, not a crash", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.mine).mockResolvedValue([allocation({ allocationId: 1, status: "PUBLISHED" })]);
    vi.mocked(extraLabsApi.extraLabsApi.cancel).mockRejectedValue(
      new ApiError({ code: "FORBIDDEN_DIVISION_ACCESS", message: "You are not assigned to division A." }, 403),
    );

    renderPage();
    await screen.findByText("BDA");
    await userEvent.click(screen.getByRole("button", { name: /^cancel$/i }));
    await userEvent.click(screen.getByRole("button", { name: /^cancel booking$/i }));

    expect(await screen.findByText(/you can schedule or manage extra labs only for your assigned class/i)).toBeInTheDocument();
  });
});
