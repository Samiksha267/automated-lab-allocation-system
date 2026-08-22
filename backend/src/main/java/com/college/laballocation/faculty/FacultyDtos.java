package com.college.laballocation.faculty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class FacultyDtos {
    private FacultyDtos() {}

    public record CreateFacultyRequest(
            @NotBlank String employeeCode, @NotBlank String name, @Email String email, String department) {}

    public record UpdateFacultyRequest(@NotBlank String name, @Email String email, String department, boolean active) {}

    public record FacultyResponse(
            Long id, String employeeCode, String name, String email, String department, boolean active) {
        public static FacultyResponse from(Faculty faculty) {
            return new FacultyResponse(
                    faculty.getId(),
                    faculty.getEmployeeCode(),
                    faculty.getName(),
                    faculty.getEmail(),
                    faculty.getDepartment(),
                    faculty.isActive());
        }
    }
}
