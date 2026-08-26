package com.college.laballocation.faculty;

import com.college.laballocation.academic.AcademicTerm;
import com.college.laballocation.academic.AcademicTermService;
import com.college.laballocation.academic.TermStatus;
import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.common.TimeIntervalUtils;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.AvailabilityCheckResponse;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.CreateFacultyAvailabilityRequest;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.FacultyAvailabilityResponse;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.UpdateFacultyAvailabilityRequest;
import com.college.laballocation.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty availability management + evaluation. Evaluation
 * ({@link #isAvailable}/{@link #check}) is reusable domain logic, not a
 * {@code SchedulingConstraint} - the future constraint engine (Phase 9+)
 * will call this service internally rather than duplicating the lookup.
 *
 * <p><b>Missing availability means unavailable</b> - a faculty with zero
 * active availability rows for a given term/day is never treated as
 * "available all day" (PART 15 of the phase brief).
 */
@Service
@Transactional(readOnly = true)
public class FacultyAvailabilityService {

    private final FacultyAvailabilityRepository availabilityRepository;
    private final FacultyService facultyService;
    private final AcademicTermService academicTermService;
    private final AuditLogService auditLogService;

    public FacultyAvailabilityService(
            FacultyAvailabilityRepository availabilityRepository,
            FacultyService facultyService,
            AcademicTermService academicTermService,
            AuditLogService auditLogService) {
        this.availabilityRepository = availabilityRepository;
        this.facultyService = facultyService;
        this.academicTermService = academicTermService;
        this.auditLogService = auditLogService;
    }

    public List<FacultyAvailabilityResponse> list(Long facultyId, Long academicTermId, DayOfWeek dayOfWeek) {
        facultyService.getEntity(facultyId);
        List<FacultyAvailability> rows;
        if (academicTermId != null && dayOfWeek != null) {
            rows = availabilityRepository.findByFacultyIdAndAcademicTermIdAndDayOfWeekOrderByStartTimeAsc(
                    facultyId, academicTermId, dayOfWeek);
        } else if (academicTermId != null) {
            rows = availabilityRepository.findByFacultyIdAndAcademicTermIdOrderByDayOfWeekAscStartTimeAsc(
                    facultyId, academicTermId);
        } else if (dayOfWeek != null) {
            rows = availabilityRepository.findByFacultyIdAndDayOfWeekOrderByStartTimeAsc(facultyId, dayOfWeek);
        } else {
            rows = availabilityRepository.findByFacultyIdOrderByDayOfWeekAscStartTimeAsc(facultyId);
        }
        return rows.stream().map(FacultyAvailabilityResponse::from).toList();
    }

    @Transactional
    public FacultyAvailabilityResponse create(Long facultyId, CreateFacultyAvailabilityRequest request, Long actingUserId) {
        Faculty faculty = requireActiveFaculty(facultyId);
        AcademicTerm term = requireUsableTerm(request.academicTermId());
        validateInterval(request.startTime(), request.endTime());
        rejectOverlap(facultyId, term.getId(), request.dayOfWeek(), request.startTime(), request.endTime(), null);

        FacultyAvailability saved = availabilityRepository.save(
                new FacultyAvailability(faculty, term, request.dayOfWeek(), request.startTime(), request.endTime()));
        recordAvailabilityChange(actingUserId, faculty, term.getId(), request.dayOfWeek(), request.startTime(), request.endTime(), "CREATED");
        return FacultyAvailabilityResponse.from(saved);
    }

    @Transactional
    public FacultyAvailabilityResponse update(
            Long facultyId, Long availabilityId, UpdateFacultyAvailabilityRequest request, Long actingUserId) {
        FacultyAvailability availability = getOwnedEntity(facultyId, availabilityId);
        validateInterval(request.startTime(), request.endTime());
        rejectOverlap(
                facultyId,
                availability.getAcademicTerm().getId(),
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                availabilityId);
        availability.update(request.dayOfWeek(), request.startTime(), request.endTime());
        recordAvailabilityChange(
                actingUserId, availability.getFaculty(), availability.getAcademicTerm().getId(),
                request.dayOfWeek(), request.startTime(), request.endTime(), "UPDATED");
        return FacultyAvailabilityResponse.from(availability);
    }

    /** Soft-deactivates (see {@link FacultyAvailability} javadoc for why this project chose deactivation over hard delete here). */
    @Transactional
    public void remove(Long facultyId, Long availabilityId, Long actingUserId) {
        FacultyAvailability availability = getOwnedEntity(facultyId, availabilityId);
        availability.deactivate();
        recordAvailabilityChange(
                actingUserId, availability.getFaculty(), availability.getAcademicTerm().getId(),
                availability.getDayOfWeek(), availability.getStartTime(), availability.getEndTime(), "DEACTIVATED");
    }

    private void recordAvailabilityChange(
            Long actingUserId, Faculty faculty, Long academicTermId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end, String operation) {
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.FACULTY_AVAILABILITY_CHANGED, AuditResourceType.FACULTY,
                faculty.getId(), faculty.getEmployeeCode(), academicTermId, null,
                Map.of(
                        "facultyCode", faculty.getEmployeeCode(), "dayOfWeek", dayOfWeek.toString(),
                        "startTime", start.toString(), "endTime", end.toString(), "operation", operation)));
    }

    /** Is {@code faculty} available for {@code [start, end)} on {@code dayOfWeek} within {@code academicTermId}? */
    public boolean isAvailable(Long facultyId, Long academicTermId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        Faculty faculty = facultyService.getEntity(facultyId);
        if (!faculty.isActive()) {
            return false;
        }
        List<FacultyAvailability> rows = availabilityRepository
                .findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        facultyId, academicTermId, dayOfWeek);
        if (rows.isEmpty()) {
            return false;
        }
        for (LocalTime[] merged : mergeAdjacent(rows)) {
            if (TimeIntervalUtils.contains(merged[0], merged[1], start, end)) {
                return true;
            }
        }
        return false;
    }

    /** Administrative preview endpoint backing (PART 24) - labeled "check," never scheduling/conflict validation. */
    public AvailabilityCheckResponse check(
            Long facultyId, Long academicTermId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        boolean available = isAvailable(facultyId, academicTermId, dayOfWeek, start, end);
        return new AvailabilityCheckResponse(facultyId, academicTermId, dayOfWeek, start, end, available);
    }

    /**
     * Merges overlapping/adjacent stored intervals for evaluation only - never
     * mutates the database (PART 19). Stored active rows never overlap
     * (enforced at write time by {@link #rejectOverlap}), so this only ever
     * needs to fold together directly-adjacent rows (e.g.
     * {@code 09:00-11:00 + 11:00-13:00 -> 09:00-13:00}) so a requested session
     * spanning across an adjacency boundary still evaluates as available
     * (PART 18).
     */
    private List<LocalTime[]> mergeAdjacent(List<FacultyAvailability> sortedByStart) {
        List<LocalTime[]> merged = new ArrayList<>();
        for (FacultyAvailability row : sortedByStart) {
            if (!merged.isEmpty() && !row.getStartTime().isAfter(merged.get(merged.size() - 1)[1])) {
                LocalTime[] last = merged.get(merged.size() - 1);
                if (row.getEndTime().isAfter(last[1])) {
                    last[1] = row.getEndTime();
                }
            } else {
                merged.add(new LocalTime[] {row.getStartTime(), row.getEndTime()});
            }
        }
        return merged;
    }

    private Faculty requireActiveFaculty(Long facultyId) {
        Faculty faculty = facultyService.getEntity(facultyId);
        if (!faculty.isActive()) {
            throw new ApiException("FACULTY_INACTIVE", HttpStatus.BAD_REQUEST, "Faculty is inactive: " + facultyId);
        }
        return faculty;
    }

    private AcademicTerm requireUsableTerm(Long academicTermId) {
        AcademicTerm term = academicTermService.getEntity(academicTermId);
        if (term.getStatus() == TermStatus.CLOSED) {
            throw new ApiException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                    "Cannot add faculty availability for a CLOSED academic term: " + academicTermId);
        }
        return term;
    }

    private void validateInterval(LocalTime start, LocalTime end) {
        if (!TimeIntervalUtils.isValid(start, end)) {
            throw new ApiException(
                    "INVALID_AVAILABILITY_INTERVAL", HttpStatus.BAD_REQUEST, "startTime must be strictly before endTime.");
        }
    }

    private void rejectOverlap(
            Long facultyId, Long academicTermId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end, Long excludeId) {
        List<FacultyAvailability> existing = availabilityRepository
                .findByFacultyIdAndAcademicTermIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        facultyId, academicTermId, dayOfWeek);
        for (FacultyAvailability row : existing) {
            if (excludeId != null && excludeId.equals(row.getId())) {
                continue;
            }
            if (TimeIntervalUtils.overlaps(row.getStartTime(), row.getEndTime(), start, end)) {
                throw new ApiException(
                        "FACULTY_AVAILABILITY_OVERLAP", HttpStatus.CONFLICT,
                        "Requested interval overlaps an existing active availability window: "
                                + row.getStartTime() + "-" + row.getEndTime());
            }
        }
    }

    private FacultyAvailability getOwnedEntity(Long facultyId, Long availabilityId) {
        FacultyAvailability availability = availabilityRepository
                .findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FACULTY_AVAILABILITY_NOT_FOUND", "Faculty availability not found: " + availabilityId));
        if (!availability.getFaculty().getId().equals(facultyId)) {
            throw new ResourceNotFoundException(
                    "FACULTY_AVAILABILITY_NOT_FOUND", "Faculty availability not found: " + availabilityId);
        }
        return availability;
    }
}
