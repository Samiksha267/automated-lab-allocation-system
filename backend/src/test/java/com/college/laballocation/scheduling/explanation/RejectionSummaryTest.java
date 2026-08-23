package com.college.laballocation.scheduling.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RejectionSummaryTest {

    private RejectedCandidateExplanation candidate(String labCode, String... errorCodes) {
        List<ViolationExplanation> violations = List.of(errorCodes).stream()
                .map(code -> new ViolationExplanation(code, code, "x", "LAB", labCode, Map.of()))
                .toList();
        return new RejectedCandidateExplanation(1L, labCode, violations);
    }

    @Test
    void oneCandidateContributesToMultipleReasonCounts() {
        RejectionSummary summary =
                RejectionSummary.from(List.of(candidate("A-101", "SOFTWARE_MISMATCH"), candidate("B-201", "SOFTWARE_MISMATCH", "CAPACITY_VIOLATION")));

        assertThat(summary.rejectedCount()).isEqualTo(2);
        assertThat(summary.countByErrorCode()).containsEntry("SOFTWARE_MISMATCH", 2).containsEntry("CAPACITY_VIOLATION", 1);
    }

    @Test
    void sumOfReasonCountsCanExceedRejectedCandidateCount() {
        RejectionSummary summary = RejectionSummary.from(List.of(candidate("B-201", "SOFTWARE_MISMATCH", "CAPACITY_VIOLATION")));

        int sumOfCounts = summary.countByErrorCode().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(sumOfCounts).isGreaterThan(summary.rejectedCount());
    }

    @Test
    void emptyRejectionListProducesZeroSummary() {
        RejectionSummary summary = RejectionSummary.from(List.of());

        assertThat(summary.rejectedCount()).isEqualTo(0);
        assertThat(summary.countByErrorCode()).isEmpty();
        assertThat(summary.mostCommonReasons()).isEmpty();
    }

    @Test
    void mostCommonReasonsReturnsAllTiedCodes() {
        RejectionSummary summary =
                RejectionSummary.from(List.of(candidate("A-101", "SOFTWARE_MISMATCH"), candidate("B-201", "CAPACITY_VIOLATION")));

        assertThat(summary.mostCommonReasons()).containsExactlyInAnyOrder("SOFTWARE_MISMATCH", "CAPACITY_VIOLATION");
    }
}
