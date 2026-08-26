package com.college.laballocation.faculty;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectFacultyAssignmentRepository extends JpaRepository<SubjectFacultyAssignment, Long> {

    Optional<SubjectFacultyAssignment> findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
            Long subjectId, Long divisionId, Long batchId, Long academicTermId);

    Optional<SubjectFacultyAssignment> findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
            Long subjectId, Long divisionId, Long academicTermId);

    /**
     * Bulk-loaded once per PDF import (Phase 19, PART 68 - never one query
     * per row): the authoritative source this project already has for
     * "which subject+division+batch+faculty combinations are valid for this
     * term" (the same table {@code SchedulingContextFactory} resolves
     * faculty from), reused directly by {@code TimetableMappingService}
     * instead of independently guessing at faculty-name matches.
     */
    List<SubjectFacultyAssignment> findByAcademicTermIdAndActiveTrue(Long academicTermId);
}
