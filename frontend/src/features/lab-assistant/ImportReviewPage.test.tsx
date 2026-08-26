import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ImportReviewPage } from "./ImportReviewPage";
import * as timetableImportsApi from "../../api/timetableImports";
import * as labsApi from "../../api/labs";
import type { ImportDetail, ImportRow } from "../../api/timetableImports";

vi.mock("../../api/timetableImports");
vi.mock("../../api/labs");

function row(overrides: Partial<ImportRow>): ImportRow {
  return {
    id: 1,
    rowNumber: 1,
    rawDay: "MONDAY",
    rawStartTime: "09:00",
    rawEndTime: "11:00",
    rawSubject: "BDA",
    rawFaculty: "Faculty BDA",
    rawLab: "B-101",
    rawDivision: "A",
    rawBatch: "A1",
    normalizedDay: "MONDAY",
    normalizedStartTime: "09:00:00",
    normalizedEndTime: "11:00:00",
    subjectId: 1,
    subjectCode: "BDA",
    facultyId: 1,
    facultyName: "Faculty BDA",
    labId: 1,
    labCode: "B-101",
    divisionId: 1,
    divisionCode: "A",
    batchId: 1,
    batchCode: "A1",
    allocationDate: "2026-07-20",
    validationStatus: "VALID",
    validationMessages: [],
    corrected: false,
    ...overrides,
  };
}

function detailWith(rows: ImportRow[], overrides: Partial<ImportDetail["importResponse"]> = {}): ImportDetail {
  const errorRows = rows.filter((r) => r.validationStatus === "ERROR").length;
  const warningRows = rows.filter((r) => r.validationStatus === "WARNING").length;
  const validRows = rows.filter((r) => r.validationStatus === "VALID").length;
  return {
    importResponse: {
      id: 1,
      academicTermId: 1,
      scheduleVersionId: 5,
      originalFilename: "timetable.pdf",
      fileSizeBytes: 1000,
      fileHash: "abc",
      status: errorRows > 0 ? "NEEDS_REVIEW" : "VALIDATED",
      failureReason: null,
      uploadedByUserId: 1,
      uploadedAt: "2026-08-25T10:00:00Z",
      approvedByUserId: null,
      approvedAt: null,
      summary: { totalRows: rows.length, validRows, warningRows, errorRows, correctedRows: rows.filter((r) => r.corrected).length },
      ...overrides,
    },
    rows,
  };
}

function renderReview() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/lab-assistant/imports/1"]}>
        <Routes>
          <Route path="/lab-assistant/imports/:importId" element={<ImportReviewPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ImportReviewPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(labsApi.labsApi.list).mockResolvedValue([{ id: 9, code: "C-202", name: "Lab C-202", capacity: 72, location: { wing: "C", floor: "3", roomNumber: "04" }, labType: { id: 1, code: "COMPUTER", name: "Computer Lab" }, active: true }]);
  });

  it("renders summary counts and per-row status for 1 valid, 1 warning, 1 error row", async () => {
    const rows = [
      row({ id: 1, rowNumber: 1, validationStatus: "VALID" }),
      row({
        id: 2,
        rowNumber: 2,
        validationStatus: "WARNING",
        validationMessages: [{ severity: "WARNING", code: "FACULTY_NAME_MISMATCH", message: "PDF lists a different faculty name than assigned." }],
      }),
      row({
        id: 3,
        rowNumber: 3,
        validationStatus: "ERROR",
        labCode: null,
        validationMessages: [{ severity: "ERROR", code: "SOFTWARE_MISMATCH", message: "Lab B-101 does not have all software required by BDA." }],
      }),
    ];
    vi.mocked(timetableImportsApi.timetableImportsApi.detail).mockResolvedValue(detailWith(rows));

    renderReview();

    expect(await screen.findByText("timetable.pdf")).toBeInTheDocument();
    const totalRowsCard = screen.getByText("Total Rows").closest("div");
    expect(within(totalRowsCard!).getByText("3")).toBeInTheDocument();
    expect(screen.getByText(/does not have all software required by bda/i)).toBeInTheDocument();
    expect(screen.getByText(/pdf lists a different faculty name/i)).toBeInTheDocument();
    // Correction action available on the reviewable import
    expect(screen.getAllByRole("button", { name: /correct/i }).length).toBeGreaterThan(0);
  });

  it("shows the explicit approve-does-not-publish boundary language", async () => {
    const rows = [row({ id: 1, rowNumber: 1, validationStatus: "VALID" })];
    vi.mocked(timetableImportsApi.timetableImportsApi.detail).mockResolvedValue(detailWith(rows, { status: "VALIDATED" }));

    renderReview();
    const approveButton = await screen.findByRole("button", { name: /approve import/i });
    await userEvent.click(approveButton);

    expect(await screen.findByText(/will NOT publish the timetable to students/i)).toBeInTheDocument();
    expect(screen.getByText(/will create confirmed allocations/i)).toBeInTheDocument();
  });

  it("submits a correction and reflects the server's revalidated result, not an optimistic guess", async () => {
    const errorRow = row({
      id: 3,
      rowNumber: 1,
      validationStatus: "ERROR",
      labCode: "B-101",
      labId: 1,
      validationMessages: [{ severity: "ERROR", code: "SOFTWARE_MISMATCH", message: "Lab B-101 does not have all software required by BDA." }],
    });
    vi.mocked(timetableImportsApi.timetableImportsApi.detail).mockResolvedValueOnce(detailWith([errorRow]));
    vi.mocked(timetableImportsApi.timetableImportsApi.correctRow).mockResolvedValue({
      ...errorRow,
      labCode: "C-202",
      labId: 9,
      validationStatus: "VALID",
      validationMessages: [],
      corrected: true,
    });
    vi.mocked(timetableImportsApi.timetableImportsApi.detail).mockResolvedValueOnce(
      detailWith([{ ...errorRow, labCode: "C-202", labId: 9, validationStatus: "VALID", validationMessages: [], corrected: true }]),
    );

    renderReview();
    await userEvent.click(await screen.findByRole("button", { name: /correct/i }));
    expect(await screen.findByText("Correct Row 1")).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/^lab$/i), "9");
    await userEvent.click(screen.getByRole("button", { name: /save & revalidate/i }));

    await waitFor(() => expect(timetableImportsApi.timetableImportsApi.correctRow).toHaveBeenCalledWith(1, 3, { labId: 9, startTime: "09:00:00", endTime: "11:00:00" }));
  });
});
