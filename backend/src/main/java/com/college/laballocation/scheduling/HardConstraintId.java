package com.college.laballocation.scheduling;

/**
 * Stable identifiers for the twelve hard constraints (docs/06-CONSTRAINTS.md),
 * kept deliberately separate from the wire-level API error codes
 * (`LAB_CONFLICT`, `FACULTY_CONFLICT`, ...) - this enum identifies *which
 * constraint ran*, the error code in {@link ConstraintViolation} describes
 * *why it failed*. No constraint class evaluates these yet - that is
 * Phase 9; this phase only names them so {@link ConstraintResult} has a
 * stable identifier to report against.
 */
public enum HardConstraintId {
    HC_01_LAB_CONFLICT,
    HC_02_FACULTY_CONFLICT,
    HC_03_FACULTY_AVAILABILITY,
    HC_04_BATCH_CONFLICT,
    HC_05_DIVISION_CONFLICT,
    HC_06_LAB_AVAILABILITY,
    HC_07_CAPACITY,
    HC_08_REQUIRED_SOFTWARE,
    HC_09_REQUIRED_EQUIPMENT,
    HC_10_REQUIRED_LAB_TYPE,
    HC_11_CR_AUTHORIZATION,
    HC_12_ACADEMIC_RELATIONSHIP
}
