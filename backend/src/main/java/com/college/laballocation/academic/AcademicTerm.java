package com.college.laballocation.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A scheduling period, e.g. "Semester 5, 2026-27" - independent of the
 * Program/Stream/AcademicYear chain, since one term applies across the
 * institution rather than nesting under a single program. Deliberately NOT a
 * global singleton "current term": different programs may legitimately be on
 * different active terms at once, so more than one row may be
 * {@link TermStatus#ACTIVE} at a time (docs/03-SYSTEM-ARCHITECTURE.md /
 * docs/15-DESIGN-DECISIONS.md) - this is intentional, not unenforced.
 */
@Entity
@Table(name = "academic_term")
public class AcademicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academic_year_label", nullable = false, length = 16)
    private String academicYearLabel;

    @Column(name = "term_number", nullable = false)
    private int termNumber;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TermStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicTerm() {}

    public AcademicTerm(
            String academicYearLabel, int termNumber, String displayName, LocalDate startDate, LocalDate endDate) {
        this.academicYearLabel = academicYearLabel;
        this.termNumber = termNumber;
        this.displayName = displayName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = TermStatus.UPCOMING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(TermStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAcademicYearLabel() {
        return academicYearLabel;
    }

    public int getTermNumber() {
        return termNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public TermStatus getStatus() {
        return status;
    }
}
