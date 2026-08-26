import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { StudentTimetablePage } from "./StudentTimetablePage";
import * as academicApi from "../../api/academic";
import * as scheduleVersionsApi from "../../api/scheduleVersions";
import type { AllocationSummary } from "../../api/scheduleVersions";

vi.mock("../../api/academic");
vi.mock("../../api/scheduleVersions");

function renderPage(initialEntries = ["/student/timetable"]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <StudentTimetablePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function allocation(overrides: Partial<AllocationSummary>): AllocationSummary {
  return {
    allocationId: 1,
    allocationType: "REGULAR",
    status: "PUBLISHED",
    targetType: "BATCH",
    subjectId: 1,
    subjectCode: "BDA",
    subjectName: "Big Data Analytics",
    facultyId: 1,
    facultyName: "Dr. Sharma",
    labId: 9,
    labCode: "C-202",
    labWing: "C",
    labFloor: "2",
    labRoomNumber: "02",
    divisionId: 1,
    divisionCode: "A",
    batchId: 1,
    batchCode: "A1",
    allocationDate: "2026-08-31", // Monday
    startTime: "09:00:00",
    endTime: "11:00:00",
    scheduleVersionId: 1,
    scheduleVersionNumber: 1,
    ...overrides,
  };
}

describe("StudentTimetablePage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(academicApi.academicApi.listAcademicTerms).mockResolvedValue([
      { id: 10, academicYearLabel: "2026", termNumber: 1, displayName: "Semester 5", startDate: "2026-01-01", endDate: "2026-06-30", status: "ACTIVE" },
    ]);
    vi.mocked(academicApi.academicApi.listPrograms).mockResolvedValue([
      { id: 1, code: "BTECH", name: "B.Tech", durationYears: 4, active: true },
      { id: 2, code: "MTECH", name: "M.Tech", durationYears: 2, active: true },
    ]);
    vi.mocked(academicApi.academicApi.listStreams).mockResolvedValue([{ id: 2, programId: 1, programCode: "BTECH", code: "CS", name: "Computer Science", active: true }]);
    vi.mocked(academicApi.academicApi.listAcademicYears).mockResolvedValue([
      { id: 3, streamId: 2, streamCode: "CS", programId: 1, programCode: "BTECH", yearNumber: 3, active: true },
    ]);
    vi.mocked(academicApi.academicApi.listDivisions).mockResolvedValue([{ id: 4, academicYearId: 3, yearNumber: 3, streamCode: "CS", code: "A", strength: 60, active: true }]);
    vi.mocked(academicApi.academicApi.listBatches).mockResolvedValue([{ id: 5, divisionId: 4, divisionCode: "A", code: "A1", strength: 30, active: true }]);
  });

  it("shows a prompt instead of issuing a request before program/stream/year/division are chosen", async () => {
    renderPage();
    expect(await screen.findByText(/choose your program, stream, year, and division/i)).toBeInTheDocument();
    expect(scheduleVersionsApi.timetableApi.current).not.toHaveBeenCalled();
  });

  it("resets dependent filters when a higher-level filter changes", async () => {
    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4&batchId=5"]);
    await within(screen.getByLabelText(/^stream$/i)).findByRole("option", { name: "CS" });

    await userEvent.selectOptions(screen.getByLabelText(/^program$/i), "2");

    expect((screen.getByLabelText(/^stream$/i) as HTMLSelectElement).value).toBe("");
    expect((screen.getByLabelText(/^year$/i) as HTMLSelectElement).value).toBe("");
    expect((screen.getByLabelText(/^division$/i) as HTMLSelectElement).value).toBe("");
    expect((screen.getByLabelText(/^batch$/i) as HTMLSelectElement).value).toBe("");
  });

  it("renders published allocations with subject name, faculty, and clear lab location", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({
      content: [allocation({})],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4"]);

    expect(await screen.findByText(/BDA — Big Data Analytics/)).toBeInTheDocument();
    expect(screen.getByText(/Dr\. Sharma/)).toBeInTheDocument();
    expect(screen.getByText(/C-202/)).toBeInTheDocument();
    expect(screen.getByText(/Wing C/)).toBeInTheDocument();
    expect(screen.getByText(/Room 02/)).toBeInTheDocument();
  });

  it("shows both division-wide and batch-specific allocations when a batch is selected (PART 9/27, mandatory)", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({
      content: [
        allocation({ allocationId: 1, batchId: 5, batchCode: "A1", subjectCode: "BDA" }),
        allocation({ allocationId: 2, batchId: null, batchCode: null, targetType: "DIVISION", subjectCode: "DBMS", subjectName: "Databases" }),
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4&batchId=5"]);

    expect(await screen.findByText(/BDA — Big Data Analytics/)).toBeInTheDocument();
    expect(screen.getByText(/DBMS — Databases/)).toBeInTheDocument();
  });

  it("shows a useful empty state, never a blank UI, when no published timetable exists (PART 14/29, mandatory)", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });

    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4"]);

    expect(await screen.findByText(/no published timetable is currently available/i)).toBeInTheDocument();
  });

  it("shows an error state, not an empty timetable, when the request fails (PART 16/30, mandatory)", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockRejectedValue(new Error("network down"));

    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4"]);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(/no published timetable is currently available/i)).not.toBeInTheDocument();
  });

  it("filters displayed allocations by day", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({
      content: [
        allocation({ allocationId: 1, allocationDate: "2026-08-31", subjectCode: "MON-SUB" }), // Monday
        allocation({ allocationId: 2, allocationDate: "2026-09-01", subjectCode: "TUE-SUB" }), // Tuesday
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    renderPage(["/student/timetable?programId=1&streamId=2&yearId=3&divisionId=4"]);
    await screen.findByText(/MON-SUB/);
    expect(screen.getByText(/TUE-SUB/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/^day$/i), "Monday");

    await waitFor(() => expect(screen.queryByText(/TUE-SUB/)).not.toBeInTheDocument());
    expect(screen.getByText(/MON-SUB/)).toBeInTheDocument();
  });
});
