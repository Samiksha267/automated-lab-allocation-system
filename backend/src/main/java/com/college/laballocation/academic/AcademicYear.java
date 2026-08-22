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
 * The program's study year (Year 1..4), NOT a calendar year like 2026 - see
 * {@link AcademicTerm} for the separate "semester/time period" concept
 * (docs/04-DATABASE-DESIGN.md). Belongs to a {@link Stream}.
 */
@Entity
@Table(name = "academic_year")
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false)
    private Stream stream;

    @Column(name = "year_number", nullable = false)
    private int yearNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicYear() {}

    public AcademicYear(Stream stream, int yearNumber) {
        this.stream = stream;
        this.yearNumber = yearNumber;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Stream getStream() {
        return stream;
    }

    public int getYearNumber() {
        return yearNumber;
    }

    public boolean isActive() {
        return active;
    }
}
