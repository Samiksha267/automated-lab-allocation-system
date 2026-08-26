package com.college.laballocation.audit;

import java.time.Instant;
import java.util.Map;

public final class AuditLogDtos {
    private AuditLogDtos() {}

    /**
     * Never the raw JPA entity (PART 35) - {@code actorDisplayName}/{@code actorEmail}
     * are resolved by {@link AuditLogService} via one bulk lookup per page,
     * never a per-row lazy association (PART 78, no N+1).
     */
    public record AuditLogResponse(
            Long id,
            Long actorUserId,
            String actorDisplayName,
            String actorEmail,
            String actorRole,
            String action,
            String resourceType,
            Long resourceId,
            String resourceDisplay,
            Long academicTermId,
            Long divisionId,
            Map<String, Object> metadata,
            Instant createdAt) {}

    /** Every field optional - an absent filter simply isn't applied (PART 32/62). */
    public record AuditLogSearchCriteria(
            Long actorUserId,
            AuditAction action,
            AuditResourceType resourceType,
            Long academicTermId,
            Long divisionId,
            Instant from,
            Instant to) {}
}
