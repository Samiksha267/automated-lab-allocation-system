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
 * One physical laboratory. {@code code} (e.g. "C-304") is the stable,
 * user-facing identifier - allocation explanations reference it, never the
 * database id (PART 4 of the phase brief) - and is immutable after creation
 * (docs/15-DESIGN-DECISIONS.md: a stable code matters for future PDF-import
 * cross-referencing, so there is no update path for it).
 *
 * <p>Location is a flat {@code wing}/{@code floor}/{@code roomNumber} model,
 * not a full campus/building hierarchy - the college currently has one
 * building with three wings, and a hierarchy for a single-campus, single
 * building scope would be speculative complexity (docs/15-DESIGN-DECISIONS.md).
 *
 * <p>{@code active = false} means permanently retired/repurposed - distinct
 * from {@link LabUnavailability}, which represents a temporary, dated window
 * (maintenance, an event) on an otherwise-active lab.
 */
@Entity
@Table(name = "lab")
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_type_id", nullable = false)
    private LabType labType;

    @Column(nullable = false, length = 16)
    private String wing;

    @Column(nullable = false, length = 16)
    private String floor;

    @Column(name = "room_number", nullable = false, length = 16)
    private String roomNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lab() {}

    public Lab(String code, String name, int capacity, LabType labType, String wing, String floor, String roomNumber) {
        this.code = code;
        this.name = name;
        this.capacity = capacity;
        this.labType = labType;
        this.wing = wing;
        this.floor = floor;
        this.roomNumber = roomNumber;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, int capacity, LabType labType, String wing, String floor, String roomNumber, boolean active) {
        this.name = name;
        this.capacity = capacity;
        this.labType = labType;
        this.wing = wing;
        this.floor = floor;
        this.roomNumber = roomNumber;
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

    public int getCapacity() {
        return capacity;
    }

    public LabType getLabType() {
        return labType;
    }

    public String getWing() {
        return wing;
    }

    public String getFloor() {
        return floor;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public boolean isActive() {
        return active;
    }
}
