package com.college.laballocation.scheduling;

import com.college.laballocation.scheduling.ScheduleVersionDtos.AllocationSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student/CR/Lab-Assistant read access to the <em>current published</em>
 * timetable for a term (Phase 18, PART 21/22/23). Deliberately separate from
 * {@link ScheduleVersionController} (Lab-Assistant-only version lifecycle
 * management + full history/any-status inspection) - this endpoint can never
 * return a DRAFT or SUPERSEDED version's rows, by construction
 * ({@code AllocationSpecifications.currentlyPublishedForTerm} always joins
 * on {@code status = PUBLISHED}, never a caller-suppliable filter).
 *
 * <p>No per-student/per-CR "own division" resolution exists here the way
 * {@code CrOwnershipService} resolves it for the CR EXTRA-booking workflow:
 * this project has no {@code Student} enrollment entity associating a
 * STUDENT account with a division/batch (Phase 4 never introduced one), and
 * a CR's own division is already independently reachable via
 * {@code GET /api/cr-assignments/me} - so both roles filter the same way
 * anyone else does, via explicit {@code divisionId}/{@code batchId} query
 * parameters, matching FR-27's documented filter shape.
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScheduleVersionService scheduleVersionService;

    public TimetableController(ScheduleVersionService scheduleVersionService) {
        this.scheduleVersionService = scheduleVersionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'CR', 'LAB_ASSISTANT')")
    public Page<AllocationSummaryResponse> currentPublished(
            @RequestParam Long academicTermId,
            @RequestParam(required = false) Long divisionId,
            @RequestParam(required = false) Long batchId,
            @PageableDefault(size = 20, sort = "allocationDate", direction = Sort.Direction.ASC) Pageable pageable) {
        Pageable capped = pageable.getPageSize() > MAX_PAGE_SIZE
                ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
                : pageable;
        return scheduleVersionService.getPublishedTimetable(academicTermId, divisionId, batchId, capped);
    }
}
