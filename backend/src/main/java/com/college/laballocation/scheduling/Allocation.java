package com.college.laballocation.scheduling;

import com.college.laballocation.academic.Batch;
import com.college.laballocation.academic.Division;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.faculty.Faculty;
import com.college.laballocation.lab.Lab;
import com.college.laballocation.subject.Subject;
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
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;

/**
 * The central persisted scheduling record - created only once already known
 * valid (either an approved PDF-import entry, Phase 19, or a hard-constraint
 * validated EXTRA booking, Phase 15/16); never a tentative/candidate row.
 * {@link com.college.laballocation.scheduling.CandidateAllocation} is the
 * transient, unpersisted counterpart evaluated before one of these is ever
 * written.
 *
 * <p><b>Target invariant</b> (ADR-005): {@code targetType = BATCH} requires a
 * non-null {@code batch} that actually belongs to {@code division};
 * {@code targetType = DIVISION} requires a null {@code batch}. The two
 * static factories below are the only way to construct an instance
 * specifically so this invariant can never be bypassed by construction -
 * mirrored by the database CHECK constraint
 * {@code chk_allocation_target_invariant} as defense in depth (the
 * cross-table "batch belongs to division" half cannot be a CHECK constraint
 * and is enforced here in application code instead).
 *
 * <p>No separate {@code academicTermId} column - the term is always
 * derivable via {@code scheduleVersion.getAcademicTerm()} (see
 * {@link ScheduleVersion} javadoc) - avoiding a redundant FK that could
 * theoretically disagree with the version it's attached to.
 */
@Entity
@Table(name = "allocation")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 16)
    private AllocationType allocationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AllocationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_version_id", nullable = false)
    private ScheduleVersion scheduleVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected Allocation() {}

    private Allocation(
            AllocationType allocationType,
            TargetType targetType,
            Division division,
            Batch batch,
            Subject subject,
            Faculty faculty,
            Lab lab,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime,
            AllocationStatus status,
            ScheduleVersion scheduleVersion,
            AppUser createdBy) {
        if (!TimeIntervalUtils.isValid(startTime, endTime)) {
            throw new ApiException(
                    "INVALID_ALLOCATION_INTERVAL", HttpStatus.BAD_REQUEST, "startTime must be strictly before endTime.");
        }
        this.allocationType = allocationType;
        this.targetType = targetType;
        this.division = division;
        this.batch = batch;
        this.subject = subject;
        this.faculty = faculty;
        this.lab = lab;
        this.allocationDate = allocationDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.scheduleVersion = scheduleVersion;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    /** Constructs a {@code BATCH}-targeted allocation. Rejects a batch that does not belong to {@code division}. */
    public static Allocation forBatch(
            AllocationType allocationType,
            Division division,
            Batch batch,
            Subject subject,
            Faculty faculty,
            Lab lab,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime,
            AllocationStatus status,
            ScheduleVersion scheduleVersion,
            AppUser createdBy) {
        if (batch == null) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "A BATCH-targeted allocation requires a batch.");
        }
        if (!batch.getDivision().getId().equals(division.getId())) {
            throw new ApiException(
                    "INVALID_ACADEMIC_RELATIONSHIP", HttpStatus.BAD_REQUEST,
                    "Batch " + batch.getCode() + " does not belong to the target division.");
        }
        return new Allocation(
                allocationType, TargetType.BATCH, division, batch, subject, faculty, lab,
                allocationDate, startTime, endTime, status, scheduleVersion, createdBy);
    }

    /** Constructs a {@code DIVISION}-targeted allocation. Rejects a non-null batch - a division-wide session has none. */
    public static Allocation forDivision(
            AllocationType allocationType,
            Division division,
            Subject subject,
            Faculty faculty,
            Lab lab,
            LocalDate allocationDate,
            LocalTime startTime,
            LocalTime endTime,
            AllocationStatus status,
            ScheduleVersion scheduleVersion,
            AppUser createdBy) {
        return new Allocation(
                allocationType, TargetType.DIVISION, division, null, subject, faculty, lab,
                allocationDate, startTime, endTime, status, scheduleVersion, createdBy);
    }

    /** {@code APPROVED -> PUBLISHED} only. */
    public void publish() {
        if (status != AllocationStatus.APPROVED) {
            throw new ApiException(
                    "INVALID_ALLOCATION_TRANSITION", HttpStatus.CONFLICT,
                    "Only an APPROVED allocation can be published; current status is " + status + ".");
        }
        this.status = AllocationStatus.PUBLISHED;
    }

    /** {@code APPROVED -> CANCELLED} or {@code PUBLISHED -> CANCELLED} - never deletes the row (NFR-06). */
    public void cancel(AppUser cancelledBy, String cancellationReason) {
        if (status == AllocationStatus.CANCELLED) {
            throw new ApiException(
                    "INVALID_ALLOCATION_TRANSITION", HttpStatus.CONFLICT, "This allocation is already cancelled.");
        }
        this.status = AllocationStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = Instant.now();
        this.cancellationReason = cancellationReason;
    }

    public Long getId() {
        return id;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Division getDivision() {
        return division;
    }

    public Batch getBatch() {
        return batch;
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

    public LocalDate getAllocationDate() {
        return allocationDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public ScheduleVersion getScheduleVersion() {
        return scheduleVersion;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AppUser getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public AppUser getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
