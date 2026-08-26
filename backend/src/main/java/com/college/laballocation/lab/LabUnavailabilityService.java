package com.college.laballocation.lab;

import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.LabUnavailabilityDtos.CreateLabUnavailabilityRequest;
import com.college.laballocation.lab.LabUnavailabilityDtos.LabUnavailabilityResponse;
import com.college.laballocation.user.AppUser;
import com.college.laballocation.user.UserRepository;
import com.college.laballocation.user.UserRole;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LabUnavailabilityService {

    private final LabUnavailabilityRepository unavailabilityRepository;
    private final LabService labService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public LabUnavailabilityService(
            LabUnavailabilityRepository unavailabilityRepository,
            LabService labService,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.unavailabilityRepository = unavailabilityRepository;
        this.labService = labService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public List<LabUnavailabilityResponse> listByLab(Long labId) {
        return unavailabilityRepository.findByLabIdOrderByStartDateTime(labId).stream()
                .map(LabUnavailabilityResponse::from)
                .toList();
    }

    @Transactional
    public LabUnavailabilityResponse create(Long labId, CreateLabUnavailabilityRequest request, Long createdByUserId) {
        if (!request.endDateTime().isAfter(request.startDateTime())) {
            throw new ApiException(
                    "INVALID_UNAVAILABILITY_INTERVAL", HttpStatus.BAD_REQUEST, "endDateTime must be after startDateTime.");
        }
        Lab lab = labService.getEntity(labId);
        AppUser createdBy = userRepository
                .findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + createdByUserId));

        LabUnavailability saved = unavailabilityRepository.save(
                new LabUnavailability(lab, request.startDateTime(), request.endDateTime(), request.reason(), createdBy));
        auditLogService.record(new AuditEvent(
                createdByUserId, UserRole.LAB_ASSISTANT, AuditAction.LAB_UNAVAILABILITY_CHANGED, AuditResourceType.LAB, lab.getId(),
                lab.getCode(), null, null,
                Map.of(
                        "labCode", lab.getCode(), "operation", "CREATED",
                        "startDateTime", request.startDateTime().toString(), "endDateTime", request.endDateTime().toString())));
        return LabUnavailabilityResponse.from(saved);
    }

    /** Hard delete - see {@link LabUnavailability} javadoc for why no soft-cancel status exists yet. */
    @Transactional
    public void remove(Long id, Long actingUserId) {
        LabUnavailability unavailability = unavailabilityRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_UNAVAILABILITY_NOT_FOUND", "Lab unavailability not found: " + id));
        Lab lab = unavailability.getLab();
        unavailabilityRepository.deleteById(id);
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.LAB_UNAVAILABILITY_CHANGED, AuditResourceType.LAB, lab.getId(),
                lab.getCode(), null, null,
                Map.of("labCode", lab.getCode(), "operation", "REMOVED")));
    }
}
