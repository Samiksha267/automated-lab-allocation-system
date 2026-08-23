package com.college.laballocation.scheduling.alternative;

import com.college.laballocation.scheduling.SchedulingRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Generates a deterministic, bounded list of candidate (date, start, end)
 * slots for alternative-time search - the requested session's exact
 * duration is always preserved (PART 10), the exact originally-requested
 * slot is never repeated, and the total returned size never exceeds
 * {@link SchedulingSlotPolicy#maxAlternativeTimeSlotsSearched()} (PART 28,
 * the search bound).
 *
 * <p><b>Ordering</b> (PART 11/12/29): same day first, in ascending
 * time-of-day distance from the originally requested start time (ties
 * broken by earlier absolute time); then day+1, day+2, ... up to
 * {@code maxLookaheadDays}, each day's own slots ordered the same way
 * internally. Non-working days ({@link SchedulingSlotPolicy#workingDays()})
 * are skipped entirely, consuming no slot budget. This ordering is a plain
 * deterministic sort, not database iteration order or a hash-based
 * collection, so identical input always produces an identical sequence.
 *
 * <p><b>Complexity</b> (PART 30): for {@code H} valid start hours per day
 * (9 here: 09-17) and {@code D = 1 + maxLookaheadDays} candidate days,
 * this generates at most {@code H * D} slots before capping to
 * {@code maxAlternativeTimeSlotsSearched} - a small, bounded set (at the
 * documented default of 6 slots searched, at most 6 full
 * generate-then-score pipeline runs occur per alternative search, each
 * itself O(labs) per Phase 10/11's own documented cost).
 */
@Component
public class SchedulingSlotProvider {

    private final SchedulingSlotPolicy policy;

    public SchedulingSlotProvider(SchedulingSlotPolicy policy) {
        this.policy = policy;
    }

    public List<CandidateSlot> generateCandidateSlots(SchedulingRequest request) {
        Duration duration = Duration.between(request.startTime(), request.endTime());
        List<CandidateSlot> ordered = new ArrayList<>();

        for (int dayOffset = 0; dayOffset <= policy.maxLookaheadDays(); dayOffset++) {
            LocalDate date = request.allocationDate().plusDays(dayOffset);
            if (!policy.workingDays().contains(date.getDayOfWeek())) {
                continue;
            }
            ordered.addAll(slotsForDay(request, date, dayOffset, duration));
            if (ordered.size() >= policy.maxAlternativeTimeSlotsSearched()) {
                break;
            }
        }

        return ordered.size() > policy.maxAlternativeTimeSlotsSearched()
                ? ordered.subList(0, policy.maxAlternativeTimeSlotsSearched())
                : ordered;
    }

    private List<CandidateSlot> slotsForDay(SchedulingRequest request, LocalDate date, int dayOffset, Duration duration) {
        List<CandidateSlot> daySlots = new ArrayList<>();
        for (LocalTime start = policy.dayStartTime();
                !start.plus(duration).isAfter(policy.dayEndTime());
                start = start.plusMinutes(policy.slotStepMinutes())) {
            if (dayOffset == 0 && start.equals(request.startTime())) {
                continue; // already evaluated (and rejected) by the original request - never re-searched.
            }
            LocalTime end = start.plus(duration);
            int displacement = Math.abs((int) Duration.between(request.startTime(), start).toMinutes());
            daySlots.add(new CandidateSlot(date, start, end, dayOffset, displacement));
        }
        daySlots.sort(Comparator.comparingInt(CandidateSlot::minutesFromOriginalStart).thenComparing(CandidateSlot::startTime));
        return daySlots;
    }
}
