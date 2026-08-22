import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";
import type { UserSummary } from "../../api/auth";

/**
 * Role-gated rendering for UX only (hides controls a role shouldn't see).
 * This is NOT a security boundary - the backend enforces `@PreAuthorize`
 * (see docs/09-AUTHORIZATION-RBAC.md) on every endpoint regardless of what
 * this component renders. A user could open devtools and force this to
 * render, and still get a 403 from the API for anything they're not
 * actually permitted to do.
 */
export function RequireRole({
  roles,
  children,
  fallback = null,
}: {
  roles: UserSummary["role"][];
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
}
