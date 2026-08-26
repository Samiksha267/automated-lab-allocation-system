import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MyTimetablePage } from "./MyTimetablePage";
import { CrAssignmentProvider } from "./CrAssignmentContext";
import * as crAssignmentsApi from "../../api/crAssignments";
import * as academicApi from "../../api/academic";
import * as scheduleVersionsApi from "../../api/scheduleVersions";

vi.mock("../../api/crAssignments");
vi.mock("../../api/academic");
vi.mock("../../api/scheduleVersions");

const assignment = {
  divisionId: 1,
  divisionCode: "A",
  program: "B.Tech",
  stream: "Computer Science",
  year: 3,
  academicTermId: 10,
  academicTerm: "Semester 5 (2026-27)",
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CrAssignmentProvider>
        <MyTimetablePage />
      </CrAssignmentProvider>
    </QueryClientProvider>,
  );
}

describe("MyTimetablePage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(crAssignmentsApi.crAssignmentsApi.me).mockResolvedValue(assignment);
    vi.mocked(academicApi.academicApi.listBatches).mockResolvedValue([]);
  });

  it("renders the current PUBLISHED timetable via the same status=PUBLISHED endpoint students/the current timetable API uses - never a version selector", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({
      content: [
        {
          allocationId: 1,
          allocationType: "REGULAR",
          status: "PUBLISHED",
          targetType: "DIVISION",
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
          batchId: null,
          batchCode: null,
          allocationDate: "2026-08-31",
          startTime: "09:00:00",
          endTime: "11:00:00",
          scheduleVersionId: 5,
          scheduleVersionNumber: 2,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    renderPage();

    expect(await screen.findByText("BDA")).toBeInTheDocument();
    expect(screen.getByText("C-202")).toBeInTheDocument();
    // The CR timetable page never exposes a version-number/status selector - that is Lab-Assistant-only territory.
    expect(screen.queryByText(/version/i)).not.toBeInTheDocument();
  });

  it("shows the no-published-timetable empty state, never a draft/superseded fallback", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });

    renderPage();

    expect(await screen.findByText(/no published timetable is available/i)).toBeInTheDocument();
  });

  it("shows an error state, not an empty table, when the timetable request fails", async () => {
    vi.mocked(scheduleVersionsApi.timetableApi.current).mockRejectedValue(new Error("Server error"));

    renderPage();

    expect(await screen.findByText(/could not load this data/i)).toBeInTheDocument();
  });
});
