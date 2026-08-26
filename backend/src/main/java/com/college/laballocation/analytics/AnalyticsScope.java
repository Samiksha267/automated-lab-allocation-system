package com.college.laballocation.analytics;

import com.college.laballocation.academic.AcademicTerm;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The resolved scope every analytics query operates within: the term, its date range (explicit or
 * term-derived), and its current PUBLISHED schedule version, if one exists. {@code publishedVersionId}
 * is empty - not zero, not a fabricated "no data" row set - when the term has never been published,
 * exactly mirroring {@code LabUtilizationService}'s existing "no basis to compare" distinction
 * (Phase 11) rather than reinventing it for analytics.
 */
public record AnalyticsScope(AcademicTerm term, LocalDate from, LocalDate to, Optional<Long> publishedVersionId) {}
