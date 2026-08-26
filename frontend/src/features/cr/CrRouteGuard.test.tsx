import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { ProtectedRoute } from "../auth/ProtectedRoute";
import { RequireRouteRole } from "../auth/RequireRouteRole";
import * as authApi from "../../api/auth";

vi.mock("../../api/auth");

/** PART 59 (mandatory) - the CR route tree, exercised through the same RequireRouteRole mechanism Phase 20 built, parameterized for CR here. */
function renderCrRouteAs(role: "CR" | "LAB_ASSISTANT" | "STUDENT" | null) {
  if (role) {
    localStorage.setItem("lab_allocation_access_token", "signed.jwt.token");
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ id: 1, email: "x@example.edu", displayName: "X", role });
  }
  return render(
    <MemoryRouter initialEntries={["/cr"]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>role home</div>} />
          <Route path="/login" element={<div>login page</div>} />
          <Route
            path="/cr"
            element={
              <ProtectedRoute>
                <RequireRouteRole roles={["CR"]}>
                  <div>CR content</div>
                </RequireRouteRole>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("CR route guard", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("lets a CR reach /cr", async () => {
    renderCrRouteAs("CR");
    expect(await screen.findByText("CR content")).toBeInTheDocument();
  });

  it("denies a LAB_ASSISTANT, redirecting away from CR content", async () => {
    renderCrRouteAs("LAB_ASSISTANT");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("CR content")).not.toBeInTheDocument();
  });

  it("denies a STUDENT, redirecting away from CR content", async () => {
    renderCrRouteAs("STUDENT");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("CR content")).not.toBeInTheDocument();
  });

  it("redirects an unauthenticated visitor to login, not the role home, without a loop", async () => {
    renderCrRouteAs(null);
    expect(await screen.findByText("login page")).toBeInTheDocument();
    expect(screen.queryByText("CR content")).not.toBeInTheDocument();
  });
});
