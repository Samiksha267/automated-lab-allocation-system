package com.college.laballocation.timetableimport;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.BatchService;
import com.college.laballocation.academic.DivisionService;
import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.faculty.FacultyService;
import com.college.laballocation.lab.LabService;
import com.college.laballocation.scheduling.Allocation;
import com.college.laballocation.scheduling.AllocationRepository;
import com.college.laballocation.scheduling.AllocationType;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.scheduling.ScheduleVersionService;
import com.college.laballocation.scheduling.ScheduleVersionStatus;
import com.college.laballocation.subject.SubjectService;
import com.college.laballocation.timetableimport.TimetableImportDtos.ApproveResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportDetailResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportRowResponse;
import com.college.laballocation.timetableimport.TimetableImportDtos.ImportSummary;
import com.college.laballocation.timetableimport.TimetableImportDtos.RowCorrectionRequest;
import com.college.laballocation.timetableimport.TimetableImportValidationService.ValidatedRow;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates the Phase 19 pipeline end to end - upload/extract/parse/map/
 * validate (synchronous, one request), review/correction, and the
 * approval trust-boundary transition. Deliberately thin on business logic
 * of its own (PART 65 - not a god class): extraction is
 * {@link PdfTextExtractor}, parsing is {@link TimetableParser}, mapping is
 * {@link TimetableMappingService}, constraint validation is
 * {@link TimetableImportValidationService} (which itself delegates to the
 * unmodified Phase 9-14 constraint pipeline) - this class only sequences
 * them and owns persistence/transaction/audit/authorization boundaries.
 */
@Service
@Transactional(readOnly = true)
public class TimetableImportService {

