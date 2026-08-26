package com.college.laballocation.scheduling;

import java.time.LocalDate;
import java.util.Arrays;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable {@link Specification} filters shared by the two Phase 18 read
 * endpoints that list allocations - {@code ScheduleVersionController}'s
 * per-version allocation listing (Lab Assistant, any status) and
 * {@code TimetableController}'s student/CR timetable (always additionally
 * constrained to the term's currently {@code PUBLISHED} version via
 * {@link #currentlyPublishedForTerm}, never a status the caller supplies).
 * Mirrors this project's existing {@code AuditLogSpecifications}/{@code LabSpecifications} pattern.
 */
final class AllocationSpecifications {
    private AllocationSpecifications() {}

    static Specification<Allocation> scheduleVersionId(Long scheduleVersionId) {
        return (root, query, cb) -> cb.equal(root.get("scheduleVersion").get("id"), scheduleVersionId);
    }

    /** Never accepts a caller-supplied status - always exactly {@code PUBLISHED}, so a DRAFT/SUPERSEDED version's rows can never leak through the timetable API (PART 21 of the phase brief). */
    static Specification<Allocation> currentlyPublishedForTerm(Long academicTermId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("scheduleVersion").get("academicTerm").get("id"), academicTermId),
                cb.equal(root.get("scheduleVersion").get("status"), ScheduleVersionStatus.PUBLISHED));
    }

    static Specification<Allocation> divisionId(Long divisionId) {
        return (root, query, cb) -> cb.equal(root.get("division").get("id"), divisionId);
    }

    static Specification<Allocation> batchId(Long batchId) {
        return (root, query, cb) -> cb.equal(root.get("batch").get("id"), batchId);
    }

    /**
     * A batch-scoped student/CR timetable view must show that batch's own rows AND the division-wide rows every
     * batch in the division attends (Phase 22, PART 9) - a strict {@code batch.id = batchId} equality would hide
     * every division-wide practical from anyone who selects a specific batch, which is exactly the "accidentally
     * hide division-wide practicals" failure the phase brief calls out by name.
     */
    static Specification<Allocation> batchIdOrDivisionWide(Long batchId) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("batch").get("id"), batchId), cb.isNull(root.get("batch")));
    }

    static Specification<Allocation> subjectId(Long subjectId) {
        return (root, query, cb) -> cb.equal(root.get("subject").get("id"), subjectId);
    }

    static Specification<Allocation> facultyId(Long facultyId) {
        return (root, query, cb) -> cb.equal(root.get("faculty").get("id"), facultyId);
    }

    static Specification<Allocation> labId(Long labId) {
        return (root, query, cb) -> cb.equal(root.get("lab").get("id"), labId);
    }

    static Specification<Allocation> allocationDate(LocalDate date) {
        return (root, query, cb) -> cb.equal(root.get("allocationDate"), date);
    }

    /** Excludes CANCELLED rows - a student's timetable shows what is actually happening, not historical cancellations (reuses the single centralized "active" definition, {@link AllocationStatus#blocksScheduling()}, never a locally reinvented status list). */
    static Specification<Allocation> activeStatus() {
        AllocationStatus[] active = Arrays.stream(AllocationStatus.values()).filter(AllocationStatus::blocksScheduling).toArray(AllocationStatus[]::new);
        return (root, query, cb) -> root.get("status").in((Object[]) active);
    }
}
