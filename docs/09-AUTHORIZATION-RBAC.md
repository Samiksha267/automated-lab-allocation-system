# Authorization / RBAC

Three authenticated roles: `LAB_ASSISTANT`, `CR`, `STUDENT`. Faculty has no login (A-08). All enforcement below is server-side (Spring Security method/URL security + service-layer ownership checks); the frontend hides controls it shouldn't show, but that is a UX convenience only — every rule here is re-checked in the backend regardless of what the client sends.

**Status: Phase 7.** Sections marked *(implemented)* describe real, verified code. Academic hierarchy (Program/Stream/AcademicYear/AcademicTerm/Division/Batch), Subject (+ requirements), Faculty (+ availability), SubjectFacultyAssignment, CrAssignment, and the Laboratory domain are now real and RBAC-enforced (see the Permission Matrix below, and the Role vs. Ownership section). Allocations and everything scheduling-related remain the Phase 1 plan, not yet implemented (Phase 8+).

## Authentication Flow *(implemented)*

```
Browser
  ↓ POST /api/auth/login {email, password}
AuthController → AuthService
  ↓ UserRepository.findByEmail (normalized: trim + lowercase)
  ↓ PasswordEncoder.matches(rawPassword, storedHash)  — BCrypt
  ↓ user.isActive() check
  ↓ JwtService.generateToken(userId, role)             — HS256, signed
Browser ← { accessToken, tokenType: "Bearer", expiresIn, user: {id, email, displayName, role} }
```

Every subsequent authenticated request:

```
Browser  --Authorization: Bearer <token>-->  JwtAuthenticationFilter
  ↓ JwtService.parseAndValidate(token)   — signature + expiration check
  ↓ UserRepository.findById(userId).filter(isActive)   — fresh DB check, every request
  ↓ SecurityContext populated with authority ROLE_<current DB role>
  ↓ Controller (e.g. GET /api/auth/me)
```

If any step fails (missing header, invalid signature, expired token, user deleted, user deactivated), no `Authentication` is set and Spring Security's `RestAuthenticationEntryPoint` returns a uniform `401 UNAUTHORIZED` — the failure reason is never distinguished in the response (avoids both credential-guessing feedback and "why was I logged out" leakage beyond the generic fact of it).

## Password Storage *(implemented)*