    private static final Logger log = LoggerFactory.getLogger(TimetableImportService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private final TimetableImportRepository timetableImportRepository;
    private final TimetableImportRowRepository timetableImportRowRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final TimetableParser timetableParser;
    private final TimetableMappingService timetableMappingService;
    private final TimetableImportValidationService timetableImportValidationService;
    private final AcademicTermService academicTermService;
    private final ScheduleVersionService scheduleVersionService;
    private final AllocationRepository allocationRepository;
    private final SubjectService subjectService;
    private final FacultyService facultyService;
    private final LabService labService;
    private final DivisionService divisionService;
    private final BatchService batchService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    TimetableImportService(
            TimetableImportRepository timetableImportRepository,
            TimetableImportRowRepository timetableImportRowRepository,
            PdfTextExtractor pdfTextExtractor,
            TimetableParser timetableParser,
            TimetableMappingService timetableMappingService,
            TimetableImportValidationService timetableImportValidationService,
            AcademicTermService academicTermService,
            ScheduleVersionService scheduleVersionService,
            AllocationRepository allocationRepository,
            SubjectService subjectService,
            FacultyService facultyService,
            LabService labService,
            DivisionService divisionService,
            BatchService batchService,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.timetableImportRepository = timetableImportRepository;
        this.timetableImportRowRepository = timetableImportRowRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.timetableParser = timetableParser;
        this.timetableMappingService = timetableMappingService;
        this.timetableImportValidationService = timetableImportValidationService;
        this.academicTermService = academicTermService;
        this.scheduleVersionService = scheduleVersionService;
        this.allocationRepository = allocationRepository;
        this.subjectService = subjectService;
        this.facultyService = facultyService;
        this.labService = labService;
        this.divisionService = divisionService;
        this.batchService = batchService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    // --- Upload ---

    @Transactional
    public ImportResponse upload(Long academicTermId, Long scheduleVersionId, MultipartFile file, Long uploadedByUserId) {
        validateUpload(file);
        AcademicTerm term = academicTermService.getEntity(academicTermId);
        ScheduleVersion version = scheduleVersionService.getEntity(scheduleVersionId);
        if (!version.getAcademicTerm().getId().equals(academicTermId)) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "The target schedule version does not belong to the specified academic term.");
        }
        if (version.getStatus() != ScheduleVersionStatus.DRAFT) {
            throw new ApiException(
                    "SCHEDULE_VERSION_NOT_DRAFT", HttpStatus.CONFLICT,
                    "PDF import can only target a DRAFT schedule version; current status is " + version.getStatus() + ".");
        }
        AppUser uploadedBy = requireUser(uploadedByUserId);

        byte[] bytes = readBytes(file);
        String hash = sha256Hex(bytes);

        TimetableImport imp = timetableImportRepository.save(
                new TimetableImport(term, version, file.getOriginalFilename(), bytes.length, hash, uploadedBy));

        ExtractedPdf extracted;
        try {
            extracted = pdfTextExtractor.extract(bytes);
        } catch (ApiException e) {
            imp.resolveAsFailed(e.getMessage());
            log.warn("Timetable import {} failed at extraction: {}", imp.getId(), e.getMessage());
            return toResponse(imp);
        }
        if (extracted.isEmpty()) {
            imp.resolveAsFailed("EMPTY_PDF: no extractable text found.");
            return toResponse(imp);
        }

        List<ParsedTimetableRow> parsedRows = timetableParser.parse(extracted);
        if (parsedRows.isEmpty()) {
            imp.resolveAsFailed("NO_TIMETABLE_ROWS_FOUND: no line matched the supported pipe-delimited format (see docs/18-PDF-IMPORT.md).");
            return toResponse(imp);
        }

        TimetableMappingService.MappingContext mappingContext = timetableMappingService.buildContext(academicTermId);
        List<ValidatedRow> validated =
                timetableImportValidationService.validateAll(parsedRows, mappingContext, timetableMappingService, term);

        boolean hasErrors = false;
        int rowNumber = 1;
        for (ValidatedRow v : validated) {
            TimetableImportRow row = timetableImportRowRepository.save(new TimetableImportRow(
                    imp, rowNumber++, v.raw().rawDay(), v.raw().rawStartTime(), v.raw().rawEndTime(),
                    v.raw().rawSubject(), v.raw().rawFaculty(), v.raw().rawLab(), v.raw().rawDivision(), v.raw().rawBatch()));
            applyValidatedRow(row, v);
            hasErrors |= v.status() == ImportRowStatus.ERROR;
        }
        imp.recomputeStatus(hasErrors);

        log.info(
                "Timetable import {} uploaded: file={} rows={} status={}",
                imp.getId(), file.getOriginalFilename(), validated.size(), imp.getStatus());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalFilename", imp.getOriginalFilename());
        metadata.put("rowCount", validated.size());
        auditLogService.record(new AuditEvent(
                uploadedByUserId, UserRole.LAB_ASSISTANT, AuditAction.TIMETABLE_IMPORT_UPLOADED, AuditResourceType.TIMETABLE_IMPORT,
                imp.getId(), imp.getOriginalFilename(), academicTermId, null, metadata));

        return toResponse(imp);
    }

    private void applyValidatedRow(TimetableImportRow row, ValidatedRow v) {
        row.applyPipelineResult(
                v.mapping().day() != null ? v.mapping().day().name() : null,
                v.mapping().startTime(), v.mapping().endTime(),
                v.mapping().subject(), v.mapping().faculty(), v.mapping().lab(), v.mapping().division(), v.mapping().batch(),
                v.allocationDate(), v.status(), v.messages().stream().map(ImportValidationMessage::toStoredMap).toList());
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "The uploaded file must not be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(
                    "FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum size of 10 MB.");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean extensionOk = filename != null && filename.toLowerCase().endsWith(".pdf");
        boolean contentTypeOk = "application/pdf".equalsIgnoreCase(contentType);
        if (!extensionOk && !contentTypeOk) {
            throw new ApiException("UNSUPPORTED_PDF", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "The uploaded file could not be read.");
        }
        // Never trust filename/Content-Type alone (PART 11) - check the actual file signature.
        if (bytes.length < PDF_SIGNATURE.length || !matchesSignature(bytes)) {
            throw new ApiException("UNSUPPORTED_PDF", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not a valid PDF (signature mismatch).");
        }
        return bytes;
    }

