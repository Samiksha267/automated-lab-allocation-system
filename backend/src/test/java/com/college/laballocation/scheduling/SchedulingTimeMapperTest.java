package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Uses fixed zones throughout - never relies on the test machine's local timezone (PART 46 of the Phase 8 brief). */
class SchedulingTimeMapperTest {

    @Test
    void convertsDateAndLocalTimeToExpectedInstantInUtc() {
        SchedulingTimeMapper mapper = new SchedulingTimeMapper("UTC");

        Instant result = mapper.toInstant(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0));

        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 24).atTime(9, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    void convertsDateAndLocalTimeToExpectedInstantInNonUtcZone() {
        // Asia/Kolkata is a fixed +05:30 offset (no DST) - deterministic without depending on the test runner's zone.
        SchedulingTimeMapper mapper = new SchedulingTimeMapper("Asia/Kolkata");

        Instant result = mapper.toInstant(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0));

        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 24).atTime(9, 0).toInstant(ZoneOffset.ofHoursMinutes(5, 30)));
    }

    @Test
    void convertsStartAndEndTimeIntoAnInstantRange() {
        SchedulingTimeMapper mapper = new SchedulingTimeMapper("UTC");

        InstantRange range = mapper.toInstantRange(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 8, 24).atTime(9, 0).toInstant(ZoneOffset.UTC));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 8, 24).atTime(11, 0).toInstant(ZoneOffset.UTC));
        assertThat(range.start()).isBefore(range.end());
    }
}
