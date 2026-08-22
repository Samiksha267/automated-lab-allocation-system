import { useState, type FormEvent } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { ApiError } from "../../api/client";

type Status = "idle" | "loading" | "invalid-credentials" | "unexpected-error";

/**
 * Simple, professional login form. Fields: email, password. Visual polish is
 * deliberately modest at this phase - dashboards/branding arrive later.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState<Status>("idle");

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setStatus("loading");
    try {
      await login(email, password);
      const redirectTo = (location.state as { from?: string } | null)?.from ?? "/";
      navigate(redirectTo, { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVALID_CREDENTIALS") {
        setStatus("invalid-credentials");
      } else {
        setStatus("unexpected-error");
      }
    }
  }

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-8">
      <form
        onSubmit={handleSubmit}
        className="max-w-sm w-full bg-white rounded-lg shadow p-6 space-y-4"
        noValidate
      >
        <h1 className="text-xl font-semibold text-slate-900">Sign in</h1>

        <div className="space-y-1">
          <label htmlFor="email" className="block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        <div className="space-y-1">
          <label htmlFor="password" className="block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="password"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
          />
        </div>

        {status === "invalid-credentials" && (
          <p role="alert" className="text-sm text-red-600">
            Invalid email or password.
          </p>
        )}
        {status === "unexpected-error" && (
          <p role="alert" className="text-sm text-red-600">
            Something went wrong. Please try again.
          </p>
        )}

        <button
          type="submit"
          disabled={status === "loading"}
          className="w-full rounded bg-slate-900 text-white py-2 text-sm font-medium disabled:opacity-50"
        >
          {status === "loading" ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </main>
  );
}
