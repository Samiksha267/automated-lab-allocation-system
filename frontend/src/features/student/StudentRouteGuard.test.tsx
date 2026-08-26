import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { ProtectedRoute } from "../auth/ProtectedRoute";
import { RequireRouteRole } from "../auth/RequireRouteRole";
import * as authApi from "../../api/auth";

vi.mock("../../api/auth");

/** Phase 22 PART 23 (mandatory) - the Student route guard, exercised through the same RequireRouteRole mechanism Phase 20/21 built. */
function renderStudentRouteAs(role: "STUDENT" | "CR" | "LAB_ASSISTANT" | null) {
  if (role) {
    localStorage.setItem("lab_allocation_access_token", "signed.jwt.token");
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ id: 1, email: "x@example.edu", displayName: "X", role });
  }
  return render(
    <MemoryRouter initialEntries={["/student"]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>role home</div>} />
          <Route path="/login" element={<div>login page</div>} />
          <Route
            path="/student"
            element={
              <ProtectedRoute>
                <RequireRouteRole roles={["STUDENT"]}>
                  <div>Student content</div>
                </RequireRouteRole>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe("Student route guard", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("lets a STUDENT reach /student", async () => {
    renderStudentRouteAs("STUDENT");
    expect(await screen.findByText("Student content")).toBeInTheDocument();
  });

  it("denies a CR, redirecting away from Student content", async () => {
    renderStudentRouteAs("CR");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("Student content")).not.toBeInTheDocument();
  });

  it("denies a LAB_ASSISTANT, redirecting away from Student content", async () => {
    renderStudentRouteAs("LAB_ASSISTANT");
    expect(await screen.findByText("role home")).toBeInTheDocument();
    expect(screen.queryByText("Student content")).not.toBeInTheDocument();
  });

  it("redirects an unauthenticated visitor to login, not the role home, without a loop", async () => {
    renderStudentRouteAs(null);
    expect(await screen.findByText("login page")).toBeInTheDocument();
    expect(screen.queryByText("Student content")).not.toBeInTheDocument();
  });
});
