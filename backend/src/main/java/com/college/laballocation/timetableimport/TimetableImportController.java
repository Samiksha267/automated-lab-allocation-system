package com.college.laballocation.timetableimport;

import com.college.laballocation.timetableimport.TimetableImportDtos.ApproveResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportDetailResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportRowResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.RowCorrectionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lab-Assistant-only PDF timetable import pipeline (Phase 19) - upload,
 * review, correction, approval, rejection. Thin: every method delegates
 * immediately to {@link TimetableImportService} (PART 23 - controllers stay
 * thin, business/authorization/audit logic lives in the service layer).
 */
@RestController
@RequestMapping("/api/timetable-imports")
public class TimetableImportController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TimetableImportService timetableImportService;

    public TimetableImportController(TimetableImportService timetableImportService) {
        this.timetableImportService = timetableImportService;
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ImportResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long academicTermId,
            @RequestParam Long scheduleVersionId,
            @AuthenticationPrincipal Long userId) {
        return timetableImportService.upload(academicTermId, scheduleVersionId, file, userId);
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public Page<ImportResponse> list(
            @RequestParam(required = false) Long academicTermId,
            @RequestParam(required = false) Long scheduleVersionId,
            @RequestParam(required = false) TimetableImportStatus status,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return timetableImportService.list(academicTermId, scheduleVersionId, status, cap(pageable));
    }

    @GetMapping("/{importId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ImportDetailResponse detail(
            @PathVariable Long importId,
            @PageableDefault(size = 20, sort = "rowNumber") Pageable pageable) {
        return timetableImportService.getDetail(importId, cap(pageable));
    }

    @PatchMapping("/{importId}/rows/{rowId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ImportRowResponse correctRow(
            @PathVariable Long importId, @PathVariable Long rowId, @RequestBody RowCorrectionRequest request) {
        return timetableImportService.correctRow(importId, rowId, request);
    }

    @PostMapping("/{importId}/approve")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ApproveResponse approve(@PathVariable Long importId, @AuthenticationPrincipal Long userId) {
        return timetableImportService.approve(importId, userId);
    }

    @PostMapping("/{importId}/reject")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public ImportResponse reject(@PathVariable Long importId, @AuthenticationPrincipal Long userId) {
        return timetableImportService.reject(importId, userId);
    }

    private Pageable cap(Pageable pageable) {
        return pageable.getPageSize() > MAX_PAGE_SIZE ? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort()) : pageable;
    }
}
