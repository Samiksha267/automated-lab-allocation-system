package com.college.laballocation.faculty;

import com.college.laballocation.common.ResourceNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves "which faculty teaches this subject for this batch/division, this
 * term" (PART 13 of the phase brief). Not used by any allocation logic yet
 * (that's Phase 9+) - exists now because it's a query the future scheduling
 * engine needs, and belongs conceptually to the academic/faculty domain, not
 * the scheduler.
 *
 * <p>Resolution order for a BATCH-scoped lookup:
 * <ol>
 *   <li>exact batch-level assignment (subject + division + batch + term)</li>
 *   <li>if absent, division-level assignment (subject + division + term, batch = null)</li>
 *   <li>if still absent, no resolvable assignment - reported as a clear
 *       "not found" error, never silently guessed</li>
 * </ol>
 * Ambiguity (two equally valid assignments) cannot occur at read time: the
 * two partial unique indexes on {@code subject_faculty_assignment} (V4
 * migration) prevent more than one *active* row from ever existing for the
 * same batch-scoped or division-scoped combination.
 *
 * <p>A DIVISION-scoped lookup only ever considers the division-level
 * assignment - there is no "batch" to fall back from.
 */
@Service
@Transactional(readOnly = true)
public class FacultyAssignmentResolutionService {

    private final SubjectFacultyAssignmentRepository assignmentRepository;

    public FacultyAssignmentResolutionService(SubjectFacultyAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public SubjectFacultyAssignment resolveForBatch(Long subjectId, Long divisionId, Long batchId, Long academicTermId) {
        return resolveForBatchIfPresent(subjectId, divisionId, batchId, academicTermId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND",
                        "No faculty assignment found for subject " + subjectId + ", division " + divisionId
                                + ", batch " + batchId + ", term " + academicTermId + "."));
    }

    public Optional<SubjectFacultyAssignment> resolveForBatchIfPresent(
            Long subjectId, Long divisionId, Long batchId, Long academicTermId) {
        Optional<SubjectFacultyAssignment> exact = assignmentRepository
                .findBySubjectIdAndDivisionIdAndBatchIdAndAcademicTermIdAndActiveTrue(
                        subjectId, divisionId, batchId, academicTermId);
        if (exact.isPresent()) {
            return exact;
        }
        return resolveForDivisionIfPresent(subjectId, divisionId, academicTermId);
    }

    public SubjectFacultyAssignment resolveForDivision(Long subjectId, Long divisionId, Long academicTermId) {
        return resolveForDivisionIfPresent(subjectId, divisionId, academicTermId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SUBJECT_FACULTY_ASSIGNMENT_NOT_FOUND",
                        "No division-level faculty assignment found for subject " + subjectId + ", division "
                                + divisionId + ", term " + academicTermId + "."));
    }

    public Optional<SubjectFacultyAssignment> resolveForDivisionIfPresent(
            Long subjectId, Long divisionId, Long academicTermId) {
        return assignmentRepository.findBySubjectIdAndDivisionIdAndBatchIdIsNullAndAcademicTermIdAndActiveTrue(
                subjectId, divisionId, academicTermId);
    }
}
