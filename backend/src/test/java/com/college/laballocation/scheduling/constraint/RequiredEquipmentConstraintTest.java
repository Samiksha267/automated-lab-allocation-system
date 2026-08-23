package com.college.laballocation.scheduling.constraint;

import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.SUBJECT_ID;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchContext;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.batchRequest;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.candidate;
import static com.college.laballocation.scheduling.constraint.SchedulingFixtures.lab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.college.laballocation.academic.AcademicYear;
import com.college.laballocation.academic.Program;
import com.college.laballocation.academic.Stream;
import com.college.laballocation.lab.Equipment;
import com.college.laballocation.scheduling.ConstraintOutcome;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.scheduling.SchedulingRefs.LabRef;
import com.college.laballocation.subject.Subject;
import com.college.laballocation.subject.SubjectEquipmentRequirement;
import com.college.laballocation.subject.SubjectEquipmentRequirementRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequiredEquipmentConstraintTest {

    @Mock
    private SubjectEquipmentRequirementRepository requirementRepository;

    private Subject subject() {
        Program program = new Program("BTECH", "B.Tech", 4);
        Stream stream = new Stream(program, "CS", "Computer Science");
        AcademicYear year = new AcademicYear(stream, 3);
        return new Subject(year, "BDA", "Big Data Analytics");
    }

    private SubjectEquipmentRequirement requirement(String code, int quantity) {
        return new SubjectEquipmentRequirement(subject(), new Equipment(code, code, null), quantity);
    }

    private LabRef labWithEquipment(Map<String, Integer> quantities) {
        return lab(70, 20L, "COMPUTER", Set.of(), quantities, List.of(), List.of());
    }

    @Test
    void idIsHc09() {
        RequiredEquipmentConstraint constraint = new RequiredEquipmentConstraint(requirementRepository);
        assertThat(constraint.id()).isEqualTo(HardConstraintId.HC_09_REQUIRED_EQUIPMENT);
    }

    @Test
    void emptyRequirementsAlwaysPass() {
        when(requirementRepository.findBySubjectIdOrderByEquipment_Code(SUBJECT_ID)).thenReturn(List.of());
        RequiredEquipmentConstraint constraint = new RequiredEquipmentConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithEquipment(Map.of())));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void passesWhenLabQuantityMeetsRequirement() {
        when(requirementRepository.findBySubjectIdOrderByEquipment_Code(SUBJECT_ID)).thenReturn(List.of(requirement("ROUTER", 10)));
        RequiredEquipmentConstraint constraint = new RequiredEquipmentConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithEquipment(Map.of("ROUTER", 12))));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.PASS);
    }

    @Test
    void failsWhenLabQuantityIsBelowRequirement() {
        when(requirementRepository.findBySubjectIdOrderByEquipment_Code(SUBJECT_ID)).thenReturn(List.of(requirement("ROUTER", 10)));
        RequiredEquipmentConstraint constraint = new RequiredEquipmentConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithEquipment(Map.of("ROUTER", 5))));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        assertThat(result.violation().errorCode()).isEqualTo("EQUIPMENT_MISMATCH");
    }

    /** PART 31 - no association row at all means available quantity 0, not a null-pointer failure. */
    @Test
    void missingEquipmentAssociationIsTreatedAsZeroAvailable() {
        when(requirementRepository.findBySubjectIdOrderByEquipment_Code(SUBJECT_ID)).thenReturn(List.of(requirement("ROUTER", 10)));
        RequiredEquipmentConstraint constraint = new RequiredEquipmentConstraint(requirementRepository);
        SchedulingContext context = batchContext(batchRequest(LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ConstraintResult result = constraint.evaluate(context, candidate(context, labWithEquipment(Map.of())));

        assertThat(result.outcome()).isEqualTo(ConstraintOutcome.FAIL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shortfalls = (List<Map<String, Object>>) result.violation().details().get("shortfalls");
        assertThat(shortfalls).hasSize(1);
        assertThat(shortfalls.get(0)).containsEntry("available", 0).containsEntry("required", 10);
    }
}
