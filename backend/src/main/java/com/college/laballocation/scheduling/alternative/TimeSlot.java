package com.college.laballocation.scheduling.alternative;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One raw (date, start, end) slot from {@link SchedulingSlotPolicy}'s
 * universe - unlike {@link CandidateSlot} (Phase 13, relative to one
 * original request's displacement), a {@code TimeSlot} carries no notion of
 * "how far from an original time" - it is used by Phase 14's automatic
 * scheduling, which has no single original request to be relative to.
 */
public record TimeSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {

    public TimeSlot {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
    }
}
