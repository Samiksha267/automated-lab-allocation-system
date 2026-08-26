package com.college.laballocation.scheduling;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.scheduling.ScheduleVersionDtos.AllocationSummaryResponse;
import com.college.laballocation.scheduling.ScheduleVersionDtos.ScheduleVersionHistoryResponse;
import com.college.laballocation.scheduling.ScheduleVersionDtos.ScheduleVersionResponse;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single owner of {@link ScheduleVersion} lifecycle transitions (Phase 18,
 * PART 37/38 of the phase brief) - no controller, repository, or other
 * service ever calls {@code ScheduleVersion.publish()}/{@code .supersede()}
 * directly, and no code anywhere sets {@code status} any other way. Extends
 * the minimal Phase 8 groundwork (create a draft, publish it) with the full
 * lifecycle: term-scoped safe version numbering, atomic/concurrency-safe
 * publication with automatic superseding, version history, and per-version
 * allocation listing.
 *
 * <p><b>Reused, not duplicated</b> (PART 1/2 of the phase brief): the
 * {@code ScheduleVersion}/{@code ScheduleVersionStatus}/{@code Allocation.scheduleVersionId}
 * model, the {@code DRAFT->PUBLISHED->SUPERSEDED} transition guards
 * ({@link ScheduleVersion#publish}/{@link ScheduleVersion#supersede}), and
 * both database invariants ({@code uq_schedule_version_term_number},
 * {@code uq_schedule_version_one_published_per_term}) already existed from
 * Phase 8 - this phase completes the service/API layer around them.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleVersionService {

    private final ScheduleVersionRepository scheduleVersionRepository;
    private final AcademicTermService academicTermService;
    private final AcademicTermRepository academicTermRepository;
    private final AllocationRepository allocationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ScheduleVersionService(
            ScheduleVersionRepository scheduleVersionRepository,
            AcademicTermService academicTermService,
            AcademicTermRepository academicTermRepository,
            AllocationRepository allocationRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.scheduleVersionRepository = scheduleVersionRepository;
        this.academicTermService = academicTermService;
        this.academicTermRepository = academicTermRepository;
        this.allocationRepository = allocationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public ScheduleVersion getEntity(Long id) {
        return scheduleVersionRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_VERSION_NOT_FOUND", "Schedule version not found: " + id));
    }

    public List<ScheduleVersion> listByTerm(Long academicTermId) {
        return scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(academicTermId);
    }

    /** {@code Optional.empty()} when the term has no current published timetable (PART 21/66) - callers must never fall back to the highest version number. */
    public Optional<ScheduleVersion> getCurrentPublished(Long academicTermId) {
        return scheduleVersionRepository.findByAcademicTermIdAndStatus(academicTermId, ScheduleVersionStatus.PUBLISHED);
    }

    private long activeAllocationCount(Long scheduleVersionId) {
        return allocationRepository.countByScheduleVersionIdAndStatusIn(
                scheduleVersionId, List.of(AllocationStatus.APPROVED, AllocationStatus.PUBLISHED));
    }

    /** PART 18 - newest version first, each carrying its own allocation count (never an unbounded nested allocation graph - see {@link #getVersionAllocations}). */
    public ScheduleVersionHistoryResponse getHistory(Long academicTermId) {
        AcademicTerm term = academicTermService.getEntity(academicTermId);
        List<ScheduleVersionResponse> versions = scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(academicTermId)
                .stream()
                .sorted((a, b) -> Integer.compare(b.getVersionNumber(), a.getVersionNumber()))
                .map(v -> ScheduleVersionResponse.from(v, activeAllocationCount(v.getId())))
                .toList();
        return new ScheduleVersionHistoryResponse(term.getId(), term.getDisplayName(), versions);
    }

    public ScheduleVersionResponse getVersion(Long scheduleVersionId) {
        ScheduleVersion version = getEntity(scheduleVersionId);
        return ScheduleVersionResponse.from(version, activeAllocationCount(version.getId()));
    }

    /** PART 20 - paginated, optionally filtered by division/batch/subject/faculty/lab/date; any status (a Lab Assistant may legitimately want to see a DRAFT's or SUPERSEDED version's rows). */
    public Page<AllocationSummaryResponse> getVersionAllocations(
            Long scheduleVersionId, Long divisionId, Long batchId, Long subjectId, Long facultyId, Long labId, LocalDate date,
            Pageable pageable) {
        getEntity(scheduleVersionId); // 404s cleanly if the version itself doesn't exist, before ever touching allocation
        Specification<Allocation> spec = AllocationSpecifications.scheduleVersionId(scheduleVersionId);
        if (divisionId != null) {
            spec = spec.and(AllocationSpecifications.divisionId(divisionId));
        }
        if (batchId != null) {
            spec = spec.and(AllocationSpecifications.batchId(batchId));
        }
        if (subjectId != null) {
            spec = spec.and(AllocationSpecifications.subjectId(subjectId));
        }
        if (facultyId != null) {
            spec = spec.and(AllocationSpecifications.facultyId(facultyId));
        }
        if (labId != null) {
            spec = spec.and(AllocationSpecifications.labId(labId));
        }
        if (date != null) {
            spec = spec.and(AllocationSpecifications.allocationDate(date));
        }
        return allocationRepository.findAll(spec, pageable).map(AllocationSummaryResponse::from);
    }

    /**
     * The student/CR-facing published timetable (PART 21/22/23/46/47/66) -
     * always selected by {@code ScheduleVersion.status = PUBLISHED}, never
     * {@code MAX(version_number)} (a higher-numbered DRAFT must never leak
     * through here). Returns an empty page, not a 404, when the term
     * currently has no published version - a "the timetable isn't published
     * yet" state is a legitimate, expected list result, not an error.
     */
    public Page<AllocationSummaryResponse> getPublishedTimetable(
            Long academicTermId, Long divisionId, Long batchId, Pageable pageable) {
        Specification<Allocation> spec = AllocationSpecifications.currentlyPublishedForTerm(academicTermId)
                .and(AllocationSpecifications.activeStatus());
        if (divisionId != null) {
            spec = spec.and(AllocationSpecifications.divisionId(divisionId));
        }
        if (batchId != null) {
            spec = spec.and(AllocationSpecifications.batchIdOrDivisionWide(batchId));
        }
        return allocationRepository.findAll(spec, pageable).map(AllocationSummaryResponse::from);
    }

    /**
     * Creates the next {@code DRAFT} version for a term. {@code reason} is
     * required for every version after the first - the initial version of a
     * term's schedule needs no justification, but every subsequent revision
     * must record why it exists (docs/04-DATABASE-DESIGN.md §7).
     *
     * <p><b>Concurrency-safe numbering</b> (PART 6/51): {@code AcademicTermRepository.lockById}
     * is acquired <i>before</i> {@code countByAcademicTermId} runs, serializing
     * concurrent draft-creation requests for the same term - mirroring the
     * per-division booking lock from Phase 16 (ADR-073) exactly, rather than
     * an unsafe {@code SELECT MAX(version_number)+1} racing across two
     * transactions. {@code uq_schedule_version_term_number} remains the
     * final database-level backstop either way.
     */
    @Transactional
    public ScheduleVersion createDraft(Long academicTermId, String reason, Long createdByUserId) {
        AcademicTerm term = academicTermService.getEntity(academicTermId);
        academicTermRepository.lockById(academicTermId);
        int nextVersionNumber = scheduleVersionRepository.countByAcademicTermId(academicTermId) + 1;
        if (nextVersionNumber > 1 && (reason == null || reason.isBlank())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "A reason is required when creating a schedule version revision (version " + nextVersionNumber + ").");
        }
        AppUser createdBy = requireUser(createdByUserId);
        ScheduleVersion saved = scheduleVersionRepository.save(new ScheduleVersion(term, nextVersionNumber, reason, createdBy));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("versionNumber", saved.getVersionNumber());
        if (reason != null) {
            metadata.put("reason", reason);
        }
        auditLogService.record(new AuditEvent(
                createdByUserId, UserRole.LAB_ASSISTANT, AuditAction.SCHEDULE_VERSION_CREATED,
                AuditResourceType.SCHEDULE_VERSION, saved.getId(), term.getDisplayName() + " v" + saved.getVersionNumber(),
                term.getId(), null, metadata));

        return saved;
    }

    /**
     * Publishes a DRAFT version, superseding the term's previously
     * PUBLISHED version (if any) in the same transaction - never deleting
     * it (ADR-009). Every APPROVED allocation already belonging to the
     * target version transitions to PUBLISHED in the same transaction too
     * (PART 15) - see {@link Allocation#publish()}; CANCELLED rows are never
     * touched, and rows already PUBLISHED (Phase 15 EXTRA bookings, which
     * are stamped PUBLISHED immediately at booking time, ADR-070) need no
     * transition.
     *
     * <p><b>Concurrency-safe publication</b> (PART 14, mandatory): the same
     * per-term {@code AcademicTermRepository.lockById} used by
     * {@link #createDraft} is acquired here too, <i>before</i> the target
     * version is even loaded (via a scalar-only {@code findAcademicTermIdById}
     * projection, so the entity itself is not yet in the persistence context
     * and cannot go stale once the lock resolves) and before the "which
     * version is currently PUBLISHED" query runs. Two concurrent publish
     * requests for the same term therefore fully serialize: the second
     * request's lock acquisition blocks until the first commits, then its
     * own fresh reads correctly see the first's already-published result -
     * never two concurrent transactions independently deciding "there is no
     * PUBLISHED version yet" and both superseding nothing. The database's
     * partial unique index ({@code uq_schedule_version_one_published_per_term})
     * remains the final backstop regardless.
     */
    @Transactional
    public ScheduleVersion publish(Long scheduleVersionId, Long publishedByUserId) {
        Long academicTermId = scheduleVersionRepository
                .findAcademicTermIdById(scheduleVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_VERSION_NOT_FOUND", "Schedule version not found: " + scheduleVersionId));
        academicTermRepository.lockById(academicTermId);

        ScheduleVersion version = getEntity(scheduleVersionId);
        AppUser publishedBy = requireUser(publishedByUserId);

        // Real bug found live in Docker (Phase 18): without this up-front guard, publishing
        // an already-PUBLISHED version made the "find the term's currently published version"
        // lookup below return `version` itself (the same row), which then got superseded
        // in-memory before ScheduleVersion.publish()'s own guard ran - producing a confusing
        // "current status is SUPERSEDED" error instead of the honest "already PUBLISHED" one,
        // and (harmlessly, since the transaction still rolled back on the exception, but
        // wastefully) mutating an entity that should never have been touched. Checking status
        // here, before ever looking up "the existing published version," makes the two cases
        // (this version isn't a DRAFT at all; a *different* version is currently PUBLISHED)
        // structurally impossible to conflate. See ADR-089, docs/15-DESIGN-DECISIONS.md.
        if (version.getStatus() != ScheduleVersionStatus.DRAFT) {
            throw new ApiException(
                    "INVALID_SCHEDULE_VERSION_TRANSITION", HttpStatus.CONFLICT,
                    "Only a DRAFT schedule version can be published; current status is " + version.getStatus() + ".");
        }

        scheduleVersionRepository
                .findByAcademicTermIdAndStatus(academicTermId, ScheduleVersionStatus.PUBLISHED)
                .ifPresent(existing -> {
                    int supersededNumber = existing.getVersionNumber();
                    Long supersededId = existing.getId();
                    existing.supersede();
                    // Forces the SUPERSEDED update to hit the database now, before `version`
                    // is touched below - real bug found live in Docker (Phase 18): Hibernate's
                    // flush ordering for two ScheduleVersion entities in the same persistence
                    // context follows LOAD order, not mutation order. `version` (the target
                    // being published) is loaded before `existing` (found here, second), so
                    // without this explicit flush its PUBLISHED update was issued to Postgres
                    // BEFORE `existing`'s SUPERSEDED update - producing two simultaneously
                    // PUBLISHED rows for one instant and tripping uq_schedule_version_one_published_per_term,
                    // even for a single, non-concurrent request. See ADR-088, docs/15-DESIGN-DECISIONS.md.
                    scheduleVersionRepository.flush();
                    auditLogService.record(new AuditEvent(
                            publishedByUserId, UserRole.LAB_ASSISTANT, AuditAction.SCHEDULE_SUPERSEDED,
                            AuditResourceType.SCHEDULE_VERSION, supersededId,
                            version.getAcademicTerm().getDisplayName() + " v" + supersededNumber,
                            academicTermId, null,
                            Map.of("versionNumber", supersededNumber, "supersededByVersionId", scheduleVersionId)));
                });

        version.publish(publishedBy);

        for (Allocation approved : allocationRepository.findByScheduleVersionIdAndStatus(scheduleVersionId, AllocationStatus.APPROVED)) {
            approved.publish();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("versionNumber", version.getVersionNumber());
        auditLogService.record(new AuditEvent(
                publishedByUserId, UserRole.LAB_ASSISTANT, AuditAction.SCHEDULE_PUBLISHED,
                AuditResourceType.SCHEDULE_VERSION, version.getId(),
                version.getAcademicTerm().getDisplayName() + " v" + version.getVersionNumber(),
                academicTermId, null, metadata));

        return version;
    }

    private AppUser requireUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
    }
}
