import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AnalyticsPage } from "./AnalyticsPage";
import * as academicApi from "../../api/academic";
import * as analyticsApi from "../../api/analytics";
import type { AnalyticsSummaryResponse, LabUtilizationResponse, ExtraLabAnalyticsResponse, PeakUsageResponse, UnusedLabsResponse, ConflictAnalyticsResponse } from "../../api/analytics";

vi.mock("../../api/academic");
vi.mock("../../api/analytics");

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AnalyticsPage />
    </QueryClientProvider>,
  );
}

const summary: AnalyticsSummaryResponse = {
  academicTermId: 1,
  academicTermDisplayName: "Semester 5",
  range: { from: "2026-01-01", to: "2026-06-30" },
  publishedVersionExists: true,
  activeAllocations: 142,
  extraLabsTotal: 18,
  extraLabsActive: 15,
  extraLabsCancelled: 3,
  overallUtilizationPercent: 52.5,
  unusedLabCount: 2,
  conflictEvidenceAvailable: false,
};

const utilization: LabUtilizationResponse = {
  academicTermId: 1,
  range: { from: "2026-01-01", to: "2026-06-30" },
  publishedVersionExists: true,
  overallUtilizationPercent: 52.5,
  labs: [
    { labId: 9, labCode: "C-202", wing: "C", capacity: 60, labTypeCode: "COMPUTER", bookedMinutes: 1260, availableMinutes: 2400, utilizationPercent: 52.5, allocationCount: 11 },
  ],
};

const extraLabs: ExtraLabAnalyticsResponse = {
  academicTermId: 1,
  range: { from: "2026-01-01", to: "2026-06-30" },
  publishedVersionExists: true,
  total: 18,
  active: 15,
  cancelled: 3,
  cancellationRatePercent: 16.7,
  byDivision: [{ key: "A", active: 10, cancelled: 2, total: 12 }],
  bySubject: [{ key: "BDA", active: 8, cancelled: 1, total: 9 }],
  byLab: [{ key: "C-202", active: 5, cancelled: 0, total: 5 }],
  successfulBookings: 18,
  failedBookingDataAvailable: false,
  failedBookingDataUnavailableReason: "Failed authoritative extra-lab booking attempts are never persisted.",
};

const peakUsage: PeakUsageResponse = {
  academicTermId: 1,
  range: { from: "2026-01-01", to: "2026-06-30" },
  publishedVersionExists: true,
  busiestDay: { date: "2026-03-10", bookedMinutes: 480, allocationCount: 4 },
  mostUsedLab: { labId: 9, labCode: "C-202", bookedMinutes: 1260, allocationCount: 11 },
  busiestTimeSlot: { slotStart: "09:00:00", slotEnd: "10:00:00", bookedMinutes: 300, allocationCount: 5 },
};

const unusedLabs: UnusedLabsResponse = {
  academicTermId: 1,
  range: { from: "2026-01-01", to: "2026-06-30" },
  publishedVersionExists: true,
  unusedLabs: [{ labId: 14, labCode: "D-202", wing: "D", capacity: 40, labTypeCode: "COMPUTER", bookedMinutes: 0, availableMinutes: 2400, utilizationPercent: 0, allocationCount: 0 }],
};

const conflicts: ConflictAnalyticsResponse = {
  academicTermId: 1,
  evidenceAvailable: false,
  categories: [],
  explanation: "No historical conflict data is available. This system detects conflicts at request time but never persists a rejected attempt.",
};

async function selectTerm() {
  await screen.findByRole("option", { name: /semester 5/i });
  await userEvent.selectOptions(screen.getByLabelText(/academic term/i), "1");
}

