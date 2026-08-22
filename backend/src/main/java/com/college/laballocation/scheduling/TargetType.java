package com.college.laballocation.scheduling;

/**
 * Explicit column, not implicit null (ADR-005, docs/15-DESIGN-DECISIONS.md) -
 * the single most important domain rule in this project: two different
 * batches of the same division may run simultaneously, but a
 * {@code DIVISION}-wide session occupies every batch beneath it.
 */
public enum TargetType {
    BATCH,
    DIVISION
}
