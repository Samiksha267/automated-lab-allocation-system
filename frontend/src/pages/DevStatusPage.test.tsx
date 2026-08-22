import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { DevStatusPage } from "./DevStatusPage";
import * as healthApi from "../api/health";

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("DevStatusPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("shows Connected once the backend health check resolves UP", async () => {
    vi.spyOn(healthApi, "fetchBackendHealth").mockResolvedValue({ status: "UP" });

    renderWithClient(<DevStatusPage />);

    expect(await screen.findByText("Connected")).toBeInTheDocument();
  });

  it("shows Unavailable when the backend health check fails", async () => {
    vi.spyOn(healthApi, "fetchBackendHealth").mockRejectedValue(new Error("network down"));

    renderWithClient(<DevStatusPage />);

    expect(await screen.findByText(/Unavailable/)).toBeInTheDocument();
  });
});
