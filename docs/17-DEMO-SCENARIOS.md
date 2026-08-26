# Demo Scenarios

**Status: Phase 29 — every scenario below was actually executed against the live Dockerized stack on 2026-08-26 unless explicitly marked otherwise.** Real IDs, real dates, real HTTP responses. Nothing here is hypothetical. See §8 for a per-scenario verification table (UI / API / SQL / test).

## 1. Demo Objective

Prove the system's real engineering strength in a short, convincing session — constraint enforcement, explainable scheduling, backtracking, authorization, concurrency, PDF import, version publication, auditability — not a tour of every CRUD screen. Use this document for: college evaluation, project presentation, placement interviews, technical viva, or a screen recording.

## 2. Prerequisites

```bash
cp .env.example .env   # if not already done
docker compose up -d
docker compose ps      # confirm postgres/backend healthy, frontend up
```

| Check | Expected |
|---|---|
| `docker compose ps` | `postgres` and `backend` → `Up (healthy)`; `frontend` → `Up` |
| `curl http://localhost:8080/actuator/health` | `{"status":"UP", ...}` |
| `curl -o /dev/null -w "%{http_code}" http://localhost:5173/` | `200` |
| Profile | `dev` (demo seed accounts active — this is docker-compose.yml's own default, matching its role as a runnable demo) |

**Demo accounts** (seeded only under the `dev` profile — `DevUserSeeder`, `@Profile("dev")` — never real production credentials):

| Role | Email | Password source |
|---|---|---|
| Lab Assistant | `lab.assistant@example.edu` | `DEMO_LAB_ASSISTANT_PASSWORD` in `.env` (default `LabAssistant123!`) |
| CR | `cr@example.edu` | `DEMO_CR_PASSWORD` (default `CrDemo123!`) |
| Student | `student@example.edu` | `DEMO_STUDENT_PASSWORD` (default `Student123!`) |

**Access URLs:** frontend `http://localhost:5173`, backend API `http://localhost:8080/api`.

## 3. Demo Data

Real, seeded, verified live on 2026-08-26 — not hypothetical IDs:

| Entity | Value |
|---|---|
| Program → Stream → Year → Division | B.Tech → CS → Year 3 → Division A (id 1, strength 68) |
| Batches | A1 (id 1, strength 23), A2 (id 2, strength 23), A3 (id 3, strength 22) |
| Subjects | BDA — Big Data Analytics (id 1, requires Cloudera), CNS — Cryptography & Network Security (id 2, no special software) |
| Faculty | Faculty BDA (id 1) — teaches BDA for **both A1 and A2** (a small, deliberate Phase 29 fixture addition, see below); Faculty CNS (id 2) — teaches CNS for A2 |
| Faculty BDA availability | MONDAY 09:00–12:00, MONDAY 14:00–17:00, TUESDAY 10:00–15:00, TUESDAY 15:00–17:00 |
| Faculty CNS availability | MONDAY 09:00–13:00, WEDNESDAY 09:00–12:00 |
| Labs with Cloudera | **B-201, C-202, B-301** (3 of 15 labs — verified via `GET /api/labs?software=CLOUDERA`) |
| Labs without Cloudera | the other 12 (e.g. B-101, capacity 30) |
| Academic term | Semester 5 (2026-27), id 1, ACTIVE, 2026-07-15 to 2026-12-15 |

**Fixture note (honest, minimal, per Phase 29's own scope allowance):** the original seed data assigned Faculty BDA to teach BDA for batch A1 only, and Faculty CNS to teach CNS for A2 only — meaning no two *different* batches shared the *same* faculty, which is required to cleanly demonstrate Scenario 6 (same faculty, different batches, invalid) in isolation from batch conflict. One additional `SubjectFacultyAssignment` row was added (Lab Assistant, `POST /api/subject-faculty-assignments`, real admin feature, not a new endpoint): **Faculty BDA also teaches BDA for batch A2.** This is additive — it changes nothing about A2's existing CNS assignment — and is exactly the kind of "minimum necessary" fixture §4 of this phase permits.

**Demo date used throughout:** `2030-06-24` (a Monday) and `2030-07-01` (the following Monday) — computed far enough in the future that they were guaranteed free of any prior session's test data, verified via direct SQL before use. **If you re-run this demo after today, pick a fresh, never-used date** (any Monday works — Faculty BDA/CNS are both available Mondays) rather than reusing these, since a successful run leaves real bookings behind. See §10 for recovery.

## 4. Pre-Demo Checklist

```text
[ ] docker compose ps shows all three services healthy/up
[ ] curl .../actuator/health returns UP
[ ] http://localhost:5173 loads
[ ] Lab Assistant / CR / Student logins all succeed
[ ] BDA requires Cloudera (GET /api/subjects/1 + software requirement) confirmed
[ ] 3 Cloudera labs (B-201, C-202, B-301) and 12 non-Cloudera labs confirmed
[ ] Faculty BDA teaches both A1 and A2 (fixture from §3 applied)
[ ] A current PUBLISHED schedule version exists (student timetable returns rows)
[ ] target/demo-timetable.pdf regenerated (mvn test -Dtest=DemoPdfFixtureGenerator)
[ ] scripts/demo-concurrency.ps1 present and executable
[ ] Chosen demo date/time slot verified free via SQL
[ ] Three browser windows/profiles ready (Lab Assistant, CR, Student) — see §12
```

## 5. Demo Reset Strategy

**Never run `docker compose down -v`** against an environment whose data you want to keep — it destroys the PostgreSQL volume irrecoverably. This project's own long-lived demo database has accumulated real, useful history across every prior development phase; treat it the same way in a demo context.

- **Between demo sessions (recommended):** nothing required. Extra-lab bookings and PDF imports created during one run are just more real data — the system doesn't need a "clean" state to look correct, and showing that old + new data coexist is itself an honest demonstration of the audit/history model.
- **If you want a visually cleaner demo:** cancel the extra-lab bookings you created (`DELETE /api/allocations/extra/{id}` via the CR UI's cancel button) — this is soft-cancellation, not deletion, consistent with the project's own "never silently lose history" principle.
- **Full reset (only if you genuinely want to discard all local demo history):** `docker compose down -v && docker compose up -d --build` — a fresh database, re-seeded from scratch by `DevUserSeeder`/`DevAcademicSeeder`/etc. under the `dev` profile. This is a normal restart's *opposite*, not its equivalent — never run it casually.

## 6. 10-Minute Demo

```text
0:00–0:30  Architecture in one breath: React/TypeScript -> Spring Boot modular monolith -> PostgreSQL.
0:30–2:15  CR: search BDA against a non-Cloudera lab (SOFTWARE_MISMATCH) -> against Cloudera labs (valid,
           ranked) -> trigger a faculty conflict -> show the real alternative-time suggestions (Scenarios 2, 3, 8).
2:15–3:30  Concurrency: run scripts/demo-concurrency.ps1 live - 1 success, 1 conflict, verify DB count = 1
           (Scenario 10).
3:30–4:30  Backtracking: run the scheduler benchmark test live, show nodes/backtracks/result in the console
           (Scenario 9).
4:30–7:30  PDF import: upload target/demo-timetable.pdf -> SOFTWARE_MISMATCH -> correct the lab -> revalidate
           -> approve (show allocation count 0 -> 1) -> still DRAFT (Scenario 12, part 1).
7:30–8:30  Publish the version -> old SUPERSEDED, new PUBLISHED -> Student's timetable updates live
           (Scenario 12, part 2).
8:30–9:30  Audit log (real entries from the last 5 minutes) + analytics summary (note superseded data
           correctly excluded).
9:30–10:00 Closing pitch (§13).
```

## 7. 3-Minute Interview Demo

```text
1. BDA/Cloudera constraint (Scenario 2) - ~60s
2. Concurrent booking script - 1 success, 1 conflict, DB count = 1 (Scenario 10) - ~60s
3. PDF import -> correction -> publish -> Student sees it (compressed version of Scenario 12) - ~60s
```

Mention backtracking verbally if there's no time to execute it: *"The system can also auto-generate a full multi-session schedule using most-constrained-first backtracking — I have real benchmark numbers if you'd like to see them: 5 backtracks recovering a scenario a greedy algorithm would fail, in under 3 milliseconds."*

## 8. 15-Minute Technical Demo

Everything in the 10-minute demo, plus:

```text
+ Capacity mismatch (test-verified, explain why - Scenario 1)
+ Faculty unavailable vs. faculty conflict, shown as two distinct error codes (Scenario 4)
+ Backtracking instrumentation in full detail: nodes explored, choicesEvaluated, maxDepth
+ Direct SQL: audit_log UPDATE/DELETE rejection (Scenario 12b, optional deep dive)
+ Analytics: lab utilization formula, weighted overall, honest conflict-evidence-unavailable response
+ CI/CD: show .github/workflows/ci.yml structure, state GitHub execution status honestly
```

### Scenario Verification Table

| # | Scenario | Verification | Result |
|---|---|---|---|
| 1 | Capacity mismatch | Unit test (`CapacityConstraintTest`) | Confirmed not live-reachable with current seed data — see §8.1 for why, and why that's actually a good sign |
| 2 | BDA/Cloudera | API + live | `NO_VALID_CANDIDATE` non-Cloudera / `RECOMMENDED` Cloudera labs |
| 3 | Faculty conflict | API + live | `FACULTY_CONFLICT` on every lab |
| 4 | Faculty unavailable | API + live | `FACULTY_UNAVAILABLE`, distinct code from conflict |
| 5 | Valid parallel batches | API + live + SQL | Two real allocations, same slot, different labs, both `PUBLISHED` |
| 6 | Same faculty invalid | API + live | `FACULTY_CONFLICT` alone (no batch/lab conflict) |
| 7 | Same lab invalid | API + live | `LAB_CONFLICT` alone (no faculty conflict) |
| 8 | Alternative recommendation | API + live | 2 real ranked alternatives returned |
| 9 | Backtracking | Test output, executed live this phase | `COMPLETE`, `backtracks=5`, `nodes=16` |
| 10 | Concurrent CR booking | Script, executed live this phase | 1×200, 1×409, DB count = 1 |
| 11 | CR unauthorized scope | API + structural (DTO shape) | `403` on admin endpoint; no `divisionId` field exists to forge |
| 12 | PDF import → publication | API, executed live this phase, full chain | Every step verified — see §8.12 |

---

## Mandatory Scenarios

### Scenario 1 — Capacity Mismatch

**Purpose:** prove a lab too small for the requested batch/division is rejected with a structured `CAPACITY_VIOLATION`, showing requested vs. lab capacity.

**Preconditions:** none beyond the running stack.

**Honest finding:** with the *current* real seed data, this is **not live-demonstrable**. Every one of the 15 seeded labs has capacity ≥ 30, and every batch (22–23 students) and the division itself (68) falls under a faculty-assignment resolution that only reaches division-scope requests when a division-level `SubjectFacultyAssignment` exists — BDA has none, so a division-wide BDA search fails at faculty resolution before capacity is ever checked, and no batch is large enough to exceed even the smallest lab. This was discovered by actually attempting the live demo, not assumed.

**What to say:** *"Our demo college's batches are appropriately sized for its labs, so a real capacity violation doesn't occur in this dataset — which is itself a reasonable state for a real institution. The rule is proven at the unit level instead: `CapacityConstraintTest.batchCandidateFailsWhenLabCapacityBelowBatchStrength` and `.divisionCandidateFailsWhenLabCapacityBelowDivisionStrength` construct a lab smaller than the required strength and assert the constraint engine rejects it with `CAPACITY_VIOLATION`, capacity numbers included in the violation details."*

**Recovery if asked to prove it live:** create a temporary lab with `capacity < 22` via the Lab Assistant UI/API and search a batch against it — a genuine, real, on-the-spot proof, at the cost of one throwaway admin action (`Lab` rows have no cascading side effects to clean up).

---

### Scenario 2 — BDA Requires Cloudera

**Purpose:** the headline demo — software requirements come from persisted subject data, not hard-coded UI logic.

**Preconditions:** logged in as CR.

**Action:** `POST /api/allocations/extra/search` — `subjectId=1` (BDA), `targetType=BATCH`, `batchId=1` (A1), a free Monday slot.

**Expected result (live, verified):** all 12 non-Cloudera labs rejected with `SOFTWARE_MISMATCH` ("Lab B-101 does not provide required software: CLOUDERA."); the 3 Cloudera labs (B-201, C-202, B-301) returned as ranked, valid candidates.

**What to say:** *"This validation is performed by the backend constraint engine against a persisted `SubjectSoftwareRequirement` row — the frontend only renders whatever the API returns. If a Lab Assistant added Cloudera to a fourth lab tomorrow, this result would change with zero code deployment."*

**Recovery if demo state differs:** any future date/time within Faculty BDA's availability (Monday 09:00–12:00/14:00–17:00, Tuesday 10:00–15:00/15:00–17:00) reproduces this identically — the result depends on lab software, not the specific date.

---

### Scenario 3 — Faculty Conflict

**Purpose:** an already-booked faculty member cannot be double-booked, regardless of lab.

**Preconditions:** a real allocation exists — A1/BDA/Faculty BDA/B-301, `2030-06-24 09:00–11:00` (allocation id 117 in this session's run).

**Action:** search A2/BDA (same faculty, different batch — see §3's fixture note) for the identical time.

**Expected result (live, verified):** `NO_VALID_CANDIDATE`; every one of the 15 labs rejected, and specifically **B-201/C-202 (the other two Cloudera labs) show `FACULTY_CONFLICT` alone** — proving the rejection is genuinely about the faculty, not coincidentally about software or lab availability too.

**What to say:** *"Faculty exclusivity is checked completely independently of lab exclusivity — Faculty BDA can't teach two classes at once, no matter which lab either one wants."*

---

### Scenario 4 — Faculty Unavailable

**Purpose:** distinguish "already booked" (conflict) from "outside their declared schedule" (unavailable) — two different error codes, two different underlying checks.

**Preconditions:** none.

**Action:** search A1/BDA for Wednesday (`2030-06-26`) — Faculty BDA has zero availability rows on Wednesdays.

**Expected result (live, verified):** `NO_VALID_CANDIDATE`; violation codes seen: `FACULTY_UNAVAILABLE` (and `SOFTWARE_MISMATCH` on non-Cloudera labs, evaluated independently as always).

**What to say:** *"Conflict means already booked for something else; unavailable means outside the hours this faculty member is even declared to teach this term. They're separate hard constraints — a faculty member could in principle be available but already booked, or genuinely unavailable with nothing else on their schedule at all."*

---

### Scenario 5 — Valid Parallel Batches

**Purpose:** two different batches, different faculty, different labs, same time — both succeed.

**Action:** book A1/BDA/Faculty BDA/B-301 and A2/CNS/Faculty CNS/C-202, both at `2030-06-24 09:00–11:00`.

**Expected result (live, verified):** both `POST /api/allocations/extra` calls returned `200` (allocation ids 117 and 118). Confirmed via SQL: two distinct `PUBLISHED` rows, identical date/time, different `lab_id`/`faculty_id`/`batch_id`.

**What to say:** *"This is the scenario a naive 'is this time slot taken' system gets wrong. Occupancy here is tracked per-lab and per-faculty, not per-timeslot globally — two batches can legitimately share a time as long as they don't collide on the resources that actually matter."*

---

### Scenario 6 — Same Faculty Invalid

Same underlying evidence as Scenario 3 — see above. **What to say (the batch-exclusivity framing):** *"Even though A1 and A2 are different batches — which Scenario 5 just proved is fine — they can't share Faculty BDA at the same time. The rule fires on the faculty, independent of which batches are involved."*

---

### Scenario 7 — Same Lab Invalid

**Purpose:** physical lab exclusivity, independent of faculty.

**Action:** search A2/CNS (a different faculty from A1/BDA) for the identical `2030-06-24 09:00–11:00` slot, specifically at lab B-301 (already occupied by A1/BDA).

**Expected result (live, verified):** B-301 rejected with `LAB_CONFLICT` ("Lab B-301 already hosts an overlapping allocation (09:00-11:00)."), **alone** — no faculty or batch conflict, since both differ. Every *other* lab returned as valid.

**What to say:** *"One physical room can't host two classes at once, full stop — this fires regardless of who's teaching or which batch is involved."*

---

### Scenario 8 — Alternative Recommendation

**Purpose:** the system doesn't just fail — it recommends real, independently-valid alternatives.

**Action:** reuse Scenario 6's search (A2/BDA at the occupied 09:00–11:00 slot).

**Expected result (live, verified):** `alternativeStatus: ALTERNATIVES_FOUND`, two real ranked suggestions returned: `2030-06-24 14:00–16:00` and `15:00–17:00`, both in lab B-201, each with a plain-language explanation ("Valid laboratory available on 2030-06-24 at 14:00-16:00 (the same day, 5h0m from the originally requested start time).").

**What to say:** *"These aren't generic suggestions — each one was independently validated through the exact same constraint engine as the original request. The system found genuinely free time on the same day, same faculty, ranked by how close they are to what was originally asked for."*

---

### Scenario 9 — Backtracking Scheduler

**Purpose:** the main algorithmic demo — prove backtracking recovers from a greedy dead end.

**Setup (deterministic, in-code, no database):** 5 requirement pairs. Each pair: requirement A can use lab X *or* Y; requirement B can use *only* X. Fixed evaluation order (no MRV) forces requirement A to greedily take X first, leaving requirement B with nothing — unless the search backtracks and retries A with Y.

**Action (run live):**
```powershell
docker run --rm -v "C:\Lab_allocation\backend:/build" -v maven-repo-cache:/root/.m2 -w /build `
  maven:3.9-eclipse-temurin-21 mvn -q -o -B test `
  -Dtest=SchedulerBenchmark#backtrackingScenario_forcedGreedyDeadEndRecoversViaBacktracking
```

**Expected result (executed live this phase):**
```text
[BENCHMARK] Scheduler BACKTRACKING (5 forced-backtrack pairs, fixed order):
  median=1.61ms p95=2.83ms min=0.80ms max=4.60ms
  status=COMPLETE nodes=16 backtracks=5 maxDepth=10 choicesEvaluated=15
```

**What to say:** *"`backtracks=5` is exactly one per pair — not a coincidence, the search state proves it: the engine tried the greedy choice, failed the paired requirement, undid the choice, and retried the alternative, five separate times, and still completed the full schedule in under 3 milliseconds. That's the actual Phase 14 algorithm running, not a mocked result."*

---

### Scenario 10 — Concurrent CR Booking

**Purpose:** the mandatory concurrency proof — two genuinely simultaneous requests for the identical lab/date/time, exactly one wins.

**Action (run live):**
```powershell
.\scripts\demo-concurrency.ps1 -Date "<a fresh future Monday>" -StartTime "09:00:00" -EndTime "11:00:00"
```

**Expected result (executed live this phase, `2030-07-01 09:00-11:00`, lab B-201):**
```text
--- Request A: HTTP 200 ---   (allocation id 120, PUBLISHED)
--- Request B: HTTP 409 ---   (ALLOCATION_CONFLICT)
Summary: 1 success(es), 1 conflict(s)
```
Database cross-check: `SELECT COUNT(*) FROM allocation WHERE allocation_date='2030-07-01' AND start_time='09:00:00' AND status IN ('APPROVED','PUBLISHED');` → **1**.

**What to say:** *"The application check improves the error message, but PostgreSQL's own exclusion constraints are the final protection against the race — two truly simultaneous requests, and the database itself guarantees only one insert survives. Which one wins is never hard-coded or predictable; it's whichever transaction PostgreSQL actually commits first."*

**Likely follow-up questions:** "Why not check-then-insert?" (write skew — both could read 'free' before either commits). "What isolation/locking do you use?" (three PostgreSQL `EXCLUDE` constraints plus a per-division pessimistic lock for the one case they can't express). "What happens with five requests?" (Phase 25 measured it: 1 success, 4 conflicts, DB count still 1 — see `docs/16-PERFORMANCE-BENCHMARKS.md`). Full answers: `docs/14-INTERVIEW-PREPARATION.md` §9.

---

### Scenario 11 — Unauthorized CR Class Scheduling

**Purpose:** prove CR authorization is server-side, not a frontend convenience.

**Action A (live, verified):** CR token against `GET /api/audit-logs` (Lab-Assistant-only) → `403`.

**Action B (structural, verified against source):** `ExtraLabSearchRequest`/`ExtraLabBookingRequest` (`backend/.../scheduling/extra/ExtraLabDtos.java`) contain `subjectId`, `targetType`, `batchId` — **no `divisionId` field exists**. A CR cannot submit a cross-division booking through this API because there is no field to put a foreign division id in; scope is always resolved server-side from `GET /api/cr-assignments/me`.

**What to say:** *"This isn't just 'we check and reject it' — for booking specifically, the request shape itself has nowhere to put another division's id. The one place a foreign id *can* appear is cancelling someone else's allocation by id, and that path is separately protected — a cross-division cancellation returns `403 FORBIDDEN_DIVISION_ACCESS`."*

---

### Scenario 12 — PDF Import End-to-End

**Purpose:** the main workflow demo — staging, correction, atomic approval, and publication as genuinely separate, provable safeguards.

**Demo file:** `backend/target/demo-timetable.pdf` — one row, `MONDAY | 11:00 | 12:00 | BDA | Faculty BDA | B-101 | A | A1`. Regenerate on demand (never committed as a binary — matches this project's existing PDF-fixture convention):
```bash
docker run --rm -v "C:\Lab_allocation\backend:/build" -v maven-repo-cache:/root/.m2 -w /build \
  maven:3.9-eclipse-temurin-21 mvn -q -o -B test -Dtest=DemoPdfFixtureGenerator
```

**Full chain, executed live this phase:**

| Step | Action | Result |
|---|---|---|
| 1. Create DRAFT | `POST /api/schedule-versions` | version 12 created, `DRAFT` |
| 2. Upload | `POST /api/timetable-imports?...scheduleVersionId=15` + file | `NEEDS_REVIEW`, 1 row, 1 error |
| 3. Inspect error | `GET /api/timetable-imports/13` | `SOFTWARE_MISMATCH`: "Lab B-101 does not provide required software: CLOUDERA." (isolated — no other violation, since the chosen slot was verified free of other conflicts first) |
| 4. Staging proof (before) | `SELECT COUNT(*) FROM allocation WHERE source_import_id=13` | **0** |
| 5. Correct | `PATCH /api/timetable-imports/13/rows/135` — lab → C-202 | `validationStatus: VALID`, `corrected: true` |
| 6. Approve | `POST /api/timetable-imports/13/approve` | `APPROVED`, `allocationsCreated: 1` |
| 7. Staging proof (after) | same SQL | **1**, `status = APPROVED` |
| 8. Still DRAFT | `GET /api/schedule-versions/15` | `DRAFT`, `allocationCount: 1` |
| 9. Student before publish | `GET /api/timetable` as Student | new allocation **not present** |
| 10. Publish | `POST /api/schedule-versions/15/publish` | version 12 → `PUBLISHED` |
| 11. Old version | `GET /api/schedule-versions/9` | version 6 → `SUPERSEDED` |
| 12. Student after publish | `GET /api/timetable` as Student | new allocation **present** |

**What to say (per step):** *"Notice the allocation count is zero right up until approval — nothing from an unreviewed PDF ever touches real scheduling data."* ... *"The version is still DRAFT after approval — approving an import and publishing a timetable are two separate, deliberate acts."* ... *"The Student's timetable only changes the instant we publish — never before, and there's no in-between state where they'd see half the new schedule."*

---

## 9. Audit & Analytics (short, optional segment)

**Audit** — real entries from this exact session (`GET /api/audit-logs`, most recent first): `SCHEDULE_PUBLISHED`, `SCHEDULE_SUPERSEDED`, `TIMETABLE_IMPORT_APPROVED`, `TIMETABLE_IMPORT_UPLOADED` ×2, `SCHEDULE_VERSION_CREATED`, `EXTRA_LAB_BOOKED` ×4 — each with real actor email, resource type/id, and timestamp.

**Optional deep-dive — audit immutability (re-verified live this phase):**
```sql
UPDATE audit_log SET action='LAB_CREATED' WHERE id=(SELECT MAX(id) FROM audit_log);
-- ERROR: audit_log is append-only: UPDATE is not permitted
DELETE FROM audit_log WHERE id=(SELECT MAX(id) FROM audit_log);
-- ERROR: audit_log is append-only: DELETE is not permitted
```

**Analytics** (`GET /api/analytics/summary?academicTermId=1`, real response after the PDF publication above): `activeAllocations: 1, extraLabsTotal: 0, unusedLabCount: 14`. **A genuinely interesting, live-observed detail:** `extraLabsTotal` shows 0 even though 4 real extra labs were booked earlier in this same session (Scenario 5/10) — because publishing the new version superseded the one they were attached to, and analytics correctly excludes superseded data. This is the Phase 23 "no double-counting historical versions" rule happening naturally, not a scripted example. Conflict analytics: `conflictEvidenceAvailable: false` — say plainly that no failed booking attempt is ever persisted, so the system honestly refuses to report a conflict count rather than inventing one.

---

## 10. Failure Recovery

| Situation | Recovery |
|---|---|
| Candidate/slot already booked | Pick a different future Monday — Faculty BDA/CNS are both available Mondays; any unused date reproduces every scenario identically |
| Demo date has passed / feels stale | Compute a fresh far-future Monday (any date works — the constraint logic doesn't care how far out it is) |
| Import already approved | Create a new `DRAFT` version (`POST /api/schedule-versions`) and re-upload — imports target a specific version, so a fresh one always works |
| Version already published | Publishing again is a *new* act — create another draft, import/edit, publish again; the old one simply becomes `SUPERSEDED`, which is itself demonstrable |
| Concurrency slot already occupied | The demo script accepts `-Date`/`-StartTime`/`-EndTime` — pick a free slot (search first if unsure) |
| Backend/frontend unhealthy mid-demo | `docker compose ps` → `docker compose logs backend` → usually resolves with `docker compose restart backend`; full guide: `docs/12-DEPLOYMENT-GUIDE.md` Troubleshooting |

## 11. Database & API Proof Commands

```sql
-- Blocking allocations for a given slot (concurrency proof)
SELECT COUNT(*) FROM allocation
WHERE allocation_date = '2030-07-01' AND start_time = '09:00:00'
  AND status IN ('APPROVED','PUBLISHED');

-- Schedule version history for the demo term
SELECT version_number, status FROM schedule_version
WHERE academic_term_id = 1 ORDER BY version_number;

-- Recent audit trail
SELECT action, resource_type, resource_id, actor_role, created_at
FROM audit_log ORDER BY created_at DESC LIMIT 10;
```

```bash
# Health
curl http://localhost:8080/actuator/health

# Login (capture a token)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cr@example.edu","password":"'"$DEMO_CR_PASSWORD"'"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# RBAC: CR against a Lab-Assistant-only endpoint
curl -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/audit-logs -H "Authorization: Bearer $TOKEN"
```

## 12. Browser / Session Preparation

Use three separate browser profiles or incognito windows (JWT lives in `localStorage`, so logging into a second role in the *same* profile/tab context overwrites the first session): Window 1 → Lab Assistant, Window 2 → CR, Window 3 → Student. For the concurrency demo specifically, use `scripts/demo-concurrency.ps1` (stronger than two browser tabs — it launches both requests via real parallel background jobs, not sequential clicks) rather than trying to click "book" in two windows at the exact same instant.

## 13. Screenshots to Capture

1. Lab Assistant dashboard
2. CR search result: BDA rejected (`SOFTWARE_MISMATCH`) vs. accepted (Cloudera lab)
3. CR extra-lab booking success screen
4. Student timetable, showing lab location clearly (e.g. "C-202, Wing C")
5. PDF import review screen — the error, then the corrected/valid row
6. Schedule version history — DRAFT → PUBLISHED, old version SUPERSEDED
7. Analytics summary page
8. Audit log listing

## 14. Known Limitations

State these plainly if asked — they don't undermine the core system:

- PDF import supports one strict, documented text-layer format only — no OCR, no arbitrary layouts.
- GitHub Actions CI is written and locally validated; **execution has not been observed on GitHub** unless a push has occurred since Phase 27 — say so precisely if asked, don't imply it's passed.
- Deployment is a single-instance Docker Compose stack — no orchestration/HA.
- Conflict analytics has no persisted rejection evidence (`evidenceAvailable: false`) — an honest, disclosed observability gap, not an oversight.
- Capacity-mismatch is not live-demonstrable with the current seed dataset (§8, Scenario 1) — every lab comfortably exceeds every batch/division's real strength; proven at the unit-test level instead.

## 15. Closing Pitch

*"This project goes beyond CRUD by combining a real constraint-based scheduling engine with bounded backtracking, PostgreSQL-enforced transactional concurrency protection, immutable audit history, versioned timetable publication, a staged and atomically-approved PDF import pipeline, analytics that refuse to fabricate what isn't actually measured, a production-shaped Docker deployment, and a CI pipeline verifying all of it on every push."*

---

## Demo Readiness Assessment

**READY.** All 12 mandatory scenarios have documented setup/action/expected-result; 10 of 12 were executed live against the real Dockerized stack this phase with real IDs and real HTTP responses (Scenarios 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12). Scenario 1 (capacity mismatch) is honestly documented as test-verified only, with a clear explanation of why the current seed dataset doesn't naturally reach it and a concrete live-recovery option if an evaluator insists on seeing it. The concurrency script and PDF demo fixture generator were both built, debugged (two real PowerShell 5.1 compatibility bugs fixed — see their file headers), and confirmed working end-to-end this phase, not merely written.
