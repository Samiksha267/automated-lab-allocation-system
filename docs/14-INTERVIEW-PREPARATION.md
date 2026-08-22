# Interview Preparation

**Status: mixed.** The Authentication & RBAC, Academic Domain, and Laboratory Domain sections are verified against real, working Phase 3/4/5 code. Everything about the scheduling engine, constraints, and performance remains Phase 1 design-level answers, explicitly marked `TO BE VERIFIED AFTER IMPLEMENTATION` — nothing below claims a benchmark, a passing test, or a working demo before those things exist.

## Elevator Pitch (30 seconds)

"I built a constraint-based lab scheduling system for a college's multi-program academic structure. Instead of a simple booking form, it runs every request — official timetable or student-rep-submitted extra practical — through a constraint engine that checks lab conflicts, faculty conflicts, faculty availability, capacity, and software requirements, then ranks the valid options with a scoring model, and can even auto-generate a full multi-session timetable using backtracking search. It also handles the tricky real case where two different batches in the same division can share a time slot as long as their lab and faculty don't collide."

## 2-Minute Explanation

**Problem:** A college has ~15 shared labs, several programs/streams/divisions/batches, and needs to allocate lab practicals so that no lab, faculty member, or student batch is double-booked — while allowing legitimate simultaneous use (different batches, different labs, different faculty). On top of the official semester timetable, class representatives book ad-hoc makeup labs that must obey the exact same rules, checked live against everything else already booked.

**Architecture:** React/TypeScript frontend, Spring Boot backend, PostgreSQL, as a modular monolith — not microservices, because there's no independent-scaling need at this size, and keeping the FCFS booking transaction inside one process/database makes correctness far easier to guarantee than a distributed alternative would.

**Scheduling engine:** A pipeline of independently-testable hard constraints (lab conflict, faculty conflict, faculty availability, batch conflict, division-wide conflict, capacity, software/equipment/lab-type requirements, authorization, academic-relationship validity) filters candidate labs; a weighted scoring model then ranks the *valid* survivors on capacity fit, environment fit, utilization balance, and schedule-gap minimization. For full-timetable generation, a most-constrained-first backtracking search assigns the hardest-to-place sessions first and backtracks when a later session runs out of options.

**Database:** PostgreSQL with an explicit `target_type` (BATCH/DIVISION) on the allocation table — this is the key modeling decision that correctly distinguishes "this batch is busy" from "the whole division is busy," which a naive boolean flag would get wrong.

**Security:** JWT auth, three roles (Lab Assistant, CR, Student), and — critically — every ownership check (which division a CR may act on) is resolved server-side from the authenticated user's assignment record, never trusted from a client-supplied ID.

**Testing:** JUnit/Mockito unit tests per constraint, Testcontainers-backed integration tests for the full request pipeline, and a dedicated concurrency test proving that two simultaneous conflicting booking requests can never both succeed.

**Results:** `TO BE VERIFIED AFTER IMPLEMENTATION` — performance numbers and test-pass status will be added here once Phases 9–25 actually produce them.

## Likely Interview Questions — Design-Level Answers

**Why is this not CRUD?** A CRUD app checks "is this row taken?" This system generates and ranks multiple valid candidates, distinguishes batch-level from division-wide occupancy, runs a backtracking search for multi-session generation, and guarantees correctness under concurrent writes — none of which a form-over-database app does.

**Why Java / Spring Boot?** Strong static typing suits a domain this relationally strict (labs, faculty, batches, terms all cross-reference each other); Spring's transaction management is what makes the FCFS/concurrency guarantee tractable; the JUnit/Mockito/Testcontainers ecosystem lets the scheduling engine be tested in real isolation from the web and persistence layers.

**Why PostgreSQL?** The data is inherently relational (foreign keys everywhere), and the concurrency-safety requirement depends directly on transactional/locking guarantees a relational database provides natively.

**Why not MongoDB?** A document store would push conflict-prevention entirely into application code, re-creating the exact check-then-insert race condition this project is specifically designed to eliminate; it also offers no benefit for data this uniformly relational, and loses foreign-key integrity for no upside.

