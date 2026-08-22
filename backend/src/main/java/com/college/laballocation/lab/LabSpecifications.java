package com.college.laballocation.lab;

import jakarta.persistence.criteria.Subquery;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Static capability filtering (PART 33/34 of the phase brief) - NOT
 * schedule-aware availability. These predicates only ever compare a lab's
 * fixed properties (capacity, type, installed software/equipment); none of
 * them look at allocations, requested time windows, or faculty, because none
 * of that exists yet (Phase 9+). Naming things like this precisely matters:
 * a lab returned here is a *candidate by static properties*, not a lab
 * confirmed free at any particular moment.
 *
 * <p>Multi-value software/equipment filters use <b>ALL</b> semantics (a lab
 * must have every requested capability, not merely one) via one {@code EXISTS}
 * subquery per requested id, ANDed together - chosen because "ALL" is what
 * the future scheduling requirement actually needs (docs/06-CONSTRAINTS.md
 * HC-08's ALL-required semantics), and per-id EXISTS subqueries compose
 * cleanly with the other filters without needing a separate GROUP BY/HAVING
 * query shape.
 */
final class LabSpecifications {

    private LabSpecifications() {}

    static Specification<Lab> active(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    static Specification<Lab> wing(String wing) {
        return (root, query, cb) -> cb.equal(root.get("wing"), wing);
    }

    static Specification<Lab> labTypeCode(String labTypeCode) {
        return (root, query, cb) -> cb.equal(root.get("labType").get("code"), labTypeCode);
    }

    static Specification<Lab> minCapacity(int minCapacity) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity);
    }

    static Specification<Lab> hasAllSoftware(List<String> softwareCodes) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            for (String code : softwareCodes) {
                Subquery<Long> subquery = query.subquery(Long.class);
                var lsRoot = subquery.from(LabSoftware.class);
                subquery.select(lsRoot.get("id"))
                        .where(
                                cb.equal(lsRoot.get("lab"), root),
                                cb.equal(lsRoot.get("software").get("code"), code));
                predicate = cb.and(predicate, cb.exists(subquery));
            }
            return predicate;
        };
    }

    static Specification<Lab> hasAllEquipment(List<String> equipmentCodes) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            for (String code : equipmentCodes) {
                Subquery<Long> subquery = query.subquery(Long.class);
                var leRoot = subquery.from(LabEquipment.class);
                subquery.select(leRoot.get("id"))
                        .where(
                                cb.equal(leRoot.get("lab"), root),
                                cb.equal(leRoot.get("equipment").get("code"), code));
                predicate = cb.and(predicate, cb.exists(subquery));
            }
            return predicate;
        };
    }
}
