package com.college.laballocation.scheduling.extra;

import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.alternative.AlternativeSearchResult;
import com.college.laballocation.scheduling.alternative.AlternativeSuggestion;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import com.college.laballocation.scheduling.explanation.RejectedCandidateExplanation;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import com.college.laballocation.scheduling.scoring.ScoreContribution;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Wire-level DTOs for the CR-facing EXTRA lab workflow (Phase 15). Every
 * response type here is a stable API shape that maps from - and never
 * exposes - an internal scheduling-engine record directly (PART 42 of the
 * phase brief): a future refactor of {@code AllocationRecommendation}'s
 * internals must never silently change this API's contract.
 *
 * <p>Deliberately no {@code divisionId}, {@code facultyId}, or
 * {@code academicTermId} field on either request - all three are resolved
 * server-side from the authenticated CR's current {@code CrAssignment}
 * (division, term) and {@code FacultyAssignmentResolutionService} (faculty),
 * never accepted from the client (PART 2/5/6/59 - "prefer omitting it
 * entirely if unnecessary").
 */
public final class ExtraLabDtos {
    private ExtraLabDtos() {}

    public record ExtraLabSearchRequest(
            @NotNull Long subjectId,
            @NotNull TargetType targetType,
            Long batchId,
            @NotNull LocalDate allocationDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime) {}

    /** Identical to {@link ExtraLabSearchRequest} plus the CR's concrete lab choice from a prior search. */
    public record ExtraLabBookingRequest(
            @NotNull Long subjectId,
            @NotNull TargetType targetType,
            Long batchId,
            @NotNull LocalDate allocationDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull Long labId) {}

    /** {@code reason} is optional - {@code null}/blank is normalized to no stored reason, never an empty string (PART 31). */
    public record ExtraLabCancelRequest(String reason) {}

    public record ExtraLabScoreFactorResponse(
            String factor, String applicability, double pointsAwarded, double maxPoints, String explanation) {
        static ExtraLabScoreFactorResponse from(ScoreContribution contribution) {
            return new ExtraLabScoreFactorResponse(
                    contribution.factor().toString(),
                    contribution.applicability().toString(),
                    contribution.pointsAwarded(),
                    contribution.maxPoints(),
                    contribution.explanation());
        }
    }

    public record ExtraLabViolationResponse(String errorCode, String label, String message) {
        static ExtraLabViolationResponse from(ViolationExplanation violation) {
            return new ExtraLabViolationResponse(violation.errorCode(), violation.displayLabel(), violation.message());
        }
    }

    public record ExtraLabCandidateResponse(
            Long labId,
            String labCode,
            int rank,
            double score,
            double maxScore,
            double normalizedScore,
            List<ExtraLabScoreFactorResponse> scoreFactors) {
        static ExtraLabCandidateResponse from(ExplainedValidCandidate candidate) {
            return new ExtraLabCandidateResponse(
                    candidate.labId(),
                    candidate.labCode(),
                    candidate.rank(),
                    candidate.score(),
                    candidate.applicableMaxScore(),
                    candidate.normalizedScore(),
                    candidate.scoreContributions().stream().map(ExtraLabScoreFactorResponse::from).toList());
        }
    }

    public record ExtraLabRejectedCandidateResponse(Long labId, String labCode, List<ExtraLabViolationResponse> violations) {
        static ExtraLabRejectedCandidateResponse from(RejectedCandidateExplanation rejected) {
            return new ExtraLabRejectedCandidateResponse(
                    rejected.labId(), rejected.labCode(), rejected.violations().stream().map(ExtraLabViolationResponse::from).toList());
        }
    }

    public record ExtraLabAlternativeResponse(
            String type,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Long labId,
            String labCode,
            double normalizedScore,
            String explanation) {
        static ExtraLabAlternativeResponse from(AlternativeSuggestion suggestion) {
            return new ExtraLabAlternativeResponse(
                    suggestion.type().toString(),
                    suggestion.date(),
                    suggestion.startTime(),
                    suggestion.endTime(),
                    suggestion.recommendedCandidate().labId(),
                    suggestion.recommendedCandidate().labCode(),
                    suggestion.recommendedCandidate().normalizedScore(),
                    suggestion.explanation());
        }
    }

    /**
     * The search response - demonstrates the project's core explainability
     * feature (PART 41): a full ranked/rejected breakdown for the requested
     * slot, plus Phase 13 alternative-time suggestions when the requested
     * slot has no valid candidate but changing the time could still help.
     */
    public record ExtraLabSearchResponse(
            String recommendationStatus,
            ExtraLabCandidateResponse recommendedLab,
            List<ExtraLabCandidateResponse> rankedValidLabs,
            List<ExtraLabRejectedCandidateResponse> rejectedLabs,
            List<String> summary,
            String alternativeStatus,
            List<ExtraLabAlternativeResponse> alternatives) {

        static ExtraLabSearchResponse from(AlternativeSearchResult result) {
            AllocationRecommendation original = result.originalRecommendation();
            return new ExtraLabSearchResponse(
                    original.status().toString(),
                    original.recommendedCandidate() == null ? null : ExtraLabCandidateResponse.from(original.recommendedCandidate()),
                    original.rankedValidCandidates().stream().map(ExtraLabCandidateResponse::from).toList(),
                    original.rejectedCandidates().stream().map(ExtraLabRejectedCandidateResponse::from).toList(),
                    original.summary(),
                    result.status().toString(),
                    result.suggestions().stream().map(ExtraLabAlternativeResponse::from).toList());
        }
    }

    /**
     * Stable persisted-allocation shape - used for the booking response, the
     * cancellation response, and every history/activity listing, so a caller
     * sees one consistent shape everywhere an {@code Allocation} is returned
     * (PART 22/63). {@code cancelledByUserId}/{@code cancelledAt}/
     * {@code cancellationReason} are always present but {@code null} unless
     * the allocation has actually been cancelled - never a second, separate
     * DTO for the cancelled case.
     */
    public record ExtraLabAllocationResponse(
            Long allocationId,
            String allocationType,
            String status,
            String targetType,
            Long subjectId,
            String subjectCode,
            Long facultyId,
            String facultyName,
            Long labId,
            String labCode,
            Long divisionId,
            String divisionCode,
            Long batchId,
            String batchCode,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime,
            Long scheduleVersionId,
            Long createdByUserId,
            Instant createdAt,
            Long cancelledByUserId,
            Instant cancelledAt,
            String cancellationReason) {

        static ExtraLabAllocationResponse from(Allocation allocation) {
            return new ExtraLabAllocationResponse(
                    allocation.getId(),
                    allocation.getAllocationType().toString(),
                    allocation.getStatus().toString(),
                    allocation.getTargetType().toString(),
                    allocation.getSubject().getId(),
                    allocation.getSubject().getCode(),
                    allocation.getFaculty().getId(),
                    allocation.getFaculty().getName(),
                    allocation.getLab().getId(),
                    allocation.getLab().getCode(),
                    allocation.getDivision().getId(),
                    allocation.getDivision().getCode(),
                    allocation.getBatch() != null ? allocation.getBatch().getId() : null,
                    allocation.getBatch() != null ? allocation.getBatch().getCode() : null,
                    allocation.getAllocationDate(),
                    allocation.getStartTime(),
                    allocation.getEndTime(),
                    allocation.getScheduleVersion().getId(),
                    allocation.getCreatedBy().getId(),
                    allocation.getCreatedAt(),
                    allocation.getCancelledBy() != null ? allocation.getCancelledBy().getId() : null,
                    allocation.getCancelledAt(),
                    allocation.getCancellationReason());
        }
    }
}
