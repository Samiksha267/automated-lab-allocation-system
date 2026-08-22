import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

/**
 * Unauthenticated -> redirect to /login (remembering where they were headed).
 * Authenticated -> render children. This is a UX convenience only - the
 * backend's Spring Security filter chain is the actual authorization
 * authority; a determined client could bypass this component entirely and
 * would still be rejected server-side (docs/09-AUTHORIZATION-RBAC.md).
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return null;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  return <>{children}</>;
}
