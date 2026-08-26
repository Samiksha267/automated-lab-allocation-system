import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthProvider } from "./AuthContext";
import { RequireRouteRole } from "./RequireRouteRole";
import * as authApi from "../../api/auth";

vi.mock("../../api/auth");

function renderAt(role: "LAB_ASSISTANT" | "CR" | "STUDENT" | null) {
  if (role) {
    localStorage.setItem("lab_allocation_access_token", "signed.jwt.token");
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ id: 1, email: "x@example.edu", displayName: "X", role });
  }
  return render(
    <MemoryRouter initialEntries={["/lab-assistant"]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>role home</div>} />
          <Route
            path="/lab-assistant"
            element={
              <RequireRouteRole roles={["LAB_ASSISTANT"]}>
                <div>lab assistant content</div>
              </RequireRouteRole>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("RequireRouteRole", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("lets a LAB_ASSISTANT reach the guarded route", async () => {
    renderAt("LAB_ASSISTANT");
    expect(await screen.findByText("lab assistant content")).toBeInTheDocument();
  });

  it("redirects a CR away from the guarded route", async () => {
    renderAt("CR");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("lab assistant content")).not.toBeInTheDocument();
  });

  it("redirects a STUDENT away from the guarded route", async () => {
    renderAt("STUDENT");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("lab assistant content")).not.toBeInTheDocument();
  });

  it("redirects an unauthenticated user away from the guarded route", async () => {
    renderAt(null);
    expect(await screen.findByText("role home")).toBeInTheDocument();
  });
});
