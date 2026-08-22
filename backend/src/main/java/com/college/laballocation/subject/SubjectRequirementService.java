package com.college.laballocation.subject;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.Equipment;
import com.college.laballocation.lab.EquipmentService;
import com.college.laballocation.lab.LabType;
import com.college.laballocation.lab.LabTypeService;
import com.college.laballocation.lab.Software;
import com.college.laballocation.lab.SoftwareService;
import com.college.laballocation.subject.SubjectRequirementDtos.AddEquipmentRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.AddSoftwareRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.EquipmentSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.LabTypeSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.SetLabTypeRequirementRequest;
import com.college.laballocation.subject.SubjectRequirementDtos.SoftwareSummary;
import com.college.laballocation.subject.SubjectRequirementDtos.SubjectRequirementsResponse;
import com.college.laballocation.subject.SubjectRequirementDtos.SubjectSummary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages what a SUBJECT requires - the mirror image of
 * {@link com.college.laballocation.lab.LabCapabilityService}, which manages
 * what a LAB has. This service only records requirements; it never answers
 * "can lab X host this subject" - that comparison belongs to the future
 * Constraint Engine (Phase 9+), which will read this data and Phase 5's lab
 * capability data as two independent inputs (docs/03-SYSTEM-ARCHITECTURE.md).
 */
@Service
@Transactional(readOnly = true)
public class SubjectRequirementService {

    private final SubjectService subjectService;
    private final SoftwareService softwareService;
    private final EquipmentService equipmentService;
    private final LabTypeService labTypeService;
    private final SubjectSoftwareRequirementRepository softwareRequirementRepository;
    private final SubjectEquipmentRequirementRepository equipmentRequirementRepository;

    public SubjectRequirementService(
            SubjectService subjectService,
            SoftwareService softwareService,
            EquipmentService equipmentService,
            LabTypeService labTypeService,
            SubjectSoftwareRequirementRepository softwareRequirementRepository,
            SubjectEquipmentRequirementRepository equipmentRequirementRepository) {
        this.subjectService = subjectService;
        this.softwareService = softwareService;
        this.equipmentService = equipmentService;
        this.labTypeService = labTypeService;
        this.softwareRequirementRepository = softwareRequirementRepository;
        this.equipmentRequirementRepository = equipmentRequirementRepository;
    }

    public SubjectRequirementsResponse get(Long subjectId) {
        Subject subject = subjectService.getEntity(subjectId);

        var software = softwareRequirementRepository.findBySubjectIdOrderBySoftware_Code(subjectId).stream()
                .map(r -> SoftwareSummary.from(r.getSoftware()))
                .toList();
        var equipment = equipmentRequirementRepository.findBySubjectIdOrderByEquipment_Code(subjectId).stream()
                .map(r -> EquipmentSummary.from(r.getEquipment(), r.getRequiredQuantity()))
                .toList();

        return new SubjectRequirementsResponse(
                SubjectSummary.from(subject),
                software,
                equipment,
                LabTypeSummary.from(subject.getRequiredLabType()),
                LabTypeSummary.from(subject.getPreferredLabType()));
    }

    @Transactional
    public SoftwareSummary addSoftwareRequirement(Long subjectId, AddSoftwareRequirementRequest request) {
        Subject subject = subjectService.getEntity(subjectId);
        Software software = softwareService.getEntity(request.softwareId());
        requireActive(software.isActive(), "INACTIVE_SOFTWARE", "Software " + software.getCode() + " is not active.");

        try {
            softwareRequirementRepository.save(new SubjectSoftwareRequirement(subject, software));
            softwareRequirementRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "SOFTWARE_REQUIREMENT_ALREADY_EXISTS", HttpStatus.CONFLICT,
                    "Subject " + subject.getCode() + " already requires " + software.getCode() + ".");
        }
        return SoftwareSummary.from(software);
    }

    @Transactional
    public void removeSoftwareRequirement(Long subjectId, Long softwareId) {
        SubjectSoftwareRequirement requirement = softwareRequirementRepository
                .findBySubjectIdAndSoftwareId(subjectId, softwareId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_REQUIREMENT_NOT_FOUND", "No software requirement " + softwareId + " for subject " + subjectId + "."));
        softwareRequirementRepository.delete(requirement);
    }

    @Transactional
    public EquipmentSummary addEquipmentRequirement(Long subjectId, AddEquipmentRequirementRequest request) {
        Subject subject = subjectService.getEntity(subjectId);
        Equipment equipment = equipmentService.getEntity(request.equipmentId());
        requireActive(equipment.isActive(), "INACTIVE_EQUIPMENT", "Equipment " + equipment.getCode() + " is not active.");

        try {
            SubjectEquipmentRequirement saved = equipmentRequirementRepository.save(
                    new SubjectEquipmentRequirement(subject, equipment, request.requiredQuantity()));
            equipmentRequirementRepository.flush();
            return EquipmentSummary.from(equipment, saved.getRequiredQuantity());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "EQUIPMENT_REQUIREMENT_ALREADY_EXISTS", HttpStatus.CONFLICT,
                    "Subject " + subject.getCode() + " already requires " + equipment.getCode() + ".");
        }
    }

    @Transactional
    public EquipmentSummary updateEquipmentRequirementQuantity(Long subjectId, Long equipmentId, int requiredQuantity) {
        SubjectEquipmentRequirement requirement = equipmentRequirementRepository
                .findBySubjectIdAndEquipmentId(subjectId, equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_REQUIREMENT_NOT_FOUND", "No equipment requirement " + equipmentId + " for subject " + subjectId + "."));
        requirement.updateRequiredQuantity(requiredQuantity);
        return EquipmentSummary.from(requirement.getEquipment(), requiredQuantity);
    }

    @Transactional
    public void removeEquipmentRequirement(Long subjectId, Long equipmentId) {
        SubjectEquipmentRequirement requirement = equipmentRequirementRepository
                .findBySubjectIdAndEquipmentId(subjectId, equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_REQUIREMENT_NOT_FOUND", "No equipment requirement " + equipmentId + " for subject " + subjectId + "."));
        equipmentRequirementRepository.delete(requirement);
    }

    /**
     * {@code requiredLabTypeId} and {@code preferredLabTypeId} - at most one
     * may be non-null (enforced by {@link Subject#setLabTypeRequirement}
     * and, redundantly, a database CHECK constraint). Passing both null
     * clears any existing lab-type requirement.
     */
    @Transactional
    public LabTypeSummary setLabTypeRequirement(Long subjectId, SetLabTypeRequirementRequest request) {
        Subject subject = subjectService.getEntity(subjectId);

        LabType required = null;
        if (request.requiredLabTypeId() != null) {
            required = labTypeService.getEntity(request.requiredLabTypeId());
            requireActive(required.isActive(), "INACTIVE_LAB_TYPE", "Lab type " + required.getCode() + " is not active.");
        }
        LabType preferred = null;
        if (request.preferredLabTypeId() != null) {
            preferred = labTypeService.getEntity(request.preferredLabTypeId());
            requireActive(preferred.isActive(), "INACTIVE_LAB_TYPE", "Lab type " + preferred.getCode() + " is not active.");
        }

        subject.setLabTypeRequirement(required, preferred);
        return LabTypeSummary.from(required != null ? required : preferred);
    }

    @Transactional
    public void clearLabTypeRequirement(Long subjectId) {
        Subject subject = subjectService.getEntity(subjectId);
        subject.setLabTypeRequirement(null, null);
    }

    private void requireActive(boolean active, String code, String message) {
        if (!active) {
            throw new ApiException(code, HttpStatus.BAD_REQUEST, message);
        }
    }
}
