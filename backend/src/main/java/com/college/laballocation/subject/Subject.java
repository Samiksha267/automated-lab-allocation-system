package com.college.laballocation.subject;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.lab.LabType;
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
import org.springframework.http.HttpStatus;

/**
 * A subject definition scoped to a specific {@link AcademicYear} (which
 * itself pins it to a stream/program) - e.g. "BDA" for B.Tech/CS/Year 3.
 * There is no separate {@code SubjectOffering} entity: the term-specific
 * "who teaches this, for which division/batch, this term" context already
 * lives on {@code SubjectFacultyAssignment}, so a second offering layer would
 * duplicate that without adding anything (docs/15-DESIGN-DECISIONS.md).
 *
 * <p>Software/equipment requirements live on their own join tables
 * ({@link SubjectSoftwareRequirement}, {@link SubjectEquipmentRequirement} -
 * a subject can require several of each). Lab-type requirement is different:
 * a subject can require at most <b>one</b> lab type, so it's a nullable FK
 * column here rather than a join table (docs/04-DATABASE-DESIGN.md).
 *
 * <p>{@code requiredLabType} (hard - HC-10, other types are invalid) and
 * {@code preferredLabType} (soft - a scoring signal, docs/07-ALLOCATION-SCORING.md)
 * are deliberately distinct concepts, never collapsed. Both may be null,
 * or exactly one may be set - never both, enforced by
 * {@link #setLabTypeRequirement} and mirrored by a database CHECK constraint
 * (defense in depth, same pattern used throughout this project).
 */
@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_lab_type_id")
    private LabType requiredLabType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_lab_type_id")
    private LabType preferredLabType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subject() {}

    public Subject(AcademicYear academicYear, String code, String name) {
        this.academicYear = academicYear;
        this.code = code;
        this.name = name;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    /**
     * Enforces the required/preferred mutual-exclusivity rule
     * (docs/06-CONSTRAINTS.md / docs/07-ALLOCATION-SCORING.md) in application
     * code, ahead of the database CHECK constraint that backs it up.
     */
    public void setLabTypeRequirement(LabType requiredLabType, LabType preferredLabType) {
        if (requiredLabType != null && preferredLabType != null) {
            throw new ApiException(
                    "INVALID_LAB_TYPE_PREFERENCE", HttpStatus.BAD_REQUEST,
                    "A subject cannot have both a required and a preferred lab type at the same time.");
        }
        this.requiredLabType = requiredLabType;
        this.preferredLabType = preferredLabType;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LabType getRequiredLabType() {
        return requiredLabType;
    }

    public LabType getPreferredLabType() {
        return preferredLabType;
    }

    public boolean isActive() {
        return active;
    }
}
