package com.college.laballocation.faculty;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectFacultyAssignmentRepository extends JpaRepository<SubjectFacultyAssignment, Long> {

    Optional<SubjectFacultyAssignment> findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
            Long subjectId, Long divisionId, Long batchId, Long academicTermId);

    Optional<SubjectFacultyAssignment> findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
            Long subjectId, Long divisionId, Long academicTermId);
}
