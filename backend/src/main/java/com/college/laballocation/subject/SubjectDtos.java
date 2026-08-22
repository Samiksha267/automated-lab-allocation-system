package com.college.laballocation.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SubjectDtos {
    private SubjectDtos() {}

    public record CreateSubjectRequest(@NotNull Long academicYearId, @NotBlank String code, @NotBlank String name) {}

    public record UpdateSubjectRequest(@NotBlank String name, boolean active) {}

    public record SubjectResponse(Long id, Long academicYearId, int yearNumber, String code, String name, boolean active) {
        public static SubjectResponse from(Subject subject) {
            return new SubjectResponse(
                    subject.getId(),
                    subject.getAcademicYear().getId(),
                    subject.getAcademicYear().getYearNumber(),
                    subject.getCode(),
                    subject.getName(),
                    subject.isActive());
        }
    }
}
