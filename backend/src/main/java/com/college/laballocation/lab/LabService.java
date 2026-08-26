package com.college.laballocation.lab;

import com.college.laballocation.audit.AuditAction;
import com.college.laballocation.audit.AuditEvent;
import com.college.laballocation.audit.AuditLogService;
import com.college.laballocation.audit.AuditResourceType;
import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.LabDtos.CreateLabRequest;
import com.college.laballocation.lab.LabDtos.InstalledEquipmentItem;
import com.college.laballocation.lab.LabDtos.InstalledSoftwareItem;
import com.college.laballocation.lab.LabDtos.LabResponse;
import com.college.laballocation.lab.LabDtos.LabSummaryResponse;
import com.college.laballocation.lab.LabDtos.UpdateLabRequest;
import com.college.laballocation.user.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LabService {

    private final LabRepository labRepository;
    private final LabTypeService labTypeService;
    private final LabSoftwareRepository labSoftwareRepository;
    private final LabEquipmentRepository labEquipmentRepository;
    private final AuditLogService auditLogService;

    public LabService(
            LabRepository labRepository,
            LabTypeService labTypeService,
            LabSoftwareRepository labSoftwareRepository,
            LabEquipmentRepository labEquipmentRepository,
            AuditLogService auditLogService) {
        this.labRepository = labRepository;
        this.labTypeService = labTypeService;
        this.labSoftwareRepository = labSoftwareRepository;
        this.labEquipmentRepository = labEquipmentRepository;
        this.auditLogService = auditLogService;
    }

    public Lab getEntity(Long id) {
        return labRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LAB_NOT_FOUND", "Lab not found: " + id));
    }

    public LabResponse get(Long id) {
        return toFullResponse(getEntity(id));
    }

    /**
     * Static capability search (PART 33/34/36/37) - filters on the lab's own
     * fixed properties only. Never implies schedule-time availability; see
     * {@link LabSpecifications} javadoc.
     */
    public List<LabSummaryResponse> search(
            Boolean active, String wing, String labTypeCode, Integer minCapacity, List<String> softwareCodes, List<String> equipmentCodes) {
        Specification<Lab> spec = Specification.allOf();
        spec = spec.and(LabSpecifications.active(active == null || active));
        if (wing != null) {
            spec = spec.and(LabSpecifications.wing(wing));
        }
        if (labTypeCode != null) {
            spec = spec.and(LabSpecifications.labTypeCode(labTypeCode));
        }
        if (minCapacity != null) {
            spec = spec.and(LabSpecifications.minCapacity(minCapacity));
        }
        if (softwareCodes != null && !softwareCodes.isEmpty()) {
            spec = spec.and(LabSpecifications.hasAllSoftware(softwareCodes));
        }
        if (equipmentCodes != null && !equipmentCodes.isEmpty()) {
            spec = spec.and(LabSpecifications.hasAllEquipment(equipmentCodes));
        }
        return labRepository.findAll(spec).stream().map(LabSummaryResponse::from).toList();
    }

    @Transactional
    public LabResponse create(CreateLabRequest request, Long actingUserId) {
        if (labRepository.existsByCode(request.code())) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Lab code already exists: " + request.code());
        }
        LabType labType = labTypeService.getEntity(request.labTypeId());
        Lab saved = labRepository.save(new Lab(
                request.code(), request.name(), request.capacity(), labType, request.wing(), request.floor(), request.roomNumber()));
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.LAB_CREATED, AuditResourceType.LAB, saved.getId(),
                saved.getCode(), null, null,
                Map.of("labCode", saved.getCode(), "capacity", saved.getCapacity(), "labTypeCode", labType.getCode())));
        return toFullResponse(saved);
    }

    /** {@code code} is deliberately not updatable - see {@link Lab} javadoc. */
    @Transactional
    public LabResponse update(Long id, UpdateLabRequest request, Long actingUserId) {
        Lab lab = getEntity(id);
        LabType labType = labTypeService.getEntity(request.labTypeId());
        lab.update(request.name(), request.capacity(), labType, request.wing(), request.floor(), request.roomNumber(), request.active());
        auditLogService.record(new AuditEvent(
                actingUserId, UserRole.LAB_ASSISTANT, AuditAction.LAB_UPDATED, AuditResourceType.LAB, lab.getId(),
                lab.getCode(), null, null,
                Map.of("labCode", lab.getCode(), "capacity", lab.getCapacity(), "active", lab.isActive())));
        return toFullResponse(lab);
    }

    private LabResponse toFullResponse(Lab lab) {
        List<InstalledSoftwareItem> software = labSoftwareRepository.findByLabId(lab.getId()).stream()
                .map(ls -> new InstalledSoftwareItem(
                        ls.getSoftware().getId(), ls.getSoftware().getCode(), ls.getSoftware().getName(), ls.getInstalledVersion()))
                .toList();
        List<InstalledEquipmentItem> equipment = labEquipmentRepository.findByLabId(lab.getId()).stream()
                .map(le -> new InstalledEquipmentItem(
                        le.getEquipment().getId(), le.getEquipment().getCode(), le.getEquipment().getName(), le.getQuantity()))
                .toList();
        return LabResponse.from(lab, software, equipment);
    }
}
