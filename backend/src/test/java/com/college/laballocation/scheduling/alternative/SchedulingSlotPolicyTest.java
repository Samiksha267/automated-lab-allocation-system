package com.college.laballocation.scheduling.alternative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SchedulingSlotPolicyTest {

    private SchedulingSlotPolicy policy() {
        return new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
    }

    @Test
    void parsesConfiguredValuesCorrectly() {
        SchedulingSlotPolicy policy = policy();

        assertThat(policy.dayStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(policy.dayEndTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(policy.slotStepMinutes()).isEqualTo(60);
        assertThat(policy.workingDays()).containsExactlyInAnyOrder(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        assertThat(policy.workingDays()).doesNotContain(DayOfWeek.SUNDAY);
        assertThat(policy.maxLookaheadDays()).isEqualTo(3);
        assertThat(policy.maxAlternativeTimeSlotsSearched()).isEqualTo(6);
        assertThat(policy.maxAlternativeSuggestions()).isEqualTo(3);
    }

    @Test
    void dayStartMustBeBeforeDayEnd() {
        assertThatThrownBy(() -> new SchedulingSlotPolicy("19:00", "09:00", 60, "MONDAY", 3, 6, 3, 120))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slotStepMustBePositive() {
        assertThatThrownBy(() -> new SchedulingSlotPolicy("09:00", "19:00", 0, "MONDAY", 3, 6, 3, 120))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxAlternativeTimeSlotsSearchedMustBePositive() {
        assertThatThrownBy(() -> new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY", 3, 0, 3, 120))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
