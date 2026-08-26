package com.college.laballocation.scheduling;

import com.college.laballocation.scheduling.ScheduleVersionDtos.AllocationSummaryResponse;
import com.college.laballocation.scheduling.ScheduleVersionDtos.CreateScheduleVersionRequest;
import com.college.laballocation.scheduling.ScheduleVersionDtos.ScheduleVersionHistoryResponse;
import com.college.laballocation.scheduling.ScheduleVersionDtos.ScheduleVersionResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab-Assistant-only timetable version lifecycle management (Phase 18) -
 * draft creation, publication, version history, and per-version allocation
 * inspection. Kept at {@code /api/schedule-versions} (resource-named, not
 * role-prefixed), matching this project's established URI convention and
 * the same reasoning as Phase 17's {@code /api/audit-logs} (ADR-085,
 * docs/15-DESIGN-DECISIONS.md) - authorization is enforced by
 * {@code @PreAuthorize}, never encoded into the path.
 *
 * <p>Student/CR read access to the <em>current published</em> timetable is a
 * separate, differently-scoped concern - see {@link TimetableController}.
 */
@RestController
@RequestMapping("/api/schedule-versions")
public class ScheduleVersionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScheduleVersionService scheduleVersionService;

    public ScheduleVersionController(ScheduleVersionService scheduleVersionService) {
        this.scheduleVersionService = scheduleVersionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ScheduleVersionResponse createDraft(
            @Valid @RequestBody CreateScheduleVersionRequest request, @AuthenticationPrincipal Long userId) {
        ScheduleVersion version = scheduleVersionService.createDraft(request.academicTermId(), request.reason(), userId);
        return scheduleVersionService.getVersion(version.getId());
    }

    @PostMapping("/{versionId}/publish")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ScheduleVersionResponse publish(@PathVariable Long versionId, @AuthenticationPrincipal Long userId) {
        scheduleVersionService.publish(versionId, userId);
        return scheduleVersionService.getVersion(versionId);
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ScheduleVersionHistoryResponse history(@RequestParam Long academicTermId) {
        return scheduleVersionService.getHistory(academicTermId);
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ScheduleVersionResponse get(@PathVariable Long versionId) {
        return scheduleVersionService.getVersion(versionId);
    }

    @GetMapping("/{versionId}/allocations")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public Page<AllocationSummaryResponse> allocations(
            @PathVariable Long versionId,
            @RequestParam(required = false) Long divisionId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) LocalDate date,
            @PageableDefault(size = 20, sort = "allocationDate", direction = Sort.Direction.ASC) Pageable pageable) {
        Pageable capped = pageable.getPageSize() > MAX_PAGE_SIZE
                ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
                : pageable;
        return scheduleVersionService.getVersionAllocations(versionId, divisionId, batchId, subjectId, facultyId, labId, date, capped);
    }
}
