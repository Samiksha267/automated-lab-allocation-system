package com.college.laballocation.academic;

/**
 * A stable application concept (unlike program/stream names, which are data,
 * not enums - PART 61). More than one term may be {@code ACTIVE}
 * simultaneously by design - see {@link AcademicTerm}.
 */
public enum TermStatus {
    UPCOMING,
    ACTIVE,
    CLOSED
}
