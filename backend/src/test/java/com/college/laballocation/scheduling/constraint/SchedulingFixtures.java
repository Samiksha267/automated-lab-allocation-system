package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.BatchRef;
import com.college.laballocation.scheduling.SchedulingRefs.DivisionRef;
import com.college.laballocation.scheduling.SchedulingRefs.ExistingAllocationSnapshot;
import com.college.laballocation.scheduling.SchedulingRefs.FacultyRef;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.scheduling.SchedulingRefs.SubjectRef;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared fixture builders for constraint unit tests - deliberately plain
 * Java, no Mockito, no Spring: every {@code Scheduling*}/{@code *Ref} type
 * is a plain record, so tests construct real domain objects directly
 * (PART 61 of the Phase 9 brief) rather than mocking data that has no
 * business being mocked.
 */
final class SchedulingFixtures {
    private SchedulingFixtures() {}

    static final LocalDate MONDAY = LocalDate.of(2026, 8, 24); // a real Monday
    static final Long DIVISION_ID = 1L;
    static final Long BATCH_ID = 2L;
    static final Long SUBJECT_ID = 3L;
    static final Long FACULTY_ID = 4L;
    static final Long TERM_ID = 5L;
    static final Long LAB_ID = 6L;
    static final Long ACADEMIC_YEAR_ID = 10L;

    static SchedulingRequest batchRequest(LocalTime start, LocalTime end) {
        return batchRequest(start, end, null);
    }

    static SchedulingRequest batchRequest(LocalTime start, LocalTime end, SchedulingActor actor) {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, DIVISION_ID, BATCH_ID, SUBJECT_ID, FACULTY_ID, TERM_ID, MONDAY, start, end, actor);
    }

    static SchedulingRequest divisionRequest(LocalTime start, LocalTime end) {
        return divisionRequest(start, end, null);
    }

    static SchedulingRequest divisionRequest(LocalTime start, LocalTime end, SchedulingActor actor) {
        return new SchedulingRequest(
                AllocationType.REGULAR, TargetType.DIVISION, DIVISION_ID, null, SUBJECT_ID, FACULTY_ID, TERM_ID, MONDAY, start, end, actor);
    }

    static SubjectRef subjectRef() {
        return subjectRef(null);
    }

    static SubjectRef subjectRef(Long requiredLabTypeId) {
        return subjectRef(ACADEMIC_YEAR_ID, requiredLabTypeId);
    }

    static SubjectRef subjectRef(Long academicYearId, Long requiredLabTypeId) {
        return new SubjectRef(SUBJECT_ID, "BDA", "Big Data Analytics", academicYearId, requiredLabTypeId);
    }

    static FacultyRef facultyRef() {
        return new FacultyRef(FACULTY_ID, "FAC-BDA", "Faculty BDA", true);
    }

    static DivisionRef divisionRef() {
        return divisionRef(ACADEMIC_YEAR_ID);
    }

    static DivisionRef divisionRef(Long academicYearId) {
        return new DivisionRef(DIVISION_ID, "A", 68, academicYearId);
    }

    static BatchRef batchRef() {
        return batchRef(DIVISION_ID);
    }

    static BatchRef batchRef(Long divisionId) {
        return new BatchRef(BATCH_ID, "A1", 23, divisionId);
    }

    /** A BATCH-targeted context with no existing allocations, default (matching) academic year/lab type. */
    static SchedulingContext batchContext(SchedulingRequest request) {
        return new SchedulingContext(request, subjectRef(), facultyRef(), divisionRef(), batchRef(), List.of(), List.of(), List.of());
    }

    /** A DIVISION-targeted context with no existing allocations. */
    static SchedulingContext divisionContext(SchedulingRequest request) {
        return new SchedulingContext(request, subjectRef(), facultyRef(), divisionRef(), null, List.of(), List.of(), List.of());
    }

    static SchedulingContext batchContextWith(
            SchedulingRequest request,
            List<ExistingAllocationSnapshot> facultyAllocs,
            List<ExistingAllocationSnapshot> batchAllocs,
            List<ExistingAllocationSnapshot> divisionAllocs) {
        return new SchedulingContext(request, subjectRef(), facultyRef(), divisionRef(), batchRef(), facultyAllocs, batchAllocs, divisionAllocs);
    }

    static SchedulingContext divisionContextWith(
            SchedulingRequest request, List<ExistingAllocationSnapshot> facultyAllocs, List<ExistingAllocationSnapshot> divisionAllocs) {
        return new SchedulingContext(request, subjectRef(), facultyRef(), divisionRef(), null, facultyAllocs, List.of(), divisionAllocs);
    }

    static ExistingAllocationSnapshot existing(
            Long allocationId, Long labId, Long facultyId, Long divisionId, Long batchId, TargetType targetType, LocalTime start, LocalTime end) {
        return existing(allocationId, labId, facultyId, divisionId, batchId, targetType, MONDAY, start, end);
    }

    static ExistingAllocationSnapshot existing(
            Long allocationId,
            Long labId,
            Long facultyId,
            Long divisionId,
            Long batchId,
            TargetType targetType,
            LocalDate date,
            LocalTime start,
            LocalTime end) {
        return new ExistingAllocationSnapshot(allocationId, labId, "LAB-X", facultyId, divisionId, batchId, targetType, date, start, end);
    }

    static LabRef lab() {
        return lab(70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    static LabRef lab(int capacity) {
        return lab(capacity, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    static LabRef lab(
            int capacity,
            Long labTypeId,
            String labTypeCode,
            Set<String> softwareCodes,
            Map<String, Integer> equipmentQuantities,
            List<ExistingAllocationSnapshot> existingAllocations,
            List<com.college.laballocation.scheduling.InstantRange> unavailabilityWindows) {
        return new LabRef(
                LAB_ID, "C-301", true, capacity, labTypeId, labTypeCode, softwareCodes, equipmentQuantities, existingAllocations,
                unavailabilityWindows);
    }

    static LabRef inactiveLab() {
        return new LabRef(LAB_ID, "C-301", false, 70, 20L, "COMPUTER", Set.of(), Map.of(), List.of(), List.of());
    }

    static CandidateAllocation candidate(SchedulingContext context) {
        return new CandidateAllocation(context, lab());
    }

    static CandidateAllocation candidate(SchedulingContext context, LabRef lab) {
        return new CandidateAllocation(context, lab);
    }
}
