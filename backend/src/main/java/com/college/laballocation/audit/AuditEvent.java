package com.college.laballocation.audit;

import com.college.laballocation.user.UserRole;
import java.util.Map;
import java.util.Objects;

/**
 * The input to {@link AuditLogService#record(AuditEvent)} - a plain,
 * self-contained description of one action to record, deliberately not a
 * giant untyped parameter list (PART 20 of the phase brief). Callers
 * construct one directly at the call site (this project's established
 * "three similar lines over a premature abstraction" style, docs/15-DESIGN-DECISIONS.md)
 * rather than via a fluent builder - every field is meaningful and there is
 * no optional/omittable subset worth a builder's complexity.
 *
 * <p>{@code academicTermId}/{@code divisionId} are nullable - populated when
 * the action genuinely has that scope (extra-lab booking, CR assignment) and
 * left {@code null} for admin actions with no term/division context (lab
 * creation, subject requirement changes) rather than a rule requiring them
 * universally (PART 15).
 */
public record AuditEvent(
        Long actorUserId,
        UserRole actorRole,
        AuditAction action,
        AuditResourceType resourceType,
        Long resourceId,
        String resourceDisplay,
        Long academicTermId,
        Long divisionId,
        Map<String, Object> metadata) {

    public AuditEvent {
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(actorRole, "actorRole must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Convenience factory for the common case of no term/division scope. */
    public static AuditEvent of(
            Long actorUserId,
            UserRole actorRole,
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            String resourceDisplay,
            Map<String, Object> metadata) {
        return new AuditEvent(actorUserId, actorRole, action, resourceType, resourceId, resourceDisplay, null, null, metadata);
    }
}