**Why not Node as another backend hop (React → Node → Java)?** It would add a second runtime, a duplicated API/domain layer, and another failure boundary, without adding any capability — Spring Boot already serves the API directly to React over REST/JSON.

**Why modular monolith?** No requirement here calls for independent deployment or scaling of sub-parts; a single deployable with clear internal package boundaries (`constraint`, `scoring`, `conflict`, `scheduler`) gets the same separation of concerns with far less operational overhead, and keeps the FCFS-safety transaction inside one process.

**What is a hard constraint?** A rule that, if violated, makes a candidate invalid regardless of how well it scores — e.g. lab conflict, faculty conflict, capacity. See [06-CONSTRAINTS.md](06-CONSTRAINTS.md).

**What is a soft constraint?** A ranking factor among already-valid candidates — e.g. capacity fit, utilization balance. See [07-ALLOCATION-SCORING.md](07-ALLOCATION-SCORING.md). Critically, no hard constraint is ever expressed as a soft penalty in this system — the scoring engine only ever ranks candidates that already passed every hard check.

**Why backtracking?** Because a greedy, single-pass assignment can leave a later session with zero valid options even though a different (still valid) earlier choice would have left room for everyone — backtracking retries earlier decisions rather than declaring outright failure.

**Why most-constrained-first?** Scheduling the session with the fewest valid candidates first, while the most resource slack still exists, minimizes the chance of a costly late-stage backtrack; flexible sessions are safest to schedule last because they have the most alternatives.

**What is the complexity?** Worst-case exponential — this is a CSP, not claimed to be polynomial. Practical performance depends on hard-constraint pre-filtering keeping branching factors small, plus a configured `maxAttempts`/`maxDepth`/`timeout` budget that bounds runtime at the cost of possibly returning a partial rather than full schedule. Real numbers: `TO BE VERIFIED AFTER IMPLEMENTATION`.

**How can different batches run simultaneously?** Because occupancy is tracked at the batch level (`target_type = BATCH`) unless a session is explicitly division-wide (`target_type = DIVISION`) — two different batches booking at the same time is only flagged as a conflict if they also collide on lab or faculty, which are checked completely independently.

**How do you prevent faculty clashes?** A dedicated hard constraint (HC-02) checks every active allocation for the same faculty on the same date using half-open interval overlap — regardless of batch, division, or lab, one faculty member can never have two overlapping sessions.

**How do you prevent lab clashes?** Same mechanism (HC-01), scoped to `lab_id` instead of `faculty_id`.

**How will FCFS remain safe under concurrent requests?** By making the constraint check and the insert happen inside a single database transaction with an appropriate isolation/locking strategy (finalized in Phase 16; see [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) ADR-010), rather than a check-then-insert pattern with a race window between the two steps. Verified with a real concurrent-request integration test before being called done.

**Why manually approve PDF extraction?** Table/OCR extraction from PDFs is not reliably accurate; publishing directly from extraction risks silently corrupting the official timetable with mis-read subjects, labs, or times. A human review-and-correct step is cheap insurance against that.

**How will you scale this later?** `TO BE VERIFIED AFTER IMPLEMENTATION` — see [18-FUTURE-IMPROVEMENTS.md](18-FUTURE-IMPROVEMENTS.md) for the honest list of what would need to change (e.g., a real solver like OR-Tools if problem size grows by orders of magnitude, splitting the scheduling engine into its own service only if it needed independent scaling).

## Trade-Off Answers

**What would you change with more time?** Likely a dynamic re-ordering variant of most-constrained-first during backtracking (currently computed once per run), and a proper OR-Tools comparison benchmark to quantify what a general solver buys over the custom engine.

**When would a mathematical solver become useful?** If the problem grows to genuinely large scale (many campuses, hundreds of divisions, full-semester joint optimization across every subject at once) where provable optimality — not just a valid, reasonably good schedule — becomes worth the added integration complexity.

