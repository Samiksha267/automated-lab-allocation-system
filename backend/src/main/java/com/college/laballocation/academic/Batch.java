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
 * Belongs to exactly one {@link Division}. The number of batches per division
 * is variable - never assume exactly two (PART 8/60). {@code strength} is the
 * capacity source of truth for {@code target_type = BATCH} allocations later
 * (docs/06-CONSTRAINTS.md HC-07) - no {@code Student} entity exists or is
 * planned for this purpose (ADR, docs/15-DESIGN-DECISIONS.md).
 *
 * <p>The sum of a division's batch strengths is <b>not</b> enforced to equal
 * the division's own strength - a strict invariant here isn't something
 * college reality requires (PART 8 of the phase brief explicitly warns
 * against inventing such a rule), and enforcing it would block legitimate
 * cases (e.g. a division strength that's a planning estimate updated on a
 * different cadence than individual batch rosters).
 */
@Entity
@Table(name = "batch")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

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

    protected Batch() {}

    public Batch(Division division, String code, int strength) {
        this.division = division;
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

    public Division getDivision() {
        return division;
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
