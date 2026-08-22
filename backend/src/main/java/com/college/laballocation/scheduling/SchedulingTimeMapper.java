package com.college.laballocation.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single, central bridge between {@code Allocation}'s local
 * {@code LocalDate}/{@code LocalTime} fields and {@code LabUnavailability}'s
 * {@code TIMESTAMPTZ}/{@code Instant} range (PART 12 of the Phase 8 brief) -
 * every future constraint that needs to compare the two (HC-06, Phase 9)
 * calls this rather than each independently deciding how to convert.
 *
 * <p>Uses a real, configurable {@link ZoneId} ({@code app.college.time-zone},
 * env {@code COLLEGE_TIME_ZONE}) rather than a hardcoded manual offset (e.g.
 * "UTC+5:30") specifically so DST rules, if the deployment zone ever has
 * any, are handled correctly by the JDK's own zone rules rather than by
 * hand-rolled arithmetic that would silently be wrong the day a rule
 * applies (PART 47).
 */
@Component
public class SchedulingTimeMapper {

    private final ZoneId collegeZone;

    public SchedulingTimeMapper(@Value("${app.college.time-zone}") String collegeTimeZone) {
        this.collegeZone = ZoneId.of(collegeTimeZone);
    }

    public ZoneId getCollegeZone() {
        return collegeZone;
    }

    public Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(collegeZone).toInstant();
    }

    /** Half-open {@code [start, end)}, matching the interval semantics used everywhere else in this project. */
    public InstantRange toInstantRange(LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new InstantRange(toInstant(date, startTime), toInstant(date, endTime));
    }
}
