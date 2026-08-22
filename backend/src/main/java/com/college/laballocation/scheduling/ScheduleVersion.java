package com.college.laballocation.scheduling;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * An immutable, timestamped snapshot of a term's official ({@code REGULAR})
 * allocations - exactly one {@code PUBLISHED} version exists per
 * {@link AcademicTerm} at a time (enforced by a partial unique index,
 * {@code uq_schedule_version_one_published_per_term}, not just this class),
 * so students only ever see the current version while every prior version
 * remains queryable history (ADR-009, docs/15-DESIGN-DECISIONS.md).
 *
 * <p>{@code EXTRA} allocations do not wait for a version cut - they attach
 * directly to the term's currently {@code PUBLISHED} version and are stamped
 * {@code PUBLISHED} immediately (Phase 15/16), which is also why every
 * {@link Allocation} carries a mandatory {@code scheduleVersionId} rather
 * than a separate, redundant {@code academicTermId} - the term is always
 * derivable via {@code allocation.getScheduleVersion().getAcademicTerm()}.
 */
@Entity
@Table(name = "schedule_version")
public class ScheduleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScheduleVersionStatus status;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private AppUser publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ScheduleVersion() {}

    public ScheduleVersion(AcademicTerm academicTerm, int versionNumber, String reason, AppUser createdBy) {
        this.academicTerm = academicTerm;
        this.versionNumber = versionNumber;
        this.status = ScheduleVersionStatus.DRAFT;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    /** {@code DRAFT -> PUBLISHED} only - see class javadoc for why {@code SUPERSEDED -> DRAFT} is never allowed. */
    public void publish(AppUser publishedBy) {
        if (status != ScheduleVersionStatus.DRAFT) {
            throw new ApiException(
                    "INVALID_SCHEDULE_VERSION_TRANSITION", HttpStatus.CONFLICT,
                    "Only a DRAFT schedule version can be published; current status is " + status + ".");
        }
        this.status = ScheduleVersionStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
    }

    /** {@code PUBLISHED -> SUPERSEDED} only, called when a newer version is published for the same term. */
    public void supersede() {
        if (status != ScheduleVersionStatus.PUBLISHED) {
            throw new ApiException(
                    "INVALID_SCHEDULE_VERSION_TRANSITION", HttpStatus.CONFLICT,
                    "Only a PUBLISHED schedule version can be superseded; current status is " + status + ".");
        }
        this.status = ScheduleVersionStatus.SUPERSEDED;
    }

    public Long getId() {
        return id;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public ScheduleVersionStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AppUser getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
