import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ImportsPage } from "./ImportsPage";
import * as academicApi from "../../api/academic";
import * as scheduleVersionsApi from "../../api/scheduleVersions";
import * as timetableImportsApi from "../../api/timetableImports";

vi.mock("../../api/academic");
vi.mock("../../api/scheduleVersions");
vi.mock("../../api/timetableImports");

const term = { id: 1, academicYearLabel: "2026-27", termNumber: 5, displayName: "Semester 5", startDate: "2026-01-01", endDate: "2026-06-30", status: "ACTIVE" as const };

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ImportsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ImportsPage upload form", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(academicApi.academicApi.listAcademicTerms).mockResolvedValue([term]);
    vi.mocked(scheduleVersionsApi.scheduleVersionsApi.history).mockResolvedValue({
      academicTermId: 1,
      academicTermDisplayName: "Semester 5",
      versions: [{ id: 5, academicTermId: 1, academicTermDisplayName: "Semester 5", versionNumber: 2, status: "DRAFT", reason: "revision", createdByUserId: 1, createdByEmail: "x@example.edu", createdAt: "2026-08-25T10:00:00Z", publishedByUserId: null, publishedByEmail: null, publishedAt: null, allocationCount: 0 }],
    });
    vi.mocked(timetableImportsApi.timetableImportsApi.list).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it("rejects a non-PDF file client-side without calling the upload API", async () => {
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: /upload pdf/i }));

    const fileInput = screen.getByLabelText(/pdf file/i) as HTMLInputElement;
    const badFile = new File(["not a pdf"], "timetable.txt", { type: "text/plain" });
    await userEvent.upload(fileInput, badFile);

    expect(await screen.findByText(/only \.pdf files are supported/i)).toBeInTheDocument();
    expect(timetableImportsApi.timetableImportsApi.upload).not.toHaveBeenCalled();
  });

  it("shows an empty-state message when there are no imports yet", async () => {
    renderPage();
    await screen.findByRole("option", { name: /semester 5/i });
    await userEvent.selectOptions(screen.getByLabelText(/academic term/i), "1");
    expect(await screen.findByText(/no timetable imports yet/i)).toBeInTheDocument();
  });
});
