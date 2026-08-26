import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import type { UserSummary } from "../../api/auth";

/**
 * Route-level role guard (PART 55/3 of the Phase 20 brief) - wraps
 * `ProtectedRoute`'s authentication check with a role check that *redirects*
 * (unlike `RequireRole`, which only conditionally hides inline content).
 * A signed-in user of the wrong role is sent to their own role's home
 * (`/`) rather than left on a blank page or bounced back to `/login` -
 * avoids the infinite-redirect risk the brief calls out (PART 55), since
 * `/` never itself requires a specific role.
 *
 * This is a UX convenience only, exactly like `RequireRole` - the backend's
 * `@PreAuthorize` remains the actual security boundary (docs/09-AUTHORIZATION-RBAC.md).
 */
export function RequireRouteRole({ roles, children }: { roles: UserSummary["role"][]; children: ReactNode }) {
  const { user, isLoading } = useAuth();
  // Mirrors ProtectedRoute's own isLoading guard - without this, a page
  // refresh briefly has `user === null` (before the stored token's `/auth/me`
  // check resolves) and would incorrectly redirect a valid LAB_ASSISTANT
  // session away before it ever had a chance to load (a real bug this
  // exact check caught in this phase's own tests).
  if (isLoading) {
    return null;
  }
  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
