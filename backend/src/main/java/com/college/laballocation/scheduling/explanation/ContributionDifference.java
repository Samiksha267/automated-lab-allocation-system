package com.college.laballocation.scheduling.explanation;

import com.college.laballocation.scheduling.scoring.ScoringFactorId;

/**
 * How much one factor differed between two {@link ExplainedValidCandidate}s
 * (PART 16/17 of the Phase 12 brief) - a structured score-difference, never
 * natural-language prose (PART 37: no LLM/NLG dependency, deterministic
 * output only). {@code difference = pointsA - pointsB}; positive means
 * candidate A scored higher on this factor.
 */
public record ContributionDifference(ScoringFactorId factor, String displayLabel, double pointsA, double pointsB, double difference) {}
