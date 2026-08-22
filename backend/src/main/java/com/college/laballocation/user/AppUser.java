package com.college.laballocation.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Authenticated system account. Exactly the three login roles (see
 * {@link UserRole}) - Faculty is a separate domain entity with no row here
 * (ADR-006, docs/15-DESIGN-DECISIONS.md). Table is named {@code app_user},
 * not {@code user}, since {@code user} collides with SQL/Postgres reserved
 * terminology (docs/04-DATABASE-DESIGN.md).
 *
 * <p>{@code email} is normalized (trimmed + lowercased) in {@link AppUser#normalizeEmail(String)}
 * before it is ever compared or persisted, rather than relying on a
 * PostgreSQL case-insensitive column type (citext) - simpler, and sufficient
 * for this project's scale (see docs/09-AUTHORIZATION-RBAC.md).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(String email, String passwordHash, UserRole role, String displayName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    /** Deactivates the account - an already-issued JWT stops working on its next request (see JwtAuthenticationFilter). */
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
