import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MyClassPage } from "./MyClassPage";
import { CrAssignmentProvider } from "./CrAssignmentContext";
import * as crAssignmentsApi from "../../api/crAssignments";
import * as academicApi from "../../api/academic";

vi.mock("../../api/crAssignments");
vi.mock("../../api/academic");

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <CrAssignmentProvider>
        <MyClassPage />
      </CrAssignmentProvider>
    </QueryClientProvider>,
  );
}

describe("MyClassPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders the CR's real assigned division, program/stream/year, term, and batches - never a selector for another division", async () => {
    vi.mocked(crAssignmentsApi.crAssignmentsApi.me).mockResolvedValue({
      divisionId: 1,
      divisionCode: "A",
      program: "B.Tech",
      stream: "Computer Science",
      year: 3,
      academicTermId: 10,
      academicTerm: "Semester 5 (2026-27)",
    });
    vi.mocked(academicApi.academicApi.listBatches).mockResolvedValue([
      { id: 1, divisionId: 1, divisionCode: "A", code: "A1", strength: 30, active: true },
      { id: 2, divisionId: 1, divisionCode: "A", code: "A2", strength: 30, active: true },
    ]);

    renderPage();

    expect(await screen.findByText("B.Tech")).toBeInTheDocument();
    expect(screen.getByText("Computer Science")).toBeInTheDocument();
    expect(screen.getByText(/Year 3 — Division A/)).toBeInTheDocument();
    expect(screen.getByText(/Semester 5/)).toBeInTheDocument();
    expect(await screen.findByText("A1")).toBeInTheDocument();
    expect(screen.getByText("A2")).toBeInTheDocument();
    // No division-choosing control exists anywhere on this page.
    expect(screen.queryByRole("combobox", { name: /division/i })).not.toBeInTheDocument();
  });

  it("shows an error state, not a blank page, when the CR has no active assignment", async () => {
    vi.mocked(crAssignmentsApi.crAssignmentsApi.me).mockRejectedValue(
      Object.assign(new Error("No active CR assignment found for the current term."), { code: "CR_ASSIGNMENT_NOT_FOUND" }),
    );

    renderPage();

    expect(await screen.findByText(/could not load this data/i)).toBeInTheDocument();
  });
});
