package com.college.laballocation.scheduling.alternative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingActor;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import com.college.laballocation.scheduling.conflict.ConflictAnalyzer;
import com.college.laballocation.scheduling.explanation.AllocationRecommendation;
import com.college.laballocation.scheduling.explanation.ExplainableAllocationService;
import com.college.laballocation.scheduling.explanation.ExplainedValidCandidate;
import com.college.laballocation.scheduling.explanation.RecommendationStatus;
import com.college.laballocation.scheduling.explanation.RejectedCandidateExplanation;
import com.college.laballocation.scheduling.explanation.RejectionSummary;
import com.college.laballocation.scheduling.explanation.ViolationExplanation;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlternativeSuggestionServiceTest {

    @Mock
    private ExplainableAllocationService explainableAllocationService;

    private final ConflictAnalyzer conflictAnalyzer = new ConflictAnalyzer();
    private AlternativeSuggestionService service;

    @BeforeEach
    void setUp() {
        SchedulingSlotPolicy policy =
                new SchedulingSlotPolicy("09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", 3, 6, 3, 120);
        service = new AlternativeSuggestionService(explainableAllocationService, conflictAnalyzer, new SchedulingSlotProvider(policy), policy);
    }

    private SchedulingRequest request() {
        return request(null);
    }

    private SchedulingRequest request(SchedulingActor actor) {
        return new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0), actor);
    }

    private ExplainedValidCandidate candidate(String labCode, double normalizedScore) {
        return new ExplainedValidCandidate(1L, labCode, 1, normalizedScore * 60, 60, normalizedScore, List.of(), List.of());
    }

    private AllocationRecommendation recommended(SchedulingRequest req, ExplainedValidCandidate candidate) {
        return new AllocationRecommendation(
                req, RecommendationStatus.RECOMMENDED, candidate, List.of(candidate), List.of(),
                new RejectionSummary(0, Map.of()), List.of("Satisfies all applicable hard constraints."), 1, 1, 0);
    }

    private AllocationRecommendation noValid(SchedulingRequest req, List<RejectedCandidateExplanation> rejected) {
        return new AllocationRecommendation(
                req, RecommendationStatus.NO_VALID_CANDIDATE, null, List.of(), rejected, RejectionSummary.from(rejected),
                List.of("No valid laboratory satisfies all hard constraints."), rejected.size(), 0, rejected.size());
    }

    private RejectedCandidateExplanation rejected(Long labId, String labCode, String... errorCodes) {
        List<ViolationExplanation> violations =
                List.of(errorCodes).stream().map(code -> new ViolationExplanation(code, code, "x", "LAB", labCode, Map.of())).toList();
        return new RejectedCandidateExplanation(labId, labCode, violations);
    }

    @Test
    void alreadyRecommendedRequestNeedsNoAlternativeSearch() {
        SchedulingRequest req = request();
        when(explainableAllocationService.recommend(any())).thenReturn(recommended(req, candidate("C-202", 0.8)));

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_NOT_NEEDED);
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.slotsSearched()).isEqualTo(0);
    }

    @Test
    void structurallyImpossibleRequestNeverTriggersTimeSearch() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(
                rejected(1L, "A-101", "CAPACITY_VIOLATION"), rejected(2L, "B-201", "CAPACITY_VIOLATION")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.NO_ALTERNATIVE_FOUND);
        assertThat(result.slotsSearched()).isEqualTo(0);
        // recommend() must only have been called once (for the original request) - no alternative slot was ever evaluated.
        org.mockito.Mockito.verify(explainableAllocationService, org.mockito.Mockito.times(1)).recommend(any());
    }

    @Test
    void temporalFailureTriggersAlternativeTimeSearch() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req)))
                .thenAnswer(inv -> noValid(inv.getArgument(0), List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE"))));

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.slotsSearched()).isGreaterThan(0);
    }

    @Test
    void mixedStructuralAndTemporalCandidatesStillSearches() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(
                rejected(1L, "A-101", "SOFTWARE_MISMATCH"),
                rejected(2L, "B-201", "SOFTWARE_MISMATCH"),
                rejected(3L, "C-301", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req)))
                .thenAnswer(inv -> noValid(inv.getArgument(0), List.of(rejected(3L, "C-301", "FACULTY_UNAVAILABLE"))));

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.slotsSearched()).isGreaterThan(0);
    }

    @Test
    void actorIsPreservedInEveryAlternativeRequest() {
        SchedulingActor crActor = new SchedulingActor(42L, UserRole.CR);
        SchedulingRequest req = request(crActor);
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req))).thenAnswer(inv -> {
            SchedulingRequest altReq = inv.getArgument(0);
            assertThat(altReq.actor()).isEqualTo(crActor);
            return noValid(altReq, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        });

        service.findAlternatives(req);
    }

    @Test
    void durationIsPreservedInEveryAlternativeRequest() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req))).thenAnswer(inv -> {
            SchedulingRequest altReq = inv.getArgument(0);
            assertThat(java.time.Duration.between(altReq.startTime(), altReq.endTime())).isEqualTo(java.time.Duration.ofHours(2));
            return noValid(altReq, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        });

        service.findAlternatives(req);
    }

    @Test
    void noValidAlternativeAcrossAllSearchedSlotsReturnsNoAlternativeFound() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req)))
                .thenAnswer(inv -> noValid(inv.getArgument(0), List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE"))));

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.NO_ALTERNATIVE_FOUND);
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.slotsSearched()).isGreaterThan(0);
    }

    @Test
    void resultIsBoundedByMaxAlternativeSuggestions() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req)))
                .thenAnswer(inv -> recommended(inv.getArgument(0), candidate("A-101", 0.5)));

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.status()).isEqualTo(AlternativeSearchStatus.ALTERNATIVES_FOUND);
        assertThat(result.suggestions()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void closerTimeDisplacementRanksAboveHigherScoreFartherAway() {
        SchedulingRequest req = request();
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req))).thenAnswer(inv -> {
            SchedulingRequest altReq = inv.getArgument(0);
            // Closest same-day slot (10:00) gets a LOW score; a farther slot (17:00) gets a HIGH score -
            // closer time must still rank first (Priority 2 outranks Priority 3).
            double score = altReq.startTime().equals(LocalTime.of(10, 0)) ? 0.2 : 0.9;
            return recommended(altReq, candidate("LAB-" + altReq.startTime(), score));
        });

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.suggestions().get(0).startTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void tiedTimeDisplacementAndScoreBreaksByLabCode() {
        // A request starting at 13:00 makes 11:00 (-120min) and 15:00 (+120min) equidistant -
        // a genuine tie in both dayOffset and |displacement|, which only Priority 4 (lab code) can break.
        SchedulingRequest req = new SchedulingRequest(
                AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L,
                LocalDate.of(2026, 8, 24), LocalTime.of(13, 0), LocalTime.of(15, 0), null);
        AllocationRecommendation original = noValid(req, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        when(explainableAllocationService.recommend(req)).thenReturn(original);
        when(explainableAllocationService.recommend(org.mockito.ArgumentMatchers.argThat(r -> r != req))).thenAnswer(inv -> {
            SchedulingRequest altReq = inv.getArgument(0);
            if (altReq.startTime().equals(LocalTime.of(11, 0))) {
                return recommended(altReq, candidate("Z-999", 0.5));
            }
            if (altReq.startTime().equals(LocalTime.of(15, 0))) {
                return recommended(altReq, candidate("A-001", 0.5));
            }
            return noValid(altReq, List.of(rejected(1L, "A-101", "FACULTY_UNAVAILABLE")));
        });

        AlternativeSearchResult result = service.findAlternatives(req);

        assertThat(result.suggestions()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.suggestions().get(0).recommendedCandidate().labCode()).isEqualTo("A-001");
        assertThat(result.suggestions().get(1).recommendedCandidate().labCode()).isEqualTo("Z-999");
    }
}
