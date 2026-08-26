import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ScheduleExtraLabPage } from "./ScheduleExtraLabPage";
import { CrAssignmentProvider } from "./CrAssignmentContext";
import { ApiError } from "../../api/client";
import * as crAssignmentsApi from "../../api/crAssignments";
import * as academicApi from "../../api/academic";
import * as subjectsApi from "../../api/subjects";
import * as labsApi from "../../api/labs";
import * as extraLabsApi from "../../api/extraLabs";
import type { ExtraLabSearchResult, ExtraLabAllocation } from "../../api/extraLabs";

vi.mock("../../api/crAssignments");
vi.mock("../../api/academic");
vi.mock("../../api/subjects");
vi.mock("../../api/labs");
vi.mock("../../api/extraLabs");

const assignment = { divisionId: 1, divisionCode: "A", program: "B.Tech", stream: "CS", year: 3, academicTermId: 10, academicTerm: "Semester 5" };

const searchResult: ExtraLabSearchResult = {
  recommendationStatus: "RECOMMENDED",
  recommendedLab: { labId: 9, labCode: "C-202", rank: 1, score: 90, maxScore: 100, normalizedScore: 90, scoreFactors: [] },
  rankedValidLabs: [{ labId: 9, labCode: "C-202", rank: 1, score: 90, maxScore: 100, normalizedScore: 90, scoreFactors: [] }],
  rejectedLabs: [],
  summary: [],
  alternativeStatus: "NONE",
  alternatives: [],
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CrAssignmentProvider>
          <ScheduleExtraLabPage />
        </CrAssignmentProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function searchAndOpenConfirm() {
  await screen.findByRole("option", { name: /bda/i }, { timeout: 5000 });
  await userEvent.selectOptions(screen.getByLabelText(/^subject$/i), "1");
  await userEvent.type(screen.getByLabelText(/^date$/i), "2026-08-31");
  await userEvent.type(screen.getByLabelText(/^start$/i), "09:00");
  await userEvent.type(screen.getByLabelText(/^end$/i), "11:00");
  await userEvent.click(screen.getByRole("button", { name: /search available labs/i }));
  await userEvent.click(await screen.findByRole("button", { name: /book this lab/i }));
}

describe("ScheduleExtraLabPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(crAssignmentsApi.crAssignmentsApi.me).mockResolvedValue(assignment);
    vi.mocked(academicApi.academicApi.getDivision).mockResolvedValue({ id: 1, academicYearId: 100, yearNumber: 3, streamCode: "CS", code: "A", strength: 60, active: true });
    vi.mocked(academicApi.academicApi.listBatches).mockResolvedValue([]);
    vi.mocked(subjectsApi.subjectsApi.list).mockResolvedValue([{ id: 1, academicYearId: 100, yearNumber: 3, code: "BDA", name: "Big Data Analytics", active: true }]);
    vi.mocked(labsApi.labsApi.list).mockResolvedValue([
      { id: 9, code: "C-202", name: "Lab C-202", capacity: 72, location: { wing: "C", floor: "3", roomNumber: "04" }, labType: { id: 1, code: "COMPUTER", name: "Computer Lab" }, active: true },
    ]);
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(searchResult);
  });

  it("PART 69 - completes search -> select -> confirm -> book, and shows real booking details on success", async () => {
    const booked: ExtraLabAllocation = {
      allocationId: 100,
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
    };
    vi.mocked(extraLabsApi.extraLabsApi.book).mockResolvedValue(booked);

    renderPage();
    await searchAndOpenConfirm();

    expect(await screen.findByText(/schedule this extra practical/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /^confirm booking$/i }));

    expect(await screen.findByText(/scheduled successfully/i)).toBeInTheDocument();
    expect(screen.getByText("Dr. Sharma")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /view my extra labs/i })).toBeInTheDocument();
  });

  it("PART 70 (mandatory) - a 409 FCFS conflict shows a clear message, never a success state, and offers Search Again", async () => {
    vi.mocked(extraLabsApi.extraLabsApi.book).mockRejectedValue(
      new ApiError({ code: "ALLOCATION_CONFLICT", message: "The selected lab is no longer valid for this request." }, 409),
    );

    renderPage();
    await searchAndOpenConfirm();
    await userEvent.click(screen.getByRole("button", { name: /^confirm booking$/i }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/this slot is no longer available/i);
    expect(screen.queryByText(/scheduled successfully/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^search again$/i })).toBeInTheDocument();
  });
});
