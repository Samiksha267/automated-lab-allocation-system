package com.college.laballocation.timetableimport;

/**
 * A synchronous, small-batch lifecycle (Phase 19) - upload, extraction, parsing,
 * normalization, mapping, and validation all happen inside one request (no
 * background job queue exists in this project), so this enum represents the
 * import's state <em>after</em> that pipeline runs, not each intermediate step:
 *
 * <ul>
 *   <li>{@link #FAILED} - the pipeline could not produce any usable rows at all
 *       (unreadable/empty PDF, unrecognized layout) - nothing to review.
 *   <li>{@link #NEEDS_REVIEW} - rows exist but at least one carries an
 *       {@code ERROR}-severity validation result - cannot be approved yet.
 *   <li>{@link #VALIDATED} - every row is {@code VALID} or {@code WARNING} -
 *       ready for approval.
 *   <li>{@link #APPROVED} - {@code Allocation} rows have been created; staging
 *       rows are now permanently read-only history (PART 49).
 *   <li>{@link #REJECTED} - a Lab Assistant explicitly discarded the import;
 *       never creates allocations, never re-approvable (PART 42).
 * </ul>
 *
 * <p>{@code UPLOADED} exists as a value for API/documentation completeness
 * (PART 7's suggested lifecycle) but is never the <em>final</em> status of a
 * persisted row - every upload request resolves synchronously to one of the
 * five statuses above before the response is returned.
 */
public enum TimetableImportStatus {
    UPLOADED,
    NEEDS_REVIEW,
    VALIDATED,
    APPROVED,
    REJECTED,
    FAILED
}
