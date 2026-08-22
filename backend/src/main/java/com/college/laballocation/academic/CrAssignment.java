package com.college.laballocation.academic;

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

/**
 * Connects an {@code app_user} (role=CR) to a {@link Division} for a given
 * {@link AcademicTerm}, preserving history rather than overwriting on
 * reassignment (docs/04-DATABASE-DESIGN.md §1). "The user must actually have
 * role CR" cannot be a database CHECK constraint across tables in Postgres,
 * so it is validated in {@code CrAssignmentService} before every insert.
 *
 * <p>Business rule (PART 16/17 of the phase brief, both DB-enforced via
 * partial unique indexes - see V5 migration): at most one {@code ACTIVE}
 * assignment per division per term, and at most one {@code ACTIVE}
 * assignment per CR user per term (one division at a time per CR, to keep
 * authorization unambiguous).
 */
@Entity
@Table(name = "cr_assignment")
public class CrAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CrAssignmentStatus status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CrAssignment() {}

    public CrAssignment(AppUser user, Division division, AcademicTerm academicTerm, AppUser createdBy) {
        this.user = user;
        this.division = division;
        this.academicTerm = academicTerm;
        this.status = CrAssignmentStatus.ACTIVE;
        this.validFrom = Instant.now();
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void end() {
        this.status = CrAssignmentStatus.ENDED;
        this.validTo = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Division getDivision() {
        return division;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public CrAssignmentStatus getStatus() {
        return status;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }
}
