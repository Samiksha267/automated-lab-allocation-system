package com.college.laballocation.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeIntervalUtilsTest {

    private static LocalTime t(int hour) {
        return LocalTime.of(hour, 0);
    }

    @Test
    void overlappingIntervalsOverlap() {
        assertThat(TimeIntervalUtils.overlaps(t(9), t(11), t(10), t(12))).isTrue();
    }

    @Test
    void backToBackIntervalsDoNotOverlap() {
        assertThat(TimeIntervalUtils.overlaps(t(9), t(11), t(11), t(13))).isFalse();
    }

    @Test
    void identicalIntervalsContainEachOther() {
        assertThat(TimeIntervalUtils.contains(t(9), t(11), t(9), t(11))).isTrue();
    }

    @Test
    void innerIntervalFullyContained() {
        assertThat(TimeIntervalUtils.contains(t(9), t(12), t(10), t(11))).isTrue();
    }

    @Test
    void innerIntervalExtendingPastOuterIsNotContained() {
        assertThat(TimeIntervalUtils.contains(t(9), t(11), t(10), t(12))).isFalse();
    }

    @Test
    void equalStartAndEndIsInvalid() {
        assertThat(TimeIntervalUtils.isValid(t(9), t(9))).isFalse();
    }

    @Test
    void startAfterEndIsInvalid() {
        assertThat(TimeIntervalUtils.isValid(t(12), t(9))).isFalse();
    }

    @Test
    void startBeforeEndIsValid() {
        assertThat(TimeIntervalUtils.isValid(t(9), t(11))).isTrue();
    }
}
