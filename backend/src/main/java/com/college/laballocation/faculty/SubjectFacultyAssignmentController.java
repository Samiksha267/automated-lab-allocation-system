package com.college.laballocation.faculty;

import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.CreateSubjectFacultyAssignmentRequest;
import com.college.laballocation.faculty.SubjectFacultyAssignmentDtos.SubjectFacultyAssignmentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subject-faculty-assignments")
public class SubjectFacultyAssignmentController {

    private final SubjectFacultyAssignmentService assignmentService;

    public SubjectFacultyAssignmentController(SubjectFacultyAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/{id}")
    public SubjectFacultyAssignmentResponse get(@PathVariable Long id) {
        return assignmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SubjectFacultyAssignmentResponse create(@Valid @RequestBody CreateSubjectFacultyAssignmentRequest request) {
        return assignmentService.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public void deactivate(@PathVariable Long id) {
        assignmentService.deactivate(id);
    }
}
