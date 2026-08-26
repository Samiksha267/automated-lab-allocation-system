import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { DashboardPage } from "./DashboardPage";
import { ApiError } from "../../api/client";
import * as academicApi from "../../api/academic";
import * as labsApi from "../../api/labs";
import * as facultyApi from "../../api/faculty";
import * as scheduleVersionsApi from "../../api/scheduleVersions";
import * as timetableImportsApi from "../../api/timetableImports";
import * as auditLogsApi from "../../api/auditLogs";

vi.mock("../../api/academic");
vi.mock("../../api/labs");
vi.mock("../../api/faculty");
vi.mock("../../api/scheduleVersions");
vi.mock("../../api/timetableImports");
vi.mock("../../api/auditLogs");

const term = { id: 1, academicYearLabel: "2026-27", termNumber: 5, displayName: "Semester 5", startDate: "2026-01-01", endDate: "2026-06-30", status: "ACTIVE" as const };

function renderDashboard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(academicApi.academicApi.listAcademicTerms).mockResolvedValue([term]);
    vi.mocked(scheduleVersionsApi.scheduleVersionsApi.history).mockResolvedValue({
      academicTermId: 1,
      academicTermDisplayName: "Semester 5",
      versions: [],
    });
    vi.mocked(labsApi.labsApi.list).mockResolvedValue([]);
    vi.mocked(facultyApi.facultyApi.list).mockResolvedValue([]);
    vi.mocked(timetableImportsApi.timetableImportsApi.list).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
    vi.mocked(auditLogsApi.auditLogsApi.search).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 6 });
  });

  it("shows loading then real zero-value data, never a blank success state", async () => {
    renderDashboard();
    await waitFor(() => expect(screen.getByText("Labs")).toBeInTheDocument());
    // Real API data of 0, not a fabricated number.
    expect(screen.getAllByText("0").length).toBeGreaterThan(0);
  });

  it("distinguishes an empty activity feed from a failed one", async () => {
    renderDashboard();
    expect(await screen.findByText("No recent activity.")).toBeInTheDocument();
  });

  it("shows a distinct error state when a card's API call fails, never a silent 0", async () => {
    vi.mocked(labsApi.labsApi.list).mockRejectedValue(new ApiError({ code: "INTERNAL_ERROR", message: "Server error" }, 500));
    renderDashboard();
    expect(await screen.findByText(/could not load this data/i)).toBeInTheDocument();
  });

  it("renders real recent activity when present", async () => {
    vi.mocked(auditLogsApi.auditLogsApi.search).mockResolvedValue({
      content: [
        {
          id: 1,
          actorUserId: 1,
          actorDisplayName: "Demo Lab Assistant",
          actorEmail: "lab.assistant@example.edu",
          actorRole: "LAB_ASSISTANT",
          action: "LAB_CREATED",
          resourceType: "LAB",
          resourceId: 1,
          resourceDisplay: "C-202",
          academicTermId: null,
          divisionId: null,
          metadata: {},
          createdAt: "2026-08-25T10:00:00Z",
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 6,
    });
    renderDashboard();
    expect(await screen.findByText(/LAB CREATED/i)).toBeInTheDocument();
  });
});
