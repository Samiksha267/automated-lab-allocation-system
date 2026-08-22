package com.college.laballocation.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Top of the academic hierarchy (docs/04-DATABASE-DESIGN.md §2). Data-driven, never an enum (PART 61). */
@Entity
@Table(name = "program")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Program() {}

    public Program(String code, String name, int durationYears) {
        this.code = code;
        this.name = name;
        this.durationYears = durationYears;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, int durationYears, boolean active) {
        this.name = name;
        this.durationYears = durationYears;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDurationYears() {
        return durationYears;
    }

    public boolean isActive() {
        return active;
    }
}
