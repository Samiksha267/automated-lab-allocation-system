package com.college.laballocation.scheduling.alternative;

import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.conflict.ConflictAnalysis;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import java.util.List;
import java.util.Objects;

/**
 * The full, advisory result of one {@code AlternativeSuggestionService.findAlternatives(...)}
 * call - transient, never persisted (PART 2 of the Phase 13 brief, same
 * advisory boundary Phase 12's {@code AllocationRecommendation} already
 * established). {@code slotsSearched} reports exactly how many alternative
 * (day, time) combinations were actually run through the full
 * generate-then-score pipeline, for search-bound transparency (PART 28).
 */
public record AlternativeSearchResult(
        SchedulingRequest request,
        AllocationRecommendation originalRecommendation,
        ConflictAnalysis conflictAnalysis,
        List<AlternativeSuggestion> suggestions,
        AlternativeSearchStatus status,
        int slotsSearched) {

    public AlternativeSearchResult {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(originalRecommendation, "originalRecommendation must not be null");
        Objects.requireNonNull(conflictAnalysis, "conflictAnalysis must not be null");
        Objects.requireNonNull(status, "status must not be null");
        suggestions = List.copyOf(suggestions);
        if (status == AlternativeSearchStatus.ALTERNATIVES_FOUND && suggestions.isEmpty()) {
            throw new IllegalArgumentException("status ALTERNATIVES_FOUND requires at least one suggestion");
        }
        if (status != AlternativeSearchStatus.ALTERNATIVES_FOUND && !suggestions.isEmpty()) {
            throw new IllegalArgumentException("only ALTERNATIVES_FOUND may carry suggestions");
        }
        if (slotsSearched < 0) {
            throw new IllegalArgumentException("slotsSearched must be >= 0, got " + slotsSearched);
        }
    }
}
