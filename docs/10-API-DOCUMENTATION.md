# API Documentation

**Status:** Auth (`/api/auth/*`, Phase 3), the academic domain (Phase 4), the laboratory domain (Phase 5), subject requirements (`/api/subjects/{id}/requirements`, `/software-requirements`, `/equipment-requirements`, `/lab-type-requirement`, Phase 6), and faculty availability (`/api/faculty/{id}/availability*`, Phase 7) are **implemented and verified**. **Phase 8 added no new endpoints at all** — `Allocation`/`ScheduleVersion` persistence now exists (docs/04-DATABASE-DESIGN.md §7), but deliberately behind no REST surface yet: verified live that `POST /api/allocations` and `GET /api/allocations` both return `404`, confirming no allocation-creation path was accidentally exposed before the constraint engine (Phase 9) exists to validate against it. Everything below marked "planned" remains the Phase 1 contract-level plan. **No entity is exposed directly — every endpoint speaks DTOs**, never raw JPA entities.

## Conventions

- Base path: `/api`.
- Auth: `Authorization: Bearer <JWT>` on every endpoint except `/api/auth/login`.
- All error responses use the structure in [§ Error Model](#error-model).
- Roles shown are the *minimum* role able to call the endpoint; ownership checks (CR → own division) apply additionally where noted.

## Resource Groups

### `/api/auth` — **implemented**

#### `POST /api/auth/login`
- **Roles:** none (public).
- **Request:**
  ```json
  { "email": "cr@example.edu", "password": "correct-password" }
  ```
  `email` must be a valid email format; `password` 8–128 characters (Bean Validation). A validation failure returns `400 VALIDATION_ERROR`.
- **Response (200):**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { "id": 1, "email": "cr@example.edu", "displayName": "Demo CR", "role": "CR" }
  }
  ```
- **Failure (401):** always the generic shape below, regardless of whether the email doesn't exist, the password is wrong, or the account is deactivated (avoids user enumeration):
  ```json
  { "code": "INVALID_CREDENTIALS", "message": "Invalid email or password.", "details": {}, "timestamp": "..." }
  ```

#### `GET /api/auth/me`
- **Roles:** any authenticated user (`LAB_ASSISTANT`, `CR`, `STUDENT`).
- **Auth:** `Authorization: Bearer <token>` required.
- **Response (200):**
  ```json
  { "id": 1, "email": "cr@example.edu", "displayName": "Demo CR", "role": "CR" }
  ```
  Never includes `passwordHash` or any internal field — this is the same `UserSummary` DTO returned inside the login response.
- **Failure (401):** missing/invalid/expired token → `{"code":"UNAUTHORIZED","message":"Authentication is required.",...}`.

#### Planned, not yet implemented
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/auth/refresh` | — | Not planned at this time — Phase 3 deliberately kept authentication simple (no refresh tokens); a token simply expires and the user logs in again. Revisit only if session-length UX becomes a real problem. |

### Academic Hierarchy — **implemented (Phase 4)**

