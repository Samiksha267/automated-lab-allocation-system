package com.college.laballocation.scheduling;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal lifecycle service - create a draft, publish it. No end-user API
 * exposes this yet (PART 33 of the Phase 8 brief: no publication
 * workflow/UI belongs in this phase); it exists so the persistence layer is
 * exercised and ready for Phase 18 (schedule-version history UI) and
 * Phase 19 (PDF import approval, which publishes a DRAFT built from
 * approved import entries) to build on without redoing this groundwork.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleVersionService {

    private final ScheduleVersionRepository scheduleVersionRepository;
    private final AcademicTermService academicTermService;
    private final UserRepository userRepository;

    public ScheduleVersionService(
            ScheduleVersionRepository scheduleVersionRepository,
            AcademicTermService academicTermService,
            UserRepository userRepository) {
        this.scheduleVersionRepository = scheduleVersionRepository;
        this.academicTermService = academicTermService;
        this.userRepository = userRepository;
    }

    public ScheduleVersion getEntity(Long id) {
        return scheduleVersionRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_VERSION_NOT_FOUND", "Schedule version not found: " + id));
    }

    public List<ScheduleVersion> listByTerm(Long academicTermId) {
        return scheduleVersionRepository.findByAcademicTermIdOrderByVersionNumberAsc(academicTermId);
    }

    /**
     * Creates the next {@code DRAFT} version for a term. {@code reason} is
     * required for every version after the first - the initial version of a
     * term's schedule needs no justification, but every subsequent revision
     * must record why it exists (docs/04-DATABASE-DESIGN.md §7).
     */
    @Transactional
    public ScheduleVersion createDraft(Long academicTermId, String reason, Long createdByUserId) {
        AcademicTerm term = academicTermService.getEntity(academicTermId);
        int nextVersionNumber = scheduleVersionRepository.countByAcademicTermId(academicTermId) + 1;
        if (nextVersionNumber > 1 && (reason == null || reason.isBlank())) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "A reason is required when creating a schedule version revision (version " + nextVersionNumber + ").");
        }
        AppUser createdBy = requireUser(createdByUserId);
        return scheduleVersionRepository.save(new ScheduleVersion(term, nextVersionNumber, reason, createdBy));
    }

    /**
     * Publishes a DRAFT version, superseding the term's previously
     * PUBLISHED version (if any) in the same transaction - never deleting
     * it (ADR-009). The database's partial unique index
     * ({@code uq_schedule_version_one_published_per_term}) backstops this
     * even if application code ever fails to supersede correctly.
     */
    @Transactional
    public ScheduleVersion publish(Long scheduleVersionId, Long publishedByUserId) {
        ScheduleVersion version = getEntity(scheduleVersionId);
        AppUser publishedBy = requireUser(publishedByUserId);

        scheduleVersionRepository
                .findByAcademicTermIdAndStatus(version.getAcademicTerm().getId(), ScheduleVersionStatus.PUBLISHED)
                .ifPresent(ScheduleVersion::supersede);

        version.publish(publishedBy);
        return version;
    }

    private AppUser requireUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
    }
}
