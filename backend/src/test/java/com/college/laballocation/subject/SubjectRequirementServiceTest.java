package com.college.laballocation.subject;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Program;
import com.college.laballocation.audit.AuditLogService;
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
import com.college.laballocation.subject.SubjectRequirementDtos.SetLabTypeRequirementRequest;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SubjectRequirementService} - validation/rejection
 * paths that don't need a database (uniqueness itself is proven at the
 * integration level, since it depends on the real DB constraint - see
 * {@code SubjectRequirementApiIT}).
 */
@ExtendWith(MockitoExtension.class)
class SubjectRequirementServiceTest {

    @Mock
    private SubjectService subjectService;

    @Mock
    private SoftwareService softwareService;

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private LabTypeService labTypeService;

    @Mock
    private SubjectSoftwareRequirementRepository softwareRequirementRepository;

    @Mock
    private SubjectEquipmentRequirementRepository equipmentRequirementRepository;

    @Mock
    private AuditLogService auditLogService;

    private SubjectRequirementService service;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Subject subject() {
        Program program = new Program("BTECH", "B.Tech", 4);
        var stream = new com.college.laballocation.academic.Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        Subject subject = new Subject(year, "BDA", "Big Data Analytics");
        setId(subject, 1L);
        return subject;
    }

    @BeforeEach
    void setUp() {
        service = new SubjectRequirementService(
                subjectService,
                softwareService,
                equipmentService,
                labTypeService,
                softwareRequirementRepository,
                equipmentRequirementRepository,
                auditLogService);
    }

    @Test
    void addingInactiveSoftwareRequirementIsRejected() {
        Subject subject = subject();
        Software inactive = new Software("OLDTOOL", "Old Tool");
        inactive.update("Old Tool", false);
        when(subjectService.getEntity(1L)).thenReturn(subject);
        when(softwareService.getEntity(2L)).thenReturn(inactive);

        assertThatThrownBy(() -> service.addSoftwareRequirement(1L, new AddSoftwareRequirementRequest(2L), 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INACTIVE_SOFTWARE");
    }

    @Test
    void addingInactiveEquipmentRequirementIsRejected() {
        Subject subject = subject();
        Equipment inactive = new Equipment("OLDGEAR", "Old Gear", null);
        inactive.update("Old Gear", null, false);
        when(subjectService.getEntity(1L)).thenReturn(subject);
        when(equipmentService.getEntity(3L)).thenReturn(inactive);

        assertThatThrownBy(() -> service.addEquipmentRequirement(1L, new AddEquipmentRequirementRequest(3L, 5), 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INACTIVE_EQUIPMENT");
    }

    @Test
    void settingBothRequiredAndPreferredLabTypeIsRejected() {
        Subject subject = subject();
        LabType required = new LabType("DATA_ENGINEERING", "Data Engineering Lab", null);
        LabType preferred = new LabType("COMPUTER", "Computer Lab", null);
        when(subjectService.getEntity(1L)).thenReturn(subject);
        when(labTypeService.getEntity(10L)).thenReturn(required);
        when(labTypeService.getEntity(20L)).thenReturn(preferred);

        assertThatThrownBy(() -> service.setLabTypeRequirement(1L, new SetLabTypeRequirementRequest(10L, 20L), 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_LAB_TYPE_PREFERENCE");
    }

    @Test
    void settingOnlyRequiredLabTypeSucceeds() {
        Subject subject = subject();
        LabType required = new LabType("DATA_ENGINEERING", "Data Engineering Lab", null);
        when(subjectService.getEntity(1L)).thenReturn(subject);
        when(labTypeService.getEntity(10L)).thenReturn(required);

        assertThatCode(() -> service.setLabTypeRequirement(1L, new SetLabTypeRequirementRequest(10L, null), 1L))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(subject.getRequiredLabType()).isEqualTo(required);
        org.assertj.core.api.Assertions.assertThat(subject.getPreferredLabType()).isNull();
    }

    @Test
    void updatingQuantityForANonExistentRequirementIsNotFound() {
        when(equipmentRequirementRepository.findBySubjectIdAndEquipmentId(1L, 99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.updateEquipmentRequirementQuantity(1L, 99L, 5, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "SUBJECT_REQUIREMENT_NOT_FOUND");
    }
}