    private static boolean matchesSignature(byte[] bytes) {
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (bytes[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must always be available on the JVM", e);
        }
    }

    // --- Review ---

    public Page<ImportResponse> list(Long academicTermId, Long scheduleVersionId, TimetableImportStatus status, Pageable pageable) {
        Specification<TimetableImport> spec = Specification.allOf();
        if (academicTermId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("academicTerm").get("id"), academicTermId));
        }
        if (scheduleVersionId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scheduleVersion").get("id"), scheduleVersionId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return timetableImportRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public ImportDetailResponse getDetail(Long importId, Pageable pageable) {
        TimetableImport imp = getEntity(importId);
        Page<ImportRowResponse> rowsPage = timetableImportRowRepository
                .findByTimetableImportIdOrderByRowNumberAsc(importId, pageable)
                .map(ImportRowResponse::from);
        return new ImportDetailResponse(toResponse(imp), rowsPage.getContent());
    }

    TimetableImport getEntity(Long importId) {
        return timetableImportRepository
                .findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("TIMETABLE_IMPORT_NOT_FOUND", "Timetable import not found: " + importId));
    }

    private ImportResponse toResponse(TimetableImport imp) {
        long total = timetableImportRowRepository.findByTimetableImportIdOrderByRowNumberAsc(imp.getId()).size();
        long valid = timetableImportRowRepository.countByTimetableImportIdAndValidationStatus(imp.getId(), ImportRowStatus.VALID);
        long warning = timetableImportRowRepository.countByTimetableImportIdAndValidationStatus(imp.getId(), ImportRowStatus.WARNING);
        long error = timetableImportRowRepository.countByTimetableImportIdAndValidationStatus(imp.getId(), ImportRowStatus.ERROR);
        long corrected = timetableImportRowRepository.findByTimetableImportIdOrderByRowNumberAsc(imp.getId()).stream()
                .filter(TimetableImportRow::isCorrected)
                .count();
        return ImportResponse.from(imp, new ImportSummary(total, valid, warning, error, corrected));
    }

    // --- Correction ---

    @Transactional
    public ImportRowResponse correctRow(Long importId, Long rowId, RowCorrectionRequest request) {
        TimetableImport imp = getEntity(importId);
        if (!imp.isEditable()) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_NOT_EDITABLE", HttpStatus.CONFLICT, "This import is " + imp.getStatus() + " and can no longer be corrected.");
        }
        TimetableImportRow row = timetableImportRowRepository
                .findByIdAndTimetableImportId(rowId, importId)
                .orElseThrow(() -> new ResourceNotFoundException("TIMETABLE_IMPORT_ROW_NOT_FOUND", "Row not found: " + rowId));

        var subject = request.subjectId() != null ? subjectService.getEntity(request.subjectId()) : null;
        var faculty = request.facultyId() != null ? facultyService.getEntity(request.facultyId()) : null;
        var lab = request.labId() != null ? labService.getEntity(request.labId()) : null;
        var division = request.divisionId() != null ? divisionService.getEntity(request.divisionId()) : null;
        var batch = request.batchId() != null ? batchService.getEntity(request.batchId()) : null;
        java.time.DayOfWeek day = request.day() != null ? TimetableNormalizer.normalizeDay(request.day()) : null;
        java.time.LocalDate allocationDate = day != null
                ? TimetableImportValidationService.firstOccurrenceOnOrAfter(imp.getAcademicTerm().getStartDate(), day)
                : null;

        row.applyCorrection(
                day != null ? day.name() : null, request.startTime(), request.endTime(),
                subject, faculty, lab, division, batch, allocationDate);

        revalidateImport(imp);
        return ImportRowResponse.from(row);
    }

    /**
     * Re-runs mapping-independent validation for every row of this import
     * (PART 29) - correcting one row can create or resolve a cross-row
     * conflict with any other row, so the whole import is reconsidered
     * rather than attempting a narrower, error-prone partial invalidation.
     * Simplicity over micro-optimization at this project's scale (a college
     * timetable import, not a high-volume batch system).
     */
    private void revalidateImport(TimetableImport imp) {
        List<TimetableImportRow> rows = timetableImportRowRepository.findByTimetableImportIdOrderByRowNumberAsc(imp.getId());
        List<ValidatedRow> perRow = rows.stream().map(row -> revalidateOneAlreadyMappedRow(row, imp.getAcademicTerm())).toList();
        List<ValidatedRow> withCrossRow = timetableImportValidationService.applyCrossRowConflicts(perRow);

        boolean hasErrors = false;
        for (int i = 0; i < rows.size(); i++) {
            TimetableImportRow row = rows.get(i);
            ValidatedRow revalidated = withCrossRow.get(i);
            row.applyPipelineResult(
                    row.getNormalizedDay(), row.getNormalizedStartTime(), row.getNormalizedEndTime(),
                    row.getSubject(), row.getFaculty(), row.getLab(), row.getDivision(), row.getBatch(),
                    row.getAllocationDate(), revalidated.status(),
                    revalidated.messages().stream().map(ImportValidationMessage::toStoredMap).toList());
            hasErrors |= revalidated.status() == ImportRowStatus.ERROR;
        }
        imp.recomputeStatus(hasErrors);
    }

    private ValidatedRow revalidateOneAlreadyMappedRow(TimetableImportRow row, AcademicTerm term) {
        // Re-runs the same hard-constraint pipeline TimetableImportValidationService uses for a
        // fresh row, but against this row's already-resolved (mapped/corrected) fields directly -
        // cross-row conflict detection still needs the full sibling set, so it is re-run for the
        // whole import by the caller rather than per-row here.
        if (row.getSubject() == null || row.getFaculty() == null || row.getLab() == null || row.getDivision() == null
                || row.getNormalizedStartTime() == null || row.getNormalizedEndTime() == null || row.getAllocationDate() == null) {
            return new TimetableImportValidationService.ValidatedRow(
                    toParsedRow(row), toMappingResult(row), null,
                    List.of(ImportValidationMessage.error("UNRESOLVED_ROW", "This row is still missing required mapped fields.", Map.of())),
                    ImportRowStatus.ERROR);
        }
        return timetableImportValidationService.revalidateMappedRow(toParsedRow(row), toMappingResult(row), row.getAllocationDate(), term);
    }

    private ParsedTimetableRow toParsedRow(TimetableImportRow row) {
        return new ParsedTimetableRow(
                row.getRowNumber(), row.getRawDay(), row.getRawStartTime(), row.getRawEndTime(),
                row.getRawSubject(), row.getRawFaculty(), row.getRawLab(), row.getRawDivision(), row.getRawBatch());
    }

    private TimetableMappingService.MappingResult toMappingResult(TimetableImportRow row) {
        return new TimetableMappingService.MappingResult(
                row.getNormalizedDay() != null ? java.time.DayOfWeek.valueOf(row.getNormalizedDay()) : null,
                row.getNormalizedStartTime(), row.getNormalizedEndTime(),
                row.getSubject(), row.getFaculty(), row.getLab(), row.getDivision(), row.getBatch(), List.of());
    }

    // --- Approval / Rejection ---

    /**
     * The trust-boundary transition (PART 32/33/34/35/36). Locks this import
     * ({@code TimetableImportRepository.lockById}, serializing concurrent
     * approval attempts of the <em>same</em> import), re-validates every row
     * against <em>current</em> live state (never trusting review-time
     * results, since scheduling state can change between review and
     * approval), and only then persists real {@code Allocation} rows - one
     * {@code saveAndFlush} per row, so the exact same PostgreSQL exclusion
     * constraints Phase 16 already relies on (never a separate, weaker
     * concurrency path) reject a genuine double-booking against another,
     * independently-approved-concurrently import. Any failure - a still-ERROR
     * row, a concurrency conflict, the target version no longer DRAFT - rolls
     * back the entire transaction: zero allocations from a rejected approval,
     * never a partial import (PART 33).
     */
    @Transactional
    public ApproveResponse approve(Long importId, Long actingUserId) {
        timetableImportRepository.lockById(importId);
        TimetableImport imp = getEntity(importId);

        ScheduleVersion version = scheduleVersionService.getEntity(imp.getScheduleVersion().getId());
        if (version.getStatus() != ScheduleVersionStatus.DRAFT) {
            throw new ApiException(
                    "SCHEDULE_VERSION_NOT_DRAFT", HttpStatus.CONFLICT,
                    "The target schedule version is no longer DRAFT (now " + version.getStatus() + ") - approval is not possible.");
        }

        revalidateImport(imp); // PART 34 - never trust review-time validation alone.
        if (imp.getStatus() != TimetableImportStatus.VALIDATED) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_HAS_ERRORS", HttpStatus.CONFLICT,
                    "This import still has unresolved ERROR rows after revalidation and cannot be approved.");
        }

        AppUser approvedBy = requireUser(actingUserId);
        List<TimetableImportRow> rows = timetableImportRowRepository.findByTimetableImportIdOrderByRowNumberAsc(importId);
        int created = 0;
        try {
            for (TimetableImportRow row : rows) {
                Allocation allocation = row.getBatch() != null
                        ? Allocation.forBatch(
                                AllocationType.REGULAR, row.getDivision(), row.getBatch(), row.getSubject(), row.getFaculty(), row.getLab(),
                                row.getAllocationDate(), row.getNormalizedStartTime(), row.getNormalizedEndTime(),
                                com.college.laballocation.scheduling.AllocationStatus.APPROVED, version, approvedBy)
                        : Allocation.forDivision(
                                AllocationType.REGULAR, row.getDivision(), row.getSubject(), row.getFaculty(), row.getLab(),
                                row.getAllocationDate(), row.getNormalizedStartTime(), row.getNormalizedEndTime(),
                                com.college.laballocation.scheduling.AllocationStatus.APPROVED, version, approvedBy);
                allocation.recordSourceImport(importId);
                allocationRepository.saveAndFlush(allocation);
                created++;
            }
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.warn("Timetable import {} approval lost a database-level concurrency race: {}", importId, e.getMessage());
            throw new ApiException(
                    "TIMETABLE_IMPORT_APPROVAL_CONFLICT", HttpStatus.CONFLICT,
                    "One or more rows conflict with an allocation created concurrently by another approval; nothing was committed.");
        }

        imp.approve(approvedBy);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scheduleVersionId", version.getId());
        metadata.put("numberOfRows", rows.size());
        metadata.put("numberOfAllocationsCreated", created);
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.TIMETABLE_IMPORT_APPROVED, AuditResourceType.TIMETABLE_IMPORT,
                imp.getId(), imp.getOriginalFilename(), imp.getAcademicTerm().getId(), null, metadata));

        return new ApproveResponse(toResponse(imp), created);
    }

    @Transactional
    public ImportResponse reject(Long importId, Long actingUserId) {
        TimetableImport imp = getEntity(importId);
        imp.reject();
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.TIMETABLE_IMPORT_REJECTED, AuditResourceType.TIMETABLE_IMPORT,
                imp.getId(), imp.getOriginalFilename(), imp.getAcademicTerm().getId(), null, Map.of()));
        return toResponse(imp);
    }

    private AppUser requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
    }
}
