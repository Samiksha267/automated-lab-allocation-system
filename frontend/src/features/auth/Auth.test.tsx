import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import { LoginPage } from "./LoginPage";
import { ProtectedRoute } from "./ProtectedRoute";
import { ApiError } from "../../api/client";
import * as authApi from "../../api/auth";

vi.mock("../../api/auth");

const mockUser: authApi.UserSummary = {
  id: 1,
  email: "cr@example.edu",
  displayName: "Demo CR",
  role: "CR",
};

function AuthStatusProbe() {
  const { isAuthenticated, user, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{isAuthenticated ? `authenticated:${user?.role}` : "anonymous"}</span>
      <button onClick={logout}>logout</button>
    </div>
  );
}

describe("auth foundation", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  it("renders the login form with email and password fields", () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("stores the token and sets auth state on successful login", async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: "signed.jwt.token",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: mockUser,
    });

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<AuthStatusProbe />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    await userEvent.type(screen.getByLabelText(/email/i), "cr@example.edu");
    await userEvent.type(screen.getByLabelText(/password/i), "correct-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated:CR"));
    expect(localStorage.getItem("lab_allocation_access_token")).toBe("signed.jwt.token");
  });

  it("shows an error message on invalid credentials without revealing which field was wrong", async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ code: "INVALID_CREDENTIALS", message: "Invalid email or password." }, 401),
    );

    render(
      <MemoryRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await userEvent.type(screen.getByLabelText(/email/i), "cr@example.edu");
    await userEvent.type(screen.getByLabelText(/password/i), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/invalid email or password/i);
  });

  it("redirects an unauthenticated user away from a protected route", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>login page</div>} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <div>protected content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("login page")).toBeInTheDocument();
    expect(screen.queryByText("protected content")).not.toBeInTheDocument();
  });

  it("allows an authenticated user (valid stored token) to reach a protected route", async () => {
    localStorage.setItem("lab_allocation_access_token", "signed.jwt.token");
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(mockUser);

    render(
      <MemoryRouter initialEntries={["/"]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>login page</div>} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <div>protected content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("protected content")).toBeInTheDocument();
  });

  it("clears auth state and stored token on logout", async () => {
    localStorage.setItem("lab_allocation_access_token", "signed.jwt.token");
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(mockUser);

    render(
      <MemoryRouter>
        <AuthProvider>
          <AuthStatusProbe />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated:CR"));

    await userEvent.click(screen.getByRole("button", { name: /logout/i }));

    expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    expect(localStorage.getItem("lab_allocation_access_token")).toBeNull();
  });
});
