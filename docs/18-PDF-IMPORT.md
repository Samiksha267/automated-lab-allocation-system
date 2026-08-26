# PDF Timetable Import (Phase 19)

Turns an institutional timetable PDF into confirmed `Allocation` records, through a staging area a Lab Assistant reviews, corrects, and explicitly approves. See docs/15-DESIGN-DECISIONS.md ("Phase 19") for the reasoning behind every decision below; this document is the reference for the format, flow, and limitations themselves.

## Trust boundary

```text
PDF upload
    v
UNTRUSTED (extraction / parsing / normalization / mapping / validation - all staging)
    v
review / correction (Lab Assistant, still staging)
    v
APPROVAL - the only moment staging data becomes trusted
    v
Allocation (APPROVED, inside a DRAFT ScheduleVersion)
    v
Phase 18 publication (separate, explicit, unmodified) - the only moment it becomes visible/current
```

Nothing upstream of approval ever calls `AllocationRepository.save(...)`. A rejected or still-`NEEDS_REVIEW` import creates zero allocations, permanently.

## Supported PDF format

**This project intentionally does not attempt to parse arbitrary institutional timetable layouts.** The only supported input is a text-based (not scanned/image) PDF containing one line per timetable session, in exactly this pipe-delimited shape:

```text
DAY | START | END | SUBJECT_CODE | FACULTY_NAME | LAB_CODE | DIVISION_CODE | BATCH_CODE
```

- `DAY` - a recognized day name or common abbreviation (`MONDAY`, `MON`, `Mon.`, etc. - see the full alias list in `TimetableNormalizer`). Case-insensitive.
- `START`/`END` - strict 24-hour `H:MM` or `HH:MM` (e.g. `9:00`, `09:00`). **AM/PM and bare-hour ("9-11") formats are not supported** and are rejected as `MALFORMED_TIME`, never guessed.
- `SUBJECT_CODE` - matched against this term's `SubjectFacultyAssignment` table by exact code (case-insensitive).
- `FACULTY_NAME` - informational; the actual faculty is *resolved from* the subject+division+batch assignment, not matched independently. A mismatch produces a non-blocking warning, not an error.
- `LAB_CODE` - matched against `Lab.code` by exact match (case-insensitive).
- `DIVISION_CODE` - matched as part of the same assignment lookup as subject.
- `BATCH_CODE` - may be blank (a trailing empty field) for a division-wide session.

Example line:

```text
MONDAY | 09:00 | 11:00 | BDA | Dr. S. Sharma | B-204 | A | A1
```

A line that does not split into exactly 8 pipe-delimited fields is silently skipped (treated as a title/header/footer line), never a per-row error. If **zero** lines match this shape, the whole import resolves to `FAILED: NO_TIMETABLE_ROWS_FOUND`.

### Explicitly not supported

- Scanned/image-only PDFs (no OCR is performed or planned for this phase - such a PDF extracts to no text and fails cleanly).
- Multi-row-per-cell / merged-cell table layouts, or any layout not already in the pipe-delimited line format above.
- 12-hour (AM/PM) or bare-hour time formats.
- Encrypted/password-protected PDFs (rejected as `UNSUPPORTED_PDF`).
- Whole-term recurring expansion - each row produces exactly **one** `Allocation`, dated to the term's first occurrence of that weekday (see ADR-099). A real weekly-recurring schedule currently needs one row per distinct occurrence you want represented, or is accepted as a "reference week" import.

## Upload flow

```http
POST /api/timetable-imports?academicTermId={id}&scheduleVersionId={id}
Content-Type: multipart/form-data
file: <the PDF>
```

`LAB_ASSISTANT` only. The target `scheduleVersionId` must belong to `academicTermId` and must currently be `DRAFT` - importing into a `PUBLISHED`/`SUPERSEDED` version is rejected (`409 SCHEDULE_VERSION_NOT_DRAFT`).

Defenses applied before any PDF library code runs:

- Non-empty file required.
- Maximum size 10 MB (enforced twice: Spring's own `multipart.max-file-size`, and this project's own explicit check - defense in depth).
- File signature checked (`%PDF-` magic bytes) - filename extension and `Content-Type` header are never trusted alone.
- The client-supplied filename is never used as a filesystem path; the file is processed entirely in memory and identified only by its own computed SHA-256 hash.

