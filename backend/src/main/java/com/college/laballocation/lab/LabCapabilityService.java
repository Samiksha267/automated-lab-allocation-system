package com.college.laballocation.lab;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.common.ResourceNotFoundException;
import com.college.laballocation.lab.LabDtos.InstalledEquipmentItem;
import com.college.laballocation.lab.LabDtos.InstalledSoftwareItem;
import com.college.laballocation.lab.LabEquipmentDtos.AssignLabEquipmentRequest;
import com.college.laballocation.lab.LabSoftwareDtos.AddLabSoftwareRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages what a lab HAS (installed software/equipment) - distinct from what
 * a subject REQUIRES, which does not exist until Phase 6 (PART 12 of the
 * phase brief). Kept separate from {@link LabService} (basic Lab CRUD) since
 * capability management is its own cohesive concern, not to avoid one giant
 * "LaboratoryService" that does everything.
 *
 * <p>Association rows are hard-deleted on removal (no soft-cancel status) -
 * before any allocation exists to reference a specific installation, there
 * is no historical evidence to preserve; once the scheduling engine exists,
 * this decision should be revisited if allocation explanations ever need to
 * say "this lab had Cloudera at the time it was scheduled."
 */
@Service
@Transactional(readOnly = true)
public class LabCapabilityService {

    private final LabService labService;
    private final SoftwareService softwareService;
    private final EquipmentService equipmentService;
    private final LabSoftwareRepository labSoftwareRepository;
    private final LabEquipmentRepository labEquipmentRepository;

    public LabCapabilityService(
            LabService labService,
            SoftwareService softwareService,
            EquipmentService equipmentService,
            LabSoftwareRepository labSoftwareRepository,
            LabEquipmentRepository labEquipmentRepository) {
        this.labService = labService;
        this.softwareService = softwareService;
        this.equipmentService = equipmentService;
        this.labSoftwareRepository = labSoftwareRepository;
        this.labEquipmentRepository = labEquipmentRepository;
    }

    public List<InstalledSoftwareItem> listSoftware(Long labId) {
        Lab lab = labService.getEntity(labId);
        return labSoftwareRepository.findByLabId(lab.getId()).stream()
                .map(ls -> new InstalledSoftwareItem(
                        ls.getSoftware().getId(), ls.getSoftware().getCode(), ls.getSoftware().getName(), ls.getInstalledVersion()))
                .toList();
    }

    @Transactional
    public InstalledSoftwareItem addSoftware(Long labId, AddLabSoftwareRequest request) {
        Lab lab = labService.getEntity(labId);
        Software software = softwareService.getEntity(request.softwareId());
        if (labSoftwareRepository.existsByLabIdAndSoftwareId(lab.getId(), software.getId())) {
            throw new ApiException(
                    "LAB_SOFTWARE_ALREADY_ASSIGNED", HttpStatus.CONFLICT,
                    "Software " + software.getCode() + " is already installed in lab " + lab.getCode() + ".");
        }
        LabSoftware saved = labSoftwareRepository.save(new LabSoftware(lab, software, request.installedVersion()));
        return new InstalledSoftwareItem(software.getId(), software.getCode(), software.getName(), saved.getInstalledVersion());
    }

    @Transactional
    public void removeSoftware(Long labId, Long softwareId) {
        LabSoftware labSoftware = labSoftwareRepository
                .findByLabIdAndSoftwareId(labId, softwareId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LAB_SOFTWARE_NOT_FOUND", "Software " + softwareId + " is not installed in lab " + labId + "."));
        labSoftwareRepository.delete(labSoftware);
    }

    public List<InstalledEquipmentItem> listEquipment(Long labId) {
        Lab lab = labService.getEntity(labId);
        return labEquipmentRepository.findByLabId(lab.getId()).stream()
                .map(le -> new InstalledEquipmentItem(
                        le.getEquipment().getId(), le.getEquipment().getCode(), le.getEquipment().getName(), le.getQuantity()))
                .toList();
    }

    @Transactional
    public InstalledEquipmentItem assignEquipment(Long labId, AssignLabEquipmentRequest request) {
        Lab lab = labService.getEntity(labId);
        Equipment equipment = equipmentService.getEntity(request.equipmentId());
        if (labEquipmentRepository.existsByLabIdAndEquipmentId(lab.getId(), equipment.getId())) {
            throw new ApiException(
                    "LAB_EQUIPMENT_ALREADY_ASSIGNED", HttpStatus.CONFLICT,
                    "Equipment " + equipment.getCode() + " is already assigned to lab " + lab.getCode() + ".");
        }
        LabEquipment saved = labEquipmentRepository.save(new LabEquipment(lab, equipment, request.quantity()));
        return new InstalledEquipmentItem(equipment.getId(), equipment.getCode(), equipment.getName(), saved.getQuantity());
    }

    @Transactional
    public InstalledEquipmentItem updateEquipmentQuantity(Long labId, Long equipmentId, int quantity) {
        LabEquipment labEquipment = labEquipmentRepository
                .findByLabIdAndEquipmentId(labId, equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LAB_EQUIPMENT_NOT_FOUND", "Equipment " + equipmentId + " is not assigned to lab " + labId + "."));
        labEquipment.updateQuantity(quantity);
        return new InstalledEquipmentItem(
                labEquipment.getEquipment().getId(), labEquipment.getEquipment().getCode(), labEquipment.getEquipment().getName(), quantity);
    }

    @Transactional
    public void removeEquipment(Long labId, Long equipmentId) {
        LabEquipment labEquipment = labEquipmentRepository
                .findByLabIdAndEquipmentId(labId, equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LAB_EQUIPMENT_NOT_FOUND", "Equipment " + equipmentId + " is not assigned to lab " + labId + "."));
        labEquipmentRepository.delete(labEquipment);
    }
}
