package com.college.laballocation.timetableimport;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Explicit, narrow normalization rules (PART 17) - a pure, static, JPA/Spring-free
 * utility (mirrors {@code TimeIntervalUtils}'s style) so every rule is trivially
 * unit-testable with no database or context. Every method returns {@code null}
 * on anything it cannot unambiguously interpret rather than guessing (PART 17:
 * "do not guess ambiguous times silently") - the caller turns a {@code null}
 * into a validation error, never a fabricated value.
 */
final class TimetableNormalizer {
    private TimetableNormalizer() {}

    private static final Map<String, DayOfWeek> DAY_ALIASES = Map.ofEntries(
            Map.entry("MON", DayOfWeek.MONDAY), Map.entry("MONDAY", DayOfWeek.MONDAY),
            Map.entry("TUE", DayOfWeek.TUESDAY), Map.entry("TUES", DayOfWeek.TUESDAY), Map.entry("TUESDAY", DayOfWeek.TUESDAY),
            Map.entry("WED", DayOfWeek.WEDNESDAY), Map.entry("WEDNESDAY", DayOfWeek.WEDNESDAY),
            Map.entry("THU", DayOfWeek.THURSDAY), Map.entry("THUR", DayOfWeek.THURSDAY), Map.entry("THURS", DayOfWeek.THURSDAY),
            Map.entry("THURSDAY", DayOfWeek.THURSDAY),
            Map.entry("FRI", DayOfWeek.FRIDAY), Map.entry("FRIDAY", DayOfWeek.FRIDAY),
            Map.entry("SAT", DayOfWeek.SATURDAY), Map.entry("SATURDAY", DayOfWeek.SATURDAY),
            Map.entry("SUN", DayOfWeek.SUNDAY), Map.entry("SUNDAY", DayOfWeek.SUNDAY));

    /** {@code "  Mon. "} / {@code "Monday"} / {@code "MON"} -&gt; {@code MONDAY}; anything else -&gt; {@code null}. */
    static DayOfWeek normalizeDay(String raw) {
        if (raw == null) {
            return null;
        }
        String key = collapseWhitespace(raw).toUpperCase().replace(".", "");
        return DAY_ALIASES.get(key);
    }

    // Deliberately strict (PART 17/52): only 24-hour "H:MM"/"HH:MM" is supported.
    // "9 AM", "9-11" (bare hour, no minutes), and 12-hour "9:00 AM - 11:00 AM"
    // forms are all rejected rather than guessed - a real, documented
    // limitation (docs/18-PDF-IMPORT.md), not an oversight.
    private static final Pattern TIME_24H = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");

    static LocalTime normalizeTime(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = collapseWhitespace(raw);
        if (!TIME_24H.matcher(candidate).matches()) {
            return null;
        }
        try {
            return LocalTime.parse(candidate.length() == 4 ? "0" + candidate : candidate);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** {@code "  BDA   LAB "} -&gt; {@code "BDA LAB"}, then uppercased - used for every code/name comparison (subject/lab/division/batch codes, faculty name matching). */
    static String normalizeToken(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = collapseWhitespace(raw);
        return collapsed.isEmpty() ? null : collapsed.toUpperCase();
    }

    private static String collapseWhitespace(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
