package com.college.laballocation.audit;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/** Composable {@link Specification} filters for the Lab Assistant activity API - mirrors this project's existing {@code LabSpecifications} pattern. */
final class AuditLogSpecifications {
    private AuditLogSpecifications() {}

    static Specification<AuditLog> actorUserId(Long actorUserId) {
        return (root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId);
    }

    static Specification<AuditLog> action(AuditAction action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    static Specification<AuditLog> resourceType(AuditResourceType resourceType) {
        return (root, query, cb) -> cb.equal(root.get("resourceType"), resourceType);
    }

    static Specification<AuditLog> academicTermId(Long academicTermId) {
        return (root, query, cb) -> cb.equal(root.get("academicTermId"), academicTermId);
    }

    static Specification<AuditLog> divisionId(Long divisionId) {
        return (root, query, cb) -> cb.equal(root.get("divisionId"), divisionId);
    }

    static Specification<AuditLog> createdAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AuditLog> createdAtTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
