package com.college.laballocation.lab;

import com.college.laballocation.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A temporary, dated administrative block on an otherwise-active lab
 * (maintenance, a college event) - distinct from {@link Lab#isActive()}
 * (permanent deactivation). Half-open interval {@code [start, end)}, same
 * semantics used throughout the project (docs/06-CONSTRAINTS.md); the
 * database CHECK constraint {@code end_date_time > start_date_time} is the
 * primary guarantee, mirrored by application validation for a clean error
 * before the row is ever attempted.
 *
 * <p>No recurrence model - an explicit dated interval only (PART 16 of the
 * phase brief); weekly-recurring unavailability is a documented future
 * extension (docs/18-FUTURE-IMPROVEMENTS.md), not built until a real
 * requirement demands it.
 *
 * <p>No soft-cancel status: nothing yet references a {@code LabUnavailability}
 * row (no allocations exist until Phase 9+), so a genuinely obsolete/created-
 * in-error record can be safely hard-deleted rather than carrying a status
 * column purely for a "cancelled" state that has no downstream consumer yet
 * (docs/15-DESIGN-DECISIONS.md).
 */
@Entity
@Table(name = "lab_unavailability")
public class LabUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @Column(name = "start_date_time", nullable = false)
    private Instant startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private Instant endDateTime;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LabUnavailability() {}

    public LabUnavailability(Lab lab, Instant startDateTime, Instant endDateTime, String reason, AppUser createdBy) {
        this.lab = lab;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Lab getLab() {
        return lab;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public Instant getEndDateTime() {
        return endDateTime;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }
}
