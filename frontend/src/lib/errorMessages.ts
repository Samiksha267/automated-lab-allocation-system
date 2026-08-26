import { ApiError } from "../api/client";

/**
 * Maps backend error codes (docs/10-API-DOCUMENTATION.md#error-model) to
 * human-readable UI text (PART 56 of the Phase 20 brief) - the backend
 * remains authoritative for *whether* an action is allowed; this module only
 * translates *why not* into language a Lab Assistant doesn't need to know
 * this project's internal enum names to understand. Falls back to the
 * backend's own `message` for any code not explicitly mapped, so a new
 * backend error is never silently swallowed - just less prettily worded
 * until someone adds it here.
 */
const CODE_MESSAGES: Record<string, string> = {
  INVALID_SCHEDULE_VERSION_TRANSITION: "This timetable version can no longer be published or edited in its current state.",
  SCHEDULE_VERSION_NOT_DRAFT: "This action requires a draft timetable version - the target version is no longer a draft.",
  TIMETABLE_IMPORT_HAS_ERRORS: "This import still has unresolved errors and cannot be approved yet. Correct the flagged rows first.",
  TIMETABLE_IMPORT_NOT_APPROVABLE: "This import cannot be approved in its current state.",
  TIMETABLE_IMPORT_NOT_REJECTABLE: "This import cannot be rejected in its current state.",
  TIMETABLE_IMPORT_NOT_EDITABLE: "This import has already been approved or rejected and can no longer be changed.",
  TIMETABLE_IMPORT_APPROVAL_CONFLICT: "This import conflicts with an allocation created by another approval just now. Nothing was changed - please review and try again.",
  SCHEDULE_VERSION_NOT_CURRENT: "This session belongs to a timetable version that is no longer current and cannot be changed.",
  SOFTWARE_MISMATCH: "The selected lab does not have all the software this subject requires.",
  LAB_CONFLICT: "The selected lab is already booked for an overlapping time.",
  FACULTY_CONFLICT: "The selected faculty member is already assigned during this time.",
  BATCH_CONFLICT: "This batch already has an overlapping session scheduled.",
  DIVISION_CONFLICT: "This division already has an overlapping session scheduled.",
  CAPACITY_EXCEEDED: "The selected lab does not have enough capacity.",
  FACULTY_UNAVAILABLE: "The selected faculty member is not available at this time.",
  LAB_UNAVAILABLE: "The selected lab is not available at this time (maintenance or another restriction).",
  UNRESOLVED_ACADEMIC_ASSIGNMENT: "No matching subject/division/batch teaching assignment was found for this row.",
  UNKNOWN_LAB: "No lab was found with this code.",
  MALFORMED_TIME: "The time in this row could not be understood - use 24-hour HH:MM format.",
  UNSUPPORTED_PDF: "This file could not be read as a PDF, or its format is not supported.",
  FILE_TOO_LARGE: "This file is larger than the 10 MB upload limit.",
  VALIDATION_ERROR: "Please check the highlighted fields and try again.",
  RESOURCE_NOT_FOUND: "That item could not be found - it may have been removed.",
  FORBIDDEN: "You do not have permission to perform this action.",
  // Phase 21 - CR-facing scope/booking errors.
  FORBIDDEN_DIVISION_ACCESS: "You can schedule or manage extra labs only for your assigned class.",
  CR_ASSIGNMENT_NOT_FOUND: "You do not currently have an active class assignment for any term.",
  ALLOCATION_CONFLICT: "This slot is no longer available. Another booking was confirmed before yours - search again to see the latest available labs.",
  EXTRA_ALLOCATION_NOT_FOUND: "This extra practical could not be found - it may already have been cancelled.",
  EXTRA_ALLOCATION_FORBIDDEN: "Only extra practicals can be cancelled through this workflow.",
  INVALID_ALLOCATION_TRANSITION: "This extra practical can no longer be cancelled.",
  NO_PUBLISHED_SCHEDULE: "No published timetable exists yet for this term, so extra practicals cannot be scheduled.",
};

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return CODE_MESSAGES[error.code] ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

export function describeErrorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined;
}
