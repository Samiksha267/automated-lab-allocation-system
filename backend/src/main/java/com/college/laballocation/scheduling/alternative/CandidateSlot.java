package com.college.laballocation.scheduling.alternative;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One (date, start, end) combination {@link SchedulingSlotProvider} offers
 * for evaluation - {@code dayOffset} (0 = the originally requested day) and
 * {@code minutesFromOriginalStart} are the two independent ranking
 * dimensions {@code AlternativeSuggestionService} sorts by (PART 13:
 * lexicographic, never merged into one magic number).
 */
public record CandidateSlot(LocalDate date, LocalTime startTime, LocalTime endTime, int dayOffset, int minutesFromOriginalStart) {

    public CandidateSlot {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        if (dayOffset < 0) {
            throw new IllegalArgumentException("dayOffset must be >= 0, got " + dayOffset);
        }
    }

    public AlternativeType type() {
        return dayOffset == 0 ? AlternativeType.SAME_DAY_DIFFERENT_TIME : AlternativeType.DIFFERENT_DAY;
    }
}
