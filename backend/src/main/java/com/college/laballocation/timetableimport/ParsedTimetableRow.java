package com.college.laballocation.timetableimport;

/**
 * One raw timetable line, split into its 8 documented columns but not yet
 * normalized or mapped to any entity (PART 15/16) - {@link TimetableParser}'s
 * only output type, deliberately a plain record with no JPA/Spring
 * dependency so it can be unit-tested with no database at all.
 */
public record ParsedTimetableRow(
        int sourceLineNumber,
        String rawDay,
        String rawStartTime,
        String rawEndTime,
        String rawSubject,
        String rawFaculty,
        String rawLab,
        String rawDivision,
        String rawBatch) {}
