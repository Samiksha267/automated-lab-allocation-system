import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { login as loginRequest, fetchCurrentUser, type UserSummary } from "../../api/auth";
import { tokenStorage } from "../../api/tokenStorage";
import { setUnauthorizedHandler } from "../../api/client";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Lightweight auth abstraction (no Redux) exposing the shape described in
 * docs/09-AUTHORIZATION-RBAC.md's frontend section: `user`, `isAuthenticated`,
 * `isLoading`, `login`, `logout`. On mount, if a token is already stored, it
 * is verified against `/api/auth/me` so a page refresh restores the session
 * (or clears it, if the token expired/was revoked server-side) rather than
 * trusting the stored token's mere presence.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      tokenStorage.clear();
      setUser(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;

    if (!tokenStorage.get()) {
      setIsLoading(false);
      return;
    }

    fetchCurrentUser()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        // apiClient's unauthorizedHandler already clears the token on 401;
        // nothing further to do here.
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function login(email: string, password: string) {
    const response = await loginRequest({ email, password });
    tokenStorage.set(response.accessToken);
    setUser(response.user);
  }

  function logout() {
    // JWT is stateless - there is no server-side revocation call. The token
    // remains cryptographically valid until it expires; logout only removes
    // it from this browser. Acceptable trade-off for this project's scope,
    // documented in docs/15-DESIGN-DECISIONS.md - a production hardening path
    // (short-lived tokens + refresh tokens + a server-side denylist) is noted
    // there, not implemented now.
    tokenStorage.clear();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: user !== null, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
