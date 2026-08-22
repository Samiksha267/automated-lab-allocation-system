package com.college.laballocation.faculty;

import jakarta.validation.constraints.NotNull;

public final class SubjectFacultyAssignmentDtos {
    private SubjectFacultyAssignmentDtos() {}

    /** {@code batchId} nullable = division-level assignment (see {@link SubjectFacultyAssignment} for exact semantics). */
    public record CreateSubjectFacultyAssignmentRequest(
            @NotNull Long subjectId, @NotNull Long facultyId, @NotNull Long divisionId, Long batchId, @NotNull Long academicTermId) {}

    public record SubjectFacultyAssignmentResponse(
            Long id,
            Long subjectId,
            String subjectCode,
            Long facultyId,
            String facultyName,
            Long divisionId,
            String divisionCode,
            Long batchId,
            String batchCode,
            Long academicTermId,
            boolean active) {
        public static SubjectFacultyAssignmentResponse from(SubjectFacultyAssignment assignment) {
            return new SubjectFacultyAssignmentResponse(
                    assignment.getId(),
                    assignment.getSubject().getId(),
                    assignment.getSubject().getCode(),
                    assignment.getFaculty().getId(),
                    assignment.getFaculty().getName(),
                    assignment.getDivision().getId(),
                    assignment.getDivision().getCode(),
                    assignment.getBatch() == null ? null : assignment.getBatch().getId(),
                    assignment.getBatch() == null ? null : assignment.getBatch().getCode(),
                    assignment.getAcademicTerm().getId(),
                    assignment.isActive());
        }
    }
}
