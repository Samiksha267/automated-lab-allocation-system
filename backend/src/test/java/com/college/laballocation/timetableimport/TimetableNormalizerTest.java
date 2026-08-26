package com.college.laballocation.timetableimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimetableNormalizerTest {

    @Test
    void recognizesFullDayNamesAndCommonAbbreviations() {
        assertThat(TimetableNormalizer.normalizeDay("Monday")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(TimetableNormalizer.normalizeDay("MON")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(TimetableNormalizer.normalizeDay(" Mon. ")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(TimetableNormalizer.normalizeDay("Tues")).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(TimetableNormalizer.normalizeDay("thursday")).isEqualTo(DayOfWeek.THURSDAY);
    }

    @Test
    void unrecognizedDayReturnsNullRatherThanGuessing() {
        assertThat(TimetableNormalizer.normalizeDay("Someday")).isNull();
        assertThat(TimetableNormalizer.normalizeDay("")).isNull();
        assertThat(TimetableNormalizer.normalizeDay(null)).isNull();
    }

    @Test
    void parses24HourTimeInBothOneAndTwoDigitHourForms() {
        assertThat(TimetableNormalizer.normalizeTime("09:00")).isEqualTo(LocalTime.of(9, 0));
        assertThat(TimetableNormalizer.normalizeTime("9:00")).isEqualTo(LocalTime.of(9, 0));
        assertThat(TimetableNormalizer.normalizeTime("23:59")).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void ambiguousOrUnsupportedTimeFormsAreNeverGuessed() {
        assertThat(TimetableNormalizer.normalizeTime("9 AM")).isNull();
        assertThat(TimetableNormalizer.normalizeTime("9-11")).isNull();
        assertThat(TimetableNormalizer.normalizeTime("9:00 AM")).isNull();
        assertThat(TimetableNormalizer.normalizeTime("25:00")).isNull();
        assertThat(TimetableNormalizer.normalizeTime("")).isNull();
    }

    @Test
    void collapsesWhitespaceAndUppercasesTokens() {
        assertThat(TimetableNormalizer.normalizeToken("  BDA   LAB ")).isEqualTo("BDA LAB");
        assertThat(TimetableNormalizer.normalizeToken("cloudera")).isEqualTo("CLOUDERA");
        assertThat(TimetableNormalizer.normalizeToken("   ")).isNull();
        assertThat(TimetableNormalizer.normalizeToken(null)).isNull();
    }
}
