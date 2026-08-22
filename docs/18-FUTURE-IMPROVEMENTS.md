# Future Improvements

Not implemented in the current scope. Recorded here so design decisions elsewhere (e.g. "why no Student entity," "why not OR-Tools now") have a clear, honest place pointing to what would change if these ever became requirements.

- **Faculty login** — would require linking `faculty` to `app_user` and adding a fourth role/permission set (e.g., faculty viewing their own schedule); deliberately out of scope now (A-08) since the spec confirms faculty has no login initially.
- **Notifications** (email/SMS/push) — for allocation approval, conflict alerts, cancellation notices.
- **Calendar integration** (Google Calendar/ICS export) for CRs and students.
- **OR-Tools / CP-SAT comparison benchmark** — a concrete side-by-side of the custom backtracking engine (ADR-008) vs a general CP solver on the same fixture data, to have real numbers behind the "when would a solver help" interview answer instead of a claim.
- **Constraint solver benchmark at larger scale** — synthetic datasets well beyond ~15 labs/tens of divisions, to find the actual point where the custom engine's exponential worst case becomes a practical problem.
- **Multiple campuses** — would require scoping labs/faculty/academic hierarchy to a campus dimension; the current schema's FK structure would extend rather than redesign for this.
- **Room scheduling beyond labs** (seminar halls, classrooms) — `lab_type` was deliberately made a configurable table rather than an enum partly so this kind of extension doesn't require a schema rework, just new `lab_type` rows and possibly a renamed umbrella concept ("Resource" instead of "Lab") if scope grows that far.
- **Mobile application** — same backend API, a native or React Native client.
- **Attendance integration** — would be the actual trigger for introducing a `Student` entity (see A-13 in [ASSUMPTIONS.md](ASSUMPTIONS.md)), since attendance is fundamentally per-student in a way capacity checking never needed to be.
- **Semester-wide timetable optimization** — jointly optimizing every subject/division/batch's schedule at once (rather than the current per-request or per-generation-run scope) is a genuinely harder global optimization problem, and is the strongest candidate for eventually justifying a real solver (OR-Tools) over the custom engine.
- **Single-date faculty/lab exception overrides** — layered on top of the current recurring-weekly `faculty_availability` and date-ranged `lab_unavailability` tables (A-15/A-16) without changing their shape.
