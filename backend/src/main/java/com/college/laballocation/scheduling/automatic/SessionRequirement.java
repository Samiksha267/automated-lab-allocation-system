package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.alternative.TimeSlot;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * "What must be scheduled?" - deliberately distinct from {@link SchedulingRequest}
 * ("evaluate this session at this concrete date/time"), per PART 9/10 of
 * the Phase 14 brief. {@code SchedulingRequest}'s invariants (Phase 8) are
 * not weakened to accommodate an unknown date/time - a {@code SessionRequirement}
 * has none yet; {@link #toRequest} converts one plus a chosen slot into a
 * real, fully-valid {@code SchedulingRequest} only once a concrete slot is
 * being evaluated.
 *
 * <p>Duration is deliberately absent here - Phase 13's confirmed fixed
 * 2-hour session policy ({@code SchedulingSlotPolicy}) is the single source
 * of duration for automatic scheduling (PART 11); introducing a second,
 * per-requirement duration concept was explicitly avoided.
 *
 * <p>{@code key} is caller-supplied and must be unique within one
 * {@link AutomaticSchedulingRequest} (PART 46/47) - it is how a returned
 * {@link PlannedAllocation}/{@link UnscheduledRequirement} maps back to the
 * input that produced it, never a list index.
 */
public record SessionRequirement(
        String key,
        AllocationType allocationType,
        TargetType targetType,
        Long divisionId,
        Long batchId,
        Long subjectId,
        Long facultyId,
        Long academicTermId,
        SchedulingActor actor) {

    public SessionRequirement {
        if (key == null || key.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A SessionRequirement key must not be blank.");
        }
        Objects.requireNonNull(allocationType, "allocationType must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(divisionId, "divisionId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(facultyId, "facultyId must not be null");
        Objects.requireNonNull(academicTermId, "academicTermId must not be null");
        // batchId/targetType coherence and the time-interval check both belong to SchedulingRequest's own
        // compact constructor (Phase 8) - re-validated automatically the moment toRequest(...) builds one.
    }

    /** Combines this requirement with a chosen {@link TimeSlot} into a real, fully-validated {@link SchedulingRequest}. */
    public SchedulingRequest toRequest(TimeSlot slot) {
        return new SchedulingRequest(
                allocationType, targetType, divisionId, batchId, subjectId, facultyId, academicTermId,
                slot.date(), slot.startTime(), slot.endTime(), actor);
    }
}