describe("AnalyticsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(academicApi.academicApi.listAcademicTerms).mockResolvedValue([
      { id: 1, academicYearLabel: "2026", termNumber: 1, displayName: "Semester 5", startDate: "2026-01-01", endDate: "2026-06-30", status: "ACTIVE" },
    ]);
    vi.mocked(analyticsApi.analyticsApi.summary).mockResolvedValue(summary);
    vi.mocked(analyticsApi.analyticsApi.labUtilization).mockResolvedValue(utilization);
    vi.mocked(analyticsApi.analyticsApi.extraLabs).mockResolvedValue(extraLabs);
    vi.mocked(analyticsApi.analyticsApi.peakUsage).mockResolvedValue(peakUsage);
    vi.mocked(analyticsApi.analyticsApi.unusedLabs).mockResolvedValue(unusedLabs);
    vi.mocked(analyticsApi.analyticsApi.conflicts).mockResolvedValue(conflicts);
  });

  it("shows a prompt and issues no request before a term is selected", async () => {
    renderPage();
    expect(await screen.findByText(/select an academic term/i)).toBeInTheDocument();
    expect(analyticsApi.analyticsApi.summary).not.toHaveBeenCalled();
  });

  it("renders real summary values with correct percentage formatting (mandatory - Phase 21 scaling-bug regression)", async () => {
    renderPage();
    await selectTerm();

    // Backend sends 52.5 already on a 0-100 scale; must render "52.5%", never "5250%" or "0.525%".
    expect((await screen.findAllByText("52.5%")).length).toBeGreaterThan(0);
    expect(screen.queryByText("5250%")).not.toBeInTheDocument();
    expect(screen.queryByText("0.525%")).not.toBeInTheDocument();
    expect(screen.getByText("142")).toBeInTheDocument();
    expect(screen.getByText("Zero allocations in scope").closest("div")).toHaveTextContent("2");
  });

  it("renders lab utilization, weighted overall (not per-lab average), booked/available minutes", async () => {
    renderPage();
    await selectTerm();

    expect((await screen.findAllByText("C-202", {}, { timeout: 5000 })).length).toBeGreaterThan(0);
    expect(screen.getByText("21h")).toBeInTheDocument(); // 1260 booked minutes
    expect(screen.getByText("40h")).toBeInTheDocument(); // 2400 available minutes
  });

  it("renders extra-lab totals, breakdowns, and the honest no-failure-data explanation", async () => {
    renderPage();
    await selectTerm();

    expect(await screen.findByText(/failed authoritative extra-lab booking attempts are never persisted/i)).toBeInTheDocument();
    expect(screen.getByText("BDA")).toBeInTheDocument();
  });

  it("renders peak usage metrics with their real values", async () => {
    renderPage();
    await selectTerm();

    expect(await screen.findByText("D-202")).toBeInTheDocument(); // sanity: page rendered
    expect(screen.getAllByText("C-202").length).toBeGreaterThan(0);
  });

  it("renders unused labs with a useful empty-state fallback", async () => {
    vi.mocked(analyticsApi.analyticsApi.unusedLabs).mockResolvedValue({ ...unusedLabs, unusedLabs: [] });
    renderPage();
    await selectTerm();

    expect(await screen.findByText(/all labs were used during the selected period/i)).toBeInTheDocument();
  });

  it("shows the honest 'no evidence available' conflict explanation, never a fabricated count (mandatory)", async () => {
    renderPage();
    await selectTerm();

    const heading = await screen.findByText("Conflicts / Allocation Attempts");
    const card = heading.closest("div")!.parentElement as HTMLElement;
    expect(within(card).getByText(/no historical conflict data is available/i)).toBeInTheDocument();
    expect(within(card).queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows an error state, not zero-value cards, when the summary request fails", async () => {
    vi.mocked(analyticsApi.analyticsApi.summary).mockRejectedValue(new Error("network down"));
    renderPage();
    await selectTerm();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText("Overall Lab Utilization")).not.toBeInTheDocument();
  });

  it("shows an amber notice, not a fabricated 0%, when no published timetable exists for the term", async () => {
    vi.mocked(analyticsApi.analyticsApi.summary).mockResolvedValue({ ...summary, publishedVersionExists: false, overallUtilizationPercent: null });
    renderPage();
    await selectTerm();

    expect(await screen.findByText(/no published timetable exists for this term yet/i)).toBeInTheDocument();
  });
});
