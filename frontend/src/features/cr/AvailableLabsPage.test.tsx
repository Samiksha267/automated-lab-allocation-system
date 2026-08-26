import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AvailableLabsPage } from "./AvailableLabsPage";
import { CrAssignmentProvider } from "./CrAssignmentContext";
import * as crAssignmentsApi from "../../api/crAssignments";
import * as academicApi from "../../api/academic";
import * as subjectsApi from "../../api/subjects";
import * as labsApi from "../../api/labs";
import * as extraLabsApi from "../../api/extraLabs";
import type { ExtraLabSearchResult } from "../../api/extraLabs";

vi.mock("../../api/crAssignments");
vi.mock("../../api/academic");
vi.mock("../../api/subjects");
vi.mock("../../api/labs");
vi.mock("../../api/extraLabs");

const assignment = { divisionId: 1, divisionCode: "A", program: "B.Tech", stream: "CS", year: 3, academicTermId: 10, academicTerm: "Semester 5" };

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CrAssignmentProvider>
          <AvailableLabsPage />
        </CrAssignmentProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function fillAndSearch() {
  await screen.findByRole("option", { name: /bda/i }, { timeout: 5000 });
  await userEvent.selectOptions(screen.getByLabelText(/^subject$/i), "1");
  await userEvent.type(screen.getByLabelText(/^date$/i), "2026-08-31");
  await userEvent.type(screen.getByLabelText(/^start$/i), "09:00");
  await userEvent.type(screen.getByLabelText(/^end$/i), "11:00");
  await userEvent.click(screen.getByRole("button", { name: /^search$/i }));
}

describe("AvailableLabsPage - PART 61/62/63/64/65 (mandatory scenarios)", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(crAssignmentsApi.crAssignmentsApi.me).mockResolvedValue(assignment);
    vi.mocked(academicApi.academicApi.getDivision).mockResolvedValue({ id: 1, academicYearId: 100, yearNumber: 3, streamCode: "CS", code: "A", strength: 60, active: true });
    vi.mocked(academicApi.academicApi.listBatches).mockResolvedValue([]);
    vi.mocked(subjectsApi.subjectsApi.list).mockResolvedValue([{ id: 1, academicYearId: 100, yearNumber: 3, code: "BDA", name: "Big Data Analytics", active: true }]);
    vi.mocked(labsApi.labsApi.list).mockResolvedValue([
      { id: 9, code: "C-202", name: "Lab C-202", capacity: 72, location: { wing: "C", floor: "3", roomNumber: "04" }, labType: { id: 1, code: "COMPUTER", name: "Computer Lab" }, active: true },
      { id: 1, code: "B-101", name: "Lab B-101", capacity: 30, location: { wing: "B", floor: "1", roomNumber: "01" }, labType: { id: 1, code: "COMPUTER", name: "Computer Lab" }, active: true },
    ]);
  });

  it("PART 62 (mandatory) - explains the BDA/Cloudera mismatch: B-101 rejected, C-202 valid and ranked", async () => {
    const result: ExtraLabSearchResult = {
      recommendationStatus: "RECOMMENDED",
      recommendedLab: { labId: 9, labCode: "C-202", rank: 1, score: 90, maxScore: 100, normalizedScore: 90, scoreFactors: [] },
      rankedValidLabs: [{ labId: 9, labCode: "C-202", rank: 1, score: 90, maxScore: 100, normalizedScore: 90, scoreFactors: [] }],
      rejectedLabs: [
        { labId: 1, labCode: "B-101", violations: [{ errorCode: "SOFTWARE_MISMATCH", label: "Missing software", message: "Lab B-101 does not have required software CLOUDERA for subject BDA." }] },
      ],
      summary: [],
      alternativeStatus: "NONE",
      alternatives: [],
    };
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(result);

    renderPage();
    await fillAndSearch();

    expect(await screen.findByText(/c-202/i)).toBeInTheDocument();
    expect(screen.getByText(/does not have required software cloudera for subject bda/i)).toBeInTheDocument();
    expect(screen.getByText(/b-101 unavailable/i)).toBeInTheDocument();
    // The raw enum code alone is never the only explanation shown to the CR.
    expect(screen.queryByText(/^SOFTWARE_MISMATCH$/)).not.toBeInTheDocument();
  });

  it("PART 63 - explains a faculty conflict in plain language", async () => {
    const result: ExtraLabSearchResult = {
      recommendationStatus: "NO_VALID_CANDIDATE",
      recommendedLab: null,
      rankedValidLabs: [],
      rejectedLabs: [
        { labId: 9, labCode: "C-202", violations: [{ errorCode: "FACULTY_CONFLICT", label: "Faculty conflict", message: "Faculty Dr. Sharma is already allocated during 09:00-11:00." }] },
      ],
      summary: [],
      alternativeStatus: "NONE",
      alternatives: [],
    };
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(result);

    renderPage();
    await fillAndSearch();

    expect(await screen.findByText(/already allocated during 09:00-11:00/i)).toBeInTheDocument();
  });

  it("PART 64 - explains faculty unavailability in plain language", async () => {
    const result: ExtraLabSearchResult = {
      recommendationStatus: "NO_VALID_CANDIDATE",
      recommendedLab: null,
      rankedValidLabs: [],
      rejectedLabs: [
        { labId: 9, labCode: "C-202", violations: [{ errorCode: "FACULTY_UNAVAILABLE", label: "Faculty unavailable", message: "Faculty Dr. Sharma is not available MONDAY 09:00-11:00." }] },
      ],
      summary: [],
      alternativeStatus: "NONE",
      alternatives: [],
    };
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(result);

    renderPage();
    await fillAndSearch();

    expect(await screen.findByText(/is not available monday 09:00-11:00/i)).toBeInTheDocument();
  });

  it("PART 65 - explains a same-lab conflict with an existing booking", async () => {
    const result: ExtraLabSearchResult = {
      recommendationStatus: "NO_VALID_CANDIDATE",
      recommendedLab: null,
      rankedValidLabs: [],
      rejectedLabs: [
        { labId: 9, labCode: "C-202", violations: [{ errorCode: "LAB_CONFLICT", label: "Lab conflict", message: "Lab C-202 already hosts an overlapping allocation (09:00-11:00)." }] },
      ],
      summary: [],
      alternativeStatus: "NONE",
      alternatives: [],
    };
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(result);

    renderPage();
    await fillAndSearch();

    expect(await screen.findByText(/already hosts an overlapping allocation/i)).toBeInTheDocument();
  });

  it("shows the no-valid-labs state and any backend-provided alternatives, never a fabricated one", async () => {
    const result: ExtraLabSearchResult = {
      recommendationStatus: "NO_VALID_CANDIDATE",
      recommendedLab: null,
      rankedValidLabs: [],
      rejectedLabs: [],
      summary: [],
      alternativeStatus: "SUGGESTED",
      alternatives: [{ type: "TIME_SHIFT", date: "2026-08-31", startTime: "11:00:00", endTime: "13:00:00", labId: 9, labCode: "C-202", normalizedScore: 88, explanation: "Same lab, two hours later." }],
    };
    vi.mocked(extraLabsApi.extraLabsApi.search).mockResolvedValue(result);

    renderPage();
    await fillAndSearch();

    expect(await screen.findByText(/no valid labs are available/i)).toBeInTheDocument();
    expect(screen.getByText(/same lab, two hours later/i)).toBeInTheDocument();
  });
});
