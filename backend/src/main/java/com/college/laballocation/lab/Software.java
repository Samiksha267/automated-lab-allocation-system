package com.college.laballocation.lab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A software capability (e.g. Cloudera), not a specific installed version.
 * {@code code} is a normalized key (uppercase, e.g. {@code "CLOUDERA"}) so
 * "Cloudera"/"cloudera"/"CLOUDERA" can never accidentally become three
 * different capabilities - {@code name} is the separate display label
 * (docs/15-DESIGN-DECISIONS.md). Per-lab installed version, if tracked, lives
 * on {@link LabSoftware}, not here - this row represents the capability
 * itself, shared across every lab that has it.
 */
@Entity
@Table(name = "software")
public class Software {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Software() {}

    public Software(String code, String name) {
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

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
