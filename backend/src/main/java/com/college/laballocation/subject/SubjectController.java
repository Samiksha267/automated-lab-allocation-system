package com.college.laballocation.subject;

import com.college.laballocation.subject.SubjectDtos.CreateSubjectRequest;
import com.college.laballocation.subject.SubjectDtos.SubjectResponse;
import com.college.laballocation.subject.SubjectDtos.UpdateSubjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public List<SubjectResponse> listByAcademicYear(@RequestParam Long academicYearId) {
        return subjectService.listByAcademicYear(academicYearId);
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable Long id) {
        return subjectService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SubjectResponse create(@Valid @RequestBody CreateSubjectRequest request) {
        return subjectService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public SubjectResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest request) {
        return subjectService.update(id, request);
    }
}
