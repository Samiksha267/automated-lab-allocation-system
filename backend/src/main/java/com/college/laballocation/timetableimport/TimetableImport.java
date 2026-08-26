package com.college.laballocation.timetableimport;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.scheduling.ScheduleVersion;
import com.college.laballocation.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * One uploaded PDF's staging record (Phase 19) - the trust boundary this
 * phase is built around lives entirely inside this class and
 * {@link TimetableImportRow}: nothing here is ever an {@code Allocation},
 * and {@link #approve} is the single, explicit moment untrusted staging
 * data is allowed to become confirmed scheduling data (see
 * {@code TimetableImportService.approve}).
 *
 * <p>The synchronous pipeline (extraction/parsing/normalization/mapping/
 * validation) runs entirely before this entity is first persisted - see
 * {@link TimetableImportStatus} for why {@code UPLOADED} is never this
 * class's actual saved status.
 */
@Entity
@Table(name = "timetable_import")
public class TimetableImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_version_id", nullable = false)
    private ScheduleVersion scheduleVersion;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimetableImportStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private AppUser uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected TimetableImport() {}

    public TimetableImport(
            AcademicTerm academicTerm,
            ScheduleVersion scheduleVersion,
            String originalFilename,
            long fileSizeBytes,
            String fileHash,
            AppUser uploadedBy) {
        this.academicTerm = academicTerm;
        this.scheduleVersion = scheduleVersion;
        this.originalFilename = originalFilename;
        this.fileSizeBytes = fileSizeBytes;
        this.fileHash = fileHash;
        this.status = TimetableImportStatus.UPLOADED;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    /** No rows could be parsed at all - a dead end, never reviewable. */
    public void resolveAsFailed(String reason) {
        this.status = TimetableImportStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * Sets the post-pipeline status from the rows actually staged - called
     * once right after upload, and again after every correction-triggered
     * revalidation (PART 29). Only legal while the import hasn't yet left
     * the reviewable phase (PART 49) - an {@code APPROVED}/{@code REJECTED}/
     * {@code FAILED} import's outcome is permanent.
     */
    public void recomputeStatus(boolean hasErrorRows) {
        if (status != TimetableImportStatus.UPLOADED
                && status != TimetableImportStatus.NEEDS_REVIEW
                && status != TimetableImportStatus.VALIDATED) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_NOT_EDITABLE", HttpStatus.CONFLICT,
                    "This import is " + status + " and its rows can no longer be revalidated.");
        }
        this.status = hasErrorRows ? TimetableImportStatus.NEEDS_REVIEW : TimetableImportStatus.VALIDATED;
    }

    /** {@code VALIDATED -> APPROVED} only (PART 31) - never from {@code NEEDS_REVIEW}, matching {@code approve}'s precondition list. */
    public void approve(AppUser approvedBy) {
        if (status != TimetableImportStatus.VALIDATED) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_NOT_APPROVABLE", HttpStatus.CONFLICT,
                    "Only a VALIDATED import (no unresolved ERROR rows) can be approved; current status is " + status + ".");
        }
        this.status = TimetableImportStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }

    /** {@code NEEDS_REVIEW}/{@code VALIDATED} -> {@code REJECTED} (PART 42) - never reversible; a rejected import stays as permanent history, never deleted. */
    public void reject() {
        if (status != TimetableImportStatus.NEEDS_REVIEW && status != TimetableImportStatus.VALIDATED) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_NOT_REJECTABLE", HttpStatus.CONFLICT,
                    "Only a reviewable import can be rejected; current status is " + status + ".");
        }
        this.status = TimetableImportStatus.REJECTED;
    }

    public boolean isEditable() {
        return status == TimetableImportStatus.UPLOADED
                || status == TimetableImportStatus.NEEDS_REVIEW
                || status == TimetableImportStatus.VALIDATED;
    }

    public Long getId() {
        return id;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public ScheduleVersion getScheduleVersion() {
        return scheduleVersion;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getFileHash() {
        return fileHash;
    }

    public TimetableImportStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public AppUser getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
