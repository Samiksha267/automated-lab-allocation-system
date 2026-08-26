package com.college.laballocation.analytics;

import com.college.laballocation.analytics.AnalyticsDtos.ConflictAnalyticsResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Honest, deliberately empty conflict analytics (PART 32/33/34/35, mandatory). This system detects
 * conflicts extensively at request time (the whole Phase 9-14 constraint engine, plus every
 * {@code 409 ALLOCATION_CONFLICT}/{@code LAB_CONFLICT}/{@code FACULTY_CONFLICT}/etc. a rejected
 * search or booking attempt returns), but a rejection is never written anywhere - no
 * {@code Conflict} table, no failure-shaped {@code AuditAction} (the enum has only
 * {@code EXTRA_LAB_BOOKED}/{@code EXTRA_LAB_CANCELLED}, both successes), no persisted trace at all.
 * Search-time candidate rejections ({@code RejectedCandidateExplanation}, Phase 12) are constructed,
 * shown to the caller, and discarded in the same request - genuinely ephemeral, not merely
 * unindexed.
 *
 * <p>The only persisted evidence anywhere in the schema that resembles a "conflict" is
 * {@code TimetableImportRow} validation errors (Phase 19) - deliberately <b>not</b> folded in here
 * (PART 38): those are PDF-import data-quality issues (a row's subject code didn't resolve, a lab
 * code was unknown), not live scheduling conflicts between real competing bookings, and conflating
 * the two under one vague "conflicts" number would misrepresent both.
 *
 * <p>Consequently this endpoint always returns {@code evidenceAvailable: false} and an empty
 * category list - never an invented, estimated, or partial conflict count. See ADR,
 * docs/15-DESIGN-DECISIONS.md.
 */
@Service
public class ConflictAnalyticsService {

    static final String EXPLANATION =
            "No historical conflict data is available. This system detects conflicts (lab/faculty/batch/division/"
                    + "capacity/software/equipment/lab-type/lab-unavailable/faculty-unavailable) at request time - during "
                    + "search and booking - but never persists a rejected attempt: a failed booking (409) writes no row, "
                    + "and search-time candidate rejections are computed and returned to the caller without being stored. "
                    + "Only successful state changes are recorded in this system's audit log. PDF timetable-import "
                    + "validation errors are a separate, import-quality concern and are not counted as scheduling conflicts here.";

    public ConflictAnalyticsResponse conflicts(AnalyticsScope scope) {
        return new ConflictAnalyticsResponse(scope.term().getId(), false, List.of(), EXPLANATION);
    }
}
