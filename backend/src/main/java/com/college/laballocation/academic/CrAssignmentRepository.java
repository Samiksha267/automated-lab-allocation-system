package com.college.laballocation.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrAssignmentRepository extends JpaRepository<CrAssignment, Long> {

    Optional<CrAssignment> findByUserIdAndAcademicTermIdAndStatus(
            Long userId, Long academicTermId, CrAssignmentStatus status);

    Optional<CrAssignment> findByDivisionIdAndAcademicTermIdAndStatus(
            Long divisionId, Long academicTermId, CrAssignmentStatus status);

    List<CrAssignment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CrAssignment> findByDivisionIdOrderByCreatedAtDesc(Long divisionId);
}
