/**
 * JWT storage strategy - chosen deliberately, not by default.
 *
 * Stored in `localStorage`, not an HttpOnly cookie. Trade-off, acknowledged
 * rather than ignored:
 * - XSS risk: any script that runs in this origin (e.g. via a dependency
 *   compromise) can read `localStorage` and exfiltrate the token. An HttpOnly
 *   cookie is immune to this specific read, since JavaScript cannot access it
 *   at all.
 * - CSRF risk: `localStorage` is NOT auto-attached by the browser to
 *   cross-site requests (unlike a cookie), so this choice actually sidesteps
 *   CSRF entirely (consistent with the backend's CSRF-disabled stance -
 *   see docs/15-DESIGN-DECISIONS.md and docs/09-AUTHORIZATION-RBAC.md).
 * - Simplicity: no CSRF-token plumbing, no cookie `SameSite`/`Secure`/domain
 *   configuration, and the token is trivially available to attach to the
 *   `Authorization` header on every request - appropriate for this project's
 *   current scope (a college project, not a bank).
 *
 * **Production hardening path** (not implemented here, flagged honestly):
 * an HttpOnly, `Secure`, `SameSite=Strict` cookie issued by the backend,
 * with CSRF protection re-enabled and scoped to state-changing requests,
 * removes the XSS-read risk at the cost of the CSRF complexity this project
 * currently avoids. See ADR in docs/15-DESIGN-DECISIONS.md.
 */

const STORAGE_KEY = "lab_allocation_access_token";

export const tokenStorage = {
  get(): string | null {
    return localStorage.getItem(STORAGE_KEY);
  },
  set(token: string): void {
    localStorage.setItem(STORAGE_KEY, token);
  },
  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
  },
};
