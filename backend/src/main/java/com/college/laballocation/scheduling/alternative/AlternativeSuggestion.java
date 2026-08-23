package com.college.laballocation.scheduling.alternative;

import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One alternative-time suggestion - a real, independently-validated
 * {@link ExplainedValidCandidate} (the exact Phase 12 object, carrying its
 * full Phase 11 score breakdown and constraint-check summary; PART 44/47:
 * no separate lab-quality ranking is invented here) for a different slot
 * than the one originally requested. {@code explanation} is factual and
 * derived only from the fields already on this record - never invented
 * prose, and never a claim like "avoids LAB_CONFLICT" that would require
 * knowing which specific violation this specific lab/time would otherwise
 * have hit (PART 48 - {@code SchedulingRequest} has no "preferred lab"
 * concept, so that framing is never available to construct honestly).
 */
public record AlternativeSuggestion(
        AlternativeType type,
        SchedulingRequest alternativeRequest,
        ExplainedValidCandidate recommendedCandidate,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int dayOffset,
        int minutesFromOriginalStart,
        String explanation) {

    public AlternativeSuggestion {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(alternativeRequest, "alternativeRequest must not be null");
        Objects.requireNonNull(recommendedCandidate, "recommendedCandidate must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
    }

    public static AlternativeSuggestion of(CandidateSlot slot, SchedulingRequest alternativeRequest, ExplainedValidCandidate recommendedCandidate) {
        String dayPhrase = slot.dayOffset() == 0 ? "the same day" : slot.dayOffset() == 1 ? "1 day later" : slot.dayOffset() + " days later";
        String explanation = "Valid laboratory available on " + slot.date() + " at " + slot.startTime() + "-" + slot.endTime()
                + " (" + dayPhrase + ", " + (slot.minutesFromOriginalStart() / 60) + "h" + (slot.minutesFromOriginalStart() % 60)
                + "m from the originally requested start time).";
        return new AlternativeSuggestion(
                slot.type(), alternativeRequest, recommendedCandidate, slot.date(), slot.startTime(), slot.endTime(),
                slot.dayOffset(), slot.minutesFromOriginalStart(), explanation);
    }
}
