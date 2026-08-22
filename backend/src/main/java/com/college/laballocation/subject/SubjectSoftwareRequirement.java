package com.college.laballocation.subject;

import com.college.laballocation.lab.Software;
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
 * "This subject requires this software" - the SUBJECT side of the future
 * Constraint Engine's HC-08 input, deliberately never stored on {@code Lab}
 * (docs/03-SYSTEM-ARCHITECTURE.md). No {@code required} boolean - a row's
 * mere existence means required; every row for a subject is ANDed together
 * (ALL-required, not ANY) by whichever future code reads them - this entity
 * only records the fact, not the matching logic.
 */
@Entity
@Table(name = "subject_software_requirement")
public class SubjectSoftwareRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_id", nullable = false)
    private Software software;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubjectSoftwareRequirement() {}

    public SubjectSoftwareRequirement(Subject subject, Software software) {
        this.subject = subject;
        this.software = software;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public Software getSoftware() {
        return software;
    }
}
