package com.college.laballocation.scheduling;

/**
 * A stable application concept (like {@code TermStatus}), not data - the two
 * kinds of allocation have genuinely different lifecycles
 * (docs/03-SYSTEM-ARCHITECTURE.md §5): {@code REGULAR} comes from an
 * approved PDF-import entry (Phase 19); {@code EXTRA} is a CR-created
 * makeup/additional practical, FCFS, validated and committed directly
 * (Phase 15/16).
 */
public enum AllocationType {
    REGULAR,
    EXTRA
}
