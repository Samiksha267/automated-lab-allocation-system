package com.college.laballocation.scheduling;

import java.time.Instant;

/** A half-open {@code [start, end)} instant range - the output shape of {@link SchedulingTimeMapper#toInstantRange}. */
public record InstantRange(Instant start, Instant end) {}
