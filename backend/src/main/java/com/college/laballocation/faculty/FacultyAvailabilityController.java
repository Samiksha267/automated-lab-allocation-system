package com.college.laballocation.faculty;

import com.college.laballocation.faculty.FacultyAvailabilityDtos.AvailabilityCheckResponse;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.CreateFacultyAvailabilityRequest;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.FacultyAvailabilityResponse;
import com.college.laballocation.faculty.FacultyAvailabilityDtos.UpdateFacultyAvailabilityRequest;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raw faculty-availability administration - deliberately restricted to
 * LAB_ASSISTANT for read and write alike (PART 22 of the phase brief): this
 * is management data with no legitimate CR/STUDENT consumer yet; the future
 * constraint engine (Phase 9+) will consume {@link FacultyAvailabilityService}
 * internally, not through this REST surface.
 */
@RestController
@RequestMapping("/api/faculty/{facultyId}/availability")
public class FacultyAvailabilityController {

    private final FacultyAvailabilityService availabilityService;

    public FacultyAvailabilityController(FacultyAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public List<FacultyAvailabilityResponse> list(
            @PathVariable Long facultyId,
            @RequestParam(required = false) Long academicTermId,
            @RequestParam(required = false) DayOfWeek dayOfWeek) {
        return availabilityService.list(facultyId, academicTermId, dayOfWeek);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public FacultyAvailabilityResponse create(
            @PathVariable Long facultyId,
            @Valid @RequestBody CreateFacultyAvailabilityRequest request,
            @AuthenticationPrincipal Long userId) {
        return availabilityService.create(facultyId, request, userId);
    }

    @PatchMapping("/{availabilityId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public FacultyAvailabilityResponse update(
            @PathVariable Long facultyId,
            @PathVariable Long availabilityId,
            @Valid @RequestBody UpdateFacultyAvailabilityRequest request,
            @AuthenticationPrincipal Long userId) {
        return availabilityService.update(facultyId, availabilityId, request, userId);
    }

    @DeleteMapping("/{availabilityId}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void remove(@PathVariable Long facultyId, @PathVariable Long availabilityId, @AuthenticationPrincipal Long userId) {
        availabilityService.remove(facultyId, availabilityId, userId);
    }

    /** Administrative preview only (PART 24) - "availability check," never scheduling/conflict validation. */
    @GetMapping("/check")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public AvailabilityCheckResponse check(
            @PathVariable Long facultyId,
            @RequestParam Long academicTermId,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime endTime) {
        return availabilityService.check(facultyId, academicTermId, dayOfWeek, startTime, endTime);
    }
}
