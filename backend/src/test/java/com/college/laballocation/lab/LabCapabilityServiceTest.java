package com.college.laballocation.lab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.common.ApiException;
import com.college.laballocation.lab.LabEquipmentDtos.AssignLabEquipmentRequest;
import com.college.laballocation.lab.LabSoftwareDtos.AddLabSoftwareRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Proves duplicate lab-software / lab-equipment assignment is rejected with a specific conflict code, not a raw DB error. */
@ExtendWith(MockitoExtension.class)
class LabCapabilityServiceTest {

    @Mock
    private LabService labService;

    @Mock
    private SoftwareService softwareService;

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private LabSoftwareRepository labSoftwareRepository;

    @Mock
    private LabEquipmentRepository labEquipmentRepository;

    private LabCapabilityService service;

    private Lab lab() {
        LabType type = new LabType("COMPUTER", "Computer Lab", null);
        return new Lab("C-999", "Test Lab", 60, type, "C", "1", "999");
    }

    @Test
    void duplicateSoftwareAssignmentIsRejected() {
        service = new LabCapabilityService(
                labService, softwareService, equipmentService, labSoftwareRepository, labEquipmentRepository);
        Lab lab = lab();
        Software software = new Software("CLOUDERA", "Cloudera");
        when(labService.getEntity(1L)).thenReturn(lab);
        when(softwareService.getEntity(2L)).thenReturn(software);
        when(labSoftwareRepository.existsByLabIdAndSoftwareId(lab.getId(), software.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.addSoftware(1L, new AddLabSoftwareRequest(2L, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_SOFTWARE_ALREADY_ASSIGNED");
    }

    @Test
    void duplicateEquipmentAssignmentIsRejected() {
        service = new LabCapabilityService(
                labService, softwareService, equipmentService, labSoftwareRepository, labEquipmentRepository);
        Lab lab = lab();
        Equipment equipment = new Equipment("ROUTER", "Router", null);
        when(labService.getEntity(1L)).thenReturn(lab);
        when(equipmentService.getEntity(3L)).thenReturn(equipment);
        when(labEquipmentRepository.existsByLabIdAndEquipmentId(lab.getId(), equipment.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.assignEquipment(1L, new AssignLabEquipmentRequest(3L, 5)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "LAB_EQUIPMENT_ALREADY_ASSIGNED");
    }
}
