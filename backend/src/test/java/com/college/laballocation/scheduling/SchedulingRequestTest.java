package com.college.laballocation.scheduling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.college.laballocation.common.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Pure domain object - no Spring context, no database (NFR-08). */
class SchedulingRequestTest {

    private static SchedulingRequest request(TargetType targetType, Long batchId, LocalTime start, LocalTime end) {
        return new SchedulingRequest(
                AllocationType.EXTRA, targetType, 1L, batchId, 2L, 3L, 4L, LocalDate.of(2026, 8, 24), start, end);
    }

    @Test
    void batchTargetedRequestRequiresBatchId() {
        assertThatThrownBy(() -> request(TargetType.BATCH, null, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void divisionTargetedRequestRejectsBatchId() {
        assertThatThrownBy(() -> request(TargetType.DIVISION, 5L, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void invalidTimeRangeIsRejected() {
        assertThatThrownBy(() -> request(TargetType.DIVISION, null, LocalTime.of(11, 0), LocalTime.of(9, 0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ALLOCATION_INTERVAL");
    }

    @Test
    void validBatchRequestIsAccepted() {
        assertThatCode(() -> request(TargetType.BATCH, 5L, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void validDivisionRequestIsAccepted() {
        assertThatCode(() -> request(TargetType.DIVISION, null, LocalTime.of(9, 0), LocalTime.of(11, 0)))
                .doesNotThrowAnyException();
    }
}
