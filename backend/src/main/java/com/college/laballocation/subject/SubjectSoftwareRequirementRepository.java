package com.college.laballocation.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectSoftwareRequirementRepository extends JpaRepository<SubjectSoftwareRequirement, Long> {
    List<SubjectSoftwareRequirement> findBySubjectIdOrderBySoftware_Code(Long subjectId);

    Optional<SubjectSoftwareRequirement> findBySubjectIdAndSoftwareId(Long subjectId, Long softwareId);

    boolean existsBySubjectIdAndSoftwareId(Long subjectId, Long softwareId);
}