The full pipeline (extract -> parse -> normalize -> map -> validate) runs synchronously within this one request (no background job queue exists in this project) and the response reflects the final resolved status immediately.

## Pipeline stages

1. **Extraction** (`PdfTextExtractor`, Apache PDFBox 3.0.3) - opens the PDF, reads text via `PDFTextStripper`, returns plain non-blank lines. Never resolves any academic identity.
2. **Parsing** (`TimetableParser`) - splits each line into the 8 documented raw columns. Pure, JPA/Spring-free.
3. **Normalization** (`TimetableNormalizer`) - day-name aliasing, strict 24-hour time parsing, whitespace/case collapsing for code/name comparisons. Returns `null` on anything ambiguous - never a guess.
4. **Mapping** (`TimetableMappingService`) - resolves normalized subject/division/batch (+ derived faculty) via `SubjectFacultyAssignment`, and lab via `Lab.code`. Never creates a new academic entity for an unknown value.
5. **Validation** (`TimetableImportValidationService`) - runs the *unmodified* Phase 9-14 constraint engine (capacity, required software/equipment/lab-type, faculty/lab availability, lab/faculty/batch/division conflicts against persisted allocations) per row, plus an import-local cross-row conflict pass.

Every row ends up `VALID`, `WARNING`, or `ERROR`; the import as a whole ends up `VALIDATED` (no `ERROR` rows) or `NEEDS_REVIEW` (at least one).

## Review, correction, approval

- `GET /api/timetable-imports` / `GET /api/timetable-imports/{id}` - list/detail, paginated rows, summary counts (`totalRows`/`validRows`/`warningRows`/`errorRows`/`correctedRows`).
- `PATCH /api/timetable-imports/{id}/rows/{rowId}` - a structured correction (already-resolved `subjectId`/`facultyId`/`labId`/`divisionId`/`batchId`/`day`/`startTime`/`endTime`, the same shape a review form would submit - not raw PDF text). Triggers a full revalidation of the whole import (every row, including cross-row conflicts), never leaving a stale result.
- `POST /api/timetable-imports/{id}/approve` - the trust-boundary transition. Locks the import, re-validates every row against **current** live state (never trusting review-time results), and only then persists real `Allocation` rows (status `APPROVED`, inside the target `DRAFT` version), atomically. Any remaining `ERROR` row, or a concurrency conflict with another approval, rolls back the entire attempt - zero allocations from a failed approval.
- `POST /api/timetable-imports/{id}/reject` - discards a reviewable import permanently (never re-approvable); it remains as history, never deleted.
- Once `APPROVED`/`REJECTED`, an import (and its rows) become permanently read-only.

## Publication (unchanged from Phase 18)

Approval never publishes anything. The imported allocations sit as `APPROVED` inside the `DRAFT` version until the Lab Assistant explicitly calls `POST /api/schedule-versions/{id}/publish` (Phase 18, completely unmodified) - at which point they, and every other `APPROVED` allocation in that version, transition to `PUBLISHED` and become visible via `GET /api/timetable`.

## The BDA/Cloudera failure-and-correction demo

A representative, live-verified scenario (docs/11-TESTING-STRATEGY.md has the full transcript):

1. Upload a PDF row for subject `BDA` (requires software `CLOUDERA`) targeting a lab that does not have it -> extraction/parsing/mapping all succeed, but validation fails with an explainable `SOFTWARE_MISMATCH` error naming the exact missing software.
2. Correct the row's `labId` to a lab that does provide `CLOUDERA`.
3. The row revalidates to `VALID`; the import becomes `VALIDATED`.
4. Approve - the allocation is created.

## Known limitations

- No OCR - scanned/image PDFs are not supported.
- One documented pipe-delimited line format only - no generic table-layout reconstruction.
- One `Allocation` per row (term's first matching weekday), not whole-term recurring expansion.
- Duplicate-file detection computes and stores a SHA-256 hash but does not yet surface a "this was already uploaded" warning in the API response (ADR-107) - re-upload is currently silently allowed as a separate import.
- Row corrections are not individually audited (only upload/approve/reject are, ADR-106).
