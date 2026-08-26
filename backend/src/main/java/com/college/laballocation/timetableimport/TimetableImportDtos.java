package com.college.laballocation.timetableimport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class TimetableImportDtos {
    private TimetableImportDtos() {}

    public record RowCorrectionRequest(
            Long subjectId, Long facultyId, Long labId, Long divisionId, Long batchId,
            String day, LocalTime startTime, LocalTime endTime) {}

    public record ImportSummary(long totalRows, long validRows, long warningRows, long errorRows, long correctedRows) {}

    public record ImportRowResponse(
            Long id,
            int rowNumber,
            String rawDay, String rawStartTime, String rawEndTime,
            String rawSubject, String rawFaculty, String rawLab, String rawDivision, String rawBatch,
            String normalizedDay, LocalTime normalizedStartTime, LocalTime normalizedEndTime,
            Long subjectId, String subjectCode,
            Long facultyId, String facultyName,
            Long labId, String labCode,
            Long divisionId, String divisionCode,
            Long batchId, String batchCode,
            LocalDate allocationDate,
            String validationStatus,
            List<Map<String, Object>> validationMessages,
            boolean corrected) {

        static ImportRowResponse from(TimetableImportRow row) {
            return new ImportRowResponse(
                    row.getId(), row.getRowNumber(),
                    row.getRawDay(), row.getRawStartTime(), row.getRawEndTime(),
                    row.getRawSubject(), row.getRawFaculty(), row.getRawLab(), row.getRawDivision(), row.getRawBatch(),
                    row.getNormalizedDay(), row.getNormalizedStartTime(), row.getNormalizedEndTime(),
                    row.getSubject() != null ? row.getSubject().getId() : null, row.getSubject() != null ? row.getSubject().getCode() : null,
                    row.getFaculty() != null ? row.getFaculty().getId() : null, row.getFaculty() != null ? row.getFaculty().getName() : null,
                    row.getLab() != null ? row.getLab().getId() : null, row.getLab() != null ? row.getLab().getCode() : null,
                    row.getDivision() != null ? row.getDivision().getId() : null, row.getDivision() != null ? row.getDivision().getCode() : null,
                    row.getBatch() != null ? row.getBatch().getId() : null, row.getBatch() != null ? row.getBatch().getCode() : null,
                    row.getAllocationDate(), row.getValidationStatus().name(), row.getValidationMessages(), row.isCorrected());
        }
    }

    public record ImportResponse(
            Long id,
            Long academicTermId,
            Long scheduleVersionId,
            String originalFilename,
            long fileSizeBytes,
            String fileHash,
            String status,
            String failureReason,
            Long uploadedByUserId,
            Instant uploadedAt,
            Long approvedByUserId,
            Instant approvedAt,
            ImportSummary summary) {

        static ImportResponse from(TimetableImport imp, ImportSummary summary) {
            return new ImportResponse(
                    imp.getId(), imp.getAcademicTerm().getId(), imp.getScheduleVersion().getId(),
                    imp.getOriginalFilename(), imp.getFileSizeBytes(), imp.getFileHash(),
                    imp.getStatus().name(), imp.getFailureReason(),
                    imp.getUploadedBy().getId(), imp.getUploadedAt(),
                    imp.getApprovedBy() != null ? imp.getApprovedBy().getId() : null, imp.getApprovedAt(),
                    summary);
        }
    }

    public record ImportDetailResponse(ImportResponse importResponse, List<ImportRowResponse> rows) {}

    public record ApproveResponse(ImportResponse importResponse, int allocationsCreated) {}
}
