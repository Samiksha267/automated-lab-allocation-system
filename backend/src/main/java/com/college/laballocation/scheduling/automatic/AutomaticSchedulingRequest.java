package com.college.laballocation.scheduling.automatic;

import com.college.laballocation.common.ApiException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * One automatic-scheduling call - a bounded set of {@link SessionRequirement}s
 * to place somewhere inside {@code [startDate, endDate]} (PART 13/14 of the
 * Phase 14 brief). The date range is always explicit and caller-supplied -
 * this class never calls {@code LocalDate.now()} or otherwise infers
 * "this week" on its own (PART 14).
 *
 * <p>Structural invariants only are enforced here (date order, non-null,
 * unique requirement keys) - the configured bounds
 * ({@code AutomaticSchedulingConfiguration.maxRequirements()}/{@code maxDateRangeDays()})
 * are enforced by {@code AutomaticSchedulingEngine}, since they are tunable
 * operational limits, not permanent domain facts (the same split
 * {@code SchedulingRequest} draws between its own structural compact-constructor
 * checks and configuration-dependent checks elsewhere).
 */
public record AutomaticSchedulingRequest(List<SessionRequirement> requirements, LocalDate startDate, LocalDate endDate) {

    public AutomaticSchedulingRequest {
        if (requirements == null) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "requirements must not be null (an empty list is valid).");
        }
        if (startDate == null || endDate == null) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "startDate and endDate are both required.");
        }
        if (startDate.isAfter(endDate)) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "startDate (" + startDate + ") must not be after endDate (" + endDate + ").");
        }
        requirements = List.copyOf(requirements);

        Set<String> seenKeys = new HashSet<>();
        for (SessionRequirement requirement : requirements) {
            if (!seenKeys.add(requirement.key())) {
                throw new ApiException(
                        "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                        "Duplicate SessionRequirement key '" + requirement.key() + "' - every requirement key must be unique within one automatic scheduling request.");
            }
        }
    }
}
