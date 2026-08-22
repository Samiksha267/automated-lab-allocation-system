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
 * Explicit lab-has-equipment association, carrying {@code quantity} (e.g.
 * "10 routers") since the phase brief's own example makes this a real,
 * not speculative, need - default 1 for binary-availability equipment.
 * Uniqueness on {@code (lab_id, equipment_id)} prevents a duplicate row;
 * quantity changes update the existing row rather than adding another.
 */
@Entity
@Table(name = "lab_equipment")
public class LabEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LabEquipment() {}

    public LabEquipment(Lab lab, Equipment equipment, int quantity) {
        this.lab = lab;
        this.equipment = equipment;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Lab getLab() {
        return lab;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public int getQuantity() {
        return quantity;
    }
}
