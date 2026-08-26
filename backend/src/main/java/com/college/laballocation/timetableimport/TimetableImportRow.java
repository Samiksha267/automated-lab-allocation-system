package com.college.laballocation.timetableimport;

import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.subject.Subject;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/**
 * One parsed timetable row, staged - never a confirmed {@code Allocation}
 * (Phase 19). Preserves the original extracted text ({@code raw*}) forever,
 * alongside whatever normalization/mapping/validation has since resolved
 * (PART 16 of the phase brief) - a reviewer can always see exactly what the
 * PDF said, not just what the system decided it meant.
 */
@Entity
@Table(name = "timetable_import_row")
public class TimetableImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_import_id", nullable = false)
    private TimetableImport timetableImport;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_day", length = 50)
    private String rawDay;

    @Column(name = "raw_start_time", length = 50)
    private String rawStartTime;

    @Column(name = "raw_end_time", length = 50)
    private String rawEndTime;

    @Column(name = "raw_subject", length = 255)
    private String rawSubject;

    @Column(name = "raw_faculty", length = 255)
    private String rawFaculty;

    @Column(name = "raw_lab", length = 255)
    private String rawLab;

    @Column(name = "raw_division", length = 255)
    private String rawDivision;

    @Column(name = "raw_batch", length = 255)
    private String rawBatch;

    @Column(name = "normalized_day", length = 16)
    private String normalizedDay;

    @Column(name = "normalized_start_time")
    private LocalTime normalizedStartTime;

    @Column(name = "normalized_end_time")
    private LocalTime normalizedEndTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id")
    private Lab lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "allocation_date")
    private LocalDate allocationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private ImportRowStatus validationStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_messages", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> validationMessages = List.of();

    @Column(nullable = false)
    private boolean corrected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimetableImportRow() {}

    public TimetableImportRow(
            TimetableImport timetableImport,
            int rowNumber,
            String rawDay,
            String rawStartTime,
            String rawEndTime,
            String rawSubject,
            String rawFaculty,
            String rawLab,
            String rawDivision,
            String rawBatch) {
        this.timetableImport = timetableImport;
        this.rowNumber = rowNumber;
        this.rawDay = rawDay;
        this.rawStartTime = rawStartTime;
        this.rawEndTime = rawEndTime;
        this.rawSubject = rawSubject;
        this.rawFaculty = rawFaculty;
        this.rawLab = rawLab;
        this.rawDivision = rawDivision;
        this.rawBatch = rawBatch;
        this.validationStatus = ImportRowStatus.ERROR;
        this.validationMessages = List.of();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Applies the result of running normalization + mapping + validation
     * (initial, or a revalidation after correction, PART 29) - the single
     * place this row's derived state changes. Never callable once the
     * parent import has left the reviewable phase (PART 49).
     */
    public void applyPipelineResult(
            String normalizedDay,
            LocalTime normalizedStartTime,
            LocalTime normalizedEndTime,
            Subject subject,
            Faculty faculty,
            Lab lab,
            Division division,
            Batch batch,
            LocalDate allocationDate,
            ImportRowStatus validationStatus,
            List<Map<String, Object>> validationMessages) {
        requireEditable();
        this.normalizedDay = normalizedDay;
        this.normalizedStartTime = normalizedStartTime;
        this.normalizedEndTime = normalizedEndTime;
        this.subject = subject;
        this.faculty = faculty;
        this.lab = lab;
        this.division = division;
        this.batch = batch;
        this.allocationDate = allocationDate;
        this.validationStatus = validationStatus;
        this.validationMessages = List.copyOf(validationMessages);
        this.updatedAt = Instant.now();
    }

    /**
     * A Lab Assistant's manual correction (PART 28) - operates on already-resolved
     * fields (mapped entity ids, normalized day/time), the same shape a
     * structured correction form would submit, not raw PDF text - re-running
     * normalization from raw text is not part of this path (see
     * {@code TimetableImportService.correctRow}'s javadoc for why). Marks
     * this row {@code corrected}; the caller is responsible for re-running
     * validation and calling {@link #applyPipelineResult} again immediately
     * after (PART 29 - never leaves a stale validation result).
     */
    public void applyCorrection(
            String normalizedDay,
            LocalTime normalizedStartTime,
            LocalTime normalizedEndTime,
            Subject subject,
            Faculty faculty,
            Lab lab,
            Division division,
            Batch batch,
            LocalDate allocationDate) {
        requireEditable();
        this.normalizedDay = normalizedDay;
        this.normalizedStartTime = normalizedStartTime;
        this.normalizedEndTime = normalizedEndTime;
        this.subject = subject;
        this.faculty = faculty;
        this.lab = lab;
        this.division = division;
        this.batch = batch;
        this.allocationDate = allocationDate;
        this.corrected = true;
        this.updatedAt = Instant.now();
    }

    private void requireEditable() {
        if (!timetableImport.isEditable()) {
            throw new ApiException(
                    "TIMETABLE_IMPORT_NOT_EDITABLE", HttpStatus.CONFLICT,
                    "This row's import is " + timetableImport.getStatus() + " and can no longer be modified.");
        }
    }

    public Long getId() {
        return id;
    }

    public TimetableImport getTimetableImport() {
        return timetableImport;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRawDay() {
        return rawDay;
    }

    public String getRawStartTime() {
        return rawStartTime;
    }

    public String getRawEndTime() {
        return rawEndTime;
    }

    public String getRawSubject() {
        return rawSubject;
    }

    public String getRawFaculty() {
        return rawFaculty;
    }

    public String getRawLab() {
        return rawLab;
    }

    public String getRawDivision() {
        return rawDivision;
    }

    public String getRawBatch() {
        return rawBatch;
    }

    public String getNormalizedDay() {
        return normalizedDay;
    }

    public LocalTime getNormalizedStartTime() {
        return normalizedStartTime;
    }

    public LocalTime getNormalizedEndTime() {
        return normalizedEndTime;
    }

    public Subject getSubject() {
        return subject;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public Lab getLab() {
        return lab;
    }

    public Division getDivision() {
        return division;
    }

    public Batch getBatch() {
        return batch;
    }

    public LocalDate getAllocationDate() {
        return allocationDate;
    }

    public ImportRowStatus getValidationStatus() {
        return validationStatus;
    }

    public List<Map<String, Object>> getValidationMessages() {
        return validationMessages;
    }

    public boolean isCorrected() {
        return corrected;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
