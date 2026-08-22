package com.college.laballocation.faculty;

import com.college.laballocation.academic.AcademicTerm;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * A recurring weekly window in which {@code faculty} is available to teach,
 * scoped to one {@code academicTerm} - term-scoping is mandatory (ADR-031,
 * docs/15-DESIGN-DECISIONS.md), superseding the earlier Phase 1 draft's
 * nullable "applies every term" design (docs/ASSUMPTIONS.md A-15, superseded
 * this phase). This models availability only - never an actual booked
 * session; see the Faculty Availability vs. Faculty Conflict distinction
 * (docs/06-CONSTRAINTS.md HC-02 vs HC-03).
 *
 * <p>Half-open interval {@code [startTime, endTime)}, the same semantics used
 * throughout this project (docs/06-CONSTRAINTS.md). Overlapping active rows
 * for the same faculty/term/dayOfWeek are rejected at write time (never
 * silently merged - ADR-032); adjacent rows (e.g. {@code 09:00-12:00} and
 * {@code 12:00-15:00}) are permitted and are treated as one continuous window
 * only during availability evaluation ({@link FacultyAvailabilityService}),
 * never mutated in the database.
 *
 * <p>{@code active=false} means administratively deactivated (soft
 * deactivation, matching this project's dominant historical-entity pattern -
 * ADR-033) rather than physically removed.
 */
@Entity
@Table(name = "faculty_availability")
public class FacultyAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 16)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FacultyAvailability() {}

    public FacultyAvailability(
            Faculty faculty, AcademicTerm academicTerm, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.faculty = faculty;
        this.academicTerm = academicTerm;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return active;
    }
}
