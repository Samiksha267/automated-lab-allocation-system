package com.college.laballocation.academic;

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
 * Belongs directly to an {@link AcademicYear} - program/stream are not
 * duplicated here since they're cleanly derivable via
 * {@code academicYear.getStream().getProgram()} (docs/04-DATABASE-DESIGN.md §7:
 * "do not store duplicated FKs if derivable from one parent").
 *
 * <p>{@code strength} is required and positive - used directly as the
 * required capacity for {@code target_type = DIVISION} allocations later
 * (docs/06-CONSTRAINTS.md HC-07), deliberately not left nullable as an
 * earlier draft had it (see ASSUMPTIONS.md for this Phase 4 correction).
 */
@Entity
@Table(name = "division")
public class Division {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false)
    private int strength;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Division() {}

    public Division(AcademicYear academicYear, String code, int strength) {
        this.academicYear = academicYear;
        this.code = code;
        this.strength = strength;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(int strength, boolean active) {
        this.strength = strength;
        this.active = active;
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

    public int getStrength() {
        return strength;
    }

    public boolean isActive() {
        return active;
    }
}
