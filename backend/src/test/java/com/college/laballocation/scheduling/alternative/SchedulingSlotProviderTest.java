package com.college.laballocation.scheduling.alternative;

import static org.assertj.core.api.Assertions.assertThat;

import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.SchedulingRequest;
import com.college.laballocation.scheduling.TargetType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingSlotProviderTest {

    private SchedulingSlotPolicy policy(int maxSlotsSearched, int maxLookaheadDays) {
        return new SchedulingSlotPolicy(
                "09:00", "19:00", 60, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY", maxLookaheadDays, maxSlotsSearched, 3, 120);
    }

    private SchedulingRequest request(LocalDate date, LocalTime start, LocalTime end) {
        return new SchedulingRequest(AllocationType.EXTRA, TargetType.BATCH, 1L, 2L, 3L, 4L, 5L, date, start, end, null);
    }

    @Test
    void preservesRequestedDurationForEverySlot() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 3));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        assertThat(slots).allSatisfy(slot -> assertThat(Duration.between(slot.startTime(), slot.endTime())).isEqualTo(Duration.ofHours(2)));
    }

    @Test
    void neverRepeatsTheExactOriginallyRequestedSlot() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 3));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        assertThat(slots).noneMatch(s -> s.dayOffset() == 0 && s.startTime().equals(LocalTime.of(9, 0)));
    }

    @Test
    void sameDaySlotsOrderedByProximityToRequestedStart() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 0));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        assertThat(slots).extracting(CandidateSlot::startTime)
                .containsExactly(
                        LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
                        LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0));
    }

    @Test
    void sameDaySlotsComeBeforeLaterDaySlots() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 3));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        int lastSameDayIndex = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).dayOffset() == 0) {
                lastSameDayIndex = i;
            }
        }
        int firstOtherDayIndex = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).dayOffset() > 0) {
                firstOtherDayIndex = i;
                break;
            }
        }
        assertThat(lastSameDayIndex).isLessThan(firstOtherDayIndex);
    }

    @Test
    void resultIsBoundedByMaxAlternativeTimeSlotsSearched() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(3, 3));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        assertThat(slots).hasSize(3);
    }

    @Test
    void nonWorkingDayIsSkippedEntirelyWhenLookingAhead() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 3));
        // Saturday 2026-08-29; +1 day = Sunday 2026-08-30 (not a working day) must be skipped,
        // +2 = Monday 2026-08-31, +3 = Tuesday 2026-09-01 should both still appear.
        SchedulingRequest request = request(LocalDate.of(2026, 8, 29), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> slots = provider.generateCandidateSlots(request);

        assertThat(slots).noneMatch(s -> s.date().equals(LocalDate.of(2026, 8, 30)));
        assertThat(slots).anyMatch(s -> s.date().equals(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void deterministicAcrossRepeatedCalls() {
        SchedulingSlotProvider provider = new SchedulingSlotProvider(policy(20, 3));
        SchedulingRequest request = request(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), LocalTime.of(11, 0));

        List<CandidateSlot> first = provider.generateCandidateSlots(request);
        List<CandidateSlot> second = provider.generateCandidateSlots(request);

        assertThat(first).isEqualTo(second);
    }
}
