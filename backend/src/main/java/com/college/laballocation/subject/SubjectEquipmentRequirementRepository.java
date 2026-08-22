package com.college.laballocation.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectEquipmentRequirementRepository extends JpaRepository<SubjectEquipmentRequirement, Long> {
    List<SubjectEquipmentRequirement> findBySubjectIdOrderByEquipment_Code(Long subjectId);

    Optional<SubjectEquipmentRequirement> findBySubjectIdAndEquipmentId(Long subjectId, Long equipmentId);

    boolean existsBySubjectIdAndEquipmentId(Long subjectId, Long equipmentId);
}