Actual paths differ from the Phase 1 sketch (flat, resource-per-noun, not nested under `/api/academic`) — updated here to match the real implementation, per this phase's instruction to keep docs and code in sync.

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/programs` | any authenticated | List all programs | — |
| GET | `/api/programs/{id}` | any authenticated | Get one program | `PROGRAM_NOT_FOUND` |
| POST | `/api/programs` | LAB_ASSISTANT | Create program | `VALIDATION_ERROR` (duplicate code, `durationYears <= 0`) |
| PATCH | `/api/programs/{id}` | LAB_ASSISTANT | Update name/duration/active | `PROGRAM_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/streams?programId=` | any authenticated | List streams under a program | — |
| GET / POST / PATCH | `/api/streams[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | Same CRUD shape as programs, scoped to a program | `PROGRAM_NOT_FOUND`, `STREAM_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/academic-years?streamId=` | any authenticated | List years under a stream | — |
| GET / POST / PATCH | `/api/academic-years[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | `yearNumber` must be `> 0`, unique per stream | `STREAM_NOT_FOUND`, `ACADEMIC_YEAR_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/academic-terms` | any authenticated | List all terms | — |
| POST | `/api/academic-terms` | LAB_ASSISTANT | Create term (`startDate < endDate` required) | `VALIDATION_ERROR` |
| PATCH | `/api/academic-terms/{id}/status` | LAB_ASSISTANT | Transition `UPCOMING`\|`ACTIVE`\|`CLOSED` — more than one term may be `ACTIVE` at once, by design (docs/04-DATABASE-DESIGN.md) | `ACADEMIC_TERM_NOT_FOUND` |
| GET | `/api/divisions?academicYearId=` | any authenticated | List divisions under a year | — |
| GET / POST / PATCH | `/api/divisions[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | `strength` required, `> 0` | `ACADEMIC_YEAR_NOT_FOUND`, `DIVISION_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/batches?divisionId=` | any authenticated | List batches under a division | — |
| GET / POST / PATCH | `/api/batches[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | `strength` required, `> 0`; unlimited batches per division | `DIVISION_NOT_FOUND`, `BATCH_NOT_FOUND`, `VALIDATION_ERROR` |

### Subject / Faculty — **implemented (Phase 4)**

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/subjects?academicYearId=` | any authenticated | List subjects under a year | — |
| GET / POST / PATCH | `/api/subjects[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | | `ACADEMIC_YEAR_NOT_FOUND`, `SUBJECT_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/faculty` | any authenticated | List all faculty | — |
| GET / POST / PATCH | `/api/faculty[/{id}]` | any authenticated (GET) / LAB_ASSISTANT (write) | No login/password fields — Faculty is a pure domain entity (ADR-006) | `FACULTY_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/subject-faculty-assignments/{id}` | any authenticated | Get one assignment | `SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND` |
| POST | `/api/subject-faculty-assignments` | LAB_ASSISTANT | Create - validates subject/faculty/division/term exist, and (if `batchId` supplied) that the batch actually belongs to the given division | `SUBJECT_NOT_FOUND`, `FACULTY_NOT_FOUND`, `DIVISION_NOT_FOUND`, `BATCH_NOT_FOUND`, `ACADEMIC_TERM_NOT_FOUND`, `INVALID_ACADEMIC_RELATIONSHIP` (batch/division mismatch), `AMBIGUOUS_FACULTY_ASSIGNMENT` (duplicate active assignment for the same scope) |
| DELETE | `/api/subject-faculty-assignments/{id}` | LAB_ASSISTANT | Deactivate (never physically deleted) | `SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND` |

### CR Assignment — **implemented (Phase 4)**

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/cr-assignments/me` | CR | Resolve the caller's own current division assignment — never an arbitrary `userId` lookup | `CR_ASSIGNMENT_NOT_FOUND` |
| GET | `/api/cr-assignments?divisionId=` or `?userId=` | LAB_ASSISTANT | List assignment history for a division or user | `VALIDATION_ERROR` (neither param supplied) |
| POST | `/api/cr-assignments` | LAB_ASSISTANT | Create (ends any existing active assignment for the same division/user first, preserving both as history) | `USER_NOT_FOUND`, `DIVISION_NOT_FOUND`, `ACADEMIC_TERM_NOT_FOUND`, `VALIDATION_ERROR` (target user isn't role CR), `DUPLICATE_ASSIGNMENT` (race-condition guard around the DB partial-unique indexes) |
| DELETE | `/api/cr-assignments/{id}` | LAB_ASSISTANT | End an assignment (`status -> ENDED`, row preserved) | `CR_ASSIGNMENT_NOT_FOUND` |

`GET /api/cr-assignments/me` response shape (PART 44 of the phase brief):
```json
{
  "divisionId": 12, "divisionCode": "A",
  "program": "B.Tech", "stream": "Computer Science", "year": 3,
  "academicTermId": 4, "academicTerm": "Semester 5 (2026-27)"
}
```

### Laboratory Domain — **implemented (Phase 5)**

`GET /api/labs` is a **static capability filter over fixed lab properties** — never schedule-aware availability. It must not be confused with a future `GET /api/labs/available`-style endpoint (deliberately not built yet — PART 33 of the phase brief — since that depends on subject requirements, requested time, existing allocations, and faculty, none of which exist until later phases).

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/lab-types` | any authenticated | List lab types | — |
| POST / PATCH | `/api/lab-types[/{id}]` | LAB_ASSISTANT | Create/update | `LAB_TYPE_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/software` | any authenticated | List software catalog | — |
| POST / PATCH | `/api/software[/{id}]` | LAB_ASSISTANT | Create/update — `code` normalized to uppercase server-side (PART 21) | `SOFTWARE_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/equipment` | any authenticated | List equipment catalog | — |
| POST / PATCH | `/api/equipment[/{id}]` | LAB_ASSISTANT | Create/update | `EQUIPMENT_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/labs?active=&wing=&labType=&minCapacity=&software=&software=&equipment=` | any authenticated | **Static** capability filter — `software`/`equipment` are repeatable params, **ALL**-match semantics (a lab must have every requested one, not merely one — matches HC-08's ALL-required design) | — |
| GET | `/api/labs/{id}` | any authenticated | Full lab detail incl. installed software/equipment | `LAB_NOT_FOUND` |
| POST | `/api/labs` | LAB_ASSISTANT | Create — `code` unique, `capacity > 0`, `labTypeId` must exist | `VALIDATION_ERROR` |
| PATCH | `/api/labs/{id}` | LAB_ASSISTANT | Update name/capacity/type/location/active — **`code` is not updatable** (immutable after creation, docs/15-DESIGN-DECISIONS.md) | `LAB_NOT_FOUND`, `LAB_TYPE_NOT_FOUND`, `VALIDATION_ERROR` |
| GET | `/api/labs/{labId}/software` | any authenticated | List installed software | `LAB_NOT_FOUND` |
| POST | `/api/labs/{labId}/software` | LAB_ASSISTANT | Install software (optional `installedVersion`) | `LAB_SOFTWARE_ALREADY_ASSIGNED` |
| DELETE | `/api/labs/{labId}/software/{softwareId}` | LAB_ASSISTANT | Remove | `LAB_SOFTWARE_NOT_FOUND` |
| GET | `/api/labs/{labId}/equipment` | any authenticated | List assigned equipment | `LAB_NOT_FOUND` |
| POST | `/api/labs/{labId}/equipment` | LAB_ASSISTANT | Assign (with `quantity`) | `LAB_EQUIPMENT_ALREADY_ASSIGNED` |
| PATCH | `/api/labs/{labId}/equipment/{equipmentId}` | LAB_ASSISTANT | Update quantity | `LAB_EQUIPMENT_NOT_FOUND` |
| DELETE | `/api/labs/{labId}/equipment/{equipmentId}` | LAB_ASSISTANT | Remove | `LAB_EQUIPMENT_NOT_FOUND` |
| GET | `/api/labs/{labId}/unavailability` | any authenticated | List administrative unavailability windows | `LAB_NOT_FOUND` |
| POST | `/api/labs/{labId}/unavailability` | LAB_ASSISTANT | Create (`endDateTime > startDateTime` required) | `INVALID_UNAVAILABILITY_INTERVAL` |
| DELETE | `/api/labs/{labId}/unavailability/{unavailabilityId}` | LAB_ASSISTANT | Remove (hard delete — see docs/15-DESIGN-DECISIONS.md) | `LAB_UNAVAILABILITY_NOT_FOUND` |

#### Planned, not yet implemented
| Endpoint (conceptual) | Depends on |
|---|---|
| `GET /api/labs/available?subjectId=&date=&startTime=&endTime=` | Subject requirements (implemented, Phase 6) and allocation persistence (implemented, Phase 8) are both ready; only the constraint engine itself (Phase 9) is still missing |

### Subject Requirements — **implemented (Phase 6)**

`GET .../requirements` reflects **only what the subject requires** — it never checks against any lab, and is intentionally a separate fact from Phase 5's lab capability endpoints. See docs/03-SYSTEM-ARCHITECTURE.md §14 for why the two are deliberately never combined by this phase.

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/subjects/{subjectId}/requirements` | any authenticated | Consolidated view: software, equipment (with `requiredQuantity`), required lab type, preferred lab type | `SUBJECT_NOT_FOUND` |
| POST | `/api/subjects/{subjectId}/software-requirements` | LAB_ASSISTANT | Add a required software (subject/software must exist, software must be active) | `SUBJECT_NOT_FOUND`, `SOFTWARE_NOT_FOUND`, `INACTIVE_SOFTWARE`, `SOFTWARE_REQUIREMENT_ALREADY_EXISTS` |
| DELETE | `/api/subjects/{subjectId}/software-requirements/{softwareId}` | LAB_ASSISTANT | Remove | `SUBJECT_REQUIREMENT_NOT_FOUND` |
| POST | `/api/subjects/{subjectId}/equipment-requirements` | LAB_ASSISTANT | Add required equipment with `requiredQuantity` (`> 0`) | `SUBJECT_NOT_FOUND`, `EQUIPMENT_NOT_FOUND`, `INACTIVE_EQUIPMENT`, `EQUIPMENT_REQUIREMENT_ALREADY_EXISTS`, `VALIDATION_ERROR` |
| PATCH | `/api/subjects/{subjectId}/equipment-requirements/{equipmentId}` | LAB_ASSISTANT | Update `requiredQuantity` | `SUBJECT_REQUIREMENT_NOT_FOUND`, `VALIDATION_ERROR` |
| DELETE | `/api/subjects/{subjectId}/equipment-requirements/{equipmentId}` | LAB_ASSISTANT | Remove | `SUBJECT_REQUIREMENT_NOT_FOUND` |
| PUT | `/api/subjects/{subjectId}/lab-type-requirement` | LAB_ASSISTANT | Set `requiredLabTypeId` and/or `preferredLabTypeId` (at most one may be non-null) | `SUBJECT_NOT_FOUND`, `LAB_TYPE_NOT_FOUND`, `INACTIVE_LAB_TYPE`, `INVALID_LAB_TYPE_PREFERENCE` |
| DELETE | `/api/subjects/{subjectId}/lab-type-requirement` | LAB_ASSISTANT | Clear both fields | `SUBJECT_NOT_FOUND` |

Example `GET .../requirements` response (BDA, as actually seeded and verified):
```json
{
  "subject": { "id": 1, "code": "BDA", "name": "Big Data Analytics" },
  "software": [{ "id": 1, "code": "CLOUDERA", "name": "Cloudera" }],
  "equipment": [],
  "requiredLabType": null,
  "preferredLabType": { "id": 3, "code": "DATA_ENGINEERING", "name": "Data Engineering Lab" }
}
```

### Faculty Availability — **implemented (Phase 7)**

Deliberately restricted to `LAB_ASSISTANT` for **read and write alike** — see the Access Model note in docs/03-SYSTEM-ARCHITECTURE.md §15 and docs/09-AUTHORIZATION-RBAC.md for why this is narrower than Phase 5/6's open-read convention. `/check` is an **administrative preview only** — it answers "is this faculty available right now, per stored data," never a scheduling/conflict validation (that remains Phase 9's constraint engine).

| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/faculty/{facultyId}/availability?academicTermId=&dayOfWeek=` | LAB_ASSISTANT | List availability windows, optionally filtered | `FACULTY_NOT_FOUND` |
| POST | `/api/faculty/{facultyId}/availability` | LAB_ASSISTANT | Add a window (`academicTermId`, `dayOfWeek`, `startTime`, `endTime`) | `FACULTY_NOT_FOUND`, `FACULTY_INACTIVE`, `ACADEMIC_TERM_NOT_FOUND`, `VALIDATION_ERROR` (CLOSED term), `INVALID_AVAILABILITY_INTERVAL`, `FACULTY_AVAILABILITY_OVERLAP` |
| PATCH | `/api/faculty/{facultyId}/availability/{availabilityId}` | LAB_ASSISTANT | Update `dayOfWeek`/`startTime`/`endTime` | `FACULTY_AVAILABILITY_NOT_FOUND`, `INVALID_AVAILABILITY_INTERVAL`, `FACULTY_AVAILABILITY_OVERLAP` |
| DELETE | `/api/faculty/{facultyId}/availability/{availabilityId}` | LAB_ASSISTANT | Deactivate (`active=false` — soft, see docs/15-DESIGN-DECISIONS.md ADR-033) | `FACULTY_AVAILABILITY_NOT_FOUND` |
| GET | `/api/faculty/{facultyId}/availability/check?academicTermId=&dayOfWeek=&startTime=&endTime=` | LAB_ASSISTANT | Administrative preview: is the faculty available for this exact interval, per stored data | `FACULTY_NOT_FOUND` |

Example `GET .../check` response (BDA, Monday, as actually seeded and verified):
```json
{ "facultyId": 1, "academicTermId": 1, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "11:00:00", "available": true }
```

### `/api/faculty` and `/api/subjects` — see Phase 4/6 sections above

The full CRUD surface for `/api/faculty` (list/create/update) is documented under "Subject / Faculty — implemented (Phase 4)" above; the full `/api/subjects/{id}/requirements` surface is documented under "Subject Requirements — implemented (Phase 6)" above. (An earlier draft of this document sketched a different, now-superseded shape here — e.g. `PUT .../software-requirements` — corrected to avoid contradicting the real, implemented endpoints.)

### `/api/cr-assignments`
| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| GET | `/api/cr-assignments` | LAB_ASSISTANT | List all (active + historical) | — |
| POST | `/api/cr-assignments` | LAB_ASSISTANT | Assign a CR user to a division (ends any prior active assignment for that division) | `VALIDATION_ERROR` |
| DELETE | `/api/cr-assignments/{id}` | LAB_ASSISTANT | End an assignment | `NOT_FOUND` |
| GET | `/api/cr-assignments/me` | CR | Resolve my own current division | — |

### `/api/allocations/search` — candidate generation (used by both PDF-review correction UI and CR extra-lab flow)
| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| POST | `/api/allocations/search` | LAB_ASSISTANT, CR | Given subject + target (batch/division) + date/time, return ranked valid candidates + rejected-with-reasons list | `FORBIDDEN_DIVISION_ACCESS` (CR only), `NO_VALID_ALLOCATION` |

**Request concept:** `{ subjectId, targetType, divisionId, batchId?, date, startTime, endTime }` — for CR callers, `divisionId` is cross-checked against (not sourced as authorization for) the resolved `cr_assignment`.
**Response concept:** `{ rankedCandidates: [{ labId, facultyId, score, scoreBreakdown, explanation }], rejectedCandidates: [{ labId, reasonCode, reasonMessage }] }`.

### `/api/allocations` — commit
| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| POST | `/api/allocations/extra` | CR | Commit a chosen candidate as an EXTRA allocation (server revalidates atomically) | `LAB_CONFLICT`, `FACULTY_CONFLICT`, `BATCH_CONFLICT`, `DIVISION_CONFLICT`, `FORBIDDEN_DIVISION_ACCESS` |
| DELETE | `/api/allocations/{id}` | CR (own EXTRA only), LAB_ASSISTANT (any) | Cancel (sets status, never deletes row) | `FORBIDDEN_DIVISION_ACCESS`, `INVALID_STATE_TRANSITION` |
| GET | `/api/allocations?divisionId=&status=` | LAB_ASSISTANT (any), CR (own only) | List allocations | — |

### `/api/schedules/generate` — automatic multi-session generation (Phase 14)
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/schedules/generate` | LAB_ASSISTANT | Run backtracking scheduler over a set of unscheduled session requirements | `NO_VALID_ALLOCATION` (partial-failure report) |

### `/api/schedule-versions`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/schedule-versions` | LAB_ASSISTANT | Create new draft version for a term | — |
| POST | `/api/schedule-versions/{id}/publish` | LAB_ASSISTANT | Publish (supersedes prior published version) | `INVALID_STATE_TRANSITION` |
| GET | `/api/schedule-versions/current?termId=` | all | Get the currently published version | `NOT_FOUND` |

### `/api/timetable-imports`
| Method | Path | Roles | Purpose | Key failure codes |
|---|---|---|---|---|
| POST | `/api/timetable-imports` | LAB_ASSISTANT | Upload PDF, triggers extraction pipeline | `VALIDATION_ERROR` |
| GET | `/api/timetable-imports/{id}/entries` | LAB_ASSISTANT | List extracted entries with validation status | — |
| PATCH | `/api/timetable-imports/{id}/entries/{entryId}` | LAB_ASSISTANT | Correct an entry's mapped fields, re-triggers validation | `VALIDATION_ERROR` |
| POST | `/api/timetable-imports/{id}/approve` | LAB_ASSISTANT | Approve all valid entries → materialize as REGULAR allocations | `CONFLICT` (any entry still invalid) |

### `/api/conflicts`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/api/conflicts?divisionId=` | LAB_ASSISTANT (all), CR (own) | List currently detected conflicts + suggested alternatives |

### `/api/audit-logs`
| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/api/audit-logs?resourceType=&resourceId=` | LAB_ASSISTANT | Query audit trail |
| GET | `/api/audit-logs/cr-activity?userId=` | LAB_ASSISTANT | CR-specific activity view |

### `/api/timetable` — student-facing read-only
| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/api/timetable?programId=&streamId=&yearId=&divisionId=&batchId=` | STUDENT, CR | Published timetable, filtered |

---

## Error Model

```json
{
  "code": "FACULTY_CONFLICT",
  "message": "Faculty is already assigned during the requested interval.",
  "details": { "conflictingAllocationId": 4821, "faculty": "Prof. Rao", "existingInterval": "09:00-11:00" },
  "timestamp": "2026-08-21T10:15:30Z"
}
```

`code` is machine-readable and stable (used by the frontend to render specific messaging/recovery flows); `message` is human-readable and may change wording without being a breaking change; `details` is endpoint-specific context, never a stack trace.

### Error Codes (canonical list — grows only per real implemented check)

**Implemented today** (Phase 2/3/4/5/6/7/8): `VALIDATION_ERROR`, `BAD_REQUEST`, `RESOURCE_NOT_FOUND`, `INTERNAL_ERROR` (Phase 2, `GlobalExceptionHandler`); `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `FORBIDDEN` (Phase 3); `PROGRAM_NOT_FOUND`, `STREAM_NOT_FOUND`, `ACADEMIC_YEAR_NOT_FOUND`, `ACADEMIC_TERM_NOT_FOUND`, `DIVISION_NOT_FOUND`, `BATCH_NOT_FOUND`, `SUBJECT_NOT_FOUND`, `FACULTY_NOT_FOUND`, `SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND`, `USER_NOT_FOUND`, `CR_ASSIGNMENT_NOT_FOUND`, `INVALID_ACADEMIC_RELATIONSHIP`, `AMBIGUOUS_FACULTY_ASSIGNMENT`, `DUPLICATE_ASSIGNMENT`, `FORBIDDEN_DIVISION_ACCESS` (Phase 4); `LAB_NOT_FOUND`, `LAB_TYPE_NOT_FOUND`, `SOFTWARE_NOT_FOUND`, `EQUIPMENT_NOT_FOUND`, `LAB_SOFTWARE_NOT_FOUND`, `LAB_EQUIPMENT_NOT_FOUND`, `LAB_UNAVAILABILITY_NOT_FOUND`, `LAB_SOFTWARE_ALREADY_ASSIGNED`, `LAB_EQUIPMENT_ALREADY_ASSIGNED`, `INVALID_UNAVAILABILITY_INTERVAL` (Phase 5); `SUBJECT_REQUIREMENT_NOT_FOUND`, `SOFTWARE_REQUIREMENT_ALREADY_EXISTS`, `EQUIPMENT_REQUIREMENT_ALREADY_EXISTS`, `INACTIVE_SOFTWARE`, `INACTIVE_EQUIPMENT`, `INACTIVE_LAB_TYPE`, `INVALID_LAB_TYPE_PREFERENCE` (Phase 6); `FACULTY_INACTIVE`, `INVALID_AVAILABILITY_INTERVAL`, `FACULTY_AVAILABILITY_OVERLAP`, `FACULTY_AVAILABILITY_NOT_FOUND` (Phase 7 — a CLOSED-term availability request deliberately reuses `VALIDATION_ERROR` rather than minting a dedicated code, same "keep the taxonomy manageable" reasoning as duplicate codes below). Duplicate lab/lab-type/software/equipment *codes* deliberately reuse `VALIDATION_ERROR` rather than minting per-entity duplicate-code codes (e.g. no `DUPLICATE_LAB_CODE`), consistent with the Phase 4 precedent and PART 59's "keep the taxonomy manageable" instruction — a genuinely distinct *conflict* (an already-installed software/equipment combination, an already-recorded requirement, or an overlapping availability window) gets its own code, following the `AMBIGUOUS_FACULTY_ASSIGNMENT`/`DUPLICATE_ASSIGNMENT` precedent, but a plain uniqueness violation on a create request does not need one.

**Implemented in code but not yet reachable via any HTTP endpoint** (Phase 8): `INVALID_ALLOCATION_INTERVAL`, `INVALID_ALLOCATION_TRANSITION`, `INVALID_SCHEDULE_VERSION_TRANSITION`, `SCHEDULE_VERSION_NOT_FOUND` — thrown by `Allocation`/`ScheduleVersion`'s constructors and lifecycle methods, and covered by unit tests, but since no controller exists yet for either entity (deliberately, PART 31 of the Phase 8 brief), no live request can currently trigger one. Listed here for completeness rather than silently omitted — they will become reachable once Phase 15/18/19 add real endpoints that call this code.

**Planned** (scheduling engine, Phase 9+ — not yet implemented, each maps 1:1 to a hard constraint in [06-CONSTRAINTS.md](06-CONSTRAINTS.md)): `LAB_CONFLICT`, `FACULTY_CONFLICT`, `FACULTY_UNAVAILABLE`, `BATCH_CONFLICT`, `DIVISION_CONFLICT`, `CAPACITY_VIOLATION`, `SOFTWARE_MISMATCH`, `EQUIPMENT_MISMATCH`, `LAB_TYPE_MISMATCH`, `LAB_UNAVAILABLE`, `FORBIDDEN_DIVISION_ACCESS`, `INVALID_ACADEMIC_RELATIONSHIP`, `NO_VALID_ALLOCATION`, `INVALID_STATE_TRANSITION`.
