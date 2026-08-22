package com.college.laballocation.user;

/**
 * Exactly the three login roles confirmed for this system (docs/02-REQUIREMENTS.md,
 * docs/09-AUTHORIZATION-RBAC.md). Faculty is a domain entity with no login and
 * therefore has no role here (see ADR-006 in docs/15-DESIGN-DECISIONS.md).
 * Persisted as a string ({@code @Enumerated(EnumType.STRING)}), never an
 * ordinal, so the stored value stays readable and reordering this enum can
 * never silently corrupt existing data.
 */
public enum UserRole {
    LAB_ASSISTANT,
    CR,
    STUDENT
}
