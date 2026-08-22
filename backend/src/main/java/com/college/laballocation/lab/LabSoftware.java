package com.college.laballocation.lab;

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
 * Explicit lab-has-software association, not an implicit {@code @ManyToMany} -
 * chosen specifically so per-installation metadata ({@code installedVersion})
 * can be added without a later migration to convert a plain join table into
 * a real entity (docs/15-DESIGN-DECISIONS.md). Uniqueness on
 * {@code (lab_id, software_id)} at the database level prevents a duplicate
 * installation row.
 */
@Entity
@Table(name = "lab_software")
public class LabSoftware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_id", nullable = false)
    private Software software;

    @Column(name = "installed_version", length = 64)
    private String installedVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LabSoftware() {}

    public LabSoftware(Lab lab, Software software, String installedVersion) {
        this.lab = lab;
        this.software = software;
        this.installedVersion = installedVersion;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Lab getLab() {
        return lab;
    }

    public Software getSoftware() {
        return software;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }
}
