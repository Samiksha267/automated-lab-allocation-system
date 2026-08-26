import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { TimetableVersionsPage } from "./TimetableVersionsPage";
import * as academicApi from "../../api/academic";
import * as scheduleVersionsApi from "../../api/scheduleVersions";
import type { ScheduleVersion } from "../../api/scheduleVersions";

vi.mock("../../api/academic");
vi.mock("../../api/scheduleVersions");

const term = { id: 1, academicYearLabel: "2026-27", termNumber: 5, displayName: "Semester 5", startDate: "2026-01-01", endDate: "2026-06-30", status: "ACTIVE" as const };

function version(overrides: Partial<ScheduleVersion>): ScheduleVersion {
  return {
    id: 1,
    academicTermId: 1,
    academicTermDisplayName: "Semester 5",
    versionNumber: 1,
    status: "DRAFT",
    reason: null,
    createdByUserId: 1,
    createdByEmail: "lab.assistant@example.edu",
    createdAt: "2026-08-25T10:00:00Z",
    publishedByUserId: null,
    publishedByEmail: null,
    publishedAt: null,
    allocationCount: 0,
    ...overrides,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <TimetableVersionsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("TimetableVersionsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(academicApi.academicApi.listAcademicTerms).mockResolvedValue([term]);
  });

  it("shows Publish only for a DRAFT version, never for PUBLISHED or SUPERSEDED", async () => {
    vi.mocked(scheduleVersionsApi.scheduleVersionsApi.history).mockResolvedValue({
      academicTermId: 1,
      academicTermDisplayName: "Semester 5",
      versions: [
        version({ id: 1, versionNumber: 1, status: "SUPERSEDED" }),
        version({ id: 2, versionNumber: 2, status: "PUBLISHED", publishedAt: "2026-08-20T10:00:00Z", publishedByEmail: "lab.assistant@example.edu" }),
        version({ id: 3, versionNumber: 3, status: "DRAFT" }),
      ],
    });

    renderPage();
    await screen.findByRole("option", { name: /semester 5/i }, { timeout: 5000 });
    await userEvent.selectOptions(screen.getByLabelText(/academic term/i), "1");
    await screen.findByText("v1");

    expect(screen.getAllByRole("button", { name: /^publish$/i })).toHaveLength(1);
  });

  it("shows the publish confirmation with the supersede/visibility consequences, then refreshes on success", async () => {
    vi.mocked(scheduleVersionsApi.scheduleVersionsApi.history)
      .mockResolvedValueOnce({
        academicTermId: 1,
        academicTermDisplayName: "Semester 5",
        versions: [version({ id: 1, versionNumber: 1, status: "PUBLISHED" }), version({ id: 2, versionNumber: 2, status: "DRAFT" })],
      })
      .mockResolvedValueOnce({
        academicTermId: 1,
        academicTermDisplayName: "Semester 5",
        versions: [version({ id: 1, versionNumber: 1, status: "SUPERSEDED" }), version({ id: 2, versionNumber: 2, status: "PUBLISHED" })],
      });
    vi.mocked(scheduleVersionsApi.scheduleVersionsApi.publish).mockResolvedValue(version({ id: 2, versionNumber: 2, status: "PUBLISHED" }));

    renderPage();
    await screen.findByRole("option", { name: /semester 5/i }, { timeout: 5000 });
    await userEvent.selectOptions(screen.getByLabelText(/academic term/i), "1");
    await screen.findByText("v2");

    await userEvent.click(screen.getByRole("button", { name: /^publish$/i }));

    expect(await screen.findByText(/version 2 will become/i)).toBeInTheDocument();
    expect(screen.getByText(/currently published version.*v1.*will become/i)).toBeInTheDocument();

    await userEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: /^publish$/i }));

    await waitFor(() => expect(scheduleVersionsApi.scheduleVersionsApi.publish).toHaveBeenCalledWith(2));
  });
});
