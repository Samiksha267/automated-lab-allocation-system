import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuditLogsPage } from "./AuditLogsPage";
import * as auditLogsApi from "../../api/auditLogs";

vi.mock("../../api/auditLogs");

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuditLogsPage />
    </QueryClientProvider>,
  );
}

describe("AuditLogsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(auditLogsApi.auditLogsApi.search).mockResolvedValue({
      content: [
        {
          id: 1,
          actorUserId: 1,
          actorDisplayName: "Demo Lab Assistant",
          actorEmail: "lab.assistant@example.edu",
          actorRole: "LAB_ASSISTANT",
          action: "SCHEDULE_PUBLISHED",
          resourceType: "SCHEDULE_VERSION",
          resourceId: 5,
          resourceDisplay: "Semester 5 v2",
          academicTermId: 1,
          divisionId: null,
          metadata: { versionNumber: 2 },
          createdAt: "2026-08-25T10:00:00Z",
        },
      ],
      totalElements: 42,
      totalPages: 3,
      number: 0,
      size: 20,
    });
  });

  it("renders rows and no edit/delete actions, matching the immutable backend", async () => {
    renderPage();
    expect(await screen.findByText(/semester 5 v2/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete/i })).not.toBeInTheDocument();
  });

  it("shows pagination reflecting the backend's totals", async () => {
    renderPage();
    await screen.findByText(/semester 5 v2/i);
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();
    expect(screen.getByText(/42 results/i)).toBeInTheDocument();
  });

  it("re-queries with the selected filter", async () => {
    renderPage();
    await screen.findByText(/semester 5 v2/i);

    await userEvent.selectOptions(screen.getByLabelText(/^action$/i), "SCHEDULE_PUBLISHED");

    await waitFor(() =>
      expect(auditLogsApi.auditLogsApi.search).toHaveBeenLastCalledWith(expect.objectContaining({ action: "SCHEDULE_PUBLISHED" })),
    );
  });

  it("opens the detail view with metadata rendered as readable text, not [object Object]", async () => {
    renderPage();
    await screen.findByText(/semester 5 v2/i);
    await userEvent.click(screen.getByRole("button", { name: /details/i }));

    expect(await screen.findByText("Audit Event #1")).toBeInTheDocument();
    expect(screen.getByText(/version number/i)).toBeInTheDocument();
    expect(screen.queryByText("[object Object]")).not.toBeInTheDocument();
  });
});
