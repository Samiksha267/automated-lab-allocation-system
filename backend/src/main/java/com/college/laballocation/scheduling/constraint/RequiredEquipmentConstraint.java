package com.college.laballocation.scheduling.constraint;

import com.college.laballocation.scheduling.CandidateAllocation;
import com.college.laballocation.scheduling.ConstraintResult;
import com.college.laballocation.scheduling.ConstraintViolation;
import com.college.laballocation.scheduling.HardConstraintId;
import com.college.laballocation.scheduling.SchedulingContext;
import com.college.laballocation.subject.SubjectEquipmentRequirement;
import com.college.laballocation.subject.SubjectEquipmentRequirementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HC-09 - Required Equipment. For every {@code (equipment, requiredQuantity)}
 * the subject requires, the candidate lab's available quantity must be ≥ the
 * required quantity. A lab with no association row for a required equipment
 * item has an available quantity of 0 (never a null-pointer failure) -
 * {@code candidate.lab().equipmentQuantities()} is a plain
 * {@code Map<String, Integer>}, so a missing key naturally defaults via
 * {@code getOrDefault(code, 0)}. An empty requirement set always passes.
 */
@Component
public class RequiredEquipmentConstraint implements SchedulingConstraint {

    private final SubjectEquipmentRequirementRepository requirementRepository;

    public RequiredEquipmentConstraint(SubjectEquipmentRequirementRepository requirementRepository) {
        this.requirementRepository = requirementRepository;
    }

    @Override
    public HardConstraintId id() {
        return HardConstraintId.HC_09_REQUIRED_EQUIPMENT;
    }

    @Override
    public ConstraintResult evaluate(SchedulingContext context, CandidateAllocation candidate) {
        List<SubjectEquipmentRequirement> required =
                requirementRepository.findBySubjectIdOrderByEquipment_Code(context.request().subjectId());

        List<Map<String, Object>> shortfalls = new ArrayList<>();
        for (SubjectEquipmentRequirement requirement : required) {
            String code = requirement.getEquipment().getCode();
            int available = candidate.lab().equipmentQuantities().getOrDefault(code, 0);
            if (available < requirement.getRequiredQuantity()) {
                shortfalls.add(Map.of(
                        "equipment", code,
                        "required", requirement.getRequiredQuantity(),
                        "available", available));
            }
        }

        if (!shortfalls.isEmpty()) {
            String summary = shortfalls.stream()
                    .map(s -> s.get("equipment") + " (needs " + s.get("required") + ", has " + s.get("available") + ")")
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            return ConstraintResult.fail(
                    id(),
                    new ConstraintViolation(
                            "EQUIPMENT_MISMATCH",
                            "Lab " + candidate.lab().code() + " does not provide enough required equipment: " + summary + ".",
                            "EQUIPMENT",
                            candidate.lab().code(),
                            Map.of("shortfalls", shortfalls)));
        }
        return ConstraintResult.pass(id());
    }
}
