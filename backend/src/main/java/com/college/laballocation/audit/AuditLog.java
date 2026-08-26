package com.college.laballocation.audit;

import com.college.laballocation.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable historical record of one important, state-changing action
 * (Phase 17) - "who did what, to what, when." Deliberately <b>not</b> the
 * source of truth for current application state (PART 2 of the phase brief):
 * {@code Allocation.status}, {@code CrAssignment.status}, etc. remain
 * authoritative; a row here only ever describes that a transition happened,
 * never re-derives or overrides it.
 *
 * <p><b>Immutability, enforced at BOTH layers</b> (PART 3/6/26/40): no setter
 * exists on this class beyond the constructor, no service anywhere calls
 * {@code AuditLogRepository.save(...)} a second time against an existing row
 * (id is never re-supplied - every call is a fresh insert), and no controller
 * exposes an update/delete endpoint - AND a PostgreSQL trigger (V12 migration)
 * rejects any {@code UPDATE}/{@code DELETE} against {@code audit_log},
 * regardless of caller. See ADR in docs/15-DESIGN-DECISIONS.md for why both
 * layers exist (application-level alone only proves this codebase doesn't
 * mutate the table - a future migration, a manual hotfix, or another service
 * sharing the database could still bypass it).
 *
 * <p>{@code @Immutable} additionally tells Hibernate this entity is never
 * dirty-checked or re-persisted once loaded - beyond documenting the
 * invariant, this is a real fix for a bug found live in Docker during Phase
 * 17 verification: without it, Hibernate could spuriously decide a
 * just-inserted {@code AuditLog} (its JSON-mapped {@code metadata} field
 * included) was "dirty" within the same flush and issue a needless UPDATE -
 * which the V12 trigger then correctly rejected, surfacing as a 500 on an
 * otherwise-successful business operation (e.g. {@code CrAssignmentService.create}
 * recording two audit events in one transaction). {@code @Immutable} removes
 * Hibernate's UPDATE code path for this entity entirely, so the class of bug
 * cannot recur.
 *
 * <p>{@code actorUserId} is a plain column, not a JPA {@code @ManyToOne} -
 * deliberately, to keep the audit-listing query free of any lazy-loading/N+1
 * risk (PART 78): the activity API resolves actor display data via one bulk
 * {@code UserRepository.findAllById(...)} call per page, not a
 * relationship Hibernate would otherwise need to fetch per row. A real
 * database foreign key to {@code app_user} still exists (V12 migration) -
 * this project never hard-deletes users (only deactivates), so the FK can
 * never be violated by a legitimate user-management action, and no
 * {@code ON DELETE CASCADE} exists (an audit row must never silently
 * disappear because of anything that happens to the actor's account later).
 *
 * <p>{@code actorRole} is a snapshot string (the role at the moment of the
 * action), not re-derived from the current {@code app_user} row - a user's
 * role could in principle change later, and the historical record must
 * still reflect what they were authorized as when the action actually
 * happened (PART 6).
 *
 * <p>{@code metadata} is PostgreSQL {@code JSONB} - event-specific, small,
 * non-sensitive context (e.g. lab code, subject code, requested time) that
 * varies per {@link AuditAction} and would otherwise need a new nullable
 * column (and migration) for every new event shape. Every field a caller
 * might reasonably want to *query or filter by* (actor, role, action,
 * resource, term, division, timestamp) is a real relational column instead -
 * JSONB is reserved for descriptive, non-searchable detail only (PART 13).
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 32)
    private UserRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private AuditResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "resource_display", length = 255)
    private String resourceDisplay;

    @Column(name = "academic_term_id")
    private Long academicTermId;

    @Column(name = "division_id")
    private Long divisionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(
            Long actorUserId,
            UserRole actorRole,
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            String resourceDisplay,
            Long academicTermId,
            Long divisionId,
            Map<String, Object> metadata) {
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.resourceDisplay = resourceDisplay;
        this.academicTermId = academicTermId;
        this.divisionId = divisionId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public UserRole getActorRole() {
        return actorRole;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditResourceType getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getResourceDisplay() {
        return resourceDisplay;
    }

    public Long getAcademicTermId() {
        return academicTermId;
    }

    public Long getDivisionId() {
        return divisionId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
