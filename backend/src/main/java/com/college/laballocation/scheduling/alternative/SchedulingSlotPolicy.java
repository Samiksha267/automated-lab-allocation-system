package com.college.laballocation.scheduling.alternative;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The authoritative college scheduling-slot rules alternative-time search
 * is bounded by (PART 75/76 of the Phase 13 brief) - collected directly
 * from the user rather than assumed, since no such rules existed anywhere
 * in this repository before this phase (docs/ASSUMPTIONS.md A-35).
 * Centralized here, exactly once, following this project's existing
 * constructor-{@code @Value} convention ({@code ScoringConfiguration},
 * {@code SchedulingTimeMapper}) rather than {@code @ConfigurationProperties}.
 *
 * <p><b>The actual rules (as given):</b> sessions run in fixed 2-hour
 * blocks; a session may start at any whole hour from {@code dayStartTime}
 * (09:00) through the latest hour that still ends by {@code dayEndTime}
 * (19:00) - so valid start hours are 09, 10, 11, ..., 17. Working days are
 * Monday through Saturday (Sunday excluded). Alternative search stays
 * same-day first, then looks ahead up to {@code maxLookaheadDays}
 * additional working days if the requested day alone yields nothing -
 * {@code maxLookaheadDays} itself was not given an exact number by the
 * user (only "up to N, a small number like 2-3"), so this defaults to 3,
 * documented explicitly as this project's own reasonable choice within
 * the user's stated bound, not a guess about college policy.
 */
@Component
public class SchedulingSlotPolicy {

    private final LocalTime dayStartTime;
    private final LocalTime dayEndTime;
    private final int slotStepMinutes;
    private final Set<DayOfWeek> workingDays;
    private final int maxLookaheadDays;
    private final int maxAlternativeTimeSlotsSearched;
    private final int maxAlternativeSuggestions;
    private final int sessionDurationMinutes;

    public SchedulingSlotPolicy(
            @Value("${app.scheduling.day-start-time}") String dayStartTime,
            @Value("${app.scheduling.day-end-time}") String dayEndTime,
            @Value("${app.scheduling.slot-step-minutes}") int slotStepMinutes,
            @Value("${app.scheduling.working-days}") String workingDays,
            @Value("${app.scheduling.max-lookahead-days}") int maxLookaheadDays,
            @Value("${app.scheduling.max-alternative-time-slots-searched}") int maxAlternativeTimeSlotsSearched,
            @Value("${app.scheduling.max-alternative-suggestions}") int maxAlternativeSuggestions,
            @Value("${app.scheduling.session-duration-minutes}") int sessionDurationMinutes) {
        this.dayStartTime = LocalTime.parse(dayStartTime);
        this.dayEndTime = LocalTime.parse(dayEndTime);
        this.slotStepMinutes = requirePositive(slotStepMinutes, "slotStepMinutes");
        this.workingDays = Arrays.stream(workingDays.split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        this.maxLookaheadDays = requireNonNegative(maxLookaheadDays, "maxLookaheadDays");
        this.maxAlternativeTimeSlotsSearched = requirePositive(maxAlternativeTimeSlotsSearched, "maxAlternativeTimeSlotsSearched");
        this.maxAlternativeSuggestions = requirePositive(maxAlternativeSuggestions, "maxAlternativeSuggestions");
        this.sessionDurationMinutes = requirePositive(sessionDurationMinutes, "sessionDurationMinutes");
        if (!this.dayStartTime.isBefore(this.dayEndTime)) {
            throw new IllegalArgumentException("dayStartTime must be before dayEndTime");
        }
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + value);
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
        return value;
    }

    public LocalTime dayStartTime() {
        return dayStartTime;
    }

    public LocalTime dayEndTime() {
        return dayEndTime;
    }

    public int slotStepMinutes() {
        return slotStepMinutes;
    }

    public Set<DayOfWeek> workingDays() {
        return workingDays;
    }

    public int maxLookaheadDays() {
        return maxLookaheadDays;
    }

    public int maxAlternativeTimeSlotsSearched() {
        return maxAlternativeTimeSlotsSearched;
    }

    public int maxAlternativeSuggestions() {
        return maxAlternativeSuggestions;
    }

    /**
     * The confirmed fixed session duration (2 hours) as an explicit,
     * addressable value - Phase 13 derived duration from each individual
     * request's own start/end instead, since it always had one to preserve;
     * Phase 14's automatic scheduling has no such request up front and
     * needs this policy to state the duration directly (PART 11 of the
     * Phase 14 brief - reusing this same policy, not a second duration
     * source).
     */
    public Duration sessionDuration() {
        return Duration.ofMinutes(sessionDurationMinutes);
    }
}