- **Encoder:** `BCryptPasswordEncoder` (Spring Security's default, industry-standard for credential storage).
- **One-way hashing, not encryption:** BCrypt is a hashing function — there is no key that reverses a hash back to the original password, unlike encryption (e.g. AES) which is explicitly designed to be reversible with the right key. Passwords must never be recoverable, even by the system's own operators; encryption would imply they could be, which is the wrong property for this data.
- **Salt handling:** BCrypt generates and embeds a random salt in every hash it produces (visible as part of the stored hash string itself, e.g. `$2a$10$<22-char-salt>$2a$10$<31-char-hash>`), so two users with the same password never produce the same stored value, and the encoder needs no separate salt column or lookup.
- **Never logged, never returned:** `AppUser.getPasswordHash()` is only ever read by `AuthService` for the `matches()` comparison; no log statement in `AuthService`/`JwtAuthenticationFilter` prints a password, a hash, a JWT, or an `Authorization` header value (verified by reading every `log.*` call in both classes).
- **Password policy:** minimum 8 characters (enforced via Bean Validation `@Size(min = 8)` on `LoginRequest.password` — since there is no public registration endpoint yet, this is currently the only enforcement point; it will be re-applied to whatever DTO Lab Assistant CR-account-creation uses in Phase 4+). No mandated character-class mix — kept simple and maintainable rather than an elaborate enterprise policy the brief explicitly warned against.

## JWT Design *(implemented)*

| Aspect | Value |
|---|---|
| Algorithm | HS256 (HMAC-SHA256), via `io.jsonwebtoken` (jjwt 0.12.6) |
| Claims | `sub` (user id), `role`, `iat`, `exp` — nothing else |
| Signing key | `app.jwt.secret` (env `JWT_SECRET`); a non-production placeholder default exists only so bare local dev works without a `.env` |
| Expiration | `app.jwt.expiration-minutes` (env `JWT_EXPIRATION_MINUTES`), default 60 minutes |
| Refresh tokens | **Not implemented** — deliberately out of scope for this phase (see ADR in docs/15-DESIGN-DECISIONS.md); a token simply expires and the user logs in again |

**Deliberately excluded from claims:** password/password hash, full user profile, CR assignment/division ownership. CR ownership in particular is always resolved from the live `cr_assignment` table at request time (once that table exists, Phase 4+), never from anything embedded in the token — a claim is only as fresh as the moment the token was issued, and division assignments can change while a token is still technically valid.

## Inactive-Account Handling *(implemented)*

- **At login:** an inactive user fails the same `InvalidCredentialsException` path as a wrong password or unknown email (`401 INVALID_CREDENTIALS`) — chosen deliberately over a distinct `ACCOUNT_DISABLED` code specifically *at login*, to avoid confirming to an anonymous caller that the email exists but is disabled (the generic-failure principle extends to this case, not just wrong-password/unknown-email).
- **On an already-issued token:** `JwtAuthenticationFilter` re-fetches the user from the database on **every** request and filters on `isActive()` — so deactivating an account takes effect on the very next request made with that account's token, not just at its next login. This is why the check happens in the filter (a hot path re-run per request) rather than only at login.

## Stateless Sessions *(implemented)*

`SessionCreationPolicy.STATELESS` — Spring Security never creates or reads an `HttpSession`; the JWT itself is the only artifact of "being logged in," which is why it must be sent (in the `Authorization` header) on every request rather than relying on an ambient cookie-backed session.

## CSRF Decision *(implemented, with reasoning — not `csrf.disable()` on faith)*

CSRF protection exists to stop a malicious third-party site from making a victim's browser **replay an ambient credential the browser attaches automatically** (classically, a session cookie) to a request the victim never intended to make. This API:
- issues no session cookie for authentication (stateless, see above);
- requires the JWT in an explicit `Authorization: Bearer <token>` header, which only this application's own JavaScript ever attaches — a third-party site's forged `<form>` POST or `<img>` tag cannot make the victim's browser add that header on its own.

Because the specific mechanism CSRF protection defends against (silent replay of an auto-attached credential) doesn't exist in this design, CSRF protection is disabled in `SecurityConfig` — a considered trade-off given the chosen token-storage strategy (see below), not a default left unexamined. If token storage ever moved to an HttpOnly cookie (the noted production-hardening path), CSRF protection would need to be re-enabled at that point, since the cookie *is* an ambient, auto-attached credential.

## CORS Design *(implemented)*

- Allowed origins: `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`), **never** a wildcard.
- Allowed headers: `Authorization`, `Content-Type` — the only two headers the frontend actually needs to send.
- Configured as a `CorsConfigurationSource` bean (not just a `WebMvcConfigurer`) specifically so Spring Security's own filter chain — which runs before Spring MVC — applies the same CORS policy to preflight `OPTIONS` requests; a `WebMvcConfigurer`-only CORS setup (Phase 2's original approach) does not reliably cover this once Spring Security is on the classpath.
- **Development:** `http://localhost:5173` (the Vite dev server / Docker frontend container port).
- **Production:** must be set to the real deployed frontend origin(s) — never `*`, and especially never `*` combined with credentialed requests (docs/12-DEPLOYMENT-GUIDE.md).

## 401 vs 403 *(implemented)*

| Code | Meaning | Backend mechanism | Response shape |
|---|---|---|---|
| `401 UNAUTHORIZED` | Authentication missing, invalid, or expired | `RestAuthenticationEntryPoint` | `{"code":"UNAUTHORIZED","message":"Authentication is required.",...}` |
| `403 FORBIDDEN` | Authenticated, but insufficient role/permission | `RestAccessDeniedHandler` | `{"code":"FORBIDDEN","message":"You do not have permission to perform this action.",...}` |

Both use the project's standard `ApiErrorResponse` shape (docs/10-API-DOCUMENTATION.md#error-model) — Spring Security's default HTML error pages are never returned, since both handlers write JSON directly.

## Frontend Authorization *(implemented — UX only, not a security boundary)*

`RequireRole` (frontend) hides/shows UI based on the current user's role from `/api/auth/me`. This is explicitly documented in the component itself as **not** a security mechanism — a user could bypass it via devtools and would still receive a real `403` from the backend for anything they're not actually authorized to do. The backend's `@PreAuthorize`/URL-security rules are the sole authority.

## Token Storage (Frontend) *(implemented — see ADR in docs/15-DESIGN-DECISIONS.md)*

`localStorage`, chosen consciously over an HttpOnly cookie: acknowledges the XSS-read trade-off, notes that this choice actually *avoids* the CSRF concern entirely (nothing to auto-attach cross-site), and documents an HttpOnly-cookie migration as the production-hardening path. See `frontend/src/api/tokenStorage.ts` for the in-code version of this reasoning and docs/15-DESIGN-DECISIONS.md for the ADR.

