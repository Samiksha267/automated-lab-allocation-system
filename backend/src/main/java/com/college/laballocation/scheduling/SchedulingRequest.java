package com.college.laballocation.scheduling;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.TimeIntervalUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;

/**
 * The requested session before a lab is chosen - decoupled from JPA/HTTP
 * (NFR-08), so it can be constructed and validated with no Spring context or
 * database. {@code facultyId} is always already resolved by the time this
 * object exists (PART 15 of the Phase 8 brief): the preferred architecture
 * is
 *
 * <pre>external scheduling input -&gt; resolve academic/faculty context (FacultyAssignmentResolutionService, Phase 4) -&gt; SchedulingRequest</pre>
 *
 * so the future constraint engine (Phase 9) always receives an unambiguous
 * request - it never needs to itself decide "which faculty teaches this,"
 * that ambiguity-resolution step belongs upstream, one layer before
 * scheduling even begins.
 *
 * <p>Validates only what needs no database: the target/batch structural
 * invariant (a {@code BATCH} request needs a {@code batchId}, a
 * {@code DIVISION} request must not have one) and the time interval. The
 * cross-table "does this batch actually belong to this division" check
 * requires loaded entities and lives on {@link Allocation}'s factory methods
 * instead, once a candidate is actually being persisted.
 */
public record SchedulingRequest(
        AllocationType allocationType,
        TargetType targetType,
        Long divisionId,
        Long batchId,
        Long subjectId,
        Long facultyId,
        Long academicTermId,
        LocalDate allocationDate,
        LocalTime startTime,
        LocalTime endTime) {

    public SchedulingRequest {
        if (targetType == TargetType.BATCH && batchId == null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A BATCH-targeted scheduling request requires a batchId.");
        }
        if (targetType == TargetType.DIVISION && batchId != null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A DIVISION-targeted scheduling request must not have a batchId.");
        }
        if (!TimeIntervalUtils.isValid(startTime, endTime)) {
            throw new ApiException(
                    "INVALID_ALLOCATION_INTERVAL", HttpStatus.BAD_REQUEST, "startTime must be strictly before endTime.");
        }
    }
}