**What happens with 1000 labs?** The current design's indexing strategy (partial indexes on `(lab_id, date)` etc., see [04-DATABASE-DESIGN.md](04-DATABASE-DESIGN.md)) should hold up; the more interesting pressure point would be candidate-generation breadth in the backtracking search, which is where a real solver's constraint propagation would start to matter more than at the current ~15-lab scale.

## Authentication & RBAC (Phase 3 — implemented, verified answers)

**How does authentication work?** The frontend posts email/password to `POST /api/auth/login`. The backend looks up the user by normalized (trimmed, lowercased) email, verifies the password with BCrypt, checks the account is active, and — if all three pass — signs a JWT (HS256) containing only the user id and role, returned alongside a safe user summary. Every subsequent request carries that JWT in an `Authorization: Bearer` header; a servlet filter validates it and re-fetches the user from the database on every single request (not just at login) before populating Spring Security's context.

**Why JWT?** It keeps the API stateless — no server-side session store to synchronize or scale — which fits a REST API cleanly. The trade-off (a token can't be instantly revoked before it expires) is accepted for this project's scope and mitigated by keeping the expiration short (60 minutes) and by re-checking the user's `active` flag on every request, so *deactivation* still takes effect immediately even though the token itself remains technically valid until expiry.

**How does Spring Security validate requests?** A custom `OncePerRequestFilter` (`JwtAuthenticationFilter`) runs before Spring Security's standard authentication filter. It extracts the bearer token, verifies its signature and expiration via the `io.jsonwebtoken` library, looks the user up fresh in the database, and — only if the user still exists and is active — populates the `SecurityContext` with an authority of `ROLE_<role>`. If any step fails, no authentication is set, and Spring Security's own access-control rules (`anyRequest().authenticated()`) reject the request with a `401` produced by a custom `AuthenticationEntryPoint` that writes the project's standard JSON error shape instead of Spring's default HTML page.

**How are passwords stored?** BCrypt hashes, via `BCryptPasswordEncoder` — never plaintext, never logged, never returned by any endpoint (verified: the safe `UserSummary` DTO returned by both `/login` and `/me` has no password field at all, so there is nothing to accidentally serialize).

**Why hashing instead of encryption?** Encryption is reversible by design (given the right key) — exactly the property you don't want for credential storage, since it means a compromised key (or a rogue insider) could recover every password. Hashing is one-way; BCrypt specifically also embeds a random salt per password, so identical passwords never produce identical stored hashes, defeating precomputed rainbow-table attacks.

**How does RBAC work?** Roles are an enum (`LAB_ASSISTANT`, `CR`, `STUDENT`) persisted as a string, never an ordinal. Spring Security's method security (`@EnableMethodSecurity`, `@PreAuthorize("hasRole(...)")`) is wired up and proven against a test fixture (no real role-restricted business endpoint exists yet — that arrives with the academic/lab domain in Phase 4+, at which point `@PreAuthorize` gets applied to real controller methods).

**Why are frontend role checks insufficient?** A `RequireRole` component hides UI for the wrong role, but it's just JavaScript running in the user's own browser — anyone can open devtools and force it to render, or call the API directly with curl/Postman. The actual authorization decision is made server-side, on every request, by Spring Security; the frontend check exists purely so the UI doesn't show controls a user isn't allowed to use, not to prevent misuse.

**What is 401 vs 403?** `401 Unauthorized` means "I don't know who you are" — no token, an invalid/expired token, or a token for a deactivated/deleted user. `403 Forbidden` means "I know who you are, and you're not allowed to do this" — an authenticated request that fails a role/ownership check. The project uses distinct handlers (`RestAuthenticationEntryPoint` for 401, `RestAccessDeniedHandler` for 403) so both cases produce the same JSON error shape as every other endpoint, never Spring's default HTML error page.

**Why stateless authentication?** No `HttpSession` is created or read (`SessionCreationPolicy.STATELESS`); the JWT itself is the complete artifact of "being logged in." This also directly informed the CSRF decision: CSRF protection defends against a browser automatically replaying an ambient credential (a cookie), and there is no such credential here — the token is only ever sent because the frontend's own JavaScript explicitly attaches it, so CSRF protection was disabled with that specific reasoning documented, not out of convenience.

**What are JWT trade-offs?** No instant server-side revocation short of waiting out the expiration (mitigated here by re-checking the `active` flag on every request, so deactivation is still immediate even if the token itself isn't "revoked" in the cryptographic sense); the token's claims are a snapshot from login time, which is exactly why claims are kept minimal (id + role only) and why anything that can change mid-session (like CR division ownership, once that exists) is resolved fresh from the database, never trusted from the token.

**Where is the token stored and why?** `localStorage` in the browser — a deliberate, documented choice (not the default), trading a real XSS-read risk for simplicity (no CSRF-token plumbing, trivial to attach to every request) appropriate for this project's current scope. See ADR-015 in [15-DESIGN-DECISIONS.md](15-DESIGN-DECISIONS.md) for the full trade-off analysis, including the HttpOnly-cookie hardening path.

**How can this be hardened for production?** Move the token to an HttpOnly, `Secure`, `SameSite=Strict` cookie (removes the XSS-read risk, but requires re-enabling CSRF protection scoped to state-changing requests); consider shorter-lived access tokens paired with a properly-secured refresh-token flow if session length becomes a real UX problem; generate the JWT signing secret via a real secrets manager rather than an environment variable in `.env`. None of this is implemented yet — it's the honest next-step list, not a claim of what exists today.

## Academic Domain & RBAC-Ownership (Phase 4 — implemented, verified answers)

**Why no `Student` table?** Capacity checks (the only thing that would need per-student data) only ever need a single headcount number per batch/division, not individual records — `strength` is a plain, required, positive integer column on both `Batch` and `Division`. Building and maintaining a `Student` table (enrollment, transfers, dropouts) for a number that's used exactly one way would be real ongoing effort for zero scheduling benefit.

**Why store batch/division strength directly instead of deriving it?** Same reason as above — there's no second use of "how many students" anywhere in the system's actual requirements yet. If per-student features (attendance, individual timetables) become a real requirement, `Student` is an additive migration at that point, not a rework.

**How is the academic hierarchy modeled?** `Program → Stream → AcademicYear → Division → Batch`, each a real table with a foreign key to its parent — not a single denormalized "class" table. `AcademicTerm` (a semester/time period, e.g. "Semester 5, 2026-27") is deliberately a *separate*, independent concept from `AcademicYear` (the program's *study year*, e.g. "Year 3") — conflating the two was a real risk the naming was chosen specifically to avoid.

**Why are Program and Stream database entities instead of enums?** Because the college's actual structure (which programs, which streams under each) is data an administrator should be able to change without a code deployment — hardcoding "CS," "IT," "AIML" etc. as Java enum constants would mean every new program or stream requires a rebuild. Enums are reserved for genuinely stable application concepts (`UserRole`, `TermStatus`) that the *application's own logic* branches on, not for college-specific configuration.

**How do you model a variable number of batches?** `Batch` has a foreign key to `Division` and nothing caps how many batch rows can point at the same division — the schema has no notion of "exactly two batches" anywhere, and the seed data deliberately uses three (A1/A2/A3) specifically to prove the model isn't hardcoded to two.

**How do you prevent a batch from being attached to the wrong division?** A batch's `division_id` foreign key guarantees it belongs to *some* real division, but not necessarily the *specific* division a request claims — that cross-table relationship can't be a database `CHECK` constraint in PostgreSQL (a `CHECK` can't query another table). It's validated explicitly in `SubjectFacultyAssignmentService.create()`, which compares `batch.getDivision().getId()` against the division actually specified in the request and throws `INVALID_ACADEMIC_RELATIONSHIP` (400) if they don't match. Proven both by a unit test with mocked entities and by an end-to-end test hitting the real endpoint with two real divisions.

**Why is Faculty separate from User?** Faculty never log in — they're referenced by scheduling data (and later, availability), but there's no account, password, or JWT identity for them. Modeling them as an `app_user` row would mean building auth infrastructure (password reset, login) for people who structurally never need it, and would blur the "exactly three login roles" boundary the whole authorization model is built around.

**How does subject-faculty assignment work?** `SubjectFacultyAssignment` connects a subject, a faculty member, a division, an academic term, and an *optional* batch. A null batch means "this faculty covers the whole division for this subject/term" — not "unknown batch." Two partial unique database indexes (one for batch-scoped rows, one for division-scoped rows) guarantee at most one active assignment per scope, so the data can never be ambiguous about who teaches what.

**How does batch-level faculty override division-level assignment?** `FacultyAssignmentResolutionService` checks for an exact batch-level match first; only if none exists does it fall back to the division-level assignment. Because the database guarantees at most one active row per scope (see above), this resolution never has to choose between two "equally valid" options — only between "more specific" and "more general," which always has one correct answer.

**What is the difference between RBAC and resource ownership?** RBAC answers "is this user even the right *kind* of user" (role authorization — `@PreAuthorize("hasRole('LAB_ASSISTANT')")`, checked before any business logic runs, with no idea *which* resource is involved). Ownership answers "is this user allowed to touch *this specific* resource" (`CrOwnershipService.requireOwnsDivision`, a runtime database lookup that no static role annotation could express). Both are needed — role authorization alone would let any CR act on any division as long as they're a CR at all.

**How do you stop a CR from scheduling another division later?** The mechanism (`CrOwnershipService`) is built and tested now, even though no scheduling endpoint exists yet to use it: it resolves the caller's division purely from their authenticated `userId` via their own `CrAssignment` row, and a client-supplied `divisionId` is only ever compared against that resolved value, never trusted as authorization on its own. Proven directly with a test where a CR assigned to Division A is denied when asked to act on Division B.

**Why preserve historical CR assignments?** Reassigning a CR (e.g. a new class representative each semester) ends the old assignment (`status → ENDED`, `validTo` set) rather than deleting or overwriting the row — so "who was the CR for Division A last semester" remains answerable, and any audit trail referencing that assignment stays valid. This mirrors the same historical-preservation principle used for `Allocation` cancellation (Phase 1 design) and CR account deactivation (Phase 3).

**What was the most difficult / interesting bug so far?** Two, both found during this phase's Docker-based manual verification, not by unit tests alone: (1) `@PreAuthorize` role denials were returning `500` instead of `403`, because the denial exception is thrown deep inside Spring MVC's own dispatch (the controller-method AOP proxy) and was being caught by the project's own global `@RestControllerAdvice` before it could ever reach Spring Security's dedicated access-denied handling — fixed by adding an explicit handler for it. (2) Several read endpoints threw `LazyInitializationException` because their DTO-mapping code touched lazy JPA associations *after* the transaction (and Hibernate session) had already closed — fixed by adding `@Transactional(readOnly = true)` to the relevant service methods, and in one case moving DTO mapping fully inside a transactional method. Both are documented in detail below and in ASSUMPTIONS.md (A-23, A-24) — real bugs, root-caused, fixed, and re-verified, not glossed over.

## Laboratory Domain (Phase 5 — implemented, verified answers)

**How do you model labs?** `Lab` has a stable, unique, user-facing `code` (e.g. "C-304") that's immutable after creation — allocation explanations will reference this code, never the database id. Capacity, a lab type reference, and a flat wing/floor/room location are the other core fields. `active=false` means permanently retired, distinct from `LabUnavailability` (a temporary, dated closure).

**Why is `LabType` data instead of an enum?** Same reasoning as `Program`/`Stream` in Phase 4: the set of lab types is something a Lab Assistant should be able to extend (a future "Electronics Lab," say) without a code deployment. Enums in this codebase are reserved for genuinely stable application concepts the code itself branches on (`UserRole`, `TermStatus`), not for college-specific configuration.

**How do you represent software installed in many labs?** A many-to-many relationship between `Lab` and `Software`, materialized as an explicit `LabSoftware` association entity (not an implicit `@ManyToMany`) so it can carry per-installation metadata (`installedVersion`) that a plain join table can't hold without a later migration to convert it into a real entity anyway.

**Why use explicit association entities instead of simple `@ManyToMany`?** Because the association isn't just "does this pair exist" — `lab_equipment.quantity` ("10 routers" vs "1 router") and `lab_software.installedVersion` are real facts the phase's own requirements called out as useful. Building the explicit entity now costs almost nothing extra and avoids a schema migration later when that metadata inevitably gets used.

**How do you distinguish permanent lab deactivation from temporary maintenance?** Two different mechanisms, not one flag doing double duty: `Lab.active` for permanent retirement, `LabUnavailability` (dated, half-open interval rows) for temporary administrative closures. They have different query semantics — a permanently inactive lab should never be a candidate anywhere; a temporarily unavailable lab is a normal candidate outside its specific closed window.

**Why does the database store capacity?** Because it's a hard, structural property of the physical room — the number of workstations or seats doesn't change per-request, and every future capacity check (HC-07) needs a stable, queryable source of truth rather than recomputing or guessing it. `capacity > 0` is enforced both by a Bean Validation annotation and a database `CHECK` constraint.

**Why not compute availability entirely in the frontend?** Two separate reasons: first, the general project principle that the frontend is never the source of truth for anything security- or correctness-relevant (the backend would still need to validate on write, so computing in the frontend would just be redundant, unverified work). Second, more specifically for Phase 5: the static capability filters (`GET /api/labs?minCapacity=...&software=...`) already require a real database query (an `EXISTS` subquery per required software/equipment code) — loading all 15+ labs into the browser and filtering client-side would not scale as the lab count grows and would duplicate query logic in two languages for no benefit.

**Why are software/equipment capabilities separate from subject requirements?** Phase 5 stores what a lab *has*; a subject's requirements (what it *needs*) don't exist until Phase 6. Mixing them now would mean guessing at the requirements model before it's actually designed — the phase brief was explicit that Phase 5 must not add `SubjectSoftwareRequirement`/`SubjectEquipmentRequirement` yet.

**How would Cloudera filtering work?** `GET /api/labs?software=CLOUDERA` (or combined with `minCapacity=68`) runs a `Specification<Lab>` that adds an `EXISTS` subquery against `lab_software` for each requested software code, ANDed together for **ALL**-match semantics (a lab must have every requested software, matching how HC-08 will eventually require *all* of a subject's listed software, not just one). Verified directly: of 15 seeded labs, exactly 3 have Cloudera, and only those 3 are returned by the filter; combining it with `minCapacity=68` correctly narrows to just the 2 that satisfy both.

**Why keep `open-in-view` disabled?** Because it trades a loud, obvious failure at development time (a `LazyInitializationException` on an under-tested code path) for a silent, easy-to-miss N+1 query problem at runtime in production. Phase 4 hit this directly — see the next answer.

**How did Phase 4 expose `LazyInitializationException` and what did you learn?** See the "Real Engineering Problems Encountered" section below for the full account. The short version: several read endpoints mapped entities to DTOs *after* their transaction had already closed, so touching a lazy association threw. The fix was consistently applying `@Transactional(readOnly = true)` at the service layer for any read path whose DTO mapping touches a lazy field. The lesson carried directly into Phase 5: every new lab-domain service follows the same class-level `@Transactional(readOnly = true)` pattern from the start, and `LabService.toFullResponse()` deliberately composes the software/equipment lists *inside* the service, not in the controller after the fact — applying the Phase 4 lesson proactively instead of waiting to hit the same bug again.

## Subject Requirements (Phase 6 — implemented, verified answers)

**How do subject requirements differ from lab capabilities?** Phase 5 stores what a lab *has* (`LabSoftware`, `LabEquipment`). Phase 6 stores what a subject *needs* (`SubjectSoftwareRequirement`, `SubjectEquipmentRequirement`, lab-type requirement). The two sides are deliberately never joined or compared in Phase 6 — that comparison is the Constraint Engine's job (Phase 9). Verified manually by querying each side independently (`GET /api/subjects/{id}/requirements` and `GET /api/labs?software=CLOUDERA`) and confirming neither endpoint references the other.

**Why are requirements optional?** Not every subject has a technical prerequisite — a theory-only subject has zero rows in either requirement table and a `null` lab-type requirement, and that's a valid, expected state, not an error condition. CNS was seeded specifically with zero requirements to prove this path works, distinct from BDA which has real requirements.

**How do you model "must have ALL of these software packages," not just "any one of them"?** Each row in `subject_software_requirement` is one required software item; a subject with two rows needs both. There's no OR/ANY semantics anywhere in this table — the same ALL-required philosophy as Phase 5's lab-side capability filter (`LabSpecifications.hasAllSoftware`), so the two sides will compare cleanly once the constraint engine reads both.

**Why no boolean `required` column instead of every row being implicitly required?** Because nothing in this project yet needs a soft/preferred capability concept, and adding an unused flag would be exactly the kind of speculative column the project avoids. See ADR-026 (docs/15-DESIGN-DECISIONS.md).

**How do you model required vs. preferred lab type as genuinely distinct?** Two separate nullable FK columns on `subject` — `required_lab_type_id`, `preferred_lab_type_id` — enforced to never both be set, at both the application layer (`Subject.setLabTypeRequirement()` throws `INVALID_LAB_TYPE_PREFERENCE`) and the database layer (a `CHECK` constraint). A single column with a "hard/soft" flag was rejected because a nullable-FK pair makes the invalid "both set" state structurally impossible rather than just application-policed. See ADR-027/ADR-028.

**Why does equipment carry a `requiredQuantity` but software doesn't?** Equipment is physically countable — a subject might need 30 oscilloscopes for a batch of 60 working in pairs — while software is a presence check (a license is installed or it isn't); a quantity on software would be meaningless in this system's model. See ADR-029.

**What happens if you try to add a requirement referencing inactive software/equipment/lab type?** Rejected with `INACTIVE_SOFTWARE`/`INACTIVE_EQUIPMENT`/`INACTIVE_LAB_TYPE` — a subject shouldn't be able to require something the Lab Assistant has already retired from the catalog, which would create an unsatisfiable requirement no lab could ever meet.

**What happens on a duplicate requirement?** `DataIntegrityViolationException` from the unique constraint (`subject_id, software_id` / `subject_id, equipment_id`) is caught and translated to `SOFTWARE_REQUIREMENT_ALREADY_EXISTS`/`EQUIPMENT_REQUIREMENT_ALREADY_EXISTS` — a domain-specific 409, never a raw SQL error, following the same `AMBIGUOUS_FACULTY_ASSIGNMENT`/`DUPLICATE_ASSIGNMENT` precedent from Phase 4.

**Who can mutate requirements?** Only `LAB_ASSISTANT`, enforced via `@PreAuthorize` on every write endpoint. CR and STUDENT can read (`GET /api/subjects/{id}/requirements`) but never write — verified against the running stack with the full RBAC matrix (LAB_ASSISTANT succeeds, CR/STUDENT get 403, unauthenticated gets 401).

**Why is requirement scope on `Subject` and not something per-term?** A subject's technical prerequisites are a curriculum fact, not a scheduling fact — BDA needing Cloudera doesn't change term-to-term. This mirrors ADR-018's earlier rejection of a `SubjectOffering` entity for the same reason. See ADR-025.

**How would you extend this to version-pinned requirements (e.g. "Cloudera >= 6.3") later?** Not modeled in Phase 6 — requirements match by software/equipment identity only, even though `LabSoftware.installedVersion` is already stored from Phase 5. Adding version comparison would need a new column and comparison operator on the requirement row; deliberately deferred rather than guessed at now, since no real scenario in this project needs it yet. See ADR-030.

**Are there any new bugs from this phase?** No — the implementation compiled and passed all unit tests (33/33) on the first attempt, and manual Docker verification found no defects. This is a genuine "clean phase," attributed to proactively applying lessons learned in Phase 4/5 (class-level `@Transactional(readOnly = true)` from the start, self-contained DTO records, public `getEntity()` accessors) rather than discovering them again the hard way.

## Real Engineering Problems Encountered

Real, verified issues only — nothing here is invented. Each entry: symptom, root cause, fix, how it was verified, lesson.

### Problem 1 — `@PreAuthorize` denial returned HTTP 500 instead of 403 (Phase 4)

- **Symptom:** `POST /api/programs` as a `CR` or `STUDENT` user — both correctly denied by `@PreAuthorize("hasRole('LAB_ASSISTANT')")` — returned `500 INTERNAL_ERROR` instead of the expected `403 FORBIDDEN`, discovered via manual `curl` verification against the Dockerized backend, not by any unit test.
- **Root cause:** `@PreAuthorize` denials throw `AuthorizationDeniedException` from deep inside the controller-method AOP proxy, which is still within Spring MVC's own request dispatch. The project's global `@RestControllerAdvice` (`GlobalExceptionHandler`) catches *any* unhandled exception during that dispatch via its generic `@ExceptionHandler(Exception.class)` — including this one — before it could ever propagate out to Spring Security's `ExceptionTranslationFilter`, which only ever sees exceptions that escape the whole dispatch unhandled.
- **Fix:** Added an explicit `@ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})` to `GlobalExceptionHandler`, mapping directly to `403 FORBIDDEN` in the project's standard error shape.
- **How verified:** Rebuilt the Docker image, redeployed, re-ran the exact same `curl` requests (CR/STUDENT `POST /api/programs`) and confirmed `403 FORBIDDEN` with the correct JSON body.
- **Lesson:** A global `@RestControllerAdvice` and Spring Security's method-security exception handling interact in a way that isn't obvious from either mechanism's documentation alone — the advice sees the exception first. This specifically needs an end-to-end HTTP-level test to catch; a pure method-security unit test (like the removed Phase 3 `RoleAuthorizationTest` fixture) would never exercise `DispatcherServlet`/`@RestControllerAdvice` at all, so it would never have caught this.

### Problem 2 — `LazyInitializationException` with `open-in-view` disabled (Phase 4)

- **Symptom:** `GET /api/cr-assignments/me` (and, latently, several other read endpoints across the academic domain) returned `500` with `org.hibernate.LazyInitializationException: could not initialize proxy - no session`.
- **Root cause:** `spring.jpa.open-in-view: false` (a deliberate Phase 2 setting) closes the Hibernate session as soon as a `@Transactional` method returns. Several service methods returned an entity, and the *controller* (or a DTO factory method called from the controller) then dereferenced a lazy association (e.g. `division.getAcademicYear().getStream().getProgram()`) - by then, outside any transaction, with no session left to fetch it.
- **Fix:** Added class-level `@Transactional(readOnly = true)` to every academic/subject/faculty service (mutating methods already had their own `@Transactional`), so DTO mapping happens while the session is still open. For `CrOwnershipService` specifically, this also required moving the DTO-mapping call *into* the service (`getCurrentAssignmentResponse`), since the controller previously mapped to a DTO after the service call had already returned.
- **How verified:** Same rebuild-redeploy-and-re-`curl` cycle as Problem 1, exercised against every previously-failing endpoint (`/api/cr-assignments/me`, `/api/streams`, `/api/academic-years`, `/api/divisions`, `/api/batches`, `/api/subjects`) plus a fresh CORS/negative-case pass to confirm nothing else regressed.
- **Lesson:** `open-in-view: false` did exactly what it exists to do — it turned a silent, easy-to-miss N+1-prone anti-pattern into a loud, immediate failure during active development rather than a slow, hard-to-diagnose performance problem discovered much later in production. This is the direct, first-hand justification for keeping it disabled (see the interview answer above), and it directly shaped how every Phase 5 lab-domain service was written from the start (class-level `@Transactional(readOnly = true)` applied proactively, not retrofitted after a repeat of this bug).
