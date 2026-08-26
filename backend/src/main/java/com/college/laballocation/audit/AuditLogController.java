package com.college.laballocation.audit;

import com.college.laballocation.audit.AuditLogDtos.AuditLogResponse;
import com.college.laballocation.audit.AuditLogDtos.AuditLogSearchCriteria;
import java.time.Instant;
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
 * Lab-Assistant-only cross-domain activity history (PART 32/37 of the phase
 * brief; FR-33). No mutation endpoint exists here or anywhere in this
 * package (PART 3) - {@code AuditLog} rows are read-only from the API's
 * perspective. Pagination is mandatory (PART 33): unbounded audit history is
 * never returned in one response.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    /** PART 18 - audit history grows without bound; no request may pull more than this many rows in one page. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditResourceType resourceType,
            @RequestParam(required = false) Long academicTermId,
            @RequestParam(required = false) Long divisionId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AuditLogSearchCriteria criteria =
                new AuditLogSearchCriteria(actorUserId, action, resourceType, academicTermId, divisionId, from, to);
        Pageable capped = pageable.getPageSize() > MAX_PAGE_SIZE
                ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
                : pageable;
        return auditLogService.search(criteria, capped);
    }
}