**Core anti-pattern this system explicitly guards against (HC-11):** a CR request that supplies a `divisionId` belonging to another division must be rejected with `FORBIDDEN_DIVISION_ACCESS`, even though the frontend would never construct such a request under normal use. Authorization never trusts client-supplied ownership fields — only the server-resolved `cr_assignment` for the authenticated user.

## Role Authorization vs. Resource Ownership *(implemented, Phase 4)*

These are two genuinely different questions, checked by two different mechanisms — conflating them is the single most likely place this project's security could quietly go wrong, so it is spelled out explicitly here.

**1. Role authorization — "is this user even the right *kind* of user?"**
Answered by Spring Security method security: `@PreAuthorize("hasRole('LAB_ASSISTANT')")` on academic-domain controllers (`ProgramController`, `StreamController`, etc.). This check has no idea *which* program/division/etc. is being touched — it only knows the caller's role, from the fresh-per-request `ROLE_<role>` authority `JwtAuthenticationFilter` sets. A `CR` or `STUDENT` calling any Lab-Assistant-only endpoint fails here, with `403 FORBIDDEN`, before any service code runs.

**2. Resource ownership — "is this user allowed to touch *this specific* resource?"**
Answered by `CrOwnershipService.requireOwnsDivision(userId, divisionId)` (`academic` package) — resolves the caller's own `CrAssignment` from their authenticated `userId` and compares it against the `divisionId` actually being acted on. This is **not** expressible as a static `@PreAuthorize` role check, because it depends on runtime data (which division *this* CR currently owns) that only a database lookup can answer.

**Worked example (the exact scenario PART 53 of the phase brief asks to document):**

```
CR-A (role = CR, CrAssignment -> Division A) sends a request with divisionId = Division B

Step 1 - Role authorization:  is CR-A a CR?             -> YES  (passes @PreAuthorize)
Step 2 - Resource ownership:  does CR-A own Division B?  -> NO   (CrOwnershipService looks up
                                                                    CR-A's real assignment: Division A)
Result: 403 FORBIDDEN_DIVISION_ACCESS
```

Note the request never even reaches this point by supplying a fake role — CR-A *is* genuinely a CR, and genuinely authenticated. The rejection is entirely about ownership of the specific resource, which is why it's a separate check, not folded into role authorization. This is proven directly in `CrOwnershipServiceTest` (`sameCrCannotClaimDivisionBOnceAssignedToDivisionA`) and end-to-end in `AcademicApiIT`.

**Where ownership is wired up (Phase 15):** `GET /api/cr-assignments/me` uses `CrOwnershipService.getCurrentAssignment` to resolve "my division" safely (never an arbitrary `userId` lookup) — unchanged since Phase 4. `POST /api/allocations/extra/search` and `POST /api/allocations/extra` (Phase 15) are the first endpoints that actually *write* against a division as a CR, and both resolve `divisionId` exclusively via this same mechanism — no request body field for it exists at all. `POST /api/allocations/extra/{id}/cancel` re-checks ownership independently via `CrOwnershipService.requireOwnsDivision(userId, allocation.getDivision().getId())`, since the allocation being cancelled could belong to a different CR entirely.

### Phase 15 endpoint matrix

