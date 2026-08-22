package com.college.laballocation.academic;

import com.college.laballocation.academic.AcademicTermDtos.AcademicTermResponse;
import com.college.laballocation.academic.AcademicTermDtos.CreateAcademicTermRequest;
import com.college.laballocation.academic.AcademicTermDtos.UpdateAcademicTermStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic-terms")
public class AcademicTermController {

    private final AcademicTermService academicTermService;

    public AcademicTermController(AcademicTermService academicTermService) {
        this.academicTermService = academicTermService;
    }

    @GetMapping
    public List<AcademicTermResponse> list() {
        return academicTermService.list();
    }

    @GetMapping("/{id}")
    public AcademicTermResponse get(@PathVariable Long id) {
        return academicTermService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public AcademicTermResponse create(@Valid @RequestBody CreateAcademicTermRequest request) {
        return academicTermService.create(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('LAB_ASSISTANT')")
    public AcademicTermResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateAcademicTermStatusRequest request) {
        return academicTermService.updateStatus(id, request);
    }
}
