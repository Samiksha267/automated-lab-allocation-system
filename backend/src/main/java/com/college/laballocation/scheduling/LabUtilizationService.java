package com.college.laballocation.scheduling;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only scheduled-load lookup for the Balanced Utilization scorer (Phase
 * 11) - deliberately just "how many minutes is this lab already scheduled
 * for," never a percentage, since no working-days/daily-operating-hours
 * concept exists anywhere in this project to divide by (PART 21/22 of the
 * Phase 11 brief; see docs/07-ALLOCATION-SCORING.md). Callers compare labs
 * to each other, not against an absolute utilization figure.
 *
 * <p>Scoped to the requesting academic term's currently
 * {@code PUBLISHED} {@link ScheduleVersion} only (PART 57) - a term with no
 * published version yet (e.g. before its first publish) has no basis for
 * comparison, so callers receive an empty result rather than a
 * fabricated zero. Both {@link AllocationType#REGULAR} and
 * {@link AllocationType#EXTRA} rows contribute (PART 25): either one
 * physically occupies the lab, and this factor measures physical load, not
 * timetable formality.
 */
@Service
@Transactional(readOnly = true)
public class LabUtilizationService {

    private static final Set<AllocationStatus> BLOCKING_STATUSES = Arrays.stream(AllocationStatus.values())
            .filter(AllocationStatus::blocksScheduling)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(AllocationStatus.class)));

    private static final Set<String> BLOCKING_STATUS_NAMES =
            BLOCKING_STATUSES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AllocationRepository allocationRepository;
    private final ScheduleVersionRepository scheduleVersionRepository;

    public LabUtilizationService(AllocationRepository allocationRepository, ScheduleVersionRepository scheduleVersionRepository) {
        this.allocationRepository = allocationRepository;
        this.scheduleVersionRepository = scheduleVersionRepository;
    }

    /**
     * Scheduled minutes per lab, for the given labs, within the term's
     * currently PUBLISHED schedule version. A lab with no allocations in
     * that version is simply absent from the returned map (zero load), not
     * an error. Returns {@link Optional#empty()} - not a present-but-empty
     * map - if the term has no PUBLISHED version at all, so callers can
     * distinguish "there is a version and every candidate happens to be
     * unloaded" (present, possibly empty map) from "there is no basis to
     * compare load at all" (empty Optional).
     */
    public Optional<Map<Long, Long>> scheduledMinutesByLab(Long academicTermId, Collection<Long> labIds) {
        Optional<ScheduleVersion> published =
                scheduleVersionRepository.findByAcademicTermIdAndStatus(academicTermId, ScheduleVersionStatus.PUBLISHED);
        if (published.isEmpty()) {
            return Optional.empty();
        }
        if (labIds.isEmpty()) {
            return Optional.of(Map.of());
        }
        List<AllocationRepository.LabLoadRow> rows =
                allocationRepository.sumScheduledMinutesByLab(published.get().getId(), labIds, BLOCKING_STATUS_NAMES);
        Map<Long, Long> minutesByLab = new HashMap<>();
        for (AllocationRepository.LabLoadRow row : rows) {
            minutesByLab.put(row.getLabId(), Math.round(row.getMinutes()));
        }
        return Optional.of(minutesByLab);
    }
}
