package com.college.laballocation.subject;

import com.college.laballocation.lab.Equipment;
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
 * "This subject requires this equipment, at least this quantity" - mirrors
 * {@link com.college.laballocation.lab.LabEquipment#getQuantity()} on the lab
 * side. Future compatibility rule (not implemented until the constraint
 * engine, Phase 9+): {@code labEquipment.quantity >= requiredQuantity} for
 * every required row.
 */
@Entity
@Table(name = "subject_equipment_requirement")
public class SubjectEquipmentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "required_quantity", nullable = false)
    private int requiredQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubjectEquipmentRequirement() {}

    public SubjectEquipmentRequirement(Subject subject, Equipment equipment, int requiredQuantity) {
        this.subject = subject;
        this.equipment = equipment;
        this.requiredQuantity = requiredQuantity;
        this.createdAt = Instant.now();
    }

    public void updateRequiredQuantity(int requiredQuantity) {
        this.requiredQuantity = requiredQuantity;
    }

    public Long getId() {
        return id;
    }

    public Subject getSubject() {
        return subject;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public int getRequiredQuantity() {
        return requiredQuantity;
    }
}