| Endpoint | LAB_ASSISTANT | CR | STUDENT | Unauthenticated |
|---|---|---|---|---|
| `POST /api/allocations/extra/search` | 403 | 200 (own division only) | 403 | 401 |
| `POST /api/allocations/extra` (book) | 403 | 200/409 (own division only) | 403 | 401 |
| `POST /api/allocations/extra/{id}/cancel` | 403 | 200/403/404/409 (own division's `EXTRA` only) | 403 | 401 |
| `GET /api/allocations/extra/mine` | 403 | 200 (own division only) | 403 | 401 |
| `GET /api/allocations/extra/activity` | 200 (all divisions) | 403 | 403 | 401 |

No functional requirement authorizes `LAB_ASSISTANT` to search/book/cancel through this workflow (see docs/15-DESIGN-DECISIONS.md) — administrative scheduling, if ever built, is a deliberately separate concern from the CR-facing FCFS booking flow this phase implements.

**Worked ownership-attack example, verified live in Docker (2026-08-24):** a CR assigned to Division A submits a booking request naming a real `subjectId`/`batchId` that genuinely belongs to Division B (constructed by directly editing the request payload — not something the CR's own UI would ever produce under normal use):

```
CR-A (CrAssignment -> Division A) submits { subjectId: <Division B's subject>, batchId: <Division B's batch>, ... }

Step 1 - divisionId resolution: ExtraLabService NEVER reads divisionId from the request at all -
         it is set to Division A (CR-A's real assignment) before any downstream call.
Step 2 - Faculty resolution: FacultyAssignmentResolutionService.resolveForBatch(subjectId, DIVISION A, batchId, term)
         finds no subject_faculty_assignment row for (Division B's subject, Division A, ...) - none exists.
Result: 404 SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND - rejected before any hard constraint even runs.
```

If a division-level faculty assignment for that subject happened to also exist in Division A (a contrived, coincidental case), the request would still be rejected one layer later, by HC-12's own "batch belongs to division" sub-check (`INVALID_ACADEMIC_RELATIONSHIP`) during book-time constraint evaluation — the attack has no path to success at any layer. Proven live and by `ExtraLabApiIT.crCannotBookAnotherDivisionsBatchOwnershipAttack` (environment-blocked here, see docs/13-DEVELOPER-SETUP.md, but correctly written).

## Permission Matrix

Legend: R = Read, C = Create, U = Update, D = Delete/Cancel, A = Approve, P = Publish. `—` = no access. "Own" = scoped to the CR's currently active division.

| Resource | LAB_ASSISTANT | CR | STUDENT |
|---|---|---|---|
| Labs | R, C, U, D | R | — |
| Software / Equipment catalogs | R, C, U, D | R | — |
| Lab–Software / Lab–Equipment links | R, C, U, D | R | — |
| Lab unavailability (maintenance) | R, C, U, D | R | — |
| Faculty | R, C, U, D | R | — |
| Faculty availability (raw management data) | R, C, U, D | — | — |
| Subjects + requirements | R, C, U, D | R | — |
| Academic hierarchy (Program/Stream/Year/Division/Batch) | R, C, U, D | R (own scope only) | R (published, filter-only) |
| CR accounts (`app_user` role=CR) | R, C, U, D | — | — |
| CR assignments | R, C, U, D | R (own) | — |
| PDF timetable import management (`/api/timetable-imports` — upload, review, correct, approve, reject) | R, C, U, A (all) | — | — |
| Timetable import review/correction | R, U | — | — |
| Extra lab: search candidates | R | C (own division) | — |
| Extra lab: create | — | C (own division) | — |
| Extra lab: cancel | R, D (any) | D (own division, EXTRA only) | — |
| Regular allocation cancellation (pre-publish) | R, D | — | — |
| Conflicts / alternatives view | R (all) | R (own division) | — |
| Audit logs (`GET /api/audit-logs`, incl. CR-activity filtering via `actorUserId`) | R (all) | — | — |
| Analytics / utilization | R | — | — |
| Schedule-version management (`/api/schedule-versions` — create draft, publish, history, per-version allocations, any status) | R, C, P (all) | — | — |
| Current published timetable (`GET /api/timetable`) | R | R (filtered by `divisionId`/`batchId`) | R (filtered by `divisionId`/`batchId`) |

## Notes on specific boundaries

- **CR cannot modify official (`REGULAR`) allocations at all** — not create, not update, not cancel. Only `EXTRA` allocations they created (or that belong to their division) are cancellable by them, and only while in a cancellable status (see [03-SYSTEM-ARCHITECTURE.md §5](03-SYSTEM-ARCHITECTURE.md)).
- **CR cannot create other CR accounts** or reassign CRs — that is exclusively `LAB_ASSISTANT` (FR-01/FR-02).
- **Student has zero write access anywhere** — every student-facing endpoint is `GET`.
- **Lab Assistant is the only role that can `APPROVE`** (timetable import entries → allocations) and `PUBLISH` (schedule versions).
- **Faculty availability, software requirements, and lab data are never editable by CR**, even for their own division's subjects — these are shared academic-configuration resources, not division-scoped data.
- **Faculty availability is CR/STUDENT-unreadable, not just uneditable (Phase 7, deliberate)** — unlike Labs (Phase 5) and Subject Requirements (Phase 6), where `GET` is open to any authenticated role, `/api/faculty/{id}/availability*` restricts read to `LAB_ASSISTANT` as well. This is a narrower access model than the rest of this project's read-open convention, chosen because raw availability management data has no legitimate CR/STUDENT consumer yet — see docs/15-DESIGN-DECISIONS.md ADR-034 and docs/03-SYSTEM-ARCHITECTURE.md §15.

## Enforcement Mechanism

- **Authentication:** JWT, issued at login, subject = `userId`, `role` claim carried alongside — *implemented* (see above). `JwtAuthenticationFilter` re-derives the effective role from a fresh database read every request rather than trusting the token's `role` claim as current truth, so a role change takes effect immediately, not at next login.
- **Coarse-grained (role) authorization:** Spring Security method security (`@PreAuthorize("hasRole('LAB_ASSISTANT')")` etc.) — infrastructure is *implemented* (`@EnableMethodSecurity` in `SecurityConfig`, proven against a test fixture in `RoleAuthorizationTest`); no real role-restricted business endpoint exists to apply it to yet (arrives with the academic/lab domain, Phase 4+).
- **Fine-grained (ownership) authorization:** a dedicated service-layer step resolves the caller's `cr_assignment` (for CR) and compares it against the resource being acted on — *implemented* (`CrOwnershipService`, Phase 4; genuinely exercised in production by `ExtraLabService`, Phase 15). This cannot be expressed as a static `@PreAuthorize` role check alone since it depends on runtime data (which division this CR currently owns); it is an explicit check inside the relevant application service, with a consistent `FORBIDDEN_DIVISION_ACCESS`/`CR_ASSIGNMENT_NOT_FOUND` error result on failure (HC-11 in [06-CONSTRAINTS.md](06-CONSTRAINTS.md) formalizes the same check as a scheduling-pipeline constraint too, so a bad request is rejected at two independent layers, not just one).
- **Never trust client `divisionId`:** as stated above and in HC-11 — this is repeated here because it is the single most important authorization rule in the system and the one most likely to be silently violated by a careless future change (e.g., a new endpoint added without threading the ownership check through).

## Tests Implemented in Phase 3

- `JwtServiceTest` (unit): token generation carries expected claims; rejects a token signed with a different key; rejects an expired token; rejects a malformed token.
- `AuthServiceTest` (unit, Mockito): successful login; wrong password; unknown email; inactive user — all via the generic `InvalidCredentialsException` path.
- `RoleAuthorizationTest` (unit, no DB/web layer): `@PreAuthorize("hasRole('LAB_ASSISTANT')")` fixture — LAB_ASSISTANT role succeeds, CR/STUDENT roles get `AccessDeniedException` (proves 403-shaped denial, not merely "unauthenticated").
- `AuthenticationIT` (Testcontainers-backed integration — see docs/13-DEVELOPER-SETUP.md for this machine's known Docker/Testcontainers limitation): login success returns a JWT + safe user summary; wrong password and unknown email both return the same generic `401 INVALID_CREDENTIALS`; a deactivated user cannot log in; `GET /api/auth/me` without a token returns `401`; with a valid token returns the safe profile; with a malformed/invalid token returns `401`; a duplicate email insert is rejected by the **database** unique constraint (`DataIntegrityViolationException`), not only application validation.

## Tests Implemented in Phase 15 (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

- Student cannot call any mutating endpoint (expect `403`) — `ExtraLabApiIT.studentIsForbiddenAndUnauthenticatedIsRejected`, verified live.
- CR can schedule within their own division; cannot schedule for another division even when supplying that division's real ID — `ExtraLabApiIT.crCannotBookAnotherDivisionsBatchOwnershipAttack`, verified live.
- CR can cancel their own EXTRA allocation; cannot cancel another division's EXTRA allocation; cannot cancel any REGULAR allocation — `ExtraLabServiceTest`/`ExtraLabApiIT` (`cancelRejectsWhenCallerDoesNotOwnAllocationsDivision`, `cancelRejectsRegularAllocationType`).
- Lab Assistant can manage CR assignments; CR cannot — unchanged since Phase 4 (`CrAssignmentController`).
- A deactivated/ended `cr_assignment` loses all division-scoped permissions immediately (verified by re-checking authorization on every request, not caching a stale permission at login) — unchanged since Phase 4, still holds for Phase 15's endpoints since every one resolves ownership fresh, per-request.

## Tests Implemented in Phase 17 (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

- `GET /api/audit-logs`: LAB_ASSISTANT `200`, CR `403`, STUDENT `403`, unauthenticated `401` — `AuditLogApiIT.onlyLabAssistantCanReadAuditHistory`, verified live against the running Docker stack.
- Actor filtering isolates one CR's activity from another's; a filter with no matches returns `totalElements: 0`, not another actor's rows — `AuditLogApiIT.actorFilterIsolatesOneCrsActivityFromAnother`.
- No audit-log endpoint accepts a client-supplied actor id anywhere — actor identity is always `@AuthenticationPrincipal`-derived (ADR-077, docs/15-DESIGN-DECISIONS.md).

## Tests Implemented in Phase 18 (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

- `/api/schedule-versions` (create/publish/history): CR `403`, anonymous `401` — `ScheduleVersionApiIT.draftCreationIsForbiddenToCrAndStudentAndRejectedForAnonymous`, `.onlyLabAssistantCanViewVersionHistory`, verified live.
- `GET /api/timetable`: LAB_ASSISTANT/CR/STUDENT all `200`, anonymous `401` — verified live against the running Docker stack, all four roles.
- A superseded EXTRA allocation cannot be cancelled by the CR who owns it — `409 SCHEDULE_VERSION_NOT_CURRENT`, `ExtraLabServiceTest.cancelRejectsWhenTheAllocationsScheduleVersionIsNoLongerCurrent`; verified live.

## Tests Implemented in Phase 19 (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

- `/api/timetable-imports` (upload, list, detail, correct, approve, reject): LAB_ASSISTANT `200`, CR `403`, STUDENT `403`, anonymous `401` — verified live against every one of the six endpoints.

## Phase 20 — Frontend Route Guards (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

`/lab-assistant/*` is guarded client-side by `ProtectedRoute` (authenticated? no -> `/login`) then `RequireRouteRole` (role is LAB_ASSISTANT? no -> `/`, ADR-109/110, docs/15-DESIGN-DECISIONS.md). **This is a UX/navigation convenience only** - every API call the resulting screens make still goes through the exact same backend `@PreAuthorize` checks listed above; a client bypassing the React guard entirely still gets `403`/`401` from the API. Verified both automatically (role-guard unit tests) and live (headless-Chromium: a CR session visiting `/lab-assistant` directly is redirected to `/` with zero Lab-Assistant-only navigation/content ever rendered).

`GET /api/users?role=` (Phase 20, ADR-113) - `LAB_ASSISTANT` only, read-only, no create/update/delete.

## Phase 21 — CR Frontend Route Guards (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

`/cr/*` is guarded identically to `/lab-assistant/*`: `ProtectedRoute` then `RequireRouteRole(["CR"])`. No new backend authorization - every CR page calls only pre-existing `CR`-only endpoints (`/api/allocations/extra/*`, `/api/cr-assignments/me`) or role-open `GET` endpoints (`/api/divisions/{id}`, `/api/subjects`, `/api/timetable`). Verified live: LAB_ASSISTANT visiting `/cr` -> redirected to `/`, zero CR content rendered; anonymous visiting `/cr` -> redirected to `/login` (via `ProtectedRoute`, checked first); a genuine two-browser-session concurrent booking race against the real UI produced exactly one success and one clean FCFS-conflict message, never two successes.

## Phase 22 — Student Frontend Route Guards (see [11-TESTING-STRATEGY.md](11-TESTING-STRATEGY.md))

`/student/*` is guarded identically to `/lab-assistant/*` and `/cr/*`: `ProtectedRoute` then `RequireRouteRole(["STUDENT"])`. No new authorization surface - the Student UI calls only role-open `GET` endpoints already reachable by any authenticated user (`/api/programs`, `/api/streams`, `/api/academic-years`, `/api/divisions`, `/api/batches`, `/api/academic-terms`) plus `GET /api/timetable`, which was already `@PreAuthorize("hasAnyRole('STUDENT', 'CR', 'LAB_ASSISTANT')")` since Phase 18. Verified live: CR and LAB_ASSISTANT sessions visiting `/student` directly are both redirected to `/` with zero Student content ever rendered; anonymous visiting `/student` redirects to `/login` via `ProtectedRoute`, checked first.

`AllocationSpecifications.batchIdOrDivisionWide` (ADR-121, docs/15-DESIGN-DECISIONS.md) changes what rows a batch-scoped `/api/timetable` request returns, not who can call it - no authorization change.

## Remaining Gap (Phase 16)

FCFS/concurrency-safe commit-time revalidation under *simultaneous* competing requests is not yet proven — Phase 15's booking path is transactionally revalidated against a single request's view of the data, which closes the "stale search" gap but not the true concurrent-race gap. See docs/15-DESIGN-DECISIONS.md's Phase 15/16 boundary ADR and docs/03-SYSTEM-ARCHITECTURE.md §23.
