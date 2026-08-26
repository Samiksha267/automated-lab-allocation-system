package com.college.laballocation.analytics;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermRepository;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.scheduling.ScheduleVersionRepository;
import com.college.laballocation.scheduling.ScheduleVersionStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the one {@link AnalyticsScope} every analytics endpoint shares (PART 3/21/22 of the
 * phase brief): the term (404 if unknown), an explicit or term-derived {@code [from, to]} date
 * range, and the term's current PUBLISHED {@code ScheduleVersion} id, if any - looked up by
 * {@code status = PUBLISHED}, never {@code MAX(version_number)} (PART 6), reusing exactly the same
 * lookup {@code LabUtilizationService}/{@code TimetableController} already rely on for this
 * guarantee rather than a third independent implementation of it.
 */
@Component
@Transactional(readOnly = true)
public class AnalyticsScopeService {

    private final AcademicTermRepository academicTermRepository;
    private final ScheduleVersionRepository scheduleVersionRepository;

    public AnalyticsScopeService(AcademicTermRepository academicTermRepository, ScheduleVersionRepository scheduleVersionRepository) {
        this.academicTermRepository = academicTermRepository;
        this.scheduleVersionRepository = scheduleVersionRepository;
    }

    public AnalyticsScope resolve(Long academicTermId, LocalDate from, LocalDate to) {
        AcademicTerm term = academicTermRepository.findById(academicTermId)
                .orElseThrow(() -> new ResourceNotFoundException("ACADEMIC_TERM_NOT_FOUND", "Academic term not found: " + academicTermId));

        LocalDate effectiveFrom = from != null ? from : term.getStartDate();
        LocalDate effectiveTo = to != null ? to : term.getEndDate();
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "'to' (" + effectiveTo + ") must not be before 'from' (" + effectiveFrom + ").");
        }

        Optional<Long> publishedVersionId = scheduleVersionRepository
                .findByAcademicTermIdAndStatus(academicTermId, ScheduleVersionStatus.PUBLISHED)
                .map(v -> v.getId());

        return new AnalyticsScope(term, effectiveFrom, effectiveTo, publishedVersionId);
    }
}
