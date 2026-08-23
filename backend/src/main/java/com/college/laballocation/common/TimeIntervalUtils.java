package com.college.laballocation.common;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Shared half-open interval {@code [start, end)} semantics
 * (docs/06-CONSTRAINTS.md) for any time-range comparison in this project.
 * Introduced for {@code FacultyAvailability} (Phase 7) and reused by the
 * Phase 9 {@code SchedulingConstraint} implementations rather than each
 * module reimplementing the overlap/containment formulas independently.
 *
 * <p>The {@link Instant} overload (Phase 9) exists specifically for
 * {@code LabAvailabilityConstraint} (HC-06), which must compare a candidate's
 * {@code Instant} range (bridged from {@code LocalDate}/{@code LocalTime} via
 * {@link com.college.laballocation.scheduling.SchedulingTimeMapper}) against
 * {@code LabUnavailability}'s native {@code Instant} range - the same
 * half-open formula, just over a different {@link Comparable} time type,
 * kept here rather than duplicated inside that one constraint.
 */
public final class TimeIntervalUtils {

    private TimeIntervalUtils() {}

    /** {@code true} iff {@code start} is strictly before {@code end} - rejects {@code start == end} and {@code start > end}. */
    public static boolean isValid(LocalTime start, LocalTime end) {
        return start != null && end != null && start.isBefore(end);
    }

    /**
     * Standard half-open overlap test: {@code startA < endB AND startB < endA}.
     * Back-to-back intervals (e.g. {@code 09:00-11:00} and {@code 11:00-13:00})
     * do not overlap - the shared boundary instant belongs only to the second
     * interval.
     */
    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /** Same half-open overlap test as {@link #overlaps(LocalTime, LocalTime, LocalTime, LocalTime)}, over {@link Instant}. */
    public static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /**
     * {@code true} iff the inner interval is fully contained within the outer
     * interval: {@code outerStart <= innerStart AND innerEnd <= outerEnd}.
     */
    public static boolean contains(LocalTime outerStart, LocalTime outerEnd, LocalTime innerStart, LocalTime innerEnd) {
        return !outerStart.isAfter(innerStart) && !innerEnd.isAfter(outerEnd);
    }
}
